package com.example.ble_demo.BLE_Services

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.ble_demo.R
import java.util.UUID
import com.example.ble_demo.ui.BleViewModel.ServiceState


class AdvertisingService : Service() {
    private val binder = LocalBinder()
    private lateinit var callback: AdvertisingServiceCallback
    private lateinit var parameters: AdvertisingSetParameters
    private var advertiseCallback: AdvertisingSetCallback? = null
    private var currentAdvertisingSet: AdvertisingSet? = null

    inner class LocalBinder : Binder() {
        fun getService(): AdvertisingService = this@AdvertisingService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    override fun onUnbind(intent: Intent): Boolean {
        stopAdvertising()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        sendBroadcast(Intent(ADVERTISING_DESTROYED))
        super.onDestroy()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun startAdvertising(callback: AdvertisingServiceCallback) {
        this.callback = callback
        callback.onAdvertisingStateChanged(ServiceState.STARTING)
        val advertiser = (this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter
            .getBluetoothLeAdvertiser()

        /**
         * Legacy Fallback
         * val data = this.resources.getString(R.string.advertising_data).toByteArray()
         * if (!checkAdvertisingDataLength(data)){
         *     throw IllegalArgumentException()
         * }
         * parameters = (AdvertisingSetParameters.Builder())
         *     .setLegacyMode(true)
         *     .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
         *     .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
         *      .build()
         * sendingData = (AdvertiseData.Builder())
         *               .addServiceUuid(ParcelUuid(UUID.fromString(this.resources.getString(R.string.service_uuid))))
         *               .addServiceData(
         *                   ParcelUuid(UUID.fromString(this.resources.getString(R.string.service_uuid))),
         *                   data,
         *               )
         *               .build()
         */

        parameters = AdvertisingSetParameters.Builder()
            .setLegacyMode(false)
            .setConnectable(true)
            .setInterval(AdvertisingSetParameters.INTERVAL_MEDIUM)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
            .setPrimaryPhy(BluetoothDevice.PHY_LE_1M)
            .setSecondaryPhy(BluetoothDevice.PHY_LE_CODED)
            .build()
        val data = this.resources.getString(R.string.advertising_data).toByteArray()
        if (!checkAdvertisingDataLength(data)) {
            throw IllegalArgumentException()
        }
        val sendingData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(UUID.fromString(this.resources.getString(R.string.service_uuid))))
            .addServiceData(
                ParcelUuid(UUID.fromString(this.resources.getString(R.string.service_uuid))),
                data
            )
            .build()
        advertiseCallback = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(
                advertisingSet: AdvertisingSet?,
                txPower: Int,
                status: Int
            ) {
                super.onAdvertisingSetStarted(advertisingSet, txPower, status)
                when (status) {
                    ADVERTISE_SUCCESS -> {
                        Log.i(
                            LOG_TAG,
                            "onAdvertisingSetStarted(): txPower:$txPower , status: $status"
                        )
                        currentAdvertisingSet = advertisingSet
                        callback.onAdvertisingStateChanged(ServiceState.RUNNING)
                    }

                    else -> {
                        Log.i(
                            LOG_TAG,
                            "onAdvertisingSetStarted(): txPower:$txPower , status: $status"
                        )
                        callback.onAdvertisingStateChanged(ServiceState.START_FAILED)
                    }
                }
            }

            override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet) {
                super.onAdvertisingSetStopped(advertisingSet)
                Log.i(
                    LOG_TAG,
                    "onAdvertisingSetStopped()"
                )
                callback.onAdvertisingStateChanged(ServiceState.UNHEALTHY)
            }
        }
        advertiser.startAdvertisingSet(
            parameters,
            sendingData,
            null,
            null,
            null,
            advertiseCallback
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun stopAdvertising() {
        val advertiser = (this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter
            .getBluetoothLeAdvertiser()
        advertiser.stopAdvertisingSet(advertiseCallback)
        advertiseCallback = null
        callback.onAdvertisingStateChanged(ServiceState.STOPPED)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun changeAdvertisingData(data: String) {
        val sendingData = data.toByteArray()
        if (!checkAdvertisingDataLength(sendingData)) {
            return
        }
        currentAdvertisingSet?.setAdvertisingData(
            AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(UUID.fromString(this.resources.getString(R.string.service_uuid))))
                .addServiceData(
                    ParcelUuid(UUID.fromString(this.resources.getString(R.string.service_uuid))),
                    sendingData
                )
                .build()
        )
    }

    private fun checkAdvertisingDataLength(data: ByteArray): Boolean {
        val adapter = (this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter
        if (adapter.getLeMaximumAdvertisingDataLength() <= data.size) {
            Log.e(
                LOG_TAG,
                "checkAdvertisingDataLength(): " +
                        "AdvertisingService data is too long and cannot be sent."
            )
            return false
        }
        return true
    }

    interface AdvertisingServiceCallback {
        fun onAdvertisingStateChanged(stat: ServiceState)
    }

    companion object {
        private const val LOG_TAG = "AdvertisingService"
        const val ADVERTISING_DESTROYED = "com.example.ble_demo.ADVERTISING_DESTROYED"
    }
}