package com.licmeth.camscanner.activity

import android.app.Dialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.licmeth.camscanner.R
import com.licmeth.camscanner.databinding.ActivityDocumentPreviewBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import com.licmeth.camscanner.model.ColorProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.graphics.createBitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class DocumentPreviewActivity : ActivityWithPreferences() {

    companion object {
        private const val TAG = "DocumentPreviewActivity"
        private const val ADAPTIVE_THRESHOLD_BLOCK_SIZE = 11
        private const val ADAPTIVE_THRESHOLD_C = 2.0
    }

    private lateinit var binding: ActivityDocumentPreviewBinding
    private lateinit var originalBitmap: Bitmap
    private lateinit var originalMat: Mat
    private var displayBitmap: Bitmap? = null
    private var currentColorProfile: ColorProfile = ColorProfile.COLOR
    private var isUseAdaptiveThreshold: Boolean = true

    private var filterDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load the captured image
        val imagePath = intent.getStringExtra("image_path")

        if (imagePath == null) {
            Log.e(TAG, "No image path provided in intent")
            Toast.makeText(this, "Error: No image path in intent.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        displayBitmap = BitmapFactory.decodeFile(imagePath)
        if (displayBitmap == null) {
            Log.e(TAG, "Failed to load image from path: $imagePath")
            Toast.makeText(this, "Error: Failed to load image.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        originalBitmap = displayBitmap!!
        binding.documentImage.setImageBitmap(displayBitmap)
        originalMat = Mat(originalBitmap.height, originalBitmap.width, org.opencv.core.CvType.CV_8UC4)
        Utils.bitmapToMat(originalBitmap, originalMat)

        // Setup buttons
        binding.retakeButton.setOnClickListener {
            finish()
        }

        binding.saveButton.setOnClickListener {
            saveToPdf()
        }

        binding.filterButton.setOnClickListener {
            setupAndShowFilterDialog()
        }

        // Observe color profile preference
        lifecycleScope.launch {
            preferences.colorProfile.collect { profile -> updateColorProfile(profile) }
        }

        // Observe adaptive threshold preference
        //TODO: Enable changing this setting from somewhere
        lifecycleScope.launch {
            preferences.enableAdaptiveThreshold.collect { enabled -> updateAdaptiveThresholdUsage(enabled) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (displayBitmap !== originalBitmap) {
            displayBitmap?.recycle()
        }
        originalBitmap.recycle()
    }

    private fun saveToPdf() {
        displayBitmap?.let { bitmap ->
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val filename = "scan_$timestamp.pdf"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Use MediaStore to place file in shared Documents directory so other apps can find it
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CamScanner")
                    }

                    val resolver = contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        ?: throw IOException("Failed to create MediaStore entry.")

                    resolver.openOutputStream(uri).use { out ->
                        if (out == null) throw IOException("Failed to open output stream.")
                        // create PDF writing to the stream
                        val stream = ByteArrayOutputStream()

                        //TODO: Use JPX / JPEG2000 for better compression
                        // 1. Build or include OpenJPEG for Android (as source submodule or prebuilt static libs) and expose it to CMake.
                        // 2. Add a small JNI wrapper that accepts raw RGB bytes (from Android Bitmap.getPixels()), calls OpenJPEG to encode JP2/JPX into an in-memory buffer, and returns a jbyteArray.
                        // 3. Call the JNI method from Kotlin (encodeBitmapToJpx(bitmap)): extract RGB bytes, call native, return the JP2/JPX ByteArray.
                        // 4. Link the produced JP2 bytes into iText via ImageDataFactory.create(jpxBytes) as in your existing code.
                        // Below are sample pdfimages -list outputs showing the image information of JPX-encoded images in PDFs created by a professional office printer/scanner
                        //   daniel@dell:~/Downloads$ pdfimages -list 2001-12-31\ Deka\ Jahresdepotauszug\ 2001.pdf
                        // page   num  type   width height color comp bpc  enc interp  object ID x-ppi y-ppi size ratio
                        // --------------------------------------------------------------------------------------------
                        // 1     0 image    1240  1754  gray    1   8  jpx    no        29  0   150   150 36.6K 1.7%
                        // 1     1 image     620   877  gray    1   8  jpx    no        31  0    75    75 5875B 1.1%
                        // 1     2 mask     2480  3508  -       1   1  jbig2  no        31  0   300   300 14.0K 1.3%
                        //   daniel@dell:~/Downloads$ pdfimages -list 2012-07-20\ DKB\ Eröffnung\ Ihres\ DKB-Cash.pdf
                        // // page   num  type   width height color comp bpc  enc interp  object ID x-ppi y-ppi size ratio
                        // --------------------------------------------------------------------------------------------
                        // 1     0 image    1240  1754  rgb     3   8  jpx    no        32  0   150   150 1186B 0.0%
                        // 1     1 image     620   877  rgb     3   8  jpx    no        34  0    75    75 40.7K 2.6%
                        // 1     2 mask     2480  3508  -       1   1  jbig2  no        34  0   300   300 35.6K 3.4%
                        // 2     3 image    1240  1754  gray    1   8  jpx    no        35  0   150   150  631B 0.0%
                        // 2     4 image     620   877  gray    1   8  jpx    no        37  0    75    75 7467B 1.4%
                        // 2     5 mask     2480  3508  -       1   1  jbig2  no        37  0   300   300 19.4K 1.8%

                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                        val imageData = ImageDataFactory.create(stream.toByteArray())

                        val pdfWriter = PdfWriter(out)
                        val pdfDocument = PdfDocument(pdfWriter)
                        val document = Document(pdfDocument)

                        val pdfImage = Image(imageData)
                        val pageSize = pdfDocument.defaultPageSize
                        val imageWidth = bitmap.width.toFloat()
                        val imageHeight = bitmap.height.toFloat()
                        val pageWidth = pageSize.width - 40f
                        val pageHeight = pageSize.height - 40f
                        val scale = minOf(pageWidth / imageWidth, pageHeight / imageHeight)
                        pdfImage.scaleAbsolute(imageWidth * scale, imageHeight * scale)
                        pdfImage.setMargins(20f, 20f, 20f, 20f)

                        document.add(pdfImage)
                        document.close()
                    }

                    Toast.makeText(this, getString(R.string.document_saved) + " (Documents)\n$filename", Toast.LENGTH_LONG).show()
                    finish()

                } else {
                    // Pre-Q: write to public Documents folder and notify media scanner
                    val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "CamScanner")
                    if (!docsDir.exists()) docsDir.mkdirs()
                    val pdfFile = File(docsDir, filename)

                    FileOutputStream(pdfFile).use { fos ->
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                        val imageData = ImageDataFactory.create(stream.toByteArray())

                        val pdfWriter = PdfWriter(fos)
                        val pdfDocument = PdfDocument(pdfWriter)
                        val document = Document(pdfDocument)

                        val pdfImage = Image(imageData)
                        val pageSize = pdfDocument.defaultPageSize
                        val imageWidth = bitmap.width.toFloat()
                        val imageHeight = bitmap.height.toFloat()
                        val pageWidth = pageSize.width - 40f
                        val pageHeight = pageSize.height - 40f
                        val scale = minOf(pageWidth / imageWidth, pageHeight / imageHeight)
                        pdfImage.scaleAbsolute(imageWidth * scale, imageHeight * scale)
                        pdfImage.setMargins(20f, 20f, 20f, 20f)

                        document.add(pdfImage)
                        document.close()
                    }

                    // Make file visible to other apps immediately
                    MediaScannerConnection.scanFile(this, arrayOf(pdfFile.absolutePath), arrayOf("application/pdf"), null)

                    Toast.makeText(this, getString(R.string.document_saved) + "\n${pdfFile.absolutePath}", Toast.LENGTH_LONG).show()
                    finish()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, getString(R.string.error_saving_document) + ": ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupAndShowFilterDialog() {
        filterDialog = Dialog(this)

        filterDialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        filterDialog?.setContentView(R.layout.filter_dialog)

        // Setup callbacks
        val profileBlackAndWhiteButton = filterDialog?.findViewById<android.view.View>(R.id.color_profile_black_and_white_button)
        val profileGrayscaleButton = filterDialog?.findViewById<android.view.View>(R.id.color_profile_grayscale_button)
        val profileColorButton = filterDialog?.findViewById<android.view.View>(R.id.color_profile_colors_button)

        profileBlackAndWhiteButton?.setOnClickListener {
            lifecycleScope.launch {
                preferences.setColorProfile(ColorProfile.BLACK_AND_WHITE)
            }
        }
        profileGrayscaleButton?.setOnClickListener {
            lifecycleScope.launch {
                preferences.setColorProfile(ColorProfile.GRAYSCALE)
            }
        }
        profileColorButton?.setOnClickListener {
            lifecycleScope.launch {
                preferences.setColorProfile(ColorProfile.COLOR)
            }
        }

        filterDialog?.show()
        filterDialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        filterDialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        filterDialog?.window?.attributes?.windowAnimations = R.style.DialogAnimation
        filterDialog?.window?.setGravity(Gravity.BOTTOM)
    }

    private fun updateAdaptiveThresholdUsage(enabled: Boolean) {
        isUseAdaptiveThreshold = enabled
        // If currently in black and white mode, re-apply the filter to reflect the change
        if (currentColorProfile == ColorProfile.BLACK_AND_WHITE) {
            updateDisplayBitmap(ColorProfile.BLACK_AND_WHITE)
        }
    }

    private fun updateColorProfile(profile: ColorProfile) {
        if (profile == currentColorProfile) return

        // update current profile
        currentColorProfile = profile

        // update dialog UI
        filterDialog?.let { dialog ->
            val colorProfileValue = dialog.findViewById<TextView>(R.id.color_profile_value)
            when (currentColorProfile) {
                ColorProfile.COLOR -> colorProfileValue?.setText(R.string.color)
                ColorProfile.GRAYSCALE -> colorProfileValue?.setText(R.string.grayscale)
                ColorProfile.BLACK_AND_WHITE -> colorProfileValue?.setText(R.string.black_and_white)
            }
        }

        // update displayed bitmap
        updateDisplayBitmap(profile)
    }

    private fun updateDisplayBitmap(profile: ColorProfile) {
        when (profile) {
            ColorProfile.COLOR -> {
                // Restore original
                updateDisplayBitmap(originalBitmap)
            }

            ColorProfile.GRAYSCALE -> {
                lifecycleScope.launch {
                    val gray = withContext(Dispatchers.Default) {
                        toGrayscale(originalMat)
                    }
                    updateDisplayBitmap(gray)
                }
            }

            ColorProfile.BLACK_AND_WHITE -> {
                lifecycleScope.launch {
                    val bw = withContext(Dispatchers.Default) {
                        toBlackAndWhite(originalMat)
                    }
                    updateDisplayBitmap(bw)
                }
            }
        }
    }

    private fun updateDisplayBitmap(newBitmap: Bitmap) {
        // recycle previous displayed bitmap if it's not the original
        if (displayBitmap !== originalBitmap) {
            displayBitmap?.recycle()
        }
        displayBitmap = newBitmap
        binding.documentImage.setImageBitmap(displayBitmap)
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
            if (isUseAdaptiveThreshold) {
                Imgproc.adaptiveThreshold(
                    grayMat, bwMat, 255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY,
                    ADAPTIVE_THRESHOLD_BLOCK_SIZE, ADAPTIVE_THRESHOLD_C
                )
            } else {
                // Otsu automatic threshold
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
}