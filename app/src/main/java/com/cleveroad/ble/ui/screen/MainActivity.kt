package com.cleveroad.ble.ui.screen

import android.os.Bundle
import com.cleveroad.ble.R
import com.cleveroad.ble.model.DeviceType
import com.cleveroad.ble.ui.base.BaseActivity
import com.cleveroad.ble.ui.screen.choose.ChooseDeviceTypeCallback
import com.cleveroad.ble.ui.screen.choose.ChooseDeviceTypeFragment
import com.cleveroad.ble.ui.screen.client.ClientFragment
import com.cleveroad.ble.ui.screen.server.ServerFragment

class MainActivity : BaseActivity(), ChooseDeviceTypeCallback {

    override val containerId = R.id.container
    override val layoutId = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) showChooseScreen()
    }

    override fun openDeviceType(type: DeviceType) {
        when (type) {
            DeviceType.SERVER -> showSeverScreen()
            DeviceType.CLIENT -> showClientScreen()
        }
    }

    private fun showChooseScreen() {
        replaceFragment(ChooseDeviceTypeFragment.newInstance(), false)
    }

    private fun showSeverScreen() {
        replaceFragment(ServerFragment.newInstance())
    }

    private fun showClientScreen() {
        replaceFragment(ClientFragment.newInstance())
    }
}
