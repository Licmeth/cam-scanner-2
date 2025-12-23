package com.licmeth.camscanner.helper

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.licmeth.camscanner.model.DocumentAspectRatio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferences(val context: Context) {

    private val dataStore = context.dataStore

    companion object {
        val KEY_ADJUST_ASPECT_RATIO = booleanPreferencesKey("adjust_aspect_ratio")
        val KEY_TARGET_ASPECT_RATIO = intPreferencesKey("target_aspect_ratio")
    }

    val adjustAspectRatio: Flow<Boolean> = dataStore.data.map { pref -> pref[KEY_ADJUST_ASPECT_RATIO] ?: false }
    val targetAspectRatio: Flow<DocumentAspectRatio> = dataStore.data.map { pref -> DocumentAspectRatio.of(pref[KEY_TARGET_ASPECT_RATIO] ?: 1) }


    suspend fun setAdjustAspectRatio(value: Boolean) {
        dataStore.edit { pref -> pref[KEY_ADJUST_ASPECT_RATIO] = value }
    }

    suspend fun setTargetAspectRatio(value: DocumentAspectRatio) {
        dataStore.edit { pref -> pref[KEY_TARGET_ASPECT_RATIO] = value.value }
    }
}