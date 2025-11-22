package com.example.realtimeedgedetection;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
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
            if (cameraDevice != null && backgroundHandler != null) {
                backgroundHandler.post(() -> {
                    if (surface != null) {
                        processFrame(surface, imageDimension.getWidth(), imageDimension.getHeight(), currentFilter);
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.textureView.setSurfaceTextureListener(textureListener);

        binding.grayscaleButton.setOnClickListener(v -> setFilter(0));
        binding.cannyEdgeButton.setOnClickListener(v -> setFilter(1));
        binding.originalButton.setOnClickListener(v -> setFilter(2));
    }

    private void setFilter(int filter) {
        Log.d(TAG, "setFilter: " + filter);
        currentFilter = filter;
        updateButtonStyles();
        binding.filterText.setText(getFilterName(filter));
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

    private void openCamera() {
        Log.d(TAG, "openCamera");
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0];
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
        if (binding.textureView.isAvailable()) {
            openCamera();
        } else {
            binding.textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        cleanup();
    }

    public native void processFrame(Surface surface, int width, int height, int filterType);
    public native void cleanup();
}
