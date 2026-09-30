package com.smartattendance.app.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TeacherAdvertisingState(
    val isAdvertising: Boolean = false,
    val sessionId: String? = null,
    val currentToken: String? = null,
    val error: String? = null
)

class TeacherBleAdvertiser(private val context: Context) {

    private val tag = "TeacherBleAdvertiser"
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var advertiser: BluetoothLeAdvertiser? = null

    private val _state = MutableStateFlow(TeacherAdvertisingState())
    val state: StateFlow<TeacherAdvertisingState> = _state.asStateFlow()

    private var rotationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(tag, "BLE Advertisement successfully broadcasted")
        }

        override fun onStartFailure(errorCode: Int) {
            val message = "BLE Advertising failed with error code: $errorCode"
            Log.e(tag, message)
            _state.value = _state.value.copy(isAdvertising = false, error = message)
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising(sessionId: String, sessionSecret: String) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _state.value = TeacherAdvertisingState(error = "Bluetooth is disabled or unavailable")
            return
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            _state.value = TeacherAdvertisingState(error = "Device does not support BLE Peripheral Advertising")
            return
        }

        rotationJob?.cancel()
        rotationJob = scope.launch {
            var lastToken: String? = null
            while (isActive) {
                val newToken = EphemeralTokenGenerator.generateToken(
                    sessionId = sessionId,
                    sessionSecret = sessionSecret
                )

                if (newToken != lastToken) {
                    lastToken = newToken
                    restartAdvertisementWithToken(newToken)
                    _state.value = TeacherAdvertisingState(
                        isAdvertising = true,
                        sessionId = sessionId,
                        currentToken = newToken,
                        error = null
                    )
                    Log.d(tag, "Updated rotating BLE token: $newToken")
                }

                // Check every 2 seconds for boundary transition
                delay(2000L)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun restartAdvertisementWithToken(tokenHex: String) {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.w(tag, "Stop advertising before restart: ${e.message}")
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0) // Run until explicitly stopped
            .build()

        val tokenBytes = EphemeralTokenGenerator.tokenToByteArray(tokenHex)

        val data = AdvertiseData.Builder()
            .addServiceUuid(BleConstants.SERVICE_PARCEL_UUID)
            .addServiceData(BleConstants.SERVICE_PARCEL_UUID, tokenBytes)
            .setIncludeTxPowerLevel(false)
            .setIncludeDeviceName(false)
            .build()

        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(tag, "SecurityException: BLUETOOTH_ADVERTISE permission required", e)
            _state.value = _state.value.copy(error = "Missing Bluetooth Advertise permission")
        } catch (e: Exception) {
            Log.e(tag, "Exception starting advertisement", e)
            _state.value = _state.value.copy(error = e.localizedMessage)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        rotationJob?.cancel()
        rotationJob = null
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.w(tag, "Error stopping advertisement: ${e.message}")
        }
        _state.value = TeacherAdvertisingState(isAdvertising = false)
        Log.d(tag, "BLE Advertisement stopped")
    }
}
