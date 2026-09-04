package com.wikiglobal.iconconverter.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wikiglobal.iconconverter.hyperos.MonetGlyphSource
import com.wikiglobal.iconconverter.hyperos.MaterialSourceOverride
import com.wikiglobal.iconconverter.hyperos.LawniconsProviderStatus
import com.wikiglobal.iconconverter.model.IconMatch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun IconConverterScreen(state: ConverterUiState, onSelect: () -> Unit, onGenerate: () -> Unit, onCheckHyperOs: () -> Unit, onExportReport: () -> Unit, onExportMaterialReport: () -> Unit, onCheckRoot: () -> Unit, onApplyTheme: () -> Unit, onRestoreTheme: () -> Unit, onRefreshCache: () -> Unit, onForceRestart: () -> Unit, onThemeMode: (ThemeMode) -> Unit, onMonetPreview: () -> Unit, onApplyMonet: () -> Unit, onMaterialOverride: (String, MaterialSourceOverride) -> Unit) {
    var tools by remember { mutableStateOf(false) }; val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it) } }
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing, topBar = { TopAppBar(title = { Column { Text("HyperIcon Converter"); Text("HyperOS 3", style = MaterialTheme.typography.labelMedium) } }, actions = { TextButton({ tools = !tools }) { Text(if (tools) "返回" else "工具") } }) }, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = { if (!tools) BottomAppBar { when (state.themeMode) { ThemeMode.ICON_PACK -> Button(onApplyTheme, enabled = state.iconPack != null && state.matchedCount > 0 && state.rootAvailable && !state.themeOperationRunning, modifier = Modifier.fillMaxWidth().padding(horizontal=16.dp)) { Text("应用原图标包") }; ThemeMode.MATERIAL_YOU -> Button(if(state.rootAvailable) onApplyMonet else onCheckRoot, enabled = state.monet.generated.isNotEmpty() && !state.themeOperationRunning, modifier = Modifier.fillMaxWidth().padding(horizontal=16.dp)) { Text(if (state.rootAvailable) "应用 Material You" else "检查 Root 并继续") } } } }) { padding ->
        if (tools) SystemToolsScreen(Modifier.padding(padding), state, onCheckRoot, onRestoreTheme, onRefreshCache, onForceRestart, onCheckHyperOs, onExportReport)
        else Column(Modifier.padding(padding).fillMaxSize()) {
            SourceCard(state, onSelect)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=8.dp)) { ThemeMode.entries.forEachIndexed { i, mode -> SegmentedButton(selected=state.themeMode==mode,onClick={onThemeMode(mode)},shape=SegmentedButtonDefaults.itemShape(i,2),label={Text(if(mode==ThemeMode.ICON_PACK)"图标包" else "Material You")}) } }
            if (state.themeMode == ThemeMode.ICON_PACK) IconPackScreen(state, onGenerate, Modifier.weight(1f)) else MaterialYouScreen(state, onMonetPreview, onMaterialOverride, onExportMaterialReport, Modifier.weight(1f))
        }
        if (state.themeOperationRunning) Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { Card { Row(Modifier.padding(20.dp), verticalAlignment=Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text("正在处理主题…") } } }
    }
}

@Composable private fun SourceCard(state: ConverterUiState, select: () -> Unit) = Card(Modifier.fillMaxWidth().padding(16.dp)) { Row(Modifier.padding(16.dp), verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("图标包", style=MaterialTheme.typography.labelMedium); Text(state.iconPack?.displayName ?: "尚未选择", style=MaterialTheme.typography.titleMedium); state.iconPack?.let { Text("${it.mappings.size} mappings · 匹配 ${state.matchedCount} / ${state.launcherActivityCount}", style=MaterialTheme.typography.bodySmall) } }; TextButton(select){Text(if(state.iconPack==null)"选择" else "更换")} } }
@Composable private fun IconPackScreen(state: ConverterUiState, export: () -> Unit, modifier: Modifier) = Column(modifier.padding(horizontal=16.dp)) { Text("匹配 ${state.matchedCount} · 未匹配 ${state.unmatchedCount} · 冲突 ${state.conflictCount}", style=MaterialTheme.typography.bodyMedium); if(state.iconPack==null) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("选择图标包后查看匹配结果")} else LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){items(state.matches){match->Card(Modifier.fillMaxWidth()){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){DrawablePreview(match.app.originalIcon,"当前");Spacer(Modifier.width(12.dp));match.drawableName?.let{state.iconPack.drawableLoader(it)}?.let{DrawablePreview(it,"目标")};Text(match.app.label,Modifier.padding(start=12.dp).weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)}}}; item { TextButton(export,Modifier.fillMaxWidth()){Text("导出 Xiaomi icons")} } } }
@Composable private fun MaterialYouScreen(state: ConverterUiState, generate: () -> Unit, setOverride: (String, MaterialSourceOverride) -> Unit, exportReport: () -> Unit, modifier: Modifier) {
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selected = state.matches.firstOrNull { it.app.packageName + "#" + it.app.launcherActivity == selectedKey }
    val p = state.monet.palette
    Column(modifier.padding(horizontal=16.dp)) {
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("Material You · ${if(state.monet.dark)"Dark" else "Light"}",style=MaterialTheme.typography.titleMedium)
            p?.let { val c=it.colors(state.monet.dark); Text("● #${"%08X".format(c.first)}     ● #${"%08X".format(c.second)}") }
            Text("可生成 ${state.monet.generated.size} · 保留 ${state.monet.sources.size-state.monet.generated.size}")
            Text("官方 ${state.monet.sourceCount(MonetGlyphSource.NATIVE_MONOCHROME)} · Lawnicons ${state.monet.sources.values.count { it.name.startsWith("LAWNICONS") }} · 自动单色 ${state.monet.sourceCount(MonetGlyphSource.AOSP_FORCED_MONOCHROME)}",style=MaterialTheme.typography.labelSmall)
            if (state.monet.lawniconsProvider.status != LawniconsProviderStatus.READY) Text("Lawnicons Provider：${lawniconsProviderLabel(state.monet.lawniconsProvider.status)}", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.error)
        } }
        if(state.monet.generated.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Button(generate){Text("生成预览")}}
        else LazyVerticalGrid(GridCells.Fixed(3),Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            items(state.matches){match ->
                val key=match.app.packageName+"#"+match.app.launcherActivity
                Column(Modifier.clickable { selectedKey=key }, horizontalAlignment=Alignment.CenterHorizontally) {
                    if(state.monet.generated.containsKey(key)) MonetPreview(state.monet.generated[key]) else DrawablePreview(match.app.originalIcon,"当前")
                    Text(match.app.label,maxLines=1,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center,style=MaterialTheme.typography.labelMedium)
                    if(!state.monet.generated.containsKey(key)) Text("保留",style=MaterialTheme.typography.labelSmall)
                }
            }
        }
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
    if (state.monet.diagnostics.isNotEmpty()) TextButton(exportReport, Modifier.fillMaxWidth()) { Text("导出 Material 来源诊断") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MaterialOverrideSheet(label: String, source: MonetGlyphSource?, availability: MaterialSourceAvailability, selected: MaterialSourceOverride, onDismiss: () -> Unit, onSelect: (MaterialSourceOverride) -> Unit) = ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(bottom=32.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label, style=MaterialTheme.typography.titleLarge)
        Text("当前来源：${materialSourceLabel(source)}", style=MaterialTheme.typography.bodyMedium)
        MaterialOverrideOption("自动", MaterialSourceOverride.AUTO, true, selected, onSelect)
        MaterialOverrideOption("官方单色（${if(availability.native) "可用" else "App 未提供"}）", MaterialSourceOverride.NATIVE, availability.native, selected, onSelect)
        MaterialOverrideOption("Lawnicons（${if(availability.lawnicons) "可用" else if(availability.lawniconsProviderStatus == LawniconsProviderStatus.READY) "未收录" else "Provider 异常"}）", MaterialSourceOverride.LAWNICONS, availability.lawnicons, selected, onSelect)
        MaterialOverrideOption("Android 自动单色（${if(availability.aosp) "可用" else "未找到 Adaptive Icon"}）", MaterialSourceOverride.AOSP_FORCE, availability.aosp, selected, onSelect)
        MaterialOverrideOption("保留原图", MaterialSourceOverride.KEEP, true, selected, onSelect)
    }
}
@Composable private fun MaterialOverrideOption(label: String, value: MaterialSourceOverride, enabled: Boolean, selected: MaterialSourceOverride, onSelect: (MaterialSourceOverride) -> Unit) = Row(Modifier.fillMaxWidth().clickable(enabled=enabled) { onSelect(value) }.padding(vertical=8.dp), verticalAlignment=Alignment.CenterVertically) { RadioButton(selected=value==selected,onClick={onSelect(value)},enabled=enabled); Text(label,Modifier.padding(start=12.dp),color=if(enabled)LocalContentColor.current else MaterialTheme.colorScheme.outline) }
private fun materialSourceLabel(source: MonetGlyphSource?) = when(source) { MonetGlyphSource.NATIVE_MONOCHROME -> "官方单色"; MonetGlyphSource.LAWNICONS_EXACT, MonetGlyphSource.LAWNICONS_PACKAGE, MonetGlyphSource.LAWNICONS_ALIAS -> "Lawnicons"; MonetGlyphSource.AOSP_FORCED_MONOCHROME -> "Android 自动单色"; MonetGlyphSource.MANUAL_KEEP -> "保留原图"; else -> "不可用" }
private fun lawniconsProviderLabel(status: LawniconsProviderStatus) = when(status) { LawniconsProviderStatus.APK_SHA_FAILED -> "APK SHA 校验失败"; LawniconsProviderStatus.APK_RESOURCES_FAILED -> "APK 资源读取失败"; LawniconsProviderStatus.GRAYSCALE_MAP_NOT_FOUND -> "未找到 grayscale map"; LawniconsProviderStatus.GRAYSCALE_MAP_PARSE_FAILED -> "grayscale map 解析失败"; LawniconsProviderStatus.EMPTY_MAP -> "grayscale map 为空"; else -> status.name }
@Composable private fun SystemToolsScreen(modifier:Modifier,state:ConverterUiState,check:()->Unit,restore:()->Unit,refresh:()->Unit,restart:()->Unit,diagnose:()->Unit,export:()->Unit)=LazyColumn(modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("系统工具",style=MaterialTheme.typography.titleLarge)};item{Text("Root：${if(state.rootAvailable)"可用" else "未检查"}")};item{Button(check,Modifier.fillMaxWidth()){Text("检查 Root")}};item{Button(restore,Modifier.fillMaxWidth(),enabled=state.rootAvailable){Text("恢复原主题")}};item{Button(refresh,Modifier.fillMaxWidth(),enabled=state.rootAvailable){Text("刷新图标缓存")}};item{Button(restart,Modifier.fillMaxWidth(),enabled=state.rootAvailable,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("强制重启桌面")}};item{HorizontalDivider()};item{Button(diagnose,Modifier.fillMaxWidth()){Text("HyperOS 3 兼容性诊断")}};item{Button(export,Modifier.fillMaxWidth(),enabled=state.diagnosticReport!=null){Text("导出诊断报告")}}}
@Composable private fun MonetPreview(bytes:ByteArray?){val b=remember(bytes){bytes?.let{android.graphics.BitmapFactory.decodeByteArray(it,0,it.size)}};if(b!=null)Image(BitmapPainter(b.asImageBitmap()),"Material 预览",Modifier.size(56.dp))else Surface(Modifier.size(56.dp),shape=MaterialTheme.shapes.small,tonalElevation=1.dp){Box(contentAlignment=Alignment.Center){Text("保留",style=MaterialTheme.typography.labelSmall)}}}
@Composable private fun DrawablePreview(d:Drawable,description:String){val b=remember(d){Bitmap.createBitmap(56,56,Bitmap.Config.ARGB_8888).also{val o=d.bounds;d.setBounds(0,0,56,56);d.draw(Canvas(it));d.bounds=o}};Image(BitmapPainter(b.asImageBitmap()),description,Modifier.size(56.dp))}
