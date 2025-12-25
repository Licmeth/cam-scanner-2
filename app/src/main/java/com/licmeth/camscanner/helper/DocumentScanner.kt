package com.licmeth.camscanner.helper

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.collections.MutableList
import kotlin.math.sqrt
import androidx.core.graphics.createBitmap
import com.licmeth.camscanner.model.DebugOutputLevel
import kotlin.math.abs

data class DocumentScannerResult(
    val corners: Array<Point>?,
    val debugOutput: Bitmap?
)

object DocumentScanner {

    enum class RotationType(val value: Int) {
        ORIGINAL(0),
        CLOCKWISE(90),
        UP_SIDE_DOWN(180),
        ANTI_CLOCKWISE(270);

        companion object {
            private val VALUE_MAP: Map<Int, RotationType> =
                entries.associateBy { it.value }

            /** Returns matching RotationType or throws if not found */
            fun of(value: Int): RotationType =
                VALUE_MAP[value]
                    ?: throw IllegalArgumentException("Unsupported rotation value: $value")
        }
    }
    
    private const val TAG = "DocumentScanner"

    private const val MAX_IMAGE_DIMENSION = 1080
    private const val TWO_PERCENT: Double = 0.02
    private const val MORPH_KERNEL_SIZE: Double = 10.0
    private const val MORPH_ITERATIONS: Int = 3
    private const val GAUSSIAN_BLUR_SIGMA_X: Double = 0.0
    private const val GAUSSIAN_BLUR_KERNEL_DIMENSION: Double = 11.0
    private const val EDGE_DILATE_KERNEL_SIZE: Double = 2.0
    private const val CANNY_LOWER_HYSTERESIS_THRESHOLD: Double = 30.0
    private const val CANNY_UPPER_HYSTERESIS_THRESHOLD: Double = 150.0
    private const val CONTOUR_SELECTION_COUNT: Int = 5

    private val morphKernel: Mat = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(MORPH_KERNEL_SIZE, MORPH_KERNEL_SIZE))
    private val morphAnchor: Point = Point(-1.0, -1.0) // Anchor point for the morphological kernel
    private val dilateKernel: Mat = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(EDGE_DILATE_KERNEL_SIZE, EDGE_DILATE_KERNEL_SIZE))
    private val gaussianBlurKernelSize: Size = Size(GAUSSIAN_BLUR_KERNEL_DIMENSION, GAUSSIAN_BLUR_KERNEL_DIMENSION)

    /**
     * Detects the document in the given grayscale image and returns the corners and optional debug output.
     *
     * The corners are returned in normalized coordinates [0,1] relative to the image dimensions.
     *
     * @param grayscaleImage The input grayscale image as an OpenCV Mat.
     * @param debugOutputLevel The stage at which to output the debug image.
     * @return A DocumentScannerResult containing the detected corners and optional debug output image.
     */
    fun detectDocument(grayscaleImage: Mat, debugOutputLevel: DebugOutputLevel): DocumentScannerResult {
        try {
            var debugOutput: Bitmap? = null
            val processingMat = preprocessInput(grayscaleImage)
            if (debugOutputLevel == DebugOutputLevel.PREPROCESSED) {
                debugOutput = createBitmap(processingMat.cols(), processingMat.rows())
                Utils.matToBitmap(processingMat, debugOutput)
            }

            removeDocumentContent(processingMat)
            if (debugOutputLevel == DebugOutputLevel.CONTENT_REMOVED) {
                debugOutput = createBitmap(processingMat.cols(), processingMat.rows())
                Utils.matToBitmap(processingMat, debugOutput)
            }

            edgeDetection(processingMat)
            if (debugOutputLevel == DebugOutputLevel.EDGES_DETECTED) {
                debugOutput = createBitmap(processingMat.cols(), processingMat.rows())
                Utils.matToBitmap(processingMat, debugOutput)
            }

            val corners = findDocumentCorners(processingMat)

            corners?: return DocumentScannerResult(null, debugOutput)

            // Sort coners in consistent order: top-left, top-right, bottom-right, bottom-left
            val sortedCorners = orderPoints(corners)

            // Normalize corner positions to [0,1] range
            sortedCorners.forEach {
                it.x = it.x / processingMat.width().toDouble()
                it.y = it.y / processingMat.height().toDouble()
            }

            // Release resources
            processingMat.release()

            return DocumentScannerResult(sortedCorners, debugOutput)

        } catch (e: Exception) {
            Log.e(TAG, "Error detecting document: ${e.message}")
            e.printStackTrace()
        }

        return DocumentScannerResult(null, null)
    }

    /**
     * Preprocesses the input image by converting it to grayscale and resizing if necessary.
     *
     * @param image The input Bitmap image.
     * @return The preprocessed image Mat.
     */
    @OptIn(ExperimentalGetImage::class)
    private fun preprocessInput(image: Mat): Mat {
        // Resize if needed
        if (isNeedsScaling(image)) {
            val processingMat = createProcessingMat(image)
            Imgproc.resize(
                image,
                processingMat,
                processingMat.size(),
                0.0,
                0.0,
                Imgproc.INTER_AREA)
            image.release()
            return processingMat
        } else {
            return image
        }
    }

    @OptIn(ExperimentalGetImage::class)
    fun toGrayScaleMat(imageProxy: ImageProxy): Mat {
        val image = imageProxy.image ?: throw IllegalArgumentException("Image is null")

        if (image.format != ImageFormat.YUV_420_888) {
            throw IllegalArgumentException("Unsupported image format: ${image.format}")
        }

        val yBuffer = image.planes[0].buffer

        // Y plane is already greyscale
        yBuffer.rewind()
        val ySize = yBuffer.remaining()
        val yBytes = ByteArray(ySize)
        yBuffer.get(yBytes, 0, ySize)

        val mat = Mat(image.height, image.width, CvType.CV_8UC1)
        mat.put(0, 0, yBytes)

        return mat
    }

    /**
     * Checks if the image needs to be scaled down based on maximum allowed dimensions.
     *
     * @param image The input image Mat.
     * @return True if scaling is needed, false otherwise.
     */
    private fun isNeedsScaling(image: Mat): Boolean {
        return image.width() > MAX_IMAGE_DIMENSION || image.height() > MAX_IMAGE_DIMENSION
    }

    /**
     * Creates a Mat for processing with dimensions matching the maximum allowed size. If scaling is
     * needed, the aspect ratio is preserved.
     *
     * @param image The input image Mat.
     * @return A new Mat sized for processing.
     */
    private fun createProcessingMat(image: Mat): Mat {
        if(!isNeedsScaling(image)) {
            return Mat.zeros(image.size(), CvType.CV_8UC1)
        }

        val scale: Double

        if (image.width() >= image.height()) {
            scale = MAX_IMAGE_DIMENSION.toDouble() / image.width().toDouble()
            return Mat.zeros((image.height() * scale).toInt(), MAX_IMAGE_DIMENSION, CvType.CV_8UC1)
        } else {
            scale = MAX_IMAGE_DIMENSION.toDouble() / image.height().toDouble()
            return Mat.zeros(MAX_IMAGE_DIMENSION, (image.width() * scale).toInt(), CvType.CV_8UC1)
        }
    }

    /**
     * Removes the content of the document by applying morphological closing.
     */
    private fun removeDocumentContent(processingMat: Mat) {
        Imgproc.morphologyEx(processingMat, processingMat, Imgproc.MORPH_CLOSE, morphKernel, morphAnchor, MORPH_ITERATIONS)
    }

    private fun edgeDetection(processingMat: Mat) {
        Imgproc.GaussianBlur(processingMat, processingMat, gaussianBlurKernelSize, GAUSSIAN_BLUR_SIGMA_X)
        Imgproc.Canny(processingMat, processingMat, CANNY_LOWER_HYSTERESIS_THRESHOLD, CANNY_UPPER_HYSTERESIS_THRESHOLD)
        Imgproc.dilate(processingMat, processingMat, dilateKernel)
    }

    /**
     * Finds the corners of the document in the processed image.
     *
     * @param processingMat The preprocessed image Mat.
     * @return An array of points containing the corners of the document, or null if not found.
     */
    private fun findDocumentCorners(processingMat: Mat): Array<Point>? {
        val contours: MutableList<MatOfPoint> = mutableListOf()
        val hierarchy = Mat()

        Imgproc.findContours(processingMat, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE)

        if (contours.isEmpty()) {
            return null
        }

        contours.sortByDescending { Imgproc.contourArea(it) }

        return detectDocumentCorners(contours, CONTOUR_SELECTION_COUNT)
    }

    /**
     * Detects the document corners from the list of contours.
     *
     * @param contours The list of detected contours, ordered by area.
     * @param limit The maximum number of contours to check.
     * @return An array of points containing the corners of the document, or null if not found.
     */
    private fun detectDocumentCorners(contours: List<MatOfPoint>, limit: Int): Array<Point>? {
        var i = 0
        val contour = MatOfPoint2f()
        val simplifiedContour = MatOfPoint2f()
        var points: Array<Point>? = null

        while (i < contours.size && i < limit) {
            contours[i].convertTo(contour, CvType.CV_32F)

            val epsilon = TWO_PERCENT * Imgproc.arcLength(contour, true)
            Imgproc.approxPolyDP(contour, simplifiedContour, epsilon, true)

            // if the approximated contour has 4 points, we found the document
            if (simplifiedContour.total() == 4L) {
                points = simplifiedContour.toArray()
                break
            }

            i++
        }

        contour.release()
        simplifiedContour.release()
        contours.forEach { it.release() }

        if (points != null) {
            return points
        }

        return null
    }

    fun transformDocument(bitmap: Bitmap, corners: Array<Point>, aspectRatio: Float?): Bitmap? {
        try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            // Calculate output dimensions
            var width = maxOf(
                distance(corners[0], corners[1]),
                distance(corners[2], corners[3])
            )
            var height = maxOf(
                distance(corners[0], corners[3]),
                distance(corners[1], corners[2])
            )

            // Adjust aspect ratio if provided
            if (aspectRatio != null) {
                val currentAspectRatio = width.toFloat() / height.toFloat()

                // Determine the closest target aspect ratio (portrait or landscape)
                val targetAspectRatio = if(abs(currentAspectRatio - aspectRatio) <= abs(currentAspectRatio - (1.0F / aspectRatio))) {
                    aspectRatio
                } else {
                    1.0F / aspectRatio
                }

                // Adjust width or height to match target aspect ratio
                if (currentAspectRatio > targetAspectRatio) {
                    width = height * targetAspectRatio
                } else {
                    height = width / targetAspectRatio
                }
            }

            // Destination points
            val dstPoints = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(width, 0.0),
                Point(width, height),
                Point(0.0, height)
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
            val resultBitmap = createBitmap(width.toInt(), height.toInt())
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

    fun rotateBitmap(src: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(angle) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /**
     * Rotates the given corner points according to the given rotation type.
     *
     * The method assumes an ordered corner array: top-left, top-right, bottom-right, bottom-left
     */
    fun rotateCorners(src: Array<Point>, rotation: RotationType): Array<Point> {
        if (src.size != 4) {
            throw IllegalArgumentException("Unsupported number of corner points: ${src.size}")
        }

        // name the corners
        val A = 0 // top-left
        val B = 1 // top-right
        val C = 2 // bottom-right
        val D = 3 // bottom-left

        if (rotation == RotationType.ORIGINAL) {
            return src
        }

        if (rotation == RotationType.CLOCKWISE) {
            //  A B  --> D A
            //  D C  --> C B
            val topLeft = Point(1.0-src[D].y, src[D].x)
            val topRight = Point(1.0-src[A].y, src[A].x)
            val bottomRight = Point(1.0-src[B].y,src[B].x)
            val bottomLeft = Point(1.0-src[C].y,src[C].x)
            return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
        }

        if (rotation == RotationType.UP_SIDE_DOWN) {
            //  A B  --> C D
            //  D C  --> B A
            val topLeft = Point(1.0-src[C].x, 1.0-src[C].y)
            val topRight = Point(1.0-src[D].x, 1.0-src[D].y)
            val bottomRight = Point(1.0-src[A].x,1.0-src[A].y)
            val bottomLeft = Point(1.0-src[B].x,1.0-src[B].y)
            return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
        }

        if (rotation == RotationType.ANTI_CLOCKWISE) {
            //  A B  --> B C
            //  D C  --> A D
            val topLeft = Point(src[B].y, 1.0-src[B].x)
            val topRight = Point(src[C].y, 1.0-src[C].x)
            val bottomRight = Point(src[D].y,1.0-src[D].x)
            val bottomLeft = Point(src[A].y,1.0-src[A].x)
            return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
        }

        throw IllegalArgumentException("Unsupported rotation type: ${rotation.name}")
    }
}
