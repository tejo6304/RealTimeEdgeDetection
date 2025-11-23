package com.example.realtimeedgedetection;

import android.graphics.Bitmap;
import android.util.Log;

/**
 * JNI interface for native image processing and edge detection
 */
public class NativeImageProcessor {
    private static final String TAG = "NativeImageProcessor";

    static {
        System.loadLibrary("realtimeedgedetection");
    }

    // Filter type constants
    public static final int FILTER_GRAYSCALE = 0;
    public static final int FILTER_CANNY_EDGE = 1;
    public static final int FILTER_ORIGINAL = 2;

    /**
     * Process a single frame with the specified filter
     * @param surface The Surface to render to
     * @param width Frame width
     * @param height Frame height
     * @param filterType Filter type (0=Grayscale, 1=Canny Edge, 2=Original)
     */
    public static native void processFrame(Object surface, int width, int height, int filterType);

    /**
     * Process a bitmap with grayscale filter
     * @param bitmap The input bitmap
     * @return Processed bitmap
     */
    public static native Bitmap processGrayscale(Bitmap bitmap);

    /**
     * Process a bitmap with Canny edge detection
     * @param bitmap The input bitmap
     * @return Processed bitmap
     */
    public static native Bitmap processCannyEdge(Bitmap bitmap);

    /**
     * Process a bitmap with specified filter
     * @param bitmap The input bitmap
     * @param filterType Filter type (0=Grayscale, 1=Canny Edge, 2=Original)
     * @return Processed bitmap
     */
    public static native Bitmap processBitmap(Bitmap bitmap, int filterType);

    /**
     * Clean up native resources
     */
    public static native void cleanup();

    /**
     * Log version info
     */
    public static void logVersion() {
        Log.i(TAG, "NativeImageProcessor initialized - JNI bridge active");
    }
}
