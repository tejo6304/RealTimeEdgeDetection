# Quick Start: Camera Integration Guide

## 📱 For Developers

### Step 1: Request Permissions
In your Activity or Fragment:

```kotlin
// Using modern Activity Results API (Recommended)
val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { permissions ->
    if (permissions.all { it.value }) {
        // All permissions granted - start camera
        startCamera()
    } else {
        // Some permissions denied
        showPermissionDialog()
    }
}

// Request permissions
val permissionManager = PermissionManager(this)
val requiredPermissions = permissionManager.getAllRequiredPermissions()
if (requiredPermissions.isNotEmpty()) {
    permissionLauncher.launch(requiredPermissions)
}
```

### Step 2: Initialize Camera

```kotlin
private val cameraManager by lazy { CameraManager(this) }

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ... setup views ...
}

override fun onResume() {
    super.onResume()
    if (permissionManager.hasCameraPermission()) {
        cameraManager.startCamera(previewView, this)
    }
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

### Step 3: Add Image Analysis (Optional)

```kotlin
val analyzer = EdgeDetectionAnalyzer()
cameraManager.startCamera(previewView, this, analyzer)
```

### Step 4: Save Processed Images

```kotlin
private val storageUtils by lazy { ImageStorageUtils(this) }

fun saveProcessedImage(bitmap: Bitmap) {
    if (permissionManager.hasStoragePermissions()) {
        storageUtils.saveBitmap(bitmap)
    }
}

fun getAllProcessedImages(): List<File> {
    return storageUtils.getAllSavedImages()
}
```

## 🔍 Layout Configuration

### Option 1: Using CameraX PreviewView (Modern)
```xml
<androidx.camera.view.PreviewView
    android:id="@+id/previewView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

### Option 2: Using TextureView (Current Implementation)
```xml
<TextureView
    android:id="@+id/textureView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

## 🚀 Complete Example Activity (Kotlin)

```kotlin
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.realtimeedgedetection.*

class CameraActivity : AppCompatActivity() {
    
    private lateinit var previewView: PreviewView
    private val permissionManager by lazy { PermissionManager(this) }
    private val cameraManager by lazy { CameraManager(this) }
    private val storageUtils by lazy { ImageStorageUtils(this) }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            startCamera()
        } else {
            Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        previewView = findViewById(R.id.previewView)
        
        // Request permissions on first launch
        if (!permissionManager.hasCameraPermission() ||
            !permissionManager.hasStoragePermissions()) {
            requestPermissions()
        } else {
            startCamera()
        }
    }
    
    private fun requestPermissions() {
        val requiredPermissions = permissionManager.getAllRequiredPermissions()
        if (requiredPermissions.isNotEmpty()) {
            permissionLauncher.launch(requiredPermissions)
        }
    }
    
    private fun startCamera() {
        val analyzer = EdgeDetectionAnalyzer()
        cameraManager.startCamera(previewView, this, analyzer)
    }
    
    override fun onResume() {
        super.onResume()
        if (permissionManager.hasCameraPermission()) {
            startCamera()
        }
    }
    
    override fun onPause() {
        cameraManager.stopCamera()
        super.onPause()
    }
    
    override fun onDestroy() {
        cameraManager.shutdown()
        super.onDestroy()
    }
}
```

## ⚠️ Important Permissions Notes

### Minimum Requirements
- **Android 5.0-5.1 (API 21-22)**: Request permissions in manifest only
- **Android 6.0+ (API 23+)**: Request permissions at runtime (this app targets 23+)
- **Android 11+ (API 30+)**: Scoped storage applies automatically

### Permission Categories
1. **Camera Permission**: Required to access device camera
2. **Storage Permissions**: Required to save processed images
   - READ_EXTERNAL_STORAGE: Read from storage
   - WRITE_EXTERNAL_STORAGE: Write to storage

### Checking Permissions
```kotlin
// Check if camera is available
if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
    == PackageManager.PERMISSION_GRANTED) {
    // Camera permission is granted
}

// Check if storage is available
val storagePermissions = arrayOf(
    Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE
)
val allGranted = storagePermissions.all {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}
```

## 🎯 Testing Checklist

- [ ] App requests camera permission on first launch
- [ ] App requests storage permissions for image saving
- [ ] Camera preview shows correctly after permissions granted
- [ ] Can capture and process frames
- [ ] Images save successfully to storage
- [ ] App handles permission denial gracefully
- [ ] Camera closes properly on pause
- [ ] No memory leaks on activity destroy

## 🐛 Troubleshooting

### Camera won't open
1. Check logcat for error messages in CameraManager
2. Verify CAMERA permission is in manifest
3. Ensure camera permission is granted at runtime
4. Check if another app is using camera

### Permission dialog not showing
1. Clear app data and reinstall
2. Grant permissions in Settings > Apps > [App Name] > Permissions
3. Check if permission was already denied permanently

### Images not saving
1. Verify WRITE_EXTERNAL_STORAGE permission is granted
2. Check available storage space
3. Look for IO exceptions in logcat
4. Verify directory creation in logcat

### Performance issues
1. Lower image resolution in ImageAnalysis
2. Use STRATEGY_KEEP_ONLY_LATEST backpressure
3. Profile CPU/memory usage with Android Profiler
4. Consider frame skipping for heavy processing

## 📚 Related Classes
- `CameraManager` - Main camera management
- `PermissionManager` - Permission handling
- `ImageStorageUtils` - Image I/O
- `EdgeDetectionAnalyzer` - Frame processing
- `PermissionExtensions` - Kotlin helpers

## 🔗 Documentation
- Full guide: See `CAMERA_INTEGRATION.md`
- Implementation details: See `IMPLEMENTATION_SUMMARY.md`
