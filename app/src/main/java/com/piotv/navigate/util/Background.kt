package com.piotv.navigate.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Exactly one background thread and one main-thread handler for the whole app.
 *
 * Why no coroutine dispatcher and no thread pools: Navigate has exactly two
 * background tasks (querying the app list, decoding icons), both short and
 * both serial. A single low-priority thread suffices and guarantees that
 * background work never crowds out the UI — on a 1.4 GB device with a 32-bit
 * ABI, that is the point that matters most.
 */
object Background {

    val main = Handler(Looper.getMainLooper())

    val io: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "navigate-io").apply { priority = Thread.MIN_PRIORITY }
    }
}
