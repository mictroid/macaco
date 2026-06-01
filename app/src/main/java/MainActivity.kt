package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.navigation.NavGraph
import com.example.myapplication.ui.theme.WanderlogTheme
import com.example.myapplication.ui.viewmodel.JournalViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as TravelJournalApp
        val factory = JournalViewModel.Factory(app.cloudEntrySync, app.preferencesManager, app.authRepository, app.billingManager)
        setContent {
            val vm: JournalViewModel = viewModel(factory = factory)
            val isDarkMode by vm.isDarkMode.collectAsState()
            val appTheme by vm.appTheme.collectAsState()
            val themeImageUri by vm.themeImageUri.collectAsState()
            WanderlogTheme(
                appTheme = appTheme,
                darkTheme = isDarkMode,
                backgroundImageUri = themeImageUri
            ) {
                NavGraph(viewModel = vm)
            }
        }
    }
}
