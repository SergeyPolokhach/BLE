package com.cleveroad.ble.ui.base

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup


abstract class BaseFragment : Fragment() {

    protected abstract val layoutId: Int

    private var backPressedCallback: BackPressedCallback? = null


    override fun onAttach(context: Context?) {
        super.onAttach(context)
        backPressedCallback = bindInterfaceOrThrow<BackPressedCallback>(parentFragment, context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(layoutId, container, false)

    override fun onDetach() {
        backPressedCallback = null
        super.onDetach()
    }

    fun backPressed() {
        backPressedCallback?.backPressed()
    }
}