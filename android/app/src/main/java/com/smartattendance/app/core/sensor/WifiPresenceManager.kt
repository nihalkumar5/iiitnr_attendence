package com.smartattendance.app.core.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

data class ConnectedWifiSnapshot(
    val isConnected: Boolean,
    val ssid: String? = null,
    val bssid: String? = null,
    val rssi: Int? = null,
    val frequencyMhz: Int? = null,
    val gatewayIp: String? = null,
    val isRogueHotspot: Boolean = false
)

data class ScannedWifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val isSecure: Boolean
)

class WifiPresenceManager(private val context: Context) {

    private val tag = "WifiPresenceManager"
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    @Volatile
    private var callbackWifiInfo: WifiInfo? = null

    init {
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                connectivityManager?.registerNetworkCallback(
                    request,
                    object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                            val info = capabilities.transportInfo as? WifiInfo
                            if (info != null) {
                                callbackWifiInfo = info
                                Log.d(tag, "Real-time NetworkCallback unredacted SSID=${info.ssid}, BSSID=${info.bssid}")
                            }
                        }

                        override fun onLost(network: Network) {
                            callbackWifiInfo = null
                        }
                    }
                )
            } else {
                connectivityManager?.registerNetworkCallback(
                    request,
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                            val info = capabilities.transportInfo as? WifiInfo
                            if (info != null) {
                                callbackWifiInfo = info
                            }
                        }

                        override fun onLost(network: Network) {
                            callbackWifiInfo = null
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not register NetworkCallback: ${e.message}")
        }
    }

    fun getGatewayIp(): String? {
        try {
            val activeNetwork = connectivityManager?.activeNetwork ?: return null
            val linkProps = connectivityManager?.getLinkProperties(activeNetwork) ?: return null
            for (route in linkProps.routes) {
                val gateway = route.gateway?.hostAddress
                if (!gateway.isNullOrBlank() && gateway != "0.0.0.0") {
                    return gateway
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Anti-Evil Twin / Anti-Rogue Hotspot Guard:
     * Detects if device is connected to a mobile phone hotspot (Android/iOS) masquerading as campus Wi-Fi.
     */
    fun isRogueMobileHotspot(gateway: String?, bssid: String?): Boolean {
        // 1. Universal phone hotspot DHCP gateways:
        // Android Hotspot: 192.168.43.1
        // iOS Hotspot: 172.20.10.1
        // Windows Hotspot: 192.168.137.1
        if (gateway == "192.168.43.1" || gateway == "172.20.10.1" || gateway == "192.168.137.1") {
            return true
        }

        // 2. Randomized Locally Administered Address (LAA) bit check on BSSID:
        // Mobile chipsets creating hotspots randomize MAC with second hex char in [2, 6, A, E].
        if (bssid != null && bssid.length >= 2) {
            val secondChar = bssid[1].lowercaseChar()
            if (secondChar == '2' || secondChar == '6' || secondChar == 'a' || secondChar == 'e') {
                if (gateway != null && (gateway.startsWith("192.168.") || gateway.startsWith("172."))) {
                    return true
                }
            }
        }
        return false
    }

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

        val transportWifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            capabilities.transportInfo as? WifiInfo
        } else null

        @Suppress("DEPRECATION")
        val legacyWifiInfo = wifiManager.connectionInfo

        val wifiInfo: WifiInfo? = callbackWifiInfo ?: transportWifiInfo ?: legacyWifiInfo

        if (wifiInfo == null) {
            return ConnectedWifiSnapshot(isConnected = true, ssid = "Unknown Wi-Fi")
        }

        val rawSsid = listOfNotNull(callbackWifiInfo?.ssid, transportWifiInfo?.ssid, legacyWifiInfo?.ssid)
            .firstOrNull { it != "<unknown ssid>" && it.isNotBlank() }
            ?.replace("\"", "")?.trim()

        val rawBssid = listOfNotNull(callbackWifiInfo?.bssid, transportWifiInfo?.bssid, legacyWifiInfo?.bssid)
            .firstOrNull { it != "02:00:00:00:00:00" && it != "<none>" && it.isNotBlank() }

        val isSanitizedBssid = rawBssid != null && rawBssid != "02:00:00:00:00:00"

        Log.d(tag, "Real Wi-Fi Snapshot: SSID=$rawSsid, BSSID=$rawBssid, RSSI=${wifiInfo.rssi} dBm")

        val cleanSsid = if (rawSsid != null && rawSsid != "<unknown ssid>" && rawSsid.isNotEmpty()) {
            rawSsid
        } else {
            "Unknown Wi-Fi"
        }

        val gateway = getGatewayIp()
        val isRogue = isRogueMobileHotspot(gateway, rawBssid)

        if (isRogue) {
            Log.w(tag, "ANTI-PROXY ALERT: Rogue mobile hotspot detected! Gateway=$gateway, BSSID=$rawBssid")
        }

        return ConnectedWifiSnapshot(
            isConnected = true,
            ssid = cleanSsid,
            bssid = if (isSanitizedBssid) rawBssid else null,
            rssi = if (wifiInfo.rssi != -127) wifiInfo.rssi else -50,
            frequencyMhz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) wifiInfo.frequency else null,
            gatewayIp = gateway,
            isRogueHotspot = isRogue
        )
    }

    /**
     * Checks whether currently connected Wi-Fi BSSID or SSID matches target classroom AP.
     */
    fun matchesClassroomAp(authorizedSsids: List<String>, targetBssids: List<String> = emptyList()): Boolean {
        val snapshot = getCurrentWifiSnapshot()
        if (!snapshot.isConnected) return false

        val currentSsid = snapshot.ssid?.replace("\"", "")?.trim() ?: return false

        // Check SSID match
        for (auth in authorizedSsids) {
            if (auth.equals(currentSsid, ignoreCase = true)) {
                return true
            }
        }

        // Check BSSID match if available
        val currentBssid = snapshot.bssid
        if (currentBssid != null) {
            for (target in targetBssids) {
                if (target.equals(currentBssid, ignoreCase = true)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Scans and returns nearby available Wi-Fi networks in the room.
     * Deduplicates multiple BSSIDs for the same SSID, prioritizing highest RSSI.
     */
    @SuppressLint("MissingPermission")
    fun getNearbyWifiNetworks(): List<ScannedWifiNetwork> {
        if (wifiManager == null) return emptyList()

        try {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
        } catch (e: Exception) {
            Log.w(tag, "Wi-Fi scan request throttled: ${e.message}")
        }

        val rawResults: List<ScanResult>? = try {
            @Suppress("DEPRECATION")
            wifiManager.scanResults
        } catch (e: Exception) {
            Log.w(tag, "Could not fetch scanResults: ${e.message}")
            null
        }

        if (rawResults.isNullOrEmpty()) {
            return emptyList()
        }

        return rawResults
            .filter { !it.SSID.isNullOrBlank() }
            .groupBy { it.SSID }
            .map { (ssid, list) ->
                val best = list.maxByOrNull { it.level } ?: list.first()
                val isSecure = best.capabilities != null &&
                    (best.capabilities.contains("WPA", ignoreCase = true) ||
                     best.capabilities.contains("WEP", ignoreCase = true) ||
                     best.capabilities.contains("RSN", ignoreCase = true))

                ScannedWifiNetwork(
                    ssid = ssid,
                    bssid = best.BSSID ?: "",
                    rssi = best.level,
                    frequencyMhz = best.frequency,
                    isSecure = isSecure
                )
            }
            .sortedByDescending { it.rssi }
    }
}
