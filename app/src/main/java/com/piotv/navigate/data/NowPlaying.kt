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
 * Was gerade läuft — gelesen aus der Media-Session des Systems.
 *
 * Der Weg ist nicht der naheliegende: `MediaSessionManager.getActiveSessions`
 * verlangt, dass der Aufrufer ein **eingetragener Benachrichtigungs-Listener**
 * ist (oder `MEDIA_CONTENT_CONTROL` hält — eine Signatur-Berechtigung, die
 * `pm grant` nicht vergeben kann). Deshalb gibt es [NowPlayingListener]: er ist
 * der Ausweis, den diese Klasse beim Abfragen vorzeigt, und sonst nichts. Ohne
 * die Freigabe wirft der Aufruf eine `SecurityException` — die wird hier
 * geschluckt, und das Feld bleibt leer. Dieselbe Haltung wie bei „grün“: lieber
 * nichts als falsch.
 *
 * Es gibt **keinen** Rückkanal (kein `addOnActiveSessionsChangedListener`): die
 * Home-Seite fragt im Takt ihres eigenen Timers, solange sie sichtbar und
 * zugeklappt ist. Ein Listener bräuchte zusätzlich einen `MediaController.Callback`
 * pro Session, um Titelwechsel innerhalb einer Session zu sehen — mehr Code,
 * mehr Zustand, derselbe sichtbare Effekt.
 */
class NowPlaying(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(appContext, NowPlayingListener::class.java)

    /**
     * Verkleinerte Cover, nach **Bytes** begrenzt (siehe [art]).
     */
    private val artCache = object : LruCache<String, Bitmap>(MAX_ART_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * Darf Navigate die Sessions sehen?
     *
     * Wird **nur** von den Einstellungen gebraucht, um ehrlich sagen zu können,
     * warum nichts erscheint. `false` heißt „kein Zugriff **oder** der Dienst
     * antwortet nicht“ — beides führt zum selben leeren Feld.
     */
    fun hasAccess(): Boolean = sessions() != null

    /**
     * **Alle** Sessions, die etwas zu zeigen haben — eine je App.
     *
     * Bis 0.12 gab es hier genau eine Session: die, die „den Ton macht“. Läuft
     * ein Hörbuch pausiert und daneben ein Radiosender, verschwand damit eine
     * der beiden Angaben. Jetzt bekommt jede App mit einer aktiven Session ihre
     * eigene Zeile.
     *
     * Vorrang **je App** hat, was wirklich spielt; sonst die erste Session mit
     * einem Titel (eine pausierte Aufnahme ist immer noch „was gerade läuft“,
     * siehe [NowPlayingText.PAUSED]).
     *
     * @param artSizePx Kantenlänge, auf die ein Cover verkleinert wird. `0` heißt
     *   „kein Bild“ — dann wird auch keines geholt.
     * @return `null`, wenn die Abfrage **fehlgeschlagen** ist (kein Zugriff,
     *   Session-Dienst antwortet nicht). Eine leere Liste heißt dagegen
     *   „erfolgreich nachgesehen, es läuft nichts“. Der Unterschied ist die
     *   Grundlage dafür, dass ein fehlgeschlagener Blick die Anzeige nicht
     *   löscht — siehe [NowPlayingBoard.observe].
     */
    fun active(artSizePx: Int = 0): List<NowPlayingTrack>? {
        val controllers = sessions() ?: return null

        // Je Paket eine Session: die spielende gewinnt, sonst die erste.
        val chosen = LinkedHashMap<String, MediaController>()
        for (controller in controllers) {
            val packageName = try {
                controller.packageName.orEmpty()
            } catch (e: Exception) {
                ""
            }
            // Ohne Paket lässt sich die Zeile weder benennen noch anspringen.
            if (packageName.isEmpty()) continue
            if (metadata(controller) == null) continue
            val existing = chosen[packageName]
            if (existing == null || (!isPlaying(existing) && isPlaying(controller))) {
                chosen[packageName] = controller
            }
        }

        val tracks = ArrayList<NowPlayingTrack>(chosen.size)
        for ((packageName, controller) in chosen) {
            val metadata = metadata(controller) ?: continue
            val info = NowPlayingInfo(
                title = text(metadata, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                artist = text(metadata, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                playing = isPlaying(controller),
            )
            // Ohne Titel und ohne Interpret gibt es nichts zu zeigen — dann auch
            // keine „pausiert“-Zeile.
            if (NowPlayingText.line(info).isEmpty()) continue

            tracks.add(
                NowPlayingTrack(
                    info = info,
                    packageName = packageName,
                    // Der „Jetzt läuft“-Bildschirm der App, wenn sie einen
                    // anbietet: der vorgesehene Weg, aus einer Session heraus zur
                    // App zu kommen. Viele TV-Apps setzen ihn nicht — deshalb gibt
                    // es den zweiten Weg in LauncherActivity.openRow.
                    activity = sessionActivity(controller),
                    art = art(metadata, artSizePx),
                )
            )
        }

        // Was spielt, steht oben. `sortedByDescending` ist stabil: innerhalb
        // einer Gruppe bleibt die Reihenfolge des Systems.
        return tracks.sortedByDescending { it.info.playing }
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
)
