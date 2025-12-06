package com.licmeth.camscanner

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt

object DocumentScanner {
    
    private const val TAG = "DocumentScanner"

    fun detectDocument(bitmap: Bitmap): Array<Point>? {
        try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            // Convert to grayscale
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)

            // Apply Gaussian blur
            val blurred = Mat()
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            // Edge detection
            val edges = Mat()
            Imgproc.Canny(blurred, edges, 50.0, 150.0)

            // Find contours
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                edges,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            // Find the largest contour
            var maxArea = 0.0
            var maxContour: MatOfPoint? = null

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area > maxArea) {
                    maxArea = area
                    maxContour = contour
                }
            }

            maxContour?.let { contour ->
                // Approximate the contour to a polygon
                val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(
                    MatOfPoint2f(*contour.toArray()),
                    approx,
                    0.02 * peri,
                    true
                )

                // If we have 4 corners, we found a document
                if (approx.rows() == 4) {
                    val points = approx.toArray()
                    
                    // Order points: top-left, top-right, bottom-right, bottom-left
                    val orderedPoints = orderPoints(points)
                    
                    // Release resources
                    mat.release()
                    gray.release()
                    blurred.release()
                    edges.release()
                    hierarchy.release()
                    approx.release()
                    
                    return orderedPoints
                }
            }

            // Release resources
            mat.release()
            gray.release()
            blurred.release()
            edges.release()
            hierarchy.release()

        } catch (e: Exception) {
            Log.e(TAG, "Error detecting document: ${e.message}")
            e.printStackTrace()
        }

        return null
    }

    fun transformDocument(bitmap: Bitmap, corners: Array<Point>): Bitmap? {
        try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            // Calculate output dimensions
            val width = maxOf(
                distance(corners[0], corners[1]),
                distance(corners[2], corners[3])
            )
            val height = maxOf(
                distance(corners[0], corners[3]),
                distance(corners[1], corners[2])
            )

            // Destination points
            val dstPoints = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(width - 1, 0.0),
                Point(width - 1, height - 1),
                Point(0.0, height - 1)
            )

            // Source points
            val srcPoints = MatOfPoint2f(*corners)

            // Get perspective transform matrix
            val transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

            // Apply perspective transformation
            val warped = Mat()
            Imgproc.warpPerspective(
                mat,
                warped,
                transform,
                Size(width, height)
            )

            // Convert back to bitmap
            val resultBitmap = Bitmap.createBitmap(
                width.toInt(),
                height.toInt(),
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(warped, resultBitmap)

            // Release resources
            mat.release()
            warped.release()
            transform.release()
            srcPoints.release()
            dstPoints.release()

            return resultBitmap

        } catch (e: Exception) {
            Log.e(TAG, "Error transforming document: ${e.message}")
            e.printStackTrace()
        }

        return null
    }

    private fun orderPoints(points: Array<Point>): Array<Point> {
        // Sort by y coordinate
        val sorted = points.sortedBy { it.y }
        
        // Top two points
        val topPoints = sorted.take(2).sortedBy { it.x }
        val topLeft = topPoints[0]
        val topRight = topPoints[1]
        
        // Bottom two points
        val bottomPoints = sorted.takeLast(2).sortedBy { it.x }
        val bottomLeft = bottomPoints[0]
        val bottomRight = bottomPoints[1]
        
        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun distance(p1: Point, p2: Point): Double {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return sqrt(dx * dx + dy * dy)
    }
}
