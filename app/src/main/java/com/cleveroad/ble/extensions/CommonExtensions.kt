package com.cleveroad.ble.extensions

import android.view.View


fun View.OnClickListener.setClickListeners(vararg views: View) {
    views.forEach { it.setOnClickListener(this) }
}

fun View.hide(gone: Boolean = true) {
    visibility = if (gone) View.GONE else View.INVISIBLE
}

fun View.show() {
    visibility = View.VISIBLE
}

fun reverse(value: ByteArray): ByteArray {
    val length = value.size
    val reversed = ByteArray(length)
    for (i in 0 until length) {
        reversed[i] = value[length - (i + 1)]
    }
    return reversed
}