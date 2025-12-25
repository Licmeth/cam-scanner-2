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

class DocumentPreviewActivity : ActivityWithPreferences() {

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

    override fun onDestroy() {
        super.onDestroy()
        currentBitmap?.recycle()
    }
}