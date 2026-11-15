package com.example.data.ssh

import android.util.Log
import com.example.core.security.SecretCipher
import com.example.domain.model.PluginCommandBuilder
import com.example.domain.model.RemoteEnvironmentInfo
import com.example.domain.model.SshAuthType
import com.example.domain.model.SshExecutionResult
import com.example.domain.model.SshHostProfile
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

private const val TAG = "SshEngine"

class SshEngine {
    private val sessionMutex = Mutex()
    private var activeSession: Session? = null
    private var activeHost: SshHostProfile? = null

    fun isConnected(): Boolean = activeSession?.isConnected == true

    suspend fun connect(profile: SshHostProfile): RemoteEnvironmentInfo = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            disconnectInternal()
            val jsch = JSch()
            if (profile.authType == SshAuthType.PRIVATE_KEY) {
                val privateKey = SecretCipher.decrypt(profile.encryptedPrivateKey)
                require(privateKey.isNotBlank()) { "Private key is empty or could not be decrypted." }
                val passphrase = SecretCipher.decrypt(profile.encryptedKeyPassphrase)
                val keyBytes = privateKey.toByteArray(StandardCharsets.UTF_8)
                val passBytes = if (passphrase.isNotEmpty()) passphrase.toByteArray(StandardCharsets.UTF_8) else null
                jsch.addIdentity("shellcraft-${profile.id}", keyBytes, null, passBytes)
            }

            val session = jsch.getSession(profile.username, profile.host, profile.port)
            if (profile.authType == SshAuthType.PASSWORD) {
                val password = SecretCipher.decrypt(profile.encryptedPassword)
                require(password.isNotBlank()) { "SSH password is empty or could not be decrypted." }
                session.setPassword(password)
            }

            session.setConfig("StrictHostKeyChecking", "no")
            session.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
            session.timeout = 15_000
            session.connect(15_000)

            activeSession = session
            activeHost = profile

            probeEnvironmentInternal(session)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            disconnectInternal()
        }
    }

    private fun disconnectInternal() {
        try {
            activeSession?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting SSH session", e)
        } finally {
            activeSession = null
            activeHost = null
        }
    }

    private fun probeEnvironmentInternal(session: Session): RemoteEnvironmentInfo {
        val probeCmd = "uname -s 2>/dev/null || ver; echo '---'; uname -m 2>/dev/null || echo unknown; echo '---'; whoami; echo '---'; echo \"\${SHELL:-sh}\"; echo '---'; command -v sudo >/dev/null 2>&1 && echo YES || echo NO"
        val result = executeBlocking(session, probeCmd, sudoPassword = null, onStdoutChunk = null, onStderrChunk = null)
        val parts = result.stdout.split("---").map { it.trim() }
        return RemoteEnvironmentInfo(
            osName = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "POSIX",
            architecture = parts.getOrNull(1).orEmpty(),
            currentUser = parts.getOrNull(2).orEmpty(),
            shell = parts.getOrNull(3)?.takeIf { it.isNotBlank() } ?: "/bin/sh",
            hasSudo = parts.getOrNull(4)?.contains("YES") == true
        )
    }

    suspend fun probeCommandDocumentation(commandName: String): String = withContext(Dispatchers.IO) {
        val session = activeSession?.takeIf { it.isConnected }
            ?: throw IllegalStateException("No active SSH connection. Connect to a host first.")
        val safeCmd = PluginCommandBuilder.posixQuote(commandName.trim())
        val probeScript = buildString {
            append("echo '=== WHICH ==='; command -v $safeCmd 2>&1 || true; ")
            append("echo '=== VERSION ==='; $safeCmd --version 2>&1 | head -n 5 || true; ")
            append("echo '=== HELP ==='; ($safeCmd --help 2>&1 || $safeCmd -h 2>&1 || true) | head -n 160; ")
            append("echo '=== MAN ==='; if command -v man >/dev/null 2>&1; then MANWIDTH=100 man $safeCmd 2>/dev/null | col -b 2>/dev/null | head -n 220; fi")
        }
        val result = executeBlocking(session, probeScript, sudoPassword = null, onStdoutChunk = null, onStderrChunk = null)
        val combined = result.stdout.ifBlank { result.stderr }
        if (combined.isBlank()) {
            throw IllegalStateException("Command '$commandName' produced no help or man output on remote host.")
        }
        combined.take(16_000)
    }

    suspend fun executeCommand(
        command: String,
        sudoPassword: String? = null,
        onStdoutChunk: ((String) -> Unit)? = null,
        onStderrChunk: ((String) -> Unit)? = null
    ): SshExecutionResult = withContext(Dispatchers.IO) {
        val session = activeSession?.takeIf { it.isConnected }
            ?: throw IllegalStateException("No active SSH connection. Connect to a remote host first.")
        val effectiveCommand = if (sudoPassword != null) {
            rewriteWithStdinSudo(command)
        } else {
            command
        }
        executeBlocking(
            session = session,
            command = effectiveCommand,
            sudoPassword = sudoPassword,
            onStdoutChunk = onStdoutChunk,
            onStderrChunk = onStderrChunk
        )
    }

    private fun rewriteWithStdinSudo(command: String): String {
        val trimmed = command.trim()
        return when {
            trimmed.startsWith("sudo -S") -> trimmed
            trimmed.startsWith("sudo ") -> "sudo -S -p '' " + trimmed.removePrefix("sudo ").trimStart()
            else -> "sudo -S -p '' $trimmed"
        }
    }

    private fun executeBlocking(
        session: Session,
        command: String,
        sudoPassword: String?,
        onStdoutChunk: ((String) -> Unit)?,
        onStderrChunk: ((String) -> Unit)?
    ): SshExecutionResult {
        val startMs = System.currentTimeMillis()
        var channel: ChannelExec? = null
        try {
            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            channel.setInputStream(null)

            val outBuffer = ByteArrayOutputStream()
            val errBuffer = ByteArrayOutputStream()
            val stdoutStream: InputStream = channel.inputStream
            val stderrStream: InputStream = channel.errStream
            val stdinStream = channel.outputStream

            channel.connect(15_000)

            if (sudoPassword != null) {
                stdinStream.write((sudoPassword + "\n").toByteArray(StandardCharsets.UTF_8))
                stdinStream.flush()
            }

            val buf = ByteArray(4096)
            while (true) {
                if (Thread.currentThread().isInterrupted) {
                    throw CancellationException("SSH command cancelled")
                }
                while (stdoutStream.available() > 0) {
                    val read = stdoutStream.read(buf, 0, buf.size)
                    if (read <= 0) break
                    outBuffer.write(buf, 0, read)
                    onStdoutChunk?.invoke(String(buf, 0, read, StandardCharsets.UTF_8))
                }
                while (stderrStream.available() > 0) {
                    val read = stderrStream.read(buf, 0, buf.size)
                    if (read <= 0) break
                    errBuffer.write(buf, 0, read)
                    onStderrChunk?.invoke(String(buf, 0, read, StandardCharsets.UTF_8))
                }
                if (channel.isClosed) {
                    if (stdoutStream.available() > 0 || stderrStream.available() > 0) continue
                    break
                }
                Thread.sleep(40)
            }

            return SshExecutionResult(
                command = command.replace("sudo -S -p '' ", "sudo "),
                exitCode = channel.exitStatus,
                stdout = outBuffer.toString(StandardCharsets.UTF_8.name()),
                stderr = errBuffer.toString(StandardCharsets.UTF_8.name()),
                durationMs = System.currentTimeMillis() - startMs,
                elevatedWithSudo = sudoPassword != null
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "SSH execution failed", e)
            throw IllegalStateException(e.localizedMessage ?: "SSH execution failed", e)
        } finally {
            try {
                channel?.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}
