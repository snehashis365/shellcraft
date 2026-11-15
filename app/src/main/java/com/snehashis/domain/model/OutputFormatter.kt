package com.snehashis.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class LogSeverity {
    ERROR,
    WARNING,
    SUCCESS,
    INFO
}

data class LogLineEntry(
    val text: String,
    val severity: LogSeverity
)

sealed interface FormattedOutput {
    data class PingReport(
        val host: String,
        val transmitted: String,
        val received: String,
        val lossPercent: String,
        val minMs: String,
        val avgMs: String,
        val maxMs: String,
        val rawLines: List<String>
    ) : FormattedOutput

    data class SystemdStatusReport(
        val unit: String,
        val loaded: String,
        val activeState: String,
        val isHealthy: Boolean,
        val mainPid: String,
        val memory: String,
        val tasks: String,
        val recentLogs: List<String>
    ) : FormattedOutput

    data class TableReport(
        val headers: List<String>,
        val rows: List<List<String>>
    ) : FormattedOutput

    data class StructuredJsonReport(
        val prettyJson: String,
        val summaryPairs: List<Pair<String, String>>,
        val itemCount: Int?
    ) : FormattedOutput

    data class KeyValueReport(
        val entries: List<Pair<String, String>>
    ) : FormattedOutput

    data class LogLinesReport(
        val entries: List<LogLineEntry>
    ) : FormattedOutput
}

object OutputFormatter {
    private val prettyJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun parse(command: String, rawOutput: String): FormattedOutput {
        val trimmed = rawOutput.trim()
        if (trimmed.isEmpty()) {
            return FormattedOutput.LogLinesReport(
                listOf(LogLineEntry("(Command completed with no output)", LogSeverity.INFO))
            )
        }

        // 1. JSON detection
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
        ) {
            val jsonParsed = tryParseJson(trimmed)
            if (jsonParsed != null) return jsonParsed
        }

        // 2. Ping output detection
        if (command.contains("ping", ignoreCase = true) ||
            (trimmed.contains("packets transmitted", ignoreCase = true) ||
                trimmed.contains("packets received", ignoreCase = true))
        ) {
            tryParsePing(trimmed)?.let { return it }
        }

        // 3. Systemctl status detection
        if (trimmed.contains("Loaded:") && trimmed.contains("Active:")) {
            tryParseSystemdStatus(trimmed)?.let { return it }
        }

        val lines = trimmed.lines().filter { it.isNotBlank() }

        // 4. Tabular / columnar CLI output (docker ps, df -h, ps, ls -la, ss, netstat, systemctl list-units)
        if (lines.size >= 2) {
            tryParseTable(lines)?.let { return it }
        }

        // 5. Key: Value output (at least 3 lines with `Key: Value`)
        val kvPairs = lines.mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx in 2..36 && !line.startsWith("http", ignoreCase = true)) {
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty() && !key.contains('[')) {
                    key to value
                } else null
            } else null
        }
        if (kvPairs.size >= 3 && kvPairs.size >= lines.size / 2) {
            return FormattedOutput.KeyValueReport(kvPairs)
        }

        // 6. Log / text lines with severity highlighting
        val logEntries = lines.map { line ->
            val severity = when {
                Regex("""\b(error|err|fatal|crit|failed|exception|panic|denied)\b""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(line) -> LogSeverity.ERROR
                Regex("""\b(warn|warning|deprecated|retry|timeout)\b""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(line) -> LogSeverity.WARNING
                Regex("""\b(ok|success|active|running|listening|ready|healthy|connected|up)\b""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(line) -> LogSeverity.SUCCESS
                else -> LogSeverity.INFO
            }
            LogLineEntry(line, severity)
        }
        return FormattedOutput.LogLinesReport(logEntries)
    }

    private fun tryParseJson(raw: String): FormattedOutput.StructuredJsonReport? {
        return runCatching {
            val element = Json.parseToJsonElement(raw)
            val formatted = prettyJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)
            val pairs = mutableListOf<Pair<String, String>>()
            var count: Int? = null
            when (element) {
                is JsonObject -> {
                    element.entries.take(8).forEach { (k, v) ->
                        val summary = when (v) {
                            is JsonPrimitive -> v.content
                            is JsonArray -> "${v.size} items"
                            is JsonObject -> "${v.size} fields"
                        }
                        pairs.add(k to summary)
                    }
                }
                is JsonArray -> {
                    count = element.size
                    pairs.add("Items" to "${element.size}")
                }
                else -> Unit
            }
            FormattedOutput.StructuredJsonReport(formatted, pairs, count)
        }.getOrNull()
    }

    private fun tryParsePing(raw: String): FormattedOutput.PingReport? {
        val lossMatch = Regex("""(\d+)\s+packets transmitted,\s+(\d+)\s+(?:packets\s+)?received.*?([\d.]+%)\s+packet loss""")
            .find(raw) ?: return null
        val rttMatch = Regex("""(?:round-trip|rtt)\s+min/avg/max(?:/\w+)?\s*=\s*([\d.]+)/([\d.]+)/([\d.]+)""")
            .find(raw)
        val hostMatch = Regex("""PING\s+([^\s(]+)""").find(raw)
        return FormattedOutput.PingReport(
            host = hostMatch?.groupValues?.getOrNull(1) ?: "Remote Target",
            transmitted = lossMatch.groupValues[1],
            received = lossMatch.groupValues[2],
            lossPercent = lossMatch.groupValues[3],
            minMs = rttMatch?.groupValues?.getOrNull(1)?.let { "$it ms" } ?: "—",
            avgMs = rttMatch?.groupValues?.getOrNull(2)?.let { "$it ms" } ?: "—",
            maxMs = rttMatch?.groupValues?.getOrNull(3)?.let { "$it ms" } ?: "—",
            rawLines = raw.lines().filter { it.isNotBlank() }
        )
    }

    private fun tryParseSystemdStatus(raw: String): FormattedOutput.SystemdStatusReport? {
        val lines = raw.lines()
        val unitLine = lines.firstOrNull()?.trim().orEmpty()
        val unit = unitLine.removePrefix("●").trim().substringBefore(" ")
        val loaded = lines.find { it.trimStart().startsWith("Loaded:") }
            ?.substringAfter("Loaded:")?.trim().orEmpty()
        val active = lines.find { it.trimStart().startsWith("Active:") }
            ?.substringAfter("Active:")?.trim().orEmpty()
        val pid = lines.find { it.trimStart().startsWith("Main PID:") }
            ?.substringAfter("Main PID:")?.trim().orEmpty()
        val memory = lines.find { it.trimStart().startsWith("Memory:") }
            ?.substringAfter("Memory:")?.trim().orEmpty()
        val tasks = lines.find { it.trimStart().startsWith("Tasks:") }
            ?.substringAfter("Tasks:")?.trim().orEmpty()
        val isHealthy = active.contains("active (running)", ignoreCase = true) ||
            active.contains("active (exited)", ignoreCase = true)
        val logStart = lines.indexOfFirst { it.trim().isEmpty() }
        val logs = if (logStart >= 0 && logStart + 1 < lines.size) {
            lines.drop(logStart + 1).filter { it.isNotBlank() }
        } else emptyList()

        return FormattedOutput.SystemdStatusReport(
            unit = unit.ifBlank { "Service Unit" },
            loaded = loaded.ifBlank { "—" },
            activeState = active.ifBlank { "unknown" },
            isHealthy = isHealthy,
            mainPid = pid.ifBlank { "—" },
            memory = memory.ifBlank { "—" },
            tasks = tasks.ifBlank { "—" },
            recentLogs = logs
        )
    }

    private fun tryParseTable(lines: List<String>): FormattedOutput.TableReport? {
        val headerLine = lines.first().trim()
        // Split by 2+ spaces or tabs (standard POSIX columnar output like docker ps, df -h, ps)
        val splitRegex = Regex("""\t+|\s{2,}""")
        val headers = headerLine.split(splitRegex).map { it.trim() }.filter { it.isNotEmpty() }
        if (headers.size !in 2..8) return null

        val dataLines = lines.drop(1).filter { line ->
            !line.startsWith("total ", ignoreCase = true) && !line.all { ch -> ch == '-' || ch == '=' }
        }
        if (dataLines.isEmpty()) return null

        val rows = dataLines.take(50).map { line ->
            val cols = line.trim().split(splitRegex, limit = headers.size).map { it.trim() }
            if (cols.size < headers.size) {
                cols + List(headers.size - cols.size) { "" }
            } else cols
        }
        val validRowCount = rows.count { row -> row.count { it.isNotEmpty() } >= 2 }
        if (validRowCount == 0 || validRowCount < rows.size / 2) return null

        return FormattedOutput.TableReport(headers = headers, rows = rows)
    }
}
