package com.novaterm.feature.terminal.ocr

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * Manages CameraX + ML Kit text recognition for OCR → terminal pipe.
 *
 * Usage:
 * 1. User taps camera button in ExtraKeysBar
 * 2. Bottom sheet opens with live camera preview
 * 3. ML Kit runs text recognition on each frame
 * 4. Detected text shown as overlay; user taps to confirm
 * 5. Confirmed text is piped to terminal input
 *
 * Lifecycle: bind to LifecycleOwner (Activity), auto-released on destroy.
 */
class CameraOcrManager(private val context: Context) {

    private val _recognizedText = MutableStateFlow("")
    /** Latest recognized text from the camera feed. */
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    /** Whether OCR is currently processing a frame. */
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * Bind camera preview and OCR analysis to a [PreviewView] and [LifecycleOwner].
     * Call this when the camera sheet is opened.
     */
    fun bindCamera(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, ::analyzeImage) }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                )
                Log.i(TAG, "Camera bound for OCR")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Unbind camera and stop analysis. */
    fun unbindCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        _recognizedText.value = ""
        _isProcessing.value = false
    }

    /** Release all resources. */
    fun release() {
        unbindCamera()
        textRecognizer.close()
        analysisExecutor.shutdown()
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun analyzeImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // Throttle: skip if already processing
        if (_isProcessing.value) {
            imageProxy.close()
            return
        }

        _isProcessing.value = true
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val text = visionText.text.trim()
                if (text.isNotEmpty()) {
                    _recognizedText.value = text
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "OCR failed", e)
            }
            .addOnCompleteListener {
                _isProcessing.value = false
                imageProxy.close()
            }
    }

    companion object {
        private const val TAG = "CameraOCR"
    }
}
