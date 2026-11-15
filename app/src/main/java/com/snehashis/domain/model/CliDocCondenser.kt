package com.snehashis.domain.model

/**
 * Prepares CLI `--help` / `man` text for token-constrained on-device models (e.g. Gemini Nano's
 * 4,000-token ceiling) and repairs common JSON formatting quirks emitted by smaller models.
 */
object CliDocCondenser {

    private val optionLineRegex = Regex("""^\s{0,8}(-[A-Za-z0-9?]|--[A-Za-z0-9][A-Za-z0-9_-]*)\b.*""")
    private val subcommandLineRegex = Regex("""^\s{2,8}([a-z][a-z0-9_-]{1,24})\s{2,}\S+.*""")
    private val sectionHeaderRegex = Regex("""^[A-Z][A-Z0-9 _/-]{2,40}:?$""")

    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        return ((text.length + 3) / 4).coerceAtLeast(1)
    }

    fun condense(commandName: String, rawDocs: String, maxChars: Int = 6_200): String {
        val cleaned = rawDocs
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .map { it.trimEnd() }

        if (cleaned.sumOf { it.length + 1 } <= maxChars) {
            return rawDocs.trim()
        }

        val headerLines = mutableListOf<String>()
        val usageLines = mutableListOf<String>()
        val subcommandLines = mutableListOf<String>()
        val optionLines = mutableListOf<String>()

        var currentSection = ""
        var includeNextAsOptionDesc = false

        for ((idx, line) in cleaned.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                includeNextAsOptionDesc = false
                continue
            }
            if (sectionHeaderRegex.matches(trimmed)) {
                currentSection = trimmed.trimEnd(':').uppercase()
                includeNextAsOptionDesc = false
                continue
            }

            if (idx < 12 && headerLines.size < 6 && !trimmed.startsWith("-")) {
                headerLines.add(trimmed)
            }

            val lower = trimmed.lowercase()
            when {
                currentSection.contains("SYNOPSIS") ||
                    lower.startsWith("usage:") ||
                    lower.startsWith("synopsis") ||
                    (trimmed.startsWith(commandName) && trimmed.length < 180) -> {
                    if (usageLines.size < 12) usageLines.add(trimmed)
                    includeNextAsOptionDesc = false
                }
                optionLineRegex.matches(line) -> {
                    optionLines.add(trimmed.take(220))
                    includeNextAsOptionDesc = true
                }
                includeNextAsOptionDesc && line.startsWith("    ") && optionLines.isNotEmpty() -> {
                    val lastIdx = optionLines.lastIndex
                    val prev = optionLines[lastIdx]
                    if (prev.length < 220) {
                        optionLines[lastIdx] = "$prev ${trimmed.take(120)}".take(260)
                    }
                    includeNextAsOptionDesc = false
                }
                currentSection.contains("COMMAND") ||
                    currentSection.contains("SUBCOMMAND") ||
                    subcommandLineRegex.matches(line) -> {
                    if (subcommandLines.size < 25) subcommandLines.add(trimmed.take(160))
                    includeNextAsOptionDesc = false
                }
                else -> {
                    includeNextAsOptionDesc = false
                }
            }
        }

        val out = StringBuilder()
        if (headerLines.isNotEmpty()) {
            out.appendLine("## SUMMARY")
            headerLines.distinct().forEach { out.appendLine(it) }
            out.appendLine()
        }
        if (usageLines.isNotEmpty()) {
            out.appendLine("## USAGE")
            usageLines.distinct().forEach { out.appendLine(it) }
            out.appendLine()
        }
        if (subcommandLines.isNotEmpty()) {
            out.appendLine("## SUBCOMMANDS")
            subcommandLines.distinct().forEach { out.appendLine(it) }
            out.appendLine()
        }
        if (optionLines.isNotEmpty()) {
            out.appendLine("## FLAGS")
            for (opt in optionLines.distinct()) {
                if (out.length + opt.length + 2 > maxChars) break
                out.appendLine(opt)
            }
        }

        val assembled = out.toString().trim()
        return if (assembled.length >= 120) {
            assembled.take(maxChars)
        } else {
            rawDocs.trim().take(maxChars)
        }
    }

    /**
     * Strips markdown fences, removes trailing commas, and closes unclosed braces/brackets
     * if a small model hit a token limit right near the end of JSON emission.
     */
    fun repairJson(raw: String): String {
        var text = raw.trim()
        // Strip ```json ... ``` fences if present
        if (text.contains("```")) {
            text = text
                .replace(Regex("""```json\s*""", RegexOption.IGNORE_CASE), "")
                .replace("```", "")
                .trim()
        }
        val start = text.indexOf('{')
        if (start < 0) return text
        text = text.substring(start)

        // Remove trailing commas before closing brackets/braces
        text = text.replace(Regex(""",\s*([}\]])"""), "$1")

        // If already cleanly closed, slice to last '}'
        var depthBrace = 0
        var depthBracket = 0
        var inString = false
        var escaped = false
        var lastValidEnd = -1

        for (i in text.indices) {
            val c = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\' && inString) {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                when (c) {
                    '{' -> depthBrace++
                    '}' -> {
                        depthBrace--
                        if (depthBrace == 0 && depthBracket == 0) {
                            lastValidEnd = i
                            break
                        }
                    }
                    '[' -> depthBracket++
                    ']' -> depthBracket--
                }
            }
        }

        if (lastValidEnd > 0) {
            return text.substring(0, lastValidEnd + 1)
                .replace(Regex(""",\s*([}\]])"""), "$1")
        }

        // Attempt recovery for truncated JSON by closing open strings/brackets/braces
        val repaired = StringBuilder(text.trimEnd())
        if (inString) repaired.append('"')
        val cleaned = repaired.toString()
            .trimEnd(',', ' ', '\n', '\r', '\t', ':')
        val finalSb = StringBuilder(cleaned)
        repeat(depthBracket.coerceAtLeast(0)) { finalSb.append(']') }
        repeat(depthBrace.coerceAtLeast(0)) { finalSb.append('}') }
        return finalSb.toString().replace(Regex(""",\s*([}\]])"""), "$1")
    }
}
