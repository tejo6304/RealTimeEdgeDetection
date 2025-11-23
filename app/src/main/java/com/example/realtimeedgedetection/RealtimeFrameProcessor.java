package com.example.realtimeedgedetection;

import android.graphics.Bitmap;
import android.util.Log;
import android.view.TextureView;

/**
 * Real-time frame processor for managing filter state
 * Actual filtering is done in native JNI layer with GPU shaders
 */
public class RealtimeFrameProcessor {
    private static final String TAG = "RealtimeFrameProcessor";
    
    private TextureView textureView;
    private int currentFilterType = 2; // Default: Original (no filter)
    private volatile boolean isProcessing = false;
    
    public RealtimeFrameProcessor(TextureView textureView) {
        this.textureView = textureView;
        Log.d(TAG, "RealtimeFrameProcessor initialized");
    }
    
    /**
     * Set filter type - updates state for native layer
     * @param filterType 0=Grayscale, 1=Canny Edge, 2=Original
     */
    public void setFilterType(int filterType) {
        this.currentFilterType = filterType;
        Log.d(TAG, "Filter type changed to: " + getFilterName(filterType));
    }
    
    public int getCurrentFilterType() {
        return currentFilterType;
    }
    
    private String getFilterName(int filterType) {
        switch (filterType) {
            case 0: return "Grayscale";
            case 1: return "Canny Edge";
            case 2: return "Original";
            default: return "Unknown";
        }
    }
}
