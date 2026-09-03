package com.wikiglobal.iconconverter.hyperos

import java.io.ByteArrayOutputStream

/** Executes a deliberately small, read-only command set through Magisk/su only after the user starts a probe. */
class RootReadOnlyShell {
    data class Result(val output: String, val exitCode: Int)

    fun isRootAvailable(): Boolean = command("id -u").output.trim() == "0"

    fun command(readOnlyCommand: String): Result {
        val process = ProcessBuilder("su", "-c", readOnlyCommand).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return Result(output.trim(), process.waitFor())
    }

    fun exists(path: String): Boolean = command("test -e ${quote(path)}").exitCode == 0
    fun isDirectory(path: String): Boolean = command("test -d ${quote(path)}").exitCode == 0
    fun lines(readOnlyCommand: String): List<String> = command(readOnlyCommand).output.lineSequence().filter { it.isNotBlank() }.toList()

    /** Used only for small textual archive entries such as transform_config.xml and manifests. */
    fun bytes(readOnlyCommand: String, maxBytes: Int = 512 * 1024): ByteArray? {
        val process = ProcessBuilder("su", "-c", readOnlyCommand).redirectErrorStream(false).start()
        val out = ByteArrayOutputStream()
        process.inputStream.use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (out.size() + count > maxBytes) {
                    process.destroyForcibly()
                    return null
                }
                out.write(buffer, 0, count)
            }
        }
        process.waitFor()
        return if (process.exitValue() == 0) out.toByteArray() else null
    }

    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
