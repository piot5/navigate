package com.piotv.navigate.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.AdapterView
import android.widget.GridView

/**
 * GridView with one fix for the remote control.
 *
 * GridView (framework) instead of RecyclerView: d-pad navigation, scrolling
 * and view recycling are built in, and it costs zero extra dependencies.
 * That is exactly the requirement here.
 */
class HomeGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.gridViewStyle,
) : GridView(context, attrs, defStyleAttr) {

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)

        if (gainFocus && selectedItemPosition == AdapterView.INVALID_POSITION && count > 0) {
            // Without a selection there would be no visible highlight after the
            // focus change, and the first d-pad press would be flying blind.
            setSelection(0)
        }

        // GridView marks the selected cell via state_selected but does not hide
        // the highlight when focus is lost. Without this line a cell would stay
        // outlined in blue while the focus is already on the "…" button below.
        selectedView?.isSelected = gainFocus
    }
}
