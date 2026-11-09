# Architecture

Goal: **minimal UI, maximum performance** on a
Chromecast HD (1.4 GB RAM, 32-bit ABI, Android 14). Every decision below
follows from this premise — and, wherever there is a number, from a measurement.

## One state, one Activity

```
HOME press  →  bottom row with the first apps  +  "…" on the row below
                    │
                    └── OK on "…"  →  the full grid (scrollable), "…" folds back
```

One Activity, one window, no ambiguity: two HOME Activities in one
app would make the system's resolution ambiguous (a selection dialog instead of
a direct launch).

There is **no second state** — "collapsed" and "expanded" differ only in the
amount the adapter reports (`limit`) and in the height of the
grid. No second layout, no second window, no animation.

## What is no longer in the Activity since 0.13

Until 0.12, the home screen was **one** file of 769 lines: grid, clock, green
text, Now-Playing block and context menu in a single class. It is now 789
lines (as of 0.17.0) — not because there is less code, but because the
pieces have their own names:

| Piece | Where | Why separated |
|---|---|---|
| Clock tick (clock · green · Now Playing) | [Ticker](../app/src/main/java/com/piotv/navigate/ui/Ticker.kt) | three `Handler`s and three `Runnable`s were the same code three times with a different interval |
| the Now-Playing lines | [NowPlayingPanel](../app/src/main/java/com/piotv/navigate/ui/NowPlayingPanel.kt) | live per app plus a last-seen line, persistence, height reporting to the grid |
| the split of the history | [LastSeen](../app/src/main/java/com/piotv/navigate/ui/LastSeen.kt) | one line visible, the older ones in the menu — pure computation, verifiable without a device |
| the menu of older entries | [HistoryMenu](../app/src/main/java/com/piotv/navigate/ui/HistoryMenu.kt) | knows only the dialog — which entries are older is known to the panel |
| the focus banner | [BannerLoader](../app/src/main/java/com/piotv/navigate/ui/BannerLoader.kt) | knows only cache and decoding — when it is shown is decided by the Activity |
| context menu | [AppMenu](../app/src/main/java/com/piotv/navigate/ui/AppMenu.kt) | knows only dialog, intents and toasts — the grid state is known to the caller |

The Activity continues to hold the **one** state (grid, order, focus) and,
as the `Listener`, answers the questions of the extracted pieces: "is the field
switched on?", "what is this app called?", "how tall are you?". The same
separation as between `AppAdapter` and the home screen — only with a listener
instead of reaching back in.

## The window: opaque, and why that was the real fix

Until 0.6, the theme was translucent (so that no black flash would appear on a
HOME press) and set all window animations to `@null`. On the target device the
window was therefore:

```
mAttrs={(0,0)(fillxfill) … fmt=TRANSPARENT}      mHasSurface=true
mDrawState=HAS_DRAWN   mLastHidden=false   Surface: shown=true alpha=1.0
input_focus: [Focus entering … LauncherActivity]   ← operable!
```

… and yet **not visible**: only on a click did something flash up briefly.
Input arrived (the user could launch apps "blind"), the content stayed
invisible. All SurfaceFlinger layers reported `alpha=1.000000`.

What the case shows: a **translucent window keeps the app below it alive**
(it is only paused, its window remains), and every app visible on this TV
has an **opaque** window, one in which the app below is stopped. The
correlation was unmistakable, the mechanism behind it not provable
(the device's video/DRM layers). 0.7 draws the only solid conclusion
from this: the most ordinary theme there is.

```xml
<style name="Theme.Navigate" parent="@android:style/Theme.Material.NoActionBar">
    <item name="android:windowBackground">@color/bg_home</item>
    <item name="android:colorBackground">@color/bg_home</item>
</style>
```

No `windowIsTranslucent`, no `windowDisablePreview`, no custom
`windowAnimationStyle`, no window-size tricks. `windowBackground` is
the same shade as the home screen — the starting window is thus a dark
rectangle instead of a white flash.

> **What is hypothesis here:** that it was precisely the translucency that
> caused it is inferred from the correlation, not proven. What is proven: the
> window was drawn, focused, operable, and invisible.

## First row and "…": one view, no rebuild

The structure is a vertical `LinearLayout` column hanging at the **bottom**
edge:

```
┌──────────────────────────────────┐
│                          [Clock] │
│                                  │   ← empty (collapsed)
│  [App][App][App][App][App][App]  │   ← the grid, one row tall when collapsed
│                          [ ••• ] │   ← the row below: the "…" button
└──────────────────────────────────┘
```

- **Collapsed:** `limit = columns` — the adapter reports exactly one row; the
  grid is `wrap_content` tall and hangs at the bottom with the column. The "…"
  button sits in the row below, right-aligned under the last cell (same
  margins as the grid).
- **Expanded:** `limit = 0` (all apps), the grid gets the whole space above
  the button via `layout_weight = 1` and scrolls. The button
  stays in the same place.

`layout_weight` instead of `match_parent`, because a `match_parent` child in a
vertical `LinearLayout` would push the button below out of view.

## The selection highlight

Two things that were visible on the device and should not stay that way:

- **The orange selection background** came from the **theme** (`listSelector`),
  not from the layout: `android:listSelector="@null"` does not switch it off.
  That is why `@android:color/transparent` now stands in the layout —
  explicitly, with the reason noted at the spot.
- **The rounded blue border stays** — it is the highlight, and it comes
  from a single drawable (`bg_grid_item.xml`: `state_selected` →
  `item_focus_fill` + 2dp `item_focus_stroke`, 12dp radius). The "…"
  button uses the same language (`bg_more_item.xml`, there on
  `state_focused`, because it is a real View).

`HomeGridView` additionally clears the highlight when the focus leaves the
grid (GridView leaves `state_selected` set): otherwise a cell would stay
framed in blue while the focus is on the "…" button.


## "Back to the last app" — and why it needs a marker

On the already visible home screen, HOME and BACK bring back the last used
app. The difference between a "first HOME press" (show the home screen) and a
"second HOME press" (back into the app) hinges on `homeOnScreen`: set in
`onResume`, cleared in `onStop` — and additionally when an app is launched
from this UI (a translucent app would only pause us, so `onStop` would not
come).

Why no lifecycle flag: the system pauses the Activity on **every** HOME
press, *before* it delivers the intent (measured: onPause → onNewIntent →
onResume). `isResumed` is already `false` there — this is exactly what the
first version of this behavior failed on in 0.1.

Where "the last app" comes from ([LastApp](../app/src/main/java/com/piotv/navigate/data/LastApp.kt)),
in this order:

1. **Usage stats** (`queryEvents`, a 5-minute window, otherwise 6 hours):
   exact, but it needs the app op `GET_USAGE_STATS`. Two windows because it is
   not the query that is expensive but the **event set** — on a used device, a
   6-hour window contains thousands of events.
2. **Marker** (`SharedPreferences`): the app last launched via Navigate,
   discarded after a system boot. Without access, this is the honest
   approximation — and `null` when nothing is known; then the home screen
   stays put.

Why not `ActivityManager.getRunningTasks`: since Android 5 this returns to
normal apps only their own tasks — useless for a launcher.

The folding of the events (`ForegroundLog.lastResumed`) is pure logic without
Android types and is tested; `LastApp` itself is wiring and only runs on a
device.

## Green text: what "active" means

The app labels are green as long as the app is **active**. What "active"
means on this device was measured, not assumed.

Because our window is **opaque**, the platform stops the app below it as soon
as the home screen appears — its Activity receives `ACTIVITY_STOPPED`. Its
**process**, however, stays alive. Re-measured right after a HOME press:

```
*APP* UID 10101 ProcessRecord{… com.netflix.ninja/u0a101}
*APP* UID 10102 ProcessRecord{… com.disney.disneyplus/u0a102}
*APP* UID 10106 ProcessRecord{… com.wbd.stream/u0a106}
*APP* UID 10064 ProcessRecord{… com.android.vending/u0a64}
```

The right question is therefore not "is the Activity running?" (answer: no)
but **"was the app last in the foreground?"** — and the usage stats answer
that with a timestamp per package (`UsageStats.getLastTimeUsed`):

- green = in the foreground within the last **24 hours**, never before the
  last boot ([LastApp.recentlyUsed](../app/src/main/java/com/piotv/navigate/data/LastApp.kt));
- the query runs on a background thread and at most every 30 seconds — a
  6-hour window on the HOME path would be exactly the bottleneck that 0.2
  removed from there;
- without usage access, the last known state stays in place (the first time:
  an empty set — nothing green instead of wrongly green).

To be honest about it: "process alive" is an **approximation**. An app used
23 hours ago and since then killed by the system is green — the window is the
compromise between "everything active" and "nothing wrong".

## "Now Playing": current per app plus history, everything jumpable

Top left, in the collapsed state, are the running sessions — the **current
one per app**, each as **App · Artist – Title**, plus `· paused` and the
cover art. Below it, **one** history line from `history.conf`: the newest
entry not currently playing ("Last seen"), dimmed. Everything older sits
behind a **long OK press on this line** — the context menu
([HistoryMenu](../app/src/main/java/com/piotv/navigate/ui/HistoryMenu.kt))
unfolds the older entries and brings back the selected app; the block
does not grow with the archive. The block is the only part of the home
screen that does anything other than "launch this app": **OK on a line
brings back exactly that app** (in the history: the app itself).

Until 0.12 it was **one** line: the session that "has the sound". That was a
choice nobody had made — with an audiobook paused next to a radio station,
one of the two pieces of information disappeared.

Where the information comes from:

| Information | Source |
|---|---|
| Title, artist, state | the session's `MediaMetadata` (`METADATA_KEY_TITLE`/`_ARTIST`) and `PlaybackState` |
| Cover art | `MediaMetadata.getBitmap()`: first `METADATA_KEY_ALBUM_ART`, then `_ART`, then `_DISPLAY_ICON`. That is all the API offers — the `*_URI` variants exist too, but they require a content resolver for an image that usually comes along anyway |
| App name | **not** from the session but from our own app list (`sessionActivity` package → `AppEntry.label`): the same names as in the grid |
| which session belongs to an app | the **playing** one, otherwise the first with a title ([NowPlaying.active](../app/src/main/java/com/piotv/navigate/data/NowPlaying.kt)) |
| where to return to | `MediaController.getSessionActivity()` — the "now playing" screen the app itself provides. If it does not set one, its launcher intent applies, as from the grid |

Three decisions that depend on this:

1. **Without notification access there is no field.** `getActiveSessions` is
   blocked for normal apps; [NowPlayingListener](../app/src/main/java/com/piotv/navigate/NowPlayingListener.kt)
   is the credentials — an empty service that reads nothing. The resulting
   `SecurityException` is swallowed, the block stays empty: the same stance
   as with "without usage access nothing is green".
2. **The cover art lives in an LRU (4 MB).** Without the cache, the same image
   would be fetched and scaled every 5 seconds (a 1000×1000 cover is 4 MB);
   the key is the **song**, not the bitmap object — every query returns a
   new one. Until 0.12 that was **one** slot (there was only one line); with
   a line per app the cache holds several. If a cover is missing, the app
   icon from our own icon cache stands there.
3. **No callback channel.** No `addOnActiveSessionsChangedListener`, no
   `MediaController.Callback`: the home screen polls on the beat of its own
   timer (5 s, only when visible and collapsed). A listener would be more
   precise and would be extra state — not worth it for a few lines with
   cover art.

**When a line changes** — the rule lives in
[NowPlayingBoard](../app/src/main/java/com/piotv/navigate/data/NowPlayingBoard.kt):

- **One line per app.** Two sessions of the same app make one line (the
  playing one wins), an app without title and artist makes none.
- **It stays as it is.** The comparison is the finished text: if it is
  equal, nothing is written and no new cover is fetched — otherwise every
  tick would reset the display (visible on the device as flicker).
- **Only an explicit end removes it.** Three things are
  kept apart: a new **title** replaces the old one — the old one moves
  into the history in the process ([HistoryStore](../app/src/main/java/com/piotv/navigate/data/HistoryStore.kt));
  a session **without a title** (loading screen, ads) or **no** session
  at all (app in the background, title change) leaves the line standing; only
  `STOPPED`/`ERROR` — twice in a row, i.e. ten seconds — removes it. On
  the target device, WOW's session after the film was **gone entirely**,
  without ever reporting
  `STOPPED`: the first version of this rule (0.13.2) failed on exactly
  that, the entry vanished after a few seconds.
- **A failed look deletes nothing.** [NowPlaying.active] then returns
  `null` instead of an empty list — "I don't know" is something different
  from "nothing is playing".
- **The history is the fourth answer — as one line plus an archive.** Live lines
  first, below them the **newest** entry from `history.conf` that is not
  currently playing; the file holds at most 500 entries, and of them exactly
  one is shown — everything older sits behind a long OK press on this line
  ([HistoryMenu](../app/src/main/java/com/piotv/navigate/ui/HistoryMenu.kt)).
  The block is at most 260 dp tall, after that it scrolls within itself — the
  grid does not slide down. (In 0.15.0 this limit was **ineffective**; why,
  is explained in the next section.)
- **The price, to say it openly:** an app that loses its session without
  `STOPPED` keeps its line until the launcher process is restarted (on 1.4 GB
  the normal case after a few hours). That is the deliberate decision: a line
  you are just reading must not vanish while you read it.

The rules live in
[NowPlayingText](../app/src/main/java/com/piotv/navigate/data/NowPlayingText.kt),
NowPlayingBoard, [HistoryCodec](../app/src/main/java/com/piotv/navigate/data/HistoryCodec.kt),
[HistoryStore](../app/src/main/java/com/piotv/navigate/data/HistoryStore.kt)
and [LastSeen](../app/src/main/java/com/piotv/navigate/ui/LastSeen.kt) and
know no Android — the same separation as for the
order ([Order](../app/src/main/java/com/piotv/navigate/data/Order.kt)):
what is tested is the rule (164 unit tests, without a device), not the device's quirks.

## The window above the lines — and why the arrow keys need a rule of their own

The block top left is the only place on the home screen that **has more lines
than it shows**. Three things follow from that — and all three were wrong in
0.15.0:

1. **`android:maxHeight` does not exist on `ScrollView`.** The platform knows
   the attribute only for `ImageView`, `ExpandableListView` and the internal
   `MaxHeightFrameLayout`; `<declare-styleable name="ScrollView">` contains
   nothing but `fillViewport`. aapt2 only checks that the attribute **exists** —
   it is silently ignored, and the block grew with the history: 40 entries at
   52 dp each are 2080 dp on a 540 dp screen. [PanelScrollView](../app/src/main/java/com/piotv/navigate/ui/PanelScrollView.kt)
   therefore reads the limit as its own attribute (`app:maxHeight`, declared in
   `res/values/attrs.xml`) and applies it in `onMeasure`.
2. **The grid must not be given the height of the content.** `grid_padding_top`
   comes from the block's feedback (`NowPlayingPanel.Listener.onHeightChanged`).
   What is reported is the height of the **ScrollView** — the window —, not that
   of the inner column with all the lines: otherwise the app row would sit
   behind 500 history entries at the bottom edge of the screen.
3. **The focus finder knows nothing about clipping.** `FocusFinder` works with
   the layout rectangles: the lines below the window geometrically sit
   where the grid starts. A `▲` from the grid therefore landed on a
   line nobody sees — the highlight was outside the picture, and the
   control felt broken without being so.

What follows from this:

- **The scrollbar stays up permanently** (`fadeScrollbars="false"`, its own
  grip, `insideInset`). Here it is no ornament but the only indication
  that the history continues and where you are in it.
- **`▲`/`▼` move the focus line by line** ([PanelScrollView](../app/src/main/java/com/piotv/navigate/ui/PanelScrollView.kt),
  `dispatchKeyEvent`): the next line in the tree, scrolled **beforehand** into
  the window. At the start and end of the list the key is **not** consumed —
  there the framework's answer is right (`▼` on the last line leads
  into the grid, `▲` on the first stays put).
- **Clipped lines are not focus candidates** (`addFocusables` asks
  `RowWindow.isVisible`). Our own focus change calls `requestFocus()` directly
  and still reaches them — they are already scrolled in by then.
- **The computation is a pure rule.** [RowWindow](../app/src/main/java/com/piotv/navigate/ui/RowWindow.kt)
  says when a line is fully visible and where one must scroll to —
  the same computation as `ScrollView.computeScrollDeltaToGetChildRectOnScreen`,
  but without Android types and therefore verifiable without a device
  (`RowWindowTest`, nine unit tests).


## What can be verified without a device (and what cannot)

| | Examples | Verifiable |
|---|---|---|
| **pure rules**, no Android type | `AppCatalog`, `Order`, `OrderCodec`, `OrderStore` (file semantics), `NowPlayingBoard`, `NowPlayingText`, `HistoryCodec`, `HistoryStore` (file semantics), `LastSeen`, `BackgroundPlan`, `SettingsCodec`, `GridColumns`, `IconGeometry`, `IconCacheKeys`, `RowWindow`, `BannerGeometry` | unit test without a device — 164 unit tests; the classes verifiable without a device sit at 100 % line coverage. The number itself is not prose: `tools/check_docs_drift.sh` compares it against the built test set |
| **Android wiring** | `LauncherActivity` (incl. background layers), `NowPlayingPanel`, `PanelScrollView`, `AppMenu`, `HistoryMenu`, `Ticker`, `AppAdapter`, `HomeGridView`, `IconLoader`, `IconDiskCache`, `BannerLoader`, `AppRepository`, `NowPlaying` | only on a device; here the measurement counts ([DEVICE_PERF.md](DEVICE_PERF.md)) |

The reason is the error history: the most expensive mistakes of this project were
**rules**, not Android quirks. Such rules therefore live in an
`object` without Android imports and can be verified in milliseconds instead
of in a 15-minute build plus a TV.

Three gates keep this in place over time: Lint (`warningsAsErrors`), the coverage gate
(`unitTestCoverageVerification`) and the docs gate (`tools/check_docs_drift.sh`).

## Decisions that have stayed

- **No runtime dependencies.** `GridView`, `LauncherApps`, `LruCache`,
  an executor, a handler — all framework. No AndroidX, no Compose,
  no coroutines, no Hilt. What is missing costs nothing.
- **Two PackageManager queries instead of one.** `LauncherApps` knows only
  `CATEGORY_LAUNCHER`; Prime Video, WOW and ARD carry only
  `CATEGORY_LEANBACK_LAUNCHER` and were therefore missing entirely. The union of
  both categories is the reason for `QUERY_ALL_PACKAGES`.
- **App list and icons are precomputed.** The list is held as a
  process singleton, each decoded icon additionally as a small WebP file
  in app-internal storage. After a process restart (on a 1.4 GB device
  the normal case) no foreign APK has to be mapped anymore.
- **The settings are the configuration files** ([OrderStore],
  [SettingsStore], see [CONFIG.md](CONFIG.md)) — via long press or in the
  settings on the TV, or via `adb push`. No
  `SharedPreferences` grab-bag: the marker of the last launched app is
  runtime state, a setting is a file.

## What is gone compared with 0.1

| Item (0.1) | today |
|---|---|
| Foreground query in the usage stats (on **every** HOME press) | gone — there is no second state anymore that needed it |
| Building the bottom row (favorites lookup, 9 × layout inflation) | gone — the bottom row now **is** the start of the grid |
| Setting the window size on every HOME press | gone — the window is always the same full-screen frame |
| Icons from foreign APKs on every bind or process start | unchanged since 0.2: memory → disk → resources |

What remains and cannot be optimized away: one measure/layout pass over the grid
and the first frame.
