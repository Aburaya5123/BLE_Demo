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


/**
 * BLEの広告を行うクラスです
 * 参考: https://qiita.com/QiitaD/items/5c313076b7b99823ce1a
 *
 * 広告の開始: startAdvertising(callback: AdvertisingServiceCallback)
 * 広告データの変更: changeAdvertisingData(data: String)
 * 広告の停止: サービスの破棄と広告の停止が同時に行われる想定で書いているため、外部からの呼び出しはありません
 */
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
        // このクラスが破棄される前に必ず広告を終了するようにしてください
        stopAdvertising()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        // このクラスが破棄されるタイミングでブロードキャストを行うことで他のクラスへ通知を行っています
        sendBroadcast(Intent(ADVERTISING_DESTROYED))
        super.onDestroy()
    }

    /**
     * 広告の開始
     * 引数として、このクラスで定義しているインターフェイスの実装を受け取っています
     */
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
            // 以下のパラメータは出力に関係するので、適宜調整してください
            .setInterval(AdvertisingSetParameters.INTERVAL_MEDIUM)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
            // ここまで
            .setPrimaryPhy(BluetoothDevice.PHY_LE_1M)
            .setSecondaryPhy(BluetoothDevice.PHY_LE_CODED)
            .build()
        // ここでユーザー毎に一意となるIDを埋め込むことができます
        val data = this.resources.getString(R.string.advertising_data).toByteArray()
        // 広告に埋め込むデータ長が固定である場合は、この確認は不要です
        if (!checkAdvertisingDataLength(data)) {
            throw IllegalArgumentException()
        }
        val sendingData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            // ここでサービスIDを広告に設定します
            .addServiceUuid(ParcelUuid(UUID.fromString(this.resources.getString(R.string.service_uuid))))
            // ここでサービスIDに紐付けられた広告データを設定します
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
                        // 後から広告データを編集する必要がある場合は、ここでAdvertisingSetを保持しておいてください
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

    /**
     * 広告の停止
     * 広告はアプリの起動中に常時実行することを想定しているため、クラスの破棄時にのみ停止処理を行っています
     * 動的に広告のオンオフを切り替えたい場合は、advertiser.stopAdvertisingSet(advertiseCallback) を呼び出してください
     * 詳細はドキュメント参照:
     * https://source.android.com/docs/core/connect/bluetooth/ble_advertising?hl=ja
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun stopAdvertising() {
        val advertiser = (this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter
            .getBluetoothLeAdvertiser()
        advertiser.stopAdvertisingSet(advertiseCallback)
        advertiseCallback = null
        callback.onAdvertisingStateChanged(ServiceState.STOPPED)
    }

    /**
     * 広告データの変更
     * 広告データに含めるデータがユーザーIDのみである場合、この処理は不要になります
     * 引数は変更後の広告データ
     */
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
        // このサービスの実行状態に変動があった際に呼び出されます
        fun onAdvertisingStateChanged(stat: ServiceState)
    }

    companion object {
        private const val LOG_TAG = "AdvertisingService"
        const val ADVERTISING_DESTROYED = "com.example.ble_demo.ADVERTISING_DESTROYED"
    }
}