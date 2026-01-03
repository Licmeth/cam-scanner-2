package com.licmeth.camscanner.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.licmeth.camscanner.helper.UserPreferences
import com.licmeth.camscanner.model.DebugOutputLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val enableDebugOverlay: Boolean = false,
    val debugOutputLevel: DebugOutputLevel = DebugOutputLevel.PREPROCESSED
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Observe preferences
        viewModelScope.launch {
            preferences.enableDebugOverlay.collect { enabled ->
                _uiState.value = _uiState.value.copy(enableDebugOverlay = enabled)
            }
        }

        viewModelScope.launch {
            preferences.debugOutputLevel.collect { level ->
                _uiState.value = _uiState.value.copy(debugOutputLevel = level)
            }
        }
    }

    fun setDebugOverlay(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setEnableDebugOverlay(enabled)
        }
    }

    fun setDebugOutputLevel(level: DebugOutputLevel) {
        viewModelScope.launch {
            preferences.setDebugOutputLevel(level)
        }
    }
}
