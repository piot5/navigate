package com.piotv.navigate.ui

import android.os.Handler
import android.os.Looper

/**
 * A beat on the main thread: runs [action] and reschedules itself.
 *
 * Two things on the home page tick (the clock, "Now Playing"), and until 0.12
 * each had its own `Handler` and its own `Runnable` object: fields and methods
 * for four times the same lines. Only the interval differed — so the pattern
 * lives here once. A third beat (the green label, every 5 seconds) was
 * dropped in 0.14.
 *
 * `start()` runs [action] **immediately**: after a press of HOME the clock
 * should stand there without waiting for the first beat. `stop()` removes
 * only the scheduled continuation — the order "remove first, then run the
 * first pass" is the same as before, so that a second `start()` does not
 * create a second beat.
 *
 * The handler hangs on the main looper, not on the caller: [action] may
 * therefore touch views (the clock does), and an expensive query belongs over
 * there in the background thread ([com.piotv.navigate.util.Background]).
 */
class Ticker(private val intervalMs: Long, private val action: () -> Unit) {

    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            action()
            handler.postDelayed(this, intervalMs)
        }
    }

    fun start() {
        stop()
        tick.run()
    }

    fun stop() {
        handler.removeCallbacks(tick)
    }
}
