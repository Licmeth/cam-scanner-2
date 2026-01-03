package com.licmeth.camscanner.ui.screen

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.licmeth.camscanner.R
import com.licmeth.camscanner.helper.DocumentScanner
import com.licmeth.camscanner.model.DocumentAspectRatio
import com.licmeth.camscanner.ui.component.DocumentOverlay
import com.licmeth.camscanner.viewmodel.MainViewModel
import kotlinx.coroutines.*
import org.opencv.android.OpenCVLoader
import org.opencv.core.Point
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToPreview: (String) -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    var hasPermissions by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val coroutineScope = rememberCoroutineScope()
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
        if (!hasPermissions) {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Check permissions on start
    LaunchedEffect(Unit) {
        // Initialize OpenCV
        if (!OpenCVLoader.initLocal()) {
            Log.e("MainScreen", "OpenCV initialization failed")
            Toast.makeText(context, "OpenCV initialization failed", Toast.LENGTH_LONG).show()
            return@LaunchedEffect
        }
        
        val requiredPermissions = mutableListOf(Manifest.permission.CAMERA).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
        
        hasPermissions = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        
        if (!hasPermissions) {
            permissionLauncher.launch(requiredPermissions)
        }
    }
    
    // Update flash when state changes
    LaunchedEffect(uiState.useFlash) {
        camera?.cameraControl?.enableTorch(uiState.useFlash)
        imageCapture?.flashMode = if (uiState.useFlash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Cam Scanner",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.headlineMedium
                )
                Divider()
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            onNavigateToSettings()
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text("About") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            Toast.makeText(context, "About: Cam Scanner v1.0", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Help") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            Toast.makeText(context, "Help: Point camera at document to scan", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Cam Scanner") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
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
                if (hasPermissions) {
                    // Camera Preview or Debug View
                    if (uiState.enableDebugOverlay && uiState.lastDebugBitmap != null) {
                        Image(
                            bitmap = uiState.lastDebugBitmap!!.asImageBitmap(),
                            contentDescription = "Debug view",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        CameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            onCameraReady = { cameraInstance, imageCaptureInstance ->
                                camera = cameraInstance
                                imageCapture = imageCaptureInstance
                            },
                            onImageAnalyzed = { corners, debugBitmap ->
                                viewModel.setDocumentDetected(corners)
                                if (uiState.enableDebugOverlay) {
                                    viewModel.setDebugBitmap(debugBitmap)
                                } else {
                                    debugBitmap?.recycle()
                                }
                            },
                            lifecycleOwner = lifecycleOwner,
                            cameraExecutor = cameraExecutor
                        )
                    }
                    
                    // Document Overlay
                    if (!uiState.enableDebugOverlay) {
                        DocumentOverlay(corners = uiState.detectedCorners)
                    }
                    
                    // Bottom Controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = uiState.statusText,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Flash Toggle
                            IconButton(onClick = { viewModel.toggleFlash() }) {
                                Icon(
                                    painter = painterResource(
                                        if (uiState.useFlash) R.drawable.ic_flash_on else R.drawable.ic_flash_off
                                    ),
                                    contentDescription = "Toggle flash",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            
                            // Capture Button
                            Button(
                                onClick = {
                                    captureDocument(
                                        imageCapture = imageCapture,
                                        detectedCorners = uiState.detectedCorners,
                                        targetAspectRatio = uiState.targetAspectRatio,
                                        context = context,
                                        coroutineScope = coroutineScope,
                                        onSuccess = onNavigateToPreview
                                    )
                                },
                                enabled = uiState.isDocumentDetected,
                                modifier = Modifier.height(56.dp)
                            ) {
                                Text("Capture")
                            }
                            
                            // Aspect Ratio Toggle
                            IconButton(onClick = { viewModel.toggleAspectRatio() }) {
                                Icon(
                                    painter = painterResource(
                                        when (uiState.targetAspectRatio) {
                                            DocumentAspectRatio.NONE -> R.drawable.ic_aspect_ratio
                                            DocumentAspectRatio.DIN_476_2 -> R.drawable.din_logo
                                            DocumentAspectRatio.ANSI_LETTER -> R.drawable.ansi_logo
                                        }
                                    ),
                                    contentDescription = "Toggle aspect ratio",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Camera permission required",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onCameraReady: (Camera, ImageCapture) -> Unit,
    onImageAnalyzed: (corners: Array<Point>?, debugBitmap: Bitmap?) -> Unit,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    cameraExecutor: java.util.concurrent.ExecutorService
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
    
    AndroidView(
        factory = { previewView },
        modifier = modifier
    ) { view ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = view.surfaceProvider
            }
            
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy, context, onImageAnalyzed, view)
                    }
                }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )
                
                onCameraReady(camera, imageCapture)
            } catch (exc: Exception) {
                Log.e("MainScreen", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}

private fun processImage(
    imageProxy: ImageProxy,
    context: Context,
    onImageAnalyzed: (corners: Array<Point>?, debugBitmap: Bitmap?) -> Unit,
    previewView: PreviewView
) {
    try {
        val image = DocumentScanner.toGrayScaleMat(imageProxy)
        val rotation = imageProxy.imageInfo.rotationDegrees
        var cameraImageHeight = imageProxy.height
        var cameraImageWidth = imageProxy.width
        
        CoroutineScope(Dispatchers.Default).launch {
            val scannerResult = DocumentScanner.detectDocument(image, com.licmeth.camscanner.model.DebugOutputLevel.PREPROCESSED)
            var corners = scannerResult.corners
            val debugBitmap = scannerResult.debugOutput
            
            withContext(Dispatchers.Main) {
                val rotatedDebugBitmap = debugBitmap?.let { bitmap ->
                    DocumentScanner.rotateBitmap(bitmap, rotation.toFloat())
                }
                
                if (corners != null) {
                    // Apply rotation
                    if (rotation == 90 || rotation == 180) {
                        val temp = cameraImageWidth
                        cameraImageWidth = cameraImageHeight
                        cameraImageHeight = temp
                        
                        corners = DocumentScanner.rotateCorners(
                            corners,
                            DocumentScanner.RotationType.of(rotation)
                        )
                    }
                    
                    // Scale corners to preview dimensions
                    val screenAspectRatio = previewView.width.toFloat() / previewView.height
                    val cameraAspectRatio = cameraImageWidth / cameraImageHeight
                    val scale = if (cameraAspectRatio <= screenAspectRatio) {
                        previewView.height.toFloat() / cameraImageHeight
                    } else {
                        previewView.width.toFloat() / cameraImageWidth
                    }
                    
                    var shiftX = 0F
                    var shiftY = 0F
                    if (cameraAspectRatio < screenAspectRatio) {
                        val overWidth = cameraImageWidth * scale - previewView.width
                        shiftX = overWidth / 2
                    }
                    if (cameraAspectRatio > screenAspectRatio) {
                        val overHeight = cameraImageHeight * scale - previewView.height
                        shiftY = overHeight / 2
                    }
                    
                    val scaledCorners = corners.map { point ->
                        Point(
                            point.x * cameraImageWidth * scale - shiftX,
                            point.y * cameraImageHeight * scale - shiftY
                        )
                    }.toTypedArray()
                    
                    onImageAnalyzed(scaledCorners, rotatedDebugBitmap)
                } else {
                    onImageAnalyzed(null, rotatedDebugBitmap)
                }
            }
        }
    } catch (e: Exception) {
        Log.e("MainScreen", "Error processing image: ${e.message}")
    } finally {
        imageProxy.close()
    }
}

private fun captureDocument(
    imageCapture: ImageCapture?,
    detectedCorners: Array<Point>?,
    targetAspectRatio: DocumentAspectRatio,
    context: Context,
    coroutineScope: CoroutineScope,
    onSuccess: (String) -> Unit
) {
    if (imageCapture == null || detectedCorners == null) return
    
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                var bitmap = image.toBitmap()
                val rotationDegrees = image.imageInfo.rotationDegrees
                image.close()
                
                coroutineScope.launch(Dispatchers.Default) {
                    var corners = detectedCorners
                    if (rotationDegrees == 90 || rotationDegrees == 270) {
                        bitmap = DocumentScanner.rotateBitmap(bitmap, rotationDegrees.toFloat())
                        corners = DocumentScanner.rotateCorners(corners, DocumentScanner.RotationType.of(rotationDegrees))
                    }
                    
                    val imageWidth = bitmap.width
                    val imageHeight = bitmap.height
                    corners = corners.map { point ->
                        Point(point.x * imageWidth, point.y * imageHeight)
                    }.toTypedArray()
                    
                    val transformedBitmap = DocumentScanner.transformDocument(
                        bitmap,
                        corners,
                        if (targetAspectRatio == DocumentAspectRatio.NONE) null else targetAspectRatio.ratio
                    )
                    
                    withContext(Dispatchers.Main) {
                        if (transformedBitmap != null) {
                            val file = File(context.cacheDir, "captured_document.jpg")
                            file.outputStream().use { out ->
                                transformedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            onSuccess(file.absolutePath)
                        } else {
                            Toast.makeText(context, "Error saving document", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            
            override fun onError(exception: ImageCaptureException) {
                Log.e("MainScreen", "Photo capture failed: ${exception.message}", exception)
                Toast.makeText(context, "Error saving document", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
