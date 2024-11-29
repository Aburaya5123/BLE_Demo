package com.example.ble_demo.BLE_Services

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.ble_demo.R
import com.example.ble_demo.ui.BleViewModel
import java.util.UUID


/**
 * アプリの実装時にはこの機能は不要です
 */
class GattServerService : Service() {
    private val binder = LocalBinder()
    private lateinit var callback: GattServerMessageCallback
    private var gattServer: BluetoothGattServer? = null

    private lateinit var serverToClientCharacteristic: BluetoothGattCharacteristic

    inner class LocalBinder : Binder() {
        fun getService(): GattServerService = this@GattServerService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onUnbind(intent: Intent): Boolean {
        stopServer()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        sendBroadcast(Intent(GATT_SERVER_DESTROYED))
        super.onDestroy()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startServer(callback: GattServerMessageCallback) {
        this.callback = callback
        callback.onStateChanged(BleViewModel.ServiceState.STARTING)

        val bluetoothManager = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        gattServer = bluetoothManager.openGattServer(this, gattServerCallback)
        val service = BluetoothGattService(
            UUID.fromString(this.resources.getString(R.string.service_uuid)),
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        val characteristicC2S = BluetoothGattCharacteristic(
            UUID.fromString(this.resources.getString(R.string.client_to_server_characteristic_uuid)),
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        serverToClientCharacteristic = BluetoothGattCharacteristic(
            UUID.fromString(this.resources.getString(R.string.server_to_client_characteristic_uuid)),
            BluetoothGattCharacteristic.PROPERTY_READ
                    or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(characteristicC2S)
        service.addCharacteristic(serverToClientCharacteristic)
        gattServer?.addService(service)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun stopServer() {
        gattServer?.clearServices()
        gattServer?.close()
        gattServer = null
        callback.onStateChanged(BleViewModel.ServiceState.STOPPED)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendDataToClient(device: BluetoothDevice, message: String): Boolean? {
        if (!checkSendingDataSize(message)) return false
        serverToClientCharacteristic.value = message.toByteArray()
        val result =
            gattServer?.notifyCharacteristicChanged(device, serverToClientCharacteristic, false)
        return result
    }

    private val gattServerCallback: BluetoothGattServerCallback =
        object : BluetoothGattServerCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(
                device: BluetoothDevice?,
                status: Int,
                newState: Int
            ) {
                super.onConnectionStateChange(device, status, newState)
                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> {
                        Log.i(
                            LOG_TAG,
                            "onConnectionStateChange(): STATE_CONNECTED \n" +
                                    "device ${device?.name} / ${device?.address}"
                        )
                        callback.onClientConnected(device)
                    }

                    BluetoothGatt.STATE_DISCONNECTED -> {
                        Log.i(
                            LOG_TAG,
                            "onConnectionStateChange(): STATE_DISCONNECTED \n" +
                                    "device ${device?.name} / ${device?.address}"
                        )
                        callback.onClientDisconnected(device)
                    }
                }
            }

            override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
                super.onServiceAdded(status, service)
                Log.i(
                    LOG_TAG,
                    "onServiceAdded(): Gatt service started."
                )
                callback.onStateChanged(BleViewModel.ServiceState.RUNNING)
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                super.onCharacteristicWriteRequest(
                    device,
                    requestId,
                    characteristic,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value
                )
                if (characteristic.uuid != UUID.fromString(
                        this@GattServerService.resources.getString(
                            R.string.client_to_server_characteristic_uuid
                        )
                    )
                ) return
                val message = String(value)
                callback.onMessageReceived(message, device)
                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null
                    )
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
                if (characteristic.uuid != UUID.fromString(
                        this@GattServerService.resources.getString(
                            R.string.server_to_client_characteristic_uuid
                        )
                    )
                ) return

                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    serverToClientCharacteristic.value
                )
            }

            override fun onNotificationSent(device: BluetoothDevice, status: Int) {
                Log.d(LOG_TAG, "onNotificationSent(): Notification sent. Status: $status")

            }
        }

    private fun checkSendingDataSize(text: String): Boolean {
        if (text.toByteArray().size > 300) {
            Log.w(
                LOG_TAG,
                "checkSendingDataSize(): " +
                        "Message is too long and cannot be sent."
            )
            return false
        }
        return true
    }

    companion object {
        private const val LOG_TAG = "GattServerService"
        const val GATT_SERVER_DESTROYED = "com.example.ble_demo.GATT_SERVER_DESTROYED"
    }

    interface GattServerMessageCallback {
        fun onMessageReceived(message: String, device: BluetoothDevice?)
        fun onClientConnected(device: BluetoothDevice?)
        fun onClientDisconnected(device: BluetoothDevice?)
        fun onStateChanged(state: BleViewModel.ServiceState)
    }
}
