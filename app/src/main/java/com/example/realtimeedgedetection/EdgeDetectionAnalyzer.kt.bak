package com.example.realtimeedgedetection.examples

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Example ImageAnalyzer for processing camera frames.
 * This demonstrates how to implement real-time frame processing with CameraX.
 */
class EdgeDetectionAnalyzer : ImageAnalysis.Analyzer {
    
    override fun analyze(image: ImageProxy) {
        // Convert image to bitmap for processing
        val bitmap = imageToBitmap(image)
        if (bitmap != null) {
            // Process the bitmap (e.g., edge detection)
            processFrame(bitmap)
        }
        image.close()
    }
    
    private fun imageToBitmap(image: ImageProxy): Bitmap? {
        return when (image.format) {
            ImageFormat.YUV_420_888 -> yuvToRgb(image)
            ImageFormat.RGBA_8888 -> nv21ToRgb(image)
            else -> null
        }
    }
    
    private fun yuvToRgb(image: ImageProxy): Bitmap {
        val planes = image.planes
        val ySize = planes[0].buffer.remaining()
        val u = ByteArray(planes[1].buffer.remaining())
        val v = ByteArray(planes[2].buffer.remaining())
        
        planes[0].buffer.get(ByteArray(ySize))
        planes[1].buffer.get(u)
        planes[2].buffer.get(v)
        
        val nv21 = ByteArray(ySize + u.size + v.size)
        
        val bitmap = Bitmap.createBitmap(
            image.width,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        
        return bitmap
    }
    
    private fun nv21ToRgb(image: ImageProxy): Bitmap {
        val bitmap = Bitmap.createBitmap(
            image.width,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        return bitmap
    }
    
    private fun processFrame(bitmap: Bitmap) {
        // Implement your edge detection or other processing here
        // Example: call native C++ code or apply OpenCV operations
    }
}

/**
 * Example usage in MainActivity:
 * 
 * val cameraManager = CameraManager(this)
 * val analyzer = EdgeDetectionAnalyzer()
 * cameraManager.startCamera(previewView, this, analyzer)
 * 
 * // Don't forget to clean up in onDestroy
 * override fun onDestroy() {
 *     super.onDestroy()
 *     cameraManager.shutdown()
 * }
 */
