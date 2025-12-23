package com.licmeth.camscanner.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.licmeth.camscanner.DocumentScanner
import com.licmeth.camscanner.R
import com.licmeth.camscanner.databinding.ActivityMainBinding
import com.licmeth.camscanner.model.DocumentAspectRatio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.Point
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ActivityWithPreferences(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var drawerLayout: DrawerLayout
    private var imageCapture: ImageCapture? = null
    private var detectedCorners: Array<Point>? = null
    private var showDebugOverlay: Boolean = false
    private var lastDebugBitmap: Bitmap? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        private const val TAG = "MainActivity"
        private const val KEY_SHOW_DEBUG_OVERLAY = "key_show_debug_overlay"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OpenCV
        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "OpenCV initialization failed")
            Toast.makeText(this, "OpenCV initialization failed", Toast.LENGTH_LONG).show()
            finish()
            return
        } else {
            Log.d(TAG, "OpenCV initialized successfully")
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showDebugOverlay = savedInstanceState?.getBoolean(KEY_SHOW_DEBUG_OVERLAY, false) == true

        binding.debugOverlaySwitch.isChecked = showDebugOverlay
        binding.debugImageView.visibility = View.GONE
        binding.debugOverlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            toggleDebugOverlay(isChecked)
        }

        // Setup drawer
        drawerLayout = binding.drawerLayout
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        binding.navView.setNavigationItemSelectedListener(this)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Handle back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Setup capture button
        binding.captureButton.setOnClickListener {
            captureDocument()
        }

        // Request permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SHOW_DEBUG_OVERLAY, showDebugOverlay)
    }

    /**
     * Check if all required permissions are granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    R.string.camera_permission_required,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    /**
     * Start the camera and bind use cases
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = binding.previewView.surfaceProvider
                }

            // Image capture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            // Image analysis for document detection
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind all use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        try {
            val image = DocumentScanner.toGrayScaleMat(imageProxy)
            val rotation = imageProxy.imageInfo.rotationDegrees
            var cameraImageHeight = imageProxy.height
            var cameraImageWidth = imageProxy.width

            // Detect document in background
            coroutineScope.launch(Dispatchers.Default) {
                val scannerResult = DocumentScanner.detectDocument(image, DocumentScanner.OutputStage.EDGES_DETECTED)
                var corners = scannerResult.corners
                val debugBitmap = scannerResult.debugOutput

                withContext(Dispatchers.Main) {
                    handleDebugOutput(debugBitmap?.let { bitmap ->
                        DocumentScanner.rotateBitmap(bitmap, rotation.toFloat())
                    })

                    if (corners != null) {
                        // Save detected corners normalized to [0,1] for use in capture
                        detectedCorners = corners

                        // Apply rotation
                        if (rotation == 90 || rotation == 180) {
                            // Swap width and height
                            val temp = cameraImageWidth
                            cameraImageWidth = cameraImageHeight
                            cameraImageHeight = temp

                            corners = DocumentScanner.rotateCorners(
                                corners,
                                DocumentScanner.RotationType.of(rotation)
                            )
                        }

                        // Determine factor to scale corners to preview view dimensions
                        val screenAspectRatio =
                            binding.previewView.width.toFloat() / binding.previewView.height
                        val cameraAspectRatio = cameraImageWidth / cameraImageHeight
                        val scale = if (cameraAspectRatio <= screenAspectRatio) {
                            // camera picture is "wider" that screen picture
                            // to fill the screen, the camera picture needs to be scaled by height
                            // areas on the left and right of the camera picture are cropped to fill the screen
                            binding.previewView.height.toFloat() / cameraImageHeight
                        } else {
                            // camera picture is "higher" that screen picture
                            // to fill the screen, the camera picture needs to be scaled by width
                            // areas on the top and bottom of the camera picture are cropped to fill the screen
                            binding.previewView.width.toFloat() / cameraImageWidth
                        }

                        // Determine offsets to shift points to match position of the preview.
                        var shiftX = 0F
                        var shiftY = 0F
                        if (cameraAspectRatio < screenAspectRatio) {
                            // camera picture is "wider" that screen picture
                            // areas on the left and right of the camera picture are cropped to fill the screen
                            val overWidth = cameraImageWidth * scale - binding.previewView.width
                            shiftX = overWidth / 2
                        }

                        if (cameraAspectRatio > screenAspectRatio) {
                            // camera picture is "higher" that screen picture
                            // areas on the top and bottom of the camera picture are cropped to fill the screen
                            val overHeight = cameraImageHeight * scale - binding.previewView.height
                            shiftY = overHeight / 2
                        }

                        val scaledCorners = corners.map { point ->
                            Point(
                                point.x * cameraImageWidth * scale - shiftX,
                                point.y * cameraImageHeight * scale - shiftY
                            )
                        }.toTypedArray()

                        binding.overlayView.setDocumentCorners(scaledCorners)
                        binding.statusText.text = getString(R.string.document_detected)
                        binding.captureButton.isEnabled = true
                    } else {
                        binding.overlayView.setDocumentCorners(null)
                        binding.statusText.text = getString(R.string.detecting_document)
                        binding.captureButton.isEnabled = false
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun toggleDebugOverlay(enabled: Boolean) {
        showDebugOverlay = enabled
        if (!enabled) {
            clearDebugOverlay()
        }

        val message = if (enabled) {
            R.string.debug_overlay_on
        } else {
            R.string.debug_overlay_off
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun handleDebugOutput(debugBitmap: Bitmap?) {
        if (!showDebugOverlay || debugBitmap == null) {
            // Recycle any provided temporary debug bitmap
            debugBitmap?.recycle()

            clearDebugOverlay()
            return
        }

        // Show debug output: replace last bitmap, ensure debug view is on top and preview is transparent
        lastDebugBitmap?.recycle()
        lastDebugBitmap = debugBitmap

        binding.debugImageView.setImageBitmap(lastDebugBitmap)
        binding.debugImageView.visibility = View.VISIBLE
        binding.previewView.visibility = View.GONE
    }

    private fun clearDebugOverlay() {
        lastDebugBitmap?.recycle()
        lastDebugBitmap = null
        binding.debugImageView.setImageDrawable(null)
        binding.debugImageView.visibility = View.GONE
        binding.previewView.visibility = View.VISIBLE
    }

    private fun captureDocument() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    var bitmap = image.toBitmap()
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    image.close()

                    // Transform and crop the document
                    coroutineScope.launch(Dispatchers.Default) {
                        detectedCorners?.let { relativeCorners ->
                            // Rotate image, if needed
                            var corners = relativeCorners
                            if (rotationDegrees == 90 || rotationDegrees == 270) {
                                bitmap = DocumentScanner.rotateBitmap(bitmap, rotationDegrees.toFloat())
                                corners = DocumentScanner.rotateCorners(corners, DocumentScanner.RotationType.of(rotationDegrees))
                            }

                            // Calculate absolute corner positions in captured image
                            val imageWidth = bitmap.width
                            val imageHeight = bitmap.height
                            corners = corners.map { point ->
                                Point(point.x * imageWidth, point.y * imageHeight)
                            }.toTypedArray()

                            val transformedBitmap = DocumentScanner.transformDocument(bitmap, corners, DocumentAspectRatio.DIN_476_2.ratio)

                            withContext(Dispatchers.Main) {
                                if (transformedBitmap != null) {
                                    // Navigate to preview activity
                                    val intent = Intent(
                                        this@MainActivity,
                                        DocumentPreviewActivity::class.java
                                    )

                                    // Save bitmap to cache and pass file path
                                    val file = File(cacheDir, "captured_document.jpg")
                                    file.outputStream().use { out ->
                                        transformedBitmap.compress(
                                            Bitmap.CompressFormat.JPEG,
                                            95,
                                            out
                                        )
                                    }

                                    intent.putExtra("image_path", file.absolutePath)
                                    startActivity(intent)
                                } else {
                                    Toast.makeText(
                                        this@MainActivity,
                                        R.string.error_saving_document,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    Toast.makeText(
                        this@MainActivity,
                        R.string.error_saving_document,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            R.id.nav_about -> {
                Toast.makeText(this, "About: Cam Scanner v1.0", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_help -> {
                Toast.makeText(this, "Help: Point camera at document to scan", Toast.LENGTH_SHORT).show()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            drawerLayout.openDrawer(GravityCompat.START)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        clearDebugOverlay()
        cameraExecutor.shutdown()
        coroutineScope.cancel()
    }
}