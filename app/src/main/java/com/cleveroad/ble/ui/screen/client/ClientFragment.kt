package com.cleveroad.ble.ui.screen.client

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context.BLUETOOTH_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.view.View
import com.cleveroad.ble.R
import com.cleveroad.ble.SCAN_PERIOD
import com.cleveroad.ble.SERVICE_UUID
import com.cleveroad.ble.extensions.hide
import com.cleveroad.ble.extensions.setClickListeners
import com.cleveroad.ble.extensions.show
import com.cleveroad.ble.ui.base.BaseFragment
import com.cleveroad.ble.utils.BluetoothUtils
import com.cleveroad.ble.utils.LOG
import com.cleveroad.ble.utils.StringUtils
import com.cleveroad.bootstrap.kotlin_permissionrequest.PermissionRequest
import com.cleveroad.bootstrap.kotlin_permissionrequest.PermissionResult
import kotlinx.android.synthetic.main.fragment_client.*
import kotlinx.android.synthetic.main.view_log.*
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx
import java.util.*


class ClientFragment : BaseFragment(), View.OnClickListener {

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_FINE_LOCATION = 2

        fun newInstance() = ClientFragment().apply {
            arguments = Bundle()
        }
    }

    override val layoutId = R.layout.fragment_client

    private var scanHandler: Handler? = null
    private var logHandler: Handler? = null

    private var devices: MutableList<BluetoothDevice>? = null
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothDevice: BluetoothDevice? = null

    private val permissionRequest = PermissionRequest()
    private var isScanning = false
    private var isConnected = false
    private var isEchoInitialized = false

    private var scanResults: MutableMap<String, BluetoothDevice>? = null
    private var scanCallback: ScanCallback? = null

    private var gatt: BluetoothGatt? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scanHandler = Handler()
        logHandler = Handler(Looper.getMainLooper())
        devices = ArrayList()

        bluetoothManager = act.run { getSystemService(BLUETOOTH_SERVICE) as BluetoothManager? }
        bluetoothAdapter = bluetoothManager?.adapter

        setClickListeners(bStopScanning, bStartScanning, bDisconnect, bSendMessage,
                bClearLog, bConnectGattServer)
        setDeviceInfo()
    }

    override fun onResume() {
        super.onResume()
        if (!act.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            logError("No LE Support.")
            backPressed()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionRequest.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.bStartScanning -> startScan()
            R.id.bStopScanning -> stopScan()
            R.id.bSendMessage -> sendMessage()
            R.id.bDisconnect -> disconnectGattServer()
            R.id.bClearLog -> clearLogs()
            R.id.bConnectGattServer -> connectDevice(bluetoothDevice)
        }
    }

    private fun setDeviceInfo() {
        tvDeviceInfo.text = bluetoothAdapter?.run { "Device Info \nName: $name \nAddress: $address" }
    }

    private fun startScan() {
        if (!hasPermissions() || isScanning) return
        hasLocationPermissions()
    }

    private fun scan() {
        disconnectGattServer()

        llServerContainer.hide()

        scanResults = HashMap()
        scanCallback = BleScanCallback(scanResults)

        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()

        val filters = ArrayList<ScanFilter>().apply { add(scanFilter) }

        val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build()

        bluetoothLeScanner?.startScan(filters, settings, scanCallback)

        scanHandler = Handler()
        scanHandler?.postDelayed({ this.stopScan() }, SCAN_PERIOD)

        isScanning = true
        log("Started scanning.")
    }

    private fun stopScan() {
        if (isScanning && bluetoothAdapter?.isEnabled == true) {
            bluetoothLeScanner?.stopScan(scanCallback)
            scanComplete()
        }

        scanCallback = null
        isScanning = false
        scanHandler = null
        log("Stopped scanning.")
    }

    private fun scanComplete() {
        scanResults?.takeIf { it.isNotEmpty() }
                ?.let {
                    for (deviceAddress in it.keys) {
                        val device = it[deviceAddress]
                        llServerContainer.show()
                        tvGattServerName.text = device?.address
                        bluetoothDevice = device
                    }
                }
    }

    fun setConnected(connected: Boolean) {
        isConnected = connected
    }

    fun initializeEcho() {
        isEchoInitialized = true
    }

    private fun disconnectGattServer() {
        log("Closing Gatt connection")
        clearLogs()
        isConnected = false
        isEchoInitialized = false
        gatt?.apply {
            disconnect()
            close()
        }
    }

    // Logging

    private fun clearLogs() {
        logHandler?.post { tvLog?.text = null }
    }

    private fun logError(error: String) {
        log("Error: $error")
    }

    private fun log(msg: String) {
        LOG.d(msg)
        logHandler?.post {
            tvLog?.append(msg + "\n")
            svLog?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun hasPermissions(): Boolean {
        if (bluetoothAdapter?.isEnabled == false) {
            requestBluetoothEnable()
            return false
        }
        return true
    }

    private fun requestBluetoothEnable() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        log("Requested user enables Bluetooth. Try starting the scan again.")
    }

    private fun hasLocationPermissions() {
        permissionRequest.request(
                this,
                REQUEST_FINE_LOCATION,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                object : PermissionResult {
                    override fun onPermissionGranted() {
                        scan()
                    }
                })
    }

    // Gatt connection

    private fun connectDevice(device: BluetoothDevice?) {
        device?.run {
            log("Connecting to $address")
            val gattClientCallback = GattClientCallback()
            gatt = connectGatt(ctx, false, gattClientCallback)
        }
    }

    // Messaging

    private fun sendMessage() {
        if (!isConnected || !isEchoInitialized) {
            return
        }

        val characteristic = gatt?.run { BluetoothUtils.findEchoCharacteristic(this) }
        if (characteristic == null) {
            logError("Unable to find echo characteristic.")
            disconnectGattServer()
            return
        }

        val message = etMessage.text.toString()
        log("Sending message: $message")

        val messageBytes = StringUtils.bytesFromString(message)
        if (messageBytes.isEmpty()) {
            logError("Unable to convert message to bytes")
            return
        }

        characteristic.value = messageBytes
        val success = gatt?.writeCharacteristic(characteristic)
        if (success == true) {
            log("Wrote: " + StringUtils.byteArrayInHexFormat(messageBytes))
        } else {
            logError("Failed to write data")
        }
    }


    // Callbacks
    private inner class BleScanCallback internal constructor(private val mScanResults: MutableMap<String, BluetoothDevice>?)
        : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addScanResult(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            for (result in results) {
                addScanResult(result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            logError("BLE Scan Failed with code $errorCode")
        }

        private fun addScanResult(result: ScanResult) {
            val device = result.device
            val deviceAddress = device.address
            mScanResults?.set(deviceAddress, device)
        }
    }

    private inner class GattClientCallback : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            log("onConnectionStateChange newState: $newState")

            if (status == BluetoothGatt.GATT_FAILURE) {
                logError("Connection Gatt failure status $status")
                disconnectGattServer()
                return
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                // handle anything not SUCCESS as failure
                logError("Connection not GATT sucess status $status")
                disconnectGattServer()
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Connected to device " + gatt.device.address)
                setConnected(true)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Disconnected from device")
                disconnectGattServer()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)

            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("Device service discovery unsuccessful, status $status")
                return
            }

            val matchingCharacteristics = BluetoothUtils.findCharacteristics(gatt)
            if (matchingCharacteristics.isEmpty()) {
                logError("Unable to find characteristics.")
                return
            }

            log("Initializing: setting write type and enabling notification")
            for (characteristic in matchingCharacteristics) {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                enableCharacteristicNotification(gatt, characteristic)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt,
                                           characteristic: BluetoothGattCharacteristic,
                                           status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Characteristic written successfully")
            } else {
                logError("Characteristic write unsuccessful, status: $status")
                disconnectGattServer()
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt,
                                          characteristic: BluetoothGattCharacteristic,
                                          status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Characteristic read successfully")
                readCharacteristic(characteristic)
            } else {
                logError("Characteristic read unsuccessful, status: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt,
                                             characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)
            log("Characteristic changed, " + characteristic.uuid.toString())
            readCharacteristic(characteristic)
        }

        private fun enableCharacteristicNotification(gatt: BluetoothGatt,
                                                     characteristic: BluetoothGattCharacteristic) {
            val characteristicWriteSuccess = gatt.setCharacteristicNotification(characteristic, true)
            if (characteristicWriteSuccess) {
                log("Characteristic notification set successfully for " + characteristic.uuid.toString())
                if (BluetoothUtils.isEchoCharacteristic(characteristic)) {
                    initializeEcho()
                }
            } else {
                logError("Characteristic notification set failure for " + characteristic.uuid.toString())
            }
        }

        private fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
            val messageBytes = characteristic.value
            log("Read: " + StringUtils.byteArrayInHexFormat(messageBytes))
            StringUtils.stringFromBytes(messageBytes)
                    ?.let { log("Received message: $it") }
                    ?: let { logError("Unable to convert bytes to string") }
        }
    }
}
