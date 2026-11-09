# Navigate

An Android TV launcher in Kotlin. Goal: **minimal UI, maximum
performance** — developed and measured on a **Chromecast HD (Google TV)**.

```
Press HOME
   →  a row with the first apps at the bottom
   →  below it the "…" button  →  OK: expand/collapse the whole grid
```

That is all this launcher does. The home screen is an app grid of which
initially **only the bottom row** is visible; below it, in the next row down,
sits the "…" button and expands the rest. At the top are the clock on the
right and — if enabled — "Now Playing" on the left. Three things can be
configured: **the app order**, **whether the Now Playing panel and the clock
appear**, and **what sits behind the grid** (solid color or image).

| | |
|---|---|
| **Release APK** | 84 KB / 85,654 B signed · 75 KB / 77,224 B unsigned · of that 87 KB / 89,044 B `classes.dex` — a single dex file |
| **Unit tests** | 164 unit tests, runnable without a device · coverage gate 18 % lines / 25 % branches (measured 24.8 % / 34.6 %) |
| **Runtime dependencies** | none — only the Android framework + Kotlin stdlib |
| **Permissions** | `QUERY_ALL_PACKAGES` (without it, leanback-only apps would be invisible); `PACKAGE_USAGE_STATS` optional, only for "back to the last app"; plus optional **notification access** for "Now Playing" (not a manifest permission but a grant via adb — see below) |
| **Start time / memory** | 128 ms / ~56 MB PSS — measured with 0.1, **not** re-measured for 0.15 ([docs/DEVICE_PERF.md](docs/DEVICE_PERF.md)) |

> APK size, dex size, test count **and** the coverage numbers are checked by
> [`tools/check_docs_drift.sh`](tools/check_docs_drift.sh) against the built APK
> and the JaCoCo report — they therefore cannot silently go stale. The script
> also checks that README and the architecture doc contain **no test count other
> than** the one in the built test set. Start time and memory are deliberately
> marked as **old** metrics: they come from the version with the bottom row,
> which no longer exists as of 0.7.

## Features

- **HOME key:** the home screen appears with the bottom app row.
- **HOME again or BACK:** brings back the last app used — the home screen is
  thus a switcher, not a dead end. With usage access the answer is exact;
  without it, the app last started via Navigate is used.
- **"…" (the row below the app row):** expands the rest of the grid — and
  collapses it again. BACK does the same while expanded.
- **OK on an app:** launches it.
- **Long OK on an app:** moves it **forward one position** and saves the order.
  One way to change the order on the TV; the other is the config file
  (`adb push`).
- **Clock** top right — since 0.17 at the **very top** of the row (previously
  centered next to the "Last seen" content), updated every minute.
- **"Now Playing" top left** (0.12, multiple lines since 0.13, optional): **one
  line per app with an active media session** — **app name, artist and title**,
  plus `· paused` when it is paused, and the **cover art** if the session
  provides one (otherwise the app icon). If an audiobook is paused while a
  radio station plays alongside, both lines are shown; the one that is
  **playing** is on top. Visible only while collapsed — expanded, the space
  belongs to the apps.
  **The panel is a bounded window (0.15.1):** it grows at most 260 dp tall and
  then scrolls within itself, with a **permanently visible scrollbar**;
  `▲`/`▼` move **line by line** through the panel and bring every line into
  view, `OK` opens it. At the bottom, `▼` returns to the grid. Previously the
  panel grew with the history until the app row was no longer visible.
  **The history is one line plus an archive on demand (0.16):** below the live
  lines sits only the **newest entry that is not currently playing**
  ("Last seen"); everything older is shown by the **context menu on long OK
  on that line** (newest first) — OK on an entry brings the app back. The
  panel does not grow with the archive.
  **OK on a line brings back exactly that app** (first to its "now playing"
  screen, otherwise the app itself). It is switched on in the settings; without
  **notification access** it stays empty (a normal app may not query the media
  sessions, see [docs/INSTALL.md](docs/INSTALL.md) step 5b). The query runs
  every 5 seconds on a background thread, only while the home screen is
  visible.
- **A line stays until something changes for that app — and nothing is
  lost.** Three cases are kept apart (the rule lives in
  `data/NowPlayingBoard.kt` and is tested without a device): a new **title**
  replaces the old one — the old one moves into the history (`history.conf`,
  newest first; the newest entry is visible, everything older sits behind
  long OK on the Last seen line); a session **without a title** (loading
  screen, ads) or **no** session at all (app in the background) leaves the
  line and its cover in place; only an **explicit end**
  (`STOPPED`/`ERROR`, twice in a row) removes it. A **failed** look (no
  access, service not responding) also deletes nothing. To be honest about
  it: an app that loses its session without `STOPPED` keeps its line until
  the launcher restarts — on the Chromecast the normal case after a few
  hours.
- **Settings (long OK on "…"):** a page with **three rows** — *Show Now
  Playing* (on/off), *Show clock* (on/off) and *Background*
  (dark · image · focus) — operated with the remote control (`▲ ▼` selects,
  `OK` toggles, `◀`/`BACK` goes back). It writes to the file immediately and
  names the paths — so the settings can also be set via `adb push`
  ([docs/CONFIG.md](docs/CONFIG.md)). "Clock off" also means: **no more
  ticking**.
- **Three backgrounds (0.14/0.17):** behind the grid sits the solid, very
  dark tone (default), an **image** (filling the frame) or the
  **focus banner**: the TV artwork (`android:banner`) of the app under the
  focus — the same one the system launcher shows behind the focused app; apps
  without a banner leave it dark. The image comes from a media file in the
  app's external folder (`background.jpg`, via `adb push`); a darkening layer
  sits over the background so the labels stay readable. Decoding happens on a
  background thread.
  The backgrounds Android TV has "built in" (Ambient/"Backdrop",
  recommendation images, wallpaper) are **out of reach** for a launcher — the
  investigation is in [docs/LAST_SEEN_TITEL.md](docs/LAST_SEEN_TITEL.md)
  (section "Background"); the one exception is the **banner** — it sits in
  the app manifest itself and needs no special permission.
- **No green text (0.14):** up to 0.13, app labels were green ("active in the
  last 24 hours") or light green ("last opened"). The colors are gone — every
  label is equally bright, and so is the tick that refreshed them every
  5 seconds. The usage statistics remain only for
  **HOME/BACK → back to the last app**.
- **Context menu (long OK):** move forward/back, uninstall app (system
  dialog), close app, close all apps. Closing only affects apps in the
  background — Android allows a normal app nothing beyond that.
- **The label sits centered below the icon** (not before 0.8: without
  `gravity`, Android places text left-aligned in the cell).
- **All apps, really all:** the grid merges `CATEGORY_LAUNCHER` and
  `CATEGORY_LEANBACK_LAUNCHER`. On the test device, without this step Prime
  Video, WOW and ARD were missing — they only carry the leanback entry.
- **Live updates:** app installed/uninstalled → the grid follows.
- **Icons are kept on disk:** every icon, once decoded, is kept as a small
  WebP file in the app-internal storage. After a process restart (the normal
  case on a 1.4 GB device) no foreign APK needs to be mapped anymore — that
  was the largest single item in memory.
- No network, no ads, no analytics, no account.

## Controls

| Key | Effect |
|---|---|
| HOME | from an app: home screen (bottom row) · on the home screen: back to the last app |
| ◀ ▶ ▲ ▼ | move the focus in the grid |
| OK | launch an app · on "…": expand/collapse the grid · on a "Now Playing" line: back to the playing app |
| BACK | collapse the expanded grid · otherwise back to the last app |
| long OK | on an app: context menu (move forward/back · uninstall · close · close all) · on "…": settings · on the "Last seen" line: older history entries |

## Building

```bash
cd projects/navigate
bash build_navigate.sh            # debug APK
bash build_navigate.sh release    # release APK (R8, signed if keystore/ exists)
```

Prerequisites: JDK 17, an Android SDK with platform 36 and build tools
(`local.properties` → `sdk.dir`). Gradle comes via the wrapper (8.14.3).

Four checks, all also in CI (`.github/workflows/ci.yml`):

```bash
sh gradlew :app:testDebugUnitTest              # 164 unit tests (no device)
sh gradlew :app:unitTestCoverageVerification   # coverage gate (report: unitTestCoverageReport)
sh gradlew :app:lintDebug                      # lint — warnings are errors
bash tools/check_docs_drift.sh                 # doc metrics ↔ built APK
```

On every push, CI runs the same steps (plus a release build) and stores the
test report, coverage report, lint report and APK as artifacts. A tag `v*`
additionally triggers `.github/workflows/release.yml`: the same gates, then a
GitHub release with the APK — signed if the secret `NAVIGATE_KEYSTORE_BASE64`
is set.

The 164 tests run **without a device**: app selection (`AppCatalog`), the
display order (`Order`), the config files (`OrderCodec`, `OrderStore`,
`SettingsCodec`, `SettingsStore`, `ConfigFile`), the Now Playing lines
(`NowPlayingText`, `NowPlayingBoard`), the history (`HistoryCodec`,
`HistoryStore`) and its split — one line visible, the older ones in the menu
(`LastSeen`) —, the background rule (`BackgroundPlan`), grid columns, icon
fitting, cache keys, the visible window over the Now Playing lines
(`RowWindow`), the target size of the focus banners (`BannerGeometry`). These
classes are at 100 % line coverage; the overall figure
(24.8 % lines / 34.6 % branches) also includes the Android classes and is
documented with its reasoning in [docs/DEVICE_PERF.md](docs/DEVICE_PERF.md).

## Install and set up as the home app

```bash
bash install_navigate.sh          # installs on the TV (only there)
```

The installer picks the first reachable **TV device** — phones and emulators
are skipped. A different target: `NAVIGATE_DEVICE=<ip>:5555`.

Then two commands on the device (details and the way back in
[docs/INSTALL.md](docs/INSTALL.md)):

```bash
adb shell cmd role add-role-holder android.app.role.HOME com.piotv.navigate
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

Without the second command, the Google TV watchdog takes the HOME key back.

Optional, so that "back to the last app" answers **exactly** (instead of
remembering the app last started here):

```bash
adb shell appops set com.piotv.navigate GET_USAGE_STATS allow
```

Optional, for the "Now Playing" panel top left (without this grant it stays
empty, see [docs/INSTALL.md](docs/INSTALL.md) step 5b):

```bash
adb shell cmd notification allow_listener com.piotv.navigate/.NowPlayingListener
```

## Configuration

Two things are configurable: the app order and whether the "Now Playing"
panel appears. Each is set in a file:

```
/data/data/com.piotv.navigate/files/navigate.conf                          (order)
/data/data/com.piotv.navigate/files/settings.conf                          (settings)
/storage/emulated/0/Android/data/com.piotv.navigate/files/…                (adb push, both)
```

```ini
# navigate.conf — app order
com.netflix.ninja
de.swr.avp.ard.tv
```

```ini
# settings.conf — settings
now_playing=on
```

One package name per line, in display order. Entries no longer installed drop
out silently, the rest follows in locale-sorted order. Format, limits and
examples for both files: [docs/CONFIG.md](docs/CONFIG.md).



## Architecture in five sentences

1. **One** activity, **one** state: the grid. Two home activities in one app
   make HOME resolution ambiguous — pressing HOME then shows a chooser
   dialog.
2. The collapsed/expanded switch is **not a second layout**: when collapsed,
   the adapter reports only one row, the grid sits at the bottom edge and is
   one row tall. Expanding rebuilds nothing — cells are added.
3. **An opaque, ordinary window.** The theme sets only
   `windowBackground`/`colorBackground`: no translucency, no custom window
   animations, no window-size tricks. The reason is in
   [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — the translucent versions up
   to 0.6 were operable on the target device but invisible.
4. No AndroidX, no Compose, no Hilt, no coroutines: `GridView`,
   `LauncherApps`, `LruCache`, one executor, one handler. What is missing
   costs nothing.
5. **Icons are also kept on disk** (WebP): after a process restart no foreign
   APK needs to be mapped anymore — on a 1.4 GB device that was the largest
   single item in memory.

In full: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Project structure

```
app/src/main/java/com/piotv/navigate/
  LauncherActivity.kt        the home screen: grid, "…", order, launching, lifecycle
  NowPlayingListener.kt      the service that allows the look into the media sessions
  data/AppRepository.kt      app list (two categories), process singleton
  data/AppEntry.kt           a launchable entry
  data/AppCatalog.kt         selection and base order of the app list (tested)
  data/Order.kt              display order, "one position forward" (tested)
  data/OrderCodec.kt         parser/serializer for the order file (tested)
  data/OrderStore.kt         the file itself, with import via adb push
  data/ConfigFile.kt         file in two places: read, write, import (tested)
  data/NavigateSettings.kt   the settings as a value (today: nowPlaying)
  data/SettingsCodec.kt      parser/serializer for the settings file (tested)
  data/SettingsStore.kt      settings.conf — the same mechanics as OrderStore
  data/NowPlaying.kt         read the active media session (MediaSessionManager)
  data/NowPlayingText.kt     turn that into a line (tested)
  data/NowPlayingBoard.kt    one line per app (+ history) — and when it changes (tested)
   data/HistoryEntry.kt       a history entry (package, line, time)
   data/HistoryCodec.kt       parser/serializer for the history file (tested)
   data/HistoryStore.kt       history.conf — history: append, newest first (tested)
  ui/AppAdapter.kt           grid cells (apps; `limit` = one row or all)
  ui/HomeGridView.kt         GridView + focus correction (marking on focus loss)
  ui/GridColumns.kt          number of grid columns (tested)
  ui/IconGeometry.kt         fitting the icons into the cell (tested)
  ui/IconLoader.kt           icons: memory cache → disk → resources
  ui/IconDiskCache.kt        WebP icons in internal storage (+ key, tested)
  ui/BannerLoader.kt         the focus banners: TV artwork per app (memory cache)
  ui/BannerGeometry.kt       target size of a focus banner (tested)
  ui/SettingsActivity.kt     the settings page (long OK on "…")
  ui/NowPlayingPanel.kt      the lines top left: live + Last seen, ticking, context menu of the older ones
  ui/PanelScrollView.kt      the panel's ScrollView: height limit, scrollbar, ▲/▼ line by line
  ui/RowWindow.kt            the visible window over the lines (tested)
  ui/LastSeen.kt             the history split: one line visible, the older ones in the menu (tested)
  ui/HistoryMenu.kt          the context menu of the Last seen line: the older entries (long OK)
  ui/AppMenu.kt              the context menu (long OK on an app)
  ui/Ticker.kt               a tick on the main thread (clock · green · Now Playing)
tools/make_assets.py         generate icon vectors + TV banners
tools/check_docs_drift.sh    docs gate: documented metrics ↔ built APK
docs/                        ARCHITECTURE · CONFIG · INSTALL · DEVICE_PERF
```

## Known open issues

Listed honestly, because they would otherwise surprise while reading the docs:

1. **Start time and memory are old figures.** The documented 128 ms and
   ~56 MB come from 0.1, the version with the bottom row. For 0.13 they were
   **not** re-measured. Measurement commands:
   [docs/DEVICE_PERF.md](docs/DEVICE_PERF.md).
2. **The reason for the earlier invisibility is a correlation, not proof.**
   All translucent versions were invisible on the target device, all apps
   visible there have opaque windows, and a translucent window keeps the app
   below it alive (only paused). 0.7 therefore uses an opaque window —
   whether that was exactly the cause only a look at the device shows.
3. **The order on the TV changes only gradually.** A long press moves an app
   *one* position forward; for a big rearrangement the file is the more
   convenient way.
4. **BACK on the home screen goes to the last app** (with no known app: no
   effect). `finish()` would be the obvious way, but the system immediately
   starts a home activity again afterwards (measured: the task switched from
   t2667 to t2668) — just a flicker.
5. **The exact "last app" needs usage access.** Without it, the marker
   decides (the app last started via Navigate) — right in the normal case,
   blind when the app came from elsewhere (remote control key, Google TV row).
6. **Setup needs ADB.** The home role and the Google TV watchdog are two
   commands (docs/INSTALL.md) — a launcher cannot show permission dialogs.
7. **A config file from an older version sticks around.** On the test device
   there was still a `navigate.conf` from the 0.4 series
   (`favorites=…`, `max_favorites=6`, `columns=0`, `show_clock=false`). The
   order file reads it as a list of package names: the favorites line then
   acts as the order (which works), while the three settings lines end up as
   invisible junk (`6`, `0`, `false`) in the list and are written back on the
   next save. To clean up:
   `adb push` a file with one package name per line (docs/CONFIG.md).
