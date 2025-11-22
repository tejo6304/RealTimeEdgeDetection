package com.example.realtimeedgedetection.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CameraManager"
    }
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    fun startCamera(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        imageAnalyzer: ImageAnalysis.Analyzer? = null
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                
                // Create Preview
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                
                // Select back camera
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()
                
                // Create ImageAnalysis for real-time processing
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                
                if (imageAnalyzer != null) {
                    imageAnalysis.setAnalyzer(cameraExecutor, imageAnalyzer)
                }
                
                // Unbind any existing use cases before binding new ones
                cameraProvider?.unbindAll()
                
                // Bind use cases
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                
                Log.d(TAG, "Camera started successfully")
            } catch (exc: Exception) {
                Log.e(TAG, "Error starting camera: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    fun stopCamera() {
        cameraProvider?.unbindAll()
        Log.d(TAG, "Camera stopped")
    }
    
    fun shutdown() {
        cameraExecutor.shutdown()
        Log.d(TAG, "CameraManager shutdown")
    }
}
