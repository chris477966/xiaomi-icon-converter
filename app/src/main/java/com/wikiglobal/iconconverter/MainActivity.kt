package com.wikiglobal.iconconverter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import com.wikiglobal.iconconverter.ui.IconConverterScreen
import com.wikiglobal.iconconverter.ui.IconConverterViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: IconConverterViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsState()
            val openApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::selectApk) }
            val saveIcons = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(viewModel::generateTo) }
            MaterialTheme { Surface { IconConverterScreen(state, { openApk.launch(arrayOf("application/vnd.android.package-archive")) }, { saveIcons.launch("icons") }) } }
        }
    }
}
