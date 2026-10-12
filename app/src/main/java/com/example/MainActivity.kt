package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.LexiViewModel
import com.example.ui.screens.MainDashboard
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
            com.example.ui.screens.DataStartup {
            val viewModel: LexiViewModel = viewModel()
            val reload by viewModel.requiresDataReload.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(reload) {
                if (reload) { viewModelStore.clear(); recreate() }
            }
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColorPref by viewModel.dynamicColor.collectAsStateWithLifecycle()

            val isDarkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MyApplicationTheme(
                darkTheme = isDarkTheme,
                dynamicColor = dynamicColorPref
            ) {
                MainDashboard(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
            }
            }
        }
    }
}
