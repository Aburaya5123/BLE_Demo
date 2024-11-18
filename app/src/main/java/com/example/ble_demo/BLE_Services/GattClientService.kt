package com.example.ble_demo.BLE_Services

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.ble_demo.R
import com.example.ble_demo.ui.BleViewModel
import java.util.UUID


class GattClientService : Service() {
    private lateinit var callback: GattClientStateCallback
    private val binder = LocalBinder()
    private var gatt: BluetoothGatt? = null
    private lateinit var clientToServerCharacteristic: BluetoothGattCharacteristic
    private lateinit var serverToClientCharacteristic: BluetoothGattCharacteristic
    private lateinit var myState: BleViewModel.ServiceState
    private val handler = Handler(Looper.getMainLooper())
    private var gattConnectionRetry = 0
    private var connectionEstablished = false
    private var sendingMessage: String = ""

    inner class LocalBinder : Binder() {
        fun getService(): GattClientService = this@GattClientService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onUnbind(intent: Intent): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        sendBroadcast(Intent(GATT_CLIENT_DESTROYED))
        super.onDestroy()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice?, callback: GattClientStateCallback) {
        this.callback = callback

        onMyStateChanged(BleViewModel.ServiceState.STARTING)
        setConnectionTimeout()
        gatt = device?.connectGatt(this, false, gattCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun disconnect() {
        val prevState = myState
        if (prevState == BleViewModel.ServiceState.STOPPING) return
        onMyStateChanged(BleViewModel.ServiceState.STOPPING)
        gatt?.setCharacteristicNotification(serverToClientCharacteristic, false)
        if (prevState == BleViewModel.ServiceState.STARTING) {
            gatt?.disconnect()
        }
        gatt?.close()
        gatt = null
        onMyStateChanged(BleViewModel.ServiceState.STOPPED)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun setConnectionTimeout() {
        handler.postDelayed({
            gatt?.disconnect()
            connectionRetry()
        }, GATT_CONNECTION_TIMEOUT)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            handler.removeCallbacksAndMessages(null)
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gattConnectionRetry = 0
                    Log.i(
                        LOG_TAG,
                        "onConnectionStateChange(): " +
                                "Connection established to GATT server."
                    )
                    gatt?.requestMtu(REQUEST_MTU)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (status != BluetoothGatt.GATT_SUCCESS ||
                        myState == BleViewModel.ServiceState.STARTING
                    ) {
                        connectionRetry()
                    } else {
                        onMyStateChanged(BleViewModel.ServiceState.STOPPED)
                    }
                }

                else -> {
                    Log.w(
                        LOG_TAG,
                        "onConnectionStateChange(): " +
                                "Received an unexpected bluetooth profile state: $newState"
                    )
                    onMyStateChanged(BleViewModel.ServiceState.START_FAILED)
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt?.discoverServices()
            } else {
                Log.w(
                    LOG_TAG,
                    "onMtuChanged(): " +
                            "Failed to get MTU: $status / mtu: $mtu"
                )
                onMyStateChanged(BleViewModel.ServiceState.START_FAILED)
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(
                    UUID.fromString(this@GattClientService.resources.getString(R.string.service_uuid))
                )
                if (service != null) {
                    clientToServerCharacteristic =
                        service.getCharacteristic(
                            UUID.fromString(this@GattClientService.resources.getString(R.string.client_to_server_characteristic_uuid))
                        )
                    serverToClientCharacteristic =
                        service.getCharacteristic(
                            UUID.fromString(this@GattClientService.resources.getString(R.string.server_to_client_characteristic_uuid))
                        )
                    handler.postDelayed({
                        sendTextToGattServer(this@GattClientService.resources.getString(R.string.gatt_connection_token))
                        gatt.setCharacteristicNotification(serverToClientCharacteristic, true)
                    }, FIRST_CONTACT_DELAY)
                } else {
                    onMyStateChanged(BleViewModel.ServiceState.START_FAILED)
                    Log.w(
                        LOG_TAG,
                        "onServicesDiscovered(): " +
                                "Failed to get Gatt service: $status"
                    )
                }
            } else {
                onMyStateChanged(BleViewModel.ServiceState.START_FAILED)
                Log.w(
                    LOG_TAG,
                    "onServicesDiscovered(): " +
                            "Failed to discover Gatt services: $status"
                )
            }
        }

        override fun onServiceChanged(gatt: BluetoothGatt) {
            super.onServiceChanged(gatt)
            Log.w(
                LOG_TAG,
                "onServiceChanged(): " +
                        "Service changed"
            )
            callback.onStateChanged(BleViewModel.ServiceState.STOPPED)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(
                    LOG_TAG,
                    "onCharacteristicWrite(): " +
                            "Characteristic write success: $status"
                )
                if (!connectionEstablished) {
                    connectionEstablished = true
                    onMyStateChanged(BleViewModel.ServiceState.RUNNING)
                } else {
                    callback.onWriteRequestResultIn(WriteRequestResult.SUCCESS, sendingMessage)
                }
            } else {
                Log.w(
                    LOG_TAG,
                    "onCharacteristicWrite(): " +
                            "Characteristic write failed: $status"
                )
                if (!connectionEstablished) {
                    onMyStateChanged(BleViewModel.ServiceState.START_FAILED)
                } else {
                    callback.onWriteRequestResultIn(WriteRequestResult.FAILED, sendingMessage)
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic.uuid == UUID.fromString(this@GattClientService.resources.getString(R.string.server_to_client_characteristic_uuid))) {
                val receivedData = characteristic.value
                callback.onMessageReceived(String(receivedData))
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectionRetry() {
        Log.w(
            LOG_TAG,
            "connectionRetry(): " +
                    "Connection retry count: " +
                    (gattConnectionRetry + 1).toString()
        )
        if (gattConnectionRetry < MAX_RETRY_COUNT) {
            callback.onStateChanged(BleViewModel.ServiceState.UNHEALTHY)
            gattConnectionRetry++
            handler.postDelayed({
                setConnectionTimeout()
                gatt?.connect()
            }, 2000)
        } else {
            onMyStateChanged(BleViewModel.ServiceState.START_FAILED)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendTextToGattServer(text: String) {
        if (!checkSendingDataSize(text)) {
            callback.onWriteRequestResultIn(WriteRequestResult.TOO_LONG, text)
            return
        }
        sendingMessage = text
        val value = text.toByteArray()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt?.writeCharacteristic(
                clientToServerCharacteristic,
                value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            clientToServerCharacteristic.value = value
            clientToServerCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gatt?.writeCharacteristic(clientToServerCharacteristic)
        }
    }

    private fun onMyStateChanged(state: BleViewModel.ServiceState) {
        myState = state
        callback.onStateChanged(state)
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
        private const val MAX_RETRY_COUNT = 5
        private const val GATT_CONNECTION_TIMEOUT = 20000L
        private const val FIRST_CONTACT_DELAY = 2000L
        private const val REQUEST_MTU = 512
        private const val LOG_TAG = "GattClientService"
        const val GATT_CLIENT_DESTROYED = "com.example.ble_demo.GATT_CLIENT_DESTROYED"
    }

    interface GattClientStateCallback {
        fun onStateChanged(state: BleViewModel.ServiceState)
        fun onWriteRequestResultIn(result: WriteRequestResult, message: String)
        fun onMessageReceived(message: String)
    }

    enum class WriteRequestResult {
        SUCCESS,
        FAILED,
        TOO_LONG,
    }
}