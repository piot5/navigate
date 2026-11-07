package com.piotv.navigate.ui

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import com.piotv.navigate.R

/**
 * Die ScrollView des Now-Playing-Blocks — mit drei Korrekturen, die der
 * Framework-ScrollView nicht mitbringt.
 *
 * ## 1. Eine Höhengrenze, die es am ScrollView **nicht** gibt
 *
 * `android:maxHeight` ist am `ScrollView` wirkungslos. Die Plattform kennt das
 * Attribut nur für `ImageView`, `ExpandableListView` und das interne
 * `MaxHeightFrameLayout` — `<declare-styleable name="ScrollView">` enthält
 * ausschließlich `fillViewport`. Schreiben lässt es sich trotzdem: aapt2 prüft
 * nur, ob es das Attribut **gibt**. Es wird still ignoriert.
 *
 * Genau das war der Fehler in 0.15.0. Der Verlauf wuchs, die ScrollView wuchs
 * mit, und mit ihr der ganze Block oben links: das Raster darunter wurde aus dem
 * Bild geschoben und die Pfeiltasten liefen ins Leere. Hier ist die Grenze
 * deshalb eine eigene Eigenschaft (`app:maxHeight`), die [onMeasure] auch
 * anwendet.
 *
 * ## 2. Abgeschnittene Zeilen sind keine Fokus-Kandidaten
 *
 * Der Fokus-Sucher des Frameworks (`FocusFinder`) kennt das Abschneiden einer
 * ScrollView nicht: die Zeilen unterhalb des sichtbaren Fensters liegen im
 * Layout weiter unten — geometrisch dort, wo das Raster anfängt. Ein ▲ aus dem
 * Raster landete deshalb auf einer Zeile, die niemand sieht. [addFocusables]
 * meldet nur die Zeilen, die **ganz** im Fenster liegen ([RowWindow.isVisible]).
 *
 * ## 3. Zeilenweise statt nach Geometrie
 *
 * ▲/▼ bewegen den Fokus um **eine Zeile** und scrollen sie vorher ins Fenster
 * ([dispatchKeyEvent]). Am Anfang und Ende der Liste wird die Taste nicht
 * verbraucht: dort entscheidet das Framework, und dort ist seine Antwort richtig
 * (▼ auf der letzten Zeile führt ins Raster, ▲ auf der ersten bleibt stehen).
 * Ohne diese Zeile hinge die Reihenfolge vom Zufall der Abstände ab.
 *
 * Was diese Klasse **nicht** weiß: welche Zeilen es gibt, was ein Klick auf eine
 * Zeile tut und wie hoch der Block sein darf. Das entscheidet das Layout und
 * [NowPlayingPanel].
 */
class PanelScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.scrollViewStyle,
) : ScrollView(context, attrs, defStyleAttr) {

    /**
     * Höchstens so hoch in Pixeln; `0` heißt „so hoch wie der Inhalt“.
     *
     * Der Wert kommt aus `app:maxHeight` (`@dimen/now_playing_max_height`).
     * `recycle()` statt `use`: `TypedArray` ist erst ab API 31 `AutoCloseable`,
     * und minSdk ist hier 23.
     */
    var maxHeight: Int = 0
        private set

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.PanelScrollView, defStyleAttr, 0)
        try {
            maxHeight = a.getDimensionPixelSize(R.styleable.PanelScrollView_maxHeight, 0)
        } finally {
            a.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (maxHeight > 0 && measuredHeight > maxHeight) {
            // Nur die Höhe deckeln — die Breite bleibt, wie das Layout sie will.
            // Danach scrollt die Ansicht in sich, statt den Block wachsen zu
            // lassen.
            setMeasuredDimension(measuredWidth, maxHeight)
        }
    }

    /**
     * Meldet dem Fokus-Sucher nur die Zeilen, die ganz im Fenster liegen.
     *
     * Der Fokus selbst geht davon unberührt: [moveFocus] ruft `requestFocus()`
     * direkt auf und erreicht damit auch die Zeile, die gerade noch unterhalb
     * des Fensters lag — sie ist dann ja schon hereingescrollt.
     */
    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
        val container = getChildAt(0) as? ViewGroup
        val rows = rows(container)
        if (container == null || rows.isEmpty()) {
            // Kein Inhalt: dann gilt die gewöhnliche Regel (und diese Ansicht
            // selbst ist im Layout nicht fokussierbar).
            super.addFocusables(views, direction, focusableMode)
            return
        }
        for (row in rows) {
            if (RowWindow.isVisible(geometry(container, row), height, scrollY)) {
                row.addFocusables(views, direction, focusableMode)
            }
        }
    }

    /**
     * ▲/▼ im Block: eine Zeile weiter, und zwar die **nächste im Baum**.
     *
     * Läuft hier, weil ein Tastendruck vom fokussierten Kind kommend zuerst die
     * Elternteile durchläuft ([ViewGroup.dispatchKeyEvent]) — diese Ansicht ist
     * ein solches Elternteil und beantwortet die Taste deshalb vor dem
     * Fokus-Sucher des Frameworks.
     *
     * @return `true`, wenn die Taste verbraucht ist; sonst übernimmt das
     *   Framework (Wechsel ins Raster bzw. Verlassen des Blocks).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && !event.isAltPressed && !event.isCtrlPressed) {
            val delta = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> 1
                KeyEvent.KEYCODE_DPAD_UP -> -1
                else -> 0
            }
            if (delta != 0 && moveFocus(delta)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Setzt den Fokus eine Zeile weiter und scrollt sie dabei ins Fenster.
     *
     * @return `false`, wenn es in dieser Richtung keine Zeile mehr gibt — dann
     *   ist das Framework zuständig.
     */
    private fun moveFocus(delta: Int): Boolean {
        val container = getChildAt(0) as? ViewGroup ?: return false
        val focused = findFocus() ?: return false
        val index = rows(container).indexOfFirst { it === focused || it.hasFocus() }
        if (index < 0) return false
        val next = container.getChildAt(index + delta) ?: return false
        // Erst scrollen, dann den Fokus geben: andersherum müsste der Fokus eine
        // Zeile anspringen, die noch außerhalb des Fensters liegt.
        scrollTo(
            0,
            RowWindow.scrollFor(
                row = geometry(container, next),
                viewportHeight = height,
                contentHeight = container.height,
                scrollY = scrollY,
            ),
        )
        return next.requestFocus()
    }

    /** Die Zeilen des Blocks — die Kinder des einen Inhalts-Containers. */
    private fun rows(container: ViewGroup?): List<View> {
        if (container == null) return emptyList()
        return (0 until container.childCount).map { container.getChildAt(it) }
    }

    /**
     * Die Zeile als Rechteck im Inhaltsmaß der ScrollView: der Inhalt beginnt
     * beim oberen Rand des Containers, die Zeile darin bei ihrer eigenen
     * Oberkante.
     */
    private fun geometry(container: View, row: View): RowWindow.Row =
        RowWindow.Row(top = container.top + row.top, height = row.height)
}
