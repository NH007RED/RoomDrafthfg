package com.nhdeveloper.roomdraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.roomdraft.domain.model.AppSettings
import com.nhdeveloper.roomdraft.domain.model.ThemeMode
import com.nhdeveloper.roomdraft.navigation.AppNavHost
import com.nhdeveloper.roomdraft.theme.RoomDraftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { RoomDraftRoot() }
    }
}

@Composable
fun RoomDraftRoot() {
    val container = appContainer()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val dark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    RoomDraftTheme(darkTheme = dark) { AppNavHost() }
}
