package com.piotv.navigate.ui

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import com.piotv.navigate.R

/**
 * The ScrollView of the Now Playing block — with three fixes the framework
 * ScrollView does not provide.
 *
 * ## 1. A height limit the ScrollView itself does **not** have
 *
 * `android:maxHeight` has no effect on a `ScrollView`. The platform knows the
 * attribute only for `ImageView`, `ExpandableListView` and the internal
 * `MaxHeightFrameLayout` — `<declare-styleable name="ScrollView">` contains
 * nothing but `fillViewport`. It can still be written: aapt2 only checks that
 * the attribute **exists**. It is silently ignored.
 *
 * That was exactly the bug in 0.15.0. The history grew, the ScrollView grew
 * with it, and with them the whole block in the top left: the grid below was
 * pushed off screen and the arrow keys ran into nothing. So here the limit is
 * an attribute of our own (`app:maxHeight`), which [onMeasure] actually
 * applies.
 *
 * ## 2. Clipped rows are not focus candidates
 *
 * The framework's focus finder (`FocusFinder`) does not know that a ScrollView
 * gets clipped: the rows below the visible window sit further down in the
 * layout — geometrically where the grid starts. A ▲ from the grid therefore
 * landed on a row nobody can see. [addFocusables] reports only the rows that
 * lie **entirely** within the window ([RowWindow.isVisible]).
 *
 * ## 3. Row by row instead of by geometry
 *
 * ▲/▼ move the focus by **one row** and scroll it into the window first
 * ([dispatchKeyEvent]). At the start and end of the list the key is not
 * consumed: there the framework decides, and there its answer is right
 * (▼ on the last row moves into the grid, ▲ on the first row stays put).
 * Without this rule the order would depend on the accident of spacing.
 *
 * What this class does **not** know: which rows exist, what a click on a row
 * does and how tall the block may be. That is decided by the layout and
 * [NowPlayingPanel].
 */
class PanelScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.scrollViewStyle,
) : ScrollView(context, attrs, defStyleAttr) {

    /**
     * At most this tall in pixels; `0` means "as tall as the content".
     *
     * The value comes from `app:maxHeight` (`@dimen/now_playing_max_height`).
     * `recycle()` instead of `use`: `TypedArray` is only an `AutoCloseable` as
     * of API 31, and minSdk here is 23.
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
            // Cap only the height — the width stays as the layout wants it.
            // The view then scrolls within itself instead of letting the
            // block grow.
            setMeasuredDimension(measuredWidth, maxHeight)
        }
    }

    /**
     * Reports to the focus finder only the rows that lie entirely within the
     * window.
     *
     * The focus itself is untouched: [moveFocus] calls `requestFocus()`
     * directly and thus also reaches the row that was just below the window —
     * by then it has already been scrolled in.
     */
    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
        val container = getChildAt(0) as? ViewGroup
        val rows = rows(container)
        if (container == null || rows.isEmpty()) {
            // No content: then the ordinary rule applies (and this view
            // itself is not focusable in the layout).
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
     * ▲/▼ in the block: one row on, and specifically the **next in the tree**.
     *
     * Runs here because a key press coming from the focused child first travels
     * up through the parents ([ViewGroup.dispatchKeyEvent]) — this view is one
     * of those parents and therefore answers the key before the framework's
     * focus finder does.
     *
     * @return `true` if the key is consumed; otherwise the framework takes over
     *   (moving into the grid, or leaving the block).
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
     * Moves the focus one row on and scrolls it into the window while doing so.
     *
     * @return `false` if there is no further row in that direction — then the
     *   framework is in charge.
     */
    private fun moveFocus(delta: Int): Boolean {
        val container = getChildAt(0) as? ViewGroup ?: return false
        val focused = findFocus() ?: return false
        val index = rows(container).indexOfFirst { it === focused || it.hasFocus() }
        if (index < 0) return false
        val next = container.getChildAt(index + delta) ?: return false
        // Scroll first, then hand over the focus: the other way around the
        // focus would have to jump to a row still outside the window.
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

    /** The rows of the block — the children of the single content container. */
    private fun rows(container: ViewGroup?): List<View> {
        if (container == null) return emptyList()
        return (0 until container.childCount).map { container.getChildAt(it) }
    }

    /**
     * The row as a rectangle in the scroll view's content coordinates: the
     * content starts at the top edge of the container, the row within it at
     * its own top edge.
     */
    private fun geometry(container: View, row: View): RowWindow.Row =
        RowWindow.Row(top = container.top + row.top, height = row.height)
}
