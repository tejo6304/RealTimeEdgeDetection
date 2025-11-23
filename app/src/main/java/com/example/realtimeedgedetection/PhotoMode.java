package com.example.realtimeedgedetection;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;
import android.view.TextureView;

import java.io.File;

public class PhotoMode {
    private static final String TAG = "PhotoMode";

    private final Context context;
    private final TextureView textureView;
    private final Handler backgroundHandler;
    private int currentFilterType = 1; // Default to Canny Edge
    private boolean isCapturing = false;

    public PhotoMode(Context context, TextureView textureView, Handler backgroundHandler) {
        this.context = context;
        this.textureView = textureView;
        this.backgroundHandler = backgroundHandler;
    }

    public void setFilterType(int filterType) {
        currentFilterType = filterType;
    }

    public void capturePhotoWithFilter(
            OnCaptureDone onCaptureDone,
            OnProcessingDone onProcessingDone
    ) {
        if (isCapturing) {
            Log.w(TAG, "Capture already in progress");
            return;
        }

        isCapturing = true;
        Log.d(TAG, "Starting capture process");
        
        backgroundHandler.post(() -> {
            try {
                // Get bitmap from texture view
                Bitmap originalBitmap = textureView.getBitmap();

                if (originalBitmap != null) {
                    Log.d(TAG, "Original bitmap captured: " + originalBitmap.getWidth() + "x" + originalBitmap.getHeight());
                    onCaptureDone.onCapture(originalBitmap);

                    // Process the bitmap with the selected filter
                    Thread processingThread = new Thread(() -> {
                        try {
                            Bitmap processedBitmap = processImageWithFilter(originalBitmap, currentFilterType);
                            onProcessingDone.onProcessing(processedBitmap);
                            Log.d(TAG, "Image processing completed");
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing image: " + e.getMessage(), e);
                            onProcessingDone.onProcessing(null);
                        }
                    });
                    processingThread.start();
                } else {
                    Log.e(TAG, "Failed to capture bitmap from TextureView");
                    onCaptureDone.onCapture(null);
                    onProcessingDone.onProcessing(null);
                    isCapturing = false;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in capturePhotoWithFilter: " + e.getMessage(), e);
                onCaptureDone.onCapture(null);
                onProcessingDone.onProcessing(null);
                isCapturing = false;
            }
        });
        
        // Reset flag after a delay to allow for multiple rapid captures
        backgroundHandler.postDelayed(() -> {
            isCapturing = false;
            Log.d(TAG, "Capture flag reset - ready for next capture");
        }, 500);
    }

    private Bitmap processImageWithFilter(Bitmap bitmap, int filterType) {
        try {
            switch (filterType) {
                case 0:
                    Log.d(TAG, "Applying Grayscale filter");
                    return NativeImageProcessor.processGrayscale(bitmap);
                case 1:
                    Log.d(TAG, "Applying Canny Edge filter");
                    return NativeImageProcessor.processCannyEdge(bitmap);
                default:
                    Log.d(TAG, "Returning original image");
                    return bitmap;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing image with filter " + filterType + ": " + e.getMessage(), e);
            return null;
        }
    }

    public void saveCapturedImage(
            Bitmap bitmap,
            ImageStorageUtils imageStorageUtils,
            OnSaveDone onSaveDone
    ) {
        backgroundHandler.post(() -> {
            try {
                String filterName = getFilterName(currentFilterType);
                File savedFile = imageStorageUtils.saveBitmapWithFilter(bitmap, filterName);
                onSaveDone.onSave(savedFile);
            } catch (Exception e) {
                Log.e(TAG, "Error saving image: " + e.getMessage(), e);
                onSaveDone.onSave(null);
            }
        });
    }

    private String getFilterName(int filterType) {
        switch (filterType) {
            case 0:
                return "grayscale";
            case 1:
                return "canny";
            default:
                return "original";
        }
    }

    public void cleanup() {
        Log.d(TAG, "PhotoMode cleaned up");
    }

    // Callback interfaces
    public interface OnCaptureDone {
        void onCapture(Bitmap bitmap);
    }

    public interface OnProcessingDone {
        void onProcessing(Bitmap bitmap);
    }

    public interface OnSaveDone {
        void onSave(File file);
    }
}
