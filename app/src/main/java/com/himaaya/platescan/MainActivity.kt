package com.himaaya.platescan

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var textRecognizer: TextRecognizer
    private val offendersMap = mutableMapOf<String, String>()

    private val plateTracker = PlateTracker()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Se requiere permiso de cámara para continuar.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        loadOffendersFromJson()
        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> startCamera()
            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val previewView = findViewById<PreviewView>(R.id.previewView)
        val overlay = findViewById<OverlayView>(R.id.overlayView)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageWithMLKit(imageProxy, overlay, previewView)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error iniciando cámara", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageWithMLKit(imageProxy: ImageProxy, overlay: OverlayView, previewView: PreviewView) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val rawDetections = processTextRecognitionResult(visionText)
                    val trackedDetections = plateTracker.update(rawDetections)
                    val transformedDetections = transformCoordinates(trackedDetections, imageProxy, previewView)
                    runOnUiThread { overlay.setDetections(transformedDetections) }
                }
                .addOnFailureListener { e ->
                    Log.e("MLKit", "Error en el reconocimiento de texto", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun processTextRecognitionResult(visionText: Text): List<Detection> {
        val detections = mutableListOf<Detection>()
        val plateRegex = Regex("^[A-Z]{3}[0-9]{3}$|^[0-9]{1,5}\\s*[A-Z]{1,2}$")

        for (block in visionText.textBlocks) {
            for(line in block.lines) {
                val lineText = line.text.replace(Regex("[^A-Z0-9]"), "").uppercase()
                if (plateRegex.matches(lineText)) {
                    val reason = offendersMap[lineText]
                    val isOffender = reason != null
                    line.boundingBox?.let { box ->
                        detections.add(
                            Detection(
                                xmin = box.left.toFloat(), ymin = box.top.toFloat(),
                                xmax = box.right.toFloat(), ymax = box.bottom.toFloat(),
                                isOffender = isOffender, plate = lineText, reason = reason
                            )
                        )
                    }
                }
            }
        }
        return detections
    }

    private fun transformCoordinates(detections: List<Detection>, imageProxy: ImageProxy, previewView: PreviewView): List<Detection> {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val imageWidth = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.height else imageProxy.width
        val imageHeight = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.width else imageProxy.height
        val viewWidth = previewView.width
        val viewHeight = previewView.height
        val viewAspectRatio = viewWidth.toFloat() / viewHeight
        val imageAspectRatio = imageWidth.toFloat() / imageHeight
        val scale: Float
        val offsetX: Float
        val offsetY: Float
        if (viewAspectRatio > imageAspectRatio) {
            scale = viewWidth.toFloat() / imageWidth
            offsetX = 0f
            offsetY = (viewHeight - imageHeight * scale) / 2
        } else {
            scale = viewHeight.toFloat() / imageHeight
            offsetX = (viewWidth - imageWidth * scale) / 2
            offsetY = 0f
        }
        return detections.map { det ->
            det.copy().apply {
                xmin = xmin * scale + offsetX
                xmax = xmax * scale + offsetX
                ymin = ymin * scale + offsetY
                ymax = ymax * scale + offsetY
            }
        }
    }

    private fun loadOffendersFromJson() {
        try {
            val inputStream = resources.openRawResource(R.raw.infractores)
            val jsonText = inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(jsonText)
            offendersMap.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                offendersMap[obj.getString("plate").uppercase()] = obj.optString("reason", "En lista roja")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error cargando infractores.json", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        textRecognizer.close()
    }
}