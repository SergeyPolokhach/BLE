package com.cleveroad.ble.utils

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
import android.bluetooth.BluetoothGattService
import com.cleveroad.ble.CHARACTERISTIC_ECHO_STRING
import com.cleveroad.ble.SERVICE_STRING
import java.util.*

object BluetoothUtils {

    // Characteristics
    fun findCharacteristics(bluetoothGatt: BluetoothGatt): List<BluetoothGattCharacteristic> {
        val matchingCharacteristics = ArrayList<BluetoothGattCharacteristic>()

        val serviceList = bluetoothGatt.services
        val service = BluetoothUtils.findService(serviceList) ?: return matchingCharacteristics

        val characteristicList = service.characteristics
        for (characteristic in characteristicList) {
            if (isMatchingCharacteristic(characteristic)) {
                matchingCharacteristics.add(characteristic)
            }
        }

        return matchingCharacteristics
    }

    fun findEchoCharacteristic(bluetoothGatt: BluetoothGatt) =
            findCharacteristic(bluetoothGatt, CHARACTERISTIC_ECHO_STRING)

    private fun findCharacteristic(bluetoothGatt: BluetoothGatt, uuidString: String): BluetoothGattCharacteristic? {
        val serviceList = bluetoothGatt.services
        val service = BluetoothUtils.findService(serviceList) ?: return null

        val characteristicList = service.characteristics
        for (characteristic in characteristicList) {
            if (characteristicMatches(characteristic, uuidString)) {
                return characteristic
            }
        }
        return null
    }

    fun isEchoCharacteristic(characteristic: BluetoothGattCharacteristic) =
            characteristicMatches(characteristic, CHARACTERISTIC_ECHO_STRING)

    private fun characteristicMatches(characteristic: BluetoothGattCharacteristic?, uuidString: String) =
            if (characteristic == null) false else uuidMatches(characteristic.uuid.toString(), uuidString)

    private fun isMatchingCharacteristic(characteristic: BluetoothGattCharacteristic?) =
            if (characteristic == null) false else matchesCharacteristicUuidString(characteristic.uuid.toString())

    private fun matchesCharacteristicUuidString(characteristicIdString: String) =
            uuidMatches(characteristicIdString, CHARACTERISTIC_ECHO_STRING)

    fun requiresResponse(characteristic: BluetoothGattCharacteristic) =
            (characteristic.properties and PROPERTY_WRITE_NO_RESPONSE) != PROPERTY_WRITE_NO_RESPONSE

    fun requiresConfirmation(characteristic: BluetoothGattCharacteristic) =
            (characteristic.properties and PROPERTY_INDICATE) == PROPERTY_INDICATE

    // Service

    private fun findService(serviceList: List<BluetoothGattService>): BluetoothGattService? {
        for (service in serviceList) {
            if (matchesServiceUuidString(service.uuid.toString())) {
                return service
            }
        }
        return null
    }

    private fun matchesServiceUuidString(serviceIdString: String) =
            uuidMatches(serviceIdString, SERVICE_STRING)

    private fun uuidMatches(uuidString: String, vararg matches: String): Boolean {
        for (match in matches) {
            if (uuidString.equals(match, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}