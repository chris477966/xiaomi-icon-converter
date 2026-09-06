package com.wikiglobal.iconconverter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wikiglobal.iconconverter.hyperos.MonetGlyphSource
import com.wikiglobal.iconconverter.hyperos.MaterialSourceOverride
import com.wikiglobal.iconconverter.hyperos.LawniconsProviderStatus
import com.wikiglobal.iconconverter.hyperos.MaterialStyle
import com.wikiglobal.iconconverter.hyperos.MaterialColorMode
import com.wikiglobal.iconconverter.hyperos.MaterialIconShape
import com.wikiglobal.iconconverter.hyperos.IconShape
import com.wikiglobal.iconconverter.hyperos.IconPackStyle
import com.wikiglobal.iconconverter.hyperos.ThemeOwnershipState
import com.wikiglobal.iconconverter.model.IconMatch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun IconConverterScreen(state: ConverterUiState, onSelect: () -> Unit, onGenerate: () -> Unit, onCheckHyperOs: () -> Unit, onExportReport: () -> Unit, onExportRouteReport: () -> Unit, onAutoAdaptEnabled: (Boolean) -> Unit, onExportAutoAdaptReport: () -> Unit, onToolsVisible: () -> Unit, onExportMaterialReport: () -> Unit, onCheckRoot: () -> Unit, onApplyTheme: () -> Unit, onRestoreTheme: () -> Unit, onRefreshCache: () -> Unit, onForceRestart: () -> Unit, onThemeMode: (ThemeMode) -> Unit, onMonetPreview: () -> Unit, onApplyMonet: () -> Unit, onMaterialOverride: (String, MaterialSourceOverride) -> Unit, onMaterialStyle: (MaterialStyle) -> Unit, onIconPackStyle: (IconPackStyle) -> Unit, onConfirmRebase: () -> Unit, onDismissRebase: () -> Unit) {
    var tools by remember { mutableStateOf(false) }; val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(tools) { if (tools) onToolsVisible() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it) } }
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing, topBar = { TopAppBar(title = { Text("HyperIcon") }, actions = { IconButton({ tools = !tools }) { Text("⚙", modifier = Modifier.semantics { contentDescription = "Settings / Tools" }) } }) }, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = { if (!tools) BottomAppBar { Button(if(state.rootAvailable) onApplyTheme else onCheckRoot, enabled = ((state.themeMode == ThemeMode.ICON_PACK && state.iconPack != null && state.matchedCount > 0) || (state.themeMode == ThemeMode.MATERIAL_YOU && state.monet.generated.isNotEmpty())) && !state.themeOperationRunning, modifier = Modifier.fillMaxWidth().padding(horizontal=16.dp)) { Text(if (state.rootAvailable) "应用到 HyperOS 3" else "检查 Root 并继续") } } }) { padding ->
        if (tools) SystemToolsScreen(Modifier.padding(padding), state, onCheckRoot, onRestoreTheme, onRefreshCache, onForceRestart, onCheckHyperOs, onExportReport, onExportRouteReport, onAutoAdaptEnabled, onExportAutoAdaptReport)
        else Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = ThemeMode.entries.indexOf(state.themeMode)) { ThemeMode.entries.forEach { mode -> Tab(selected = state.themeMode == mode, onClick = { onThemeMode(mode) }, text = { Text(if(mode == ThemeMode.ICON_PACK) "图标包" else "Material You") }) } }
            if (state.themeMode == ThemeMode.ICON_PACK) { SourceCard(state, onSelect); IconPackScreen(state, onIconPackStyle, Modifier.weight(1f)) } else MaterialYouScreen(state, onMonetPreview, onMaterialOverride, onExportMaterialReport, onMaterialStyle, Modifier.weight(1f))
        }
        if (state.themeOperationRunning) Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { Card { Row(Modifier.padding(20.dp), verticalAlignment=Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text("正在处理主题…") } } }
        if (state.rebaseConfirmationRequired) AlertDialog(onDismissRequest = onDismissRebase, title = { Text("检测到图标主题已更换") }, text = { Text("当前图标主题可能已通过系统主题商店或其他工具更换。继续后，本应用会把当前主题作为新的基础主题，并只在其上替换所选图标。以后“恢复主题”将恢复到当前这套主题。") }, confirmButton = { TextButton(onConfirmRebase) { Text("以当前主题为基础并应用") } }, dismissButton = { TextButton(onDismissRebase) { Text("取消") } })
    }
}

@Composable private fun SourceCard(state: ConverterUiState, select: () -> Unit) = Card(Modifier.fillMaxWidth().padding(16.dp)) { Row(Modifier.padding(16.dp), verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("图标包", style=MaterialTheme.typography.labelMedium); Text(state.iconPack?.displayName ?: "尚未选择", style=MaterialTheme.typography.titleMedium); state.iconPack?.let { Text("${it.mappings.size} mappings · 匹配 ${state.matchedCount} / ${state.launcherActivityCount}", style=MaterialTheme.typography.bodySmall) } }; TextButton(select){Text(if(state.iconPack==null)"选择" else "更换")} } }
@Composable private fun IconPackScreen(state: ConverterUiState, setStyle: (IconPackStyle) -> Unit, modifier: Modifier) {
    var shapeSheet by remember { mutableStateOf(false) }
    Column(modifier.padding(horizontal=8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp, vertical=8.dp)) { FilterChip(selected=false, onClick={shapeSheet=true}, label={Text(shapeLabel(state.iconPackStyle.shape))}) }
        if(state.iconPack==null) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("选择图标包 APK")}
        else LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding=PaddingValues(8.dp), verticalArrangement=Arrangement.spacedBy(12.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) { items(state.matches, key={componentPreviewKey(it.app.packageName,it.app.launcherActivity)}) { match -> val key=componentPreviewKey(match.app.packageName,match.app.launcherActivity); Column(horizontalAlignment=Alignment.CenterHorizontally) { CachedPreview(state.iconPreviews.target[key] ?: state.iconPreviews.original[key], "最终图标", 80.dp); Text(match.app.label,maxLines=1,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center,style=MaterialTheme.typography.labelMedium); if(match.status==com.wikiglobal.iconconverter.model.MatchStatus.UNMATCHED) Text("未匹配",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.outline) } } }
    }
    if(shapeSheet) SharedShapeSheet(state.iconPackStyle.shape, { setStyle(state.iconPackStyle.copy(shape=it)); shapeSheet=false }, { shapeSheet=false })
}
@Composable private fun MaterialYouScreen(state: ConverterUiState, generate: () -> Unit, setOverride: (String, MaterialSourceOverride) -> Unit, exportReport: () -> Unit, setStyle: (MaterialStyle) -> Unit, modifier: Modifier) {
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selected = state.matches.firstOrNull { it.app.packageName + "#" + it.app.launcherActivity == selectedKey }
    val style = state.monet.style
    var seedDialog by remember { mutableStateOf(false) }
    var confirmFollow by remember { mutableStateOf(false) }
    var colorSheet by remember { mutableStateOf(false) }
    var shapeSheet by remember { mutableStateOf(false) }
    Box(modifier) {
        LazyVerticalGrid(GridCells.Fixed(3),Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=4.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            item(span={GridItemSpan(maxLineSpan)}) {
                Column(Modifier.fillMaxWidth(), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically) {
                        FilterChip(selected=false,onClick={colorSheet=true},label={Text(materialColorModeLabel(style.colorMode), maxLines=1, overflow=TextOverflow.Ellipsis)}, modifier=Modifier.weight(1f))
                        FilterChip(selected=false,onClick={shapeSheet=true},label={Text(shapeLabel(style.shape), maxLines=1, overflow=TextOverflow.Ellipsis)}, modifier=Modifier.weight(1f))
                    }
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        if (style.colorMode != MaterialColorMode.CUSTOM) Checkbox(style.followWallpaperMonet, { checked -> if (checked) confirmFollow=true else setStyle(style.copy(followWallpaperMonet=false)) })
                        if (style.colorMode != MaterialColorMode.CUSTOM) Text("壁纸变化后自动应用图标", style=MaterialTheme.typography.bodySmall)
                    }
                    if (style.colorMode == MaterialColorMode.CUSTOM) Row(verticalAlignment=Alignment.CenterVertically) { Surface(Modifier.size(24.dp), color=androidx.compose.ui.graphics.Color(style.customSeedColor), shape=MaterialTheme.shapes.small) {}; TextButton({ seedDialog=true }) { Text("选择 Seed Color") } }
                }
            }
            item(span={GridItemSpan(maxLineSpan)}) {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                        Text("Material You · ${if(state.monet.dark)"Dark" else "Light"}", Modifier.weight(1f), style=MaterialTheme.typography.titleMedium)
                        if (state.monet.diagnostics.isNotEmpty()) IconButton(exportReport, modifier = Modifier.size(40.dp)) { Text("ⓘ", modifier = Modifier.semantics { contentDescription = "诊断" }) }
                    }
                    Text("颜色来源：${paletteSourceLabel(state.monet.paletteResolution)}", style=MaterialTheme.typography.labelMedium)
                    state.monet.paletteResolution?.wallpaperColors?.let { colors ->
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically) {
                            PaletteSwatch(colors.primary, "主色"); PaletteSwatch(colors.secondary, "辅助色"); PaletteSwatch(colors.tertiary, "第三色")
                        }
                    }
                    state.monet.palette?.let { palette -> val c=palette.colors(state.monet.dark); Text("● #${"%08X".format(c.first)}  ● #${"%08X".format(c.second)}",style=MaterialTheme.typography.labelSmall) }
                    Text("可生成 ${state.monet.generated.size} · 保留 ${state.monet.sources.size-state.monet.generated.size}")
                    Text("Preview target：${state.monet.previewTargetSize}px${if (state.themeProfile == null) "（Root/Profile 未检测，fallback）" else ""}", style=MaterialTheme.typography.labelSmall)
                    Text("官方 ${state.monet.sourceCount(MonetGlyphSource.NATIVE_MONOCHROME)} · Lawnicons ${state.monet.sources.values.count { it.name.startsWith("LAWNICONS") }} · 自动单色 ${state.monet.sourceCount(MonetGlyphSource.AOSP_FORCED_MONOCHROME)}",style=MaterialTheme.typography.labelSmall)
                    if (state.monet.paletteResolution?.fallbackUsed == true) Text("壁纸颜色暂不可用，当前使用系统配色",style=MaterialTheme.typography.labelSmall)
                    if (state.monet.lawniconsProvider.status != LawniconsProviderStatus.READY) Text("Lawnicons Provider：${lawniconsProviderLabel(state.monet.lawniconsProvider.status)}", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.error)
                } }
            }
            if (state.monet.generated.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) { Box(Modifier.fillMaxWidth().padding(vertical=16.dp),contentAlignment=Alignment.Center){Button(generate){Text("生成预览")}} }
            items(state.matches,key={componentPreviewKey(it.app.packageName,it.app.launcherActivity)},contentType={"material-app"}){match ->
                val key=componentPreviewKey(match.app.packageName,match.app.launcherActivity)
                Column(Modifier.height(120.dp).clickable { selectedKey=key }, horizontalAlignment=Alignment.CenterHorizontally) {
                    if(state.monet.generated.containsKey(key)) CachedPreview(state.monet.previewBitmaps[key],"Material 预览",PreviewBitmapPipeline.MATERIAL_PREVIEW_DP.dp) else CachedPreview(state.iconPreviews.original[key],"当前",PreviewBitmapPipeline.MATERIAL_PREVIEW_DP.dp)
                    Text(match.app.label,maxLines=1,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center,style=MaterialTheme.typography.labelMedium)
                    if(!state.monet.generated.containsKey(key)) Text("保留",style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (colorSheet) MaterialColorSheet(style, { next -> setStyle(next); colorSheet=false }, { colorSheet=false }, { seedDialog=true })
        if (shapeSheet) SharedShapeSheet(style.shape, { next -> setStyle(style.copy(shape=next)); shapeSheet=false }, { shapeSheet=false }, state.monet.palette, state.monet.dark)
    }
    selected?.let { match ->
        val key = match.app.packageName + "#" + match.app.launcherActivity
        MaterialOverrideSheet(
            label = match.app.label,
            source = state.monet.sources[key],
            availability = state.monet.availability[key] ?: MaterialSourceAvailability(),
            selected = state.monet.overrides[key] ?: MaterialSourceOverride.AUTO,
            onDismiss = { selectedKey = null },
            onSelect = { override -> setOverride(key, override); selectedKey = null }
        )
    }
    if (seedDialog) SeedColorDialog(style.customSeedColor, { color -> setStyle(style.copy(customSeedColor=color)); seedDialog=false }, { seedDialog=false })
    if (confirmFollow) AlertDialog(onDismissRequest={confirmFollow=false}, title={Text("启用壁纸变化后自动应用？")}, text={Text("应用运行期间会自动检测壁纸配色变化并更新图标；如果应用进程已被系统关闭，将在下次打开应用时同步最新系统配色。自动更新需要已授权的 Root 权限，不会修改系统分区或动态图标。")}, confirmButton={TextButton({setStyle(style.copy(followWallpaperMonet=true));confirmFollow=false}){Text("确认")}}, dismissButton={TextButton({confirmFollow=false}){Text("取消")}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MaterialColorSheet(style: MaterialStyle, onSelect: (MaterialStyle) -> Unit, onDismiss: () -> Unit, openSeed: () -> Unit) = ModalBottomSheet(onDismissRequest=onDismiss) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("颜色来源", style=MaterialTheme.typography.titleLarge)
        ColorModeOption("壁纸自动", "使用当前桌面壁纸主色生成 Material 调色板", style.colorMode == MaterialColorMode.WALLPAPER_AUTO) { onSelect(style.copy(colorMode=MaterialColorMode.WALLPAPER_AUTO)) }
        ColorModeOption("系统配色", "使用 HyperOS / Android 当前 system_accent 配色", style.colorMode == MaterialColorMode.SYSTEM_MONET) { onSelect(style.copy(colorMode=MaterialColorMode.SYSTEM_MONET)) }
        ColorModeOption("自定义", "手动选择 Seed Color", style.colorMode == MaterialColorMode.CUSTOM) { onSelect(style.copy(colorMode=MaterialColorMode.CUSTOM, followWallpaperMonet=false)); openSeed() }
    }
}

@Composable private fun ColorModeOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) = Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=8.dp), verticalAlignment=Alignment.CenterVertically) {
    RadioButton(selected=selected,onClick=onClick)
    Column(Modifier.padding(start=10.dp)) { Text(title); Text(description,style=MaterialTheme.typography.bodySmall) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SharedShapeSheet(selected: IconShape, onSelect: (IconShape) -> Unit, onDismiss: () -> Unit, palette: com.wikiglobal.iconconverter.hyperos.MonetPalette? = null, dark: Boolean = false) = ModalBottomSheet(onDismissRequest=onDismiss) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("图标形状", style=MaterialTheme.typography.titleLarge)
        IconShape.entries.forEach { shape ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(shape) }.padding(vertical=6.dp), verticalAlignment=Alignment.CenterVertically) {
                MaterialShapePreview(shape, palette, dark)
                Text(shapeLabel(shape), Modifier.padding(start=12.dp))
            }
        }
    }
}

/** Uses the production shape renderer directly; no approximation or source discovery. */
@Composable private fun MaterialShapePreview(shape: IconShape, palette: com.wikiglobal.iconconverter.hyperos.MonetPalette?, dark: Boolean) {
    val color = palette?.colors(dark)?.first ?: 0xffd9e2ff.toInt()
    val bitmap = remember(shape, color) {
        com.wikiglobal.iconconverter.hyperos.MaterialIconShapeRenderer.previewBitmap(shape, color)
    }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Image(
        painter = BitmapPainter(image, filterQuality = FilterQuality.High),
        contentDescription = shapeLabel(shape),
        modifier = Modifier.size(56.dp),
        contentScale = ContentScale.Fit
    )
}

@Composable private fun PaletteSwatch(color: Int?, label: String) { if (color != null) Row(verticalAlignment=Alignment.CenterVertically) { Surface(Modifier.size(14.dp), color=androidx.compose.ui.graphics.Color(color), shape=MaterialTheme.shapes.small) {}; Text(label,Modifier.padding(start=3.dp),style=MaterialTheme.typography.labelSmall) } }
private fun materialColorModeLabel(mode: MaterialColorMode) = when(mode) { MaterialColorMode.WALLPAPER_AUTO -> "壁纸自动"; MaterialColorMode.SYSTEM_MONET -> "系统配色"; MaterialColorMode.CUSTOM -> "自定义" }
private fun paletteSourceLabel(resolution: com.wikiglobal.iconconverter.hyperos.MaterialPaletteResolution?) = when {
    resolution == null -> "未生成"
    resolution.fallbackUsed -> "壁纸自动 → 系统配色（Fallback）"
    resolution.source == com.wikiglobal.iconconverter.hyperos.MaterialPaletteSource.WALLPAPER -> "壁纸自动"
    resolution.source == com.wikiglobal.iconconverter.hyperos.MaterialPaletteSource.SYSTEM_MONET -> "系统配色"
    else -> "自定义"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MaterialOverrideSheet(label: String, source: MonetGlyphSource?, availability: MaterialSourceAvailability, selected: MaterialSourceOverride, onDismiss: () -> Unit, onSelect: (MaterialSourceOverride) -> Unit) = ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(bottom=32.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label, style=MaterialTheme.typography.titleLarge)
        Text("当前来源：${materialSourceLabel(source)}", style=MaterialTheme.typography.bodyMedium)
        MaterialOverrideOption("自动", MaterialSourceOverride.AUTO, true, selected, onSelect)
        if (availability.native) MaterialOverrideOption("官方单色", MaterialSourceOverride.NATIVE, true, selected, onSelect)
        if (availability.lawnicons) MaterialOverrideOption("Lawnicons", MaterialSourceOverride.LAWNICONS, true, selected, onSelect)
        if (availability.aosp) MaterialOverrideOption("Android 自动单色", MaterialSourceOverride.AOSP_FORCE, true, selected, onSelect)
        MaterialOverrideOption("保留原图", MaterialSourceOverride.KEEP, true, selected, onSelect)
    }
}
@Composable private fun MaterialOverrideOption(label: String, value: MaterialSourceOverride, enabled: Boolean, selected: MaterialSourceOverride, onSelect: (MaterialSourceOverride) -> Unit) = Row(Modifier.fillMaxWidth().clickable(enabled=enabled) { onSelect(value) }.padding(vertical=8.dp), verticalAlignment=Alignment.CenterVertically) { RadioButton(selected=value==selected,onClick={onSelect(value)},enabled=enabled); Text(label,Modifier.padding(start=12.dp),color=if(enabled)LocalContentColor.current else MaterialTheme.colorScheme.outline) }
private fun materialSourceLabel(source: MonetGlyphSource?) = when(source) { MonetGlyphSource.NATIVE_MONOCHROME -> "官方单色"; MonetGlyphSource.LAWNICONS_EXACT, MonetGlyphSource.LAWNICONS_PACKAGE, MonetGlyphSource.LAWNICONS_ALIAS -> "Lawnicons"; MonetGlyphSource.AOSP_FORCED_MONOCHROME -> "Android 自动单色"; MonetGlyphSource.MANUAL_KEEP -> "保留原图"; else -> "不可用" }
private fun lawniconsProviderLabel(status: LawniconsProviderStatus) = when(status) { LawniconsProviderStatus.APK_SHA_FAILED -> "APK SHA 校验失败"; LawniconsProviderStatus.APK_RESOURCES_FAILED -> "APK 资源读取失败"; LawniconsProviderStatus.GRAYSCALE_MAP_NOT_FOUND -> "未找到 grayscale map"; LawniconsProviderStatus.GRAYSCALE_MAP_PARSE_FAILED -> "grayscale map 解析失败"; LawniconsProviderStatus.EMPTY_MAP -> "grayscale map 为空"; else -> status.name }
private fun shapeLabel(shape: IconShape) = when(shape) { IconShape.SYSTEM -> "系统"; IconShape.HYPEROS -> "HyperOS"; IconShape.CIRCLE -> "圆形"; IconShape.SQUIRCLE -> "Squircle"; IconShape.ROUNDED_SQUARE -> "圆角方形"; IconShape.SQUARE -> "方形"; IconShape.TEARDROP -> "水滴" }
@Composable private fun SeedColorDialog(seed: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) { var value by remember { mutableStateOf("#%06X".format(seed and 0xFFFFFF)) }; AlertDialog(onDismissRequest=onDismiss,title={Text("自定义颜色")},text={OutlinedTextField(value,{value=it},label={Text("#RRGGBB")},singleLine=true)},confirmButton={TextButton({value.removePrefix("#").toLongOrNull(16)?.takeIf{it<=0xFFFFFF}?.let{onConfirm((0xFF000000L or it).toInt())}}){Text("应用")}},dismissButton={TextButton(onDismiss){Text("取消")}}) }
@Composable private fun SystemToolsScreen(modifier:Modifier,state:ConverterUiState,check:()->Unit,restore:()->Unit,refresh:()->Unit,restart:()->Unit,diagnose:()->Unit,export:()->Unit,exportRoutes:()->Unit,setAutoAdapt:(Boolean)->Unit,exportAutoAdapt:()->Unit)=LazyColumn(modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
    item{Text("Tools",style=MaterialTheme.typography.titleLarge)}
    item{Text("自动适配新应用",style=MaterialTheme.typography.titleMedium)}
    item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(state.autoAdaptEnabled)"试运行" else "OFF");Text("安装或更新应用后自动分析图标适配方案，不会修改系统主题。",style=MaterialTheme.typography.bodySmall)};Switch(state.autoAdaptEnabled,{setAutoAdapt(it)})}}
    item{Text("最近一次：${state.autoAdaptSummary.packageCount} 个应用 · 可匹配 ${state.autoAdaptSummary.matchedCount} · Blocked ${state.autoAdaptSummary.blockedCount}",style=MaterialTheme.typography.bodySmall)}
    item{Button(exportAutoAdapt,Modifier.fillMaxWidth(),enabled=state.autoAdaptSummary.timestamp>0){Text("导出自动适配报告")}}
    item{Text("Root：${if(state.rootAvailable)"可用" else "未检查"}")}
    item{Text("当前 Theme Profile",style=MaterialTheme.typography.titleMedium)}
    item{Text("Ownership：${state.themeOwnership.javaClass.simpleName}\nBase SHA：${state.themeBaseSha?.take(12) ?: "—"}\nTarget icon size：${state.themeProfile?.staticIconSize ?: "—"}\nDetected static icons：${state.themeProfile?.detectedIconCount ?: "—"}\nProfile confidence：${state.themeProfile?.confidence ?: "—"}")}
    state.lastApplySummary?.let{s->item{Text("最近应用\n应用组件：${s.generatedComponents}\n命中当前主题 Activity：${s.existingActivityRouteComponents}\n仅 Package fallback：${s.packageOnlyComponents}\nLegacy unique alias：${s.legacyAliasComponents}\n主题条目：${s.totalReplacements}\nACTIVITY_ENTRIES_MATCHED=${s.existingActivityEntriesMatched}\nPLANNED_COMPONENTS=${s.plannedComponents}\nUNROUTED_COMPONENTS=${s.unroutedComponents}\nENTRY_CONFLICTS=${s.entryConflicts}\nPATCH_VERIFIED=${s.patchVerified}\nARCHIVE_INSTALL_VERIFIED=${s.archiveInstallVerified}\nLAUNCHER_REFRESH_STATUS=${s.launcherRefreshStatus}")}}
    item{Button(check,Modifier.fillMaxWidth()){Text("检查 Root")}};item{Button(restore,Modifier.fillMaxWidth(),enabled=state.rootAvailable){Text("恢复主题")}};item{Button(refresh,Modifier.fillMaxWidth(),enabled=state.rootAvailable){Text("刷新图标缓存")}};item{Button(restart,Modifier.fillMaxWidth(),enabled=state.rootAvailable,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("重启桌面")}};item{HorizontalDivider()};item{Button(diagnose,Modifier.fillMaxWidth()){Text("Compatibility")}};item{Button(export,Modifier.fillMaxWidth(),enabled=state.diagnosticReport!=null){Text("Diagnostic export")}};item{Button(exportRoutes,Modifier.fillMaxWidth(),enabled=state.lastApplyRoutes.isNotEmpty()){Text("导出最近应用路由")}}
}
@Composable private fun CachedPreview(bitmap:android.graphics.Bitmap?,description:String,size:androidx.compose.ui.unit.Dp){if(bitmap!=null){val image=remember(bitmap){bitmap.asImageBitmap()};val painter=remember(image){BitmapPainter(image,filterQuality=FilterQuality.High)};Image(painter,description,Modifier.size(size),contentScale=ContentScale.Fit)}else Surface(Modifier.size(size),shape=MaterialTheme.shapes.small,tonalElevation=1.dp){Box(contentAlignment=Alignment.Center){Text("保留",style=MaterialTheme.typography.labelSmall)}}}
