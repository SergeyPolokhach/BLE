package com.cleveroad.ble.ui.screen.choose

import android.content.Context
import android.os.Bundle
import android.view.View
import com.cleveroad.ble.R
import com.cleveroad.ble.extensions.setClickListeners
import com.cleveroad.ble.model.DeviceType
import com.cleveroad.ble.ui.base.BaseFragment
import com.cleveroad.ble.ui.base.bindInterfaceOrThrow
import kotlinx.android.synthetic.main.choose_device_type.*


class ChooseDeviceTypeFragment() : BaseFragment(), View.OnClickListener {

    companion object {

        fun newInstance() = ChooseDeviceTypeFragment().apply {
            arguments = Bundle()
        }
    }

    override val layoutId = R.layout.choose_device_type

    private var callback: ChooseDeviceTypeCallback? = null

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        callback = bindInterfaceOrThrow<ChooseDeviceTypeCallback>(parentFragment, context)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setClickListeners(bServer, bClient)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.bServer -> openScreen(DeviceType.SERVER)
            R.id.bClient -> openScreen(DeviceType.CLIENT)
        }
    }

    override fun onDetach() {
        callback = null
        super.onDetach()
    }

    private fun openScreen(type: DeviceType) {
        callback?.run { openDeviceType(type) }
    }
}