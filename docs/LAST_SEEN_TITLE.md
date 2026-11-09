# Last seen — can a title sit below it?

**Question:** Below the "Last seen" row (top left), a **new line above the
icons** should show the **title** — but only, **if the series is a
favorite**. Doable?

**Answer in one sentence:** Yes, with the data Navigate already reads
today — but "series" is a concept **neither Android nor the media sessions
know**. Feasible is "title of the last seen app, if that app is a
favorite"; the detection *series vs. movie vs. radio* is not.

---

## 1. What already exists today

`NowPlayingPanel` (`app/src/main/java/com/piotv/navigate/ui/NowPlayingPanel.kt`)
shows in the top-left **one line per app** with an active media session:

| Component | Source | Line in code |
|---|---|---|
| App name ("ZDF") | `AppRepository` (app list) | `NowPlayingInfo.app` |
| Title / artist | the session's `MediaMetadata` | `NowPlayingInfo.title` / `.artist` |
| "· paused" | `PlaybackState` | `NowPlayingInfo.playing` |
| Cover (40 dp) | `MediaMetadata` → scaled down, in the LRU cache | `NowPlaying.art` |
| OK on the row | `MediaController.sessionActivity` (PendingIntent) | `NowPlayingTrack.activity` |
| Row text | rule without Android types | `NowPlayingText.line()` |

The build is already **dynamic**: the block is empty in the layout
(`@+id/now_playing` in `activity_home.xml`), the rows are created at
runtime from `item_now_playing.xml`, and the block **reports its height**
to the grid (`Listener.onHeightChanged` → `grid.setPadding(top, …)`). An
additional line is thus **no rebuild of the layout** but an additional
line in the block.

## 2. Where "favorite" comes from — and what it means here

Nowhere does a launcher learn that a **piece of content** is a favorite.
There is no system concept of "series", "favorite" or "season" for media
content; a player's media session delivers exactly four usable fields
(`title`, `artist`, `album`, `albumArt`). "Favorite" can therefore only be
understood by Navigate via **apps**:

- The **first grid row** is the favorites row in Navigate (visible while
  collapsed).
- In the file that corresponds to the first entries in `navigate.conf`
  (`favorites=…`/`order=…`, see `docs/CONFIG.md`).

"Series as a favorite" thus means technically: **the app the title comes
from sits in the first grid row.** That is an approximation — it is honest
and checkable, but it does not distinguish between "Netflix series" and
"Netflix movie".

## 3. Three variants of what the title line could look like

| # | Variant | What the user sees | Effort | Risk |
|---|---|---|---|---|
| 1 | **Second text line in the existing row** | `ZDF · How much nonsense…`<br>`Last episode: season 2, episode 3` | small (layout `item_now_playing.xml`, `NowPlayingText`) | low — but "season/episode" is not delivered by any session (see 4) |
| 2 | **Own "Last seen" line below the block** | Line 1: `ZDF · title`<br>Line 2 (gray): `Last seen · ZDF · title` | small (`NowPlayingPanel` + `NowPlayingBoard`) | low; duplicate information when only one app is playing |
| 3 | **Title only for favorites** (the first row's apps) | For favorite apps: line 1 + title line; all others: only line 1 | medium (passing the favorite list into the panel) | low; the grid's height only grows when a favorite is playing |

**Recommendation:** variant 3. It is the only one that fulfills the
condition "as long as the series is a favorite" without a new data
source, and it costs one line in `NowPlayingPanel` plus passing the
favorite packages through (they already sit in `OrderStore.current()`).

## 4. What does **not** work — the honest limits

1. **"Series" is not recognizable.** None of the available sources
   (`MediaMetadata`, `UsageStats`, `LauncherApps`) knows series, seasons
   or episodes. Recognizable is only **what the player publishes
   voluntarily**.
2. **Not every app publishes anything.** On the target device ZDF
   delivered title and cover; Netflix delivers **no** metadata at all
   depending on state. Then the line stays empty — that is already so
   today and would be the same with a title line.
3. **A title is not a series.** `How much nonsense is too much? STREET
   FIGHTER & WHALE…` is a broadcast, not a series name; a radio station
   delivers the same text as a movie. A title line therefore cannot be
   called "series", only "title".
4. **No looking back.** The session shows what is running **now** (or
   paused). "Last seen" in the sense of "three days ago, episode 7"
   Navigate cannot know: `UsageStats` delivers only packages and
   timestamps, no content. The title "Last seen" is thus only provable
   for the **playing** app.
5. **Space.** The block reports its height, the grid slides down
   (`grid_padding_top`). Collapsed, the Chromecast HD (1080p) has room
   for ~4 rows of 52 dp; from three Now Playing lines plus title lines
   it gets tight, then the title line has to give way (e.g. only the
   first row gets it). **Since 0.15.1 the block is capped at 260 dp
   (≈5 rows) and scrolls within itself afterwards** — only the
   *visible* height is reported to the grid. An additional title line
   thus no longer costs an app row, it only pushes the history further
   down.

## 5. What implementing it would concretely take

1. Extend `NowPlayingBoard.Row` with a field `detail: String?` (pure
   rule, without Android types — hence testable in
   `NowPlayingBoardTest`).
2. Extend `item_now_playing.xml` with a second `TextView` (gray,
   `text_dim`, `12–14 sp`); the block's height changes → `reportHeight`
   re-measures automatically.
3. `NowPlayingPanel` receives the favorite packages from the `Listener`
   (`OrderStore.current()` → first `columns` entries) and sets `detail`
   only for those.
4. Add the switch `last_seen_title=on|off` to `docs/CONFIG.md` (default
   `off`, like every new switch in this project).

**Estimated effort:** half an hour of code, one test, one doc paragraph —
no new permission, no new dependency, no network.

---

## 6. Background — what Android TV has "built in" (and why it doesn't work)

The same question as above, only for the surface: **three** backgrounds
should be available, ideally the ones Android TV already ships. What of
that is reachable:

| Source | What sits there | Reachable for a launcher? |
|---|---|---|
| Google TV ambient/"Backdrop" | the changing images of the screensaver | **No.** They sit in the private storage of `com.google.android.apps.tv.launcherx` (deactivated on the target device anyway, see the [googletv docs](../../googletv/docs/TV_ANALYSE_2026-10-14.md)) |
| `TvContract` recommendations (`Channels`/`PreviewPrograms`) | posters and preview images of the recommendation row | **No.** They demand TV input access (`android.permission.ACCESS_TV_*`), which only a TV input service gets |
| `WallpaperManager` | the system wallpaper | **No.** Chromecast HD/Google TV has no wallpaper service; `getDrawable()` delivers nothing |
| an **own media file** | an image, pushed via `adb push` into the app folder | **Yes** — that is exactly what is implemented |

From that came the two options that have sat in the settings since 0.14:
**Dark** (default) and **Image** — and why that is so is in
[CONFIG.md](CONFIG.md#background--three-options-since-014--017). A
**video** background existed briefly in 0.14 as a third value; it was
removed again in 0.14.1, because a video permanently occupies a hardware
decoder on the Chromecast.

**Honest costs:** an image costs one surface-sized memory slot, nothing
else.
