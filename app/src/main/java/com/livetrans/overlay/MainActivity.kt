package com.livetrans.overlay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.livetrans.overlay.data.AudioSourceType
import com.livetrans.overlay.service.LiveSubtitleService
import com.livetrans.overlay.ui.screens.HomeScreen
import com.livetrans.overlay.ui.theme.LiveTransTheme

class MainActivity : ComponentActivity() {

    private var isServiceRunning by mutableStateOf(false)

    // Activity result launcher for System Audio Capture (MediaProjection)
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            launchSubtitleService()
        } else {
            Toast.makeText(this, "需要系统录音授权以捕获内录音频", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission request launcher for Microphone and Notification
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (recordGranted) {
            checkOverlayAndStart()
        } else {
            Toast.makeText(this, "请授予录音权限以进行实时同传识别", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as LiveTransApplication

        setContent {
            LiveTransTheme {
                HomeScreen(
                    isServiceRunning = isServiceRunning,
                    appPreferences = app.appPreferences,
                    onStartService = { checkPermissionsAndStart() },
                    onStopService = { stopSubtitleService() }
                )
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            checkOverlayAndStart()
        }
    }

    private fun checkOverlayAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请在系统设置中允许“显示在其他应用上层”悬浮窗权限", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        val app = application as LiveTransApplication
        val audioSource = app.appPreferences.loadAudioSource()

        if (audioSource == AudioSourceType.SYSTEM_INTERNAL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        } else {
            launchSubtitleService()
        }
    }

    private fun launchSubtitleService() {
        LiveSubtitleService.start(this)
        isServiceRunning = true
        Toast.makeText(this, "悬浮同传字幕已开启，已悬浮于屏幕上方", Toast.LENGTH_SHORT).show()
    }

    private fun stopSubtitleService() {
        LiveSubtitleService.stop(this)
        isServiceRunning = false
        Toast.makeText(this, "悬浮字幕已关闭", Toast.LENGTH_SHORT).show()
    }
}
