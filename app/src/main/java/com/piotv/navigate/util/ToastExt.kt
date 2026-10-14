package com.piotv.navigate.util

import android.content.Context
import android.widget.Toast

/**
 * Navigate meldet sich nur mit kurzen Toasts: "Favorit hinzugefügt", "App lässt
 * sich nicht starten". Kein Dialog, kein Snackbar (AndroidX), keine Animation —
 * ein Toast ist auf Android TV die billigste Rückmeldung, die es gibt.
 */
fun Context.shortToast(message: CharSequence) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.shortToast(resId: Int) {
    Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
