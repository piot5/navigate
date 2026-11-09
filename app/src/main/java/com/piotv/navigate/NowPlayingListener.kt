package com.piotv.navigate

import android.service.notification.NotificationListenerService

/**
 * The access key to the "Now Playing" field — and nothing else.
 *
 * A normal app must **not** query the active media sessions:
 * `MediaSessionManager.getActiveSessions` requires the caller to be a
 * registered notification listener (or to hold `MEDIA_CONTENT_CONTROL` — a
 * signature permission that `pm grant` cannot grant).
 *
 * This service fulfills exactly the first condition. And it does **nothing**:
 * no notification is read, stored or passed on — the registered component
 * name is the credential that `getActiveSessions` requires. Without the
 * grant the service stays unbound and the field stays empty; Navigate keeps
 * running unchanged (see docs/INSTALL.md, step 5b).
 *
 * The name here is also the name in the grant:
 * `adb shell cmd notification allow_listener <installed package>/.NowPlayingListener`
 * (release: `com.piotv.navigate/`, debug: `com.piotv.navigate.debug/`)
 */
class NowPlayingListener : NotificationListenerService()
