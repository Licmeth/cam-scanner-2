package com.licmeth.camscanner.viewmodel

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.licmeth.camscanner.helper.UserPreferences
import com.licmeth.camscanner.model.ColorProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject

data class DocumentPreviewUiState(
    val originalBitmap: Bitmap? = null,
    val displayBitmap: Bitmap? = null,
    val colorProfile: ColorProfile = ColorProfile.COLOR,
    val isAdaptiveThreshold: Boolean = true,
    val flattenBackground: Boolean = false,
    val isSaving: Boolean = false,
    val showFilterDialog: Boolean = false
)

@HiltViewModel
class DocumentPreviewViewModel @Inject constructor(
    private val preferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentPreviewUiState())
    val uiState: StateFlow<DocumentPreviewUiState> = _uiState.asStateFlow()

    private var originalMat: Mat? = null

    companion object {
        private const val ADAPTIVE_THRESHOLD_BLOCK_SIZE = 11
        private const val ADAPTIVE_THRESHOLD_C = 2.0
        private const val FLATTEN_BLUR_SIGMA = 50.0
    }

    init {
        // Observe preferences
        viewModelScope.launch {
            preferences.colorProfile.collect { profile ->
                _uiState.value = _uiState.value.copy(colorProfile = profile)
                reapplyEffects()
            }
        }

        viewModelScope.launch {
            preferences.enableAdaptiveThreshold.collect { enabled ->
                _uiState.value = _uiState.value.copy(isAdaptiveThreshold = enabled)
                if (_uiState.value.colorProfile == ColorProfile.BLACK_AND_WHITE) {
                    reapplyEffects()
                }
            }
        }

        viewModelScope.launch {
            preferences.flattenBackground.collect { enabled ->
                _uiState.value = _uiState.value.copy(flattenBackground = enabled)
                reapplyEffects()
            }
        }
    }

    fun setOriginalBitmap(bitmap: Bitmap) {
        // Clean up previous bitmaps
        if (_uiState.value.displayBitmap !== _uiState.value.originalBitmap) {
            _uiState.value.displayBitmap?.recycle()
        }
        _uiState.value.originalBitmap?.recycle()
        originalMat?.release()

        _uiState.value = _uiState.value.copy(
            originalBitmap = bitmap,
            displayBitmap = bitmap
        )

        originalMat = Mat(bitmap.height, bitmap.width, org.opencv.core.CvType.CV_8UC4)
        Utils.bitmapToMat(bitmap, originalMat)

        reapplyEffects()
    }

    fun showFilterDialog() {
        _uiState.value = _uiState.value.copy(showFilterDialog = true)
    }

    fun hideFilterDialog() {
        _uiState.value = _uiState.value.copy(showFilterDialog = false)
    }

    fun setColorProfile(profile: ColorProfile) {
        viewModelScope.launch {
            preferences.setColorProfile(profile)
        }
    }

    fun setFlattenBackground(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setFlattenBackground(enabled)
        }
    }

    private fun reapplyEffects() {
        val original = _uiState.value.originalBitmap ?: return
        val mat = originalMat ?: return
        val state = _uiState.value

        // Step 1: Optionally flatten and denoise background
        val flattenedMat: Mat? = if (state.flattenBackground) flattenBackground(mat) else null
        val workingMat = flattenedMat ?: mat

        // Step 2: Apply color profile
        val resultBitmap = when (state.colorProfile) {
            ColorProfile.COLOR -> {
                if (flattenedMat != null) {
                    val flattenedBitmap = createBitmap(workingMat.width(), workingMat.height())
                    Utils.matToBitmap(workingMat, flattenedBitmap)
                    flattenedBitmap
                } else {
                    original
                }
            }
            ColorProfile.GRAYSCALE -> toGrayscale(workingMat)
            ColorProfile.BLACK_AND_WHITE -> toBlackAndWhite(workingMat)
        }

        flattenedMat?.release()
        updateDisplayBitmap(resultBitmap)
    }

    /**
     * Flattens uneven background illumination using OpenCV to make the document
     * background fully white. Estimates the background via a large Gaussian blur
     * and normalises each pixel by its local background estimate.
     */
    private fun flattenBackground(src: Mat): Mat {
        val srcFloat = Mat()
        src.convertTo(srcFloat, CvType.CV_32F)

        // Estimate background illumination with a large-radius Gaussian blur
        val background = Mat()
        Imgproc.GaussianBlur(srcFloat, background, Size(0.0, 0.0), FLATTEN_BLUR_SIGMA)
        // Add a small epsilon to avoid division by zero
        Core.add(background, Scalar(1.0, 1.0, 1.0, 1.0), background)

        // Normalise: result = (src / background) * 255
        val normalized = Mat()
        Core.divide(srcFloat, background, normalized, 255.0)
        Core.max(normalized, Scalar(0.0, 0.0, 0.0, 0.0), normalized)
        Core.min(normalized, Scalar(255.0, 255.0, 255.0, 255.0), normalized)

        val output = Mat()
        normalized.convertTo(output, CvType.CV_8UC4)

        srcFloat.release()
        background.release()
        normalized.release()

        return output
    }

    private fun updateDisplayBitmap(newBitmap: Bitmap) {
        // Recycle previous displayed bitmap if it's not the original
        if (_uiState.value.displayBitmap !== _uiState.value.originalBitmap) {
            _uiState.value.displayBitmap?.recycle()
        }
        _uiState.value = _uiState.value.copy(displayBitmap = newBitmap)
    }

    private fun toGrayscale(src: Mat): Bitmap {
        val width = src.width()
        val height = src.height()
        val grayBitmap = createBitmap(width, height)

        val grayMat = Mat(height, width, org.opencv.core.CvType.CV_8UC1)
        val grayRgbaMat = Mat(height, width, org.opencv.core.CvType.CV_8UC4)

        try {
            Imgproc.cvtColor(src, grayMat, Imgproc.COLOR_BGR2GRAY)
            Imgproc.cvtColor(grayMat, grayRgbaMat, Imgproc.COLOR_GRAY2RGBA)
            Utils.matToBitmap(grayRgbaMat, grayBitmap)
        } finally {
            grayMat.release()
            grayRgbaMat.release()
        }

        return grayBitmap
    }

    private fun toBlackAndWhite(src: Mat): Bitmap {
        val width = src.width()
        val height = src.height()
        val bwBitmap = createBitmap(width, height)

        val grayMat = Mat(height, width, org.opencv.core.CvType.CV_8UC1)
        val bwMat = Mat(height, width, org.opencv.core.CvType.CV_8UC1)
        val bwRgbaMat = Mat(height, width, org.opencv.core.CvType.CV_8UC4)

        try {
            Imgproc.cvtColor(src, grayMat, Imgproc.COLOR_BGR2GRAY)
            if (_uiState.value.isAdaptiveThreshold) {
                Imgproc.adaptiveThreshold(
                    grayMat, bwMat, 255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY,
                    ADAPTIVE_THRESHOLD_BLOCK_SIZE, ADAPTIVE_THRESHOLD_C
                )
            } else {
                Imgproc.threshold(grayMat, bwMat, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            }
            Imgproc.cvtColor(bwMat, bwRgbaMat, Imgproc.COLOR_GRAY2RGBA)
            Utils.matToBitmap(bwRgbaMat, bwBitmap)
        } finally {
            grayMat.release()
            bwMat.release()
            bwRgbaMat.release()
        }

        return bwBitmap
    }

    fun setSaving(saving: Boolean) {
        _uiState.value = _uiState.value.copy(isSaving = saving)
    }

    override fun onCleared() {
        super.onCleared()
        if (_uiState.value.displayBitmap !== _uiState.value.originalBitmap) {
            _uiState.value.displayBitmap?.recycle()
        }
        _uiState.value.originalBitmap?.recycle()
        originalMat?.release()
    }
}
