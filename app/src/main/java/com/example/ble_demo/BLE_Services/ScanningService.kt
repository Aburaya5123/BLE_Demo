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


/**
 * BLEのスキャンを行うクラスです
 * 参考: https://qiita.com/QiitaD/items/5c313076b7b99823ce1a
 *
 * 現在のコードでは、scan() 呼び出し時に SCAN_PERIOD 時間だけスキャンを実行していますが、
 * 本番環境ではスキャンのオンオフ切り替えを行えるようにしてください
 *
 * スキャンの準備: startScanningService(callback: ScanningServiceCallback)
 * スキャンの開始: scan()
 * スキャンの停止: stopScan()
 */
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
        // このクラスが破棄されるタイミングで、必ず、handlerの中身をクリアしてください
        handler.removeCallbacksAndMessages(null)
        // このクラスが破棄されるタイミングでブロードキャストを行うことで他のクラスへ通知を行っています
        sendBroadcast(Intent(SCANNING_DESTROYED))
        super.onDestroy()
    }

    /**
     * スキャンの準備処理です
     * スキャンの実行には下部にある scan() を呼び出してください
     * 引数として、このクラスで定義しているインターフェイスの実装を受け取っています
     */
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
            // 以下のパラメータは検出の頻度/最大件数に関係するので、適宜調整してください
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setScanMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT)
            // ここまで
            .setLegacy(false)
            .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            .build()
    }

    private fun onScanningStateChanged(state: Boolean) {
        isScanning = state
        callback.onScanningStatusChanged(state)
    }

    /**
     * スキャンを実行します
     * 引数として、スキャンの実行時間に関連するパラメータを受け取っています
     * 本番環境ではオンオフの切り替えができるように書き換えてください
     */
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

    /**
     * スキャンを停止します
     * handlerに待機中の処理があるかもしれないので、念のためリセットを行っています
     */
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

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(
                LOG_TAG,
                "onScanFailed(): Scan failed: $errorCode"
            )
        }
    }

    companion object {
        // スキャンを実行する時間(ミリ秒)です
        private const val SCAN_PERIOD: Long = 5000
        private const val LOG_TAG = "ScanningService"
        const val SCANNING_DESTROYED = "com.example.ble_demo.SCANNING_DESTROYED"
    }

    interface ScanningServiceCallback {
        // スキャン結果を受け取った際のコールバック
        fun onScanResultReceived(result: ScanResult)
        // スキャンの実行状況が変動した際のコールバック
        fun onScanningStatusChanged(state: Boolean)
    }

    /**
     * スキャンにフィルターをかけるか否かを指定
     * 本番環境では常にフィルターをかけてください
     */
    enum class ScanFilterOption {
        NON_FILTERED_SCAN,
        SERVICE_FILTERED_SCAN,
    }

    /**
     * 断続的なスキャンか連続的なスキャンかを指定
     * 本番環境ではオンオフを切り替えられるようにしてください
     */
    enum class ScanModeOption {
        CONTINUOUS_SCAN,
        INTERVAL_SCAN,
    }
}