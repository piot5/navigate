package com.piotv.navigate

import android.app.Activity
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.AdapterView
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.piotv.navigate.data.AppEntry
import com.piotv.navigate.data.AppRepository
import com.piotv.navigate.data.BackgroundPlan
import com.piotv.navigate.data.BackgroundStyle
import com.piotv.navigate.data.HistoryStore
import com.piotv.navigate.data.LastApp
import com.piotv.navigate.data.NowPlaying
import com.piotv.navigate.data.Order
import com.piotv.navigate.data.OrderStore
import com.piotv.navigate.data.SettingsStore
import com.piotv.navigate.ui.AppAdapter
import com.piotv.navigate.ui.AppMenu
import com.piotv.navigate.ui.BannerLoader
import com.piotv.navigate.ui.GridColumns
import com.piotv.navigate.ui.IconLoader
import com.piotv.navigate.ui.NowPlayingPanel
import com.piotv.navigate.ui.PanelScrollView
import com.piotv.navigate.ui.SettingsActivity
import com.piotv.navigate.ui.Ticker
import com.piotv.navigate.util.Background
import com.piotv.navigate.util.shortToast
import java.io.File
import java.util.Date
import kotlin.math.max

/**
 * The home screen — this launcher has nothing else.
 *
 * A press of HOME shows a grid with all launchable apps, the clock and — if
 * enabled — "Now Playing" at the top left (since 0.13 **one row per app** with
 * an active media session). Only the **first grid row** is visible at first;
 * the rightmost cell in it is the "…" button and unfolds the rest.
 * Configurable are the order of the apps ([OrderStore], via long-press or
 * `adb push`) and whether the Now Playing field appears ([SettingsStore]; a
 * long press of OK on "…" opens the settings).
 *
 * Since 0.15.1 the Now Playing block is a **bounded window**: it grows at most
 * to `now_playing_max_height` and then scrolls within itself, with a
 * permanently visible scrollbar ([PanelScrollView]). The grid starts below the
 * height the block **visibly** occupies ([onHeightChanged]) — otherwise the
 * history would push the app row off the screen.
 *
 * ## What lives here — and what has lived elsewhere since 0.13
 *
 * This class holds the **one** state of the launcher (grid, order, focus) and
 * the lifecycle hooks. The rest lives in its own classes, because otherwise the
 * file would grow back to 769 lines:
 *
 *  - the two tickers (clock, Now Playing) → [Ticker],
 *  - the Now Playing block → [NowPlayingPanel] (several rows, persistence),
 *  - the context menu → [AppMenu],
 *  - the rules behind them anyway: `Order`, `AppCatalog`, `NowPlayingBoard`.
 *
 * Two decisions learned from mistakes:
 *
 *  - **An opaque window.** The theme is deliberately the most ordinary one
 *    there is (`res/values/styles.xml`). Versions up to 0.6 had a translucent
 *    window; on the target device that window was drawn, focused and operable
 *    — but **not visible**.
 *  - **One activity, one state.** Two HOME activities in one app make the
 *    system's resolution ambiguous (a chooser dialog instead of a direct launch).
 */
class LauncherActivity : Activity(), NowPlayingPanel.Listener, AppMenu.Listener {

    private lateinit var grid: GridView
    private lateinit var clock: TextView

    /** The "…" button below the app row. */
    private lateinit var more: TextView

    /** The background (0.14): the image and the darkening layer on top of it. */
    private lateinit var backgroundImage: ImageView
    private lateinit var backgroundScrim: View

    /**
     * What currently counts as the background (`art|path`) — so that a press of
     * HOME does not decode the image again.
     */
    private var appliedBackground: String? = null

    private lateinit var repository: AppRepository
    private lateinit var iconLoader: IconLoader
    private lateinit var order: OrderStore
    private lateinit var settings: SettingsStore
    private lateinit var history: HistoryStore
    private lateinit var lastApp: LastApp

    /** The block at the top left — it owns its rows, its ticker and its state. */
    private lateinit var nowPlayingPanel: NowPlayingPanel

    /** The context menu (long press of OK on an app). */
    private lateinit var menu: AppMenu

    /** The focus banners: the TV artwork per app — the same route as the icons. */
    private lateinit var bannerLoader: BannerLoader

    private var adapter: AppAdapter? = null

    /** All apps in their base order (sorted by language) — the display is built
     *  from that plus the configured order. */
    private var allApps: List<AppEntry> = emptyList()

    /** The apps in display order. */
    private var display: List<AppEntry> = emptyList()

    /** Only the first row, or everything? */
    private var expanded = false

    private var iconsWarmed = false
    private var columns = 0

    /** Top padding of the grid when nothing sits above it (resource). */
    private var baseGridTopPadding = 0
    private var topRowPaddingTop = 0
    private var nowPlayingGap = 0

    /**
     * Is the home screen currently visible on the screen?
     *
     * Not a lifecycle flag: the system pauses the activity on every press of
     * HOME **before** it delivers the intent (`onPause → onNewIntent →
     * onResume`) — `isResumed` is already `false` there. This is exactly where
     * the first version of this behavior failed in 0.1.
     */
    private var homeOnScreen = false

    private val clockTicker = Ticker(CLOCK_INTERVAL_MS) {
        clock.text = DateFormat.getTimeFormat(this).format(Date())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = AppRepository.get(this)
        iconLoader = IconLoader.get(this)
        order = OrderStore.get(this)
        settings = SettingsStore.get(this)
        history = HistoryStore.get(this)
        lastApp = LastApp(this)

        setContentView(R.layout.activity_home)
        grid = findViewById(R.id.grid)
        more = findViewById(R.id.more)
        clock = findViewById(R.id.clock)
        backgroundImage = findViewById(R.id.background_image)
        backgroundScrim = findViewById(R.id.background_scrim)
        baseGridTopPadding = resources.getDimensionPixelSize(R.dimen.grid_padding_top)
        topRowPaddingTop = resources.getDimensionPixelSize(R.dimen.top_row_padding_top)
        nowPlayingGap = resources.getDimensionPixelSize(R.dimen.now_playing_gap_bottom)

        nowPlayingPanel = NowPlayingPanel(
            activity = this,
            container = findViewById(R.id.now_playing),
            scroll = findViewById(R.id.now_playing_scroll),
            listener = this,
            source = NowPlaying(this),
            iconLoader = iconLoader,
            history = history,
            intervalMs = NowPlayingPanel.NOW_PLAYING_INTERVAL_MS,
        )
        menu = AppMenu(this, this)
        bannerLoader = BannerLoader.get(this)

        more.setOnClickListener { setExpanded(!expanded) }
        // A long press of OK on "…" opens the settings — the only place outside
        // the grid that this screen has.
        more.setOnLongClickListener {
            openSettings()
            true
        }

        columns = GridColumns.compute(resources.configuration.screenWidthDp)
        grid.numColumns = columns
        setupAdapter()

        // Build the app list in the background right away, not only when it is
        // shown: the first press of HOME should not have to wait for a query.
        refreshApps(force = false)

        applyGridHeight()
        applyMoreButton()
        applySettings()
        focusGrid()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Every press of HOME arrives here (launchMode=singleTask).
        //
        // Two cases, and they are the whole point of this flag:
        //  - The home screen was already visible → the user wants to go back to
        //    the app they were in (HOME as a "switcher").
        //  - The home screen just came to the front from an app → they want to
        //    see the home screen, collapsed (a fresh home screen).
        if (homeOnScreen && returnToLastApp()) return

        setExpanded(false)
        refreshApps(force = false)
        applySettings()
        focusGrid()
    }

    override fun onResume() {
        super.onResume()
        homeOnScreen = true
        // The order may have been changed from outside (`adb push`). A `stat`
        // costs nothing; a file watcher would be effort without benefit.
        if (order.reloadIfChanged()) applyOrder(allApps)
        // The settings may have changed — in the settings screen itself or via
        // `adb push` (the same throttling as for the order).
        settings.reloadIfChanged()
        nowPlayingPanel.start()
        refreshApps(force = false)
        applySettings()
        focusGrid()
    }

    /**
     * The home screen leaves the screen (another app in the foreground) → the
     * next press of HOME shows it again instead of jumping back.
     */
    override fun onStop() {
        super.onStop()
        homeOnScreen = false
    }

    override fun onPause() {
        super.onPause()
        // No timers while nobody is looking.
        stopClock()
        nowPlayingPanel.stop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Language or density changed: app names and icon sizes live in the
        // cached data and must be rebuilt. Deliberately without an activity
        // restart (configChanges in the manifest) — on the Chromecast a restart
        // would be a visible flicker.
        iconLoader.evictAll()
        iconsWarmed = false
        columns = GridColumns.compute(resources.configuration.screenWidthDp)
        grid.numColumns = columns
        setupAdapter()
        refreshApps(force = true)
    }

    /**
     * Under memory pressure, drop the icons before the system kills the process
     * for them — they are back within milliseconds (disk cache).
     *
     * `TRIM_MEMORY_RUNNING_CRITICAL` is deprecated as of API 34 (the system no
     * longer reports it on newer devices), but minSdk is 23: on older devices
     * it is exactly the value that matters.
     */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            iconLoader.evictAll()
        }
    }

    /**
     * BACK: first collapse the unfolded grid, then return to the last app.
     * On the collapsed home screen without a known app it does nothing.
     *
     * `finish()` would be the obvious route — but the system immediately starts
     * a home activity again afterwards (measured on the target device: the task
     * switched from t2667 to t2668), i.e. a visible flicker for nothing.
     */
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        when {
            expanded -> setExpanded(false)
            homeOnScreen -> returnToLastApp()
        }
    }

    /**
     * Brings the app that was last in the foreground back to the front.
     *
     * The route is the same as tapping a cell: the app's launcher intent. It
     * brings the **existing** task to the front (the app is resumed, not
     * restarted) and gives it the focus back.
     *
     * @return `true` if an app could be started.
     */
    private fun returnToLastApp(): Boolean {
        val packageName = lastApp.packageName() ?: return false
        val entry = display.firstOrNull { it.packageName == packageName }
        if (entry != null) {
            launch(entry)
            return true
        }
        // Second attempt via the launcher intent: also works when the app is
        // not in the grid (e.g. a system app).
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return try {
            homeOnScreen = false
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    // ------------------------------------------------------------------
    // Grid
    // ------------------------------------------------------------------

    /**
     * Adapter and listeners. Built only once (or rebuilt on a configuration
     * change), because `setAdapter` triggers a complete relayout.
     */
    private fun setupAdapter() {
        val newAdapter = AppAdapter(
            iconLoader = iconLoader,
            iconSizePx = resources.getDimensionPixelSize(R.dimen.grid_icon),
        )
        grid.adapter = newAdapter
        grid.setOnItemClickListener { _, _, position, _ -> launch(newAdapter.getItem(position)) }
        grid.setOnItemLongClickListener { _, _, position, _ ->
            menu.show(newAdapter.getItem(position))
            true
        }
        // The focus banner (0.17): the background follows the app under the
        // focus. D-pad movement fires onItemSelected; losing focus (Now Playing
        // block, "…" button) cleans up. The listeners stay installed, also in
        // other background modes — the mode flag decides inside them whether
        // anything happens. Switching in the settings then needs no rewiring.
        grid.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                showFocusBanner(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // The selection is gone (rare on a d-pad): in this mode that
                // means "no app under the focus". In other modes nothing
                // happens here — the image background stays.
                if (settings.current().background == BackgroundStyle.FOCUS) clearFocusBanner()
            }
        }
        grid.setOnFocusChangeListener { _, hasFocus ->
            if (settings.current().background != BackgroundStyle.FOCUS) return@setOnFocusChangeListener
            if (hasFocus) refreshFocusBanner() else clearFocusBanner()
        }
        adapter = newAdapter
    }

    /**
     * How many entries the adapter reports: one row or all.
     *
     * This is the entire difference between "collapsed" and "expanded" — there
     * is no second layout and no second view.
     */
    private fun rowLimit(): Int = if (expanded) 0 else columns

    /**
     * Collapsed, the grid is only one row high and sits with its column at the
     * bottom edge; expanded, it gets all the space above the "…" button and
     * scrolls. `layout_weight` instead of `match_parent`, because a
     * `match_parent` child in a vertical LinearLayout would push the button
     * below it off the screen.
     */
    private fun applyGridHeight() {
        val params = grid.layoutParams as LinearLayout.LayoutParams
        if (expanded) {
            params.height = 0
            params.weight = 1f
        } else {
            params.height = LinearLayout.LayoutParams.WRAP_CONTENT
            params.weight = 0f
        }
        grid.layoutParams = params
    }

    /** Label for screen readers — the glyph alone says nothing. */
    private fun applyMoreButton() {
        more.contentDescription =
            getString(if (expanded) R.string.less_label else R.string.more_label)
    }

    /** Expands the grid (everything) or collapses it (only the first row). */
    private fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        adapter?.submit(display, rowLimit())
        applyGridHeight()
        applyMoreButton()
        // When expanded the space belongs to the apps: the Now Playing block
        // disappears (it keeps its state — see NowPlayingPanel).
        nowPlayingPanel.refreshVisibility()
    }

    private fun refreshApps(force: Boolean) {
        // The app list belongs on the background thread: the query through the
        // PackageManager used to be the item that made a press of HOME slow.
        // Until 0.13 this also ran the question "which apps are still running?"
        // for the green label — that went away with the color.
        Background.io.execute {
            repository.refresh(force) { apps ->
                allApps = apps
                applyOrder(apps)
            }
        }
    }

    /**
     * Puts the apps into the configured order and hands them to the adapter.
     * The computation itself lives in [Order.arrange] (without Android types,
     * so it can be tested without a device).
     */
    private fun applyOrder(apps: List<AppEntry>) {
        display = Order.arrange(order.current(), apps) { it.packageName }
        val changed = adapter?.submit(display, rowLimit()) ?: false
        // Warm the icons: the next press of HOME then finds them ready in memory
        // or on disk instead of decoding them.
        if (changed || !iconsWarmed) {
            iconsWarmed = true
            iconLoader.warm(display, resources.getDimensionPixelSize(R.dimen.grid_icon))
        }
    }

    /**
     * Long press: move the app one position forward and save immediately.
     * This is the way to change the order **on the TV** (the other way is the
     * file, see docs/CONFIG.md).
     */
    private fun moveForward(entry: AppEntry) {
        if (order.moveForward(entry.packageName)) {
            shortToast(R.string.toast_order_moved)
            applyOrder(allApps)
        } else {
            shortToast(R.string.toast_order_first)
        }
    }

    /** Like [moveForward], but one position backward (context menu). */
    private fun moveBackward(entry: AppEntry) {
        if (order.moveBackward(entry.packageName)) {
            shortToast(R.string.toast_order_back)
            applyOrder(allApps)
        } else {
            shortToast(R.string.toast_order_last)
        }
    }

    private fun focusGrid() {
        // Try directly first: a `post` costs an extra pass through the message
        // loop, and after a press of HOME the view has long been measured. Only
        // the very first build needs the retry.
        if (!grid.requestFocus()) grid.post { grid.requestFocus() }
    }

    // ------------------------------------------------------------------
    // Clock and settings
    // ------------------------------------------------------------------

    /**
     * Applies the settings to the UI: clock on/off and background.
     *
     * Called on every `onResume` and every press of HOME — an `adb push` may
     * have changed the file. That is why every action here is **cheap when
     * nothing has changed**: the clock is only switched when it is in the wrong
     * state, and the background is only reloaded when a different file is
     * selected.
     */
    private fun applySettings() {
        applyClock()
        applyBackground()
    }

    /**
     * The clock: visible **and** ticking — or off, and then without a ticker
     * too.
     *
     * The ticker is the reason "clock off" is not just `visibility = GONE`: a
     * timer that writes to an invisible field every 30 seconds is measurable on
     * the Chromecast (same rule as for the Now Playing block).
     */
    private fun applyClock() {
        val show = settings.current().showClock
        clock.visibility = if (show) View.VISIBLE else View.GONE
        if (show) clockTicker.start() else clockTicker.stop()
    }

    private fun stopClock() = clockTicker.stop()

    // ------------------------------------------------------------------
    // Background (0.14)
    // ------------------------------------------------------------------

    /**
     * The background — three options ([BackgroundStyle], see docs/CONFIG.md).
     * If the same state is already set, **nothing** happens: this call arrives
     * on every press of HOME, and decoding an image again on every press would
     * be exactly the delay you can see on a TV.
     */
    private fun applyBackground() {
        val current = settings.current()
        val file = BackgroundPlan.file(
            style = current.background,
            configured = current.backgroundFile,
            externalDir = getExternalFilesDir(null)?.absolutePath,
        )
        val key = "${current.background}|${file?.absolutePath.orEmpty()}"
        if (key == appliedBackground) return
        appliedBackground = key
        when (current.background) {
            BackgroundStyle.DARK -> showDarkBackground()
            BackgroundStyle.IMAGE -> showImageBackground(file)
            // Focus: dark base; the banner comes from the focus itself. If the
            // focus is already on an app, the banner shows up immediately —
            // otherwise only with the next focus change.
            BackgroundStyle.FOCUS ->
                if (grid.hasFocus()) refreshFocusBanner() else clearFocusBanner()
        }
    }

    /** No background: the layer off — the theme paints `@color/bg_home`. */
    private fun showDarkBackground() {
        backgroundImage.setImageDrawable(null)
        backgroundImage.visibility = View.GONE
        backgroundScrim.visibility = View.GONE
    }

    /**
     * An image as the background.
     *
     * Decoding happens on the **background thread**, downscaled to screen size
     * ([decodeScaled]): a 1080p photo would otherwise be 8 MB, a 4K photo 33 MB
     * of memory for a surface a TV cannot resolve from three meters away. If
     * the file is missing or not an image, the screen stays dark — the settings
     * screen names the path; a message on the home screen would be noise.
     */
    private fun showImageBackground(file: File?) {
        backgroundScrim.visibility = View.GONE
        backgroundImage.setImageDrawable(null)
        backgroundImage.visibility = View.GONE
        if (file == null) return
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        Background.io.execute {
            val bitmap = decodeScaled(file, width, height)
            Background.main.post {
                if (bitmap == null) return@post
                backgroundImage.setImageBitmap(bitmap)
                backgroundImage.visibility = View.VISIBLE
                backgroundScrim.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Decodes an image file to at most `maxWidth` × `maxHeight`.
     *
     * `inJustDecodeBounds` first: the dimensions are in the file header; a full
     * decode just to measure would be exactly the waste these two lines avoid.
     */
    private fun decodeScaled(file: File, maxWidth: Int, maxHeight: Int): Bitmap? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth &&
            bounds.outHeight / (sample * 2) >= maxHeight
        ) {
            sample *= 2
        }
        return try {
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        } catch (e: OutOfMemoryError) {
            // An oversized image must not kill the launcher: stay dark.
            null
        }
    }

    // ------------------------------------------------------------------
    // Focus banner (0.17)
    // ------------------------------------------------------------------

    /**
     * The banner of the app **currently** under the focus — the call after a
     * focus change back to the grid, when `onItemSelected` does not fire again
     * (the selection has not changed, after all).
     */
    private fun refreshFocusBanner() {
        val position = grid.selectedItemPosition
        if (position == AdapterView.INVALID_POSITION) {
            clearFocusBanner()
            return
        }
        showFocusBanner(position)
    }

    /**
     * The TV banner (`android:banner`) of the app at this position as the
     * background — the same artwork the system launcher shows behind the
     * focused app. If the app has no banner, the screen stays dark; the mode
     * flag decides both before loading **and** in the callback, so that a
     * switch (or `adb push`) can no longer put a running decode on screen.
     */
    private fun showFocusBanner(position: Int) {
        val adapter = adapter ?: return clearFocusBanner()
        if (position < 0 || position >= adapter.count) return clearFocusBanner()
        if (settings.current().background != BackgroundStyle.FOCUS) return
        val entry = adapter.getItem(position)
        val key = entry.packageName
        backgroundImage.tag = key
        // The same flag (`tag`) as for the icons: the decode can come back while
        // the focus has long moved elsewhere.
        val cached = bannerLoader.get(entry) { bitmap ->
            if (backgroundImage.tag == key &&
                settings.current().background == BackgroundStyle.FOCUS
            ) {
                showBanner(bitmap)
            }
        }
        if (cached != null) showBanner(cached) else clearFocusBanner()
    }

    private fun showBanner(bitmap: Bitmap) {
        backgroundImage.setImageBitmap(bitmap)
        backgroundImage.visibility = View.VISIBLE
        backgroundScrim.visibility = View.VISIBLE
    }

    /**
     * Back to the dark base: no app under the focus, no banner layer. The same
     * surface as [showDarkBackground] — but the flag (`tag`) must go too,
     * otherwise a late decode would still end up on screen.
     */
    private fun clearFocusBanner() {
        backgroundImage.tag = null
        showDarkBackground()
    }

    /** Long press of OK on "…": the settings (its own activity, see there). */
    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    // ------------------------------------------------------------------
    // NowPlayingPanel.Listener — what the block needs from the home screen
    // ------------------------------------------------------------------

    /**
     * The block is visible only when it is enabled **and** the grid is
     * collapsed. When expanded, the space belongs to the apps.
     */
    override fun isEnabled(): Boolean = settings.current().nowPlaying && !expanded

    /** The entry of an app: first from the display list, then from the full list. */
    override fun entryOf(packageName: String): AppEntry? =
        display.firstOrNull { it.packageName == packageName }
            ?: allApps.firstOrNull { it.packageName == packageName }

    /**
     * OK on a Now Playing row: back into the app that is making the sound.
     *
     * Two routes, in this order:
     *
     *  1. The session's `sessionActivity` — the "Now playing" screen the app
     *     **itself** offers (`PendingIntent`). That is the intended route: it
     *     lands where the user can keep listening, not in the app's main menu.
     *  2. Otherwise the launcher intent, exactly as from the grid ([launch]).
     *
     * Without a session the row does nothing instead of starting anything.
     */
    override fun openRow(packageName: String, sessionActivity: PendingIntent?) {
        if (sessionActivity != null) {
            try {
                homeOnScreen = false
                sessionActivity.send()
                return
            } catch (e: Exception) {
                // Expired, already used, or blocked by its creator: then the
                // normal route.
            }
        }
        val entry = entryOf(packageName)
        if (entry != null) {
            launch(entry)
        } else {
            shortToast(R.string.toast_launch_failed)
        }
    }

    /** The block disappears (expanded): the focus belongs back in the grid. */
    override fun onFocusLost() {
        focusGrid()
    }

    /**
     * The block reports its height; the grid starts below it.
     *
     * Since 0.13 it can be **several** rows tall (one per app with an active
     * media session). Without this feedback the first app row would sit behind
     * the second Now Playing row: the block lies in the `FrameLayout` above the
     * grid, and that only knows its fixed top edge.
     */
    override fun onHeightChanged(heightPx: Int) {
        val needed = if (heightPx <= 0) {
            baseGridTopPadding
        } else {
            max(baseGridTopPadding, heightPx + topRowPaddingTop + nowPlayingGap)
        }
        if (needed == grid.paddingTop) return
        grid.setPadding(grid.paddingLeft, needed, grid.paddingRight, grid.paddingBottom)
    }

    // ------------------------------------------------------------------
    // AppMenu.Listener — the context menu
    // ------------------------------------------------------------------

    override fun onMoveForward(entry: AppEntry) {
        moveForward(entry)
    }

    override fun onMoveBackward(entry: AppEntry) {
        moveBackward(entry)
    }

    /** Close all background apps — the same limit as [AppMenu.close]. */
    override fun onCloseAll() {
        val manager = getSystemService(ActivityManager::class.java) ?: return
        for (entry in allApps) {
            manager.killBackgroundProcesses(entry.packageName)
        }
        shortToast(R.string.toast_closed_all)
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private fun launch(entry: AppEntry) {
        // We are leaving the home screen — even when the app being launched is
        // transparent and only pauses us (onStop would not fire then).
        homeOnScreen = false
        lastApp.remember(entry.packageName)

        // Explicit component + MAIN/LAUNCHER: exactly what the LauncherApps
        // system service does internally. The category is meaningless with an
        // explicit component, so this also starts leanback-only apps.
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(entry.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            shortToast(R.string.toast_launch_failed)
        }
    }

    private companion object {
        /** Once a minute is enough: a clock with seconds would be a timer per
         *  second — measurable on the Chromecast, with no benefit. */
        const val CLOCK_INTERVAL_MS = 30_000L
    }
}
