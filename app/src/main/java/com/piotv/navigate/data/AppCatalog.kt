package com.piotv.navigate.data

/**
 * Selection and order of the app list — pure rules, no Android types, so
 * testable without a device (AppCatalogTest).
 *
 * Why this was pulled out: these rules caused the bug that cost the most on
 * the target device — Prime Video, WOW and ARD were missing from the grid
 * because they carry **only** `CATEGORY_LEANBACK_LAUNCHER`. They used to sit
 * between PackageManager calls in [AppRepository] and were thus untested.
 *
 * The order of the input is part of the rule: first come, first served.
 * [AppRepository] therefore collects `LAUNCHER` first, then
 * `LEANBACK_LAUNCHER` — apps with both categories land via their LAUNCHER
 * entry, the same one the vendor launcher uses.
 */
object AppCatalog {

    /** The debug build runs alongside the release build and is not itself a
     *  launchable app — both variants drop out of our own grid. */
    const val DEBUG_SUFFIX = ".debug"

    /** A candidate as returned by a PackageManager query. */
    data class Candidate(
        val packageName: String,
        val activityName: String,
        val label: String,
        val enabled: Boolean,
    )

    /**
     * Applies the rules and sorts:
     *
     *  1. disabled apps drop out (they could not be started),
     *  2. our own launcher drops out — release **and** debug variant,
     *  3. per package the **first** candidate wins (see class comment),
     *  4. an empty label is replaced by the package name,
     *  5. sorting uses [labelOrder] (a `Collator` in production, i.e.
     *     language-aware — a fixed comparator in tests).
     */
    fun select(
        candidates: List<Candidate>,
        ownPackage: String,
        labelOrder: Comparator<String>,
    ): List<Candidate> {
        val ownBase = ownPackage.removeSuffix(DEBUG_SUFFIX)
        val byPackage = LinkedHashMap<String, Candidate>(candidates.size)
        for (candidate in candidates) {
            if (!candidate.enabled) continue
            if (candidate.packageName == ownBase || candidate.packageName == ownBase + DEBUG_SUFFIX) continue
            if (byPackage.containsKey(candidate.packageName)) continue
            byPackage[candidate.packageName] = candidate.copy(
                label = candidate.label.ifBlank { candidate.packageName },
            )
        }
        return byPackage.values.sortedWith { first, second -> labelOrder.compare(first.label, second.label) }
    }
}
