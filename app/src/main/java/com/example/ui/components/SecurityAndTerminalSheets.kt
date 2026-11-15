package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.core.security.BiometricAuthHelper
import com.example.core.security.BiometricCapability
import com.example.domain.model.PluginSchemaSpec
import com.example.domain.model.RiskLevel
import com.example.domain.model.SshAuthType
import com.example.domain.model.SshHostProfile
import com.example.domain.model.TargetOs
import com.example.ui.PendingExecutionRequest
import com.example.ui.TerminalSessionState
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun SafetyAndSudoDialog(
    pending: PendingExecutionRequest,
    requireBiometricForSudo: Boolean,
    onBiometricVerified: () -> Unit,
    onConfirm: (sudoPassword: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricCapability = remember(context) { BiometricAuthHelper.checkCapability(context) }

    var sudoPassword by remember { mutableStateOf("") }
    var biometricError by remember { mutableStateOf<String?>(null) }

    val needsBioStep = pending.assessment.requiresSuperuserAuth &&
        requireBiometricForSudo &&
        !pending.biometricVerified &&
        biometricCapability == BiometricCapability.AVAILABLE &&
        activity != null

    val isHighRisk = pending.assessment.effectiveRisk >= RiskLevel.HIGH

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isHighRisk) Icons.Filled.Warning else Icons.Filled.Security,
                contentDescription = null,
                tint = riskColor(pending.assessment.effectiveRisk),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = when {
                    pending.assessment.requiresSuperuserAuth -> "Superuser & Safety Check"
                    isHighRisk -> "High-Risk Command Confirmation"
                    else -> "Confirm Command Execution"
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RiskBadge(
                    risk = pending.assessment.effectiveRisk,
                    requiresSudo = pending.assessment.requiresSuperuserAuth
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "$ ${pending.command}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                }

                pending.assessment.warnings.forEach { warning ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = riskColor(pending.assessment.effectiveRisk),
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(15.dp)
                        )
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (pending.assessment.requiresSuperuserAuth) {
                    if (needsBioStep) {
                        OutlinedButton(
                            onClick = {
                                biometricError = null
                                BiometricAuthHelper.authenticate(
                                    activity = activity!!,
                                    title = "Authorize Superuser Execution",
                                    subtitle = "Confirm identity to elevate `${pending.pluginName}` with sudo",
                                    onSuccess = onBiometricVerified,
                                    onError = { err -> biometricError = err }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("biometric_authorize_button")
                        ) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify Phone Biometrics")
                        }
                        if (!biometricError.isNullOrBlank()) {
                            Text(
                                text = biometricError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else if (pending.biometricVerified && requireBiometricForSudo) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Biometric identity verified",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = sudoPassword,
                        onValueChange = { sudoPassword = it },
                        label = { Text("Remote sudo password (optional if NOPASSWD)") },
                        placeholder = { Text("Never stored; piped via stdin only") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sudo_password_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(sudoPassword.takeIf { it.isNotEmpty() }) },
                enabled = !needsBioStep,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isHighRisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_execute_button")
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isHighRisk) "Authorize & Run" else "Run Command")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("cancel_execute_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTerminalSheet(
    state: TerminalSessionState,
    onCopyOutput: (String) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        text = if (state.isRunning) "Streaming SSH Execution..." else "Execution Result",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.isRunning) {
                        IconButton(
                            onClick = onStop,
                            modifier = Modifier.testTag("stop_execution_button")
                        ) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = "Cancel command",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { onCopyOutput(state.output) },
                            modifier = Modifier.testTag("copy_terminal_output_button")
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy output")
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_terminal_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close terminal drawer")
                    }
                }
            }

            state.lastResult?.let { result ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val ok = result.exitCode == 0
                    Surface(
                        color = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                        contentColor = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "EXIT ${result.exitCode}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "${result.durationMs} ms",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 380.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = "$ ${state.command}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!state.errorMessage.isNullOrBlank()) {
                        Text(
                            text = "ERROR: ${state.errorMessage}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                    } else {
                        Text(
                            text = state.output.ifBlank { "Waiting for remote output..." },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun HostEditorDialog(
    initialHost: SshHostProfile?,
    onSave: (
        label: String,
        host: String,
        port: Int,
        username: String,
        authType: SshAuthType,
        password: String,
        privateKey: String,
        passphrase: String,
        targetOs: TargetOs
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(initialHost?.label ?: "") }
    var host by remember { mutableStateOf(initialHost?.host ?: "") }
    var portText by remember { mutableStateOf((initialHost?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(initialHost?.username ?: "") }
    var authType by remember { mutableStateOf(initialHost?.authType ?: SshAuthType.PASSWORD) }
    var password by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var targetOs by remember { mutableStateOf(initialHost?.targetOsHint ?: TargetOs.POSIX) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialHost == null) "Add SSH Host" else "Edit SSH Host",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Display Label") },
                    placeholder = { Text("prod-eu-1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("host_label_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Hostname / IP") },
                        placeholder = { Text("192.168.1.50") },
                        singleLine = true,
                        modifier = Modifier.weight(2.2f).testTag("host_address_input")
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter(Char::isDigit) },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("host_port_input")
                    )
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    placeholder = { Text("ubuntu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("host_username_input")
                )

                Text("Authentication", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = authType == SshAuthType.PASSWORD,
                        onClick = { authType = SshAuthType.PASSWORD },
                        label = { Text("Password") },
                        modifier = Modifier.testTag("auth_password_chip")
                    )
                    FilterChip(
                        selected = authType == SshAuthType.PRIVATE_KEY,
                        onClick = { authType = SshAuthType.PRIVATE_KEY },
                        label = { Text("Private Key") },
                        modifier = Modifier.testTag("auth_key_chip")
                    )
                }

                if (authType == SshAuthType.PASSWORD) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = {
                            Text(if (initialHost == null) "SSH Password" else "New SSH Password (leave blank to keep)")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("host_password_input")
                    )
                } else {
                    OutlinedTextField(
                        value = privateKey,
                        onValueChange = { privateKey = it },
                        label = { Text("OpenSSH / PEM Private Key") },
                        placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().testTag("host_key_input")
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Key Passphrase (optional)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("host_passphrase_input")
                    )
                }

                Text("Target Environment", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TargetOs.entries.forEach { os ->
                        FilterChip(
                            selected = targetOs == os,
                            onClick = { targetOs = os },
                            label = { Text(os.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        label,
                        host,
                        portText.toIntOrNull() ?: 22,
                        username,
                        authType,
                        password,
                        privateKey,
                        passphrase,
                        targetOs
                    )
                },
                enabled = host.isNotBlank() && username.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp).testTag("save_host_button")
            ) {
                Text("Save Host")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginSchemaDocsSheet(
    onImportJson: (String) -> Result<*>,
    onCopySpec: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var jsonInput by remember { mutableStateOf("") }
    var importFeedback by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Plugin Architecture & SDK Spec", style = MaterialTheme.typography.titleLarge)
                Row {
                    IconButton(onClick = { onCopySpec(PluginSchemaSpec.SPEC_MARKDOWN) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy plugin schema spec")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close spec sheet")
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = PluginSchemaSpec.SPEC_MARKDOWN,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier.padding(12.dp)
                )
            }

            Text("Import Third-Party / Agent Plugin JSON", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = jsonInput,
                onValueChange = {
                    jsonInput = it
                    importFeedback = null
                },
                label = { Text("Paste shellcraft.plugin.v1 JSON") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth().testTag("import_plugin_json_input")
            )
            if (!importFeedback.isNullOrBlank()) {
                Text(
                    text = importFeedback!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Button(
                onClick = {
                    val result = onImportJson(jsonInput)
                    importFeedback = result.fold(
                        onSuccess = {
                            jsonInput = ""
                            "Validated and imported plugin into local store!"
                        },
                        onFailure = { "Invalid plugin JSON: ${it.localizedMessage}" }
                    )
                },
                enabled = jsonInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("validate_import_plugin_button")
            ) {
                Text("Validate & Import Plugin")
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
