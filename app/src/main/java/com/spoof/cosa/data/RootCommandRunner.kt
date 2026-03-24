package com.spoof.cosa.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class RootCommandRunner {

    data class CommandResult(
        val isSuccess: Boolean,
        val output: String,
        val exitCode: Int
    )

    fun run(command: String): CommandResult {
        return runCatching {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            val outputReader = thread(name = "root-command-output", isDaemon = true) {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.forEachLine { line ->
                        if (output.isNotEmpty()) {
                            output.append('\n')
                        }
                        output.append(line)
                    }
                }
            }

            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy()
                if (!process.waitFor(PROCESS_EXIT_GRACE_PERIOD_MS, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                }
                outputReader.join(THREAD_JOIN_TIMEOUT_MS)
                return@runCatching CommandResult(
                    isSuccess = false,
                    output = "root 命令执行超时，请确认已授予 root 权限",
                    exitCode = TIMEOUT_EXIT_CODE
                )
            }

            outputReader.join(THREAD_JOIN_TIMEOUT_MS)
            val exitCode = process.exitValue()
            CommandResult(exitCode == 0, output.toString().trim(), exitCode)
        }.getOrElse { error ->
            CommandResult(
                isSuccess = false,
                output = error.message.orEmpty(),
                exitCode = -1
            )
        }
    }

    companion object {
        private const val COMMAND_TIMEOUT_SECONDS = 20L
        private const val PROCESS_EXIT_GRACE_PERIOD_MS = 500L
        private const val THREAD_JOIN_TIMEOUT_MS = 500L
        private const val TIMEOUT_EXIT_CODE = -2
    }
}
