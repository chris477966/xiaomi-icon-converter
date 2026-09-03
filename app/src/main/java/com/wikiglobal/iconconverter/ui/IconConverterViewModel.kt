package com.wikiglobal.iconconverter.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wikiglobal.iconconverter.compiler.XiaomiIconCompiler
import com.wikiglobal.iconconverter.hyperos.ThemeCompatibilityProbe
import com.wikiglobal.iconconverter.hyperos.ThemeCompatibilityReport
import com.wikiglobal.iconconverter.hyperos.FileThemeSessionStore
import com.wikiglobal.iconconverter.hyperos.HyperOs3IconReplacement
import com.wikiglobal.iconconverter.hyperos.HyperOs3ThemePatcher
import com.wikiglobal.iconconverter.hyperos.SuThemeRootExecutor
import com.wikiglobal.iconconverter.hyperos.ThemeBackupManager
import com.wikiglobal.iconconverter.hyperos.MonetGlyphRenderer
import com.wikiglobal.iconconverter.hyperos.MonetGlyphSource
import com.wikiglobal.iconconverter.hyperos.MonetPalette
import com.wikiglobal.iconconverter.hyperos.MonetPaletteReader
import com.wikiglobal.iconconverter.hyperos.MonochromeResolver
import com.wikiglobal.iconconverter.matcher.IconMatcher
import com.wikiglobal.iconconverter.model.IconMatch
import com.wikiglobal.iconconverter.model.IconPack
import com.wikiglobal.iconconverter.model.MatchStatus
import com.wikiglobal.iconconverter.model.XiaomiIconEntry
import com.wikiglobal.iconconverter.parser.IconPackParser
import com.wikiglobal.iconconverter.renderer.IconRenderer
import com.wikiglobal.iconconverter.repository.InstalledAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ThemeMode { ICON_PACK, MATERIAL_YOU }
data class MonetUiState(val palette: MonetPalette? = null, val dark: Boolean = false, val sources: Map<String, MonetGlyphSource> = emptyMap(), val generated: Map<String, ByteArray> = emptyMap()) {
    val available get() = palette != null
    fun sourceCount(source: MonetGlyphSource) = sources.values.count { it == source }
}

data class ConverterUiState(
    val loading: Boolean = true,
    val iconPack: IconPack? = null,
    val matches: List<IconMatch> = emptyList(),
    val launcherActivityCount: Int = 0,
    val uniquePackageCount: Int = 0,
    val diagnosticReport: ThemeCompatibilityReport? = null,
    val diagnosticRunning: Boolean = false,
    val diagnosticMessage: String? = null,
    val rootAvailable: Boolean = false,
    val themeOperationRunning: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.ICON_PACK,
    val monet: MonetUiState = MonetUiState(),
    val message: String? = null
) {
    val matchedCount get() = matches.count { it.status != MatchStatus.UNMATCHED && it.status != MatchStatus.CONFLICT }
    val unmatchedCount get() = matches.count { it.status == MatchStatus.UNMATCHED }
    val conflictCount get() = matches.count { it.status == MatchStatus.CONFLICT }
    val dynamicCalendarCount get() = matches.count { it.dynamicCalendar?.dayDrawables?.isNotEmpty() == true }
}

class IconConverterViewModel(application: Application) : AndroidViewModel(application) {
    private val parser = IconPackParser(application)
    private val appsRepository = InstalledAppsRepository(application)
    private val rootExecutor = SuThemeRootExecutor()
    private val backupManager = ThemeBackupManager(application.filesDir, rootExecutor, FileThemeSessionStore(java.io.File(application.filesDir, "theme-session.txt")))
    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    init { reloadApps() }

    /** Explicit only: app startup never asks for su authorization. */
    fun checkRoot() = viewModelScope.launch {
        val available = withContext(Dispatchers.IO) { rootExecutor.isRootAvailable() }
        _uiState.value = _uiState.value.copy(rootAvailable = available, message = if (available) "Root 可用" else "Root 不可用")
    }

    fun selectThemeMode(mode: ThemeMode) { _uiState.value = _uiState.value.copy(themeMode = mode) }

    /** Previews exactly the 250px PNGs that Material You Apply will patch into the current base archive. */
    fun generateMonetPreview() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, message = null)
        runCatching { withContext(Dispatchers.Default) {
            val palette = MonetPaletteReader.read() ?: error("当前系统 Monet 调色板不可用")
            val dark = (getApplication<Application>().resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val sources = linkedMapOf<String, MonetGlyphSource>(); val pngs = linkedMapOf<String, ByteArray>(); val pack = _uiState.value.iconPack
            _uiState.value.matches.forEach { match ->
                val packGlyph = match.drawableName?.let { pack?.drawableLoader(it) }
                val (glyph, source) = MonochromeResolver.resolve(match.app.originalIcon, packGlyph)
                val key = match.app.packageName + "#" + match.app.launcherActivity
                sources[key] = source
                if (glyph != null) pngs[key] = MonetGlyphRenderer.render(glyph, palette, dark)
            }
            MonetUiState(palette, dark, sources, pngs)
        } }.onSuccess { monet -> _uiState.value = _uiState.value.copy(loading = false, themeMode = ThemeMode.MATERIAL_YOU, monet = monet, message = "已生成 ${monet.generated.size} 个 Material You 预览") }
            .onFailure { error -> _uiState.value = _uiState.value.copy(loading = false, message = error.message ?: "Material You 预览失败") }
    }

    fun applyMonetToSystem() = viewModelScope.launch {
        val state = _uiState.value
        _uiState.value = state.copy(themeOperationRunning = true, message = null)
        runCatching { withContext(Dispatchers.IO) {
            check(rootExecutor.isRootAvailable()) { "Root 不可用，未修改系统主题" }
            val monet = state.monet.takeIf { it.available && it.generated.isNotEmpty() } ?: error("请先生成 Material You 预览")
            val base = java.io.File(getApplication<Application>().filesDir, "staging/base-icons.zip").also { it.parentFile?.mkdirs() }
            val original = rootExecutor.inspect(SuThemeRootExecutor.ACTIVE_ICONS) ?: error("无法读取当前主题 icons")
            check(rootExecutor.copySystemFileTo(SuThemeRootExecutor.ACTIVE_ICONS, base).success) { "无法读取当前主题 icons" }
            check(HyperOs3ThemePatcher.sha256(base) == original.sha256) { "当前主题 SHA 校验失败" }
            val replacements = state.matches.mapNotNull { match -> monet.generated[match.app.packageName + "#" + match.app.launcherActivity]?.let { HyperOs3IconReplacement(match.app.packageName, it) } }.distinctBy { it.entryName }
            check(replacements.isNotEmpty()) { "没有可应用的 Material You 图标" }
            val patched = java.io.File(getApplication<Application>().filesDir, "staging/patched-monet-icons.zip")
            HyperOs3ThemePatcher.patch(base, patched, replacements)
            backupManager.install(patched, "system-monet", "MATERIAL_YOU", monet.palette!!.hash()).getOrThrow()
            replacements.size
        } }.onSuccess { count -> _uiState.value = _uiState.value.copy(themeOperationRunning = false, message = "已原子应用 $count 个 Material You 图标") }
            .onFailure { error -> _uiState.value = _uiState.value.copy(themeOperationRunning = false, message = error.message ?: "Material You 应用失败") }
    }

    private fun reloadApps() = viewModelScope.launch {
        val apps = withContext(Dispatchers.IO) { appsRepository.launcherApps() }
        _uiState.value = _uiState.value.copy(
            loading = false,
            launcherActivityCount = apps.size,
            uniquePackageCount = apps.map { it.packageName }.distinct().size,
            matches = apps.map { IconMatch(it, MatchStatus.UNMATCHED, com.wikiglobal.iconconverter.model.MatchConfidence.NONE) }
        )
    }

    fun selectApk(uri: Uri) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, message = null)
        runCatching {
            withContext(Dispatchers.IO) {
                val pack = parser.parse(uri)
                val apps = appsRepository.launcherApps()
                Triple(pack, apps, IconMatcher.match(apps, pack.mappings, pack.calendars))
            }
        }.onSuccess { (pack, apps, matches) ->
            _uiState.value = ConverterUiState(
                loading = false,
                iconPack = pack,
                matches = matches,
                launcherActivityCount = apps.size,
                uniquePackageCount = apps.map { it.packageName }.distinct().size
            )
        }.onFailure { error ->
            _uiState.value = _uiState.value.copy(loading = false, message = error.message ?: "解析 APK 失败")
        }
    }

    fun generateTo(uri: Uri) = viewModelScope.launch {
        val state = _uiState.value; val pack = state.iconPack ?: return@launch
        _uiState.value = state.copy(loading = true, message = null)
        runCatching {
            withContext(Dispatchers.IO) {
                val named = state.matches.filter { it.drawableName != null && it.status != MatchStatus.CONFLICT }
                val packageDrawableCounts = named.groupBy { it.app.packageName }.mapValues { (_, values) -> values.mapNotNull { it.drawableName }.distinct().size }
                val entries = named.mapNotNull { match ->
                    pack.drawableLoader(match.drawableName!!)?.let { drawable ->
                        XiaomiIconEntry(match.app.packageName, match.app.launcherActivity, IconRenderer.renderPng(drawable), packageDrawableCounts.getValue(match.app.packageName) > 1)
                    }
                }
                require(entries.isNotEmpty()) { "没有可生成的已匹配图标" }
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { XiaomiIconCompiler.writeIcons(it, entries) }
                    ?: error("无法写入所选文件")
                entries.size
            }
        }.onSuccess { count -> _uiState.value = _uiState.value.copy(loading = false, message = "已生成 icons（$count 个图标）") }
            .onFailure { error -> _uiState.value = _uiState.value.copy(loading = false, message = error.message ?: "生成失败") }
    }

    /** The only path that can invoke root writes; it is deliberately wired to the explicit Apply button. */
    fun applyIconPackToSystem() = viewModelScope.launch {
        val state = _uiState.value; val pack = state.iconPack ?: return@launch
        _uiState.value = state.copy(themeOperationRunning = true, message = null)
        runCatching { withContext(Dispatchers.IO) {
            check(rootExecutor.isRootAvailable()) { "Root 不可用，未修改系统主题" }
            val base = java.io.File(getApplication<Application>().filesDir, "staging/base-icons.zip").also { it.parentFile?.mkdirs() }
            val original = rootExecutor.inspect(SuThemeRootExecutor.ACTIVE_ICONS) ?: error("无法读取当前主题 icons")
            check(rootExecutor.copySystemFileTo(SuThemeRootExecutor.ACTIVE_ICONS, base).success) { "无法读取当前主题 icons" }
            check(HyperOs3ThemePatcher.sha256(base) == original.sha256) { "当前主题 SHA 校验失败" }
            val rendered = state.matches.filter { it.drawableName != null && it.status != MatchStatus.CONFLICT }.mapNotNull { match ->
                pack.drawableLoader(match.drawableName!!)?.let { drawable -> Triple(match, match.drawableName, IconRenderer.renderPng(drawable, HyperOs3ThemePatcher.ICON_SIZE)) }
            }
            val replacements = rendered.groupBy { it.first.app.packageName }.flatMap { (packageName, icons) ->
                val distinctDrawables = icons.map { it.second }.distinct().size
                icons.flatMapIndexed { index, (match, _, png) -> buildList {
                    if (index == 0) add(HyperOs3IconReplacement(packageName, png))
                    if (distinctDrawables > 1) add(HyperOs3IconReplacement(packageName, png, XiaomiIconCompiler.activityPart(match.app.launcherActivity, packageName)))
                } }
            }.distinctBy { it.entryName }
            check(replacements.isNotEmpty()) { "没有可应用的匹配图标" }
            val patched = java.io.File(getApplication<Application>().filesDir, "staging/patched-icons.zip")
            HyperOs3ThemePatcher.patch(base, patched, replacements)
            backupManager.install(patched, pack.packageName, "ICON_PACK", null).getOrThrow()
            replacements.size
        } }.onSuccess { count -> _uiState.value = _uiState.value.copy(themeOperationRunning = false, message = "已原子应用 $count 个图标；可选择刷新桌面") }
            .onFailure { error -> _uiState.value = _uiState.value.copy(themeOperationRunning = false, message = error.message ?: "应用失败；当前主题未被覆盖") }
    }

    fun restoreOriginalTheme() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(themeOperationRunning = true, message = null)
        val result = withContext(Dispatchers.IO) { backupManager.restore() }
        _uiState.value = if (result.isSuccess) _uiState.value.copy(themeOperationRunning = false, message = "原主题已安全恢复") else _uiState.value.copy(themeOperationRunning = false, message = result.exceptionOrNull()?.message ?: "恢复失败")
    }

    fun refreshLauncher() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(themeOperationRunning = true, message = null)
        val result = withContext(Dispatchers.IO) { rootExecutor.refreshLauncher() }
        _uiState.value = _uiState.value.copy(themeOperationRunning = false, message = if (result.success) "已请求刷新桌面" else "刷新桌面失败：${result.message}")
    }

    fun checkHyperOsCompatibility() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(diagnosticRunning = true, diagnosticMessage = null)
        runCatching { withContext(Dispatchers.IO) { ThemeCompatibilityProbe(getApplication()).run() } }
            .onSuccess { report -> _uiState.value = _uiState.value.copy(diagnosticRunning = false, diagnosticReport = report, diagnosticMessage = "HyperOS 兼容性探针已完成") }
            .onFailure { error -> _uiState.value = _uiState.value.copy(diagnosticRunning = false, diagnosticMessage = error.message ?: "兼容性检测失败") }
    }

    fun exportDiagnosticReport(uri: Uri) = viewModelScope.launch {
        val report = _uiState.value.diagnosticReport ?: return@launch
        runCatching {
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(report.toText()) }
                    ?: error("无法写入诊断报告")
            }
        }.onSuccess { _uiState.value = _uiState.value.copy(diagnosticMessage = "诊断报告已导出") }
            .onFailure { error -> _uiState.value = _uiState.value.copy(diagnosticMessage = error.message ?: "导出失败") }
    }
}
