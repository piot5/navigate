package com.piotv.navigate.data

/**
 * The display order — pure rules without Android types, so testable without a
 * device (OrderTest).
 *
 * The order is the **only** thing this UI sets: the grid shows only the first
 * row, so the order decides which apps you see without a keypress.
 *
 * Two rules:
 *  - [arrange] puts the configured packages up front (in their order) and
 *    appends the rest in its previous (language-sorted) order. A configured
 *    package that is not installed is silently dropped.
 *  - [moveForward] moves a package **one** position forward. A package not in
 *    the list is appended — that is exactly one position forward, because it
 *    previously sat behind all configured ones.
 */
object Order {

    fun <T> arrange(order: List<String>, items: List<T>, key: (T) -> String): List<T> {
        if (order.isEmpty()) return items
        val byKey = items.associateBy(key)
        val head = order.mapNotNull { byKey[it] }
        if (head.isEmpty()) return items
        val configured = order.toHashSet()
        val tail = items.filter { key(it) !in configured }
        return head + tail
    }

    /**
     * Moves a package **one** position backward.
     *
     * A package not in the list stays put: it already sits behind all
     * configured ones, and "further back" could only be expressed by moving
     * another app forward.
     */
    fun moveBackward(order: List<String>, packageName: String): List<String> {
        val index = order.indexOf(packageName)
        return when {
            index < 0 -> order
            index == order.lastIndex -> order
            else -> order.toMutableList().apply { add(index + 1, removeAt(index)) }
        }
    }

    fun moveForward(order: List<String>, packageName: String): List<String> {
        val index = order.indexOf(packageName)
        return when {
            // Already at the very front: do nothing.
            index == 0 -> order
            // From the "rest": appending means one position forward.
            index < 0 -> order + packageName
            else -> order.toMutableList().apply { add(index - 1, removeAt(index)) }
        }
    }
}
