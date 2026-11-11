package com.simpletodo.data

/**
 * Turns pasted text into tasks, one per line.
 *
 * A shopping list copied out of a chat or a notes app arrives as several lines; typed into a
 * single task it became one unreadable run-on. Each non-empty line becomes its own task instead,
 * with the bullet, number or checkbox it was written with taken off the front.
 */
object TaskLines {

    /** "- ", "* ", "• ", "1. ", "2) ", "[ ] ", "[x] " and the like, at the start of a line. */
    private val MARKER = Regex("""^\s*(?:[-*•·–—]+|\d{1,3}[.)]|\[[ xX]?])\s+""")

    /** The tasks [text] would create: its non-empty lines, cleaned. One entry for ordinary text. */
    fun parse(text: String): List<String> = text
        .split('\n', '\r')
        .map { line -> line.replace(MARKER, "").trim() }
        .filter { it.isNotEmpty() }

    /** True when adding [text] would create more than one task. */
    fun isSeveral(text: String): Boolean = parse(text).size > 1
}
