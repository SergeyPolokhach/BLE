package com.cleveroad.ble.ui.screen.client

import android.os.Bundle
import android.view.View
import com.cleveroad.ble.R
import com.cleveroad.ble.ui.base.BaseFragment


class ClientFragment() : BaseFragment(), View.OnClickListener {

    companion object {

        fun newInstance() = ClientFragment().apply {
            arguments = Bundle()
        }
    }

    override val layoutId = R.layout.client

    override fun onClick(v: View) {
        when (v.id) {
        // do nothing
        }
    }
}
