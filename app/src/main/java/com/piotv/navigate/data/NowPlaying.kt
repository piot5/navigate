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
                    // Der „Jetzt läuft“-Bildschirm der App, wenn sie einen
                    // anbietet: der vorgesehene Weg, aus einer Session heraus zur
                    // App zu kommen. Viele TV-Apps setzen ihn nicht — deshalb gibt
                    // es den zweiten Weg in LauncherActivity.openRow.
                    activity = sessionActivity(controller),
                    art = metadata?.let { art(it, artSizePx) },
                    // „Diese App hat beendet“ ist etwas anderes als „diese App
                    // sagt gerade nichts“ — siehe [isEnded].
                    ended = isEnded(controller),
                )
            )
        }

        // Was spielt, steht oben, was beendet ist unten. `sortedWith` ist stabil:
        // innerhalb einer Gruppe bleibt die Reihenfolge des Systems.
        return tracks.sortedWith(
            compareByDescending<NowPlayingTrack> { !it.ended }.thenByDescending { it.info.playing }
        )
    }

    /**
     * Hat die App **beendet**?
     *
     * `STATE_STOPPED` und `STATE_ERROR` sind ein ausdrückliches „hier läuft nichts
     * mehr“ — das ist die Erlaubnis, eine Zeile zu entfernen. `STATE_NONE` zählt
     * **nicht** dazu: das ist auch der Zustand einer frisch angelegten Session, und
     * eine App, die gerade startet, soll ihre letzte Zeile nicht verlieren.
     */
    private fun isEnded(controller: MediaController): Boolean = try {
        val state = controller.playbackState?.state
        state == PlaybackState.STATE_STOPPED || state == PlaybackState.STATE_ERROR
    } catch (e: Exception) {
        false
    }

    /** Der „Jetzt läuft“-Bildschirm der Session — oder `null`. */
    private fun sessionActivity(controller: MediaController): PendingIntent? = try {
        controller.sessionActivity
    } catch (e: Exception) {
        null
    }

    /**
     * Das Cover in Symbolgröße — mit einem **kleinen** Cache.
     *
     * Ohne den Cache würde alle fünf Sekunden dasselbe Bild neu aus der fremden
     * App geholt und verkleinert; ein 1000×1000-Cover sind 4 MB, die auf einem
     * 1,4-GB-Gerät niemand fünfmal pro Minute durch den Speicher schaufeln will.
     * Bis 0.12 war der Cache **ein** Platz (es gab ja nur eine Zeile); seit es
     * eine Zeile je App gibt, hält ein LRU mehrere Cover — begrenzt nach Bytes
     * statt nach Anzahl, weil die Größe des Ergebnisses von der fremden App
     * abhängt.
     *
     * Der Schlüssel ist das **Lied** ([artKey]) und nicht das Bitmap-Objekt:
     * jede Abfrage liefert ein neues. Eine Session **ohne** Cover wird nicht
     * gespeichert — dort ist die Abfrage billig, der Schlüssel fehlt einfach.
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

    /** Lied-Kennung plus Zielgröße — ändert sich das Lied, gilt der Cache nicht mehr. */
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

    /** Album-Cover, dann Artwork, dann Anzeige-Symbol — mehr bietet die API nicht. */
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
     * Verkleinert das Cover auf höchstens `sizePx` (längste Kante). Kleine Bilder
     * bleiben, wie sie sind — die fremde App hat sie schon passend gerechnet.
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
     * Die erste gesetzte Zeichenkette — fremde Apps füllen nicht immer alle
     * Schlüssel. `getString` wirft, wenn ein Schlüssel nicht als Text abgelegt
     * wurde (kommt bei Drittanbieter-Sessions vor), deshalb einzeln abgesichert.
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
        // Kein Benachrichtigungszugriff: auf einem frisch eingerichteten Gerät
        // der Normalfall, kein Fehler. Siehe docs/INSTALL.md, Schritt 5b.
        null
    } catch (e: Exception) {
        // Der Session-Dienst antwortet gerade nicht (Gerät fährt herunter o. Ä.).
        null
    }

    private companion object {
        /**
         * Obergrenze des Cover-Caches. 4 MB sind ein paar Alben-Bilder in
         * Symbolgröße und auf einem 1,4-GB-Gerät vertretbar — dieselbe
         * Größenordnung wie der Symbol-Cache eines Rasters, nicht wie ein
         * ganzer Symbol-Cache.
         */
        const val MAX_ART_BYTES = 4 * 1024 * 1024

        /**
         * Reihenfolge der Bild-Schlüssel: das Album-Cover zuerst, dann das
         * generische Artwork, dann das Anzeige-Symbol. Manche Apps füllen nur
         * eines davon — bei Radio-Streams fehlt `METADATA_KEY_ALBUM_ART`
         * regelmäßig.
         *
         * Nur **Bitmap**-Schlüssel: die `*_URI`-Varianten gäbe es auch, sie
         * verlangen aber einen zweiten Weg (Content-Resolver, fremder
         * ContentProvider) für ein Bild, das die Session meist schon mitliefert.
         */
        val ART_KEYS = arrayOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON,
        )
    }
}

/**
 * Was gerade läuft — die Zeile **und** alles, was man zum Anspringen braucht.
 *
 * Drei Dinge kommen hier zusammen: [info] (Titel, Interpret, Zustand — rein und
 * ohne Android-Typen, deshalb prüfbar), [packageName] (welche App die Session
 * hält) und [activity] — der „Jetzt läuft“-Bildschirm der fremden App als
 * [PendingIntent]: der Weg, den die Session **selbst** für „bring mich nach
 * vorne“ anbietet. Dazu [art], das Cover in Symbolgröße.
 *
 * Warum das nicht in [NowPlayingInfo] steht: dort steht Text. `PendingIntent` und
 * `Bitmap` sind Android-Typen — sie würden die reine Zeilen-Regel
 * ([NowPlayingText]) ohne Gerät unprüfbar machen.
 */
class NowPlayingTrack(
    val info: NowPlayingInfo,
    /** Paket der Session — `""`, wenn das System keines nennt. */
    val packageName: String,
    /** `null`, wenn die App keinen „Jetzt läuft“-Bildschirm anbietet. */
    val activity: PendingIntent?,
    /** Cover in Symbolgröße oder `null`. */
    val art: Bitmap?,
    /**
     * `true`, wenn die App **beendet** hat (Session steht auf `STOPPED`/`ERROR`).
     * Dann darf ihre Zeile verschwinden. „Keine Session“ heißt dagegen nur „jetzt
     * nichts zu sagen“ und lässt sie stehen — siehe [NowPlayingBoard].
     */
    val ended: Boolean = false,
)
