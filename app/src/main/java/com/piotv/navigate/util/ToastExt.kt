package com.piotv.navigate.util

import android.content.Context
import android.widget.Toast

/**
 * Navigate speaks only in short toasts: "Favorite added", "App cannot be
 * started". No dialog, no Snackbar (AndroidX), no animation — a toast is the
 * cheapest feedback available on Android TV.
 */
fun Context.shortToast(message: CharSequence) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.shortToast(resId: Int) {
    Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
