package com.piotv.navigate

/**
 * Die Regeln, die ein HOME-Druck auslöst — als reine Funktionen ohne
 * Android-Typen.
 *
 * Warum das hier steht und nicht in [LauncherActivity]: an genau diesen drei
 * Regeln ist die erste Fassung gescheitert (siehe CHANGELOG, „Behoben"), und
 * keine von ihnen braucht ein Gerät zum Nachdenken. Die Activity hält nur noch
 * den Zustand (`dockOnScreen`, `homeIntentPending`, `launchedInternally`) und
 * fragt hier — dadurch ist die Entscheidung prüfbar (HomeKeyPolicyTest), und
 * zwar genau in den Fällen, die auf dem Chromecast gemessen wurden.
 */
object HomeKeyPolicy {

    /**
     * Schließt dieser HOME-Intent die Zeile?
     *
     * Der zweite HOME-Druck bei stehender Zeile schließt sie und bringt die App
     * zurück. Wichtig: die Antwort hängt **nicht** am Lebenszyklus. Das System
     * pausiert die Activity bei jedem HOME-Druck, *bevor* es den Intent
     * zustellt (auf dem Zielgerät gemessen: onPause → onNewIntent → onResume) —
     * ein `isResumed` wäre dort immer schon `false`. Genau daran ist die erste
     * Fassung gescheitert; deshalb kommt hier der eigene Merker an.
     */
    fun dismissesDock(intentIsHome: Boolean, dockOnScreen: Boolean): Boolean =
        intentIsHome && dockOnScreen

    /**
     * Soll `onResume` auf die Home-Seite zurückfallen?
     *
     * Der Fall „ohne HOME-Druck wieder oben, während die Zeile steht" heißt: die
     * App davor ist beendet, es läuft nichts mehr. Ein gerade konsumierter
     * HOME-Intent oder der Rückweg aus den eigenen Einstellungen ist kein
     * solcher Fall.
     */
    fun fallsBackToGrid(
        homeIntentPending: Boolean,
        launchedInternally: Boolean,
        mode: LauncherMode,
    ): Boolean = !homeIntentPending && !launchedInternally && mode == LauncherMode.DOCK

    /**
     * Wurde Navigate **nicht** über HOME gestartet (TV-Apps-Zeile,
     * `LEANBACK_LAUNCHER`)? Dann will der Nutzer den Launcher sehen, und die
     * teure Vordergrund-Abfrage entfällt.
     *
     * `null` heißt „kein Intent" — bei einer Activity praktisch unmöglich — und
     * verhält sich wie ein HOME-Intent: dann entscheidet der Vordergrund.
     */
    fun mustShowGrid(isHomeIntent: Boolean?): Boolean = isHomeIntent == false
}
