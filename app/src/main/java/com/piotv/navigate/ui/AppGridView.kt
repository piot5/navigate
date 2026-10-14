package com.piotv.navigate.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.AdapterView
import android.widget.GridView

/**
 * GridView mit zwei Korrekturen für die Fernbedienung.
 *
 * GridView (Framework) statt RecyclerView: D-Pad-Navigation, Scrollen und
 * View-Recycling sind eingebaut, und es kostet null zusätzliche Abhängigkeit.
 * Genau das ist hier die Anforderung.
 */
class AppGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.gridViewStyle,
) : GridView(context, attrs, defStyleAttr) {

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)

        if (gainFocus && selectedItemPosition == AdapterView.INVALID_POSITION && count > 0) {
            // Ohne Auswahl gäbe es nach dem Fokuswechsel keine sichtbare
            // Markierung und der erste D-Pad-Druck wäre ein Blindflug.
            setSelection(0)
        }

        // GridView markiert die gewählte Zelle über state_selected, blendet die
        // Markierung beim Fokusverlust aber nicht aus. Ohne diese Zeile bliebe
        // eine Zelle hervorgehoben, während das Dock den Fokus hat.
        selectedView?.isSelected = gainFocus
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && isInLastRow()) {
            // Absichtlich NICHT konsumieren: dann sucht das Framework selbst
            // unterhalb weiter und findet das Dock. Ein konsumierter Tastendruck
            // würde den Fokus im Raster festnageln.
            return false
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun isInLastRow(): Boolean {
        val total = count
        val columns = numColumns
        if (total == 0 || columns <= 0) return false
        return selectedItemPosition >= total - columns
    }
}
