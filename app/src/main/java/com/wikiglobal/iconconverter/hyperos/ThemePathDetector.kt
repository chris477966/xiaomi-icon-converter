package com.wikiglobal.iconconverter.hyperos

/** Finds only declared Xiaomi/MIUI theme locations and records anything actually present. */
class ThemePathDetector(private val shell: RootReadOnlyShell) {
    private val standardPaths = listOf(
        "/data/system/theme/icons",
        "/data/system/theme/dynamicicons",
        "/system/media/theme/default/icons",
        "/system/media/theme/default/dynamicicons",
        "/product/media/theme/default/icons",
        "/system_ext/media/theme/default/icons"
    )

    fun detect(): List<RootFileMetadata> {
        val discovered = shell.lines(
            "find /data/system/theme /system/media/theme /product/media/theme /system_ext/media/theme " +
                "-type d \\( -name icons -o -name dynamicicons \\) 2>/dev/null"
        )
        return (standardPaths + discovered).distinct().filter(shell::exists).map(::metadata)
    }

    fun directoryTree(path: String, depth: Int = 3): List<String> = if (shell.isDirectory(path)) {
        shell.lines("find ${shell.quote(path)} -maxdepth $depth -print 2>/dev/null | sort").take(512)
    } else emptyList()

    fun archiveEntries(path: String): List<String>? {
        val tools = listOf("unzip", "toybox unzip", "busybox unzip")
        tools.forEach { tool ->
            val compact = shell.command("$tool -Z1 ${shell.quote(path)} 2>/dev/null")
            val compactEntries = compact.output.lineSequence().filter { it.isNotBlank() && !it.startsWith("Archive:") }.toList()
            if (compact.exitCode == 0 && compactEntries.isNotEmpty()) return compactEntries
            val long = shell.command("$tool -l ${shell.quote(path)} 2>/dev/null")
            val longEntries = parseLongZipListing(long.output)
            if (long.exitCode == 0 && longEntries.isNotEmpty()) return longEntries
        }
        return null
    }

    fun archiveEntry(path: String, entry: String): ByteArray? =
        listOf("unzip", "toybox unzip", "busybox unzip").firstNotNullOfOrNull { tool ->
            shell.bytes("$tool -p ${shell.quote(path)} ${shell.quote(entry)} 2>/dev/null")
        }

    companion object {
        /** Parses only filename rows from the portable `unzip -l` output; no archive extraction occurs. */
        fun parseLongZipListing(output: String): List<String> = output.lineSequence().mapNotNull { line ->
            Regex("^\\s*\\d+\\s+\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}\\s+(.+)$").matchEntire(line)?.groupValues?.getOrNull(1)?.trim()
        }.filter { it.isNotBlank() }.toList()
    }

    private fun metadata(path: String): RootFileMetadata {
        val type = if (shell.isDirectory(path)) "DIRECTORY" else shell.command("file -b ${shell.quote(path)} 2>/dev/null").output.ifBlank { "REGULAR_FILE" }
        val stat = shell.command("stat -c '%s|%u|%g|%a' ${shell.quote(path)} 2>/dev/null").output.split('|')
        val context = shell.command("ls -Zd ${shell.quote(path)} 2>/dev/null").output.substringBeforeLast(' ', "").trim().ifBlank { null }
        val sha = if (shell.isDirectory(path)) null else shell.command("sha256sum ${shell.quote(path)} 2>/dev/null | cut -d ' ' -f 1").output.trim().ifBlank { null }
        return RootFileMetadata(
            path = path,
            fileType = type,
            sizeBytes = stat.getOrNull(0)?.toLongOrNull(),
            uid = stat.getOrNull(1), gid = stat.getOrNull(2), mode = stat.getOrNull(3),
            sha256 = sha, selinuxContext = context
        )
    }
}
