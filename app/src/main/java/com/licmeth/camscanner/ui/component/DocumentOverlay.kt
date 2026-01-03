package com.licmeth.camscanner.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import org.opencv.core.Point

@Composable
fun DocumentOverlay(
    corners: Array<Point>?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        corners?.let {
            if (it.size == 4) {
                drawDocumentOverlay(it)
            }
        }
    }
}

private fun DrawScope.drawDocumentOverlay(corners: Array<Point>) {
    val path = Path().apply {
        val first = corners[0]
        moveTo(first.x.toFloat(), first.y.toFloat())
        
        for (i in 1 until 4) {
            lineTo(corners[i].x.toFloat(), corners[i].y.toFloat())
        }
        close()
    }

    // Draw filled area
    drawPath(
        path = path,
        color = Color.Green.copy(alpha = 0.2f),
        style = Fill
    )

    // Draw border
    drawPath(
        path = path,
        color = Color.Green,
        style = Stroke(width = 8f)
    )

    // Draw corner circles
    corners.forEach { point ->
        drawCircle(
            color = Color.Green,
            radius = 15f,
            center = Offset(point.x.toFloat(), point.y.toFloat())
        )
    }
}
