package com.example.realtimeedgedetection

import android.content.Context
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

object CameraPermissionHelper {
    
    fun createCameraPermissionLauncher(
        activity: AppCompatActivity,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            onGranted()
        } else {
            onDenied()
        }
    }
    
    fun createStoragePermissionLauncher(
        activity: AppCompatActivity,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            onGranted()
        } else {
            onDenied()
        }
    }
}

fun Fragment.requestCameraPermission(
    onGranted: () -> Unit,
    onDenied: () -> Unit
) {
    val launcher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onGranted()
        } else {
            onDenied()
        }
    }
    launcher.launch(android.Manifest.permission.CAMERA)
}

fun Fragment.requestStoragePermissions(
    onGranted: () -> Unit,
    onDenied: () -> Unit
) {
    val launcher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            onGranted()
        } else {
            onDenied()
        }
    }
    val permissions = arrayOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    launcher.launch(permissions)
}
