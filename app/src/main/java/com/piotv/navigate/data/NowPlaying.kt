package com.piotv.navigate.data

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.LruCache
import com.piotv.navigate.NowPlayingListener
import kotlin.math.max

/**
 * What's playing now — read from the system's media session.
 *
 * The route here is not the obvious one: `MediaSessionManager.getActiveSessions`
 * requires the caller to be a **registered notification listener**
 * (or to hold `MEDIA_CONTENT_CONTROL` — a signature permission that
 * `pm grant` cannot hand out). That is what [NowPlayingListener] is for: it is
 * the credential this class presents when polling, and nothing more. Without
 * the grant the call throws a `SecurityException` — it is swallowed here,
 * and the field stays empty. Same stance as with "green": rather
 * nothing than something wrong.
 *
 * There is **no** callback channel (no `addOnActiveSessionsChangedListener`): the
 * home page polls on the beat of its own timer while it is visible and
 * collapsed. A listener would additionally need a `MediaController.Callback`
 * per session to see track changes within a session — more code,
 * more state, the same visible effect.
 */
class NowPlaying(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(MediaSessionManager::class.java)

    /**
     * The address of the "Now Playing" service under which Navigate has to
     * register with the system (and which `getActiveSessions` requires for that).
     *
     * The value is **not** hard-coded to the qualified package name
     * (`com.piotv.navigate`): `applicationIdSuffix` (debug builds) and
     * other signature configurations come with a different name. Letting the
     * name follow `applicationContext.packageName` makes it adapt
     * automatically, whether the release APK is `com.piotv.navigate` or the
     * debug APK is `com.piotv.navigate.debug`. That way the notification-
     * listener grant (see [NowPlayingListener] and docs/INSTALL.md,
     * step 5b) always targets the package of the currently installed APK
     * in `adb shell cmd notification allow_listener`.
     */
    private val listener = ComponentName(
        appContext.packageName,
        NowPlayingListener::class.java.name,
    )

    /**
     * Scaled-down covers, limited by **bytes** (see [art]).
     */
    private val artCache = object : LruCache<String, Bitmap>(MAX_ART_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * May Navigate see the sessions?
     *
     * Needed **only** by the settings screen, so it can honestly say
     * why nothing shows up. `false` means "no access **or** the service
     * is not responding" — both lead to the same empty field.
     */
    fun hasAccess(): Boolean = sessions() != null

    /**
     * **All** sessions that have something to show — one per app.
     *
     * Until 0.12 there was exactly one session here: the one "making the
     * sound". With an audiobook paused next to a radio station, one of the
     * two entries disappeared. Now every app with an active session gets its
     * own line.
     *
     * Per app, what is actually playing wins; otherwise the first session with
     * a title (a paused recording is still "what's playing now",
     * see [NowPlayingText.PAUSED]).
     *
     * @param artSizePx Edge length a cover is scaled down to. `0` means
     *   "no image" — then none is fetched either.
     * @return `null` when the poll **failed** (no access,
     *   the session service is not responding). An empty list, by contrast,
     *   means "looked successfully, nothing is playing". The difference is
     *   what keeps a failed look from clearing the display —
     *   see [NowPlayingBoard.observe].
     */
    fun active(artSizePx: Int = 0): List<NowPlayingTrack>? {
        val controllers = sessions() ?: return null

        // One session per package: the playing one wins, otherwise the first. A
        // session **without** metadata stays — it is the answer "the app
        // is there but saying nothing right now" (loading screen, ads, menu) and
        // must not delete an existing line (see [NowPlayingBoard]).
        val chosen = LinkedHashMap<String, MediaController>()
        for (controller in controllers) {
            val packageName = try {
                controller.packageName.orEmpty()
            } catch (e: Exception) {
                ""
            }
            // Without a package the line can be neither named nor jumped to.
            if (packageName.isEmpty()) continue
            val existing = chosen[packageName]
            if (existing == null || (!isPlaying(existing) && isPlaying(controller))) {
                chosen[packageName] = controller
            }
        }

        val tracks = ArrayList<NowPlayingTrack>(chosen.size)
        for ((packageName, controller) in chosen) {
            val metadata = metadata(controller)
            tracks.add(
                NowPlayingTrack(
                    // Title and artist can be missing. Then the line is empty,
                    // and the display keeps what it had — instead of deleting it.
                    info = NowPlayingInfo(
                        title = metadata?.let {
                            text(it, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                        },
                        artist = metadata?.let {
                            text(it, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                        },
                        playing = isPlaying(controller),
                    ),
                    packageName = packageName,
                    // The app's "now playing" screen, when it offers one: the
                    // intended route out of a session and into the app. Many
                    // TV apps don't set it — hence the second route in
                    // LauncherActivity.openRow.
                    activity = sessionActivity(controller),
                    art = metadata?.let { art(it, artSizePx) },
                    // "This app has ended" is something different from "this
                    // app isn't saying anything right now" — see [isEnded].
                    ended = isEnded(controller),
                )
            )
        }

        // What plays sits at the top, what has ended at the bottom. `sortedWith`
        // is stable: within a group the system's order stays.
        return tracks.sortedWith(
            compareByDescending<NowPlayingTrack> { !it.ended }.thenByDescending { it.info.playing }
        )
    }

    /**
     * Has the app **ended**?
     *
     * `STATE_STOPPED` and `STATE_ERROR` are an explicit "nothing plays here
     * anymore" — that is the permission to remove a line. `STATE_NONE` does
     * **not** count: it is also the state of a freshly created session, and
     * an app that is just starting should not lose its last line.
     */
    private fun isEnded(controller: MediaController): Boolean = try {
        val state = controller.playbackState?.state
        state == PlaybackState.STATE_STOPPED || state == PlaybackState.STATE_ERROR
    } catch (e: Exception) {
        false
    }

    /** The session's "now playing" screen — or `null`. */
    private fun sessionActivity(controller: MediaController): PendingIntent? = try {
        controller.sessionActivity
    } catch (e: Exception) {
        null
    }

    /**
     * The cover at icon size — with a **small** cache.
     *
     * Without the cache the same image would be fetched from the foreign app
     * and scaled down every five seconds; a 1000×1000 cover is 4 MB, and on
     * a 1.4 GB device nobody wants it shoved through memory five times a
     * minute. Until 0.12 the cache had **one** slot (there was only one
     * line, after all); since there is one line per app, an LRU holds
     * several covers — limited by bytes instead of by count, because the
     * size of the result depends on the foreign app.
     *
     * The key is the **song** ([artKey]), not the Bitmap object: every poll
     * delivers a new one. A session **without** a cover is not stored — the
     * poll is cheap there, the key is simply missing.
     */
    private fun art(metadata: MediaMetadata, sizePx: Int): Bitmap? {
        if (sizePx <= 0) return null
        val key = artKey(metadata, sizePx)
        artCache.get(key)?.let { return it }
        val source = artwork(metadata) ?: return null
        val scaled = small(source, sizePx)
        artCache.put(key, scaled)
        return scaled
    }

    /** Song identity plus target size — when the song changes, the cache no longer applies. */
    private fun artKey(metadata: MediaMetadata, sizePx: Int): String {
        val uri = text(
            metadata,
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        )
        val title = text(metadata, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = text(metadata, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        return "$sizePx|$uri|$title|$artist"
    }

    /** Album cover, then artwork, then display icon — that is all the API offers. */
    private fun artwork(metadata: MediaMetadata): Bitmap? {
        for (key in ART_KEYS) {
            val value = try {
                metadata.getBitmap(key)
            } catch (e: Exception) {
                null
            }
            if (value != null) return value
        }
        return null
    }

    /**
     * Scales the cover down to at most `sizePx` (longest edge). Small images
     * stay as they are — the foreign app already sized them to fit.
     */
    private fun small(source: Bitmap, sizePx: Int): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= sizePx) return source
        val factor = sizePx.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            max(1, (source.width * factor).toInt()),
            max(1, (source.height * factor).toInt()),
            true,
        )
    }

    private fun isPlaying(controller: MediaController): Boolean = try {
        controller.playbackState?.state == PlaybackState.STATE_PLAYING
    } catch (e: Exception) {
        false
    }

    private fun metadata(controller: MediaController): MediaMetadata? = try {
        controller.metadata
    } catch (e: Exception) {
        null
    }

    /**
     * The first string that is set — foreign apps don't always fill every
     * key. `getString` throws when a key wasn't stored as text (happens with
     * third-party sessions), hence the per-key guarding.
     */
    private fun text(metadata: MediaMetadata, vararg keys: String): String? {
        for (key in keys) {
            val value = try {
                metadata.getString(key)
            } catch (e: Exception) {
                null
            }
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun sessions(): List<MediaController>? = try {
        manager?.getActiveSessions(listener)
    } catch (e: SecurityException) {
        // No notification access: on a freshly set-up device that is the
        // normal case, not an error. See docs/INSTALL.md, step 5b.
        null
    } catch (e: Exception) {
        // The session service isn't responding right now (device shutting
        // down or such).
        null
    }

    private companion object {
        /**
         * Upper bound of the cover cache. 4 MB are a few album images at
         * icon size and are defensible on a 1.4 GB device — the same order
         * of magnitude as the icon cache of one grid, not a whole icon
         * cache.
         */
        const val MAX_ART_BYTES = 4 * 1024 * 1024

        /**
         * Order of the image keys: the album cover first, then the generic
         * artwork, then the display icon. Some apps fill only one of them —
         * with radio streams `METADATA_KEY_ALBUM_ART` is regularly missing.
         *
         * Only **Bitmap** keys: the `*_URI` variants exist too, but they
         * demand a second route (ContentResolver, a foreign ContentProvider)
         * for an image the session usually ships along anyway.
         */
        val ART_KEYS = arrayOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON,
        )
    }
}

/**
 * What is playing right now — the line **and** everything needed to jump there.
 *
 * Three things come together here: [info] (title, artist, state — pure and
 * without Android types, hence testable), [packageName] (which app holds the
 * session) and [activity] — the foreign app's "now playing" screen as a
 * [PendingIntent]: the route the session **itself** offers for "bring me
 * forward". Plus [art], the cover at icon size.
 *
 * Why this doesn't live in [NowPlayingInfo]: that one holds text.
 * `PendingIntent` and `Bitmap` are Android types — they would make the pure
 * line rule ([NowPlayingText]) untestable without a device.
 */
class NowPlayingTrack(
    val info: NowPlayingInfo,
    /** The session's package — `""` when the system names none. */
    val packageName: String,
    /** `null` when the app offers no "now playing" screen. */
    val activity: PendingIntent?,
    /** The cover at icon size, or `null`. */
    val art: Bitmap?,
    /**
     * `true` when the app has **ended** (the session sits at `STOPPED`/`ERROR`).
     * Then its line may disappear. "No session", by contrast, only means
     * "nothing to say right now" and leaves the line standing — see
     * [NowPlayingBoard].
     */
    val ended: Boolean = false,
)
