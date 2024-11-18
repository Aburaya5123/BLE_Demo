package com.example.ble_demo

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelUuid
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import io.morfly.compose.bottomsheet.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import io.morfly.compose.bottomsheet.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.ble_demo.BLE_Services.AdvertisingService
import com.example.ble_demo.BLE_Services.AdvertisingService.Companion.ADVERTISING_DESTROYED
import com.example.ble_demo.BLE_Services.GattClientService
import com.example.ble_demo.BLE_Services.GattClientService.Companion.GATT_CLIENT_DESTROYED
import com.example.ble_demo.BLE_Services.GattServerService
import com.example.ble_demo.BLE_Services.GattServerService.Companion.GATT_SERVER_DESTROYED
import com.example.ble_demo.BLE_Services.ScanningService
import com.example.ble_demo.BLE_Services.ScanningService.Companion.SCANNING_DESTROYED
import com.example.ble_demo.BleBroadcastReceiver.Companion.isBluetoothEnabled
import com.example.ble_demo.BleBroadcastReceiver.Companion.isGpsEnabled
import com.example.ble_demo.ui.BleViewModel
import com.example.ble_demo.ui.BleViewModel.ServiceConnectionState
import com.example.ble_demo.ui.BleViewModel.ServiceName
import com.example.ble_demo.ui.ChatMessage
import com.example.ble_demo.ui.theme.BLE_DemoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.morfly.compose.bottomsheet.material3.rememberBottomSheetState


class MainActivity : FragmentActivity() {
    private lateinit var advertisingServiceBinder: AdvertisingService.LocalBinder
    private lateinit var scanningServiceBinder: ScanningService.LocalBinder
    private lateinit var gattServerServiceBinder: GattServerService.LocalBinder
    private lateinit var gattClientServiceBinder: GattClientService.LocalBinder

    private var advertiser: AdvertisingService? = null
    private var scanner: ScanningService? = null
    private var gattServer: GattServerService? = null
    private var gattClient: GattClientService? = null

    private val viewModel: BleViewModel by viewModels()
    private lateinit var broadcastReceiver: BleBroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BLE_DemoTheme {
                FullScreenBottomSheetExample()
            }
        }
        checkBleAvailability()
        setAutoRebindingService()
        viewModel.allPermissionsGrantedLiveData.observe(this) { granted ->
            if (granted && viewModel.allConditionsMet) {
                onAllConditionsMet()
            }
        }
        viewModel.gpsEnabledLiveData.observe(this) { granted ->
            if (granted && viewModel.allConditionsMet) {
                onAllConditionsMet()
            }
        }
        viewModel.bluetoothEnabledLiveData.observe(this) { granted ->
            if (granted && viewModel.allConditionsMet) {
                onAllConditionsMet()
            }
        }
        viewModel.connectingDeviceLiveData.observe(this) { result ->
            if (result != null) {
                connectToGattServer()
            }
        }

        broadcastReceiver = BleBroadcastReceiver()
        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(ADVERTISING_DESTROYED)
            addAction(SCANNING_DESTROYED)
            addAction(GATT_SERVER_DESTROYED)
            addAction(GATT_CLIENT_DESTROYED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                this, broadcastReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.apply {
            resetPermissionGrantedState()
            resetFunctionEnabledState()
        }
        requestPermissions()
        if (!this.isGpsEnabled) {
            enableGps()
        } else {
            viewModel.onGpsEnabled()
        }
        if (!this.isBluetoothEnabled) {
            enableBluetooth()
        } else {
            viewModel.onBluetoothEnabled()
        }
    }

    override fun onDestroy() {
        unbindAllServices()
        unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    private fun bindMyService(service: ServiceName, serviceConnection: ServiceConnectionState) {
        if (serviceConnection != ServiceConnectionState.NOT_CONNECTED &&
            serviceConnection != ServiceConnectionState.WAITING
        ) {
            Log.w(
                LOG_TAG,
                "startService(): Service is already running: $service"
            )
            return
        }
        when (service) {
            ServiceName.Advertising -> {
                val advertisingIntent = Intent(this@MainActivity, AdvertisingService::class.java)
                bindService(advertisingIntent, advertisingServiceConnection, BIND_AUTO_CREATE)
            }

            ServiceName.Scanning -> {
                val scanningIntent = Intent(this@MainActivity, ScanningService::class.java)
                bindService(scanningIntent, scanningServiceConnection, BIND_AUTO_CREATE)
            }

            ServiceName.GattServer -> {
                val gattServerIntent = Intent(this@MainActivity, GattServerService::class.java)
                bindService(gattServerIntent, gattServerServiceConnection, BIND_AUTO_CREATE)
            }

            ServiceName.GattClient -> {
                val gattClientIntent = Intent(this@MainActivity, GattClientService::class.java)
                bindService(gattClientIntent, gattClientServiceConnection, BIND_AUTO_CREATE)
            }
        }
        viewModel.onServiceConnectionStateChanged(service, ServiceConnectionState.CONNECTING)
    }

    private fun unbindMyService(service: ServiceName, serviceConnection: ServiceConnectionState) {
        if (serviceConnection == ServiceConnectionState.NOT_CONNECTED) {
            Log.w(
                LOG_TAG,
                "unbindService(): Service is not running: $service"
            )
            return
        } else if (serviceConnection == ServiceConnectionState.DISCONNECTING) {
            Log.w(
                LOG_TAG,
                "unbindService(): Service is already disconnecting: $service"
            )
            return
        }
        viewModel.onServiceConnectionStateChanged(service, ServiceConnectionState.DISCONNECTING)
        when (service) {
            ServiceName.Advertising -> {
                unbindService(advertisingServiceConnection)
                advertiser = null
            }

            ServiceName.Scanning -> {
                unbindService(scanningServiceConnection)
                scanner = null
            }

            ServiceName.GattServer -> {
                unbindService(gattServerServiceConnection)
                gattServer = null
            }

            ServiceName.GattClient -> {
                unbindService(gattClientServiceConnection)
                gattClient = null
            }
        }
    }

    private fun unbindAllServices() {
        unbindMyService(ServiceName.Advertising, viewModel.advertisingConnection)
        unbindMyService(ServiceName.Scanning, viewModel.scanningConnection)
        unbindMyService(ServiceName.GattServer, viewModel.gattServerConnection)
        unbindMyService(ServiceName.GattClient, viewModel.gattClientConnection)
    }

    private fun setAutoRebindingService() {
        viewModel.advertisingConnectionLiveData.observe(this) { conn ->
            if (conn == ServiceConnectionState.NOT_CONNECTED &&
                viewModel.gattClientConnection != ServiceConnectionState.CONNECTING
            ) {
                bindMyService(ServiceName.Advertising, viewModel.advertisingConnection)
            }
        }
        viewModel.scanningConnectionLiveData.observe(this) { conn ->
            if (conn == ServiceConnectionState.NOT_CONNECTED) {
                bindMyService(ServiceName.Scanning, viewModel.scanningConnection)
            }
        }
        viewModel.gattServerConnectionLiveData.observe(this) { conn ->
            if (conn == ServiceConnectionState.NOT_CONNECTED) {
                bindMyService(ServiceName.GattServer, viewModel.gattServerConnection)
            }
        }
    }

    private fun onAllConditionsMet() {
        bindMyService(ServiceName.Advertising, viewModel.advertisingConnection)
        bindMyService(ServiceName.Scanning, viewModel.scanningConnection)
        bindMyService(ServiceName.GattServer, viewModel.gattServerConnection)
    }

    private fun checkBleAvailability() {
        val adapter = (this.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) ||
            !adapter.isLe2MPhySupported() ||
            !adapter.isLeExtendedAdvertisingSupported()
        ) {
            Toast.makeText(this, R.string.ble_is_not_supported, Toast.LENGTH_SHORT).show()
            if (!this.isFinishing) finish()
            return
        }
        if (!adapter.isMultipleAdvertisementSupported() || !adapter.isOffloadedFilteringSupported() ||
            !adapter.isOffloadedScanBatchingSupported()
        ) {
            Toast.makeText(this, R.string.ble_is_not_supported, Toast.LENGTH_SHORT).show()
            if (!this.isFinishing) finish()
            return
        }
    }

    fun onServiceDestroyed(serviceName: ServiceName) {
        Log.i(LOG_TAG, "onServiceDestroyed(): $serviceName")
        lifecycleScope.launch {
            delay(2000)
            viewModel.onServiceConnectionStateChanged(
                serviceName,
                ServiceConnectionState.NOT_CONNECTED
            )
            if (serviceName == ServiceName.GattClient) {
                viewModel.onGattClientStateChanged(BleViewModel.ServiceState.WAITING)
            }
        }
    }

    private fun connectToGattServer() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        viewModel.onGattClientStateChanged(BleViewModel.ServiceState.STARTING)
        viewModel.onServiceConnectionStateChanged(
            ServiceName.GattClient,
            ServiceConnectionState.CONNECTING
        )
        val gattIntent = Intent(
            this@MainActivity,
            GattClientService::class.java
        )
        if (scanner != null && viewModel.isScanning) {
            scanner!!.stopScan()
        }
        bindService(gattIntent, gattClientServiceConnection, BIND_AUTO_CREATE)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendMessageToGattServer() {
        if (gattClient == null) return

        viewModel.onWaitingWriteResponse(true)
        gattClient!!.sendTextToGattServer(viewModel.inputMessage)
        viewModel.onInputMessageChanged("")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendMessageToGattClient() {
        if (gattServer == null) return

        viewModel.connectedDevice?.let {
            if (gattServer!!.sendDataToClient(
                    it,
                    viewModel.inputMessage
                ) == true
            ) {
                viewModel.addMessage(viewModel.inputMessage, true)
            } else {
                toastOnUiThread(this.resources.getString(R.string.gatt_server_writing_failed))
            }
        } ?: {
            toastOnUiThread(this.resources.getString(R.string.gatt_server_client_not_found))
        }
        viewModel.onInputMessageChanged("")
    }

    fun enableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startForResult.launch(enableBtIntent)
    }

    fun enableGps() {
        val enableGPS = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startForResult.launch(enableGPS)
    }

    private fun requestPermissions() {
        requestPermissionLauncher.launch(
            REQUIRED_PERMISSIONS
        )
    }

    private val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_CANCELED) {
                when (result.data?.action) {
                    BluetoothAdapter.ACTION_REQUEST_ENABLE -> {
                        Toast.makeText(this, R.string.bluetooth_is_not_enabled, Toast.LENGTH_SHORT)
                            .show()
                    }

                    Settings.ACTION_LOCATION_SOURCE_SETTINGS -> {
                        Toast.makeText(this, R.string.gps_is_not_enabled, Toast.LENGTH_SHORT).show()
                    }
                }
                if (!this.isFinishing) finish()
            } else if (result.resultCode == RESULT_OK) {
                when (result.data?.action) {
                    BluetoothAdapter.ACTION_REQUEST_ENABLE -> {
                        viewModel.onBluetoothEnabled()
                    }

                    Settings.ACTION_LOCATION_SOURCE_SETTINGS -> {
                        viewModel.onGpsEnabled()
                    }
                }
            }
        }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val deniedPermissions = granted.filterNot { it.value }.map { it.key }
        if (deniedPermissions.isNotEmpty()) {
            val deniedPermissionsString = deniedPermissions.joinToString(", ")
            Log.e(
                LOG_TAG,
                "requestPermissionLauncher: " +
                        "The following permissions were not granted: $deniedPermissionsString"
            )
            Toast.makeText(
                this,
                this.resources.getString(R.string.permission_request_denied)
                        + deniedPermissionsString,
                Toast.LENGTH_LONG
            ).show()
            if (!this.isFinishing) finish()
        } else {
            viewModel.onAllPermissionsGranted()
        }
    }

    private fun toastOnUiThread(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private val advertisingServiceConnection: ServiceConnection = object : ServiceConnection {
        @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            advertisingServiceBinder = service as AdvertisingService.LocalBinder
            advertiser = advertisingServiceBinder.getService()
            viewModel.onServiceConnectionStateChanged(
                ServiceName.Advertising,
                ServiceConnectionState.CONNECTED
            )
            advertiser!!.startAdvertising(
                object : AdvertisingService.AdvertisingServiceCallback {
                    override fun onAdvertisingStateChanged(stat: BleViewModel.ServiceState) {
                        when (stat) {
                            BleViewModel.ServiceState.RUNNING -> {
                                toastOnUiThread(
                                    this@MainActivity.resources.getString(R.string.advertising_start)
                                )
                            }

                            BleViewModel.ServiceState.START_FAILED -> {
                                toastOnUiThread(
                                    this@MainActivity.resources.getString(R.string.advertising_failed)
                                )
                                unbindMyService(
                                    ServiceName.Advertising,
                                    viewModel.advertisingConnection
                                )
                            }

                            BleViewModel.ServiceState.STOPPED -> {
                                toastOnUiThread(
                                    this@MainActivity.resources.getString(R.string.advertising_stop)
                                )
                                unbindMyService(
                                    ServiceName.Advertising,
                                    viewModel.advertisingConnection
                                )
                            }

                            else -> {
                                Log.i(LOG_TAG, "onAdvertisingStateChanged(): $stat")
                            }
                        }
                    }
                })
        }

        override fun onServiceDisconnected(name: ComponentName) {
            viewModel.onServiceConnectionStateChanged(
                ServiceName.Advertising,
                ServiceConnectionState.UNHEALTHY
            )
            advertiser = null
        }
    }

    private val scanningServiceConnection: ServiceConnection = object : ServiceConnection {
        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            scanningServiceBinder = service as ScanningService.LocalBinder
            scanner = scanningServiceBinder.getService()
            viewModel.onScanningStatusChanged(false)
            viewModel.onServiceConnectionStateChanged(
                ServiceName.Scanning,
                ServiceConnectionState.CONNECTED
            )
            scanner!!.startScanningService(object : ScanningService.ScanningServiceCallback {
                override fun onScanResultReceived(result: ScanResult) {
                    viewModel.addScanResultList(result)
                }

                override fun onBatchScanResultReceived(results: MutableList<ScanResult>?) {
                    viewModel.addBatchScanResultsList(results)
                }

                override fun onScanningStatusChanged(state: Boolean) {
                    viewModel.onScanningStatusChanged(state)
                    if (state) {
                        toastOnUiThread(
                            this@MainActivity.resources.getString(R.string.scanning_devices)
                        )
                    }
                }
            })
        }

        override fun onServiceDisconnected(name: ComponentName) {
            viewModel.onServiceConnectionStateChanged(
                ServiceName.Scanning,
                ServiceConnectionState.UNHEALTHY
            )
            scanner = null
        }
    }

    private val gattClientServiceConnection: ServiceConnection = object : ServiceConnection {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            gattClientServiceBinder = service as GattClientService.LocalBinder
            gattClient = gattClientServiceBinder.getService()

            viewModel.apply {
                onServiceConnectionStateChanged(
                    ServiceName.GattClient,
                    ServiceConnectionState.CONNECTED
                )
                onWaitingWriteResponse(false)
            }
            gattClient!!.connect(
                viewModel.connectingDevice?.device,
                object : GattClientService.GattClientStateCallback {
                    override fun onStateChanged(state: BleViewModel.ServiceState) {
                        runOnUiThread {
                            viewModel.onGattClientStateChanged(state)
                            if (state == BleViewModel.ServiceState.RUNNING) {
                                viewModel.onConnectionTypeChanged(BleViewModel.ConnectionType.CLIENT)
                            } else {
                                viewModel.onConnectionTypeChanged(BleViewModel.ConnectionType.NONE)
                            }
                        }
                        when (state) {
                            BleViewModel.ServiceState.RUNNING -> {
                                toastOnUiThread(
                                    this@MainActivity.resources.getString(R.string.gatt_client_connected)
                                )
                            }

                            BleViewModel.ServiceState.START_FAILED -> {
                                toastOnUiThread(
                                    this@MainActivity.resources.getString(R.string.gatt_client_connection_failure)
                                )
                                unbindMyService(
                                    ServiceName.GattClient,
                                    viewModel.gattClientConnection
                                )
                            }

                            BleViewModel.ServiceState.STOPPED -> {
                                toastOnUiThread(
                                    this@MainActivity.resources.getString(R.string.gatt_client_disconnected)
                                )
                                unbindMyService(
                                    ServiceName.GattClient,
                                    viewModel.gattClientConnection
                                )
                            }

                            BleViewModel.ServiceState.UNHEALTHY -> {
                                toastOnUiThread(
                                    this@MainActivity.resources.getString(R.string.gatt_client_connection_retry)
                                )
                            }

                            else -> {
                                Log.i(LOG_TAG, "onStateChanged(): GattClient $state")
                            }
                        }
                    }

                    override fun onWriteRequestResultIn(
                        result: GattClientService.WriteRequestResult,
                        message: String
                    ) {
                        when (result) {
                            GattClientService.WriteRequestResult.SUCCESS -> {
                                runOnUiThread {
                                    viewModel.addMessage(message, true)
                                }
                            }

                            GattClientService.WriteRequestResult.FAILED -> {
                                toastOnUiThread(
                                    this@MainActivity.resources.getString(R.string.gatt_client_writing_failed)
                                )
                            }

                            GattClientService.WriteRequestResult.TOO_LONG -> {
                                toastOnUiThread(
                                    this@MainActivity.resources.getString(R.string.gatt_client_writing_toolong)
                                )
                            }
                        }
                        runOnUiThread {
                            viewModel.onWaitingWriteResponse(false)
                        }
                    }

                    override fun onMessageReceived(message: String) {
                        Log.i(LOG_TAG, "onMessageReceived(): GattClient $message")
                        runOnUiThread {
                            viewModel.addMessage(message, false)
                        }
                    }
                })
        }

        override fun onServiceDisconnected(name: ComponentName) {
            viewModel.onServiceConnectionStateChanged(
                ServiceName.GattClient,
                ServiceConnectionState.UNHEALTHY
            )
            gattClient = null
        }
    }

    private val gattServerServiceConnection: ServiceConnection = object : ServiceConnection {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            gattServerServiceBinder = service as GattServerService.LocalBinder
            gattServer = gattServerServiceBinder.getService()
            viewModel.onServiceConnectionStateChanged(
                ServiceName.GattServer,
                ServiceConnectionState.CONNECTED
            )
            gattServer!!.startServer(
                object : GattServerService.GattServerMessageCallback {
                    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    override fun onMessageReceived(message: String, device: BluetoothDevice?) {
                        if (message == this@MainActivity.resources.getString(R.string.gatt_connection_token)
                            && device != null
                        ) {
                            runOnUiThread {
                                viewModel.apply {
                                    onGattClientConnected(true, device)
                                    onConnectionTypeChanged(BleViewModel.ConnectionType.SERVER)
                                }
                            }
                            toastOnUiThread(
                                this@MainActivity.resources.getString(R.string.gatt_client_connected) +
                                        ": device ${device.name} / ${device.address}"
                            )
                        } else {
                            runOnUiThread {
                                viewModel.onMessageReceived(message)
                            }
                        }
                    }

                    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    override fun onClientConnected(device: BluetoothDevice?) {
                        // これは全てのBluetooth周辺機器の接続で呼び出されるので、あまり使えない
                    }

                    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    override fun onClientDisconnected(device: BluetoothDevice?) {
                        if (device == null || device != viewModel.connectedDevice) return
                        toastOnUiThread(
                            this@MainActivity.resources.getString(R.string.gatt_client_disconnected) +
                                    ": device ${device.name} / ${device.address}"
                        )
                        if (viewModel.connectionType == BleViewModel.ConnectionType.SERVER) {
                            runOnUiThread {
                                viewModel.apply {
                                    onGattClientConnected(false, device)
                                    onConnectionTypeChanged(BleViewModel.ConnectionType.NONE)
                                }
                            }
                        }
                    }

                    override fun onStateChanged(state: BleViewModel.ServiceState) {
                        when (state) {
                            BleViewModel.ServiceState.RUNNING -> {
                                toastOnUiThread(
                                    this@MainActivity.resources.getString(R.string.gatt_server_up)
                                )
                            }

                            BleViewModel.ServiceState.STOPPED -> {
                                toastOnUiThread(
                                    this@MainActivity.resources.getString(R.string.gatt_server_down)
                                )
                                unbindMyService(
                                    ServiceName.GattServer,
                                    viewModel.gattServerConnection
                                )
                            }

                            else -> {
                                Log.i(LOG_TAG, "onStateChanged(): $state")
                            }
                        }
                    }
                })
        }

        override fun onServiceDisconnected(name: ComponentName) {
            viewModel.onServiceConnectionStateChanged(
                ServiceName.GattServer,
                ServiceConnectionState.UNHEALTHY
            )
            gattServer = null
        }
    }

    companion object {
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
        private const val LOG_TAG = "MainActivity"
    }


    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    fun FullScreenBottomSheetExample() {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            defineValues = {
                SheetValue.Hidden at height(100.dp)
                SheetValue.PartiallyExpanded at offset(percent = 60)
                SheetValue.Expanded at contentHeight
            }
        )
        val scaffoldState = rememberBottomSheetScaffoldState(sheetState)
        val messages by viewModel.messages.collectAsState()
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetContent = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MessageInputField()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.DarkGray)
                    ) {
                        ChatScreen(messages)
                    }
                }

            },
            sheetContainerColor = Color.DarkGray,

            ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.LightGray)
                    .padding(innerPadding),
            ) {
                ScanButton()
                SendMessageButton()
                DisconnectGattButton()
                ScanResultsList()
                RssiFilterCheck()
                UuidFilterCheck()
            }
        }
    }

    @Composable
    fun ScanButton() {
        val isScanning = viewModel.scanningStateLiveData.observeAsState()
        val connectType = viewModel.connectionTypeLiveData.observeAsState()

        Button(
            onClick = {
                if (isScanning.value == false &&
                    connectType.value == BleViewModel.ConnectionType.NONE
                ) {
                    if (scanner != null &&
                        ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.resetScanResultList()
                        if (viewModel.isUuidFiltered) {
                            scanner!!.scan(
                                ScanningService.ScanFilterOption.SERVICE_FILTERED_SCAN,
                                ScanningService.ScanModeOption.INTERVAL_SCAN
                            )
                        } else {
                            scanner!!.scan(
                                ScanningService.ScanFilterOption.NON_FILTERED_SCAN,
                                ScanningService.ScanModeOption.INTERVAL_SCAN
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .size(width = 140.dp, height = 48.dp)
                .absoluteOffset(200.dp, 90.dp)
        ) {
            if (isScanning.value == true ||
                connectType.value != BleViewModel.ConnectionType.NONE
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Text("Scan", color = Color.White)
            }
        }
    }

    @Composable
    fun SendMessageButton() {
        val connection = viewModel.gattClientStateLiveData.observeAsState()
        val writing = viewModel.waitingWriteResponseLiveData.observeAsState()
        val connectType = viewModel.connectionTypeLiveData.observeAsState()
        Button(
            onClick = {
                if (connection.value == BleViewModel.ServiceState.RUNNING &&
                    writing.value == false && connectType.value == BleViewModel.ConnectionType.CLIENT
                ) {
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        sendMessageToGattServer()
                    }
                } else if (connectType.value == BleViewModel.ConnectionType.SERVER) {
                    sendMessageToGattClient()
                } else {
                    toastOnUiThread(
                        this.resources.getString(R.string.connection_not_established)
                    )
                }
                viewModel.onInputMessageChanged("")
            },
            modifier = Modifier
                .size(width = 140.dp, height = 48.dp)
                .wrapContentSize(Alignment.Center)
                .absoluteOffset(200.dp, 140.dp)
        ) {
            if ((connection.value != BleViewModel.ServiceState.RUNNING ||
                        writing.value == true) && connectType.value != BleViewModel.ConnectionType.SERVER
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Text("Send", color = Color.White)
            }
        }
    }

    @Composable
    fun DisconnectGattButton() {
        val connection = viewModel.gattClientConnectionLiveData.observeAsState()
        val connectType = viewModel.connectionTypeLiveData.observeAsState()
        Button(
            onClick = {
                if (connection.value == ServiceConnectionState.CONNECTED &&
                    connectType.value == BleViewModel.ConnectionType.CLIENT
                ) {
                    unbindMyService(ServiceName.GattClient, viewModel.gattClientConnection)
                }
            },
            modifier = Modifier
                .size(width = 140.dp, height = 48.dp)
                .wrapContentSize(Alignment.Center)
                .absoluteOffset(40.dp, 140.dp)
        ) {
            if (connection.value != ServiceConnectionState.CONNECTED ||
                connectType.value != BleViewModel.ConnectionType.CLIENT
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Text("Disconnect", color = Color.White)
            }
        }
    }

    @Composable
    fun ScanResultsList() {
        val items by viewModel.scanResultsLiveData.observeAsState(initial = emptyList())
        LazyColumn(
            modifier = Modifier.absoluteOffset(10.dp, 200.dp)
        ) {
            items(items) { item: ScanResult ->
                var shownText = item.device.address + " / " + item.rssi
                if (item.scanRecord?.serviceData != null) {
                    shownText += " / " +
                            item.scanRecord?.serviceData!![ParcelUuid.fromString(
                                (LocalContext.current as MainActivity).resources.getString(R.string.service_uuid)
                            )]?.let {
                                String(
                                    it
                                )
                            }
                    shownText += " / " + item.scanRecord?.serviceUuids.toString()
                }
                ClickableText(
                    text = AnnotatedString(shownText),
                    onClick = {
                        viewModel.onScanResultSelected(item)
                    },
                    style = TextStyle(color = Color.Black, fontSize = 25.sp)
                )
            }
        }
    }

    @Composable
    fun RssiFilterCheck() {
        var checked by remember { mutableStateOf(true) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.absoluteOffset(170.dp, 10.dp)
        ) {
            Text(
                "Filter Rssi", color = Color.Black
            )
            Checkbox(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    viewModel.onRssiFilterChanged(it)
                },
                colors = CheckboxDefaults.colors(uncheckedColor = Color.Black)
            )
        }
    }

    @Composable
    fun UuidFilterCheck() {
        var checked by remember { mutableStateOf(true) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.absoluteOffset(170.dp, 40.dp)
        ) {
            Text(
                "Filter ServiceUUID", color = Color.Black
            )
            Checkbox(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    viewModel.onUuidFilterChanged(it)
                },
                colors = CheckboxDefaults.colors(uncheckedColor = Color.Black)
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MessageInputField() {
        val textState = viewModel.inputMessageLiveData.observeAsState()
        val connection = viewModel.gattClientStateLiveData.observeAsState()
        val writing = viewModel.waitingWriteResponseLiveData.observeAsState()
        val connectType = viewModel.connectionTypeLiveData.observeAsState()
        textState.value?.let { it ->
            TextField(
                value = it,
                onValueChange = {
                    viewModel.onInputMessageChanged(it)
                },
                label = { Text("送信メッセージを入力") },
                modifier = Modifier
                    .fillMaxWidth()
                    .absoluteOffset()
                    .background(Color.DarkGray)
                    .onKeyEvent {
                        if (it.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                            if (connection.value == BleViewModel.ServiceState.RUNNING &&
                                writing.value == false && connectType.value == BleViewModel.ConnectionType.CLIENT
                            ) {
                                if (ActivityCompat.checkSelfPermission(
                                        this,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    sendMessageToGattServer()
                                }
                            } else if (connectType.value == BleViewModel.ConnectionType.SERVER) {
                                sendMessageToGattClient()
                            } else {
                                toastOnUiThread(
                                    this.resources.getString(R.string.connection_not_established)
                                )
                            }
                        }
                        viewModel.onInputMessageChanged("")
                        true
                    },
                colors =
                TextFieldDefaults.textFieldColors(
                    containerColor = Color.DarkGray
                ),
                trailingIcon = {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "clear text",
                        modifier = Modifier
                            .clickable {
                                viewModel.onInputMessageChanged("")
                            }
                    )
                }
            )
        }
    }

    @Composable
    fun ChatMessageItem(message: ChatMessage) {
        val alignment = if (message.isFromMe) Alignment.TopEnd else Alignment.TopStart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = alignment
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (message.isFromMe) Color.White else Color.LightGray
            ) {
                Text(
                    text = message.content,
                    color = if (message.isFromMe) Color.Black else Color.Black,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    @Composable
    fun ChatScreen(messages: List<ChatMessage>) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            reverseLayout = false
        ) {
            items(messages) { message ->
                ChatMessageItem(message)
            }
        }
    }
}
