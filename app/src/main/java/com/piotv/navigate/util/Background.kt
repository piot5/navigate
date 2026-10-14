package com.piotv.navigate.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Genau ein Hintergrund-Thread und ein Main-Thread-Handler für die ganze App.
 *
 * Warum kein Coroutine-Dispatcher und keine Thread-Pools: Navigate hat exakt
 * zwei Hintergrund-Aufgaben (App-Liste abfragen, Symbole dekodieren), beide
 * kurz und beide seriell. Ein einzelner Thread mit niedriger Priorität reicht
 * und garantiert, dass Hintergrundarbeit niemals die UI verdrängt — auf einem
 * 1,4-GB-Gerät mit 32-bit-ABI der wichtigste Punkt.
 */
object Background {

    val main = Handler(Looper.getMainLooper())

    val io: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "navigate-io").apply { priority = Thread.MIN_PRIORITY }
    }
}
