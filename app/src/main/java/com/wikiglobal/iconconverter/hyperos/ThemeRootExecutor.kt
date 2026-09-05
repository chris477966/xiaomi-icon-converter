package com.wikiglobal.iconconverter.hyperos

import java.io.File

/** System writes are exposed only by this narrow interface and are invoked exclusively from an explicit Apply/Restore action. */
data class ThemeFileMetadata(val sha256: String, val size: Long, val uid: Int, val gid: Int, val mode: String, val selinuxContext: String)
data class ThemeMetadataDiff(
    val shaMatch: Boolean,
    val uidMatch: Boolean,
    val gidMatch: Boolean,
    val modeMatch: Boolean,
    val contextMatch: Boolean,
    val expected: ThemeFileMetadata,
    val actual: ThemeFileMetadata?
) {
    val allMatch: Boolean get() = shaMatch && uidMatch && gidMatch && modeMatch && contextMatch
}

/** Pure comparison seam: install and rollback retain all five metadata requirements. */
object ThemeMetadataVerifier {
    fun compare(expectedSha: String, expectedMetadata: ThemeFileMetadata, actual: ThemeFileMetadata?): ThemeMetadataDiff = ThemeMetadataDiff(
        shaMatch = actual?.sha256 == expectedSha,
        uidMatch = actual?.uid == expectedMetadata.uid,
        gidMatch = actual?.gid == expectedMetadata.gid,
        modeMatch = actual?.mode == expectedMetadata.mode,
        contextMatch = actual?.selinuxContext == expectedMetadata.selinuxContext,
        expected = expectedMetadata,
        actual = actual
    )

    fun diagnostic(diff: ThemeMetadataDiff): String = if (diff.actual == null) {
        "sha=false\nuid=false\ngid=false\nmode=false\ncontext=false\nactual=null"
    } else {
        "sha=${diff.shaMatch}\nuid=${diff.uidMatch}\ngid=${diff.gidMatch}\nmode=${diff.modeMatch}\n" +
            "context=${diff.contextMatch}\nexpectedContext=${diff.expected.selinuxContext}\nactualContext=${diff.actual.selinuxContext}"
    }
}

enum class RootOperationFailure { COPY_FAIL, STAGED_SHA_FAIL, MV_FAIL, CHOWN_FAIL, CHMOD_FAIL, RESTORECON_FAIL, CHCON_FAIL, POST_INSTALL_METADATA_MISMATCH }
enum class RootOperationWarning { RESTORECON_NONZERO_BUT_CONTEXT_VALID }
data class RootOperation(
    val success: Boolean,
    val message: String = "",
    val failure: RootOperationFailure? = null,
    val warnings: List<RootOperationWarning> = emptyList(),
    /** True only when a failed operation may already have replaced the target. */
    val targetMayHaveChanged: Boolean = false
)

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
    override fun copySystemFileTo(source: String, destination: File): RootOperation = run("cat ${q(source)} > ${q(destination.absolutePath)}", RootOperationFailure.COPY_FAIL)
    override fun atomicInstall(localArchive: File, target: String, original: ThemeFileMetadata): RootOperation {
        require(target == ACTIVE_ICONS) { "Only the active icons archive may be changed" }
        val staged = "/data/system/theme/.xiaomi_icon_converter_icons.new"
        val expectedSha = HyperOs3ThemePatcher.sha256(localArchive)
        val context = q(original.selinuxContext)
        val targetPath = q(target)
        val command = """
            cat ${q(localArchive.absolutePath)} > $staged || { echo ROOT_FAILURE:COPY_FAIL; exit 1; }
            test \"${'$'}(sha256sum $staged | awk '{print ${'$'}1}')\" = \"$expectedSha\" || { echo ROOT_FAILURE:STAGED_SHA_FAIL; exit 1; }
            mv $staged $targetPath || { echo ROOT_FAILURE:MV_FAIL; exit 1; }
            chown ${original.uid}:${original.gid} $targetPath || { echo ROOT_FAILURE:CHOWN_FAIL; exit 1; }
            chmod ${original.mode} $targetPath || { echo ROOT_FAILURE:CHMOD_FAIL; exit 1; }
            restorecon $targetPath
            restorecon_status=${'$'}?
            actual_context=${'$'}(ls -Zd $targetPath | awk '{print ${'$'}1}')
            if [ \"${'$'}actual_context\" != $context ]; then
              chcon $context $targetPath || { echo ROOT_FAILURE:CHCON_FAIL; exit 1; }
              actual_context=${'$'}(ls -Zd $targetPath | awk '{print ${'$'}1}')
            fi
            test \"${'$'}actual_context\" = $context || { echo ROOT_FAILURE:POST_INSTALL_METADATA_MISMATCH; exit 1; }
            if [ "${'$'}restorecon_status" -ne 0 ]; then echo ROOT_WARNING:RESTORECON_NONZERO_BUT_CONTEXT_VALID; fi
            sync
        """.trimIndent()
        val result = run(command)
        if (!result.success) run("rm -f $staged")
        val mayHaveChanged = result.failure in setOf(
            RootOperationFailure.CHOWN_FAIL,
            RootOperationFailure.CHMOD_FAIL,
            RootOperationFailure.CHCON_FAIL,
            RootOperationFailure.POST_INSTALL_METADATA_MISMATCH
        )
        return result.copy(targetMayHaveChanged = mayHaveChanged)
    }
    /** User-triggered only. This never force-stops Launcher. */
    override fun refreshIconCache(): RootOperation = run("am broadcast -a miui.intent.action.THEME_CHANGED")
    /** User-triggered only, shown as a separate operation when cache refresh did not take effect. */
    override fun forceStopLauncher(): RootOperation = run("am force-stop com.miui.home")
    private fun run(command: String, fallback: RootOperationFailure? = null): RootOperation = runCatching {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText().trim()
        val success = process.waitFor() == 0
        val failure = if (success) null else RootOperationFailure.entries.firstOrNull { text.lineSequence().any { line -> line.trim() == "ROOT_FAILURE:$it" } } ?: fallback
        val warnings = RootOperationWarning.entries.filter { warning -> text.lineSequence().any { line -> line.trim() == "ROOT_WARNING:$warning" } }
        RootOperation(success, text, failure, warnings)
    }.getOrElse { RootOperation(false, it.message ?: "root command failed", fallback) }
    private fun q(value: String) = "'" + value.replace("'", "'\\''") + "'"
    companion object { const val ACTIVE_ICONS = "/data/system/theme/icons" }
}
