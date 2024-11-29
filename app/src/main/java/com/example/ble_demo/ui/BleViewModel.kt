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
    // Gatt未接続/Gattサーバー/Gattクライアントのいずれか
    private val _connectionType = MutableLiveData(ConnectionType.NONE)
    val connectionTypeLiveData: LiveData<ConnectionType> = _connectionType
    val connectionType: ConnectionType
        get() = connectionTypeLiveData.value!!

    // それぞれのserviceの接続状況(bind)
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

    // すべての権限の取得に成功すればtrue
    private val _allPermissionsGranted = MutableLiveData(false)
    val allPermissionsGrantedLiveData: LiveData<Boolean> = _allPermissionsGranted

    private val _bluetoothEnabled = MutableLiveData(false)
    val bluetoothEnabledLiveData: LiveData<Boolean> = _bluetoothEnabled

    private val _gpsEnabled = MutableLiveData(false)
    val gpsEnabledLiveData: LiveData<Boolean> = _gpsEnabled

    // Scan結果の一覧
    private val _scanResults = MutableLiveData<List<ScanResult>>(listOf())
    val scanResultsLiveData: LiveData<List<ScanResult>> = _scanResults

    // 接続先Gattサーバーのデバイス情報
    private val _connectingDevice = MutableLiveData<ScanResult>(null)
    val connectingDeviceLiveData: LiveData<ScanResult> = _connectingDevice
    val connectingDevice: ScanResult?
        get() = connectingDeviceLiveData.value

    // 接続されているGattクライアントのデバイス情報
    private val _connectedDevice = MutableLiveData<BluetoothDevice?>(null)
    val connectedDeviceLiveData: LiveData<BluetoothDevice?> = _connectedDevice
    val connectedDevice: BluetoothDevice?
        get() = connectedDeviceLiveData.value

    // 現在のスキャン実行状態、実行中であればtrue
    private val _scanningState = MutableLiveData(false)
    val scanningStateLiveData: LiveData<Boolean> = _scanningState
    val isScanning: Boolean
        get() = scanningStateLiveData.value == true

    // Gattクライアントの接続状態（Service内部処理）
    private val _gattClientState: MutableLiveData<ServiceState> = MutableLiveData()
    val gattClientStateLiveData: LiveData<ServiceState> = _gattClientState

    // Gattの接続相手から受け取ったテキストメッセージ
    private val _receivedMessage: MutableLiveData<String> = MutableLiveData()
    val receivedMessageLiveData: LiveData<String> = _receivedMessage

    // TextFieldに入力したテキストメッセージ
    private val _inputMessage: MutableLiveData<String> = MutableLiveData("")
    val inputMessageLiveData: LiveData<String> = _inputMessage
    val inputMessage: String
        get() = inputMessageLiveData.value ?: ""

    // Gattサーバーに対してWriteリクエストを行った際に、レスポンス待ちの間はtrueとなる
    private val _waitingWriteResponse = MutableLiveData(false)
    val waitingWriteResponseLiveData: LiveData<Boolean> = _waitingWriteResponse

    // Rssi（電波強度)のフィルターオン/オフ
    private var rssiFiltered = true
    val isRssiFiltered: Boolean
        get() = rssiFiltered

    // サービスUUIDのフィルターオン/オフ
    private var uuidFiltered = true
    val isUuidFiltered: Boolean
        get() = uuidFiltered

    // チャットメッセージ一覧
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()


    /**
     * チャットメッセージを追加
     */
    fun addMessage(content: String, isFromMe: Boolean) {
        val newMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = content,
            isFromMe = isFromMe,
            timestamp = System.currentTimeMillis()
        )
        _messages.value += newMessage
    }

    /**
     * Gattへの接続形態が変動した際に呼び出される
     *
     * @param type 接続タイプ: 未接続/クライアント/サーバー
     */
    fun onConnectionTypeChanged(type: ConnectionType) {
        _connectionType.value = type
    }

    /**
     * bluetooth/GPS/全ての権限取得
     * 上記の条件がそろった場合に呼び出され、LiveDataの値を書き換える
     */
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

    /**
     * サービスの接続状態が変動した際に呼び出され、LiveDataの値を書き換える
     *
     * @param service サービスの名前
     * @param state 接続状態
     */
    fun onServiceConnectionStateChanged(service: ServiceName, state: ServiceConnectionState) {
        when (service) {
            ServiceName.Advertising -> _advertisingConnection.value = state
            ServiceName.Scanning -> _scanningConnection.value = state
            ServiceName.GattServer -> _gattServerConnection.value = state
            ServiceName.GattClient -> _gattClientConnection.value = state
        }
    }

    /**
     * Gattクライアント/Gattサーバーからメッセージを受け取った際に呼び出され。
     * LiveDataを書き換えたのち、メッセージログを追加する
     *
     * @param message メッセージ
     */
    fun onMessageReceived(message: String) {
        _receivedMessage.value = message
        addMessage(message, false)
    }

    /**
     * TextFieldの値が変更された際に呼び出され、LiveDataの値を書き換える
     *
     * @param message メッセージ
     */
    fun onInputMessageChanged(message: String) {
        _inputMessage.value = message
    }

    /**
     * スキャン結果を一覧に追加
     *
     * @param result 検出されたデバイス情報
     */
    fun addScanResultList(result: ScanResult) {
        if (_scanResults.value?.none { it.device.address == result.device.address } == true) {
            if (isRssiFiltered && result.rssi < RSSI_STRENGTH_BAR) {
                return
            }
            _scanResults.value = _scanResults.value?.plus(result)
        }
    }

    fun resetScanResultList() {
        _scanResults.value = listOf()
    }

    /**
     * スキャン結果の一覧表が画面上でタップされた際に呼び出され、
     * Gattサーバーへの接続を試みる
     *
     * @param result 選択されたスキャン結果
     */
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

    /**
     * 自らのGattサーバーへGattクライアントが接続された際に呼び出され、
     * 接続のあったデバイスをLiveDataの値に書き込む
     *
     * @param connected 接続時 true / 切断時 false
     * @param device 接続デバイス
     */
    fun onGattClientConnected(connected: Boolean, device: BluetoothDevice) {
        if (connected) {
            _connectedDevice.value = device
        } else {
            _connectedDevice.value = null
        }
    }

    /**
     * スキャンの実行状態が切り替わった際に呼び出され、
     * LiveDataの値を書き換える
     */
    fun onScanningStatusChanged(state: Boolean) {
        _scanningState.value = state
    }

    /**
     * 自らのGattクライアントの接続状況が変更された際に呼び出され、
     * LiveDataの値を書き換える
     */
    fun onGattClientStateChanged(state: ServiceState) {
        _gattClientState.value = state
    }

    /**
     * Gattサーバーに対するWriteリクエストのレスポンス待機状態に変動があった際に呼び出され、
     * LiveDataの値を書き換える
     *
     * @param waiting true:レスポンス待機中 / false:writeリクエスト実行可能
     */
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
        // Rssiフィルターの電波強度
        private const val RSSI_STRENGTH_BAR = -60
        private const val LOG_TAG = "BleViewModel"
    }

    /**
     * service内部処理の実行状態を表す値
     */
    enum class ServiceState {
        WAITING,
        STARTING,
        START_FAILED,
        RUNNING,
        UNHEALTHY,
        STOPPING,
        STOPPED,
    }

    /**
     * serviceのbind状態を表す値
     */
    enum class ServiceConnectionState {
        NOT_CONNECTED,
        CONNECTING,
        CONNECTED,
        UNHEALTHY,
        DISCONNECTING,
        WAITING
    }

    /**
     * サービス名
     */
    enum class ServiceName {
        Advertising,
        Scanning,
        GattServer,
        GattClient
    }

    /**
     * Gattサーバー/Gattクライアント/Gatt接続無し
     * のいずれか
     */
    enum class ConnectionType {
        SERVER,
        CLIENT,
        NONE
    }
}