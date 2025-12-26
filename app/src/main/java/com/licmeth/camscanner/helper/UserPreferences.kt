package com.licmeth.camscanner.helper

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.licmeth.camscanner.model.ColorProfile
import com.licmeth.camscanner.model.DebugOutputLevel
import com.licmeth.camscanner.model.DocumentAspectRatio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferences(val context: Context) {

    private val dataStore = context.dataStore

    companion object {
        val KEY_TARGET_ASPECT_RATIO = intPreferencesKey("target_aspect_ratio")
        val KEY_ENABLE_DEBUG_OVERLAY = booleanPreferencesKey("enable_debug_overlay")
        val KEY_DEBUG_OUTPUT_LEVEL = intPreferencesKey("debug_output_level")
        val KEY_USE_FLASH = booleanPreferencesKey("use_flash")
        val KEY_COLOR_PROFILE = intPreferencesKey("color_profile")
    }

    val targetAspectRatio: Flow<DocumentAspectRatio> = dataStore.data.map { pref -> DocumentAspectRatio.of(pref[KEY_TARGET_ASPECT_RATIO] ?: DocumentAspectRatio.DIN_476_2.value) }
    val enableDebugOverlay: Flow<Boolean> = dataStore.data.map { pref -> pref[KEY_ENABLE_DEBUG_OVERLAY] ?: false }
    val debugOutputLevel: Flow<DebugOutputLevel> = dataStore.data.map { pref -> DebugOutputLevel.of(pref[KEY_DEBUG_OUTPUT_LEVEL] ?: DebugOutputLevel.PREPROCESSED.value) }
    val useFlash: Flow<Boolean> = dataStore.data.map { pref -> pref[KEY_USE_FLASH] ?: false }
    val colorProfile: Flow<ColorProfile> = dataStore.data.map { pref -> ColorProfile.of(pref[KEY_COLOR_PROFILE] ?: ColorProfile.COLOR.value) }

    suspend fun setTargetAspectRatio(value: DocumentAspectRatio) {
        dataStore.edit { pref -> pref[KEY_TARGET_ASPECT_RATIO] = value.value }
    }

    suspend fun setEnableDebugOverlay(value: Boolean) {
        dataStore.edit { pref -> pref[KEY_ENABLE_DEBUG_OVERLAY] = value }
    }

    suspend fun setDebugOutputLevel(value: DebugOutputLevel) {
        dataStore.edit { pref -> pref[KEY_DEBUG_OUTPUT_LEVEL] = value.value }
    }

    suspend fun setUseFlash(value: Boolean) {
        dataStore.edit { pref -> pref[KEY_USE_FLASH] = value }
    }

    suspend fun setColorProfile(value: ColorProfile) {
        dataStore.edit { pref -> pref[KEY_COLOR_PROFILE] = value.value }
    }
}