package com.piotv.navigate.ui

import android.os.Handler
import android.os.Looper

/**
 * Ein Takt auf dem Main-Thread: führt [action] aus und plant sich selbst neu.
 *
 * Zwei Dinge auf der Home-Seite ticken (Uhr, „Now Playing"), und bis 0.12 hatte
 * jedes seinen eigenen `Handler` und sein eigenes `Runnable`-Objekt: Felder und
 * Methoden für viermal dieselben Zeilen. Unterschiedlich war nur das Intervall —
 * deshalb steht das Muster einmal hier. Ein dritter Takt (die grüne
 * Beschriftung, alle 5 Sekunden) ist in 0.14 entfallen.
 *
 * `start()` führt [action] **sofort** aus: nach einem HOME-Druck soll die Uhr
 * stehen, ohne auf den ersten Takt zu warten. `stop()` entfernt nur die geplante
 * Fortsetzung — die Reihenfolge „erst entfernen, dann den ersten Durchlauf“ ist
 * dieselbe wie vorher, damit ein zweites `start()` keinen zweiten Takt erzeugt.
 *
 * Der Handler hängt am Main-Looper, nicht am Aufrufer: [action] darf also Views
 * anfassen (die Uhr tut das), und eine teure Abfrage gehört von dort aus in den
 * Hintergrund-Thread ([com.piotv.navigate.util.Background]).
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
