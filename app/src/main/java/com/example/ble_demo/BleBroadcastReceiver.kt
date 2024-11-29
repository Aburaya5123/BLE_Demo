package com.example.ble_demo

import android.app.Activity.BLUETOOTH_SERVICE
import android.app.Activity.LOCATION_SERVICE
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import com.example.ble_demo.BLE_Services.AdvertisingService.Companion.ADVERTISING_DESTROYED
import com.example.ble_demo.BLE_Services.GattClientService.Companion.GATT_CLIENT_DESTROYED
import com.example.ble_demo.BLE_Services.GattServerService.Companion.GATT_SERVER_DESTROYED
import com.example.ble_demo.BLE_Services.ScanningService.Companion.SCANNING_DESTROYED
import com.example.ble_demo.ui.BleViewModel


/**
 * sendBroadcast() で送信されたブロードキャストを受け取るクラスです
 * ブロードキャストの必要性として、アプリ起動中にBluetoothやGPSがユーザー操作により無効化された場合、
 * ブロードキャストで検知し、ユーザーに有効化を促すことができます
 *
 * 他にも自ら定義したIntentを送信することで、クラス間を跨いだ情報共有が行えます
 */
class BleBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != null) {
            when (intent.action) {
                // GPSの有効化/無効化が行われた際
                LocationManager.PROVIDERS_CHANGED_ACTION -> {
                    if (!context.isGpsEnabled) {
                        (context as MainActivity).enableGps()
                    }
                }
                // Bluetoothの有効化/無効化が行われた際
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    if (!context.isBluetoothEnabled) {
                        (context as MainActivity).enableBluetooth()
                    }
                }
                // それぞれのサービスが破棄された際
                ADVERTISING_DESTROYED -> {
                    (context as MainActivity).onServiceDestroyed(BleViewModel.ServiceName.Advertising)
                }

                SCANNING_DESTROYED -> {
                    (context as MainActivity).onServiceDestroyed(BleViewModel.ServiceName.Scanning)
                }

                GATT_SERVER_DESTROYED -> {
                    (context as MainActivity).onServiceDestroyed(BleViewModel.ServiceName.GattServer)
                }

                GATT_CLIENT_DESTROYED -> {
                    (context as MainActivity).onServiceDestroyed(BleViewModel.ServiceName.GattClient)
                }
            }
        }
    }

    companion object {
        val Context?.isBluetoothEnabled: Boolean
            get() {
                val bluetoothAdapter =
                    (this?.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
                return bluetoothAdapter.isEnabled
            }

        val Context?.isGpsEnabled: Boolean
            get() {
                val locationManager = this?.getSystemService(LOCATION_SERVICE) as LocationManager
                return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }
    }
}

