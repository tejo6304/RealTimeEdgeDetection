package com.example.realtimeedgedetection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DataFlowController manages the complete image processing pipeline:
 * Camera → Frame Buffer → JNI → OpenCV Processing → Processed Data → Renderers
 */
class DataFlowController(private val context: Context) {
    private val TAG = "DataFlowController"
    
    // Processing pipeline components
    private lateinit var webServer: WebServerManager
    
    // Frame buffer queue for backpressure handling
    private val frameQueue = LinkedBlockingQueue<FrameData>(2)
    
    // Processing thread
    private var processingThread: Thread? = null
    private val isRunning = AtomicBoolean(false)
    
    // Current state
    @Volatile
    private var currentFilter = FilterType.CANNY_EDGE
    
    @Volatile
    private var isProcessing = false
    
    // Performance metrics
    private var frameCount = 0L
    private var lastMetricsTime = System.currentTimeMillis()
    private var currentFps = 0.0
    
    data class FrameData(
        val frameId: Long,
        val rawData: ByteArray,
        val width: Int,
        val height: Int,
        val format: String, // YUV_420, RGBA, etc.
        val timestamp: Long = System.currentTimeMillis()
    )
    
    data class ProcessedFrame(
        val frameId: Long,
        val bitmap: Bitmap,
        val filterType: FilterType,
        val processingTimeMs: Long,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    fun initialize() {
        try {
            webServer = WebServerManager(context)
            Log.d(TAG, "DataFlowController initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DataFlowController", e)
        }
    }
    
    fun startPipeline() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Pipeline already running")
            return
        }
        
        try {
            webServer.startServer()
            
            // Start processing thread
            processingThread = Thread {
                processingLoop()
            }.apply {
                name = "DataFlowProcessing"
                priority = Thread.MAX_PRIORITY - 1
                start()
            }
            
            Log.d(TAG, "Processing pipeline started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start pipeline", e)
            isRunning.set(false)
        }
    }
    
    fun stopPipeline() {
        if (!isRunning.getAndSet(false)) {
            return
        }
        
        try {
            processingThread?.join(2000)
            webServer.stopServer()
            Log.d(TAG, "Processing pipeline stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping pipeline", e)
        }
    }
    
    /**
     * Submit a raw camera frame for processing
     * Non-blocking with queue overflow handling
     */
    fun submitFrame(frameData: FrameData): Boolean {
        if (!isRunning.get()) {
            return false
        }
        
        // Drop oldest frame if queue is full (backpressure)
        if (!frameQueue.offer(frameData)) {
            frameQueue.poll() // Drop oldest
            frameQueue.offer(frameData)
            Log.w(TAG, "Frame queue overflow - dropped frame")
        }
        
        return true
    }
    
    fun setActiveFilter(filter: FilterType) {
        currentFilter = filter
        Log.d(TAG, "Filter changed to: $filter")
    }
    
    fun getActiveFilter(): FilterType = currentFilter
    
    fun isProcessing(): Boolean = isProcessing
    
    fun getMetrics(): ProcessingMetrics {
        return ProcessingMetrics(
            frameCount = frameCount,
            fps = currentFps,
            queueSize = frameQueue.size,
            isRunning = isRunning.get()
        )
    }
    
    data class ProcessingMetrics(
        val frameCount: Long,
        val fps: Double,
        val queueSize: Int,
        val isRunning: Boolean
    )
    
    private fun processingLoop() {
        try {
            while (isRunning.get()) {
                val frameData = frameQueue.poll()
                
                if (frameData != null) {
                    processFrame(frameData)
                } else {
                    Thread.sleep(1) // Prevent busy waiting
                }
                
                // Update metrics
                updateMetrics()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in processing loop", e)
        }
    }
    
    private fun processFrame(frameData: FrameData) {
        isProcessing = true
        val startTime = System.currentTimeMillis()
        
        try {
            // Create placeholder bitmap for demo
            val processedBitmap = Bitmap.createBitmap(frameData.width, frameData.height, Bitmap.Config.ARGB_8888)
            
            val processingTime = System.currentTimeMillis() - startTime
            
            // Send to web server with latest frame
            webServer.updateFrame(processedBitmap)
            
            frameCount++
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        } finally {
            isProcessing = false
        }
    }
    
    private fun updateMetrics() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastMetricsTime
        
        if (elapsed >= 1000) {
            currentFps = (frameCount * 1000.0) / elapsed
            frameCount = 0
            lastMetricsTime = now
            
            val metrics = getMetrics()
            Log.d(TAG, "Metrics - FPS: %.1f, Queue: %d".format(metrics.fps, metrics.queueSize))
        }
    }
}

