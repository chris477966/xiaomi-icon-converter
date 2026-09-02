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

@Composable
fun IconConverterScreen(state: ConverterUiState, onSelect: () -> Unit, onGenerate: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onSelect, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("选择图标包 APK") }
        Spacer(Modifier.height(12.dp))
        state.iconPack?.let { PackSummary(it, state) }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp)) }
        if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.matches, key = { it.app.packageName + it.app.launcherActivity }) { match -> AppRow(match, state.iconPack) }
        }
        Button(onClick = onGenerate, enabled = state.iconPack != null && state.matchedCount > 0 && !state.loading, modifier = Modifier.fillMaxWidth()) { Text("生成 Xiaomi icons") }
    }
}

@Composable private fun PackSummary(pack: IconPack, state: ConverterUiState) = Card(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
    Column(Modifier.padding(12.dp)) {
        Text(pack.displayName, style = MaterialTheme.typography.titleMedium)
        Text("Icon mappings: ${pack.mappings.size}  ·  本机 Launcher App: ${state.launcherCount}")
        Text("已匹配: ${state.matchedCount}  未匹配: ${state.unmatchedCount}  冲突: ${state.conflictCount}  动态日历: ${state.dynamicCalendarCount}")
    }
}

@Composable private fun AppRow(match: IconMatch, pack: IconPack?) = Card(Modifier.fillMaxWidth()) {
    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        DrawablePreview(match.app.originalIcon, "原始")
        Spacer(Modifier.size(8.dp))
        match.drawableName?.let { pack?.drawableLoader(it) }?.let { DrawablePreview(it, "目标") }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(match.app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(match.app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(match.status.name, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable private fun DrawablePreview(drawable: Drawable, description: String) {
    val bitmap = remember(drawable) {
        Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { bitmap ->
            val old = drawable.bounds; drawable.setBounds(0, 0, 48, 48); drawable.draw(Canvas(bitmap)); drawable.bounds = old
        }
    }
    Image(BitmapPainter(bitmap.asImageBitmap()), description, Modifier.size(48.dp))
}
