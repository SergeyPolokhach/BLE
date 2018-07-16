package com.cleveroad.ble.ui.screen.server

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context.BLUETOOTH_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.view.View
import com.cleveroad.ble.CHARACTERISTIC_ECHO_UUID
import com.cleveroad.ble.R
import com.cleveroad.ble.SERVICE_UUID
import com.cleveroad.ble.extensions.reverse
import com.cleveroad.ble.extensions.setClickListeners
import com.cleveroad.ble.ui.base.BaseFragment
import com.cleveroad.ble.utils.BluetoothUtils
import com.cleveroad.ble.utils.LOG
import com.cleveroad.ble.utils.StringUtils
import kotlinx.android.synthetic.main.fragment_server.*
import kotlinx.android.synthetic.main.view_log.*
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx
import java.util.*


class ServerFragment : BaseFragment(), View.OnClickListener {

    companion object {

        fun newInstance() = ServerFragment().apply {
            arguments = Bundle()
        }
    }

    override val layoutId = R.layout.fragment_server

    private var handler: Handler? = null
    private var logHandler: Handler? = null

    private var devices: MutableList<BluetoothDevice>? = null
    private var gattServer: BluetoothGattServer? = null
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        handler = Handler()
        logHandler = Handler(Looper.getMainLooper())
        devices = ArrayList()

        bluetoothManager = act.run { getSystemService(BLUETOOTH_SERVICE) as BluetoothManager? }
        bluetoothAdapter = bluetoothManager?.adapter

        setClickListeners(bRestartServer, bClearLog)
    }

    override fun onResume() {
        super.onResume()

        if (bluetoothAdapter?.isEnabled == false) {
            // Request user to enable it
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableBtIntent)
            backPressed()
            return
        }

        // Check low energy support
        if (!act.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            // Get a newer device
            log("No LE Support.")
            backPressed()
            return
        }

        // Check advertising
        if (bluetoothAdapter?.isMultipleAdvertisementSupported == false) {
            // Unable to run the server on this device, get a better device
            log("No Advertising Support.")
            backPressed()
            return
        }

        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        val gattServerCallback = GattServerCallback()
        gattServer = bluetoothManager?.openGattServer(ctx, gattServerCallback)

        setDeviceInfo()

        setupServer()
        startAdvertising()
    }

    override fun onPause() {
        stopAdvertising()
        stopServer()
        super.onPause()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.bRestartServer -> restartServer()
            R.id.bClearLog -> clearLogs()
        }
    }

    private fun setDeviceInfo() {
        tvDeviceInfo.text = bluetoothAdapter?.run { "Device Info \nName: $name \nAddress: $address" }
    }


    private fun clearLogs() {
        logHandler?.post { tvLog.text = null }
    }

    private fun log(msg: String) {
        LOG.d(msg)
        logHandler?.post {
            tvLog?.append(msg + "\n")
            svLog?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun setupServer() {
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val writeCharacteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_ECHO_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE)

        service.addCharacteristic(writeCharacteristic)

        gattServer?.addService(service)
    }

    private fun stopServer() {
        gattServer?.close()
    }

    private fun restartServer() {
        stopAdvertising()
        stopServer()
        setupServer()
        startAdvertising()
    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .build()

        val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()

        bluetoothLeAdvertiser?.startAdvertising(settings, data, mAdvertiseCallback)
    }

    private fun stopAdvertising() {
        bluetoothLeAdvertiser?.stopAdvertising(mAdvertiseCallback)
    }

    private val mAdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            log("Peripheral advertising started.")
        }

        override fun onStartFailure(errorCode: Int) {
            log("Peripheral advertising failed: $errorCode")
        }
    }

    private fun notifyCharacteristic(value: ByteArray, uuid: UUID) {
        handler?.post {
            val service = gattServer?.getService(SERVICE_UUID)
            val characteristic = service?.getCharacteristic(uuid)
            log("Notifying characteristic " + characteristic?.uuid.toString()
                    + ", new value: " + StringUtils.byteArrayInHexFormat(value))

            characteristic?.value = value
            characteristic?.let {
                val confirm = BluetoothUtils.requiresConfirmation(it)
                devices?.run {
                    for (device in this) {
                        gattServer?.notifyCharacteristicChanged(device, characteristic, confirm)
                    }
                }
            }
        }
    }

    fun addDevice(device: BluetoothDevice) {
        log("Device added: " + device.address)
        handler?.post { devices?.add(device) }
    }

    fun removeDevice(device: BluetoothDevice) {
        log("Device removed: " + device.address)
        handler?.post { devices?.remove(device) }
    }

    fun sendResponse(device: BluetoothDevice, requestId: Int, status: Int, offset: Int, value: ByteArray?) {
        handler?.post { gattServer?.sendResponse(device, requestId, status, 0, null) }
    }

    private fun sendReverseMessage(message: ByteArray) {
        handler?.post {
            val response = reverse(message)
            log("Sending: " + StringUtils.byteArrayInHexFormat(response))
            notifyCharacteristicEcho(response)
        }
    }

    private fun notifyCharacteristicEcho(value: ByteArray) {
        notifyCharacteristic(value, CHARACTERISTIC_ECHO_UUID)
    }

    private inner class GattServerCallback : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            log("onConnectionStateChange " + device.address
                    + "\nstatus " + status
                    + "\nnewState " + newState)

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                addDevice(device)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                removeDevice(device)
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice,
                                                 requestId: Int,
                                                 offset: Int,
                                                 characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)

            log("onCharacteristicReadRequest " + characteristic.uuid.toString())

            if (BluetoothUtils.requiresResponse(characteristic)) {
                sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice,
                                                  requestId: Int,
                                                  characteristic: BluetoothGattCharacteristic,
                                                  preparedWrite: Boolean,
                                                  responseNeeded: Boolean,
                                                  offset: Int,
                                                  value: ByteArray) {
            super.onCharacteristicWriteRequest(device,
                    requestId,
                    characteristic,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value)
            log("onCharacteristicWriteRequest" + characteristic.uuid.toString()
                    + "\nReceived: " + StringUtils.byteArrayInHexFormat(value))

            if (CHARACTERISTIC_ECHO_UUID == characteristic.uuid) {
                sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                sendReverseMessage(value)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            super.onNotificationSent(device, status)
            log("onNotificationSent")
        }
    }
}
