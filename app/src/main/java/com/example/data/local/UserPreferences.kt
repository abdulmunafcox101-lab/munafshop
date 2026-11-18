package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppLanguage
import com.example.data.model.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "munaf_prefs")

class UserPreferences(private val context: Context) {
    private val keyLanguage = stringPreferencesKey("language")
    private val keyThemeMode = stringPreferencesKey("theme_mode")
    private val keyVoiceNotif = booleanPreferencesKey("voice_notif")
    private val keyActiveBranch = stringPreferencesKey("active_branch_id")
    private val keyActiveOperator = stringPreferencesKey("active_operator_id")

    val languageFlow: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[keyLanguage])
    }

    val themeModeFlow: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        AppThemeMode.fromName(prefs[keyThemeMode])
    }

    val voiceNotifFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[keyVoiceNotif] ?: true
    }

    val activeBranchIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyActiveBranch] ?: ""
    }

    val activeOperatorIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyActiveOperator] ?: "OWNER"
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[keyLanguage] = language.code }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[keyThemeMode] = mode.name }
    }

    suspend fun setVoiceNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[keyVoiceNotif] = enabled }
    }

    suspend fun setActiveBranchId(branchId: String) {
        context.dataStore.edit { it[keyActiveBranch] = branchId }
    }

    suspend fun setActiveOperatorId(operatorId: String) {
        context.dataStore.edit { it[keyActiveOperator] = operatorId }
    }
}
