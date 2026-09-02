package com.wikiglobal.iconconverter.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wikiglobal.iconconverter.compiler.XiaomiIconCompiler
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

data class ConverterUiState(
    val loading: Boolean = true,
    val iconPack: IconPack? = null,
    val matches: List<IconMatch> = emptyList(),
    val launcherCount: Int = 0,
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
    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    init { reloadApps() }

    private fun reloadApps() = viewModelScope.launch {
        val apps = withContext(Dispatchers.IO) { appsRepository.launcherApps() }
        _uiState.value = _uiState.value.copy(loading = false, launcherCount = apps.size, matches = apps.map { IconMatch(it, MatchStatus.UNMATCHED, com.wikiglobal.iconconverter.model.MatchConfidence.NONE) })
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
            _uiState.value = ConverterUiState(false, pack, matches, apps.size)
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
}
