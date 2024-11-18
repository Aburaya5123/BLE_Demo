package com.example.ble_demo.BLE_Services

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.ble_demo.R
import java.util.UUID


class ScanningService : Service() {
    private lateinit var callback: ScanningServiceCallback
    private val binder = LocalBinder()
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private var filters: MutableList<ScanFilter> = ArrayList()
    private var dummyFilters: MutableList<ScanFilter> = ArrayList()
    private lateinit var scanSettings: ScanSettings
    private val handler = android.os.Handler(Looper.getMainLooper())
    private var isScanning = false
    private var scanMode: ScanModeOption = ScanModeOption.INTERVAL_SCAN

    inner class LocalBinder : Binder() {
        fun getService(): ScanningService = this@ScanningService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onUnbind(intent: Intent): Boolean {
        stopScan()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        sendBroadcast(Intent(SCANNING_DESTROYED))
        super.onDestroy()
    }

    fun startScanningService(callback: ScanningServiceCallback) {
        this.callback = callback

        val adapter = (this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        bluetoothLeScanner = adapter.bluetoothLeScanner
        val filter = ScanFilter.Builder()
            .setServiceUuid(
                ParcelUuid(
                    UUID.fromString(this.resources.getString(R.string.service_uuid))
                )
            )
            .build()
        val dummyFilter = ScanFilter.Builder().build()
        filters.add(filter)
        dummyFilters.add(dummyFilter)
        scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setScanMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT)
            .setLegacy(false)
            .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            .build()
    }

    private fun onScanningStateChanged(state: Boolean) {
        isScanning = state
        callback.onScanningStatusChanged(state)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun scan(filterMode: ScanFilterOption, scanMode: ScanModeOption) {
        if (scanMode != this.scanMode) {
            this.scanMode = scanMode
            if (isScanning) {
                stopScan()
            }
        }
        if (isScanning) return
        onScanningStateChanged(true)

        if (this.scanMode == ScanModeOption.INTERVAL_SCAN) {
            handler.postDelayed({
                stopScan()
            }, SCAN_PERIOD)
        }
        when (filterMode) {
            ScanFilterOption.NON_FILTERED_SCAN -> {
                bluetoothLeScanner.startScan(dummyFilters, scanSettings, scanCallback)
            }

            ScanFilterOption.SERVICE_FILTERED_SCAN -> {
                bluetoothLeScanner.startScan(filters, scanSettings, scanCallback)
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        bluetoothLeScanner.stopScan(scanCallback)
        onScanningStateChanged(false)
        handler.removeCallbacksAndMessages(null)
    }

    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            callback.onScanResultReceived(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            callback.onBatchScanResultReceived(results)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(
                LOG_TAG,
                "onScanFailed(): Scan failed: $errorCode"
            )
        }
    }

    companion object {
        private const val SCAN_PERIOD: Long = 5000
        private const val LOG_TAG = "ScanningService"
        const val SCANNING_DESTROYED = "com.example.ble_demo.SCANNING_DESTROYED"
    }

    interface ScanningServiceCallback {
        fun onScanResultReceived(result: ScanResult)
        fun onBatchScanResultReceived(results: MutableList<ScanResult>?)
        fun onScanningStatusChanged(state: Boolean)
    }

    enum class ScanFilterOption {
        NON_FILTERED_SCAN,
        SERVICE_FILTERED_SCAN,
    }

    enum class ScanModeOption {
        CONTINUOUS_SCAN,
        INTERVAL_SCAN,
    }
}