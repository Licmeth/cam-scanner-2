package com.licmeth.camscanner.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.licmeth.camscanner.helper.UserPreferences
import com.licmeth.camscanner.model.DocumentAspectRatio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.opencv.core.Point
import javax.inject.Inject

data class MainUiState(
    val isDocumentDetected: Boolean = false,
    val detectedCorners: Array<Point>? = null,
    val relativeCorners: Array<Point>? = null,
    val statusText: String = "Detecting document...",
    val useFlash: Boolean = false,
    val targetAspectRatio: DocumentAspectRatio = DocumentAspectRatio.NONE,
    val enableDebugOverlay: Boolean = false,
    val lastDebugBitmap: Bitmap? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MainUiState

        if (isDocumentDetected != other.isDocumentDetected) return false
        if (detectedCorners != null) {
            if (other.detectedCorners == null) return false
            if (!detectedCorners.contentEquals(other.detectedCorners)) return false
        } else if (other.detectedCorners != null) return false
        if (relativeCorners != null) {
            if (other.relativeCorners == null) return false
            if (!relativeCorners.contentEquals(other.relativeCorners)) return false
        } else if (other.relativeCorners != null) return false
        if (statusText != other.statusText) return false
        if (useFlash != other.useFlash) return false
        if (targetAspectRatio != other.targetAspectRatio) return false
        if (enableDebugOverlay != other.enableDebugOverlay) return false
        if (lastDebugBitmap != other.lastDebugBitmap) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isDocumentDetected.hashCode()
        result = 31 * result + (detectedCorners?.contentHashCode() ?: 0)
        result = 31 * result + (relativeCorners?.contentHashCode() ?: 0)
        result = 31 * result + statusText.hashCode()
        result = 31 * result + useFlash.hashCode()
        result = 31 * result + targetAspectRatio.hashCode()
        result = 31 * result + enableDebugOverlay.hashCode()
        result = 31 * result + (lastDebugBitmap?.hashCode() ?: 0)
        return result
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // Observe preferences
        viewModelScope.launch {
            preferences.useFlash.collect { enabled ->
                _uiState.value = _uiState.value.copy(useFlash = enabled)
            }
        }

        viewModelScope.launch {
            preferences.targetAspectRatio.collect { ratio ->
                _uiState.value = _uiState.value.copy(targetAspectRatio = ratio)
            }
        }

        viewModelScope.launch {
            preferences.enableDebugOverlay.collect { enabled ->
                _uiState.value = _uiState.value.copy(enableDebugOverlay = enabled)
            }
        }
    }

    fun setDetectedCorners(corners: Array<Point>?, relativeCorners: Array<Point>?) {
        _uiState.value = _uiState.value.copy(
            isDocumentDetected = corners != null,
            detectedCorners = corners,
            relativeCorners = relativeCorners,
            statusText = if (corners != null) "Document detected" else "Detecting document..."
        )
    }

    fun toggleFlash() {
        viewModelScope.launch {
            val newValue = !_uiState.value.useFlash
            preferences.setUseFlash(newValue)
        }
    }

    fun toggleAspectRatio() {
        viewModelScope.launch {
            val current = _uiState.value.targetAspectRatio
            val next = when (current) {
                DocumentAspectRatio.NONE -> DocumentAspectRatio.DIN_476_2
                DocumentAspectRatio.DIN_476_2 -> DocumentAspectRatio.ANSI_LETTER
                DocumentAspectRatio.ANSI_LETTER -> DocumentAspectRatio.NONE
            }
            preferences.setTargetAspectRatio(next)
        }
    }

    fun setDebugBitmap(bitmap: Bitmap?) {
        _uiState.value.lastDebugBitmap?.recycle()
        _uiState.value = _uiState.value.copy(lastDebugBitmap = bitmap)
    }

    override fun onCleared() {
        super.onCleared()
        _uiState.value.lastDebugBitmap?.recycle()
    }
}
