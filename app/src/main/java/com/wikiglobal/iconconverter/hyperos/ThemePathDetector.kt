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
        val result = shell.command("unzip -Z1 ${shell.quote(path)} 2>/dev/null")
        return if (result.exitCode == 0) result.output.lineSequence().filter { it.isNotBlank() }.toList() else null
    }

    fun archiveEntry(path: String, entry: String): ByteArray? =
        shell.bytes("unzip -p ${shell.quote(path)} ${shell.quote(entry)} 2>/dev/null")

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
