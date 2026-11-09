# Configuration

Four things are configurable in Navigate: the **order of the apps**
(`navigate.conf`), whether the **"Now Playing" field** and the **clock**
appear and what sits **behind the grid** (`settings.conf`). Then the
**history** (`history.conf`) — what played, newest on top; the newest entry
is the one on display, everything older sits behind a long press of OK on
the Last-seen row. Each of the files lives in **two** places — the
difference matters:

| File | Content | Source of truth (Navigate reads here) | Import (`adb push`) |
|---|---|---|---|
| `navigate.conf` | Order — one package-name line per entry | `/data/data/com.piotv.navigate/files/navigate.conf` | `/storage/emulated/0/Android/data/com.piotv.navigate/files/navigate.conf` |
| `settings.conf` | Settings — `key=value` | `/data/data/com.piotv.navigate/files/settings.conf` | `/storage/emulated/0/Android/data/com.piotv.navigate/files/settings.conf` |
| `history.conf` | History — `time|package|line`, newest last | `/data/data/com.piotv.navigate/files/history.conf` | `/storage/emulated/0/Android/data/com.piotv.navigate/files/history.conf` (read-only — a `push` is only adopted when the file is newer) |

An import file is adopted only when it is **newer** than at the last
import. Both files use the same mechanics (in code
`data/ConfigFile.kt`): writing happens internally, importing from outside.

**Why not the external path directly?** Since Android 11, `/sdcard` runs
through the MediaProvider FUSE daemon: `stat` and `open` there **always** go
through a userspace daemon, and FUSE passthrough only helps read/write
*after* the open (source.android.com/docs/core/storage/fuse-passthrough).
The launcher checks the file on every `onResume` — on a 1.4 GB device a
FUSE round trip on the HOME path is measurable time.

**How does a pushed file arrive?** The import path is looked at at most
every 5 seconds, and only a file with a **newer** timestamp than at the
last import is adopted. After that the state lives internally; an old file
pushed once thus does not overwrite a change made on the TV (long press).

> Until 0.3 this route **never** worked: the throttle's marker sat at
> `Long.MIN_VALUE`, `now - Long.MIN_VALUE` overflows and turns negative, so
> the throttle always engaged. Found in 0.4 by the test
> `OrderStoreTest.a newer import file is adopted` (see
> [CHANGELOG](../CHANGELOG.md)).

## settings.conf — the settings

One line per entry, in the form `key=value`:

```ini
# Navigate — settings
now_playing=on
show_clock=on
background=dark
background_file=
```

| Key | Values | Effect |
|---|---|---|
| `now_playing` | `on` / `off` (also `true`/`false`, `1`/`0`, `an`/`aus`, `ja`/`nein`, case-insensitive) | shows the "Now Playing" field in the top-left — since 0.13 **one line per app** with an active media session, only while collapsed |
| `show_clock` | like `now_playing` | shows the **clock** in the top-right (switchable off since 0.14; "off" also means: no more ticking) |
| `background` | `dark` / `image` / `focus` (also `dunkel`, `bild`, `aus`, `fokus`) | what sits **behind the grid**: a solid color (default), an **image** (since 0.14) or the **TV banner** of the app under the focus (since 0.17) |
| `background_file` | path or bare name; **empty** = default name | the media file for `background=image` (since 0.14; unused in focus mode) |

Exactly as with the order, **tolerance is intentional**: `#` lines, empty
lines, surrounding whitespace, `now-playing` instead of `now_playing` and
unknown keys are understood or **ignored** — a hand-written file must never
keep Navigate from starting. An unknown **value** leaves the previous state
standing (no guessing).

On the TV the settings screen writes this file (**long OK on "…"**); via
`adb` it works just the same:

```bash
adb shell 'printf "now_playing=on\n" > /sdcard/Android/data/com.piotv.navigate/files/settings.conf'
```

> **Without notification access the field stays empty** — even with
> `now_playing=on`. Why is in [INSTALL.md](INSTALL.md), step 5b.

## Background — three options (since 0.14 / 0.17)

Behind the grid sits the solid, very dark background (default), an
**image** or the **focus banner**:

| `background` | What is on screen | File |
|---|---|---|
| `dark` (default) | `@color/bg_home` — the same tone as the launch window | none |
| `image` | an image, filling the area, scaled down to screen size | `background.jpg` |
| `focus` (since 0.17) | the **TV banner** of the app under the focus, filling the area, dimmed; apps without a banner: dark | none — it comes from the app manifest |

A **video** background existed in 0.14 as a fourth value; it was removed in
0.14.1. A `background=video` in an old file is now read as an unknown
value — the previous state stays standing.

The **focus banner** (0.17) does not come from a file: `background=focus`
puts the **TV banner** (`android:banner` from the app manifest) of the app
the focus sits on behind the grid — the same artwork the system launcher
shows behind the focused app. No special permission needed; apps without a
banner leave it dark, `background_file` stays unused in focus mode.

The file lives in the **external app folder** — the same folder the
configuration files are pushed into:

```bash
# Image
adb push vacation.jpg /sdcard/Android/data/com.piotv.navigate/files/background.jpg
adb shell 'printf "background=image\n" > /sdcard/Android/data/com.piotv.navigate/files/settings.conf'
```

A **custom** path works too (`background_file=/sdcard/Pictures/wall.jpg`);
`background_file=` **empty** takes the default name again. If the file is
missing or not a readable image, it stays dark — the settings screen names
the path, on the screen there is no error message.

**What deliberately does not exist:** the backgrounds Android TV has
"built in". The images of the Google TV ambient mode ("Backdrop") sit in
the private storage of the Google TV launcher, the recommendation/channel
images from `TvContract` demand a TV input permission, and
`WallpaperManager` delivers no image on the Chromecast HD. A normal
launcher can reach none of them (reasoning and evidence:
[LAST_SEEN_TITLE.md](LAST_SEEN_TITLE.md), section "Background").
The one exception is the **banner**: it sits in the manifest of the app
itself and is readable with `PackageManager` without a special permission —
exactly why `focus` can use it.

**Costs, honestly:** an image is cheap (decode once, then it is a surface).
That is why `dark` is the default and not `image`. A banner is loaded only
once the focus is on the app — one decode per app and process, after that
the memory cache hits (12 MB, capped at 1280 px width).

## navigate.conf — the order

**One package-name line per entry**, in display order. `#` starts a
comment, empty lines are allowed, unknown lines are **ignored** instead of
treated as an error: a hand-written file must never keep Navigate from
starting. Also tolerated: surrounding whitespace, comma lists and lines of
the old form `key=value`; duplicate entries drop out.

```ini
# Navigate — order of the apps
com.netflix.ninja
de.swr.avp.ard.tv
com.google.android.youtube.tv
```

- Entries that are (no longer) installed are silently skipped — the file
  cleans up after itself when an app is uninstalled.
- All apps not in the file follow afterwards in their language-sorted order
  (the same one the vendor launcher uses).

You find the package names with:

```bash
adb shell pm list packages -3
```

## Changing the order on the TV

**Long OK** on an app moves it **one position forward** and writes the file
immediately. If the app is already at the very front, nothing happens
(toast). From the "rest" (i.e. from the not-configured part) that means
appending is exactly one position forward.

For a large reshuffle the file is the more convenient route — it knows the
complete order, a long press only the next step.

## Backing up and resetting

```bash
# back up (the import path; the internal file is unreadable without root)
adb pull /sdcard/Android/data/com.piotv.navigate/files/navigate.conf ./navigate.conf

# restore — adopted on the next access (at most 5 s)
adb push ./navigate.conf /sdcard/Android/data/com.piotv.navigate/files/navigate.conf

# back to the factory state (no order = language-sorted)
adb push /dev/null /sdcard/Android/data/com.piotv.navigate/files/navigate.conf
```

The last command pushes an **empty** file to the import path; it is newer
than everything before it and is adopted — an empty file yields exactly the
base order.

## history.conf — the history

What played: one entry per line, `time|package|line`, newest last.

```ini
# Navigate — history (Now Playing)
1730000000000|com.zdf|ZDF · Daily News
1730000005000|com.spotify|Spotify · The Way
```

- Navigate writes the file itself: when a new title replaces an app's line,
  the old one moves here — instead of disappearing.
- **It is read as "last seen":** the **topmost** entry is the *last seen*,
  everything below it the *seen before* — the same order a person looks
  back in. The live rows (apps with a playing media session) sit above the
  history; the newest line is thus always the one playing now or last.
- **The content is stored, not the state.** The line in the history carries
  **no** "· paused": otherwise the same title would sit twice in the list —
  once with, once without the suffix (that is exactly how it looked on the
  target device). The display **above** the history keeps carrying the
  suffix, because a paused session would otherwise look like a playing one.
- **One rule for every app.** Which app holds the session stands in the
  `package` field; the line rule (`NowPlayingText.content`) knows no package
  name — Netflix, Disney+, ZDF, WOW and Prime are treated alike. An app
  becomes visible only when its session publishes **metadata**: Netflix
  sometimes delivers no title at all, and then it stays without a line
  (that is a limit of the app, not of Navigate).
- Shown is the newest on top: live rows first, below them **one** history
  line — the newest entry not playing right now ("Last seen"). Everything
  older is shown by the **context menu**: a long press of OK on that line
  unfolds the older entries (newest first, without the entries currently
  playing), OK on an entry brings the app back. The block does not grow
  with the archive; it stays at most 260 dp tall (about five rows at
  1080p), after that it scrolls within itself — several live rows can fill
  the window, the history no longer can.
- **The scrollbar stands permanently, and `▲`/`▼` move row by row** (0.15.1):
  every key press brings the next row into view instead of guessing it
  geometrically. At the lower end of the history `▼` leads back into the
  grid. In 0.15.0 the height limit was ineffective (`android:maxHeight`
  doesn't exist on a `ScrollView`) — the block grew with the history until
  the app row left the screen.
- At most 500 entries: older ones drop out when writing. Whoever wants more
  pulls via `adb pull` — the file is readable:

```bash
adb pull /sdcard/Android/data/com.piotv.navigate/files/history.conf ./history.conf
```
