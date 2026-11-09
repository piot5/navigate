# Installation (Chromecast / Google TV)

Tested on a **Chromecast HD** (Google TV, Android 14, 1920×1080, TV
density, 1.4 GB RAM). The steps apply to all Google TV devices; on Fire TV
/ other Android TV devices the vendor launcher has a different name, the
procedure stays the same.

## 1. Build the APK and install it

```bash
cd projects/navigate
bash build_navigate.sh release
bash install_navigate.sh app/build/outputs/apk/release/app-release.apk
```

`install_navigate.sh` picks the first reachable **TV device** on its own
(`ro.build.characteristics` contains `tv`) — emulators and phones are
skipped, so that a test phone hanging on adb at the same time is not the
wrong target. A different target is forced with
`NAVIGATE_DEVICE=192.168.178.23:5555` (then nothing is checked).
The prerequisite is ADB switched on on the device
(developer options → USB debugging, on Google TV additionally
"network debugging", then `adb connect <ip>:5555`).

After installing, Navigate is launchable but not yet the home app.

## 2. Register it as the home app

On Android 10+ the **role** `android.app.role.HOME` decides, not an older
"preferred activity" entry. `cmd package set-home-activity` sets the entry
but not reliably the role — the role is the dependable route:

```bash
adb shell cmd role add-role-holder android.app.role.HOME com.piotv.navigate
adb shell cmd role get-role-holders android.app.role.HOME   # check
```

> **Only one Navigate variant on the device.** The debug APK
> (`com.piotv.navigate.debug`) and the release APK (`com.piotv.navigate`)
> **both** carry a HOME filter. When both sit on the device, the HOME press
> shows a **chooser dialog** instead of the grid (measured on 2026-10-29 on
> the target device: `topResumedActivity=…ResolverActivity`).
> So before registering the role, remove the respective other variant:
>
> ```bash
> adb uninstall com.piotv.navigate.debug   # or com.piotv.navigate
> ```

## 3. Switch off Google's home watchdog

Without this step `com.google.android.tungsten.setupwraith` takes the HOME
key back — verifiable on the test device: the HOME press started
`setupwraith/.RecoveryActivity` (its HOME filter has `priority=1`, a
watchdog starts it again and again), although Navigate held the HOME role.
Navigate therefore declares `priority=10` itself; that is still not enough
against the watchdog.

```bash
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

The Google TV launcher (`com.google.android.apps.tv.launcherx`) does
**not** have to be deactivated for this — contrary to what some guides
claim. If it is already deactivated (e.g. because another launcher ran
before), that does no harm.

## 4. Done

Press HOME: the home page appears — at the bottom a row with the first
apps, below it the "…" button. "…" unfolds the whole grid and folds it
back.

## 5. Optional: accesses (one by one, independent)

Both accesses are voluntary, each unlocks exactly **one** feature, and
both can be revoked individually (see "Way back"). Without them Navigate
starts unchanged — only the one field or the exact answer to "back into
the last app" is missing.

### 5a. Usage access — for "back into the last app"

On the **already visible** home page, HOME and BACK bring back the last
used app. Where Navigate knows that from hangs on an optional app op:

```bash
adb shell appops set com.piotv.navigate GET_USAGE_STATS allow
adb shell appops get com.piotv.navigate GET_USAGE_STATS   # check
```

- **With** the access Navigate reads the usage statistics (short window,
  then a long one) — exact, also for apps not started from here.
- **Without** it Navigate remembers the app last **started via Navigate**.
  In the normal case that is right and otherwise bothers nobody: without a
  known app the home page simply stays.

Alternatively on the device: **Settings → Apps → Special access →
Usage access → Navigate**.

### 5b. Notification access — for "Now Playing"

The field in the top-left shows app, artist, title and cover of the playing
media session — **without this access it stays empty**. The reason is not
convenience but a limit of Android: `getActiveSessions` requires the caller
to be a registered notification listener. (The alternative would be
`MEDIA_CONTENT_CONTROL` — a signature permission that `pm grant` cannot
hand out.)

```bash
adb shell cmd notification allow_listener com.piotv.navigate/com.piotv.navigate.NowPlayingListener
# Check — the name has to sit in the list:
adb shell settings get secure enabled_notification_listeners
```

Navigate reads **no** notifications doing this: `NowPlayingListener` is an
empty service, its registered name is the credential for querying the
media sessions. Without the grant the field stays empty — and the settings
screen says so too (long OK on "…").

Alternatively on the device: **Settings → Apps → Special access →
Notification access → Navigate — Now Playing**.

## Controls

| Key | Effect |
|---|---|
| HOME | from an app: the home page · on the home page: back into the last app |
| ◀ ▲ ▼ ▶ | focus in the grid |
| OK | launch an app · on "…": unfold/fold the grid · on "Now Playing": back into the playing app |
| BACK | fold the unfolded grid · otherwise back into the last app |
| long OK | on an app: the context menu (position forward/back · uninstall · close · close all) · on "…": settings |


## Way back

Everything is reversible. Order doesn't matter:

```bash
# 1. Watchdog back on
adb shell pm enable com.google.android.tungsten.setupwraith

# 2. Role to the previous launcher (example: Projectivy)
adb shell cmd role add-role-holder android.app.role.HOME com.spocky.projengmenu
# or entirely without a home role:
adb shell cmd role remove-role-holder android.app.role.HOME com.piotv.navigate

# 3. Switch the Google TV launcher back on, if it was deactivated
adb shell pm enable com.google.android.apps.tv.launcherx

# 4. Revoke the accesses (only what was set)
adb shell appops set com.piotv.navigate GET_USAGE_STATS default
adb shell cmd notification disallow_listener com.piotv.navigate/com.piotv.navigate.NowPlayingListener

# 5. Uninstall Navigate (optional)
adb uninstall com.piotv.navigate
```

After `pm enable` of the Google launcher and the HOME press the system
asks which home app to use.

## Troubleshooting

**HOME still shows the old launcher.** Usually step 3 is missing or an old
home task is still running. Check:

```bash
adb shell cmd role get-role-holders android.app.role.HOME
adb shell dumpsys package com.google.android.tungsten.setupwraith | grep 'User 0'
# enabled=3 means: deactivated (that is how it should look)
```

Getting rid of an old home task: `adb shell am force-stop <old-launcher>`.

**The row above the playing app is missing.** It has not existed since
0.6 — the HOME press always shows the grid. If something else happens
instead, the home role is not set (step 2) or the Google TV watchdog is
active again (step 3).

**The "Now Playing" field stays empty.** Three possible causes, check in
this order:

```bash
# 1. Is the setting on? (long OK on "…" — or via the file)
adb shell cat /sdcard/Android/data/com.piotv.navigate/files/settings.conf
# 2. Does Navigate have notification access? (step 5b)
adb shell settings get secure enabled_notification_listeners
# 3. Is anything playing at all? (empty = nothing to show, the field stays off)
adb shell dumpsys media_session | grep -E 'package=|state='
```

Empty is not an error: without a session the block is **hidden** instead of
showing a placeholder. A paused title is shown, by contrast (with
`· paused`) — otherwise it would look like "playing".

**An icon is missing from the grid.** Check whether the app is launchable
at all:

```bash
adb shell cmd package query-activities -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER | grep -i <name>
```

Apps without a launcher entry (pure libraries, system services) do not
belong in the grid. TV apps with only `LEANBACK_LAUNCHER` are picked up.
