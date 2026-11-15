package com.example.domain.model

enum class PromptKind {
    CONFIRM_YN,
    CONFIRM_YES_NO,
    SECRET_INPUT,
    PRESS_ENTER,
    FREE_INPUT
}

data class InteractivePrompt(
    val kind: PromptKind,
    val promptText: String,
    val quickReplies: List<Pair<String, String>> = emptyList()
)

object InteractivePromptDetector {

    private val ansiRegex = Regex("""\u001B\[[;?0-9]*[A-Za-z]|\u001B\][^\u0007]*\u0007|\r""")

    fun stripAnsi(raw: String): String = ansiRegex.replace(raw, "")

    /**
     * Inspects the trailing output of a live SSH process to determine whether the CLI
     * is paused waiting for interactive user input on stdin.
     */
    fun detect(rawOutput: String, stalledOnPartialLine: Boolean = false): InteractivePrompt? {
        val clean = stripAnsi(rawOutput).trimEnd(' ', '\t')
        if (clean.isBlank()) return null

        val lastLine = clean.substringAfterLast('\n').trim()
        if (lastLine.isBlank()) return null

        // Never match extremely long prose paragraphs
        if (lastLine.length > 220) return null

        val lower = lastLine.lowercase()

        // 1. Secret / password / passphrase prompts
        if (Regex("""\b(password|passphrase|pin|passcode|otp|verification code)\b.*[:?]?$""").containsMatchIn(lower) ||
            lower.startsWith("[sudo] password") ||
            lower.endsWith("secret:")
        ) {
            return InteractivePrompt(
                kind = PromptKind.SECRET_INPUT,
                promptText = lastLine
            )
        }

        // 2. Press Enter / Return prompts
        if (Regex("""press\s+(enter|return|the enter key)|hit\s+(enter|return)|press\s+any\s+key""").containsMatchIn(lower)) {
            return InteractivePrompt(
                kind = PromptKind.PRESS_ENTER,
                promptText = lastLine,
                quickReplies = listOf("Press Enter ↵" to "")
            )
        }

        // 3. Explicit yes/no full-word prompts (e.g. "Are you sure you want to continue connecting (yes/no)?")
        if (Regex("""\(yes/no(/[^\)]*)?\)|\[yes/no\]|type\s+['"]?yes['"]?""").containsMatchIn(lower)) {
            return InteractivePrompt(
                kind = PromptKind.CONFIRM_YES_NO,
                promptText = lastLine,
                quickReplies = listOf("yes" to "yes", "no" to "no")
            )
        }

        // 4. Short [y/N], [Y/n], (y/n), [y/n/q] prompts
        if (Regex("""\[[ynYN]/[ynYN](?:/[a-zA-Z])*\]|\([ynYN]/[ynYN]\)|proceed\s*\?|continue\s*\?|overwrite.*\?|delete.*\?""").containsMatchIn(lower)) {
            return InteractivePrompt(
                kind = PromptKind.CONFIRM_YN,
                promptText = lastLine,
                quickReplies = listOf("Yes (y)" to "y", "No (n)" to "n")
            )
        }

        // 5. Generic trailing prompt waiting on an incomplete line (no newline yet)
        if (stalledOnPartialLine && !rawOutput.endsWith('\n')) {
            val endsWithPromptPunct = lastLine.endsWith(":") ||
                lastLine.endsWith("?") ||
                lastLine.endsWith(">") ||
                lastLine.endsWith("#") ||
                Regex("""\[[^\]]{1,12}\]\s*$""").containsMatchIn(lastLine)
            if (endsWithPromptPunct) {
                return InteractivePrompt(
                    kind = PromptKind.FREE_INPUT,
                    promptText = lastLine
                )
            }
        }

        return null
    }
}
