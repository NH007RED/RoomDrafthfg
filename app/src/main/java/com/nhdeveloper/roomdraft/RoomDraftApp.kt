package com.nhdeveloper.roomdraft

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nhdeveloper.roomdraft.data.local.AppDatabase
import com.nhdeveloper.roomdraft.data.repository.ProjectRepository
import com.nhdeveloper.roomdraft.data.repository.SettingsRepository

class AppContainer(context: Context) {
    private val database = AppDatabase.build(context)
    val projects = ProjectRepository(database)
    val settings = SettingsRepository(context)
}

class RoomDraftApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as RoomDraftApp).container

class VMFactory(private val block: () -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = block() as T
}
