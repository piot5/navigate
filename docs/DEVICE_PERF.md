# Device metrics — Chromecast HD

All values measured with the release APK (`assembleRelease`, R8 + resource
shrinking) on the target device, not estimated.

## Device

| | |
|---|---|
| Model | Chromecast HD (Google TV) |
| Android | 14 (API 34) |
| CPU/ABI | Amlogic, `armeabi-v7a` (32-bit) |
| Display | 1920 × 1080 at density 320 → **960 × 540 dp** |
| RAM | 1,439,332 kB total, ~200 MB available in operation |
| Heap limit | 256 MB |

## 0.17.0 (Focus banner · clock at the very top)

| | 0.16.0 | 0.17.0 |
|---|---|---|
| Release APK (signed) | 84,234 B | **85,654 B** |
| Release APK (unsigned) | 75,808 B | **77,224 B** |
| `classes.dex` | 85,612 B | **89,044 B** |
| Files in the APK | 33 | **33** |
| Unit tests | 161 | **164** |
| Coverage (lines / branches) | 25.7 % / 35.7 % | **24.8 % / 34.6 %** |

The background gets its third kind: **focus** — the TV banner
(`android:banner`) of the app under the focus fills the space behind the
grid, apps without a banner leave it dark. `ui/BannerLoader.kt` loads it like
the icons (memory cache, background thread), `ui/BannerGeometry.kt` caps the
target size at 1280 px width (3 new tests); the wiring (focus and selection
listeners on the grid, a flag against late decodes) lives in
`LauncherActivity`. Since 0.17 the clock sits at the very top of the row
instead of centered next to the "Last seen" content (`layout_gravity="top"`
— one layout line).

The Dex grows by ~3.4 KB: the banner loader, the geometry rule and the focus
wiring. The APK grows by ~1.4 KB — R8 strips out part of the new classes
again, the resources (the string, the layout attribute) stay. Coverage drops
by 0.9 and 1.1 points respectively, because the denominator grows by the
Android classes that can't be tested without a device (`BannerLoader`, the
wiring) — the numerator grows only by `BannerGeometry`. The gate value sits
at 18 % / 25 % and is still met.

**Not re-measured** (carried over unchanged, see below): start time and
memory. The focus banner loads afterwards — it doesn't touch the HOME path;
the memory cache is capped at 12 MB.

## 0.16.0 (History: one row, archive in the context menu)

| | 0.15.1 | 0.16.0 |
|---|---|---|
| Release APK (signed) | 84,196 B | **84,234 B** |
| Release APK (unsigned) | 75,764 B | **75,808 B** |
| `classes.dex` | 85,920 B | **85,612 B** |
| Files in the APK | 33 | **33** |
| Unit tests | 157 | **161** |
| Coverage (lines / branches) | 25.7 % / 35.7 % | **25.7 % / 35.7 %** |

The history is no longer a list under the live rows but **one** row — the
newest entry that isn't currently running — plus an archive behind a long
press of OK on that row (`ui/HistoryMenu.kt`, the same framework dialog as in
the grid). The split rule lives as pure computation in `ui/LastSeen.kt`
(5 new tests); together with `page()` and `PAGE_SIZE` the paged reload path
drops out of the panel (`historyShown`, `loadMoreIfNeeded`, the focus
listener on the last history row).

The Dex shrinks by 308 B: R8 throws out the reload logic, and the two new
classes together cost less than what goes away. The APK grows by
38 B (signed) or 44 B (unsigned) — the dialog resources and the new string.
Coverage stays put (25.7 % / 35.7 %): the tested `page()` lines drop out of
numerator and denominator, the tested `LastSeen` lines come in — the ratio
rounds to the same value.

**Not re-measured** (carried over unchanged, see below): start time and
memory. The HOME path now draws fewer rows than before — the change can only
help there, and the history is kept in memory anyway and never turned into
views all at once.

## 0.15.1 (Now-playing block: a window instead of a growing block)

| | 0.15.0 | 0.15.1 |
|---|---|---|
| Release APK (signed) | 82,329 B | **84,196 B** |
| Release APK (unsigned) | 69,960 B | **75,764 B** |
| `classes.dex` | 84,068 B | **85,920 B** |
| Files in the APK | 32 | **33** |
| Unit tests | 148 | **157** |
| Coverage (lines / branches) | 26.0 % / 36.5 % | **25.7 % / 35.7 %** |

Two bugs that together made the arrow keys hit nothing once the history grew
(reported on the target device):

1. **`android:maxHeight` has no effect on the `ScrollView`.** The platform
   knows the attribute only for `ImageView`, `ExpandableListView` and the
   internal `MaxHeightFrameLayout`; `<declare-styleable name="ScrollView">`
   contains only `fillViewport`. aapt2 lets it through anyway — the attribute
   *exists* — but the ScrollView doesn't read it. The view therefore grew
   with the history: at 40 entries of 52 dp each the block was 2080 dp tall,
   and the screen has 540 dp.
2. **The grid was given the height of the content, not that of the window.**
   `NowPlayingPanel` hung off `container` (all rows) instead of the ScrollView
   (the part that is visible). `grid.setPadding(top = …)` thus pushed the app
   row below the screen edge — the controls stayed intact, but invisible.

The change: `ui/PanelScrollView.kt` (height cap via its own attribute
`app:maxHeight` in `res/values/attrs.xml`), an always-visible scrollbar
(`scrollbar_thumb`, `insideInset`), and the arrow keys walk the block **row by
row** — `▲`/`▼` move the focus to the next row and scroll it into the window
first; cut-off rows are no longer focus candidates (`addFocusables`). The
computation lives as a pure rule in `ui/RowWindow.kt`.

The APK grows by ~1.9 KB, the Dex by ~1.8 KB: the new view class, the
attribute and the scrollbar resources. Coverage drops slightly and **only**
because the denominator grows: `PanelScrollView` sits in it with 48 lines
that can't run without a device (like `NowPlayingPanel`, `HomeGridView`,
`AppAdapter` — they need a UI, not a coverage tool). `RowWindow` is fully
covered at 11 lines.

**Not re-measured** (carried over unchanged, see below): start time and
memory. The change doesn't touch the HOME path — it caps one view and
answers two key presses.

## 0.15.0 (Now-playing history)

| | 0.14.1 | 0.15.0 |
|---|---|---|
| Release APK (signed) | 78,312 B | **82,329 B** |
| `classes.dex` | 76,040 B | **84,068 B** |
| Files in the APK | 32 | **32** |
| Unit tests | 120 | **148** |
| Coverage (lines / branches) | 22.9 % / 32.4 % | **26.0 % / 36.5 %** |

The history (`history.conf`, paged on reload, at most 500 entries) costs
~3.8 KB of APK and ~7.5 KB of Dex: three new files (`HistoryEntry`,
`HistoryCodec`, `HistoryStore`), the carryover rule in the board and the
history rendering in the panel plus the ScrollView in the layout. Coverage
rises because the new rules are fully tested (18 new tests) — the
denominator grows by tested lines.

> The ScrollView from 0.15.0 was **capped without effect**: its
> `android:maxHeight` was ignored (see 0.15.1). As early as 0.15.0 the
> history spanned the whole screen once enough titles had run through.

**Not re-measured** (carried over unchanged, see below): start time and
memory. The history is written on a background thread and read page by page
— the HOME path only touches the in-memory state.

## 0.14.1 (video background removed · background image swapped)

| | 0.14.0 | 0.14.1 |
|---|---|---|
| Release APK (signed) | 79,232 B | **78,312 B** |
| Release APK (unsigned) | 70,876 B | **69,960 B** |
| `classes.dex` | 77,848 B | **76,040 B** |
| Files in the APK | 32 | **32** |
| Unit tests | 120 | **120** |
| Coverage (lines / branches) | 22.5 % / 32.2 % | **22.9 % / 32.4 %** |

The **video background** is gone: the third value `background=video`, the
`VideoView` layer in the layout and the two methods for it drop out. This
makes the APK smaller (R8 throws the video path out with it), and coverage
rises slightly because fewer untested Activity branches sit in the
denominator. The background image `background.jpg` was replaced with a new
image — same path, different content, so no metric.

## 0.14.0 (no green text · clock switchable · three backgrounds)

| | 0.13.3 | 0.14.0 |
|---|---|---|
| Release APK | 76,481 B | **79,232 B** |
| `classes.dex` | 73,652 B | **77,848 B** |
| Files in the APK | 32 | **32** |
| Unit tests | 111 | **120** |
| Coverage (lines / branches) | 21.2 % / 29.3 % | **22.5 % / 32.2 %** |

Three changes that show up in the numbers:

1. **No more green text.** `text_active` and `text_last` are gone from
   `colors.xml`, `AppAdapter` no longer sets any label color at all
   (the color lives in `item_app.xml`), and the tick that refreshed the
   coloring every 5 seconds is gone — along with `LastApp.recentlyUsed()`.
   The APK still grows: the two new features below are more code than
   what fell away.
2. **The clock can be switched off** (`show_clock`) — and "off" also means:
   no tick.
3. **Three backgrounds** (`background=dark|image|video`): an image is decoded
   scaled down, a video runs muted in a loop. Both cost code
   (`BackgroundPlan`, `decodeScaled`, the two layers in the layout) — hence
   the growth of ~2.7 KB APK and ~4.2 KB Dex.

**Not re-measured** (carried over unchanged, see below): start time and
memory. The changes don't touch the HOME path — the background is set once,
the image decoded once.

What the new backgrounds **cost** would have to be measured honestly: a video
occupies a hardware decoder on this device for as long as the home screen is
visible. The default is therefore `dark`, and leaving the home screen pauses
it (`onPause`).

## 0.13.3 (the entry stays put)

| | 0.13.0 | 0.13.3 |
|---|---|---|
| Release APK | 76,187 B | **76,481 B** |
| `classes.dex` | 73,112 B | **73,652 B** |
| Files in the APK | 32 | **32** |
| Unit tests | 108 | **111** |
| Coverage (lines / branches) | 20.8 % / 28.9 % | **21.2 % / 29.3 %** |

Fixed: **the Now Playing entry vanished after a few seconds** although the
app kept running (seen and reported on the target device: "the entry of e.g.
right now hbo is gone after a few sec"). The cause was a rule that treated
**three different things alike**: "title present", "session present but
without a title" (loading screen, ads) and "no session" (app in background,
title change) were all read as "nothing to show" and deleted the row.

Measured on the target device (2026-10-17), as proof of the third case:

```
--- t=1 ---  package=com.wbd.stream  state=PLAYING(3)  metadata: Return to Silent Hill
--- t=6 ---  (no more entry for com.wbd.stream)
```

The WOW session was **completely gone** after the film — without ever
reporting `STOPPED`. Whoever reads that as "the app is gone" deletes a row
the user is just reading.

Since 0.13.3 this holds ([NowPlayingBoard](../app/src/main/java/com/piotv/navigate/data/NowPlayingBoard.kt)):

| What the query says | What happens |
|---|---|
| A title (playing or paused) | Create the row, or rewrite it only on **changed text** |
| Session present, but **without a title** | The row stays as it is |
| **No** session | The row stays |
| Session reports **ended** (`STOPPED`/`ERROR`) | The row disappears after two queries (10 s) |
| Query **failed** (`null`) | nothing changes |

The price, openly stated: an app that loses its session without `STOPPED`
keeps its row until the launcher process restarts (on 1.4 GB, the normal
case after a few hours). This is the deliberate choice — a row you are
reading must not vanish while you read it.

## 0.13 (one Now Playing line per app, extracted components)

| | 0.12 | 0.13 |
|---|---|---|
| Release APK | 73,146 B | **76,187 B** |
| `classes.dex` | 68,608 B | **73,112 B** |
| Files in the APK | 31 | **32** |
| Unit tests | 94 | **108** |
| Coverage (lines / branches) | 20.1 % / 27.3 % | **20.8 % / 28.9 %** |

New: **one "Now Playing" line per app** with an active media session
(instead of exactly one line for "the" session), with **persistence** — a
line changes only when something changes for that app — and with a grace
period, so that a session briefly disappearing on a track change does not
make the line flicker. On top, three components were extracted from
`LauncherActivity` (`Ticker`, `NowPlayingPanel`, `AppMenu`); the file fell
from **769 to 596 lines**.

What the numbers explain and what they don't:

- The **one additional file** in the APK is `res/layout/item_now_playing.xml` —
  the row is inflated per app, it can no longer sit fixed in the layout.
- `classes.dex` grows by **4,504 B**. That is the price of 14 new tests, the
  board state, the panel, the menu and the `Ticker` — not of new
  dependencies (there are still **none** at runtime).
- **Coverage rises**, although the denominator holds about 100 more lines
  of Android code (panel, menu, ticker, the activity's height management):
  the 14 new tests cover the pure rule (`NowPlayingBoard`) completely.

### What was **not** verified on the device

Honest, because the numbers don't show it: 0.13 was **built and tested,
but not seen on the TV**. Three things therefore remain unverified that
only show there:

1. **The look of several rows.** Two rows stacked with a focus marker per
   row — verified is only that the views are created and bind correctly.
2. **The height feedback to the grid.** The block reports its height, the
   grid receives it as the top inset (`onHeightChanged`). On a device one
   has to see whether the spacing fits when two rows stand.
3. **The clock with several rows.** The top row still centers its content
   vertically (`gravity="center_vertical"`): with one row that is as
   before, with three rows the clock would sit next to the middle of the
   block.

Measurement commands in case it is caught up on the device: see "Start
time, memory, jank" below — the same procedure (`dumpsys`, `screencap`,
`logcat`).

## 0.12 (Now Playing in the top-left, settings)

| | 0.11 | 0.12 |
|---|---|---|
| Release APK | 59,060 B | **73,146 B** |
| `classes.dex` | 47,800 B | **68,608 B** |
| Files in the APK | 27 | **31** |
| Unit tests | 63 | **94** |
| Coverage (lines / branches) | 18.3 % / 25.6 % | **20.1 % / 27.3 %** |

New: the "Now Playing" field in the top-left (app, artist, title, cover —
from the playing media session, clickable: OK brings the playing app
back), a settings screen (long OK on "…") and the shared file mechanics
(`ConfigFile`) for order and settings.

What the numbers explain and what they don't: the **four additional
files** in the APK are the new resources — two layouts and two focus
drawables (layouts and drawables sit in the APK as individual
`res/*.xml` entries, not in `resources.arsc`). **Coverage rises** against
0.11 although the new Android classes (settings screen, media session
query) sit in the denominator: the new rules (settings file, Now Playing
row) are fully tested.

The dex growth is the biggest item, and it was measured instead of
estimated: the **new** classes (`NowPlayingListener`, `data/ConfigFile*`,
`data/NavigateSettings`, `data/NowPlaying*`, `data/Settings*`,
`ui/SettingsActivity*`) take up **56,292 B** in the unminified debug dex
(sum of the individual dex files from `dexBuilderDebug`, without
`LauncherActivity`). After R8, 20,808 B of that remain — the amount
`classes.dex` grew by — and part of that is contributed by Kotlin itself
(sequences, `BaseAdapter`, lowercasing without a locale). What this split
does **not** show: how much R8 additionally removed elsewhere; for that
both versions would have to be built and the dex output compared.

Honest about it: the two entry points are the price of the field. Whoever
wants to keep the 59 KB version doesn't need to grant the access —
without the grant the field stays empty, the classes are in the APK
anyway.

### Checked on the target device (2026-10-16)

Not only built but seen on the Chromecast HD — with a paused ZDF session
as the example:

| Check | Result |
|---|---|
| Field with cover, app name, title, state | `[Cover] ZDF · Hunting Osama bin Laden · paused` (screenshot) |
| Reachable with the remote | ▲ ▲ from the grid → the block has the focus (blue border) |
| **OK on the block** | `topResumedActivity=com.zdf.android.mediathek/.feature.player.ui.tv.PlayerActivity` — the **player** screen, i.e. the session's `sessionActivity` (not the app's main menu) |
| Then HOME | `topResumedActivity=com.piotv.navigate/.LauncherActivity`, field back |
| Grant | `settings get secure enabled_notification_listeners` contains `com.piotv.navigate/com.piotv.navigate.NowPlayingListener`; logcat: "Allowing notification listener …" |

What this does **not** show: a playing (not paused) title was not checked
on the device, and the choice of "the first session actually playing" was
tested with one session only — with several simultaneous sessions it is a
rule, not a measurement.

## 0.11 (context menu, live refresh, three shades of green)

| | 0.10 | 0.11 |
|---|---|---|
| Release APK | 58,302 B | **59,060 B** |
| `classes.dex` | 45,560 B | **47,800 B** |
| Files in the APK | 29 | **27** |
| Unit tests | 60 | **63** |
| Coverage (lines / branches) | 19.9 % / 26.4 % | **18.3 % / 25.6 %** |

New: a context menu on long OK (position, uninstall, close), live refresh
of the green text (5-second beat, only while visible) and its own green
shade for the most recently opened app. The file count in the APK
**drops** because two color selectors go away — the label color is set by
the adapter directly now.

## 0.10 (green = "in the foreground within the last 24 hours")

0.9 wanted to determine "the app is still running" via event folding ("the
last event is not `STOPPED`"). On the target device that is **backwards**:
because our window is opaque, the app below receives an `ACTIVITY_STOPPED`
on the HOME press — the rule would thus have made **nothing** green ever.

Measured directly after the HOME press (`dumpsys activity processes`):

```
*APP* UID 10101 ProcessRecord{… 28159:com.netflix.ninja/u0a101}
*APP* UID 10102 ProcessRecord{… 31660:com.disney.disneyplus/u0a102}
*APP* UID 10106 ProcessRecord{… 16828:com.wbd.stream/u0a106}
*APP* UID 10064 ProcessRecord{… 16905:com.android.vending/u0a64}
```

The **processes** live on, only their activity is stopped. That is exactly
"last in the foreground" — and the usage statistics deliver it cheaply as
a timestamp per package (`UsageStats.getLastTimeUsed`), without walking
all events:

- green = in the foreground within the last **24 hours** (never before the
  last system boot),
- the query runs on a background thread and at most every 30 seconds,
- without usage access the last known state stays standing.

Honestly: "the process lives" is thereby **approximated**, not proven —
an app used 23 hours ago and since killed by the system is green.

| | 0.9 | 0.10 |
|---|---|---|
| Release APK | 58,149 B | **58,302 B** |
| `classes.dex` | 44,964 B | **45,560 B** |
| Files in the APK | 29 | 29 |
| Unit tests | 65 | **60** |
| Coverage (lines / branches) | 20.9 % / 29.6 % | **19.9 % / 26.4 %** |

The test count drops because the folding `ForegroundLog.alive` went away
together with its six tests: a timestamp answers the question now, not an
event list.

## 0.9 (green text for running apps)

The app labels in the grid are **green as long as the app is still
running**, and the label now sits centered below the icon.

> **Corrected in 0.10:** the rule of this version ("the last event is not
> `STOPPED`") was ineffective on the device — see section 0.10 above. The
> rest of this section describes what 0.9 built.

| | 0.8 | 0.9 |
|---|---|---|
| Release APK | 56,616 B | **58,149 B** |
| `classes.dex` | 42,788 B | **44,964 B** |
| Files in the APK | 28 | **29** |
| Unit tests | 59 | **65** |
| Coverage (lines / branches) | 20.9 % / 27.8 % | **20.9 % / 29.6 %** |

## 0.8 (back into the last app) — historical

A HOME press on the **already visible** home page brings back the last
used app — and BACK does the same (first it folds the unfolded grid, then
it goes into the app). HOME is thus a switcher instead of a dead end.

The answer to "which app was in the foreground last?" comes from two
sources, in this order:

1. **Usage statistics** (`UsageStatsManager.queryEvents`): exact. Needs
   the app op `GET_USAGE_STATS` (`adb shell appops set com.piotv.navigate
   GET_USAGE_STATS allow`) — a launcher cannot query it itself. The short
   window (5 minutes) decides in the normal case, the long one (6 hours)
   is the safeguard: it is not the query that costs, but the event set.
2. **Marker**: the last app started via Navigate (`SharedPreferences`,
   discarded after a system boot). Without access that is the honest
   approximation — right when you started the app from here.

Why not `ActivityManager.getRunningTasks`: since Android 5 that delivers
only the caller's own tasks to normal apps — useless for a launcher.

| | 0.7 | 0.8 |
|---|---|---|
| Release APK | 55,537 B | **56,616 B** |
| `classes.dex` | 40,868 B | **42,788 B** |
| Files in the APK | 28 | 28 |
| Unit tests | 54 | **59** |
| Coverage (lines / branches) | 22.1 % / 27.4 % | **20.9 % / 27.8 %** |

Coverage drops slightly because `LastApp` (usage statistics,
SharedPreferences) is pure Android wiring and does not run without a
device; the testable folding (`ForegroundLog`) sits at 100 %. Details:
[ARCHITECTURE.md](ARCHITECTURE.md).

## 0.7 (rebuilt from the ground up) — historical

The versions up to 0.6 were **operable but invisible** on the target
device: `dumpsys window` reported the window as drawn and visible
(`mDrawState=HAS_DRAWN`, `mHasSurface=true`, `Surface: shown=true
alpha=1.0`), the input dispatcher gave it the focus ("Focus entering …
LauncherActivity"), the app could be operated blindly — all that was seen
was a brief flash on a click. Every SurfaceFlinger layer reported
`alpha=1.000000`.

All invisible versions had in common a **translucent window**
(`windowIsTranslucent=true`, all window animations set to `@null`); every
app visible on this TV has an opaque window, where the app below is
stopped. 0.7 therefore takes the most ordinary theme there is and rebuilds
the surface: at the bottom a row of apps, below it the "…" button.

| | 0.6 | 0.7 |
|---|---|---|
| Release APK | 54,463 B | **55,537 B** |
| `classes.dex` | 40,916 B | **40,868 B** |
| Files in the APK | 27 | **28** |
| Unit tests | 51 | **54** |
| Coverage (lines / branches) | 22.8 % / 26.7 % | **22.1 % / 27.4 %** |

The build sizes are reconstructed and checked by the docs gate. **Not**
re-measured are start time, memory and frame time (see below) — and
whether the opaque window really was the cause is concluded from a
correlation, not proven (details in
[ARCHITECTURE.md](ARCHITECTURE.md)).

## 0.6 (the row is removed) — historical

The bottom row no longer exists: a HOME press shows only the grid (see
[CHANGELOG](../CHANGELOG.md)). For the launcher that means: no usage
statistics query, no row build, no window relayout per press — and
considerably less code.

| | 0.4 | 0.6 |
|---|---|---|
| Release APK | 76,410 B | **54,463 B** |
| `classes.dex` | 69,260 B | **40,916 B** |
| Files in the APK | 37 | **27** |
| Unit tests | 93 | **51** |
| Coverage (lines / branches) | 18.3 % / 25.4 % | **22.8 % / 26.7 %** |

The build sizes are reconstructed and checked by the docs gate. **Not**
re-measured are start time, memory and frame time: the values documented
further below (128 ms, ~56 MB, 23 ms) come from versions **with** the
row. The expectation for 0.6 is smaller — no usage statistics query, no
row build, one window less — but that is an expectation, not a
measurement. Measurement commands: "Re-measuring" at the end of this
file.

## 0.4 on the device (measured 2026-10-14) — historical

> This measurement series belongs to the version **with** the bottom row.
> It stands here because it proves the two mistakes that triggered the
> rebuild: the window was registered correctly, the row was still not to
> be seen — and the two-state design cost work on every HOME press for
> content the grid delivers anyway.

First measurement since 0.1 — 0.2, 0.3 and 0.4 had only run on the
computer until here. Installed with `adb install -r` (same signature, app
data kept), device as above, the three setup steps from
[INSTALL.md](INSTALL.md) were already active.

### What works (verified functionally)

| Check | Result |
|---|---|
| Home page | **15 apps** in the grid: ARD Mediathek, File Manager +, Disney+, Settings, HBO Max, Netflix, Play Store, Prime Video, Projectivy Launcher, Shizuku, Stremio, Termux, Termux:Boot, YouTube, ZDF |
| of which found only via LEANBACK | **Prime Video, HBO Max (`wbd.stream`), ARD Mediathek** — exactly the three a pure `LAUNCHER` query hides |
| Favorites row | **9 items**: Netflix · YouTube · HBO Max · Play Store · Disney+ · ZDF · Home · Navigate · Settings |
| HOME over a running app | window registered correctly (`fillxwrap`, `Requested 1920 × 272`, `frame=[0,808][1920,1080]`) — **the row was not visible on the screen** (user observation, see window geometry below) |
| HOME again | app in front and focused again (Play Store) |
| Crashes | none (crash buffer empty) |

The favorites row is at the same time the proof of the import repair from
0.4: the six favorites stood only in the `navigate.conf` dropped via
`adb push` into the external import path. Before 0.4 the import would
never have happened (see [CHANGELOG](../CHANGELOG.md)), the row would
have stayed empty.

### Values

| | 0.1 (on the device) | 0.4 (on the device) |
|---|---|---|
| TOTAL PSS (grid warm) | ~56 MB | **53.4 MB** (53,384–53,424 kB, 3 samples) |
| TOTAL RSS | not measured | 110 MB |
| Views | 76 | **85** |
| Frame time on the HOME press | not measured | **23 ms** (50th/90th/95th percentile over two presses) |

`am start -W` is not a meaningful measurement for a home app: the system
keeps the process warm, the command then reports `TotalTime: 0` because
only the existing window comes forward (the 128 ms from 0.1 were the
cold-start special case). Solid is the frame time of the transition:
**23 ms** for unfolding the row.

### Two documented claims do not reproduce

1. **"The app behind the row is stopped"** — it is **paused**. Measured
   with the Play Store and with WOW: `ACTIVITY_PAUSED`, the window stays
   (`mHasSurface=true`, `isOnScreen=true`). The app thus keeps running.
   That is the better behavior, but it is not what was measured in 0.1.
2. **"With `TRANSLUCENT` the row is not visible"** — that claim leaned on
   `screencap`, and `screencap` is unfit for this question on this device:
   the row is composited as a **hardware overlay** (`composition
   type=DEVICE`, valid buffer `1920 × 272`, `alpha=1.0`), and exactly such
   layers the capture leaves out. Evidence: with the row standing, three
   captures in different states are **byte-identical** (same MD5 sum) and
   show the app below, while the app's window layer has a valid buffer.

   The shipped route (`TRANSPARENT`) therefore stays right — only the
   reasoning was wrong: on this device it delivers **both**, a visible row
   and an app that keeps running. Whoever wants to re-measure this needs
   the window and SurfaceFlinger data, not `screencap`:

   ```bash
   adb shell dumpsys window windows | grep -A12 'navigate'
   adb shell dumpsys SurfaceFlinger | grep -A6 'LauncherActivity#'
   #   valid: geomBufferSize=[0 0 1920 272]  composition type=DEVICE  alpha=1.000000
   ```

   Rule of thumb next to the DRM rule below: **an empty or unchanged
   `screencap` image does not mean "not visible"** — on this device
   overlay layers are missing from the image.

## Size

| | |
|---|---|
| Release APK | **75 KB** (76,430 B, 37 files) |
| of which `classes.dex` | **69,284 B** (68 KB) — the only dex file |
| Debug APK (without R8) | 890,792 B (870 KB) |
| Dependencies | none except `kotlin-stdlib` (trimmed by R8) |

The comparison debug → release is the whole performance plan in one line:
R8 removes the unused Kotlin stdlib, `shrinkResources` the unused
resources. Less dex means a shorter cold start.

## Metrics, machine-readable

This block is the source of truth for
[`tools/check_docs_drift.sh`](../tools/check_docs_drift.sh): the script
reads the values here, compares them against the built release APK and
aborts on a deviation. The prose in this file and in README.md must name
the same numbers — otherwise the documents drift apart and the reader
believes the wrong one. (That is exactly what happened in 0.3: 73,686 B
documented, 73,679 B built.)

**Two APKs, two numbers — and both are checked.** With credentials AGP
signs to `app-release.apk`, without them to `app-release-unsigned.apk`;
the signature sits in the archive as `META-INF/CERT.*`, which is why the
unsigned version is smaller (and has three files fewer). The gate takes
**the built** APK and compares it against the matching value: locally,
with `keystore/keystore.properties`, that is
`release_apk_bytes`/`apk_files`, in CI without a keystore secret
`release_apk_unsigned_bytes`/`apk_unsigned_files`. The dex is byte-equal
in both cases (`release_dex_bytes`), because the signature only adds
entries in `META-INF`.

```
release_apk_bytes=85654
apk_files=33
release_apk_unsigned_bytes=77224
apk_unsigned_files=30
release_dex_bytes=89044
release_dex_files=1
unit_tests=164
coverage_lines_percent=24.8
coverage_branches_percent=34.6
```

Why the APK size is reproducible: the VCS info file
(`META-INF/version-control-info.textproto`) is no longer packed in since
0.4 (`vcsInfo.include = false`). It carried the build's git state and
varied between 42 B ("NO_VALID_GIT_FOUND") and 120 B (commit hash) — a
documented byte count would not have been verifiable that way.

## Start time

```bash
adb shell am force-stop com.piotv.navigate
adb shell am start -W -n com.piotv.navigate/.LauncherActivity
```

| Case | `TotalTime` |
|---|---|
| Process already running (normal case, HOME press) | **128 ms** |
| HOME press over a running app | only a window change (`fillxwrap`), no activity restart → not measurable, not visible |

The system keeps the launcher process warm (home app). The HOME press
usually changes only window size and visibility — which is why the time
that feels relevant is the first frame after the dock unfolds, not a
process start.

`force-stop` on the home app is a special case: the system restarts it
immediately (`LaunchState: HOT`), so the measurement is realistic.

## Window geometry (the proof of the two modes)

```bash
adb shell dumpsys window windows | grep -A12 'com.piotv.navigate/com.piotv.navigate'
```

| Mode | `mAttrs` | `Requested w/h` | Meaning |
|---|---|---|---|
| (the only one) | `(0,0)(fillxfill) gr=TOP START CENTER` | 1920 × 1080 | home page over the whole screen |

Since 0.6 there is only one state, and the window is always the whole
screen. Until 0.5 the DOCK window was shrunk to the row — measured with
0.4:

```
mAttrs={(0,0)(fillxwrap) gr=BOTTOM CENTER … ty=BASE_APPLICATION fmt=TRANSPARENT
Requested w=1920 h=272
Frames: frame=[0,808][1920,1080]
```

So the window was registered correctly, and the system delivered the
HOME intent normally (`wm_on_resume_called`, `input_focus: Focus entering
… reason=Window became focusable`). On the screen the row was still not
to be seen — reported by the user, and exactly that was the only
structural difference to the grid state, which visibly works. 0.5 aligned
the size, 0.6 removed the second state: one window, one content.

## Window format: the measured compromise

```bash
adb shell dumpsys usagestats | grep -E 'ACTIVITY_(RESUMED|PAUSED|STOPPED)'
adb shell dumpsys window windows | grep -A12 'Window.*termux'
```

| Window format | App below | Row visible? |
|---|---|---|
| `TRANSPARENT` (theme value, as shipped), 0.1 | `ACTIVITY_STOPPED`, window gone (`mHasSurface=false`) | **yes** |
| `TRANSLUCENT` (`window.setFormat`), 0.1 | `ACTIVITY_PAUSED`, window stays (`mViewVisibility=0x0`) | "no" — measured via `screencap`, see the caveat below |
| `TRANSPARENT`, **re-measured in 0.4** | `ACTIVITY_PAUSED`, window stays (`mHasSurface=true`) | **yes** (overlay with a valid buffer) |

Measurement of the second case (own root layout filled signal red as a
test):

```bash
adb shell screencap -p /data/local/tmp/shot.png
# Pixels in the window area: (4,3,3) and (2,2,2) — the app below, no red
```

At the same time the system reported the window as healthy:

```
mDrawState=HAS_DRAWN   mLastHidden=false
Surface: shown=true layer=0 alpha=1.0
SurfaceFlinger: VisibleRegion [0,808,1920,1080]
```

So the compositor held the surface for visible, the pixels said something
else. What ships is therefore `TRANSPARENT`: a row you cannot see is not
a row. The price (the app is stopped) is in
[ARCHITECTURE.md](ARCHITECTURE.md).

> **Caveat, re-measured on 2026-10-14 with 0.4:** neither row of this
> table reproduces that way. The app below is only paused even with
> `TRANSPARENT`, and the "not visible" finding for `TRANSLUCENT` leaned
> on `screencap`, which on this device leaves hardware overlay layers
> (and thus exactly the row) out of the image. Details and evidence in
> the section ["0.4 on the device"](#04-on-the-device-measured-2026-10-14).
> The decision for `TRANSPARENT` stays right — it is only better than
> assumed: a visible row **and** an app that keeps running.

## Why `screencap` was "broken" for so long

`adb shell screencap` delivered 0 bytes as long as Netflix was in the
foreground:

```bash
$ adb shell screencap -p /data/local/tmp/s.png ; echo $?
1
$ adb shell ls -la /data/local/tmp/s.png
-rw-rw-r-- 1 shell shell 0 ...        # empty file
```

The cause is **not** the device and **not** the app: Netflix sets
`FLAG_SECURE` (DRM) for its playback, and a secured surface cannot be
captured — the capture then fails entirely. After `am force-stop
com.netflix.ninja` (or with any other app in the foreground) `screencap`
works immediately:

```bash
$ adb shell screencap -p /data/local/tmp/shot.png ; ls -la ...
-rw-rw-r-- 1 shell shell 103982 ...   # 103 KB, 1920x1080 RGBA
```

Rule of thumb for troubleshooting on this device: check `screencap`
first, then judge. An empty file means "DRM is running here", not "the
screenshot does not work".

## Memory

```bash
adb shell dumpsys meminfo com.piotv.navigate
```

| | 0.1 | 0.4 (2026-10-14) |
|---|---|---|
| TOTAL PSS | ~56 MB | **53.4 MB** |
| TOTAL RSS | not measured | 110 MB |
| Private Dirty | ~17.6 MB | not recorded separately |
| Private Clean | ~25.7 MB (mapped app resources for the icons, evictable by the kernel) | not recorded separately |
| Native Heap | ~10.8 MB | ~7.0 MB |
| Dalvik Heap | ~1.3 MB | ~6.5 MB |
| Views | 76 | **85** |
| Activities | 1 | 1 |

Measured right after the grid was built with 15 app icons (LRU cache max.
8 MB). The "Private Clean" share arises because decoding an icon requires
mapping the respective app's resources — file-backed, thus evictable
under memory pressure.

Both columns belong to the version **with** the bottom row (85 views =
grid + 9 row items). For 0.6 the memory was **not** re-measured; the
expectation is lower, because the row and its nine icon views go away —
but that is an expectation.

## App detection (the measurement that determined the architecture)

```bash
adb shell cmd package query-activities -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER | grep packageName
adb shell cmd package query-activities -a android.intent.action.MAIN \
  -c android.intent.category.LEANBACK_LAUNCHER | grep packageName
```

| Category | Packages (device) |
|---|---|
| `LAUNCHER` | filemanager, vending, disney+, youtube, netflix, projengmenu, stremio, termux, termux.boot, zdf, shizuku |
| `LEANBACK_LAUNCHER` | the same **+ amazonvideo, wbd.stream, ard.tv, tv.settings** |

→ 15 apps in the grid, 3 of which a pure `LAUNCHER` query (and thus
`LauncherApps.getActivityList`) would never have found.

## HOME resolution (the measurement that determined the priority)

```bash
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN \
  -c android.intent.category.HOME
```

Before:

```
priority=1 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
com.google.android.tungsten.setupwraith/.RecoveryActivity
```

Although Navigate held the HOME role. After `pm disable-user` of
`setupwraith` and `priority=10` in the own HOME filter:

```
START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME]
          cmp=com.piotv.navigate/.LauncherActivity} from uid 0
```

## The HOME press: the two delivery routes

```bash
adb shell logcat -d | grep 'START u0.*HOME'
```

| Delivery route | `result code` | Meaning | Reaction |
|---|---|---|---|
| Launcher in the background | 2 | `START_TASK_TO_FRONT` | show the grid (`onCreate`/`onResume`) |
| Launcher already in front | 3 | `START_DELIVERED_TO_TOP` | `onNewIntent` → show the grid |

Both routes end in the same idempotent work (`onHomePressed()`), and that
is intentional: on **every** HOME press an `onPause` of the activity runs
before `onNewIntent` (measured). A state marker "intent already seen?"
would be worthless there — that is exactly where the first version of the
second HOME press failed.

## Re-measuring (0.6 is built — the device measurement of this version is still missing)

State of 0.6, built and tested here in the project; the numbers are
checked by the docs gate. The device measurements further above (start
time, memory, frame time) come from versions **with** the bottom row. The
following table carries the build sizes across the versions:

| | 0.1 (measured on the device) | 0.2 (built locally) | 0.3 (built locally) | 0.4 (built locally) | 0.6 (built locally) |
|---|---|---|---|---|---|
| Release APK | 71,321 B | 73,721 B | 73,686 B | 76,430 B | **54,463 B** |
| `classes.dex` | 61 KB | 65,664 B (65 KB) | 65,608 B (64 KB) | 69,284 B (68 KB) | **40,916 B** (40 KB) |
| Files in the APK | 36 | 36 | 36 | 37 | **27** |
| Unit tests | 23 `@Test` (README claimed 25) | 40 | 47 | 93 | **51** |
| Lint warnings | not checked | not checked | 15 | 0 | **0** |
| Coverage (lines / branches) | not measured | not measured | not measured | 18.3 % / 25.4 % | **22.8 % / 26.7 %** |

0.3 is **35 B smaller** than 0.2, because dead code was removed
(`ForegroundProbe.hasUsageAccess()` and an unused string) — no runtime
behavior changed, but R8 has less to keep.

0.4 is **2,744 B larger** than 0.3, and the dex file grows by **3,676 B**:
four new classes with their tests and two new resource files. Working
against it is the dropped VCS entry (120 B) — exactly the reason the
number is verifiable now.

0.6 is **21,947 B smaller** than 0.4 and the dex file **28,344 B
smaller**: with the row, `DockController`, `DockSignature`,
`SettingsActivity`, `SettingsAdapter`, `FavoritesCodec`, `ForegroundProbe`,
`UsageEventFolder`, `ModeDecider`, `HomeKeyPolicy` and `Prefs` went away —
plus half the resources (27 instead of 37 files in the APK). That is not
a saving decided at the detail level, but the consequence of the launcher
having one state instead of two.

### Lint and coverage (the two new gates)

```bash
sh gradlew :app:lintDebug                    # 0 warnings, warningsAsErrors = true
sh gradlew :app:unitTestCoverageReport       # HTML + XML under build/reports/jacoco/
sh gradlew :app:unitTestCoverageVerification # gate: 16 % lines / 22 % branches
bash tools/check_docs_drift.sh               # the block above against the built APK
```

The coverage number needs context, otherwise it is misleading: **22.8 %
lines** sounds like little, but it is the ratio over *all* classes — and
the classes that need a device (activity, adapter, loader, repository,
executor, toasts: about 400 of the 530 lines) sit in it and are at 0 %.
Every class testable without a device is **completely** covered:

| Class | Lines | Coverage |
|---|---|---|
| `ConfigCodec` (+ `Config`) | 40 | 100 % |
| `NavigateConfig` | 54 | 94.4 % (without `get(context)`) |
| `AppCatalog` (+ `Candidate`) | 15 | 100 % |
| `GridColumns` | 2 | 100 % |
| `IconGeometry` (+ `Fit`) | 10 | 100 % |
| `IconCacheKeys` | 4 | 100 % |

The overall ratio is thus a **regression** guard, not a quality goal: new
logic without a test lowers it and fails the build. Whoever really wants
to raise the number needs Robolectric or instrumented tests — both would
be a new dependency, and in this project that is a deliberate decision
(see app/build.gradle.kts).

The dex file stays a single one and tiny — that is the prerequisite for a
startup profile / DEX layout to be able to help at all
(developer.android.com/topic/performance/baselineprofiles/dex-layout-optimizations:
"startup code should fit into `classes.dex`"). With a 41 KB dex this
lever is already exhausted; with a 10 MB Flutter APK it would not be.

### `am start -W` does not measure the HOME press


A HOME press over a running app is mostly only a window change
(`fillxwrap`), not an activity start — `TotalTime` says nothing about it.
The solid measurement is the frame times:

```bash
# 1) Current state of the HOME press
adb -s <ip>:5555 shell dumpsys gfxinfo com.piotv.navigate reset
adb -s <ip>:5555 shell input keyevent KEYCODE_HOME
adb -s <ip>:5555 shell dumpsys gfxinfo com.piotv.navigate framestats | head -40
adb -s <ip>:5555 shell dumpsys meminfo com.piotv.navigate | head -20

# 2) Cold start (in case the system killed the process)
adb -s <ip>:5555 shell am force-stop com.piotv.navigate
adb -s <ip>:5555 shell am start -W -n com.piotv.navigate/.LauncherActivity
```

### Proving the three items one by one

Every item from "The time budget of a HOME press" (ARCHITECTURE.md) can
be isolated by switching it off briefly and repeating the same
measurement:

| Question | Intervention | Expectation |
|---|---|---|
| What does the configuration file cost? | skip `reloadIfChanged()` in `onResume` | difference = FUSE `stat` |
| What does decoding the icons cost? | `show_icons=false` in `navigate.conf` | difference in PSS and first frames |

### AOT compilation (no code, works on every cold start)

```bash
adb -s <ip>:5555 shell pm compile -m speed -f com.piotv.navigate
# way back:  pm compile --reset com.piotv.navigate
```

On modern devices `pm compile -m` only knows `speed`, `speed-profile` and
`verify` (checked via `pm help` on an Android 16 device). Google names
"about 30 %" faster code execution from the first start for
profile-guided AOT compilation — exactly the lever an APK installed via
ADB does **not** get automatically (baseline profiles only kick in for
non-Play installs at the nightly background dexopt).

### Expected target values for 0.6 (an estimate, not a measurement)

| | 0.1 measured | expectation 0.6 |
|---|---|---|
| HOME press warm | 128 ms | **below** the 128 ms — the usage statistics query, the row build and the second window relayout go away |
| PSS after the grid is built | ~56 MB | **below** 56 MB — the row and its nine icon views are gone |
| Cold start | not measured | unchanged (the app list and the icons were never the bottleneck) |

This line is deliberately marked as an estimate: it stands here so that
the next measurement has an expectation it is allowed to fail against.

## Reproducing

```bash
bash build_navigate.sh release
NAVIGATE_DEVICE=<ip>:5555 bash install_navigate.sh
# HOME test: over a running app
adb -s <ip>:5555 shell am start -n com.netflix.ninja/.MainActivity
adb -s <ip>:5555 shell input keyevent KEYCODE_HOME
adb -s <ip>:5555 shell dumpsys window windows | grep -A12 'com.piotv.navigate/com'
adb -s <ip>:5555 exec-out screencap -p > home.png    # 0 bytes with DRM apps, see above
```

Expectation: **one** window, `(0,0)(fillxfill) gr=TOP START CENTER`,
`Requested w=1920 h=1080`, `mHasSurface=true`, `mDrawState=HAS_DRAWN` —
and a `home.png` with the grid.
