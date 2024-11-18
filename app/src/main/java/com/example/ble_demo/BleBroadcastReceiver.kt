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

class BleBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != null) {
            when (intent.action) {
                LocationManager.PROVIDERS_CHANGED_ACTION -> {
                    if (!context.isGpsEnabled) {
                        (context as MainActivity).enableGps()
                    }
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    if (!context.isBluetoothEnabled) {
                        (context as MainActivity).enableBluetooth()
                    }
                }

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

