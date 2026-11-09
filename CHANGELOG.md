# Changelog

All notable changes to Navigate, newest first.

The format follows [Keep a Changelog](https://keepachangelog.com/de/1.1.0/);
versioning follows [Semantic Versioning](https://semver.org/lang/de/).

> The version is maintained in `app/build.gradle.kts` (`versionCode`/`versionName`),
> not here.

## 0.17.0

### Added

- **Focus banner: the background follows the app under the focus.** The third
  background type besides *Dark* and *Image*: when the focus is on an app, its
  **TV banner** (`android:banner` from the app manifest) fills the area behind
  the grid — the same artwork the system launcher shows; the dimming layer
  stays on top of it. Apps without a banner keep it dark. The banner is the
  one background source a normal launcher can reach **via an API**:
  ambient/backdrop images, recommendation images and wallpapers are not
  ([docs/LAST_SEEN_TITEL.md](docs/LAST_SEEN_TITEL.md), section
  "Background"). Configurable like the other values: *Settings →
  Background* continues, with *Focus* as the value; via `adb push` the value
  is called `background=focus`.
- `ui/BannerLoader.kt` — the banner loader, the same approach as for the
  icons: memory cache (12 MB LRU), decoding on a background thread, capped at
  1280 px width (`ui/BannerGeometry.kt`, **3 new tests**, 164 instead of 161).
  Deliberately **no** disk cache: a banner is only needed once the focus is
  on the app — one decode per app and process, after that the cache hits.
  Coverage drops to 24.8 % / 34.6 % because `BannerLoader` and the focus
  wiring are Android classes that don't run without a device (the gate value
  is 18 % / 25 %) — same reasoning as for
  `PanelScrollView` in 0.15.1.

### Changed

- **The clock sits right at the top.** In the top row it sat centered next to
  the "Last seen" content — with several rows it hung in the middle of the
  field. It now sits at the top edge of the row, level with the first line of
  the block (`layout_gravity="top"`).

- **The project's text is English now.** UI strings, README, CHANGELOG and
  ARCHITECTURE included — CONFIG, DEVICE_PERF, INSTALL and LAST_SEEN_TITEL
  follow. On screen this changes one word: the pause suffix now reads
  "· paused" (was "· pausiert"). History files written by versions up to
  0.14.0 with the German suffix stay readable — HistoryCodec strips both.


## 0.16.0

### Changed

- **The history is no longer a list you have to page through — it is one
  line plus an archive on demand.** Below the live rows, the Now Playing block
  shows **only the newest history entry** ("Last seen"): the newest entry not
  currently shown as a live row. Everything older sits behind a **long press
  of OK on that row** — the context menu unfolds the older entries (newest
  first, excluding the entries currently playing), and OK on an entry brings
  exactly that app back. Until 0.15.1, eight rows were there from the start
  and the rest followed page by page while scrolling down — a list you first
  have to piece together is no list for a TV.
- **`HistoryStore.page()` and `PAGE_SIZE` are gone.** They were the API of
  the page-by-page loading; with that loading gone, their only use is gone
  too. Nothing changes in the file: `history.conf` is still read and written
  in full (see [docs/CONFIG.md](docs/CONFIG.md)).

### Added

- `ui/LastSeen.kt` — the split rule (one line visible, the older ones in the
  menu) as pure computation without Android types, **5 new tests** (161
  instead of 157; the paging test goes away together with `page()`).
- `ui/HistoryMenu.kt` — the context menu of the Last seen row: the same
  framework dialog as in the grid (`ui/AppMenu.kt`).


## 0.15.1

### Fixed

- **The Now Playing block grew with the history until the apps were pushed
  off screen.** Reported as "arrow-key control no longer works properly
  because elements are no longer on screen" — and there were two causes in
  the same place:

  1. **`android:maxHeight` has no effect on a `ScrollView`.** The platform
     knows the attribute only for `ImageView`, `ExpandableListView` and the
     internal `MaxHeightFrameLayout`; `<declare-styleable name="ScrollView">`
     contains only `fillViewport`. aapt2 lets it through anyway — the attribute
     *exists* — but the ScrollView never reads it. So the block grew with the
     history: 40 entries at 52 dp each are 2080 dp on a 540 dp screen.
  2. **The height the grid received was the content height.** `NowPlayingPanel`
     hung on the inner column (all rows) instead of on the ScrollView (what
     is visible). `grid.setPadding(top = …)` thus pushed the app row below
     the screen edge.

  Together that meant: the focus landed on rows nobody saw — the control was
  not broken, it was invisible.

### Changed

- **The block is a bounded window with a scrollbar.** At most
  `now_playing_max_height` (260 dp) tall; beyond that it scrolls **within
  itself**. The scrollbar is **permanent** and sits inside (`insideInset`) so
  that it covers no row text.
- **`▲`/`▼` move through the block row by row.** The key moves the focus to
  the next row and scrolls it **beforehand** into the window. At the start
  and end of the list the key is left to the framework — there its answer is
  right (`▼` on the last row moves into the grid, `▲` on the first row stays
  put).
- **Clipped rows are no longer focus candidates.** The framework's focus
  searcher doesn't know about ScrollView clipping: the rows below the window
  sit geometrically in the layout where the grid begins. A `▲` from the grid
  therefore landed on an invisible row.

### Added

- `ui/PanelScrollView.kt` — the block's ScrollView: the height limit via a
  custom attribute (`app:maxHeight`, declared in `res/values/attrs.xml`),
  the scrollbar and the row-by-row arrow-key control.
- `ui/RowWindow.kt` — the visible window over the rows as pure computation
  without Android types, **9 new tests** (157 instead of 148). Coverage still
  drops slightly (25.7 % instead of 26.0 %): the new view class is in the
  denominator but cannot be tested without a device (see
  [docs/DEVICE_PERF.md](docs/DEVICE_PERF.md)).


## 0.15.0

### Added

- **The Now Playing block has a memory.** When a new title replaces an app's
  row, the old one moves into the history (`files/history.conf`) instead of
  disappearing. The display shows live rows first, below them the history
  **newest on top**; older entries are loaded dynamically page by page (8)
  while scrolling down — the file (at most 500 entries) is never turned into
  views all at once. History entries are dimmed and carry only the app icon
  (the session is long gone); OK on a row starts the app. The block is at
  most 260 dp tall; beyond that it scrolls within itself — the grid doesn't
  drift endlessly downward. It can also be read via `adb pull` (see
  [docs/CONFIG.md](docs/CONFIG.md)).
- **28 new unit tests** (148 instead of 120): history as a file —
  read/write/page/limit (`HistoryCodecTest`, `HistoryStoreTest`), the
  carryover rule (a new title pushes the old one into the history) and the
  paused state as **not** a new title (`NowPlayingBoardTest`,
  `NowPlayingTextTest`).

### Fixed

- **A title appeared twice in the history — once with, once without "· paused".**
  The display row (with the pause suffix) served as the change key; every
  pause tap was therefore a "new title". The history now keys on the
  **content** without the pause suffix ([NowPlayingText.content]), the display
  still on the row with the suffix ([NowPlayingText.line]). This applies to
  **every** app — the rule knows no package name.
- **Two HOME candidates.** With the debug APK (`com.piotv.navigate.debug`)
  installed next to the release APK, pressing HOME showed a chooser dialog
  instead of the grid (both carry a HOME filter). For actual use, only
  **one** Navigate variant belongs on the device (see
  [docs/INSTALL.md](docs/INSTALL.md)).
- **The notification listener address is no longer hard-wired.** It now comes
  from `applicationContext.packageName` — so the grant
  `cmd notification allow_listener <Paket>/.NowPlayingListener` also fits the
  debug APK (`.debug`), and the field no longer stays empty.

## 0.14.1

### Changed

- **The video background is removed.** `background=video` from 0.14 is now
  read like an unknown value — the previous setting stays in place, no
  guessing. There are two options again: `dark` (default) and `image`.
  A video permanently occupied a hardware decoder on the Chromecast, for an
  area behind the grid — a price without benefit.
- **Background image swapped:** the default file `background.jpg` was
  replaced with a new image (`adb push`).

## 0.14.0

### Added

- **The settings have three rows instead of one** (long OK on "…"):

  | Row | Values | Effect |
  |---|---|---|
  | Show Now Playing | On / Off | the field top left (as before) |
  | Show clock | On / Off | the clock top right — **now switchable off**, default is on |
  | Background | Dark · Image · Video | what sits **behind the grid** |

  Every change goes straight into `settings.conf` (`show_clock=`, `background=`,
  `background_file=`) and can therefore also be set via `adb push`
  ([docs/CONFIG.md](docs/CONFIG.md)).

- **When switched off, the clock is not just invisible — it stops ticking
  too.** A timer for a field nobody sees would be measurable on the
  Chromecast — the same rule as for the Now Playing block.

- **Three backgrounds.** Until now the area behind the grid was always the
  solid, very dark tone from the theme. Now there are also **Image** and
  **Video** (muted, looping), both from a media file in the app's external
  folder (`adb push`, see [docs/CONFIG.md](docs/CONFIG.md)):

  | `background` | What is shown | File |
  |---|---|---|
  | `dark` (default) | as before | none |
  | `image` | an image, filling the format | `background.jpg` |
  | `video` | a video, muted and looping | `background.mp4` |

  On top of the background sits a **dimming layer** (`@color/bg_scrim`, 40 %
  black): without it a bright photo competes with the app labels. The image
  is decoded scaled down to screen size (on a background thread,
  `inJustDecodeBounds` + `inSampleSize`) — otherwise a 4K photo would be
  33 MB of memory. If the file is missing or unreadable, it stays dark; the
  settings page names the path.

  **What is deliberately not offered:** the backgrounds Android TV has
  "built in" (Google TV ambient/"Backdrop", `TvContract` recommendation
  images, wallpapers) — they sit behind system permissions or in someone
  else's private folders. The assessment is in
  [docs/LAST_SEEN_TITEL.md](docs/LAST_SEEN_TITEL.md), section 6.

- **A feasibility note on the "Last seen" title** in
  [docs/LAST_SEEN_TITEL.md](docs/LAST_SEEN_TITEL.md): whether and how a
  **title** could appear under the field top left when the app is a favorite.
  Answer: yes, with the data already available — but neither Android nor the
  media session knows a "series", and not every app publishes metadata at
  all.

### Changed

- **No more green text.** Until 0.13 Navigate colored the app labels by
  state: green if the app had been in the foreground in the last 24 h, light
  green if it was the one opened last. Which tone meant which state was
  unguessable without docs — and for the user the color changed nothing:
  a green label was no more tappable than a gray one. Now **every** label is
  `text_primary`, and the two greens (`text_active`,
  `text_last`) have disappeared from `colors.xml`. The warning row on the
  settings page ("No notification access") was the last green text
  and is now `text_dim` like every other hint.

- **That drops one tick.** The coloring was refreshed every 5 seconds
  (`activeTicker` → `LastApp.recentlyUsed()`, a query of the usage stats).
  Both are gone; two ticks remain (clock, Now Playing). What remains is the
  usage stats for **HOME/BACK → back to the last app**
  (`LastApp.packageName()`, triggered by a key press, not on a tick).

### Fixed

- **`AppAdapter.submit` compared states that no longer exist.** The
  comparison (`limit`, `active`, `lastPackage`, app list) decided whether a
  `notifyDataSetChanged()` was needed; it now checks only limit and list.

## 0.13.3

### Fixed

- **The "Now Playing" entry disappeared after a few seconds although the app
  kept running.** Seen and reported on the target device ("the entry of e.g.
  hbo now disappeared after a few sec"). The cause was a rule in 0.13 that
  treated **three different things alike**: "title there", "session there
  but without a title" (loading screen, ads, menu) and "no session at all"
  (app in the background, title change) were all read as "nothing to show"
  — and deleted the row. Re-measured on the target device: WOW's session was
  **completely gone** after the film, without ever reporting `STOPPED`.

  Now this holds
  ([NowPlayingBoard](app/src/main/java/com/piotv/navigate/data/NowPlayingBoard.kt)):

  | What the query says | What happens |
  |---|---|
  | A title (playing or paused) | create the row, or rewrite it only on **changed text** |
  | Session there but **without a title** | the row stays as it is |
  | **No** session | the row stays |
  | Session reports **ended** (`STOPPED`/`ERROR`) | the row disappears after two queries (10 s) |
  | Query **failed** (`null`) | nothing changes |

- `NowPlayingTrack.ended` and `NowPlaying.isEnded` — "the app has ended" is
  something else than "the app is currently saying nothing". `STATE_NONE`
  does **not** count as ended: that is also the state of a freshly created
  session, and an app that is just starting should not lose its last row.
- `NowPlaying.active` now also returns sessions **without** metadata (they
  were skipped before — exactly there the bug hung) and sorts ended ones to
  the bottom.

### Known / open

- **The price of persistence:** an app that loses its session without
  `STOPPED` keeps its row until the launcher process restarts. That is the
  deliberate decision — a row you are currently reading must not vanish
  under you while reading ([docs/DEVICE_PERF.md](docs/DEVICE_PERF.md)).

## 0.13.2

### Fixed

- **The release run failed at the docs gate:** "unitTestCoverageReport.xml
  is missing". `release.yml` only ran `unitTestCoverageVerification` — the
  gate computes from the execution data but writes no report. The docs gate
  reads the coverage **numbers** from the XML file. Now both steps run there
  too, as in `ci.yml` (the CI run was already green).

## 0.13.1

### Fixed

- **The CI was red — since the first push.** Two errors, both visible only
  in the CI environment, both fixed here:
  1. **`Release-APK` broke:** `Execution failed for task
     ':app:validateSigningRelease'` — "Keystore file … not found for signing
     config 'release'". Cause: GitHub passes an **unset** secret into the
     environment as an empty string, not as a missing variable;
     `System.getenv` then returns `""` instead of `null`. The build file
     built a signing configuration with an empty password from that — and it
     pointed at a keystore that doesn't exist in the CI. Now an empty value
     counts as "not set", **and** the keystore file must exist; without both
     the APK stays unsigned, as this file has described since 0.4.
  2. **`docs gate` broke:** it looked for `app-release.apk` and found
     nothing because an unsigned release APK is called
     `app-release-unsigned.apk`. The gate now takes **the built** APK and
     compares it against the matching documented value: the unsigned variant
     has its own (67,840 B, 29 files), because the signature sits in the
     archive as `META-INF/CERT.*`. The dex is byte-identical in both cases.

### Changed

- `ci.yml` and `release.yml` put the built APK under a **fixed** name
  (`navigate-release.apk` and `navigate-<version>.apk` respectively). A
  hard-wired path to `app-release.apk` was wrong in one of the two cases.
- The two APK variants, including the numbers, are in
  [docs/DEVICE_PERF.md](docs/DEVICE_PERF.md).

## 0.13

### Added

- **One "Now Playing" row per app** — instead of exactly one row for "the"
  session. Until 0.12 one of the bits of information disappeared when an
  audiobook ran paused next to a radio station: whoever "made the sound"
  won. Now **every app with an active media session** gets its own row
  (cover included), what is playing stands on top, and **OK on a row brings
  exactly that app back** (the session's `sessionActivity`, otherwise its
  launcher intent).
- **The rows stay.** A row changes only if something changes for **this
  app** (text comparison in `NowPlayingPanel.bind`); otherwise nothing is
  written and no new cover is fetched. Until 0.12 the block was reset on
  every pass.
- **Grace period and honest errors.** A session missing in **one** pass
  (the normal case on a title change) lets its row disappear only after two
  successful queries without it. A **failed** query (no notification
  access, service not responding) deletes nothing at all:
  `NowPlaying.active` then returns `null` instead of an empty list.
- `data/NowPlayingBoard.kt` — the rule behind it, without Android types and
  with 14 unit tests: one row per app, order, persistence, grace period.

### Changed

- **Three building blocks moved out of `LauncherActivity`** — the file fell
  from **769 to 596 lines**:
  `ui/Ticker.kt` (one tick instead of three `Handler`/`Runnable` pairs),
  `ui/NowPlayingPanel.kt` (the rows, their tick, their height),
  `ui/AppMenu.kt` (context menu and its actions). The activity still holds
  the one state and answers the parts' questions as `Listener`.
- The **cover cache** is an LRU (4 MB) instead of **one** slot: with one row
  per app, several covers are needed at the same time.
- The **docs gate** checks two more things: the coverage numbers (from the
  JaCoCo report) and that README/ARCHITECTURE don't state a test count
  other than the one in the built test set — exactly this error was in the
  architecture doc in 0.12 (54 next to 94).
- The **coverage gate** sits at 18 % lines / 25 % branches (before
  16 %/22 %, measured 20.8 %/28.9 %).
- The home page now **derives the top edge of the grid** from the height of
  the Now Playing block (`onHeightChanged`): with two rows, the first app
  row would otherwise lie below it.

### Known / open

- **0.13 has not been seen on the device.** Built, tested and measured, but
  the display of several rows, the spacing to the grid and the clock next
  to a multi-row block are unverified ([docs/DEVICE_PERF.md](docs/DEVICE_PERF.md),
  "What of this is unverified on the device").

## 0.12

### Added

- **"Now Playing" top left** — visible in the collapsed state, i.e. where
  the space is empty: **app name, artist and title** of the running media
  session, plus `· paused` when the session is paused, and the **cover**
  from the session's metadata (if missing, the app icon stands there).
  Source is the `MediaSessionManager` (the same instance `dumpsys
  media_session` shows); the session taken is the one **really playing**,
  otherwise the first one with a title. The row is **one** row and truncates
  with an ellipsis so a long title doesn't overtake the clock on the right.
- **OK on "Now Playing" brings the playing app back** — first via the
  session's `sessionActivity` (the "Now playing" screen the app itself
  offers), otherwise via its launcher intent. The block is focusable for
  this and shows its focus like the grid cells.
- **Settings** — long OK on "…" opens a dedicated page with the switch
  *Show Now Playing* (`▲ ▼` selects, `OK` toggles, `◀`/`BACK` back). It
  writes **immediately** to `files/settings.conf` (see
  [docs/CONFIG.md](docs/CONFIG.md)) and names both file paths so an
  `adb push` doesn't have to be guessed.
- Honest feedback instead of silent idleness: if the switch is on and
  access is missing, the page says so explicitly.

### Good to know

- **Without notification access the field stays empty.** A normal app is
  not allowed to query the active media sessions: `getActiveSessions`
  requires a registered notification listener (or `MEDIA_CONTENT_CONTROL` —
  a signature permission that `pm grant` cannot grant). Navigate ships
  `NowPlayingListener` for this: a service that reads **nothing** — its
  registered name is the credential. The grant is one command
  ([docs/INSTALL.md](docs/INSTALL.md), step 5b).
- The query runs **every 5 seconds**, on a background thread, and only
  while the home page is visible and the grid is collapsed. Expanded, the
  space belongs to the apps — then the field disappears.

### Changed

- **The file mechanics now live in one place** (`data/ConfigFile.kt`): order
  and settings share internal storage, `adb push` import, throttle and
  cache. Exactly in these mechanics sat the bug that `OrderStoreTest` found
  in 0.4 — two copies would be two chances to reintroduce it. `OrderStore`
  behaves unchanged (its tests are still green).
- `README.md`, `docs/ARCHITECTURE.md`, `docs/CONFIG.md` and `docs/INSTALL.md`
  describe the second setting and the optional access. The claim that "only
  the order is configurable" no longer holds and has been corrected
  everywhere.

## 0.11

### Added

- **Context menu on long OK** (long press in the grid): *Move position
  forward*, *Move position back*, *Uninstall app*, *Close app*, *Close all
  apps*. Uninstall shows the system dialog (`ACTION_DELETE` +
  `REQUEST_DELETE_PACKAGES`); closing goes through
  `ActivityManager.killBackgroundProcesses` (+ `KILL_BACKGROUND_PROCESSES`) —
  that is the only path a normal app has, and it only affects apps in the
  background (on the home page that is the normal case).
- **The green text keeps refreshing** (every 5 seconds, only while the home
  page is visible). The query remains a timestamp look into the usage
  stats, on a background thread.
- **The app opened last has its own shade of green** — one step lighter
  than the other active apps.

### Fixed

- **Green was invisible on the selected cell.** The color hung on a state
  selector (`state_selected` → white). Now it hangs only on the app's
  state: green stays green, selection is shown by the blue border.
- **The "…" button now sits centered** below the app row (previously on
  the right).

## 0.10

### Fixed

- **The green marking from 0.9 would never have fired.** The rule was "the
  last lifecycle event is not `STOPPED`" — on the target device the app
  underneath gets exactly that `ACTIVITY_STOPPED` on a HOME press (a
  consequence of the opaque window), although its **process** keeps living.
  Re-measured with `dumpsys activity processes`: Netflix, Disney+, WOW and
  the Play Store were all still there.
- **New rule:** green = in the foreground within the last **24 hours**
  (never before the last system boot), read from the usage stats timestamps
  (`UsageStats.getLastTimeUsed`) — cheap, on a background thread, at most
  every 30 seconds. Honestly added: "process alive" is thereby approximated,
  not proven.
- The event folding `ForegroundLog.alive` and its six tests are gone (they
  didn't answer the question); `ForegroundLog.lastResumed` remains for
  "back to the last app".

## 0.9

### Added

- **Green label for apps still running.** The basis is the same usage stats
  as for "back to the last app": a package counts as running as long as its
  last lifecycle event is not `STOPPED` (paused = still running). The
  folding (`ForegroundLog.alive`) is pure logic and tested; the query runs
  on a background thread and at most every 30 seconds — a 6-hour window on
  the HOME path would be exactly the post that 0.2 removed there. Without
  usage access nothing turns green, instead of something turning green
  wrongly.
- Two state lists `app_label` / `app_label_running`: the **selection** stays
  white in both cases so that "selected" looks the same regardless of
  whether the app is running.

### Fixed

- **The app label was not centered under the icon.** `item_app.xml` had no
  `android:gravity` — Android then sets the text left-aligned in the cell,
  and a short name stuck to the left edge. Now `gravity="center"` +
  `textAlignment="center"`.

## 0.8

### Added

- **HOME and BACK bring the app used last back.** A HOME press on the
  already visible home page is thus a switcher instead of a dead end; BACK
  first collapses an expanded grid and then goes into the app. The answer
  to "which app was last in front?" comes from the usage stats (app op
  `GET_USAGE_STATS`, optional) and otherwise from the marker of the app
  last started via Navigate — the folding of the events (`ForegroundLog`)
  is pure logic and tested.
- **`PACKAGE_USAGE_STATS` back in the manifest** — explicitly optional and
  only for this one question; without the app op, Navigate starts
  unchanged.

## 0.7

**Rebuilt from the ground up** — because the versions up to 0.6 were usable
on the target device, but **invisible**. A HOME press now shows: at the
bottom a row with apps, below it the "…" button; "…" unfolds the whole grid.

### Changed

- **The window is opaque and completely ordinary.** The theme now sets only
  `windowBackground`/`colorBackground`; `windowIsTranslucent`,
  `windowDisablePreview`, the custom `windowAnimationStyle` with nothing but
  `@null` entries and all the window-size tricks are removed.
  **Reason (measured):** with the translucent theme, the window on the
  device was drawn, focused, usable — and not visible; only on a click
  something flashed up briefly. All SurfaceFlinger layers reported
  `alpha=1.000000`, `mDrawState=HAS_DRAWN`, `Surface: shown=true alpha=1.0`.
  A translucent window keeps the app underneath alive (only paused); every
  app visible on this TV has an opaque window. Details:
  [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
- **Layout redone:** one vertical column at the bottom edge. Collapsed, the
  adapter reports exactly **one row** (the grid is `wrap_content` tall);
  below it the "…" button stands in its own row. Expanded, the grid gets
  the whole space via `layout_weight` and scrolls. No second layout, no
  second window, no animation.
- **One more icon in the row.** The "…" button no longer occupies a grid
  cell: the row now shows all columns (6 instead of 5 apps on the
  Chromecast).
- **Orange selection highlight removed, blue border stays.** The orange
  background came from the theme (`listSelector`);
  `android:listSelector="@null"` doesn't switch it off — now
  `@android:color/transparent` stands there. The marking itself stays as it
  was: `bg_grid_item.xml` (rounded, blue 2dp border). `HomeGridView` clears
  it when the focus moves to the "…" button.
- **Only the order is configurable now.** `Order`, `OrderCodec`,
  `OrderStore` replace `ConfigCodec`/`NavigateConfig`/`FavoritesCodec`; the
  config file contains one package-name line per entry. `columns`,
  `show_clock`, `show_icons` and the favorites are gone (the clock is
  always on, icons always on, columns come from the screen width).
- **Order on the TV:** long OK on an app moves it one position forward and
  saves immediately.
- **Measured smaller:** release APK **55,537 B**, `classes.dex` **40,868 B**,
  54 tests, line coverage **22.1 %** (branches 27.4 %).

## 0.6

**The bottom row is gone.** A HOME press now shows only the grid with all
launchable apps — no overlay, no favorites row, no second state. This is
also the performance change: the HOME press no longer has a second piece of
content for which it previously paid a usage-stats query, a row build and a
window relayout on **every** press.

### Changed

- **One state instead of two.** `LauncherMode`, `ModeDecider` and the whole
  window-size switching are gone; the window is always the whole screen.
  What remains is the content: the grid.
- **Foreground detection removed** (`ForegroundProbe`, `UsageEventFolder`,
  `ForegroundSnapshot`, `Prefs`). It existed only to answer "is an app
  running?" — the question no longer arises. With it, the
  `PACKAGE_USAGE_STATS` permission also drops out of the manifest (and with
  it the "usage access" setup step).
- **Bottom row removed** (`DockController`, `DockSignature`, `item_dock.xml`,
  `bg_dock.xml`, `ic_home`/`ic_tune`/`ic_settings`, dock dimensions and
  colors). Long press in the grid no longer toggles a favorite either.
- **Navigate settings removed** (`SettingsActivity`, `SettingsAdapter`) —
  that screen was exclusively the favorites editor. The remaining settings
  (`columns`, `show_clock`, `show_icons`) still live in the config file
  (docs/CONFIG.md).
- **`FavoritesConfig` → `NavigateConfig`, `FavoritesCodec` gone.** The file
  now holds only the grid settings. Keys from earlier versions
  (`favorites`, `max_favorites`) are ignored instead of treated as errors —
  an old file on the device therefore doesn't disturb anything.
- **Measured smaller and leaner:** release APK **54,463 B** instead of
  76,410 B, `classes.dex` **40,916 B** instead of 69,260 B, 27 instead of
  37 files in the APK. The test count drops with the removed code from 93
  to 51 (the tests for the removed rules went with them); line coverage
  rises thereby from 18.3 % to **22.8 %** (branches 26.7 %).
- **Docs honesty:** the theme entry with all-`@null` animations does
  **not** switch off the transition — the shell replaces a missing
  animation with its own default (in the log: `loadAnimation:
  anim=AlphaAnimation … isEntrance=true`). This now stands at the finding
  and in docs/ARCHITECTURE.md.
- **`install_navigate.sh` now only hits TV devices.** Without
  `NAVIGATE_DEVICE` the script took the *first reachable* device — with a
  test phone also hanging on adb at the same time, that was the wrong
  target, and the error would have been silent. Now
  `ro.build.characteristics` is checked (must contain `tv`), emulators and
  phones are skipped, and the chosen device is printed with its model.
  A different target forces `NAVIGATE_DEVICE`.

## 0.5

### Fixed

- **The row above the running app was not visible on the target device.**
  The DOCK window was shrunk to the height of the row until 0.4 (height
  `WRAP_CONTENT`, `Gravity.BOTTOM`). `dumpsys window` reported it correctly
  at the time (`(0,0)(fillxwrap) gr=BOTTOM CENTER`, `Requested w=1920 h=272`,
  `frame=[0,808][1920,1080]`, surface with a valid buffer, `alpha=1.0`), and
  the system delivered the HOME intent normally (`wm_on_resume_called`,
  `input_focus: Focus entering … reason=Window became focusable`) — nothing
  of the row arrived on the screen. The window size was the **only**
  structural difference to GRID mode, which visibly works; it is now the
  same in both modes (full screen). The mode lives only in the content:
  GRID paints the root opaquely, DOCK leaves it transparent and shows only
  the row. Measurement evidence: [docs/DEVICE_PERF.md](docs/DEVICE_PERF.md).

- **The CI failed at the "Android SDK" step.**
  `android-actions/setup-android@v3` calls `sdkmanager tools`, and the
  "tools" package no longer exists in the SDK repository (replaced by
  cmdline-tools). The step had been in the file since 0.3 and had never
  run. Now only what is missing is checked — the runner image brings SDK,
  licenses and cmdline-tools along.
- **Lint failed in the CI although it was green locally.** The cause is
  not a code site but a network-dependent check:
  `AndroidGradlePluginVersion` reported "A newer version of Gradle than
  8.14.3 is available: 8.14.6". Locally, Lint ran with `--offline` and
  therefore stayed silent. The three version checks
  (`AndroidGradlePluginVersion`, `GradleDependency`, `NewerVersionAvailable`)
  are now switched off: the wrapper (with sha256) and AGP/Kotlin are pinned
  here deliberately, and a warning that turns red upstream on every release
  is not a quality signal. Lesson for the local run: check the Lint gate at
  least once **online**, otherwise you are checking a different set than
  the CI.

### Changed

- **`.gitignore` completed**: Kotlin/AGP outputs (`.kotlin/`, `captures/`,
  `*.dex`, `*.class`, `*.hprof`), signing material also individually
  (`*.jks`, `*.keystore`), editor and OS leftovers, logs.
  `gradle/wrapper/gradle-wrapper.jar` deliberately stays versioned (that's
  why no `*.jar` in the file).

## 0.4

No new interaction behavior: this version closes verification gaps (Lint,
coverage, docs), repairs the config file import and makes the documented
metrics checkable. **Not** re-measured on the device — the startup-time and
memory values still come from 0.1.

### Fixed

- **The config file import (`adb push`) never happened.** The throttle
  marker was at `Long.MIN_VALUE`; `now - Long.MIN_VALUE` overflows and goes
  negative, so the "within the interval" condition was **always** true —
  the import path was never reached. Found by the test rewritten in 0.4
  (`FavoritesConfigTest.eine neuere Import-Datei wird uebernommen`), i.e.
  exactly the check that didn't exist before. Only the path described in
  docs/CONFIG.md was affected; the internal file and the Navigate settings
  were never affected.
- **The icons in the Navigate settings had no content description** — a
  screen reader read "App icon" for every row. Grid and dock already set
  the app name; the settings didn't.
- **"Maximum 1 favorites"** and "Favorites: 1 of 6" — both strings are now
  plurals (`<plurals>`); the number decides the form.

### Added

- **Lint as a gate** (`warningsAsErrors = true` in app/build.gradle.kts):
  the build breaks on every warning, locally as in the CI. The 15 warnings
  from 0.3 were worked through instead of suppressed — fixed (plurals,
  overdraw, RedundantLabel, two unused resources, missing
  `contentDescription`, monochrome icon layer, backup rules for Android 11
  and older) or suppressed at the finding with a reason
  (`TranslucentOrientation`, `DiscouragedApi`). `lint` now reports
  "No issues found."
- **Unit test coverage** via the JaCoCo built into Gradle:
  `unitTestCoverageReport` writes HTML and XML, `unitTestCoverageVerification`
  is the gate (16 % lines / 22 % branches). Measured in 0.4: **18.3 % lines
  / 25.4 % branches** across all classes; without the Android classes it
  would be ~100 % — every class testable without a device is fully covered.
- **docs gate** (`tools/check_docs_drift.sh`): checks the documented
  metrics against the built APK (APK bytes, dex bytes, dex files, file
  count, test count). It proved itself on the first run — the 73,686 B
  documented in 0.3 did not match the rebuild (73,679 B).
- **Release workflow** (`.github/workflows/release.yml`): on a `v*` tag,
  tests, coverage, Lint and docs gate run, then a GitHub release is created
  with the APK. Signed if `NAVIGATE_KEYSTORE_BASE64` is set.
- **46 new unit tests** (47 → **93**) and four new building blocks testable
  without a device: `HomeKeyPolicy` (the HOME rules — exactly the cases
  that went wrong on the device), `AppCatalog` (selection and order of the
  app list, including the "Prime Video/WOW/ARD are missing" bug),
  `IconGeometry` (icon fitting) and `DockSignature` (cache key of the row).
- **Gradle cache in the CI** — the release build alone takes ~15 min on
  weak hardware; without a cache every run downloads the dependencies
  again.

### Changed

- **`FavoritesConfig` is testable without a device**: the constructor takes
  two files and a clock instead of a `Context`; the production path lives
  in `get(context)`. This made the file semantics (broken content, import
  rules, throttle) testable — and it found the bug above.
- **`AppRepository`** now contains only the PackageManager queries; the
  rules (disabled apps, own launcher in both variants, first entry wins,
  empty label, sorting) live in `AppCatalog`.
- **`LauncherActivity`** now contains only the wiring of the HOME rules;
  the rules themselves live in `HomeKeyPolicy`. Likewise, the icon fitting
  moved to `IconGeometry` and the dock cache key to `DockSignature`.
- **The VCS info file is no longer packaged** (`vcsInfo.include = false`,
  per build type — at the top level the switch doesn't exist). It contained
  the Git state and made the APK size irreproducible: exactly the cause of
  the 7 B deviation in 0.3 (73,686 B documented, 73,679 B built; measured
  in 0.4: 42 B "NO_VALID_GIT_FOUND" vs 120 B with a commit hash). The
  release APK is thereby 120 B smaller, and the documented size is
  reproducible.
- **Dead state removed:** `LauncherActivity.launchedSinceDock` was written
  in five places and never read — the same kind of find as
  `ForegroundProbe.hasUsageAccess()` in 0.3.
- `versionCode` 4, `versionName` "0.4"; `targetSdk` stays 34 (reason in the
  build file).

### Known / open

- Unchanged from 0.3: the app behind the row is stopped (theme format
  `TRANSPARENT`), the way back from the row is a single key press, without
  usage access a heuristic decides, and **0.4 was not re-measured on the
  device**.
- Total coverage stays low (18.3 %) because the Android classes sit in the
  denominator. Raising it would take Robolectric or instrumented tests —
  both deliberately not in this project (no extra dependency).

No runtime behavior changed: this version cleans up, closes a test gap and
puts the docs right. **Not** re-measured on the device — the startup-time
and memory values still come from 0.1.

### Changed

- **The grid's column count is a pure function** (`ui/GridColumns.kt`,
  previously a private method of `LauncherActivity`). Behavior unchanged:
  a configured number still applies unchanged, `0` means automatic
  (width / 150dp, limited to 4–8). The reason is testability, not logic.
- **Dead code removed:** `ForegroundProbe.hasUsageAccess()` was never
  called (the query falls back to `null` anyway), likewise the string
  `toast_usage_hint`. The release APK is thereby 35 B smaller.
- **README corrected:** the section "Architecture in five sentences"
  named the window format `TRANSLUCENT`. What ships is `TRANSPARENT`
  (theme) — exactly as described in ARCHITECTURE.md and DEVICE_PERF.md.
- **Broken docs link** in `docs/INSTALL.md` (the anchor pointed to a
  heading that no longer exists).
- **Debug APK size** in `docs/DEVICE_PERF.md`: 858 KB → 868 KB (888,854 B,
  re-measured).

### Added

- **7 unit tests for the column count** (now 47): the Chromecast yields 6,
  the automatic mode never falls below 4 and never goes above 8, a
  configured number is not clamped.
- **CI** (`.github/workflows/ci.yml`): unit tests and release build on
  every push; the APK is then available as an artifact (unsigned as long
  as no signing secrets are set).
- **Version control:** the project now lives under `git` (`keystore/`,
  `local.properties` and all build folders are excluded via `.gitignore`).

## 0.2

Not yet re-measured on the device (no access to the TV during the
change) — the changes target the HOME-press costs measured in 0.1.
Measurement commands: docs/DEVICE_PERF.md, section "Re-measuring".

### Changed

- **Foreground detection in two windows.** Previously, every HOME press
  walked a 6-hour window of the usage stats on the main thread (thousands
  of events). Now a 5-minute window first — the normal case when an app
  was just running — and only on an empty result the long window as a
  safety net.
- **Config file now lives app-internally.** `/sdcard` goes through the
  MediaProvider FUSE daemon: `stat` and `open` there always pass through a
  userspace daemon, and the file was checked several times on every HOME
  press. The external path remains as the **import** source for `adb push`
  (throttled check, taken over only with a newer timestamp).
- **`FavoritesConfig.current()` no longer reads.** The state lives in
  memory; it is only re-checked in `reloadIfChanged()`.
- **Favorites are looked up via an index.** `AppRepository` keeps
  `byPackageName` — previously a new HashMap over all apps was built on
  every HOME press.
- **Window size is only set on a real change.** A HOME press set it twice
  (onNewIntent and onResume) — the second time a relayout over Binder for
  nothing.
- **Focus without a detour.** `grid.post { requestFocus() }` is now a
  direct `requestFocus()` with `post` only as a fallback.
- **Icons live on disk.** Every decoded icon is kept as a WebP file (v1,
  90 %) in internal storage; `IconLoader.warm()` pre-fills memory and disk
  on a background thread as soon as the app list is ready.

### Added

- **Text mode** (`show_icons=false`): the grid shows only app names, the
  icon bitmaps are released. Intended for minimal load on the 1.4 GB
  device; the row keeps its icons.
- **Import path in the settings screen** — both paths (internal and for
  `adb push`) are shown there.
- **17 new unit tests** (now 40): event folding (`UsageEventFolderTest`),
  cache keys (`IconCacheKeysTest`), `show_icons` in the parser
  (`ConfigCodecTest`).

### Corrected

- **The test count in the 0.1 documentation was wrong.** It named 25
  tests, the code had 23 `@Test`. Both numbers are now counted out and
  agree: 23 (0.1) + 17 (0.2) = 40.

## 0.1

First version. Fully measured and operated on the target device
(Chromecast HD, Google TV, Android 14).

### Added

- **Two modes in one activity**: `GRID` (home page, all apps in the grid)
  and `DOCK` (only the bottom row above the running app). The HOME press
  decides which mode appears.
- **Bottom row** with favorites, Home, Navigate settings and system
  settings. The focus moves with ▼ from the grid into the row and with
  ▲ back.
- **App grid** with 6 columns at 1080p/TV density, D-pad navigation,
  clock top right, focus marking without animations.
- **App list from two categories** (`LAUNCHER` ∪ `LEANBACK_LAUNCHER`) —
  otherwise Prime Video, WOW and ARD would be invisible on the test
  device.
- **Config file** `navigate.conf` for the favorites row (`favorites`,
  `max_favorites`, `columns`, `show_clock`), with comments, error
  tolerance and change detection at runtime.
- **Navigate settings** as their own screen: OK toggles a favorite on or
  off, ◀ ▶ moves it, every change goes straight into the file.
- **Long press** in the grid sets/removes a favorite, in the row it
  removes it.
- **Foreground detection** via the usage stats (lifecycle of the last
  foreign app) with a documented heuristic as fallback.
- **Icons** are decoded in the background, scaled to the target size,
  given 22 % rounding and kept in an 8 MB LRU cache.
- **23 unit tests** for decision logic, config parser and favorites
  list — runnable without a device.
- Generator for icon vectors and TV banners (`tools/make_assets.py`).
- Docs: README, INSTALL, CONFIG, ARCHITECTURE, DEVICE_PERF.

### Fixed (during development, found on the device)

- **HOME landed on the system app instead of Navigate.** Cause: Google's
  `com.google.android.tungsten.setupwraith/.RecoveryActivity` carries a
  HOME filter with `priority=1` and a watchdog keeps restarting it.
  Solution: own HOME filter with `priority=10` plus disabling `setupwraith`
  (documented, reversible).
- **The row was a dead end: the app underneath could no longer be
  operated.** Cause: the row holds the keyboard focus, the app is only
  paused. Solution: HOME (second press) and BACK close the row and bring
  the app back to the foreground via an explicit launcher intent —
  `finish()` doesn't work for a home activity, the system restarts it
  immediately (measured: the task switched from t2667 to t2668).
- **The second HOME press was not recognized.** Cause: the system pauses
  the activity on every HOME press *before* delivering the intent
  (`onPause → onNewIntent → onResume`) — a lifecycle flag is already
  `false` there. Solution: own marker "row is visibly up" instead of
  `isResumed`.
- **The HOME press showed the home page instead of the row.** Cause: on a
  HOME press the launcher itself already stands as `RESUMED` in the usage
  stats, so the "newest foreground event" was always itself. Solution: the
  lifecycle of the last **foreign** app is evaluated (`PAUSED`/`RESUMED` =
  alive, `STOPPED` = ended).
- **Three apps were missing from the grid.**
  `LauncherApps.getActivityList` only knows `CATEGORY_LAUNCHER`; Prime
  Video, WOW and ARD carry only `CATEGORY_LEANBACK_LAUNCHER`. Solution:
  union of both categories via the PackageManager (with
  `QUERY_ALL_PACKAGES`).
- **Screenshot didn't work** — `screencap` returned 0 bytes while Netflix
  was in the foreground (DRM → `FLAG_SECURE`). Not an app bug, but the
  cause of several wrong diagnoses; see docs/DEVICE_PERF.md.
- **`setting_row_height`** was misspelled in the layout (build error).
- **`IconLoader`** was meant to be a singleton, but the companion was
  private.

### Known / open

- **The app behind the row is stopped** (theme format `TRANSPARENT`, so
  that the row is visible). With `window.setFormat(TRANSLUCENT)` it stays
  paused and visible, but then the window content was no longer visible on
  the target device. Both variants measured, decision documented:
  README "Known open points", docs/DEVICE_PERF.md.
- Without usage access, a heuristic decides instead of the measurement.
- An open IME keyboard of the app underneath lies above the row (window
  layer).

