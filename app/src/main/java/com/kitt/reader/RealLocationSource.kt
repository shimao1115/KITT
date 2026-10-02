package com.kitt.reader

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat

class RealLocationSource(context: Context, private val unavailable: (String) -> Unit) : LocationSource {
    private val context = context.applicationContext
    private val manager = context.getSystemService(LocationManager::class.java)
    private var listener: LocationListener? = null
    override fun start(onFix: (Fix) -> Unit) {
        stop()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            unavailable("请允许精确定位，或在开发入口使用模拟。")
            return
        }
        val callback = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onFix(Fix(location.latitude, location.longitude, location.time,
                    if (location.hasSpeed()) location.speed.toDouble() * 3.6 else 0.0,
                    if (location.hasBearing()) location.bearing.toDouble() else 0.0,
                    if (location.hasAltitude()) location.altitude else null,
                    if (location.hasAccuracy()) location.accuracy.toDouble() else 200.0))
            }
            override fun onProviderDisabled(provider: String) { unavailable("定位已关闭；打开系统定位后继续。") }
            @Deprecated("Platform callback")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        }
        listener = callback
        try {
            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) unavailable("等待 GPS；请打开系统定位。")
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, callback, Looper.getMainLooper())
        } catch (_: SecurityException) { unavailable("定位权限不可用，请在系统设置允许。") }
    }
    override fun stop() { listener?.let { manager.removeUpdates(it) }; listener = null }
}
