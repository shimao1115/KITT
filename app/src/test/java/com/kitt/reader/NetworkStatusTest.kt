package com.kitt.reader

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkStatusTest {
    private fun caps(transport: Int, vpn: Boolean = false, validated: Boolean = true) = NetworkCapabilities().apply {
        shadowOf(this).addTransportType(transport)
        if (vpn) { shadowOf(this).addTransportType(NetworkCapabilities.TRANSPORT_VPN); shadowOf(this).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) }
        else shadowOf(this).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        shadowOf(this).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (validated) shadowOf(this).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    @Test fun wifiAndCellularVpnAreSystemSignalsOnly() {
        val wifi = networkStatus(caps(NetworkCapabilities.TRANSPORT_WIFI, vpn = true))
        assertEquals("Wi-Fi", wifi.transport); assertEquals(true, wifi.vpn); assertEquals(true, wifi.validated)
        assertFalse(wifi.label.contains("AI"))
        val cellular = networkStatus(caps(NetworkCapabilities.TRANSPORT_CELLULAR))
        assertEquals("蜂窝", cellular.transport); assertEquals(false, cellular.vpn)
    }
    @Test fun internetCapabilityAloneIsNotValidated() {
        val value = networkStatus(caps(NetworkCapabilities.TRANSPORT_WIFI, validated = false))
        assertEquals(false, value.validated); assertTrue(value.label.contains("联网未验证"))
    }
    @Test fun missingInformationAndVpnWithoutPhysicalTransportStayUnknown() {
        assertTrue(NetworkStatus().label.contains("未知"))
        assertTrue(NetworkStatus(connected = false).label.contains("未连接"))
        val vpn = networkStatus(NetworkCapabilities().apply { shadowOf(this).addTransportType(NetworkCapabilities.TRANSPORT_VPN) })
        assertEquals("传输未知", vpn.transport); assertEquals(true, vpn.vpn)
    }
    @Test @Config(sdk = [26]) fun callbackHandoffAndStopIgnoreLateEventsAndStartDoesNotDuplicate() {
        val observer = AndroidNetworkStatus(RuntimeEnvironment.getApplication())
        val field = AndroidNetworkStatus::class.java.getDeclaredField("callback").apply { isAccessible = true }
        observer.start()
        val first = field.get(observer) as ConnectivityManager.NetworkCallback
        observer.start(); assertSame(first, field.get(observer))
        val old = ShadowNetwork.newInstance(100); val next = ShadowNetwork.newInstance(200)
        first.onAvailable(old); first.onCapabilitiesChanged(old, caps(NetworkCapabilities.TRANSPORT_WIFI))
        assertEquals("Wi-Fi", observer.snapshot.transport)
        first.onAvailable(next); first.onCapabilitiesChanged(next, caps(NetworkCapabilities.TRANSPORT_CELLULAR, vpn = true))
        first.onLost(old); assertEquals("蜂窝", observer.snapshot.transport)
        first.onCapabilitiesChanged(old, caps(NetworkCapabilities.TRANSPORT_WIFI)); assertEquals("蜂窝", observer.snapshot.transport)
        first.onLost(next); assertEquals(false, observer.snapshot.connected)
        observer.stop(); assertNull(field.get(observer))
        first.onAvailable(old); assertEquals(false, observer.snapshot.connected)
        observer.start(); assertNotSame(first, field.get(observer)); observer.stop()
    }
    @Test fun visibleSystemVpnIsDetectedEvenWhenDefaultAppNetworkBypassesIt() {
        val observer = AndroidNetworkStatus(RuntimeEnvironment.getApplication())
        observer.start()
        fun callback(name: String) = AndroidNetworkStatus::class.java.getDeclaredField(name).apply { isAccessible = true }
            .get(observer) as ConnectivityManager.NetworkCallback
        val default = callback("callback"); val vpn = callback("vpnCallback")
        val cellNetwork = ShadowNetwork.newInstance(100); val vpnNetwork = ShadowNetwork.newInstance(200)
        default.onAvailable(cellNetwork)
        default.onCapabilitiesChanged(cellNetwork, caps(NetworkCapabilities.TRANSPORT_CELLULAR))
        vpn.onAvailable(vpnNetwork)
        assertEquals("蜂窝", observer.snapshot.transport); assertEquals(true, observer.snapshot.vpn)
        assertTrue(observer.snapshot.label.contains("VPN已检测"))
        vpn.onLost(vpnNetwork); assertEquals(false, observer.snapshot.vpn)
        observer.stop(); vpn.onAvailable(vpnNetwork); assertEquals(false, observer.snapshot.vpn)
    }
}
