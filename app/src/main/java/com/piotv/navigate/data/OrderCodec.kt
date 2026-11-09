package com.piotv.navigate.data

/**
 * The order as a text file: one package-name line per entry.
 *
 * ```
 * # Navigate — app order
 * com.netflix.ninja
 * de.swr.avp.ard.tv
 * ```
 *
 * Pure Kotlin without Android types. It is the **order** of the UI; the
 * settings live alongside in `settings.conf` ([SettingsCodec]). A parser is
 * exactly the place where a hand-written file silently breaks (spaces,
 * commas, duplicate entries) — hence separately testable (OrderCodecTest).
 *
 * Tolerance is intentional: blank lines, `#` comments, surrounding
 * whitespace, comma lists and lines of the form `order=...` (earlier versions
 * had `key=value`) are understood. Duplicates are dropped — a duplicate name
 * would put an app into the first row twice.
 */
object OrderCodec {

    fun parse(text: String?): List<String> {
        if (text == null) return emptyList()
        val result = ArrayList<String>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) continue
            // Read "order=a,b" (the old form) just like "a,b" or "a".
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
        appendLine("# Navigate — app order")
        appendLine("#")
        appendLine("# One package-name line per entry, in display order.")
        appendLine("# Settings live next door in settings.conf — this file")
        appendLine("# is only the order. Lines starting with '#' are comments;")
        appendLine("# unknown lines are ignored.")
        appendLine()
        for (packageName in order) appendLine(packageName)
    }
}
