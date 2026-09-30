package com.smartattendance.app.core.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

data class ConnectedWifiSnapshot(
    val isConnected: Boolean,
    val ssid: String? = null,
    val bssid: String? = null,
    val rssi: Int? = null,
    val frequencyMhz: Int? = null
)

class WifiPresenceManager(private val context: Context) {

    private val tag = "WifiPresenceManager"
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    @SuppressLint("MissingPermission")
    fun getCurrentWifiSnapshot(): ConnectedWifiSnapshot {
        if (connectivityManager == null || wifiManager == null) {
            return ConnectedWifiSnapshot(isConnected = false)
        }

        val activeNetwork = connectivityManager.activeNetwork ?: return ConnectedWifiSnapshot(isConnected = false)
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return ConnectedWifiSnapshot(isConnected = false)

        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return ConnectedWifiSnapshot(isConnected = false)
        }

        // On modern Android (API 31+), WifiInfo is obtained from NetworkCapabilities
        val wifiInfo: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            capabilities.transportInfo as? WifiInfo
        } else {
            wifiManager.connectionInfo
        }

        if (wifiInfo == null) {
            return ConnectedWifiSnapshot(isConnected = true)
        }

        val rawSsid = wifiInfo.ssid?.replace("\"", "")
        val rawBssid = wifiInfo.bssid

        // Android returns 02:00:00:00:00:00 or "<unknown ssid>" if location permissions are not granted
        val isSanitizedBssid = rawBssid != null && rawBssid != "02:00:00:00:00:00"

        Log.d(tag, "Wi-Fi Snapshot: SSID=$rawSsid, BSSID=$rawBssid, RSSI=${wifiInfo.rssi} dBm")

        return ConnectedWifiSnapshot(
            isConnected = true,
            ssid = if (rawSsid != "<unknown ssid>") rawSsid else null,
            bssid = if (isSanitizedBssid) rawBssid else null,
            rssi = wifiInfo.rssi,
            frequencyMhz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) wifiInfo.frequency else null
        )
    }

    /**
     * Checks whether currently connected Wi-Fi BSSID matches the target classroom AP hardware MAC.
     */
    fun matchesClassroomAp(targetPrimaryBssid: String, secondaryBssids: List<String> = emptyList()): Boolean {
        val snapshot = getCurrentWifiSnapshot()
        val currentBssid = snapshot.bssid ?: return false

        if (currentBssid.equals(targetPrimaryBssid, ignoreCase = true)) {
            return true
        }

        for (sec in secondaryBssids) {
            if (currentBssid.equals(sec, ignoreCase = true)) {
                return true
            }
        }

        return false
    }
}
