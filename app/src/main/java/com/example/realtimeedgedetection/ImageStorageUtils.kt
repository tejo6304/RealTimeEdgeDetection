package com.example.realtimeedgedetection.utils

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageStorageUtils(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageStorageUtils"
        private const val IMAGE_FOLDER = "RealTimeEdgeDetection"
    }
    
    private fun getOutputDirectory(): File {
        val mediaDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let {
            File(it, IMAGE_FOLDER).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else context.filesDir
    }
    
    fun saveBitmap(bitmap: Bitmap, filename: String = generateFilename()): File? {
        return try {
            val outputDir = getOutputDirectory()
            val imageFile = File(outputDir, filename)
            
            FileOutputStream(imageFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            
            Log.d(TAG, "Image saved: ${imageFile.absolutePath}")
            imageFile
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image: ${e.message}")
            null
        }
    }
    
    fun generateFilename(): String {
        val timeStamp = SimpleDateFormat(
            "yyyy-MM-dd_HH:mm:ss",
            Locale.US
        ).format(Date())
        return "edge_detection_$timeStamp.png"
    }
    
    fun getSavedImagesDirectory(): File {
        return getOutputDirectory()
    }
    
    fun getAllSavedImages(): List<File> {
        val outputDir = getOutputDirectory()
        return outputDir.listFiles()?.filter { it.isFile && it.extension == "png" } ?: emptyList()
    }
    
    fun deleteImage(file: File): Boolean {
        return try {
            val deleted = file.delete()
            if (deleted) {
                Log.d(TAG, "Image deleted: ${file.absolutePath}")
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting image: ${e.message}")
            false
        }
    }
}
