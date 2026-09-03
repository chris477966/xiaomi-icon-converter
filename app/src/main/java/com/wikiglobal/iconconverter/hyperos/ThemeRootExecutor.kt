package com.wikiglobal.iconconverter.hyperos

import java.io.File

/** System writes are exposed only by this narrow interface and are invoked exclusively from an explicit Apply/Restore action. */
data class ThemeFileMetadata(val sha256: String, val size: Long, val uid: Int, val gid: Int, val mode: String, val selinuxContext: String)
data class RootOperation(val success: Boolean, val message: String = "")

interface ThemeRootExecutor {
    fun isRootAvailable(): Boolean
    fun inspect(path: String): ThemeFileMetadata?
    fun copySystemFileTo(source: String, destination: File): RootOperation
    fun atomicInstall(localArchive: File, target: String, original: ThemeFileMetadata): RootOperation
    fun refreshIconCache(): RootOperation
    fun forceStopLauncher(): RootOperation
}

/** Real root executor. It never runs on creation: callers must invoke it after a user action. */
class SuThemeRootExecutor : ThemeRootExecutor {
    override fun isRootAvailable(): Boolean = run("id").success
    override fun inspect(path: String): ThemeFileMetadata? {
        val result = run("sha256sum ${q(path)}; stat -c '%s %u %g %a' ${q(path)}; ls -Zd ${q(path)}")
        if (!result.success) return null
        val lines = result.message.lines().filter { it.isNotBlank() }; if (lines.size < 3) return null
        val hash = lines[0].substringBefore(' ').trim(); val stat = lines[1].trim().split(Regex("\\s+")); val context = lines[2].trim().substringBefore(' ')
        return stat.takeIf { it.size >= 4 }?.let { ThemeFileMetadata(hash, it[0].toLong(), it[1].toInt(), it[2].toInt(), it[3], context) }
    }
    override fun copySystemFileTo(source: String, destination: File): RootOperation = run("cat ${q(source)} > ${q(destination.absolutePath)}")
    override fun atomicInstall(localArchive: File, target: String, original: ThemeFileMetadata): RootOperation {
        require(target == ACTIVE_ICONS) { "Only the active icons archive may be changed" }
        val staged = "/data/system/theme/.xiaomi_icon_converter_icons.new"
        val command = "cat ${q(localArchive.absolutePath)} > $staged && " +
            "test \"${'$'}(sha256sum $staged | awk '{print ${'$'}1}')\" = \"${HyperOs3ThemePatcher.sha256(localArchive)}\" && " +
            "chown ${original.uid}:${original.gid} $staged && chmod ${original.mode} $staged && " +
            "(restorecon $staged || chcon ${q(original.selinuxContext)} $staged) && sync && mv $staged ${q(target)}"
        val result = run(command)
        if (!result.success) run("rm -f $staged")
        return result
    }
    /** User-triggered only. This never force-stops Launcher. */
    override fun refreshIconCache(): RootOperation = run("am broadcast -a miui.intent.action.THEME_CHANGED")
    /** User-triggered only, shown as a separate operation when cache refresh did not take effect. */
    override fun forceStopLauncher(): RootOperation = run("am force-stop com.miui.home")
    private fun run(command: String): RootOperation = runCatching {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start(); val text = process.inputStream.bufferedReader().readText(); RootOperation(process.waitFor() == 0, text.trim())
    }.getOrElse { RootOperation(false, it.message ?: "root command failed") }
    private fun q(value: String) = "'" + value.replace("'", "'\\''") + "'"
    companion object { const val ACTIVE_ICONS = "/data/system/theme/icons" }
}
