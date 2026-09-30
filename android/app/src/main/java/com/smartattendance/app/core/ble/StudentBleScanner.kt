package com.smartattendance.app.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class DetectedObservation(
    val token: String,
    val rssi: Int,
    val timestampMs: Long = System.currentTimeMillis()
)

data class StudentScannerState(
    val isScanning: Boolean = false,
    val lastDetectedToken: String? = null,
    val lastRssi: Int? = null,
    val totalObservationsCount: Int = 0,
    val lastSeenTimestampMs: Long? = null,
    val errorMessage: String? = null
)

class StudentBleScanner(private val context: Context) {

    private val tag = "StudentBleScanner"
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var scanner: BluetoothLeScanner? = null

    private val _state = MutableStateFlow(StudentScannerState())
    val state: StateFlow<StudentScannerState> = _state.asStateFlow()

    private val _observations = MutableSharedFlow<DetectedObservation>(extraBufferCapacity = 64)
    val observations: SharedFlow<DetectedObservation> = _observations.asSharedFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val scanRecord = result.scanRecord ?: return

            // Proximity threshold check: discard weak spillover signals from adjacent corridors
            val rssi = result.rssi
            if (rssi < BleConstants.MAX_REJECT_RSSI_THRESHOLD) {
                Log.v(tag, "Discarded weak signal: $rssi dBm")
                return
            }

            // Extract the 8-byte ephemeral token payload from service data
            val serviceData = scanRecord.getServiceData(BleConstants.SERVICE_PARCEL_UUID)
            if (serviceData != null && serviceData.isNotEmpty()) {
                val tokenHex = EphemeralTokenGenerator.byteArrayToToken(serviceData)
                val observation = DetectedObservation(
                    token = tokenHex,
                    rssi = rssi,
                    timestampMs = System.currentTimeMillis()
                )

                _observations.tryEmit(observation)

                _state.value = _state.value.copy(
                    lastDetectedToken = tokenHex,
                    lastRssi = rssi,
                    totalObservationsCount = _state.value.totalObservationsCount + 1,
                    lastSeenTimestampMs = observation.timestampMs,
                    errorMessage = null
                )

                Log.d(tag, "Teacher BLE Signal Detected: Token=$tokenHex, RSSI=$rssi dBm")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val message = "BLE Scan failed with code: $errorCode"
            Log.e(tag, message)
            _state.value = _state.value.copy(isScanning = false, errorMessage = message)
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _state.value = _state.value.copy(errorMessage = "Bluetooth is disabled")
            return
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            _state.value = _state.value.copy(errorMessage = "Bluetooth LE Scanner unavailable")
            return
        }

        // Hardware-level scan filter matching the Smart Attendance Service UUID
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(BleConstants.SERVICE_PARCEL_UUID)
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setReportDelay(0)
            .build()

        try {
            scanner?.startScan(filters, settings, scanCallback)
            _state.value = _state.value.copy(isScanning = true, errorMessage = null)
            Log.d(tag, "Started passive BLE scan for Smart Attendance Service")
        } catch (e: SecurityException) {
            Log.e(tag, "Missing BLUETOOTH_SCAN permission", e)
            _state.value = _state.value.copy(errorMessage = "Missing Bluetooth Scan permission")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start BLE scan", e)
            _state.value = _state.value.copy(errorMessage = e.localizedMessage)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(tag, "Stop scan error: ${e.message}")
        }
        _state.value = _state.value.copy(isScanning = false)
        Log.d(tag, "Stopped BLE scan")
    }
}
