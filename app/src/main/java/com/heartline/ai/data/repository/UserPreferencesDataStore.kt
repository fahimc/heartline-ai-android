package com.heartline.ai.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userSettings by preferencesDataStore(name = "heartline_settings")

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val aiProvider: String = "Mock",
    val responseLength: String = "Normal",
    val memoryRetrieval: String = "Basic",
    val theme: String = "System",
    val chatWallpaper: String = "Warm",
    val bubbleStyle: String = "Soft"
)

class UserPreferencesDataStore(private val context: Context) {
    private object Keys {
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val aiProvider = stringPreferencesKey("ai_provider")
        val responseLength = stringPreferencesKey("response_length")
        val memoryRetrieval = stringPreferencesKey("memory_retrieval")
        val theme = stringPreferencesKey("theme")
        val chatWallpaper = stringPreferencesKey("chat_wallpaper")
        val bubbleStyle = stringPreferencesKey("bubble_style")
    }

    val settings: Flow<AppSettings> = context.userSettings.data.map { prefs ->
        AppSettings(
            onboardingComplete = prefs[Keys.onboardingComplete] ?: false,
            aiProvider = prefs[Keys.aiProvider] ?: "Mock",
            responseLength = prefs[Keys.responseLength] ?: "Normal",
            memoryRetrieval = prefs[Keys.memoryRetrieval] ?: "Basic",
            theme = prefs[Keys.theme] ?: "System",
            chatWallpaper = prefs[Keys.chatWallpaper] ?: "Warm",
            bubbleStyle = prefs[Keys.bubbleStyle] ?: "Soft"
        )
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.userSettings.edit { it[Keys.onboardingComplete] = complete }
    }

    suspend fun updateAiSettings(provider: String, responseLength: String, memoryRetrieval: String) {
        context.userSettings.edit {
            it[Keys.aiProvider] = provider
            it[Keys.responseLength] = responseLength
            it[Keys.memoryRetrieval] = memoryRetrieval
        }
    }

    suspend fun updateAppearance(theme: String, wallpaper: String, bubbleStyle: String) {
        context.userSettings.edit {
            it[Keys.theme] = theme
            it[Keys.chatWallpaper] = wallpaper
            it[Keys.bubbleStyle] = bubbleStyle
        }
    }
}
