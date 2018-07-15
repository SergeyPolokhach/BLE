package com.cleveroad.ble.ui.screen.server

import android.os.Bundle
import android.view.View
import com.cleveroad.ble.R
import com.cleveroad.ble.ui.base.BaseFragment


class ServerFragment() : BaseFragment(), View.OnClickListener {

    companion object {

        fun newInstance() = ServerFragment().apply {
            arguments = Bundle()
        }
    }

    override val layoutId = R.layout.server

    override fun onClick(v: View) {
        when (v.id) {
        // do nothing
        }
    }
}
