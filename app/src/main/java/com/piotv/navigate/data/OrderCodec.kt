package com.piotv.navigate.data

/**
 * Die Reihenfolge als Textdatei: eine Paketnamen-Zeile pro Eintrag.
 *
 * ```
 * # Navigate — Reihenfolge der Apps
 * com.netflix.ninja
 * de.swr.avp.ard.tv
 * ```
 *
 * Reines Kotlin ohne Android-Typen. Die Datei ist die **einzige** Einstellung
 * dieser Oberfläche, und ein Parser ist genau die Stelle, an der eine
 * handgeschriebene Datei still kaputtgeht (Leerzeichen, Kommas, doppelte
 * Einträge) — deshalb getrennt testbar (OrderCodecTest).
 *
 * Toleranz ist Absicht: Leerzeilen, `#`-Kommentare, umgebende Leerzeichen,
 * Komma-Listen und Zeilen der Form `order=…` (frühere Fassungen hatten
 * `schlüssel=wert`) werden verstanden. Doppelte Einträge fallen weg — ein
 * doppelter Name würde eine App zweimal in die erste Zeile setzen.
 */
object OrderCodec {

    fun parse(text: String?): List<String> {
        if (text == null) return emptyList()
        val result = ArrayList<String>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) continue
            // "order=a,b" (alte Form) genauso lesen wie "a,b" oder "a".
            val value = line.substringAfter('=', line)
            for (part in value.split(',')) {
                val name = part.trim()
                if (name.isEmpty()) continue
                if (result.contains(name)) continue
                result.add(name)
            }
        }
        return result
    }

    fun serialize(order: List<String>): String = buildString {
        appendLine("# Navigate — Reihenfolge der Apps")
        appendLine("#")
        appendLine("# Eine Paketnamen-Zeile pro Eintrag, in Anzeige-Reihenfolge.")
        appendLine("# Diese Datei ist die einzige Einstellung: die Home-Seite zeigt")
        appendLine("# nur die erste Rasterzeile, den Rest holt der „…“-Knopf.")
        appendLine("# Zeilen mit '#' sind Kommentare, unbekannte Zeilen werden ignoriert.")
        appendLine()
        for (packageName in order) appendLine(packageName)
    }
}
