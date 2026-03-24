package com.spoof.cosa.data

import com.spoof.cosa.common.SpoofConfig

class CosaMaintenanceActions(
    private val rootCommandRunner: RootCommandRunner = RootCommandRunner()
) {

    sealed interface ApplyResult {
        data object Success : ApplyResult
        data class Failure(val message: String) : ApplyResult
    }

    fun applySavedValueAndReboot(): ApplyResult {
        val command = """
            am force-stop ${SpoofConfig.targetPackageName} || true
            for d in /data/user/0/${SpoofConfig.targetPackageName} /data/user_de/0/${SpoofConfig.targetPackageName} /data/data/${SpoofConfig.targetPackageName}; do
                if [ -d "${'$'}d" ]; then
                    find "${'$'}d" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
                fi
            done
            sync
            sleep 2
            reboot
        """.trimIndent()

        val result = rootCommandRunner.run(command)
        return if (result.isSuccess) {
            ApplyResult.Success
        } else {
            ApplyResult.Failure(
                result.output.ifBlank { "exitCode=${result.exitCode}" }
            )
        }
    }
}
