package com.licmeth.camscanner.activity

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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