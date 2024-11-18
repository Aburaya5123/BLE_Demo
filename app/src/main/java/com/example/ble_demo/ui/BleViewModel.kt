package com.example.ble_demo.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.ble_demo.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID


class BleViewModel(application: Application) : AndroidViewModel(application) {
    private val _connectionType = MutableLiveData(ConnectionType.NONE)
    val connectionTypeLiveData: LiveData<ConnectionType> = _connectionType
    val connectionType: ConnectionType
        get() = connectionTypeLiveData.value!!

    private val _advertisingConnection: MutableLiveData<ServiceConnectionState> =
        MutableLiveData(ServiceConnectionState.WAITING)
    val advertisingConnectionLiveData: LiveData<ServiceConnectionState> = _advertisingConnection
    val advertisingConnection: ServiceConnectionState
        get() = advertisingConnectionLiveData.value!!

    private val _scanningConnection: MutableLiveData<ServiceConnectionState> =
        MutableLiveData(ServiceConnectionState.WAITING)
    val scanningConnectionLiveData: LiveData<ServiceConnectionState> = _scanningConnection
    val scanningConnection: ServiceConnectionState
        get() = scanningConnectionLiveData.value!!

    private val _gattServerConnection: MutableLiveData<ServiceConnectionState> =
        MutableLiveData(ServiceConnectionState.WAITING)
    val gattServerConnectionLiveData: LiveData<ServiceConnectionState> = _gattServerConnection
    val gattServerConnection: ServiceConnectionState
        get() = gattServerConnectionLiveData.value!!

    private val _gattClientConnection: MutableLiveData<ServiceConnectionState> =
        MutableLiveData(ServiceConnectionState.WAITING)
    val gattClientConnectionLiveData: LiveData<ServiceConnectionState> = _gattClientConnection
    val gattClientConnection: ServiceConnectionState
        get() = gattClientConnectionLiveData.value!!

    private val _allPermissionsGranted = MutableLiveData(false)
    val allPermissionsGrantedLiveData: LiveData<Boolean> = _allPermissionsGranted

    private val _bluetoothEnabled = MutableLiveData(false)
    val bluetoothEnabledLiveData: LiveData<Boolean> = _bluetoothEnabled

    private val _gpsEnabled = MutableLiveData(false)
    val gpsEnabledLiveData: LiveData<Boolean> = _gpsEnabled

    private val _scanResults = MutableLiveData<List<ScanResult>>(listOf())
    val scanResultsLiveData: LiveData<List<ScanResult>> = _scanResults

    private val _connectingDevice = MutableLiveData<ScanResult>(null)
    val connectingDeviceLiveData: LiveData<ScanResult> = _connectingDevice
    val connectingDevice: ScanResult?
        get() = connectingDeviceLiveData.value

    private val _connectedDevice = MutableLiveData<BluetoothDevice?>(null)
    val connectedDeviceLiveData: LiveData<BluetoothDevice?> = _connectedDevice
    val connectedDevice: BluetoothDevice?
        get() = connectedDeviceLiveData.value

    private val _scanningState = MutableLiveData(false)
    val scanningStateLiveData: LiveData<Boolean> = _scanningState
    val isScanning: Boolean
        get() = scanningStateLiveData.value == true

    private val _gattClientState: MutableLiveData<ServiceState> = MutableLiveData()
    val gattClientStateLiveData: LiveData<ServiceState> = _gattClientState

    private val _receivedMessage: MutableLiveData<String> = MutableLiveData()
    val receivedMessageLiveData: LiveData<String> = _receivedMessage

    private val _inputMessage: MutableLiveData<String> = MutableLiveData("")
    val inputMessageLiveData: LiveData<String> = _inputMessage
    val inputMessage: String
        get() = inputMessageLiveData.value ?: ""

    private val _waitingWriteResponse = MutableLiveData(false)
    val waitingWriteResponseLiveData: LiveData<Boolean> = _waitingWriteResponse

    private var rssiFiltered = true
    val isRssiFiltered: Boolean
        get() = rssiFiltered

    private var uuidFiltered = true
    val isUuidFiltered: Boolean
        get() = uuidFiltered

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()


    fun addMessage(content: String, isFromMe: Boolean) {
        val newMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = content,
            isFromMe = isFromMe,
            timestamp = System.currentTimeMillis()
        )
        _messages.value += newMessage
    }

    fun onConnectionTypeChanged(type: ConnectionType) {
        _connectionType.value = type
    }

    fun onAllPermissionsGranted() {
        _allPermissionsGranted.value = true
    }

    fun resetPermissionGrantedState() {
        _allPermissionsGranted.value = false
    }

    fun onBluetoothEnabled() {
        _bluetoothEnabled.value = true
    }

    fun onGpsEnabled() {
        _gpsEnabled.value = true
    }

    fun resetFunctionEnabledState() {
        _bluetoothEnabled.value = false
        _gpsEnabled.value = false
    }

    val allConditionsMet: Boolean
        get() = allPermissionsGrantedLiveData.value == true &&
                bluetoothEnabledLiveData.value == true &&
                gpsEnabledLiveData.value == true

    fun onServiceConnectionStateChanged(service: ServiceName, state: ServiceConnectionState) {
        when (service) {
            ServiceName.Advertising -> _advertisingConnection.value = state
            ServiceName.Scanning -> _scanningConnection.value = state
            ServiceName.GattServer -> _gattServerConnection.value = state
            ServiceName.GattClient -> _gattClientConnection.value = state
        }
    }

    fun onMessageReceived(message: String) {
        _receivedMessage.value = message
        addMessage(message, false)
    }

    fun onInputMessageChanged(message: String) {
        _inputMessage.value = message
    }

    fun addScanResultList(result: ScanResult) {
        if (_scanResults.value?.none { it.device.address == result.device.address } == true) {
            if (isRssiFiltered && result.rssi < RSSI_STRENGTH_BAR) {
                return
            }
            _scanResults.value = _scanResults.value?.plus(result)
        }
    }

    fun addBatchScanResultsList(results: MutableList<ScanResult>?) {
        for (result in results!!) {
            if (_scanResults.value?.none { it.device.address == result.device.address } == true) {
                if (isRssiFiltered && result.rssi < RSSI_STRENGTH_BAR) {
                    return
                }
                _scanResults.value = _scanResults.value?.plus(result)
            }
        }
    }

    fun resetScanResultList() {
        _scanResults.value = listOf()
    }

    fun onScanResultSelected(result: ScanResult) {
        if (connectionType == ConnectionType.SERVER) {
            Toast.makeText(
                this.getApplication(),
                R.string.already_connected_to_gatt_client,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        when (_gattClientState.value) {
            ServiceState.RUNNING -> {
                Toast.makeText(
                    this.getApplication(),
                    R.string.already_connected_to_gatt_server,
                    Toast.LENGTH_SHORT
                ).show()
                Log.e(
                    LOG_TAG,
                    "onScanResultSelected(): Already connected to Gatt Server."
                )
            }

            ServiceState.STARTING -> {
                Toast.makeText(
                    this.getApplication(),
                    R.string.already_connecting_to_gatt_server,
                    Toast.LENGTH_SHORT
                ).show()
                Log.e(
                    LOG_TAG,
                    "onScanResultSelected(): Already connecting to Gatt Server."
                )
            }

            ServiceState.UNHEALTHY,
            ServiceState.START_FAILED -> {
                Toast.makeText(
                    this.getApplication(),
                    R.string.gatt_client_error,
                    Toast.LENGTH_SHORT
                ).show()
                Log.e(
                    LOG_TAG,
                    "onScanResultSelected(): Can't connect to Gatt Server now."
                )
            }

            else -> {
                if (gattClientConnection == ServiceConnectionState.NOT_CONNECTED ||
                    gattClientConnection == ServiceConnectionState.WAITING
                ) {
                    _connectingDevice.value = result
                }
                Log.i(
                    LOG_TAG,
                    "onScanResultSelected(): " + _gattClientState.value.toString()
                )
            }
        }
    }

    fun onGattClientConnected(connected: Boolean, device: BluetoothDevice) {
        if (connected) {
            _connectedDevice.value = device
        } else {
            _connectedDevice.value = null
        }
    }

    fun onScanningStatusChanged(state: Boolean) {
        _scanningState.value = state
    }

    fun onGattClientStateChanged(state: ServiceState) {
        _gattClientState.value = state
    }

    fun onWaitingWriteResponse(waiting: Boolean) {
        _waitingWriteResponse.value = waiting
    }

    fun onRssiFilterChanged(on: Boolean) {
        rssiFiltered = on
    }

    fun onUuidFilterChanged(on: Boolean) {
        uuidFiltered = on
    }

    companion object {
        private const val RSSI_STRENGTH_BAR = -60
        private const val LOG_TAG = "BleViewModel"
    }

    enum class ServiceState {
        WAITING,
        STARTING,
        START_FAILED,
        RUNNING,
        UNHEALTHY,
        STOPPING,
        STOPPED,
    }

    enum class ServiceConnectionState {
        NOT_CONNECTED,
        CONNECTING,
        CONNECTED,
        UNHEALTHY,
        DISCONNECTING,
        WAITING
    }

    enum class ServiceName {
        Advertising,
        Scanning,
        GattServer,
        GattClient
    }

    enum class ConnectionType {
        SERVER,
        CLIENT,
        NONE
    }
}