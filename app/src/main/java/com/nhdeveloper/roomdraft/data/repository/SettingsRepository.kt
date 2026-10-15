package com.nhdeveloper.roomdraft.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nhdeveloper.roomdraft.domain.model.AppSettings
import com.nhdeveloper.roomdraft.domain.model.MeasureUnit
import com.nhdeveloper.roomdraft.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "roomdraft_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val UNIT = stringPreferencesKey("default_unit")
        val GRID = booleanPreferencesKey("show_grid")
        val SPACING = intPreferencesKey("grid_spacing_cm")
        val SNAP = booleanPreferencesKey("snap_to_grid")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val defaults = AppSettings()
            AppSettings(
                themeMode = ThemeMode.values().firstOrNull { it.name == p[Keys.THEME] } ?: defaults.themeMode,
                defaultUnit = MeasureUnit.values().firstOrNull { it.name == p[Keys.UNIT] } ?: defaults.defaultUnit,
                showGrid = p[Keys.GRID] ?: defaults.showGrid,
                gridSpacingCm = p[Keys.SPACING] ?: defaults.gridSpacingCm,
                snapToGrid = p[Keys.SNAP] ?: defaults.snapToGrid
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) { context.settingsStore.edit { it[Keys.THEME] = mode.name } }
    suspend fun setDefaultUnit(unit: MeasureUnit) { context.settingsStore.edit { it[Keys.UNIT] = unit.name } }
    suspend fun setShowGrid(show: Boolean) { context.settingsStore.edit { it[Keys.GRID] = show } }
    suspend fun setGridSpacing(cm: Int) { context.settingsStore.edit { it[Keys.SPACING] = cm } }
    suspend fun setSnapToGrid(snap: Boolean) { context.settingsStore.edit { it[Keys.SNAP] = snap } }
}
