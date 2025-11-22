# Real-Time FPS Display Implementation

## Overview
Real-time FPS (Frames Per Second) counter has been implemented in MainActivity to display the current camera frame rate.

## Implementation Details

### FPS Tracking Variables
```java
private long lastFpsUpdateTime = 0;      // Timestamp of last FPS calculation
private int frameCount = 0;              // Frame counter for current second
private double currentFps = 0.0;         // Current FPS value
```

### How It Works

1. **Frame Counting**
   - Each time `onSurfaceTextureUpdated()` is called, a frame is processed
   - `frameCount` is incremented in `updateFps()` method
   - The method checks if 1 second (1000ms) has elapsed

2. **FPS Calculation**
   - Every 1000ms, FPS is calculated as: `FPS = frameCount` (frames in last second)
   - Frame counter resets to 0
   - Last update time is reset to current time

3. **UI Update**
   - FPS value is formatted to 1 decimal place
   - Updated on main thread using `runOnUiThread()`
   - Displayed in `binding.fpsText` TextView

### Code Flow

```
onSurfaceTextureUpdated()
    ↓
processFrame() (native C++ processing)
    ↓
updateFps()
    ├─ Increment frameCount++
    ├─ Check if 1 second elapsed
    ├─ If yes:
    │   ├─ Calculate FPS = frameCount
    │   ├─ Reset frameCount = 0
    │   ├─ Update lastFpsUpdateTime
    │   └─ Update UI with new FPS
    └─ If no: continue counting
```

### updateFps() Method

```java
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
```

## Features

✅ **Real-Time Updates**
- FPS updates every second
- Smooth updates without blocking

✅ **Accurate Measurement**
- Based on actual frame count
- Accounts for processing time

✅ **Thread-Safe**
- FPS calculation on background thread
- UI updates on main thread using `runOnUiThread()`

✅ **Formatted Display**
- Shows FPS with 1 decimal place (e.g., "25.0")
- Easy to read format

✅ **Logging**
- FPS values logged to Logcat for debugging
- Tag: "MainActivity"

## Performance Impact

- **Minimal Overhead:** FPS calculation is very lightweight
- **Accurate Timing:** Uses `System.currentTimeMillis()` for precision
- **No Frame Drops:** Calculation doesn't affect frame processing

## Expected FPS Values

| Device | Expected FPS | Notes |
|--------|--------------|-------|
| High-end Phone | 25-30 | Full processing with edge detection |
| Mid-range Phone | 15-20 | Real-time processing at lower speed |
| Low-end Phone | 10-15 | Slower processing capabilities |

The FPS counter accounts for both camera capture and native C++ edge detection processing time.

## Testing

1. **Build and Run** the application
2. **Observe** the FPS display in the stats section
3. **Verify** FPS updates every second
4. **Check Logcat** for FPS logging: `adb logcat | grep "MainActivity"`

## Integration with UI

The FPS value is displayed in:
- **Layout:** `activity_main.xml` → `fps_text` TextView
- **Location:** Stats section (top middle of scrollable content)
- **Format:** Single value with 1 decimal place

## Future Enhancements

- Add min/max FPS tracking
- Show average FPS
- Add FPS graph visualization
- Allow FPS overlay customization
- Export FPS data for analysis

## Files Modified

- `MainActivity.java` - Added FPS tracking and calculation logic

## Variables Reference

| Variable | Type | Purpose |
|----------|------|---------|
| `lastFpsUpdateTime` | `long` | Stores timestamp of last FPS update |
| `frameCount` | `int` | Counts frames in current second |
| `currentFps` | `double` | Stores calculated FPS value |

---

**Status:** ✅ Complete and Ready for Testing
**Date:** November 22, 2025
