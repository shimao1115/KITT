package com.kitt.reader

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class NetworkStatus(val connected: Boolean? = null, val transport: String = "传输未知",
    val validated: Boolean? = null, val vpn: Boolean? = null) {
    private val vpnLabel get() = when (vpn) { true -> "VPN已检测"; false -> "未检测到VPN"; null -> "VPN未知" }
    val label get() = when (connected) {
        null -> "网络未知 · $vpnLabel"
        false -> "网络未连接 · $vpnLabel"
        true -> "$transport · ${when (validated) { true -> "系统联网已验证"; false -> "联网未验证"; null -> "网络未知" }} · $vpnLabel"
    }
}

internal fun networkStatus(caps: NetworkCapabilities): NetworkStatus {
    val transports = listOf(NetworkCapabilities.TRANSPORT_WIFI to "Wi-Fi", NetworkCapabilities.TRANSPORT_CELLULAR to "蜂窝",
        NetworkCapabilities.TRANSPORT_ETHERNET to "以太网").filter { caps.hasTransport(it.first) }.map { it.second }
    return NetworkStatus(true, transports.joinToString("/").ifBlank { "传输未知" },
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) true else
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) false else null)
}

/** Passive default + visible VPN callbacks, owned by UI or Journey. A bypassed default is not VPN-off. */
class AndroidNetworkStatus(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    var snapshot by mutableStateOf(NetworkStatus()); private set
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var vpnCallback: ConnectivityManager.NetworkCallback? = null
    private val visibleVpns = mutableSetOf<Network>()
    private var vpnKnown = false
    private var defaultStatus = NetworkStatus()
    private var current: Network? = null
    private fun publish() {
        snapshot = defaultStatus.copy(vpn = if (defaultStatus.vpn == true || visibleVpns.isNotEmpty()) true else
            if (vpnKnown) false else null)
    }
    fun start() {
        if (callback == null) {
            val observer = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (callback !== this) return
                    current = network; defaultStatus = NetworkStatus(connected = true); publish()
                }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (callback === this && current == network) { defaultStatus = networkStatus(caps); publish() }
                }
                override fun onLost(network: Network) {
                    if (callback === this && current == network) { current = null; defaultStatus = NetworkStatus(connected = false); publish() }
                }
            }
            callback = observer
            try { manager.registerDefaultNetworkCallback(observer, handler) }
            catch (_: RuntimeException) { callback = null; snapshot = NetworkStatus(); return }
        }
        if (vpnCallback == null) {
            val observer = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (vpnCallback === this) { visibleVpns += network; publish() }
                }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (vpnCallback !== this) return
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) visibleVpns += network else visibleVpns -= network
                    publish()
                }
                override fun onLost(network: Network) {
                    if (vpnCallback === this) { visibleVpns -= network; publish() }
                }
            }
            vpnCallback = observer
            try { manager.registerNetworkCallback(NetworkRequest.Builder().removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN).build(), observer, handler) }
            catch (_: RuntimeException) { vpnCallback = null; vpnKnown = false }
        }
        refresh()
    }
    fun refresh() {
        try {
            current = manager.activeNetwork
            defaultStatus = current?.let { network -> manager.getNetworkCapabilities(network)?.let(::networkStatus)
                ?: NetworkStatus(connected = true) } ?: NetworkStatus(connected = false)
            visibleVpns.clear()
            val caps = manager.allNetworks.map { it to manager.getNetworkCapabilities(it) }
            caps.filter { it.second?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }.forEach { visibleVpns += it.first }
            vpnKnown = vpnCallback != null && caps.all { it.second != null }
            publish()
        } catch (_: RuntimeException) { defaultStatus = NetworkStatus(); vpnKnown = false; visibleVpns.clear(); publish() }
    }
    fun stop() {
        callback?.let { runCatching { manager.unregisterNetworkCallback(it) } }
        vpnCallback?.let { runCatching { manager.unregisterNetworkCallback(it) } }
        callback = null; vpnCallback = null; current = null; visibleVpns.clear(); vpnKnown = false
    }
}
