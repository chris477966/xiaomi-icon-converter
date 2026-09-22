package com.wikiglobal.iconconverter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wikiglobal.iconconverter.hyperos.IconPackStyle
import com.wikiglobal.iconconverter.hyperos.IconShape
import com.wikiglobal.iconconverter.hyperos.LawniconsProviderStatus
import com.wikiglobal.iconconverter.hyperos.MaterialColorMode
import com.wikiglobal.iconconverter.hyperos.MaterialIconShapeRenderer
import com.wikiglobal.iconconverter.hyperos.MaterialSourceOverride
import com.wikiglobal.iconconverter.hyperos.MaterialStyle
import com.wikiglobal.iconconverter.hyperos.MonetGlyphSource
import com.wikiglobal.iconconverter.model.IconAssignmentType
import com.wikiglobal.iconconverter.model.IconEntry
import com.wikiglobal.iconconverter.model.IconPack
import com.wikiglobal.iconconverter.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CardShape = RoundedCornerShape(24.dp)
private val ControlShape = RoundedCornerShape(18.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconConverterScreen(
    state: ConverterUiState,
    onSelect: () -> Unit,
    onOpenPicker: (InstalledApp) -> Unit,
    onClosePicker: () -> Unit,
    onPickerPack: (String) -> Unit,
    onSearchIcons: (String) -> Unit,
    onChooseIcon: (IconEntry) -> Unit,
    onRestoreAutomatic: (String) -> Unit,
    onSetActivePack: (String) -> Unit,
    onDeletePack: (String) -> Unit,
    onGenerate: () -> Unit,
    onCheckHyperOs: () -> Unit,
    onExportReport: () -> Unit,
    onExportRouteReport: () -> Unit,
    onExportMaterialReport: () -> Unit,
    onCheckRoot: () -> Unit,
    onApplyTheme: () -> Unit,
    onRestoreTheme: () -> Unit,
    onRefreshCache: () -> Unit,
    onForceRestart: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onMonetPreview: () -> Unit,
    onApplyMonet: () -> Unit,
    onMaterialOverride: (String, MaterialSourceOverride) -> Unit,
    onMaterialStyle: (MaterialStyle) -> Unit,
    onIconPackStyle: (IconPackStyle) -> Unit,
    onConfirmRebase: () -> Unit,
    onDismissRebase: () -> Unit,
) {
    var tools by remember { mutableStateOf(false) }
    var managePacks by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val canApply = (
        state.themeMode == ThemeMode.ICON_PACK &&
            state.iconPack?.entries?.isNotEmpty() == true &&
            state.matchedCount > 0
        ) || (
        state.themeMode == ThemeMode.MATERIAL_YOU &&
            state.monet.generated.isNotEmpty()
        )

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it) }
    }
    BackHandler(enabled = tools) { tools = false }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            AppleHeader(
                settings = tools,
                onBack = { tools = false },
                onSettings = { tools = true },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!tools) {
                GlassBottomDock(
                    selectedMode = state.themeMode,
                    onMode = onThemeMode,
                    applyLabel = if (state.rootAvailable) "应用到 HyperOS 3" else "检查 Root 并继续",
                    applyEnabled = canApply && !state.themeOperationRunning,
                    onApply = if (state.rootAvailable) onApplyTheme else onCheckRoot,
                )
            }
        },
    ) { padding ->
        if (tools) {
            SystemToolsScreen(
                modifier = Modifier.padding(padding),
                state = state,
                check = onCheckRoot,
                restore = onRestoreTheme,
                refresh = onRefreshCache,
                restart = onForceRestart,
                diagnose = onCheckHyperOs,
                export = onExportReport,
                exportRoutes = onExportRouteReport,
            )
        } else {
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (state.themeMode) {
                    ThemeMode.ICON_PACK -> Column(Modifier.fillMaxSize()) {
                        SourceCard(state, onSelect, { managePacks = true })
                        IconPackScreen(
                            state = state,
                            setStyle = onIconPackStyle,
                            openPicker = onOpenPicker,
                            restoreAutomatic = onRestoreAutomatic,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ThemeMode.MATERIAL_YOU -> MaterialYouScreen(
                        state = state,
                        generate = onMonetPreview,
                        setOverride = onMaterialOverride,
                        exportReport = onExportMaterialReport,
                        setStyle = onMaterialStyle,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (state.themeOperationRunning) ProcessingOverlay()
        if (state.rebaseConfirmationRequired) {
            AlertDialog(
                onDismissRequest = onDismissRebase,
                title = { Text("检测到图标主题已更换") },
                text = { Text("当前图标主题可能已通过系统主题商店或其他工具更换。继续后，本应用会把当前主题作为新的基础主题，并只在其上替换所选图标。以后“恢复主题”将恢复到当前这套主题。") },
                confirmButton = { TextButton(onConfirmRebase) { Text("以当前主题为基础并应用") } },
                dismissButton = { TextButton(onDismissRebase) { Text("取消") } },
            )
        }
        if (managePacks) {
            IconPackManagementDialog(
                state = state,
                setActive = {
                    onSetActivePack(it)
                    managePacks = false
                },
                delete = onDeletePack,
                dismiss = { managePacks = false },
            )
        }
        state.iconPickerApp?.let { app ->
            IconPickerSheet(state, app, onPickerPack, onSearchIcons, onChooseIcon, onClosePicker)
        }
    }

    // The dedicated callback remains part of the public screen contract. The bottom action
    // uses applyCurrentModeToSystem so both visual modes keep one consistent interaction.
    remember(onApplyMonet) { onApplyMonet }
    remember(onGenerate) { onGenerate }
}

@Composable
private fun AppleHeader(settings: Boolean, onBack: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (settings) {
            GlassIconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("设置", style = MaterialTheme.typography.displaySmall)
                Text("系统与主题维护", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.weight(1f)) {
                Text("HyperIcon", style = MaterialTheme.typography.displaySmall)
                Text("让桌面图标回到同一种语言", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GlassIconButton(onSettings) { Icon(Icons.Filled.Settings, contentDescription = "设置") }
        }
    }
}

@Composable
private fun GlassIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.size(46.dp).shadow(8.dp, CircleShape),
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

@Composable
private fun GlassBottomDock(
    selectedMode: ThemeMode,
    onMode: (ThemeMode) -> Unit,
    applyLabel: String,
    applyEnabled: Boolean,
    onApply: () -> Unit,
) {
    val glassShape = RoundedCornerShape(32.dp)
    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .shadow(20.dp, glassShape),
        shape = glassShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Box(
            Modifier.background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.16f), MaterialTheme.colorScheme.surface.copy(alpha = 0.04f)),
                ),
            ),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f)) {
                    Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ModeSegment("图标包", selectedMode == ThemeMode.ICON_PACK, { onMode(ThemeMode.ICON_PACK) }, Modifier.weight(1f))
                        ModeSegment("Material You", selectedMode == ThemeMode.MATERIAL_YOU, { onMode(ThemeMode.MATERIAL_YOU) }, Modifier.weight(1f))
                    }
                }
                Button(
                    onClick = onApply,
                    enabled = applyEnabled,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) { Text(applyLabel, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun ModeSegment(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(40.dp),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 3.dp else 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SourceCard(state: ConverterUiState, select: () -> Unit, manage: () -> Unit) {
    AppleCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    SectionEyebrow("当前图标包")
                    Text(state.iconPack?.displayName ?: "选择一个图标包", style = MaterialTheme.typography.titleLarge)
                    state.iconPack?.let { pack ->
                        if (pack.diagnostics.resourceIndexFailed) {
                            Text("图标包映射已读取，但图标资源索引失败", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Text(pack.diagnostics.summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(
                                "${pack.entries.size} 个图标 · 已匹配 ${state.iconPackResolvedMatchCount} / ${state.launcherActivityCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                StatusDot(active = state.iconPack != null)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(select, shape = ControlShape, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
                    Text(if (state.iconPack == null) "导入图标包" else "更换")
                }
                OutlinedButton(
                    onClick = manage,
                    enabled = state.iconPacks.isNotEmpty(),
                    shape = ControlShape,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                ) { Text("管理") }
            }
        }
    }
}

@Composable
private fun IconPackScreen(
    state: ConverterUiState,
    setStyle: (IconPackStyle) -> Unit,
    openPicker: (InstalledApp) -> Unit,
    restoreAutomatic: (String) -> Unit,
    modifier: Modifier,
) {
    var shapeSheet by remember { mutableStateOf(false) }
    Column(modifier.padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("图标预览", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (state.iconPack == null) "导入后可浏览并手动替换" else "点击图标可选择其他资源",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OptionPill(shapeLabel(state.iconPackStyle.shape)) { shapeSheet = true }
        }
        if (state.iconPack == null) {
            EmptyState("还没有图标包", "从上方导入一个 APK，即可开始预览与匹配。", Modifier.fillMaxSize())
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.matches, key = { componentPreviewKey(it.app.packageName, it.app.launcherActivity) }) { match ->
                    val previewKey = componentPreviewKey(match.app.packageName, match.app.launcherActivity)
                    IconPackAppTile(
                        label = match.app.label,
                        bitmap = state.iconPreviews.target[previewKey] ?: state.iconPreviews.original[previewKey],
                        assignmentType = match.assignmentType,
                        matched = match.drawableName != null,
                        enabled = state.iconPack?.entries?.isNotEmpty() == true,
                        onClick = { openPicker(match.app) },
                        onRestore = { restoreAutomatic(match.app.packageName) },
                    )
                }
            }
        }
    }
    if (shapeSheet) {
        SharedShapeSheet(
            selected = state.iconPackStyle.shape,
            onSelect = {
                setStyle(state.iconPackStyle.copy(shape = it))
                shapeSheet = false
            },
            onDismiss = { shapeSheet = false },
        )
    }
}

@Composable
private fun IconPackAppTile(
    label: String,
    bitmap: android.graphics.Bitmap?,
    assignmentType: IconAssignmentType,
    matched: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(Modifier.clickable(enabled = enabled, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        PreviewTile { CachedPreview(bitmap, "最终图标", 72.dp) }
        Spacer(Modifier.height(7.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
        Text(
            when {
                assignmentType == IconAssignmentType.MANUAL -> "手动选择"
                matched -> "自动匹配"
                else -> "未匹配"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (assignmentType == IconAssignmentType.MANUAL) {
            TextButton(onRestore, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                Text("恢复自动", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconPickerSheet(
    state: ConverterUiState,
    app: InstalledApp,
    selectPack: (String) -> Unit,
    search: (String) -> Unit,
    choose: (IconEntry) -> Unit,
    dismiss: () -> Unit,
) = ModalBottomSheet(
    onDismissRequest = dismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text("为 ${app.label} 选择图标", style = MaterialTheme.typography.headlineSmall)
        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        var packMenu by remember { mutableStateOf(false) }
        val pickerPack = state.iconPacks.firstOrNull { it.id == state.pickerIconPackId }
        Box(Modifier.padding(top = 8.dp)) {
            TextButton({ packMenu = true }, enabled = state.iconPacks.size > 1) { Text("${pickerPack?.displayName ?: "未选择"}  ▾") }
            DropdownMenu(expanded = packMenu, onDismissRequest = { packMenu = false }) {
                state.iconPacks.forEach { pack ->
                    DropdownMenuItem(
                        text = { Text(pack.displayName) },
                        onClick = {
                            selectPack(pack.id)
                            packMenu = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            state.iconSearchQuery,
            search,
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            singleLine = true,
            label = { Text("搜索 App、包名或资源名") },
            shape = ControlShape,
        )
        if (state.iconSearchResults.isEmpty()) {
            Text("没有结果；清空搜索框可浏览全部图标", Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyVerticalGrid(
            GridCells.Fixed(4),
            Modifier.fillMaxWidth().heightIn(max = 520.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                state.iconSearchResults,
                key = { it.iconPackId + ":" + it.resourceType + "/" + it.resourceName + ":" + it.resourceIdentifier },
            ) { entry -> IconEntryTile(entry, state.iconPacks.firstOrNull { it.id == entry.iconPackId }, choose) }
        }
    }
}

@Composable
private fun IconEntryTile(entry: IconEntry, pack: IconPack?, choose: (IconEntry) -> Unit) {
    var bitmap by remember(entry.iconPackId, entry.resourceType, entry.resourceName, entry.resourceIdentifier) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    LaunchedEffect(entry.iconPackId, entry.resourceType, entry.resourceName, entry.resourceIdentifier) {
        bitmap = withContext(Dispatchers.IO) {
            pack?.let { source ->
                (source.resourceLoader?.invoke(entry.resourceIdentifier) ?: source.drawableLoader(entry.resourceName))
                    ?.let { PreviewBitmapPipeline.rasterizeIcon(it) }
            }
        }
    }
    Column(Modifier.clickable { choose(entry) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
            Box(Modifier.padding(6.dp), contentAlignment = Alignment.Center) { CachedPreview(bitmap, entry.resourceName, 56.dp) }
        }
        Text(entry.resourceName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        entry.mappedPackageNames.firstOrNull()?.let {
            Text(
                it.substringAfterLast('.'),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IconPackManagementDialog(
    state: ConverterUiState,
    setActive: (String) -> Unit,
    delete: (String) -> Unit,
    dismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = dismiss,
    title = { Text("图标包管理") },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.iconPacks.forEach { pack ->
                Surface(shape = ControlShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(state.activeIconPackId == pack.id, { setActive(pack.id) })
                        Column(Modifier.weight(1f)) {
                            Text(pack.displayName, style = MaterialTheme.typography.titleMedium)
                            Text("${pack.entries.size} 个图标", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton({ delete(pack.id) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    },
    confirmButton = { TextButton(dismiss) { Text("完成") } },
)

@Composable
private fun MaterialYouScreen(
    state: ConverterUiState,
    generate: () -> Unit,
    setOverride: (String, MaterialSourceOverride) -> Unit,
    exportReport: () -> Unit,
    setStyle: (MaterialStyle) -> Unit,
    modifier: Modifier,
) {
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selected = remember(state.matches, selectedKey) {
        state.matches.firstOrNull { componentPreviewKey(it.app.packageName, it.app.launcherActivity) == selectedKey }
    }
    val previewCache = remember(state.monet.generationId) { MaterialPreviewBitmapCache() }
    val style = state.monet.style
    var seedDialog by remember { mutableStateOf(false) }
    var confirmFollow by remember { mutableStateOf(false) }
    var colorSheet by remember { mutableStateOf(false) }
    var shapeSheet by remember { mutableStateOf(false) }

    Box(modifier) {
        LazyVerticalGrid(
            GridCells.Fixed(3),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text("Material You", style = MaterialTheme.typography.headlineSmall)
                        Text("统一配色，同时保留每个 App 的辨识度", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AppleCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionEyebrow("视觉样式")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OptionPill(materialColorModeLabel(style.colorMode), Modifier.weight(1f)) { colorSheet = true }
                                OptionPill(shapeLabel(style.shape), Modifier.weight(1f)) { shapeSheet = true }
                            }
                            if (style.colorMode != MaterialColorMode.CUSTOM) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("跟随壁纸", style = MaterialTheme.typography.titleMedium)
                                        Text("壁纸配色变化时自动同步", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        style.followWallpaperMonet,
                                        { checked ->
                                            if (checked) confirmFollow = true
                                            else setStyle(style.copy(followWallpaperMonet = false))
                                        },
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        Modifier.size(28.dp),
                                        color = Color(style.customSeedColor),
                                        shape = CircleShape,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    ) {}
                                    TextButton({ seedDialog = true }) { Text("修改自定义颜色") }
                                }
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { MaterialStatusCard(state.monet, state.matches.size, exportReport) }
            if (state.loading) {
                item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape)) }
            }
            if (state.monet.generated.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(generate, Modifier.fillMaxWidth().height(50.dp), shape = ControlShape) { Text("生成预览") }
                }
            }
            items(
                state.matches,
                key = { componentPreviewKey(it.app.packageName, it.app.launcherActivity) },
                contentType = { "material-app" },
            ) { match ->
                val componentKey = componentPreviewKey(match.app.packageName, match.app.launcherActivity)
                MaterialAppTile(
                    match.app.label,
                    componentKey,
                    state.monet.generationId,
                    state.monet.previewTargetSize,
                    state.monet.generated[componentKey],
                    state.iconPreviews.original[componentKey],
                    previewCache,
                ) { selectedKey = componentKey }
            }
        }
        if (colorSheet) {
            MaterialColorSheet(
                style,
                {
                    setStyle(it)
                    colorSheet = false
                },
                { colorSheet = false },
                { seedDialog = true },
            )
        }
        if (shapeSheet) {
            SharedShapeSheet(
                style.shape,
                {
                    setStyle(style.copy(shape = it))
                    shapeSheet = false
                },
                { shapeSheet = false },
                state.monet.palette,
                state.monet.dark,
            )
        }
    }
    selected?.let { match ->
        val componentKey = match.app.packageName + "#" + match.app.launcherActivity
        MaterialOverrideSheet(
            match.app.label,
            state.monet.sources[componentKey],
            state.monet.availability[componentKey] ?: MaterialSourceAvailability(),
            state.monet.overrides[componentKey] ?: MaterialSourceOverride.AUTO,
            { selectedKey = null },
        ) { override ->
            setOverride(componentKey, override)
            selectedKey = null
        }
    }
    if (seedDialog) {
        SeedColorDialog(
            style.customSeedColor,
            {
                setStyle(style.copy(customSeedColor = it))
                seedDialog = false
            },
            { seedDialog = false },
        )
    }
    if (confirmFollow) {
        AlertDialog(
            onDismissRequest = { confirmFollow = false },
            title = { Text("启用壁纸变化后自动应用？") },
            text = { Text("应用运行期间会自动检测壁纸配色变化并更新图标；如果应用进程已被系统关闭，将在下次打开应用时同步最新系统配色。自动更新需要已授权的 Root 权限，不会修改系统分区或动态图标。") },
            confirmButton = {
                TextButton({
                    setStyle(style.copy(followWallpaperMonet = true))
                    confirmFollow = false
                }) { Text("确认") }
            },
            dismissButton = { TextButton({ confirmFollow = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialColorSheet(
    style: MaterialStyle,
    onSelect: (MaterialStyle) -> Unit,
    onDismiss: () -> Unit,
    openSeed: () -> Unit,
) = ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("颜色来源", style = MaterialTheme.typography.headlineSmall)
        ColorModeOption("壁纸自动", "使用当前桌面壁纸主色生成配色", style.colorMode == MaterialColorMode.WALLPAPER_AUTO) {
            onSelect(style.copy(colorMode = MaterialColorMode.WALLPAPER_AUTO))
        }
        ColorModeOption("系统配色", "使用 HyperOS / Android 当前系统配色", style.colorMode == MaterialColorMode.SYSTEM_MONET) {
            onSelect(style.copy(colorMode = MaterialColorMode.SYSTEM_MONET))
        }
        ColorModeOption("自定义", "手动选择一枚主题色", style.colorMode == MaterialColorMode.CUSTOM) {
            onSelect(style.copy(colorMode = MaterialColorMode.CUSTOM, followWallpaperMonet = false))
            openSeed()
        }
    }
}

@Composable
private fun ColorModeOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = ControlShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, onClick)
            Column(Modifier.padding(start = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedShapeSheet(
    selected: IconShape,
    onSelect: (IconShape) -> Unit,
    onDismiss: () -> Unit,
    palette: com.wikiglobal.iconconverter.hyperos.MonetPalette? = null,
    dark: Boolean = false,
) = ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("图标形状", style = MaterialTheme.typography.headlineSmall)
        IconShape.entries.forEach { shape ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSelect(shape) },
                shape = ControlShape,
                color = if (shape == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    MaterialShapePreview(shape, palette, dark)
                    Text(shapeLabel(shape), Modifier.padding(start = 14.dp), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    if (shape == selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Uses the production shape renderer directly; no approximation or source discovery. */
@Composable
private fun MaterialShapePreview(
    shape: IconShape,
    palette: com.wikiglobal.iconconverter.hyperos.MonetPalette?,
    dark: Boolean,
) {
    val color = palette?.colors(dark)?.first ?: 0xffd9e2ff.toInt()
    var bitmap by remember(shape, color) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(shape, color) {
        bitmap = withContext(Dispatchers.Default) { MaterialIconShapeRenderer.previewBitmap(shape, color) }
    }
    CachedPreview(bitmap, shapeLabel(shape), 52.dp)
}

@Composable
private fun PaletteSwatch(color: Int?, label: String) {
    if (color != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                Modifier.size(16.dp),
                color = Color(color),
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {}
            Text(label, Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun materialColorModeLabel(mode: MaterialColorMode) = when (mode) {
    MaterialColorMode.WALLPAPER_AUTO -> "壁纸自动"
    MaterialColorMode.SYSTEM_MONET -> "系统配色"
    MaterialColorMode.CUSTOM -> "自定义"
}

private fun paletteSourceLabel(resolution: com.wikiglobal.iconconverter.hyperos.MaterialPaletteResolution?) = when {
    resolution == null -> "未生成"
    resolution.fallbackUsed -> "系统配色"
    resolution.source == com.wikiglobal.iconconverter.hyperos.MaterialPaletteSource.WALLPAPER -> "壁纸自动"
    resolution.source == com.wikiglobal.iconconverter.hyperos.MaterialPaletteSource.SYSTEM_MONET -> "系统配色"
    else -> "自定义"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialOverrideSheet(
    label: String,
    source: MonetGlyphSource?,
    availability: MaterialSourceAvailability,
    selected: MaterialSourceOverride,
    onDismiss: () -> Unit,
    onSelect: (MaterialSourceOverride) -> Unit,
) = ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
        Text("当前来源：${materialSourceLabel(source)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MaterialOverrideOption("自动", MaterialSourceOverride.AUTO, true, selected, onSelect)
        if (availability.native) MaterialOverrideOption("官方单色", MaterialSourceOverride.NATIVE, true, selected, onSelect)
        if (availability.lawnicons) MaterialOverrideOption("Lawnicons", MaterialSourceOverride.LAWNICONS, true, selected, onSelect)
        if (availability.aosp) MaterialOverrideOption("Android 自动单色", MaterialSourceOverride.AOSP_FORCE, true, selected, onSelect)
        MaterialOverrideOption("保留原图", MaterialSourceOverride.KEEP, true, selected, onSelect)
    }
}

@Composable
private fun MaterialOverrideOption(
    label: String,
    value: MaterialSourceOverride,
    enabled: Boolean,
    selected: MaterialSourceOverride,
    onSelect: (MaterialSourceOverride) -> Unit,
) {
    val isSelected = value == selected
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onSelect(value) },
        enabled = enabled,
        shape = ControlShape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(isSelected, { onSelect(value) }, enabled = enabled)
            Text(label, Modifier.padding(start = 10.dp), color = if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.outline)
        }
    }
}

private fun materialSourceLabel(source: MonetGlyphSource?) = when (source) {
    MonetGlyphSource.NATIVE_MONOCHROME -> "官方单色"
    MonetGlyphSource.LAWNICONS_EXACT,
    MonetGlyphSource.LAWNICONS_PACKAGE,
    MonetGlyphSource.LAWNICONS_ALIAS -> "Lawnicons"
    MonetGlyphSource.AOSP_FORCED_MONOCHROME -> "Android 自动单色"
    MonetGlyphSource.MANUAL_KEEP -> "保留原图"
    else -> "不可用"
}

private fun shapeLabel(shape: IconShape) = when (shape) {
    IconShape.SYSTEM -> "系统"
    IconShape.HYPEROS -> "HyperOS"
    IconShape.CIRCLE -> "圆形"
    IconShape.SQUIRCLE -> "连续圆角"
    IconShape.ROUNDED_SQUARE -> "圆角方形"
    IconShape.SQUARE -> "方形"
    IconShape.TEARDROP -> "水滴"
}

@Composable
private fun SeedColorDialog(seed: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("#%06X".format(seed and 0xFFFFFF)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义颜色") },
        text = {
            OutlinedTextField(value, { value = it }, label = { Text("#RRGGBB") }, singleLine = true, shape = ControlShape)
        },
        confirmButton = {
            TextButton({
                value.removePrefix("#").toLongOrNull(16)?.takeIf { it <= 0xFFFFFF }?.let {
                    onConfirm((0xFF000000L or it).toInt())
                }
            }) { Text("应用") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SystemToolsScreen(
    modifier: Modifier,
    state: ConverterUiState,
    check: () -> Unit,
    restore: () -> Unit,
    refresh: () -> Unit,
    restart: () -> Unit,
    diagnose: () -> Unit,
    export: () -> Unit,
    exportRoutes: () -> Unit,
) = LazyColumn(
    modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
) {
    item {
        val lastApplySuccess = state.lastApplySummary?.let { summary ->
            summary.patchVerified && summary.archiveInstallVerified &&
                summary.unroutedComponents == 0 && summary.entryConflicts == 0 &&
                summary.launcherRefreshStatus == LauncherRefreshStatus.SUCCESS
        }
        AppleCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionEyebrow("系统权限")
                    Text(if (state.rootAvailable) "Root 已授权" else "Root 尚未检查", style = MaterialTheme.typography.titleLarge)
                    lastApplySuccess?.let {
                        Text(
                            if (it) "最近一次主题应用成功" else "最近一次应用存在异常",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                }
                StatusDot(active = state.rootAvailable)
            }
        }
    }
    item {
        SettingsGroup("主题维护") {
            SettingsAction("检查 Root", "确认系统权限状态", true, check)
            SettingsDivider()
            SettingsAction("恢复主题", "恢复到应用前的主题", state.rootAvailable, restore)
            SettingsDivider()
            SettingsAction("刷新图标缓存", "让桌面重新读取图标", state.rootAvailable, refresh)
            SettingsDivider()
            SettingsAction("重启桌面", "重启系统桌面进程", state.rootAvailable, restart)
        }
    }
    item {
        SettingsGroup("诊断与导出") {
            SettingsAction("兼容性检查", "检查当前 HyperOS 主题环境", true, diagnose)
            SettingsDivider()
            SettingsAction("导出诊断报告", "保留完整技术信息", state.diagnosticReport != null, export)
            SettingsDivider()
            SettingsAction("导出最近应用路由", "查看最近一次图标替换路径", state.lastApplyRoutes.isNotEmpty(), exportRoutes)
        }
    }
    item { Spacer(Modifier.height(16.dp)) }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppleCard(Modifier.fillMaxWidth(), content)
    }
}

@Composable
private fun SettingsAction(title: String, detail: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
            )
        }
        Text("›", fontSize = 28.sp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun SettingsDivider() {
    Spacer(
        Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    )
}

@Composable
private fun CachedPreview(bitmap: android.graphics.Bitmap?, description: String, size: Dp) {
    if (bitmap != null) {
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        val painter = remember(image) { BitmapPainter(image, filterQuality = FilterQuality.High) }
        Image(painter, description, Modifier.size(size), contentScale = ContentScale.Fit)
    } else {
        Surface(
            Modifier.size(size),
            shape = RoundedCornerShape((size.value * 0.24f).dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("保留", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MaterialStatusCard(monet: MonetUiState, total: Int, exportReport: () -> Unit) {
    val unavailable = remember(monet.sources) { monet.sources.values.count { it == MonetGlyphSource.UNAVAILABLE_LEGACY } }
    AppleCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionEyebrow("预览状态")
                    Text(if (monet.generated.isEmpty()) "等待生成" else "已生成 ${monet.generated.size} / $total", style = MaterialTheme.typography.titleLarge)
                }
                if (monet.diagnostics.isNotEmpty()) {
                    Surface(onClick = exportReport, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("i", Modifier.semantics { contentDescription = "诊断" }, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Text(
                "${if (monet.dark) "深色" else "浅色"} · ${if (monet.paletteResolution == null) materialColorModeLabel(monet.style.colorMode) else paletteSourceLabel(monet.paletteResolution)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val colors = monet.paletteResolution?.wallpaperColors
                if (colors != null) {
                    PaletteSwatch(colors.primary, "主色")
                    PaletteSwatch(colors.secondary, "辅助色")
                    PaletteSwatch(colors.tertiary, "第三色")
                } else {
                    monet.palette?.colors(monet.dark)?.let {
                        PaletteSwatch(it.first, "主色")
                        PaletteSwatch(it.second, "辅助色")
                    }
                }
            }
            if (monet.paletteResolution?.fallbackUsed == true) Text("当前使用系统配色", style = MaterialTheme.typography.bodySmall)
            if (monet.lawniconsProvider.status != LawniconsProviderStatus.READY && unavailable > 0) {
                Text("部分图标无法使用扩展单色资源", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MaterialAppTile(
    label: String,
    componentKey: String,
    generationId: Long,
    previewTargetSize: Int,
    png: ByteArray?,
    original: android.graphics.Bitmap?,
    cache: MaterialPreviewBitmapCache,
    onClick: () -> Unit,
) {
    key(generationId, componentKey, previewTargetSize) {
        var bitmap by remember(png, cache) { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(png, cache) {
            bitmap = png?.let { cache.preview(MaterialPreviewBitmapCache.Key(generationId, componentKey, previewTargetSize), it) }
        }
        Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
            PreviewTile {
                CachedPreview(
                    if (png == null) original else bitmap,
                    if (png == null) "当前" else "Material 预览",
                    PreviewBitmapPipeline.MATERIAL_PREVIEW_DP.dp,
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            Text(
                if (png == null) "保留原图" else "已生成",
                style = MaterialTheme.typography.labelSmall,
                color = if (png == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PreviewTile(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.size(92.dp),
        shape = RoundedCornerShape(25.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

@Composable
private fun AppleCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 1.dp,
        content = content,
    )
}

@Composable
private fun SectionEyebrow(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatusDot(active: Boolean) {
    Surface(
        modifier = Modifier.size(12.dp),
        shape = CircleShape,
        color = if (active) Color(0xFF34C759) else MaterialTheme.colorScheme.outline,
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.surface),
    ) {}
}

@Composable
private fun OptionPill(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(42.dp),
        onClick = onClick,
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        AppleCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(horizontal = 28.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(Modifier.size(64.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Text("＋", fontSize = 30.sp, color = MaterialTheme.colorScheme.primary) }
                }
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(detail, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProcessingOverlay() {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shadowElevation = 18.dp) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
                Text("正在处理主题…", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Preview(showBackground = true, locale = "zh", widthDp = 393, heightDp = 800)
@Composable
private fun SettingsPreview() = HyperIconTheme {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppleHeader(settings = true, onBack = {}, onSettings = {})
        SystemToolsScreen(Modifier.weight(1f), ConverterUiState(), {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true, locale = "zh", widthDp = 393, heightDp = 800)
@Composable
private fun MaterialYouPreview() = HyperIconTheme {
    MaterialYouScreen(
        ConverterUiState(loading = false, themeMode = ThemeMode.MATERIAL_YOU),
        {},
        { _, _ -> },
        {},
        {},
        Modifier.fillMaxSize(),
    )
}
