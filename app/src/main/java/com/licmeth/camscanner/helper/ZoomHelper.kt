package com.licmeth.camscanner.helper

import android.annotation.SuppressLint
import android.graphics.Matrix
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import kotlin.math.min
import kotlin.math.sqrt

class ZoomHelper(
    private val imageView: ImageView,
    private var maxScaleMultiplier: Float = 4f
) {
    private val imageMatrix = Matrix()
    private var baseScale = 1f
    private var currentScale = 1f
    private var maxScale = 4f

    private val scaleDetector = ScaleGestureDetector(imageView.context, ScaleListener())
    private val gestureDetector = GestureDetector(imageView.context, GestureListener())

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var isClickableBackup = false

    /**
     * Attaches the zoom and pan functionality to the ImageView.
     * Suppress lint for clickable view accessibility because we call performClick() when a click is detected.
     */
    @Suppress("ClickableViewAccessibility")
    fun attach() {
        isClickableBackup = imageView.isClickable
        imageView.isClickable = true
        imageView.scaleType = ImageView.ScaleType.MATRIX
        imageView.imageMatrix = imageMatrix
        imageView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    isDragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleDetector.isInProgress) {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        if (sqrt((dx * dx + dy * dy).toDouble()) > 5.0) {
                            isDragging = true
                        }
                        if (isDragging) {
                            imageMatrix.postTranslate(dx, dy)
                            fixTranslation()
                            imageView.imageMatrix = imageMatrix
                        }
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // If the touch ended without a drag/scale, treat as a click for accessibility
                    if (!isDragging && !scaleDetector.isInProgress && event.actionMasked == MotionEvent.ACTION_UP) {
                        imageView.performClick()
                    }
                    isDragging = false
                }
            }
            true
        }
    }

    /**
     * Detaches the zoom and pan functionality from the ImageView.
     * Suppress lint for clickable view accessibility because we restore the original clickable state.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun detach() {
        imageView.isClickable = isClickableBackup
        imageView.setOnTouchListener(null)
    }

    fun setupInitialImageMatrix() {
        val drawable = imageView.drawable ?: return
        val dWidth = drawable.intrinsicWidth.toFloat()
        val dHeight = drawable.intrinsicHeight.toFloat()
        val vWidth = imageView.width.toFloat()
        val vHeight = imageView.height.toFloat()
        if (dWidth <= 0f || dHeight <= 0f || vWidth <= 0f || vHeight <= 0f) return

        baseScale = min(vWidth / dWidth, vHeight / dHeight)
        currentScale = baseScale
        maxScale = baseScale * maxScaleMultiplier

        imageMatrix.reset()
        imageMatrix.postScale(baseScale, baseScale)
        val redundantX = vWidth - dWidth * baseScale
        val redundantY = vHeight - dHeight * baseScale
        imageMatrix.postTranslate(redundantX / 2f, redundantY / 2f)
        imageView.imageMatrix = imageMatrix
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val targetScale = currentScale * scaleFactor
            val clamped = targetScale.coerceIn(baseScale, maxScale)
            val scaleBy = clamped / currentScale
            imageMatrix.postScale(scaleBy, scaleBy, detector.focusX, detector.focusY)
            currentScale = clamped
            fixTranslation()
            imageView.imageMatrix = imageMatrix
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Reset to base scale and center
            currentScale = baseScale
            imageMatrix.reset()
            imageMatrix.postScale(baseScale, baseScale)

            val drawable = imageView.drawable ?: return true
            val dWidth = drawable.intrinsicWidth.toFloat()
            val dHeight = drawable.intrinsicHeight.toFloat()
            val vWidth = imageView.width.toFloat()
            val vHeight = imageView.height.toFloat()
            val redundantX = vWidth - dWidth * baseScale
            val redundantY = vHeight - dHeight * baseScale
            imageMatrix.postTranslate(redundantX / 2f, redundantY / 2f)
            imageView.imageMatrix = imageMatrix
            return true
        }
    }

    private fun fixTranslation() {
        val rect = getMatrixRectF()
        val viewWidth = imageView.width.toFloat()
        val viewHeight = imageView.height.toFloat()
        var deltaX = 0f
        var deltaY = 0f

        if (rect.width() <= viewWidth) {
            deltaX = (viewWidth - rect.width()) / 2f - rect.left
        } else {
            if (rect.left > 0) deltaX = -rect.left
            if (rect.right < viewWidth) deltaX = viewWidth - rect.right
        }

        if (rect.height() <= viewHeight) {
            deltaY = (viewHeight - rect.height()) / 2f - rect.top
        } else {
            if (rect.top > 0) deltaY = -rect.top
            if (rect.bottom < viewHeight) deltaY = viewHeight - rect.bottom
        }

        imageMatrix.postTranslate(deltaX, deltaY)
    }

    private fun getMatrixRectF(): RectF {
        val drawable = imageView.drawable ?: return RectF()
        val rect = RectF(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        imageMatrix.mapRect(rect)
        return rect
    }
}
