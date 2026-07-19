package com.darren.anitrackpulse.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val darkModeKey = booleanPreferencesKey("dark_mode")
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")

    val darkModeFlow: Flow<Boolean> = context.dataStore.data.map { it[darkModeKey] ?: false }
    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[notificationsEnabledKey] ?: true }

    suspend fun setDarkMode(enabled: Boolean) { context.dataStore.edit { it[darkModeKey] = enabled } }
    suspend fun setNotificationsEnabled(enabled: Boolean) { context.dataStore.edit { it[notificationsEnabledKey] = enabled } }
}
