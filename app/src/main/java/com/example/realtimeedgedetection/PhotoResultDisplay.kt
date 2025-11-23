package com.example.realtimeedgedetection

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PhotoResultDisplay : AppCompatActivity() {

    companion object {
        private const val TAG = "PhotoResultDisplay"
        const val EXTRA_BITMAP_PATH = "bitmap_path"
        const val EXTRA_FILTER_TYPE = "filter_type"
    }

    private lateinit var imageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_result)

        imageView = findViewById(R.id.result_image_view)

        // Get the bitmap path from intent
        val bitmapPath = intent.getStringExtra(EXTRA_BITMAP_PATH)
        val filterType = intent.getIntExtra(EXTRA_FILTER_TYPE, 2)

        if (!bitmapPath.isNullOrEmpty()) {
            loadAndDisplayBitmap(bitmapPath, filterType)
        } else {
            Toast.makeText(this, "No image to display", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadAndDisplayBitmap(bitmapPath: String, filterType: Int) {
        Thread {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeFile(bitmapPath)
                if (bitmap != null) {
                    runOnUiThread {
                        imageView.setImageBitmap(bitmap)
                        val filterName = when (filterType) {
                            0 -> "Grayscale"
                            1 -> "Canny Edge"
                            else -> "Original"
                        }
                        title = "Result: $filterName"
                        Log.d(TAG, "Image loaded and displayed successfully")
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading image: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }.start()
    }
}
