package com.licmeth.camscanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import org.opencv.core.Point

class DocumentOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        color = Color.argb(50, 0, 255, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var documentCorners: Array<Point>? = null
    private var isDocumentDetected = false

    fun setDocumentCorners(corners: Array<Point>?) {
        documentCorners = corners
        isDocumentDetected = corners != null && corners.size == 4
        
        if (isDocumentDetected) {
            paint.color = Color.GREEN
            fillPaint.color = Color.argb(50, 0, 255, 0)
        } else {
            paint.color = Color.RED
            fillPaint.color = Color.argb(50, 255, 0, 0)
        }
        
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        documentCorners?.let { corners ->
            if (corners.size == 4) {
                val path = Path()
                
                // Convert OpenCV points to screen coordinates
                val screenCorners = corners.map { point ->
                    Pair(point.x.toFloat(), point.y.toFloat())
                }

                path.moveTo(screenCorners[0].first, screenCorners[0].second)
                for (i in 1 until 4) {
                    path.lineTo(screenCorners[i].first, screenCorners[i].second)
                }
                path.close()

                // Draw filled area
                canvas.drawPath(path, fillPaint)
                
                // Draw border
                canvas.drawPath(path, paint)

                // Draw corner circles
                val circlePaint = Paint(paint).apply {
                    style = Paint.Style.FILL
                }
                
                screenCorners.forEach { (x, y) ->
                    canvas.drawCircle(x, y, 15f, circlePaint)
                }
            }
        }
    }
}
