package com.licmeth.camscanner.ui.screen

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image as PdfImage
import com.licmeth.camscanner.model.ColorProfile
import com.licmeth.camscanner.viewmodel.DocumentPreviewViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentPreviewScreen(
    imagePath: String,
    onNavigateBack: () -> Unit,
    viewModel: DocumentPreviewViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    
    // Load bitmap on start
    LaunchedEffect(imagePath) {
        val bitmap = BitmapFactory.decodeFile(imagePath)
        if (bitmap != null) {
            viewModel.setOriginalBitmap(bitmap)
        } else {
            Toast.makeText(context, "Error loading image", Toast.LENGTH_LONG).show()
            onNavigateBack()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document Preview") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showFilterDialog() }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Display bitmap
            uiState.displayBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Document",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            
            // Bottom buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Retake")
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.setSaving(true)
                            val success = saveToPdf(context, uiState.displayBitmap)
                            viewModel.setSaving(false)
                            if (success) {
                                onNavigateBack()
                            }
                        }
                    },
                    enabled = !uiState.isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Save as PDF")
                    }
                }
            }
            
            // Filter Dialog
            if (uiState.showFilterDialog) {
                FilterDialog(
                    currentProfile = uiState.colorProfile,
                    flattenBackground = uiState.flattenBackground,
                    onProfileSelected = { profile ->
                        viewModel.setColorProfile(profile)
                        viewModel.hideFilterDialog()
                    },
                    onFlattenBackgroundToggled = { enabled ->
                        viewModel.setFlattenBackground(enabled)
                    },
                    onDismiss = { viewModel.hideFilterDialog() }
                )
            }
        }
    }
}

@Composable
fun FilterDialog(
    currentProfile: ColorProfile,
    flattenBackground: Boolean,
    onProfileSelected: (ColorProfile) -> Unit,
    onFlattenBackgroundToggled: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color Profile") },
        text = {
            Column {
                ColorProfile.entries.forEach { profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProfileSelected(profile) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentProfile == profile,
                            onClick = { onProfileSelected(profile) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (profile) {
                                ColorProfile.COLOR -> "Color"
                                ColorProfile.GRAYSCALE -> "Grayscale"
                                ColorProfile.BLACK_AND_WHITE -> "Black and White"
                            }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Flatten Background")
                    Switch(
                        checked = flattenBackground,
                        onCheckedChange = onFlattenBackgroundToggled
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private suspend fun saveToPdf(context: android.content.Context, bitmap: android.graphics.Bitmap?): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            bitmap ?: return@withContext false
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "scan_$timestamp.pdf"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CamScanner")
                }
                
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IOException("Failed to create MediaStore entry.")
                
                resolver.openOutputStream(uri).use { out ->
                    if (out == null) throw IOException("Failed to open output stream.")
                    
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, stream)
                    val imageData = ImageDataFactory.create(stream.toByteArray())
                    
                    val pdfWriter = PdfWriter(out)
                    val pdfDocument = PdfDocument(pdfWriter)
                    val document = Document(pdfDocument)
                    
                    val pdfImage = PdfImage(imageData)
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
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Document saved (Documents)\n$filename", Toast.LENGTH_LONG).show()
                }
            } else {
                val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "CamScanner")
                if (!docsDir.exists()) docsDir.mkdirs()
                val pdfFile = File(docsDir, filename)
                
                FileOutputStream(pdfFile).use { fos ->
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, stream)
                    val imageData = ImageDataFactory.create(stream.toByteArray())
                    
                    val pdfWriter = PdfWriter(fos)
                    val pdfDocument = PdfDocument(pdfWriter)
                    val document = Document(pdfDocument)
                    
                    val pdfImage = PdfImage(imageData)
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
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Document saved\n${pdfFile.absolutePath}", Toast.LENGTH_LONG).show()
                }
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error saving document: ${e.message}", Toast.LENGTH_LONG).show()
            }
            false
        }
    }
}
