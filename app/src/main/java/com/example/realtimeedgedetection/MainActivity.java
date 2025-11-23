package com.example.realtimeedgedetection;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.realtimeedgedetection.databinding.ActivityMainBinding;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    static {
        System.loadLibrary("realtimeedgedetection");
    }

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private ActivityMainBinding binding;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private Surface surface;

    private int currentFilter = 1; // 0: Grayscale, 1: Canny Edge, 2: Original
    private int cameraFacing = CameraCharacteristics.LENS_FACING_BACK; // 0: Back, 1: Front
    
    // FPS Counter variables
    private long lastFpsUpdateTime = 0;
    private int frameCount = 0;
    private double currentFps = 0.0;
    
    // Image storage
    private ImageStorageUtils imageStorageUtils;
    
    // Real-time frame processor
    private RealtimeFrameProcessor frameProcessor;
    
    // Photo mode handler
    private PhotoMode photoMode;

    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable");
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
            updateFps();
            
            // Update filter in JNI layer
            if (backgroundHandler != null && imageDimension != null) {
                backgroundHandler.post(() -> {
                    if (currentFilter >= 0 && currentFilter <= 2) {
                        // Signal the native layer about the current filter
                        notifyFilterChange(currentFilter);
                    }
                });
            }
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "onDisconnected");
            camera.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "onError: " + error);
            camera.close();
            cameraDevice = null;
        }
    };

    // Web server for remote viewing
    private WebServerManager webServerManager;
    
    // Data flow controller for pipeline management
    private DataFlowController dataFlowController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize image storage utility
        imageStorageUtils = new ImageStorageUtils(this);
        
        // Initialize frame processor
        frameProcessor = new RealtimeFrameProcessor(binding.textureView);
        
        // Initialize web server for remote viewing (optional)
        webServerManager = new WebServerManager(this);
        
        // Initialize data flow controller
        dataFlowController = new DataFlowController(this);
        dataFlowController.initialize();
        
        binding.textureView.setSurfaceTextureListener(textureListener);

        binding.grayscaleButton.setOnClickListener(v -> {
            Log.d(TAG, "Grayscale button clicked");
            setFilter(0);
        });
        binding.cannyEdgeButton.setOnClickListener(v -> {
            Log.d(TAG, "Canny Edge button clicked");
            setFilter(1);
        });
        binding.originalButton.setOnClickListener(v -> {
            Log.d(TAG, "Original button clicked");
            setFilter(2);
        });
        
        // Capture button listener
        binding.captureButton.setOnClickListener(v -> {
            Log.d(TAG, "Capture button clicked - starting capture");
            captureImage();
        });
        
        // Gallery button listener
        binding.galleryButton.setOnClickListener(v -> {
            Log.d(TAG, "Gallery button clicked");
            openGallery();
        });
        
        // Flip camera button listener
        binding.cameraFlipButton.setOnClickListener(v -> {
            Log.d(TAG, "Flip camera button clicked");
            flipCamera();
        });
    }

    private void setFilter(int filter) {
        Log.d(TAG, "setFilter: " + filter);
        currentFilter = filter;
        frameProcessor.setFilterType(filter);
        if (photoMode != null) {
            photoMode.setFilterType(filter);
        } else {
            Log.w(TAG, "PhotoMode not initialized yet");
        }
        updateButtonStyles();
        binding.filterText.setText(getFilterName(filter));
        
        // Try to apply filter to current preview frame
        applyFilterToPreview(filter);
    }
    
    private void applyFilterToPreview(int filterType) {
        // This method notifies the native layer which filter to apply
        // The actual filter application happens in the camera preview rendering
        if (backgroundHandler != null) {
            backgroundHandler.post(() -> {
                // Signal to native layer that filter has changed
                notifyFilterChange(filterType);
                Log.d(TAG, "Filter change notified to native layer: " + filterType);
            });
        }
    }
    
    private String getFilterName(int filter) {
        switch (filter) {
            case 0:
                return "Grayscale";
            case 1:
                return "Canny Edge";
            case 2:
                return "Original";
            default:
                return "";
        }
    }

    private void updateButtonStyles() {
        binding.grayscaleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.inactive_button));
        binding.cannyEdgeButton.setBackgroundColor(ContextCompat.getColor(this, R.color.inactive_button));
        binding.originalButton.setBackgroundColor(ContextCompat.getColor(this, R.color.inactive_button));

        switch (currentFilter) {
            case 0:
                binding.grayscaleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.active_button));
                break;
            case 1:
                binding.cannyEdgeButton.setBackgroundColor(ContextCompat.getColor(this, R.color.active_button));
                break;
            case 2:
                binding.originalButton.setBackgroundColor(ContextCompat.getColor(this, R.color.active_button));
                break;
        }
    }

    protected void startBackgroundThread() {
        Log.d(TAG, "startBackgroundThread");
        backgroundThread = new HandlerThread("Camera Background");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        Log.d(TAG, "stopBackgroundThread");
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "stopBackgroundThread: ", e);
        }
    }

    protected void createCameraPreview() {
        Log.d(TAG, "createCameraPreview");
        try {
            SurfaceTexture texture = binding.textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "onConfigured");
                    if (null == cameraDevice) {
                        return;
                    }
                    captureSession = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "onConfigureFailed");
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "createCameraPreview: ", e);
        }
    }
    
    private void setupImageReader(int width, int height) {
        // Placeholder - not actively used in current implementation
    }
    
    private void processFrameForFilter(Image image, int filterType) {
        // Placeholder - not actively used in current implementation
    }
    
    private Bitmap convertImageToBitmap(Image image) {
        return null;
    }

    private void openCamera() {
        Log.d(TAG, "openCamera");
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = null;
            // Find camera with the desired facing
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == cameraFacing) {
                    cameraId = id;
                    break;
                }
            }
            
            // Fallback to first camera if desired facing not found
            if (cameraId == null) {
                cameraId = manager.getCameraIdList()[0];
            }
            
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), binding.textureView.getWidth(), binding.textureView.getHeight());
            binding.resolutionText.setText(String.format("%dx%d", imageDimension.getWidth(), imageDimension.getHeight()));


            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "openCamera: ", e);
        }
    }

    private Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight) {
        List<Size> bigEnough = new ArrayList<>();
        List<Size> notBigEnough = new ArrayList<>();
        int w = textureViewWidth;
        int h = textureViewHeight;
        for (Size option : choices) {
            if (option.getWidth() >= w && option.getHeight() >= h) {
                bigEnough.add(option);
            } else {
                notBigEnough.add(option);
            }
        }

        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    protected void updatePreview() {
        Log.d(TAG, "updatePreview");
        if (null == cameraDevice) {
            return;
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        try {
            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
            binding.cameraFeedPlaceholder.setVisibility(View.GONE);
        } catch (CameraAccessException e) {
            Log.e(TAG, "updatePreview: ", e);
        }
    }

    private void closeCamera() {
        Log.d(TAG, "closeCamera");
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != captureSession) {
            captureSession.close();
            captureSession = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(MainActivity.this, "You can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        startBackgroundThread();
        
        // Start web server and data flow pipeline
        dataFlowController.startPipeline();
        webServerManager.startServer();
        
        // Initialize photo mode after background thread is ready
        if (photoMode == null) {
            photoMode = new PhotoMode(this, binding.textureView, backgroundHandler);
        }
        
        if (binding.textureView.isAvailable()) {
            openCamera();
        } else {
            binding.textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        
        // Stop web server and data flow pipeline
        dataFlowController.stopPipeline();
        webServerManager.stopServer();
        
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (photoMode != null) {
            photoMode.cleanup();
        }
        super.onDestroy();
        cleanup();
    }

    private void captureImage() {
        Log.d(TAG, "Capture image clicked");
        
        photoMode.capturePhotoWithFilter(
            bitmap -> {
                if (bitmap != null) {
                    Log.d(TAG, "Original bitmap captured successfully");
                }
            },
            bitmap -> {
                if (bitmap != null) {
                    Log.d(TAG, "Image processed successfully");
                    // Save the processed image
                    photoMode.saveCapturedImage(bitmap, imageStorageUtils, file -> {
                        if (file != null) {
                            Toast.makeText(MainActivity.this, "Image saved: " + file.getName(), Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "Image saved to: " + file.getAbsolutePath());
                        } else {
                            Toast.makeText(MainActivity.this, "Failed to save image", Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "Failed to save image");
                        }
                    });
                } else {
                    Toast.makeText(MainActivity.this, "Failed to process image", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Image processing failed");
                }
            }
        );
    }

    private void openGallery() {
        Log.d(TAG, "Gallery button clicked");
        
        try {
            // Open WebViewerActivity which displays captured images with filters
            Intent intent = new Intent(MainActivity.this, WebViewerActivity.class);
            startActivity(intent);
            Log.d(TAG, "Opening web viewer gallery");
        } catch (Exception e) {
            Toast.makeText(this, "Error opening gallery: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error opening gallery: " + e.getMessage(), e);
        }
    }

    private void flipCamera() {
        Log.d(TAG, "Flip camera clicked");
        // Toggle between back and front camera
        cameraFacing = (cameraFacing == CameraCharacteristics.LENS_FACING_BACK) 
            ? CameraCharacteristics.LENS_FACING_FRONT 
            : CameraCharacteristics.LENS_FACING_BACK;
        
        String cameraType = cameraFacing == CameraCharacteristics.LENS_FACING_BACK ? "Back" : "Front";
        Toast.makeText(this, "Switched to " + cameraType + " camera", Toast.LENGTH_SHORT).show();
        
        closeCamera();
        startBackgroundThread();
        if (binding.textureView.isAvailable()) {
            openCamera();
        } else {
            binding.textureView.setSurfaceTextureListener(textureListener);
        }
    }

    private void updateFps() {
        frameCount++;
        long currentTime = System.currentTimeMillis();
        
        // Update FPS every second (1000ms)
        if (currentTime - lastFpsUpdateTime >= 1000) {
            currentFps = frameCount;
            frameCount = 0;
            lastFpsUpdateTime = currentTime;
            
            // Update UI on main thread
            runOnUiThread(() -> {
                String fpsText = String.format("%.1f", currentFps);
                binding.fpsText.setText(fpsText);
                Log.d(TAG, "FPS: " + fpsText);
            });
        }
    }

    public native void processFrame(Surface surface, int width, int height, int filterType);
    public native void notifyFilterChange(int filterType);
    public native void cleanup();
}
