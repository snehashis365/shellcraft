package com.snehashis.core.security

import com.snehashis.domain.model.PluginAction
import com.snehashis.domain.model.RiskLevel

data class SafetyAssessment(
    val effectiveRisk: RiskLevel,
    val requiresConfirmation: Boolean,
    val requiresSuperuserAuth: Boolean,
    val warnings: List<String>
)

object SafetyGuard {

    private data class RiskRule(
        val regex: Regex,
        val level: RiskLevel,
        val reason: String
    )

    private val riskRules = listOf(
        RiskRule(
            Regex("""(^|\s)rm\s+.*-[a-zA-Z]*r[a-zA-Z]*f|rm\s+-rf\s+/"""),
            RiskLevel.CRITICAL,
            "Recursive forced file deletion (`rm -rf`) can permanently erase data."
        ),
        RiskRule(
            Regex("""(^|\s)(mkfs|fdisk|parted|wipefs|dd)\b"""),
            RiskLevel.CRITICAL,
            "Low-level block device / filesystem operation detected."
        ),
        RiskRule(
            Regex("""(^|\s)(shutdown|reboot|poweroff|halt|init\s+[06])\b"""),
            RiskLevel.CRITICAL,
            "System power or reboot command will terminate active remote sessions."
        ),
        RiskRule(
            Regex(""">\s*/dev/(sd|nvme|vd|hd|disk)"""),
            RiskLevel.CRITICAL,
            "Direct write redirection to a raw block device detected."
        ),
        RiskRule(
            Regex("""(curl|wget)\b.*\|\s*(sh|bash|zsh|python|perl)"""),
            RiskLevel.CRITICAL,
            "Remote script pipe-to-shell execution detected."
        ),
        RiskRule(
            Regex("""(^|\s)(kill\s+-9|killall|pkill\s+-9)\b"""),
            RiskLevel.HIGH,
            "Forceful process termination (`SIGKILL`) prevents graceful shutdown."
        ),
        RiskRule(
            Regex("""(^|\s)(chmod\s+.*777|chown\s+-R)\b"""),
            RiskLevel.HIGH,
            "Broad filesystem permission or recursive ownership change."
        ),
        RiskRule(
            Regex("""\b(prune|purge|drop|truncate|flushall|flushdb|reset\s+--hard)\b""", RegexOption.IGNORE_CASE),
            RiskLevel.HIGH,
            "Destructive prune, reset, or data-removal keyword detected."
        ),
        RiskRule(
            Regex("""\b(iptables|ufw\s+disable|nft\s+flush|systemctl\s+(stop|disable|mask))\b"""),
            RiskLevel.HIGH,
            "Firewall or system service availability modification."
        ),
        RiskRule(
            Regex("""\b(systemctl\s+restart|docker\s+(rm|stop|restart)|apt\s+(remove|purge))\b"""),
            RiskLevel.MODERATE,
            "Service state or package modification on the remote host."
        )
    )

    fun assess(
        command: String,
        action: PluginAction?,
        elevateWithSudo: Boolean,
        confirmModerateCommands: Boolean
    ): SafetyAssessment {
        val warnings = mutableListOf<String>()
        var highestRisk = action?.riskLevel ?: RiskLevel.SAFE

        val hasSudoKeyword = elevateWithSudo ||
            (action?.requiresSudo == true) ||
            Regex("""(^|\s)(sudo|doas|su)\b""").containsMatchIn(command)

        if (hasSudoKeyword) {
            warnings.add("Superuser (`sudo`) elevation requires manual biometric or password authorization.")
            if (highestRisk < RiskLevel.HIGH) {
                highestRisk = RiskLevel.HIGH
            }
        }

        for (rule in riskRules) {
            if (rule.regex.containsMatchIn(command)) {
                warnings.add(rule.reason)
                if (rule.level > highestRisk) {
                    highestRisk = rule.level
                }
            }
        }

        if (command.contains(";") || command.contains("&&") || command.contains("`") || command.contains("$(")) {
            warnings.add("Compound shell operator or subshell substitution detected; inspect arguments carefully.")
            if (highestRisk < RiskLevel.MODERATE) {
                highestRisk = RiskLevel.MODERATE
            }
        }

        val requiresConfirmation = when (highestRisk) {
            RiskLevel.CRITICAL, RiskLevel.HIGH -> true
            RiskLevel.MODERATE -> confirmModerateCommands
            RiskLevel.SAFE -> false
        }

        return SafetyAssessment(
            effectiveRisk = highestRisk,
            requiresConfirmation = requiresConfirmation,
            requiresSuperuserAuth = hasSudoKeyword,
            warnings = warnings.distinct()
        )
    }
}
