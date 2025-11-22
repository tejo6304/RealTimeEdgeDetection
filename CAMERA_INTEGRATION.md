# Camera Integration & Permissions Guide

## Overview
This guide documents the camera integration implemented for the Real-Time Edge Detection app using modern Android APIs.

## Changes Made

### 1. AndroidManifest.xml Updates
Added the following permissions and features:
- **CAMERA** permission - Required for camera access
- **READ_EXTERNAL_STORAGE** - For reading images from device storage
- **WRITE_EXTERNAL_STORAGE** - For saving processed images
- **camera hardware feature** - Declares camera usage
- **camera.autofocus hardware feature** - Declares autofocus support
- **OpenGL ES 2.0 feature** - For graphics rendering

### 2. Gradle Dependencies
Updated `gradle/libs.versions.toml` with CameraX and Lifecycle dependencies:
- **camerax-core** (1.3.0) - Core CameraX functionality
- **camerax-camera2** (1.3.0) - Camera2 integration
- **camerax-lifecycle** (1.3.0) - Lifecycle-aware camera management
- **camerax-view** (1.3.0) - PreviewView widget for camera preview
- **lifecycle-runtime** (2.6.2) - Lifecycle management utilities

### 3. New Kotlin Classes

#### CameraManager.kt
Provides a high-level API for managing camera operations using CameraX:
- `startCamera()` - Initialize camera preview and image analysis
- `stopCamera()` - Release camera resources
- `shutdown()` - Clean up executor service

**Usage:**
```kotlin
val cameraManager = CameraManager(context)
cameraManager.startCamera(previewView, lifecycleOwner, imageAnalyzer)
// Later...
cameraManager.shutdown()
```

#### PermissionManager.kt
Handles runtime permission checking and requesting:
- `hasCameraPermission()` - Check if camera permission is granted
- `hasStoragePermissions()` - Check storage permissions (handles API level differences)
- `getCameraPermissionsToRequest()` - Get camera permissions that need requesting
- `getStoragePermissionsToRequest()` - Get storage permissions that need requesting
- `getAllRequiredPermissions()` - Get all permissions that need requesting

**Usage:**
```kotlin
val permissionManager = PermissionManager(context)
val requiredPermissions = permissionManager.getAllRequiredPermissions()
if (requiredPermissions.isNotEmpty()) {
    ActivityCompat.requestPermissions(this, requiredPermissions, REQUEST_CODE)
}
```

#### ImageStorageUtils.kt
Manages image storage and retrieval for processed images:
- `saveBitmap()` - Save bitmap to external files directory
- `generateFilename()` - Generate timestamped filenames
- `getSavedImagesDirectory()` - Get storage directory
- `getAllSavedImages()` - List all saved images
- `deleteImage()` - Delete an image file

**Usage:**
```kotlin
val storageUtils = ImageStorageUtils(context)
storageUtils.saveBitmap(processedBitmap, "edge_detection.png")
val savedImages = storageUtils.getAllSavedImages()
```

#### PermissionExtensions.kt
Kotlin extension functions for modern permission handling using Activity Results API:
- `CameraPermissionHelper.createCameraPermissionLauncher()` - Create permission launcher
- `Fragment.requestCameraPermission()` - Request camera permission in Fragment
- `Fragment.requestStoragePermissions()` - Request storage permissions in Fragment

**Usage:**
```kotlin
val launcher = CameraPermissionHelper.createCameraPermissionLauncher(
    this,
    onGranted = { startCamera() },
    onDenied = { showPermissionDialog() }
)
launcher.launch(permissionManager.getAllRequiredPermissions())
```

#### EdgeDetectionAnalyzer.kt
Example implementation of `ImageAnalysis.Analyzer` for real-time frame processing:
- Converts images to bitmap format
- Handles YUV and RGBA image formats
- Ready for custom edge detection processing

### 4. Existing MainActivity.java Integration
The app continues to use the existing Camera2 API implementation with:
- TextureView for camera preview
- SurfaceTexture for rendering
- Native C++ code integration for edge detection
- Background thread handling

## Permission Handling

### Runtime Permissions (Android 6.0+)
The app requires runtime permission requests for:
1. **Camera** - To access device camera
2. **Storage** - To save processed images

### API Level Handling
- **Android 6.0-10**: Requires explicit READ/WRITE_EXTERNAL_STORAGE permissions
- **Android 11+**: Uses Scoped Storage (automatic, no permission needed for app-specific directory)

### Implementation Options

**Option 1: Traditional requestPermissions()**
```kotlin
if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
    != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE)
}
```

**Option 2: Modern Activity Results API (Recommended)**
```kotlin
val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { permissions ->
    if (permissions.values.all { it }) {
        // All permissions granted
    }
}
permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
```

## Camera Preview Setup

### Using CameraX PreviewView
```kotlin
<androidx.camera.view.PreviewView
    android:id="@+id/previewView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

### Using TextureView (Current Implementation)
The existing MainActivity uses TextureView with SurfaceTexture for custom rendering of edge detection results.

## Surface Texture Configuration
The app creates a surface texture with:
- Dimensions matching camera resolution and view size
- Optimization for real-time processing
- Native C++ integration for edge detection filters

```kotlin
val texture = textureView.surfaceTexture
texture.setDefaultBufferSize(width, height)
val surface = Surface(texture)
```

## Image Storage

### Default Location
Images are saved to: `Context.getExternalFilesDir(DIRECTORY_PICTURES)/RealTimeEdgeDetection/`

### Fallback
If external storage is unavailable, images are saved to: `Context.filesDir`

### Filename Format
Processed images are saved with timestamp: `edge_detection_yyyy-MM-dd_HH:mm:ss.png`

## Cleanup and Lifecycle

Always clean up resources in appropriate lifecycle callbacks:

```kotlin
override fun onResume() {
    super.onResume()
    cameraManager.startCamera(previewView, this)
}

override fun onPause() {
    cameraManager.stopCamera()
    super.onPause()
}

override fun onDestroy() {
    cameraManager.shutdown()
    super.onDestroy()
}
```

## Testing Permissions

### Check Permissions Before Using Camera
```kotlin
val permissionManager = PermissionManager(context)
if (permissionManager.hasCameraPermission() && permissionManager.hasStoragePermissions()) {
    startCamera()
} else {
    requestMissingPermissions()
}
```

### Handle Permission Denial
Gracefully handle when users deny permissions:
```kotlin
if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {
    Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
    finish()
}
```

## Build Configuration

Ensure the following in `app/build.gradle.kts`:
- `buildFeatures.viewBinding = true` (for view binding support)
- `compileSdk 36` and `targetSdk 36`
- `minSdk 24` (supports Android 7.0+)

## Troubleshooting

### Camera won't open
- Check if CAMERA permission is granted
- Verify camera is not in use by another app
- Check logcat for CameraManager errors

### Images not saving
- Verify WRITE_EXTERNAL_STORAGE permission is granted
- Check if storage is available
- Ensure directory creation succeeded

### SurfaceTexture not available
- Wait for TextureView.SurfaceTextureListener.onSurfaceTextureAvailable callback
- Don't access surface texture before callback

## Next Steps

1. Implement edge detection processing in EdgeDetectionAnalyzer
2. Add capture functionality to save edge detection results
3. Implement camera facing toggle (front/back)
4. Add video recording capability
5. Optimize frame processing performance
