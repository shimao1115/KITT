package com.kitt.reader

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat

class MainActivity : ComponentActivity() {
    private val runtime get() = (application as KittApp).runtime
    private var startAfterPermission = false
    private var microphonePermissionPending = false
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasLocation() && startAfterPermission) requestNotificationAndStart()
        startAfterPermission = false
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { startServiceJourney() }
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphonePermissionPending = false
        (runtime.voice as? AndroidVoice)?.permissionResult(granted)
    }
    private fun hasLocation() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun startJourney() {
        if (hasLocation()) requestNotificationAndStart() else {
            startAfterPermission = true
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    private fun requestNotificationAndStart() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startServiceJourney()
    }
    private fun startServiceJourney() {
        ContextCompat.startForegroundService(this, Intent(this, JourneyService::class.java).setAction(JourneyService.START))
    }
    private fun endJourney() { runtime.end(); stopService(Intent(this, JourneyService::class.java)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val r = runtime.revision.intValue
            var developer by remember { mutableStateOf(false) }; var taps by remember { mutableIntStateOf(0) }
            var settings by remember { mutableStateOf(false) }
            var unavailableSettings by remember { mutableStateOf(false) }
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF9BD2C0)) else
                lightColorScheme(primary = Color(0xFF205D52), surface = Color(0xFFF4F5EF))) {
                Surface(Modifier.fillMaxSize()) {
                    val summary = runtime.summary
                    if (settings) SettingsScreen(runtime, NotificationManagerCompat.from(this).areNotificationsEnabled(), {
                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                    }, { settings = false })
                    else if (summary != null) EndScreen(summary, runtime.store, runtime::dismissSummary)
                    else DrivingScreen(runtime.journey, (if (runtime.config.kind == ProviderKind.FAKE) "离线演示 · " else "") + runtime.sourceLabel,
                        ::startJourney, runtime.loop::speak, ::endJourney,
                        { if (runtime.journey.running) unavailableSettings = true else settings = true },
                        { taps++; if (taps >= 5) { developer = true; taps = 0 } })
                    if (unavailableSettings) AlertDialog(onDismissRequest = { unavailableSettings = false },
                        title = { Text("请结束旅程后调整设置") }, confirmButton = { TextButton({ unavailableSettings = false }) { Text("知道了") } })
                    if (runtime.recovery != null) AlertDialog(onDismissRequest = {}, title = { Text("继续刚才的旅程？") },
                        text = { Text("恢复旅程意图和临时偏好；从当前现场重新判断。") },
                        confirmButton = { TextButton({ runtime.resumeRequested = true; startJourney() }) { Text("继续") } },
                        dismissButton = { TextButton(::endJourney) { Text("结束") } })
                    if (developer) AlertDialog(onDismissRequest = { developer = false }, title = { Text("开发模拟 · 非导航级") }, text = {
                        Column {
                            Text("成都→德阳→绵阳粗粒度 fixture；只替换位置来源。请先结束当前旅程，再切换。")
                            Row { Text("模拟位置"); Switch(runtime.simulation, { runtime.developer(simulated = it) }, enabled = !runtime.journey.running) }
                            Row { listOf(1.0, 16.0, 60.0).forEach { speed -> TextButton({ runtime.developer(speed = speed) }) { Text("${speed.toInt()}×") } } }
                            Text("当前 ${runtime.acceleration.toInt()}×；模拟速度 80 km/h")
                            Button({ developer = false; if (runtime.journey.running) endJourney() else startJourney() }) {
                                Text(if (runtime.journey.running) "停止模拟 / 结束" else "开始")
                            }
                        }
                    }, confirmButton = { TextButton({ developer = false }) { Text("返回") } })
                }
            }
            LaunchedEffect(r) {
                if (runtime.journey.running) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    override fun onResume() {
        super.onResume(); runtime.foreground(true)
        (runtime.voice as? AndroidVoice)?.requestMicrophone = { microphonePermissionPending = true; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }
    override fun onPause() {
        runtime.foreground(false, microphonePermissionPending); (runtime.voice as? AndroidVoice)?.requestMicrophone = null; super.onPause()
    }
}
