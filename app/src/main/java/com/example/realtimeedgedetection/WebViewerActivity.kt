package com.example.realtimeedgedetection

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream

class WebViewerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val gson = Gson()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_viewer)

        webView = findViewById(R.id.web_view)

        // Configure WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = true
            allowContentAccess = true
        }

        // Add JavaScript bridge
        webView.addJavascriptInterface(WebViewerBridge(this), "ImageGalleryBridge")

        // Load HTML
        webView.loadUrl("file:///android_asset/webapp/index.html")
    }

    /**
     * JavaScript bridge for communication between web and Android
     */
    private inner class WebViewerBridge(private val context: Context) {

        @JavascriptInterface
        fun getImages(): String {
            return try {
                val images = ImageStorageUtils.getAllCapturedImages(this@WebViewerActivity)
                val imageDataList = images.map { file ->
                    val metadata = ImageStorageUtils.getImageMetadata(file)
                    // Use proper file:// URI that can be accessed by WebView
                    val path = "file://" + file.absolutePath

                    mapOf(
                        "name" to file.name,
                        "path" to path,
                        "filter" to (metadata?.filter ?: "Original"),
                        "timestamp" to file.lastModified(),
                        "width" to (metadata?.width ?: 0),
                        "height" to (metadata?.height ?: 0)
                    )
                }
                gson.toJson(imageDataList)
            } catch (e: Exception) {
                e.printStackTrace()
                gson.toJson(emptyList<Any>())
            }
        }

        @JavascriptInterface
        fun saveImages(imagesJson: String) {
            try {
                // This is optional - mainly for tracking deletions
                // Actual deletion is handled by the HTML/JS
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @JavascriptInterface
        fun goBack() {
            runOnUiThread {
                finish()
            }
        }

        @JavascriptInterface
        fun deleteImageFile(fileName: String) {
            try {
                val file = File(context.getExternalFilesDir("captured_images"), fileName)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
