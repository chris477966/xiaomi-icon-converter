package com.wikiglobal.iconconverter.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wikiglobal.iconconverter.model.IconMatch
import com.wikiglobal.iconconverter.model.IconPack
import com.wikiglobal.iconconverter.hyperos.MonetGlyphSource

@Composable
fun IconConverterScreen(state: ConverterUiState, onSelect: () -> Unit, onGenerate: () -> Unit, onCheckHyperOs: () -> Unit, onExportReport: () -> Unit, onCheckRoot: () -> Unit, onApplyTheme: () -> Unit, onRestoreTheme: () -> Unit, onRefreshCache: () -> Unit, onForceRestart: () -> Unit, onThemeMode: (ThemeMode) -> Unit, onMonetPreview: () -> Unit, onApplyMonet: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onSelect, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("选择图标包 APK") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onCheckHyperOs, enabled = !state.diagnosticRunning, modifier = Modifier.fillMaxWidth()) { Text("检查 HyperOS 3 兼容性") }
        state.diagnosticMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp)) }
        state.diagnosticReport?.let { DiagnosticSummary(it, onExportReport) }
        state.iconPack?.let { PackSummary(it, state) }
        Text("Root: ${if (state.rootAvailable) "可用" else "未检查/不可用"}", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onCheckRoot, enabled = !state.themeOperationRunning, modifier = Modifier.fillMaxWidth()) { Text("检查 Root") }
        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(state.themeMode == ThemeMode.ICON_PACK, { onThemeMode(ThemeMode.ICON_PACK) }); Text("原图标包"); RadioButton(state.themeMode == ThemeMode.MATERIAL_YOU, { onThemeMode(ThemeMode.MATERIAL_YOU) }); Text("Material You") }
        if (state.themeMode == ThemeMode.MATERIAL_YOU) MonetSummary(state, onMonetPreview, onApplyMonet)
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp)) }
        if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.matches, key = { it.app.packageName + it.app.launcherActivity }) { match -> AppRow(match, state.iconPack, state.monet.sources[match.app.packageName + "#" + match.app.launcherActivity], state.monet.generated[match.app.packageName + "#" + match.app.launcherActivity], state.themeMode == ThemeMode.MATERIAL_YOU) }
        }
        Button(onClick = onGenerate, enabled = state.iconPack != null && state.matchedCount > 0 && !state.loading, modifier = Modifier.fillMaxWidth()) { Text("生成 Xiaomi icons") }
        Button(onClick = onApplyTheme, enabled = state.iconPack != null && state.matchedCount > 0 && state.rootAvailable && !state.themeOperationRunning, modifier = Modifier.fillMaxWidth()) { Text("应用到系统") }
        Button(onClick = onRestoreTheme, enabled = state.rootAvailable && !state.themeOperationRunning, modifier = Modifier.fillMaxWidth()) { Text("恢复原主题") }
        Button(onClick = onRefreshCache, enabled = state.rootAvailable && !state.themeOperationRunning, modifier = Modifier.fillMaxWidth()) { Text("刷新图标缓存") }
        Button(onClick = onForceRestart, enabled = state.rootAvailable && !state.themeOperationRunning, modifier = Modifier.fillMaxWidth()) { Text("强制重启桌面（仅在图标未刷新时使用）") }
    }
}

@Composable private fun MonetSummary(state: ConverterUiState, onPreview: () -> Unit, onApply: () -> Unit) = Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
    Column(Modifier.padding(12.dp)) {
        val palette = state.monet.palette
        Text("Current Mode: ${if (state.monet.dark) "Dark" else "Light"}")
        if (palette != null) { val colors = palette.colors(state.monet.dark); Text("Background: #${"%08X".format(colors.first)}  Foreground: #${"%08X".format(colors.second)}") }
        Text("Native ${state.monet.sourceCount(MonetGlyphSource.NATIVE_MONOCHROME)} · Adaptive ${state.monet.sourceCount(MonetGlyphSource.ADAPTIVE_FOREGROUND)} · Icon Pack ${state.monet.sourceCount(MonetGlyphSource.ICON_PACK_GLYPH)} · Unavailable ${state.monet.sourceCount(MonetGlyphSource.UNAVAILABLE)}")
        Button(onClick = onPreview, modifier = Modifier.fillMaxWidth()) { Text("生成 Material You 预览") }
        Button(onClick = onApply, enabled = state.rootAvailable && state.monet.generated.isNotEmpty() && !state.themeOperationRunning, modifier = Modifier.fillMaxWidth()) { Text("应用 Material You 到系统") }
    }
}

@Composable private fun DiagnosticSummary(report: com.wikiglobal.iconconverter.hyperos.ThemeCompatibilityReport, onExport: () -> Unit) = Card(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
    Column(Modifier.padding(12.dp)) {
        Text("HyperOS Theme Compatibility", style = MaterialTheme.typography.titleMedium)
        Text("Root read-only: ${report.rootAvailable}")
        Text("HyperOS: ${report.systemProperties["ro.mi.os.version.name"] ?: "NOT_FOUND"}")
        Text("Theme paths: ${report.paths.size}  ·  ZIP archives: ${report.archives.count { it.isZipCompatible }}")
        Text("Launcher: ${report.launcher?.versionName ?: "NOT_FOUND"}  ·  Adaptive: ${report.adaptiveIcons.adaptiveNative}  ·  Monochrome: ${report.adaptiveIcons.nativeMonochrome}")
        Button(onClick = onExport, modifier = Modifier.padding(top = 8.dp)) { Text("导出诊断报告") }
    }
}

@Composable private fun PackSummary(pack: IconPack, state: ConverterUiState) = Card(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
    Column(Modifier.padding(12.dp)) {
        Text(pack.displayName, style = MaterialTheme.typography.titleMedium)
        Text("Icon mappings: ${pack.mappings.size}")
        Text("Launcher Activities: ${state.launcherActivityCount}  ·  Unique Packages: ${state.uniquePackageCount}")
        Text("已匹配: ${state.matchedCount}  未匹配: ${state.unmatchedCount}  冲突: ${state.conflictCount}  动态日历: ${state.dynamicCalendarCount}")
    }
}

@Composable private fun AppRow(match: IconMatch, pack: IconPack?, monetSource: MonetGlyphSource?, monetPng: ByteArray?, materialMode: Boolean) = Card(Modifier.fillMaxWidth()) {
    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        DrawablePreview(match.app.originalIcon, "原始")
        Spacer(Modifier.size(8.dp))
        if (materialMode) MonetPreview(monetPng) else match.drawableName?.let { pack?.drawableLoader(it) }?.let { DrawablePreview(it, "目标") }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(match.app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(match.app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(match.app.launcherActivity, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(match.status.name, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            monetSource?.let { Text(if (it == MonetGlyphSource.UNAVAILABLE) "Monet: 无法生成" else "Monet: ${it.name}", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable private fun MonetPreview(bytes: ByteArray?) {
    val bitmap = remember(bytes) { bytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) } }
    if (bitmap != null) Image(BitmapPainter(bitmap.asImageBitmap()), "Material You 最终预览", Modifier.size(48.dp)) else Text("无法生成", style = MaterialTheme.typography.labelSmall)
}

@Composable private fun DrawablePreview(drawable: Drawable, description: String) {
    val bitmap = remember(drawable) {
        Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { bitmap ->
            val old = drawable.bounds; drawable.setBounds(0, 0, 48, 48); drawable.draw(Canvas(bitmap)); drawable.bounds = old
        }
    }
    Image(BitmapPainter(bitmap.asImageBitmap()), description, Modifier.size(48.dp))
}
