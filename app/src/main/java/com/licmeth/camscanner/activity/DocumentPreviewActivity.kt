package com.licmeth.camscanner.activity

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import kotlin.div
import kotlin.text.format
import kotlin.text.insert
import kotlin.text.toFloat

class DocumentPreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDocumentPreviewBinding
    private var currentBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load the captured image
        val imagePath = intent.getStringExtra("image_path")
        imagePath?.let {
            currentBitmap = BitmapFactory.decodeFile(it)
            binding.documentImage.setImageBitmap(currentBitmap)
        }

        // Setup buttons
        binding.retakeButton.setOnClickListener {
            finish()
        }

        binding.saveButton.setOnClickListener {
            saveToPdf()
        }
    }

    private fun saveToPdf() {
        currentBitmap?.let { bitmap ->
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

    private fun saveToPdfOld() {
        currentBitmap?.let { bitmap ->
            try {
                // Create output directory in app-specific external storage
                val documentsDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // For Android 10+, use app-specific external storage
                    File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "CamScanner")
                } else {
                    // For older versions, use public documents directory
                    File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                        "CamScanner"
                    )
                }

                if (!documentsDir.exists()) {
                    documentsDir.mkdirs()
                }

                // Create file with timestamp
                val timestamp = SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.getDefault()
                ).format(Date())
                val pdfFile = File(documentsDir, "scan_$timestamp.pdf")

                // Convert bitmap to byte array
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                val imageData = ImageDataFactory.create(stream.toByteArray())

                // Create PDF
                val pdfWriter = PdfWriter(pdfFile)
                val pdfDocument = PdfDocument(pdfWriter)
                val document = Document(pdfDocument)

                // Add image to PDF
                val pdfImage = Image(imageData)

                // Scale image to fit page
                val pageSize = pdfDocument.defaultPageSize
                val imageWidth = bitmap.width.toFloat()
                val imageHeight = bitmap.height.toFloat()
                val pageWidth = pageSize.width - 40f  // 20pt margins on each side
                val pageHeight = pageSize.height - 40f

                val scaleWidth = pageWidth / imageWidth
                val scaleHeight = pageHeight / imageHeight
                val scale = minOf(scaleWidth, scaleHeight)

                pdfImage.scaleAbsolute(imageWidth * scale, imageHeight * scale)
                pdfImage.setMargins(20f, 20f, 20f, 20f)

                document.add(pdfImage)
                document.close()

                Toast.makeText(
                    this,
                    getString(R.string.document_saved) + "\n${pdfFile.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()

                finish()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this,
                    getString(R.string.error_saving_document) + ": ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentBitmap?.recycle()
    }
}