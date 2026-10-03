package com.kitt.reader

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class JourneyService : Service() {
    private val runtime get() = (application as KittApp).runtime
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "正在读山河", NotificationManager.IMPORTANCE_LOW).apply {
                description = "旅程中的安静与结束控制"; setSound(null, null); enableVibration(false); setShowBadge(false)
            })
        runtime.notificationChanged = ::updateNotification
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            END -> { runtime.end(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
            QUIET -> { runtime.quietToggle(); return START_NOT_STICKY }
            START -> {
                try {
                    if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                    else startForeground(ID, notification())
                    if (!runtime.journey.running) runtime.start(simulated = intent.getBooleanExtra(SIMULATED, false))
                    updateNotification()
                } catch (_: SecurityException) {
                    android.util.Log.w("KITT", "Location foreground permission unavailable")
                    runtime.locationUnavailable("定位不可用，请打开系统定位并允许位置权限。"); stopSelf()
                }
            }
            else -> { stopSelf(); return START_NOT_STICKY }
        }
        return START_NOT_STICKY
    }
    private fun actionIntent(action: String) = PendingIntent.getService(this, action.hashCode(),
        Intent(this, JourneyService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_journey).setContentTitle("路上读山河")
            .setContentText(if (runtime.journey.isQuiet) "安静模式" else "旅程进行中")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(null, if (runtime.journey.isQuiet) "结束安静" else "安静 10 分钟", actionIntent(QUIET)).build())
            .addAction(Notification.Action.Builder(null, "结束旅程", actionIntent(END)).build()).build()
    }
    private fun updateNotification() { getSystemService(NotificationManager::class.java).notify(ID, notification()) }
    override fun onDestroy() {
        runtime.notificationChanged = null; runtime.serviceLost()
        super.onDestroy()
    }
    companion object {
        const val START = "com.kitt.reader.START"; const val END = "com.kitt.reader.END"; const val QUIET = "com.kitt.reader.QUIET"
        const val CHANNEL = "journey"; const val ID = 7
        const val SIMULATED = "com.kitt.reader.SIMULATED"
    }
}
