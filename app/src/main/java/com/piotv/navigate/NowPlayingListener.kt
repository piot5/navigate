package com.piotv.navigate

import android.service.notification.NotificationListenerService

/**
 * Der Zugangsschlüssel zum „Now Playing“-Feld — und sonst nichts.
 *
 * Eine normale App darf die aktiven Media-Sessions **nicht** abfragen:
 * `MediaSessionManager.getActiveSessions` verlangt, dass der Aufrufer ein
 * eingetragener Benachrichtigungs-Listener ist (oder `MEDIA_CONTENT_CONTROL`
 * hält — eine Signatur-Berechtigung, die `pm grant` nicht vergeben kann).
 *
 * Dieser Dienst erfüllt genau die erste Bedingung. Er tut dabei **nichts**:
 * keine Benachrichtigung wird gelesen, gespeichert oder weitergegeben — der
 * eingetragene Komponentenname ist der Ausweis, den `getActiveSessions`
 * verlangt. Ohne die Freigabe bleibt der Dienst ungebunden und das Feld leer;
 * Navigate läuft unverändert weiter (siehe docs/INSTALL.md, Schritt 5b).
 *
 * Der Name hier ist auch der Name in der Freigabe:
 * `adb shell cmd notification allow_listener com.piotv.navigate/.NowPlayingListener`
 */
class NowPlayingListener : NotificationListenerService()
