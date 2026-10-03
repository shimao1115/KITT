package com.kitt.reader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** A bounded login task, separate from Journey: prevents OEM freezing the loopback listener in the browser. */
class ChatGptAuthService : Service() {
    private val account get() = (application as KittApp).runtime.chatGpt
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL || !account.busy) { account.cancelSignIn(); stopSelf(); return START_NOT_STICKY }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "ChatGPT 登录", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false) })
        val open = PendingIntent.getActivity(this, 40, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getService(this, 41, Intent(this, javaClass).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_journey)
            .setContentTitle("完成 ChatGPT 登录").setContentText("请在系统浏览器授权后返回沿途；等待最多两分半。")
            .setContentIntent(open).setOngoing(true).setSilent(true).addAction(0, "取消登录", cancel).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        else startForeground(ID, notification)
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int) { account.cancelSignIn(); stopSelf() }
    override fun onTimeout(startId: Int, fgsType: Int) { account.cancelSignIn(); stopSelf() }
    override fun onDestroy() {
        if (account.busy) account.cancelSignIn()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object { const val CANCEL = "com.kitt.reader.CANCEL_CHATGPT_AUTH"; private const val CHANNEL = "chatgpt-auth"; private const val ID = 2 }
}
