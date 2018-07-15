package com.cleveroad.ble.ui.screen.choose

import com.cleveroad.ble.model.DeviceType


interface ChooseDeviceTypeCallback {
    fun openDeviceType(type: DeviceType)
}
