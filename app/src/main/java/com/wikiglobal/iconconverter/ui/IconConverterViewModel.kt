package com.wikiglobal.iconconverter.ui

import android.app.Application
import android.net.Uri
import android.graphics.drawable.AdaptiveIconDrawable
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
import com.wikiglobal.iconconverter.hyperos.ThemeOwnershipState
import com.wikiglobal.iconconverter.hyperos.HyperOsThemeProfile
import com.wikiglobal.iconconverter.hyperos.HyperOsThemeProfileDetector
import com.wikiglobal.iconconverter.hyperos.HyperOsThemeEntryResolver
import com.wikiglobal.iconconverter.hyperos.HyperOsThemeReplacementPlanner
import com.wikiglobal.iconconverter.hyperos.RenderedThemeActivityIcon
import com.wikiglobal.iconconverter.hyperos.ThemeApplicationPlan
import com.wikiglobal.iconconverter.hyperos.ThemeApplicationPlanVerifier
import com.wikiglobal.iconconverter.hyperos.ThemeApplyCompletion
import com.wikiglobal.iconconverter.hyperos.IconPackStyle
import com.wikiglobal.iconconverter.hyperos.IconPackStyleStore
import com.wikiglobal.iconconverter.hyperos.MonetGlyphRenderer
import com.wikiglobal.iconconverter.hyperos.MonetGlyphSource
import com.wikiglobal.iconconverter.hyperos.MonetPalette
import com.wikiglobal.iconconverter.hyperos.MonetPaletteReader
import com.wikiglobal.iconconverter.hyperos.MonochromeResolver
import com.wikiglobal.iconconverter.hyperos.RawAppIconResolver
import com.wikiglobal.iconconverter.hyperos.RawIconCandidate
import com.wikiglobal.iconconverter.hyperos.RawIconSource
import com.wikiglobal.iconconverter.hyperos.LawniconsThemedProvider
import com.wikiglobal.iconconverter.hyperos.LawniconsProviderState
import com.wikiglobal.iconconverter.hyperos.LawniconsProviderStatus
import com.wikiglobal.iconconverter.hyperos.MonetGlyphResult
import com.wikiglobal.iconconverter.hyperos.MaterialOverrideStore
import com.wikiglobal.iconconverter.hyperos.MaterialSourceOverride
import com.wikiglobal.iconconverter.hyperos.AospMonochromeGenerator
import com.wikiglobal.iconconverter.hyperos.MaterialCandidateDiscovery
import com.wikiglobal.iconconverter.hyperos.MaterialSourcePriority
import com.wikiglobal.iconconverter.hyperos.MaterialStyle
import com.wikiglobal.iconconverter.hyperos.MaterialStyleStore
import com.wikiglobal.iconconverter.hyperos.MaterialColorMode
import com.wikiglobal.iconconverter.hyperos.MaterialPaletteFactory
import com.wikiglobal.iconconverter.hyperos.MaterialStyledGlyphRenderer
import com.wikiglobal.iconconverter.hyperos.MaterialGlyphCache
import com.wikiglobal.iconconverter.hyperos.MaterialPaletteResolver
import com.wikiglobal.iconconverter.hyperos.MaterialPaletteResolution
import com.wikiglobal.iconconverter.hyperos.MaterialInstalledStateStore
import com.wikiglobal.iconconverter.hyperos.MaterialInstalledState
import com.wikiglobal.iconconverter.hyperos.AutoRecolorStatus
import com.wikiglobal.iconconverter.hyperos.MaterialPreviewRefreshPolicy
import com.wikiglobal.iconconverter.hyperos.SystemMonetChangeNotifier
import com.wikiglobal.iconconverter.matcher.IconMatcher
import com.wikiglobal.iconconverter.model.IconMatch
import com.wikiglobal.iconconverter.model.IconPack
import com.wikiglobal.iconconverter.model.MatchStatus
import com.wikiglobal.iconconverter.model.XiaomiIconEntry
import com.wikiglobal.iconconverter.parser.IconPackParser
import com.wikiglobal.iconconverter.renderer.IconRenderer
import com.wikiglobal.iconconverter.renderer.AdaptiveIconPackRenderer
import com.wikiglobal.iconconverter.hyperos.IconShape
import com.wikiglobal.iconconverter.repository.InstalledAppsRepository
import com.wikiglobal.iconconverter.autoadapt.ActiveThemeMode
import com.wikiglobal.iconconverter.autoadapt.ActiveThemeModeStore
import com.wikiglobal.iconconverter.autoadapt.AutoAdaptDryRunStore
import com.wikiglobal.iconconverter.autoadapt.AutoAdaptSettingsStore
import com.wikiglobal.iconconverter.autoadapt.AutoAdaptSummary
import com.wikiglobal.iconconverter.autoadapt.SelectedIconPackMetadata
import com.wikiglobal.iconconverter.autoadapt.SelectedIconPackStore
import com.wikiglobal.iconconverter.autoadapt.WorkManagerAutoAdaptScheduler
import com.wikiglobal.iconconverter.autoadapt.PendingPackageStore
import com.wikiglobal.iconconverter.autoadapt.AutoAdaptDisableController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ThemeMode { ICON_PACK, MATERIAL_YOU }
data class MaterialSourceAvailability(val native: Boolean = false, val lawnicons: Boolean = false, val aosp: Boolean = false, val lawniconsProviderStatus: LawniconsProviderStatus = LawniconsProviderStatus.UNINITIALIZED) {
    fun supports(override: MaterialSourceOverride) = when (override) {
        MaterialSourceOverride.AUTO, MaterialSourceOverride.KEEP -> true
        MaterialSourceOverride.NATIVE -> native
        MaterialSourceOverride.LAWNICONS -> lawnicons
        MaterialSourceOverride.AOSP_FORCE -> aosp
    }
}
data class MaterialSourceDiagnostic(
    val packageName: String, val launcherActivity: String,
    val rawActivityType: String, val rawApplicationType: String,
    val nativeAvailable: Boolean, val nativeCandidateSource: String?,
    val lawniconsProviderStatus: LawniconsProviderStatus, val lawniconsMapped: Boolean, val lawniconsDrawableId: Int,
    val aospAdaptiveAvailable: Boolean, val aospCandidateSource: String?, val finalSource: MonetGlyphSource
)
data class MonetUiState(val generationId: Long = 0, val palette: MonetPalette? = null, val dark: Boolean = false, val sources: Map<String, MonetGlyphSource> = emptyMap(), val generated: Map<String, ByteArray> = emptyMap(), val availability: Map<String, MaterialSourceAvailability> = emptyMap(), val overrides: Map<String, MaterialSourceOverride> = emptyMap(), val diagnostics: Map<String, MaterialSourceDiagnostic> = emptyMap(), val lawniconsProvider: LawniconsProviderState = LawniconsProviderState(), val style: MaterialStyle = MaterialStyle(), val timestamp: Long = 0, val previewBitmaps: Map<String, android.graphics.Bitmap> = emptyMap(), val paletteResolution: MaterialPaletteResolution? = null, val previewTargetSize: Int = HyperOsThemeProfileDetector.FALLBACK_SIZE) {
    val available get() = palette != null
    fun sourceCount(source: MonetGlyphSource) = sources.values.count { it == source }
}
enum class LauncherRefreshStatus { SUCCESS, FAILED }
data class ThemeApplySummary(val mode: String, val generatedComponents: Int, val plannedComponents: Int, val existingActivityRouteComponents: Int, val packageOnlyComponents: Int, val existingActivityEntriesMatched: Int, val legacyAliasComponents: Int, val packageBaseEntries: Int, val activityAliasEntries: Int, val totalReplacements: Int, val unroutedComponents: Int, val entryConflicts: Int, val patchVerified: Boolean, val archiveInstallVerified: Boolean, val launcherRefreshStatus: LauncherRefreshStatus)
data class ThemeRouteDiagnostic(
    val packageName: String,
    val launcherActivity: String,
    val targetActivity: String?,
    val currentActivityEntry: String,
    val currentActivityEntryExisted: Boolean,
    val directMatchedEntries: List<String>,
    val targetFallbackMatchedEntries: List<String>,
    val legacyThemeAliasEntries: List<String>,
    val legacyAliasStatus: String,
    val finalReplacementEntries: List<String>
)
private data class AppliedThemeResult(val plan: ThemeApplicationPlan, val archiveSha: String, val refreshStatus: LauncherRefreshStatus)

data class ConverterUiState(
    val loading: Boolean = true,
    val iconPack: IconPack? = null,
    val matches: List<IconMatch> = emptyList(),
    val iconPreviews: IconPackPreviewBitmaps = IconPackPreviewBitmaps(),
    val launcherActivityCount: Int = 0,
    val uniquePackageCount: Int = 0,
    val diagnosticReport: ThemeCompatibilityReport? = null,
    val diagnosticRunning: Boolean = false,
    val diagnosticMessage: String? = null,
    val rootAvailable: Boolean = false,
    val themeOperationRunning: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.ICON_PACK,
    val iconPackStyle: IconPackStyle = IconPackStyle(),
    val themeOwnership: ThemeOwnershipState = ThemeOwnershipState.Unmanaged,
    val themeProfile: HyperOsThemeProfile? = null,
    val themeBaseSha: String? = null,
    val rebaseConfirmationRequired: Boolean = false,
    val monet: MonetUiState = MonetUiState(),
    val lastApplySummary: ThemeApplySummary? = null,
    val lastApplyRoutes: List<ThemeRouteDiagnostic> = emptyList(),
    val autoAdaptEnabled: Boolean = false,
    val autoAdaptSummary: AutoAdaptSummary = AutoAdaptSummary(),
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
    private val rawIconResolver = RawAppIconResolver(application)
    private val lawniconsProvider = LawniconsThemedProvider(application)
    private val overrideStore = MaterialOverrideStore(application)
    private val styleStore = MaterialStyleStore(application)
    private val iconPackStyleStore = IconPackStyleStore(application)
    private val paletteResolver = MaterialPaletteResolver(application)
    private val materialGlyphCache = linkedMapOf<String, MonetGlyphResult>()
    private val glyphDiskCache = MaterialGlyphCache(application)
    private val installedMaterialStore = MaterialInstalledStateStore(application)
    private val previewRefreshMutex = Mutex()
    private val backupManager = ThemeBackupManager(application.filesDir, rootExecutor, FileThemeSessionStore(java.io.File(application.filesDir, "theme-session.txt")))
    private val activeThemeModeStore = ActiveThemeModeStore(application)
    private val selectedIconPackStore = SelectedIconPackStore(application)
    private val autoAdaptSettingsStore = AutoAdaptSettingsStore(application)
    private val autoAdaptDryRunStore = AutoAdaptDryRunStore(application)
    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    init {
        reloadApps()
        _uiState.value = _uiState.value.copy(autoAdaptEnabled = autoAdaptSettingsStore.enabled(), autoAdaptSummary = autoAdaptDryRunStore.summary())
        viewModelScope.launch {
            SystemMonetChangeNotifier.changes.collect {
                refreshSystemMonetPreviewIfChanged()
            }
        }
    }

    /** Explicit only: app startup never asks for su authorization. */
    fun checkRoot() = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            val available = rootExecutor.isRootAvailable()
            if (!available) Triple(false, ThemeOwnershipState.Unmanaged, null) else {
                val profileFile = java.io.File(getApplication<Application>().filesDir, "staging/profile-icons.zip").also { it.parentFile?.mkdirs() }
                val profile = rootExecutor.copySystemFileTo(SuThemeRootExecutor.ACTIVE_ICONS, profileFile).takeIf { it.success }?.let { HyperOsThemeProfileDetector.detect(profileFile) }
                Triple(true, backupManager.ownershipState(), profile)
            }
        }
        val current = _uiState.value
        // A failed refresh must not discard an already confirmed target profile and
        // silently return its canonical previews to the 250px fallback.
        val resolvedProfile = result.third ?: current.themeProfile
        val targetSize = resolvedProfile?.staticIconSize ?: HyperOsThemeProfileDetector.FALLBACK_SIZE
        val rerendered = withContext(Dispatchers.Default) { rerenderCachedMaterial(current.monet, targetSize) }
        _uiState.value = current.copy(rootAvailable = result.first, themeOwnership = result.second, themeProfile = resolvedProfile, themeBaseSha = backupManager.currentSession()?.original?.sha256, monet = rerendered, message = if (result.first) "Root 可用" else "Root 不可用")
    }

    fun selectThemeMode(mode: ThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
        if (mode == ThemeMode.MATERIAL_YOU) refreshSystemMonetPreviewIfChanged()
    }

    private data class ResolvedMaterialComponent(
        val key: String,
        val glyph: MonetGlyphResult,
        val availability: MaterialSourceAvailability,
        val override: MaterialSourceOverride,
        val diagnostic: MaterialSourceDiagnostic
    )

    /** Previews canonical PNGs at the known baseline target size, with a transparent 250 fallback before Root inspection. */
    fun generateMonetPreview() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, message = null)
        runCatching { withContext(Dispatchers.Default) {
            val style = styleStore.get()
            val resolution = paletteResolver.resolve(style) ?: error("当前系统/壁纸 Monet 调色板不可用")
            val palette = resolution.palette
            val dark = (getApplication<Application>().resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val targetSize = _uiState.value.themeProfile?.staticIconSize ?: HyperOsThemeProfileDetector.FALLBACK_SIZE
            val sources = linkedMapOf<String, MonetGlyphSource>()
            val pngs = linkedMapOf<String, ByteArray>()
            val availability = linkedMapOf<String, MaterialSourceAvailability>()
            val overrides = linkedMapOf<String, MaterialSourceOverride>()
            val diagnostics = linkedMapOf<String, MaterialSourceDiagnostic>()
            val providerState = lawniconsProvider.load()
            _uiState.value.matches.forEach { match ->
                val resolved = resolveMaterialComponent(match)
                sources[resolved.key] = resolved.glyph.source
                availability[resolved.key] = resolved.availability
                overrides[resolved.key] = resolved.override
                diagnostics[resolved.key] = resolved.diagnostic
                materialGlyphCache[resolved.key] = resolved.glyph
                glyphDiskCache.save(resolved.key, resolved.glyph)
                MaterialStyledGlyphRenderer.render(resolved.glyph, palette, dark, style.shape, targetSize)?.let { pngs[resolved.key] = it }
            }
            MonetUiState(
                System.nanoTime(), palette, dark, sources, pngs, availability, overrides,
                diagnostics, providerState, style, System.currentTimeMillis(),
                PreviewBitmapPipeline.decodeMaterialPngs(pngs, targetSize),
                resolution,
                targetSize
            )
        } }.onSuccess { monet -> _uiState.value = _uiState.value.copy(loading = false, themeMode = ThemeMode.MATERIAL_YOU, monet = monet, message = "已生成 ${monet.generated.size} 个 Material You 预览") }
            .onFailure { error -> _uiState.value = _uiState.value.copy(loading = false, message = error.message ?: "Material You 预览失败") }
    }

    /** Saves a user choice and only replaces that component's source/PNG. Existing previews remain byte-identical. */
    fun setMaterialOverride(componentKey: String, override: MaterialSourceOverride) {
        overrideStore.set(componentKey, override)
        regenerateMonetComponent(componentKey)
    }

    fun regenerateMonetComponent(componentKey: String) = viewModelScope.launch {
        val snapshot = _uiState.value
        val monet = snapshot.monet.takeIf { it.available } ?: return@launch
        val match = snapshot.matches.firstOrNull { componentKey(it) == componentKey } ?: return@launch
        runCatching { withContext(Dispatchers.Default) {
            val providerState = lawniconsProvider.load()
            val resolved = resolveMaterialComponent(match)
            val sources = monet.sources.toMutableMap().apply { put(componentKey, resolved.glyph.source) }
            val availability = monet.availability.toMutableMap().apply { put(componentKey, resolved.availability) }
            val overrides = monet.overrides.toMutableMap().apply { put(componentKey, resolved.override) }
            val diagnostics = monet.diagnostics.toMutableMap().apply { put(componentKey, resolved.diagnostic) }
            val generated = monet.generated.toMutableMap().apply {
                materialGlyphCache[componentKey] = resolved.glyph
                glyphDiskCache.save(componentKey, resolved.glyph)
                MaterialStyledGlyphRenderer.render(resolved.glyph, monet.palette!!, monet.dark, monet.style.shape, monet.previewTargetSize)?.let { put(componentKey, it) } ?: remove(componentKey)
            }
            val previews = monet.previewBitmaps.toMutableMap().apply {
                generated[componentKey]?.let { PreviewBitmapPipeline.decodeMaterialPng(it, monet.previewTargetSize) }?.let { put(componentKey, it) } ?: remove(componentKey)
            }
            monet.copy(generationId = System.nanoTime(), sources = sources, generated = generated, previewBitmaps = previews, availability = availability, overrides = overrides, diagnostics = diagnostics, lawniconsProvider = providerState, timestamp = System.currentTimeMillis())
        } }.onSuccess { updated ->
            _uiState.value = _uiState.value.copy(monet = updated, message = "已更新该应用的 Material You 预览")
        }.onFailure { error -> _uiState.value = _uiState.value.copy(message = error.message ?: "更新 Material You 预览失败") }
    }

    private fun resolveMaterialComponent(match: IconMatch): ResolvedMaterialComponent {
        val key = componentKey(match)
        val candidates = rawIconResolver.candidates(match.app)
        val nativeCandidate = MaterialCandidateDiscovery.firstAvailable(candidates.inPriorityOrder.map { candidate ->
            candidate.source to MonochromeResolver.nativeOrNull(candidate.drawable)?.takeIf { it.alphaMask != null }
        })
        val native = nativeCandidate?.second
        val themed = lawniconsProvider.lookup(match.app.packageName)
        val lawnGlyph = themed?.let { entry -> lawniconsProvider.drawable(entry)?.let { drawable -> lawniconsProvider.alphaMask(drawable)?.let { mask -> MonetGlyphResult(mask, MonetGlyphSource.LAWNICONS_PACKAGE, null) } } }
        val aospCandidate = MaterialCandidateDiscovery.firstAvailable(candidates.inPriorityOrder.map { candidate ->
            candidate.source to (candidate.drawable as? AdaptiveIconDrawable)?.let(AospMonochromeGenerator::generate)?.takeIf { it.alphaMask != null }
        })
        val aosp = aospCandidate?.second
        val providerState = lawniconsProvider.state
        val availability = MaterialSourceAvailability(native != null, lawnGlyph != null, aosp != null, providerState.status)
        val auto = MaterialSourcePriority.automatic(native, lawnGlyph, aosp)
            ?: MonetGlyphResult(null, MonetGlyphSource.UNAVAILABLE_LEGACY, null, MonetGlyphSource.UNAVAILABLE_LEGACY)
        val requested = overrideStore.get(key)
        val glyph = when (requested) {
            MaterialSourceOverride.AUTO -> auto
            MaterialSourceOverride.NATIVE -> native ?: auto
            MaterialSourceOverride.LAWNICONS -> lawnGlyph ?: auto
            MaterialSourceOverride.AOSP_FORCE -> aosp ?: auto
            MaterialSourceOverride.KEEP -> MonetGlyphResult(null, MonetGlyphSource.MANUAL_KEEP, null, MonetGlyphSource.MANUAL_KEEP)
        }
        // The sheet hides unavailable choices. If a stale persisted override becomes unavailable,
        // AUTO is selected safely rather than attempting a different forced source.
        val effectiveOverride = if (availability.supports(requested)) requested else MaterialSourceOverride.AUTO
        val diagnostic = MaterialSourceDiagnostic(
            match.app.packageName, match.app.launcherActivity,
            candidates.activityResource.drawable.typeName(), candidates.applicationResource.drawable.typeName(),
            native != null, nativeCandidate?.first?.name,
            providerState.status, themed != null, themed?.drawableResourceId ?: 0,
            aosp != null, aospCandidate?.first?.name, glyph.source
        )
        return ResolvedMaterialComponent(key, glyph, availability, effectiveOverride, diagnostic)
    }

    private fun componentKey(match: IconMatch) = match.app.packageName + "#" + match.app.launcherActivity
    private fun android.graphics.drawable.Drawable?.typeName() = this?.javaClass?.simpleName ?: "NOT_FOUND"

    /** Style changes only re-render cached glyph masks; provider discovery and source policy stay untouched. */
    fun updateMaterialStyle(style: MaterialStyle) = viewModelScope.launch {
        styleStore.set(style)
        val current = _uiState.value.monet
        if (!current.available || materialGlyphCache.isEmpty()) {
            _uiState.value = _uiState.value.copy(monet = current.copy(style = style)); return@launch
        }
        val resolution = withContext(Dispatchers.Default) { paletteResolver.resolve(style) } ?: return@launch
        val palette = resolution.palette
        val rendered = withContext(Dispatchers.Default) { materialGlyphCache.mapNotNull { (key, glyph) -> MaterialStyledGlyphRenderer.render(glyph, palette, current.dark, style.shape, current.previewTargetSize)?.let { key to it } }.toMap() }
        val previews = withContext(Dispatchers.Default) { PreviewBitmapPipeline.decodeMaterialPngs(rendered, current.previewTargetSize) }
        _uiState.value = _uiState.value.copy(monet = current.copy(generationId = System.nanoTime(), palette = palette, generated = rendered, previewBitmaps = previews, paletteResolution = resolution, style = style, timestamp = System.currentTimeMillis()), message = "已按新颜色/形状重新渲染预览")
    }

    fun updateIconPackStyle(style: IconPackStyle) = viewModelScope.launch {
        iconPackStyleStore.set(style)
        val snapshot = _uiState.value
        val previews = withContext(Dispatchers.Default) { prepareIconPackPreviews(snapshot.matches, snapshot.iconPack, style.shape) }
        _uiState.value = _uiState.value.copy(iconPackStyle = style, iconPreviews = previews, message = "已按新形状重新渲染图标包预览")
    }

    /**
     * Repaints an existing System Monet preview from cached masks only.  It is
     * intentionally independent from provider discovery, WorkManager, Root and
     * the installed-theme recolor path.
     */
    fun refreshSystemMonetPreviewIfChanged() = viewModelScope.launch {
        previewRefreshMutex.withLock {
            val current = _uiState.value.monet
            if (current.style.colorMode !in setOf(MaterialColorMode.SYSTEM_MONET, MaterialColorMode.WALLPAPER_AUTO) ||
                !current.available || materialGlyphCache.isEmpty()) return@withLock
            val resolution = withContext(Dispatchers.Default) { paletteResolver.resolve(current.style) } ?: return@withLock
            val newPalette = resolution.palette
            val refreshPlan = MaterialPreviewRefreshPolicy.plan(
                    current.style.colorMode,
                    current.palette?.hash(),
                    resolution.renderHash,
                    materialGlyphCache.isNotEmpty(),
                    current.paletteResolution?.wallpaperStateHash,
                    resolution.wallpaperStateHash
                )
            if (!refreshPlan.rerender) {
                // Secondary/tertiary wallpaper swatches may change without changing
                // the primary-derived palette. Update only the resolver metadata.
                if (current.paletteResolution?.wallpaperStateHash != resolution.wallpaperStateHash || current.paletteResolution == null) {
                    _uiState.value = _uiState.value.copy(monet = current.copy(
                        paletteResolution = resolution,
                        timestamp = System.currentTimeMillis()
                    ))
                }
                return@withLock
            }
            val rendered = withContext(Dispatchers.Default) {
                materialGlyphCache.mapNotNull { (key, glyph) ->
                    MaterialStyledGlyphRenderer.render(glyph, newPalette, current.dark, current.style.shape, current.previewTargetSize)
                        ?.let { key to it }
                }.toMap()
            }
            val previews = withContext(Dispatchers.Default) { PreviewBitmapPipeline.decodeMaterialPngs(rendered, current.previewTargetSize) }
            // Preserve source/override/availability/diagnostic maps verbatim. A palette
            // refresh is serialized rendering work only, never source discovery.
            _uiState.value = _uiState.value.copy(monet = current.copy(
                generationId = System.nanoTime(),
                palette = newPalette,
                generated = rendered,
                previewBitmaps = previews,
                paletteResolution = resolution,
                timestamp = System.currentTimeMillis()
            ))
        }
    }

    /** Re-renders only already-resolved glyph masks. Provider and source discovery are deliberately not invoked. */
    private fun rerenderCachedMaterial(current: MonetUiState, targetSize: Int): MonetUiState {
        if (!current.available || current.previewTargetSize == targetSize || materialGlyphCache.isEmpty()) return current
        val rendered = materialGlyphCache.mapNotNull { (key, glyph) ->
            MaterialStyledGlyphRenderer.render(glyph, current.palette!!, current.dark, current.style.shape, targetSize)?.let { key to it }
        }.toMap()
        val previews = PreviewBitmapPipeline.decodeMaterialPngs(rendered, targetSize)
        return current.copy(generationId = System.nanoTime(), generated = rendered, previewBitmaps = previews, previewTargetSize = targetSize, timestamp = System.currentTimeMillis())
    }

    fun applyMonetToSystem() = viewModelScope.launch {
        val state = _uiState.value
        _uiState.value = state.copy(themeOperationRunning = true, message = null)
        runCatching { withContext(Dispatchers.IO) {
            check(rootExecutor.isRootAvailable()) { "Root 不可用，未修改系统主题" }
            val monet = state.monet.takeIf { it.available && it.generated.isNotEmpty() } ?: error("请先生成 Material You 预览")
            check(backupManager.ownershipState() !is ThemeOwnershipState.ExternalChanged) { "当前图标主题已更换，需要先确认以当前主题为基础" }
            val session = backupManager.ensureOriginalBackup("UNKNOWN", "UNKNOWN").getOrThrow()
            val base = session.backup
            val profile = HyperOsThemeProfileDetector.detect(base)
            val canonicalMonet = rerenderCachedMaterial(monet, profile.staticIconSize)
            check(canonicalMonet.previewTargetSize == profile.staticIconSize) { "无法将 Material 预览重渲染为当前主题尺寸" }
            // Publish the rerender before patching so the visible preview and the
            // bytes about to be installed are the same canonical PNGs.
            _uiState.value = _uiState.value.copy(themeProfile = profile, monet = canonicalMonet)
            // Apply consumes the exact current canonical preview bytes. It never independently renders a second output set.
            val plan = themedReplacements(base, state.matches.mapNotNull { match -> canonicalMonet.generated[componentKey(match)]?.let { match to it } })
            check(plan.entryConflicts.isEmpty()) { "ENTRY_CONFLICT: ${plan.entryConflicts.joinToString { it.entryName }}" }
            check(plan.generatedComponentCount == plan.plannedComponentCount) { "UNROUTED_COMPONENT" }
            check(plan.replacements.isNotEmpty()) { "没有可应用的 Material You 图标" }
            val patched = java.io.File(getApplication<Application>().filesDir, "staging/patched-monet-icons.zip")
            HyperOs3ThemePatcher.patch(base, patched, plan.replacements, profile.staticIconSize)
            check(ThemeApplicationPlanVerifier.verify(patched, plan)) { "PATCH_PLAN_BYTES_VERIFICATION_FAILED" }
            val installedSession = backupManager.install(patched, "system-monet", "MATERIAL_YOU", monet.palette!!.hash()).getOrThrow()
            AppliedThemeResult(plan, installedSession.lastInstalledSha256.orEmpty(), if (ThemeApplyCompletion.softRefresh(rootExecutor)) LauncherRefreshStatus.SUCCESS else LauncherRefreshStatus.FAILED)
        } }.onSuccess { applied ->
            val monet = _uiState.value.monet
            installedMaterialStore.save(MaterialInstalledState(true, monet.style.colorMode, monet.palette!!.hash(), monet.style.customSeedColor, monet.style.shape, monet.dark, System.currentTimeMillis(), applied.archiveSha, monet.style.followWallpaperMonet, AutoRecolorStatus.IDLE))
            activeThemeModeStore.set(ActiveThemeMode.MATERIAL_YOU)
            _uiState.value = _uiState.value.copy(themeOperationRunning = false, lastApplySummary = summary("MATERIAL_YOU", applied), lastApplyRoutes = routeDiagnostics(applied), message = applyMessage(applied))
        }
            .onFailure { error -> _uiState.value = _uiState.value.copy(themeOperationRunning = false, message = error.message ?: "Material You 应用失败") }
    }

    private fun reloadApps() = viewModelScope.launch {
        val apps = withContext(Dispatchers.IO) { appsRepository.launcherApps() }
        val matches = apps.map { IconMatch(it, MatchStatus.UNMATCHED, com.wikiglobal.iconconverter.model.MatchConfidence.NONE) }
        val previews = withContext(Dispatchers.Default) { prepareIconPackPreviews(matches, null, iconPackStyleStore.get().shape) }
        _uiState.value = _uiState.value.copy(
            loading = false,
            iconPackStyle = iconPackStyleStore.get(),
            launcherActivityCount = apps.size,
            uniquePackageCount = apps.map { it.packageName }.distinct().size,
            matches = matches,
            iconPreviews = previews
        )
    }

    fun selectApk(uri: Uri) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, message = null)
        runCatching {
            withContext(Dispatchers.IO) {
                val temporary = selectedIconPackStore.copyToTemporary(uri)
                val validated = parser.parseApk(temporary)
                val selected = selectedIconPackStore.commitTemporary(temporary, SelectedIconPackMetadata(validated.packageName, validated.displayName, HyperOs3ThemePatcher.sha256(temporary), System.currentTimeMillis()))
                val pack = parser.parseApk(selected)
                val apps = appsRepository.launcherApps()
                Triple(pack, apps, IconMatcher.match(apps, pack.mappings, pack.calendars))
            }
        }.onSuccess { (pack, apps, matches) ->
            viewModelScope.launch {
                val style = iconPackStyleStore.get()
                val previews = withContext(Dispatchers.Default) { prepareIconPackPreviews(matches, pack, style.shape) }
            _uiState.value = _uiState.value.copy(
                loading = false,
                iconPack = pack,
                matches = matches,
                iconPreviews = previews,
                launcherActivityCount = apps.size,
                uniquePackageCount = apps.map { it.packageName }.distinct().size,
                monet = MonetUiState(),
                iconPackStyle = style,
                message = null
            )
            }
        }.onFailure { error ->
            _uiState.value = _uiState.value.copy(loading = false, message = error.message ?: "解析 APK 失败")
        }
    }

    /** Runs once after app/pack changes; no Lazy item may load or rasterize a Drawable. */
    private fun prepareIconPackPreviews(matches: List<IconMatch>, pack: IconPack?, shape: IconShape): IconPackPreviewBitmaps {
        val original = linkedMapOf<String, android.graphics.Bitmap>()
        val target = linkedMapOf<String, android.graphics.Bitmap>()
        matches.forEach { match ->
            val key = componentPreviewKey(match.app.packageName, match.app.launcherActivity)
            original[key] = PreviewBitmapPipeline.rasterizeIcon(match.app.originalIcon)
            match.drawableName?.let { name -> pack?.drawableLoader?.invoke(name) }
                ?.let { target[key] = AdaptiveIconPackRenderer.renderBitmap(it, shape, PreviewBitmapPipeline.ICON_PACK_PREVIEW_SIZE) }
        }
        return IconPackPreviewBitmaps(original, target)
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

    /** The only path that can invoke root writes; an external theme requires an explicit foreground rebase. */
    fun applyCurrentModeToSystem() = viewModelScope.launch {
        val external = withContext(Dispatchers.IO) { rootExecutor.isRootAvailable() && backupManager.ownershipState() is ThemeOwnershipState.ExternalChanged }
        if (external) {
            _uiState.value = _uiState.value.copy(themeOwnership = ThemeOwnershipState.ExternalChanged, rebaseConfirmationRequired = true)
        } else if (_uiState.value.themeMode == ThemeMode.MATERIAL_YOU) applyMonetToSystem() else applyIconPackToSystem()
    }

    /** Called only by the foreground confirmation dialog. Rebase itself is read-only to the active archive. */
    fun confirmRebaseAndApply() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(themeOperationRunning = true, rebaseConfirmationRequired = false, message = null)
        val result = withContext(Dispatchers.IO) { backupManager.rebaseToCurrentTheme("UNKNOWN", "UNKNOWN") }
        if (result.isFailure) {
            _uiState.value = _uiState.value.copy(themeOperationRunning = false, message = result.exceptionOrNull()?.message ?: "重新建立主题基础失败")
        } else {
            _uiState.value = _uiState.value.copy(themeOperationRunning = false, themeOwnership = ThemeOwnershipState.ManagedOriginal, themeBaseSha = result.getOrNull()?.original?.sha256)
            if (_uiState.value.themeMode == ThemeMode.MATERIAL_YOU) applyMonetToSystem() else applyIconPackToSystem()
        }
    }
    fun dismissRebaseConfirmation() { _uiState.value = _uiState.value.copy(rebaseConfirmationRequired = false) }
    fun applyIconPackToSystem() = viewModelScope.launch {
        val state = _uiState.value; val pack = state.iconPack ?: return@launch
        _uiState.value = state.copy(themeOperationRunning = true, message = null)
        runCatching { withContext(Dispatchers.IO) {
            check(rootExecutor.isRootAvailable()) { "Root 不可用，未修改系统主题" }
            check(backupManager.ownershipState() !is ThemeOwnershipState.ExternalChanged) { "当前图标主题已更换，需要先确认以当前主题为基础" }
            val session = backupManager.ensureOriginalBackup("UNKNOWN", "UNKNOWN").getOrThrow()
            val base = session.backup // Full apply always starts from the managed original, never the last transformed archive.
            val profile = HyperOsThemeProfileDetector.detect(base)
            val rendered = state.matches.filter { it.drawableName != null && it.status != MatchStatus.CONFLICT }.mapNotNull { match ->
                pack.drawableLoader(match.drawableName!!)?.let { drawable -> Triple(match, match.drawableName, AdaptiveIconPackRenderer.renderPng(drawable, state.iconPackStyle.shape, profile.staticIconSize)) }
            }
            val plan = themedReplacements(base, rendered.map { it.first to it.third })
            check(plan.entryConflicts.isEmpty()) { "ENTRY_CONFLICT: ${plan.entryConflicts.joinToString { it.entryName }}" }
            check(plan.generatedComponentCount == plan.plannedComponentCount) { "UNROUTED_COMPONENT" }
            check(plan.replacements.isNotEmpty()) { "没有可应用的匹配图标" }
            val patched = java.io.File(getApplication<Application>().filesDir, "staging/patched-icons.zip")
            HyperOs3ThemePatcher.patch(base, patched, plan.replacements, profile.staticIconSize)
            check(ThemeApplicationPlanVerifier.verify(patched, plan)) { "PATCH_PLAN_BYTES_VERIFICATION_FAILED" }
            val installed = backupManager.install(patched, pack.packageName, "ICON_PACK", null).getOrThrow()
            AppliedThemeResult(plan, installed.lastInstalledSha256.orEmpty(), if (ThemeApplyCompletion.softRefresh(rootExecutor)) LauncherRefreshStatus.SUCCESS else LauncherRefreshStatus.FAILED)
        } }.onSuccess { applied -> activeThemeModeStore.set(ActiveThemeMode.ICON_PACK); _uiState.value = _uiState.value.copy(themeOperationRunning = false, lastApplySummary = summary("ICON_PACK", applied), lastApplyRoutes = routeDiagnostics(applied), message = applyMessage(applied)) }
            .onFailure { error -> _uiState.value = _uiState.value.copy(themeOperationRunning = false, message = error.message ?: "应用失败；当前主题未被覆盖") }
    }

    /** Delegates exact alias matching to the archive-only planner; never wildcard-matches package names. */
    private fun themedReplacements(base: java.io.File, rendered: List<Pair<IconMatch, ByteArray>>): ThemeApplicationPlan = rendered
        .map { (match, png) -> RenderedThemeActivityIcon(match.app.packageName, match.app.launcherActivity, match.app.activityAliases, match.app.targetActivity, png) }
        .let { HyperOsThemeReplacementPlanner.plan(base, it) }

    private fun summary(mode: String, applied: AppliedThemeResult) = ThemeApplySummary(mode, applied.plan.generatedComponentCount, applied.plan.plannedComponentCount, applied.plan.existingActivityRouteComponents, applied.plan.packageOnlyComponents, applied.plan.existingActivityEntriesMatched, applied.plan.legacyAliasComponents, applied.plan.packageBaseReplacementCount, applied.plan.activityAliasReplacementCount, applied.plan.totalReplacementCount, applied.plan.unroutedComponents.size, applied.plan.entryConflicts.size, true, true, applied.refreshStatus)
    private fun routeDiagnostics(applied: AppliedThemeResult): List<ThemeRouteDiagnostic> = applied.plan.components.map { route ->
        ThemeRouteDiagnostic(
            packageName = route.packageName,
            launcherActivity = route.launcherActivity,
            targetActivity = route.targetActivity,
            currentActivityEntry = route.currentActivityEntry,
            currentActivityEntryExisted = route.currentActivityEntryExisted,
            directMatchedEntries = route.directMatchedEntries,
            targetFallbackMatchedEntries = route.targetFallbackMatchedEntries,
            legacyThemeAliasEntries = route.legacyThemeAliasEntries,
            legacyAliasStatus = route.legacyAliasStatus.name,
            finalReplacementEntries = route.finalReplacementEntries
        )
    }
    private fun applyMessage(applied: AppliedThemeResult): String = "已应用 ${applied.plan.plannedComponentCount} 个应用 · 更新 ${applied.plan.totalReplacementCount} 个主题条目" + if (applied.refreshStatus == LauncherRefreshStatus.SUCCESS) "" else "；桌面刷新请求失败，可在工具页重试"

    fun restoreOriginalTheme() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(themeOperationRunning = true, message = null)
        val result = withContext(Dispatchers.IO) { backupManager.restore() }
        if (result.isSuccess) activeThemeModeStore.set(ActiveThemeMode.NONE)
        _uiState.value = if (result.isSuccess) _uiState.value.copy(themeOperationRunning = false, message = "原主题已安全恢复") else _uiState.value.copy(themeOperationRunning = false, message = result.exceptionOrNull()?.message ?: "恢复失败")
    }

    fun refreshIconCache() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(themeOperationRunning = true, message = null)
        val result = withContext(Dispatchers.IO) { rootExecutor.refreshIconCache() }
        _uiState.value = _uiState.value.copy(themeOperationRunning = false, message = if (result.success) "已请求刷新图标缓存" else "刷新图标缓存失败：${result.message}")
    }
    fun forceRestartLauncher() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(themeOperationRunning = true, message = null)
        val result = withContext(Dispatchers.IO) { rootExecutor.forceStopLauncher() }
        _uiState.value = _uiState.value.copy(themeOperationRunning = false, message = if (result.success) "已强制重启桌面" else "强制重启桌面失败：${result.message}")
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

    /** Exports only exact archive route names from the last successful apply. */
    fun exportLastApplyRouteReport(uri: Uri) = viewModelScope.launch {
        val state = _uiState.value
        val summary = state.lastApplySummary ?: return@launch
        if (state.lastApplyRoutes.isEmpty()) return@launch
        runCatching {
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(ThemeRouteReport.format(summary, state.lastApplyRoutes))
                } ?: error("无法写入路由报告")
            }
        }.onSuccess { _uiState.value = _uiState.value.copy(message = "最近应用路由已导出") }
            .onFailure { error -> _uiState.value = _uiState.value.copy(message = error.message ?: "导出失败") }
    }

    fun setAutoAdaptEnabled(enabled: Boolean) { autoAdaptSettingsStore.setEnabled(enabled); if (!enabled) AutoAdaptDisableController(PendingPackageStore(getApplication()),WorkManagerAutoAdaptScheduler(getApplication())).disable(); _uiState.value = _uiState.value.copy(autoAdaptEnabled = enabled) }
    fun refreshAutoAdaptSummary() { _uiState.value = _uiState.value.copy(autoAdaptSummary = autoAdaptDryRunStore.summary()) }
    fun exportAutoAdaptReport(uri: Uri) = viewModelScope.launch {
        val report = autoAdaptDryRunStore.report() ?: return@launch
        runCatching { withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(report) } ?: error("无法写入自动适配报告") } }
            .onSuccess { _uiState.value = _uiState.value.copy(message = "自动适配报告已导出") }
            .onFailure { error -> _uiState.value = _uiState.value.copy(message = error.message ?: "导出失败") }
    }

    /** Exports provider/candidate decisions only; no theme archive or PNG bytes leave the app. */
    fun exportMaterialSourceReport(uri: Uri) = viewModelScope.launch {
        val monet = _uiState.value.monet
        if (monet.diagnostics.isEmpty()) return@launch
        runCatching { withContext(Dispatchers.IO) {
            val report = buildString {
                appendLine("MATERIAL_SOURCE_REPORT")
                monet.paletteResolution?.let { resolution ->
                    appendLine("PALETTE_SOURCE=${resolution.source}")
                    appendLine("PALETTE_FALLBACK_USED=${resolution.fallbackUsed}")
                    appendLine("WALLPAPER_PRIMARY_SEED=${resolution.wallpaperColors?.primary?.let { "%08x".format(it) } ?: "NOT_AVAILABLE"}")
                    appendLine("PALETTE_SEED=${resolution.seedColor?.let { "%08x".format(it) } ?: "NOT_AVAILABLE"}")
                    appendLine("A1_100=%08x".format(resolution.palette.accent1_100))
                    appendLine("A1_700=%08x".format(resolution.palette.accent1_700))
                    appendLine("A2_800=%08x".format(resolution.palette.accent2_800 ?: 0))
                    appendLine("A1_200=%08x".format(resolution.palette.accent1_200))
                }
                appendLine("LAWNICONS_PROVIDER=${monet.lawniconsProvider.status}")
                appendLine("LAWNICONS_ENTRY_COUNT=${monet.lawniconsProvider.entryCount}")
                monet.lawniconsProvider.lastError?.let { appendLine("LAWNICONS_ERROR=$it") }
                monet.diagnostics.toSortedMap().forEach { (_, item) ->
                    appendLine()
                    appendLine("PACKAGE=${item.packageName}")
                    appendLine("LAUNCHER_ACTIVITY=${item.launcherActivity}")
                    appendLine("RAW_ACTIVITY_TYPE=${item.rawActivityType}")
                    appendLine("RAW_APPLICATION_TYPE=${item.rawApplicationType}")
                    appendLine("NATIVE_AVAILABLE=${item.nativeAvailable}")
                    appendLine("NATIVE_CANDIDATE_SOURCE=${item.nativeCandidateSource ?: "NONE"}")
                    appendLine("LAWNICONS_PROVIDER_STATUS=${item.lawniconsProviderStatus}")
                    appendLine("LAWNICONS_MAPPED=${item.lawniconsMapped}")
                    appendLine("LAWNICONS_DRAWABLE_ID=${item.lawniconsDrawableId}")
                    appendLine("AOSP_ADAPTIVE_AVAILABLE=${item.aospAdaptiveAvailable}")
                    appendLine("AOSP_CANDIDATE_SOURCE=${item.aospCandidateSource ?: "NONE"}")
                    appendLine("FINAL_SOURCE=${item.finalSource}")
                }
            }
            getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(report) } ?: error("无法写入 Material 来源报告")
        } }.onSuccess { _uiState.value = _uiState.value.copy(message = "Material 来源诊断已导出") }
            .onFailure { error -> _uiState.value = _uiState.value.copy(message = error.message ?: "导出失败") }
    }
}
