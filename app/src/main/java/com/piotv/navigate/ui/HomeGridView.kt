package com.piotv.navigate.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.AdapterView
import android.widget.GridView

/**
 * GridView mit einer Korrektur für die Fernbedienung.
 *
 * GridView (Framework) statt RecyclerView: D-Pad-Navigation, Scrollen und
 * View-Recycling sind eingebaut, und es kostet null zusätzliche Abhängigkeit.
 * Genau das ist hier die Anforderung.
 */
class HomeGridView @JvmOverloads constructor(
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
        // eine Zelle blau umrahmt, während der Fokus schon auf dem „…“-Knopf
        // darunter steht.
        selectedView?.isSelected = gainFocus
    }
}
