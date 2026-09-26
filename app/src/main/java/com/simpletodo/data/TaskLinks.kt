package com.simpletodo.data

/**
 * Pulls web links out of task text.
 *
 * A link can reach a task two ways — shared in from another app, or pasted into the composer — and
 * both go through [split], so a YouTube link lands the same whichever way the user brought it.
 */
object TaskLinks {

    private val URL_PATTERN = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

    /** Sentence punctuation that follows a link in prose far more often than it ends one. */
    private val TRAILING = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"')

    private val EXTRA_SPACE = Regex("""\s{2,}""")

    /** The first http(s) link in [text], or null. */
    fun find(text: String?): String? {
        val match = URL_PATTERN.find(text ?: return null) ?: return null
        return sanitize(match.value.trimEnd(*TRAILING))
    }

    /** [raw] if it is a usable http(s) link, otherwise null. Never throws. */
    fun sanitize(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.length > TodoLimits.TASK_URL_MAX) return null
        if (!value.startsWith("http://", ignoreCase = true) &&
            !value.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        if (value.any { it.isWhitespace() }) return null
        return value.takeIf { host(it).isNotEmpty() }
    }

    /**
     * A link the user typed by hand, where `youtube.com/watch?v=…` is as good as the full address.
     * A missing scheme is taken to be https, and the site must at least look like a domain.
     */
    fun parseTyped(raw: String): String? {
        val value = raw.trim()
        val withScheme = if (value.contains("://")) value else "https://$value"
        return sanitize(withScheme)?.takeIf { '.' in host(it) }
    }

    /** The site a link belongs to, as a person would say it: `youtube.com`, not `m.youtube.com`. */
    fun host(url: String): String = url
        .substringAfter("://")
        .substringBefore('/').substringBefore('?').substringBefore('#')
        .substringAfterLast('@').substringBefore(':')
        .lowercase()
        .removePrefix("www.").removePrefix("m.")

    /**
     * Splits what the user typed or shared into a title and a link.
     *
     * An explicit [url] wins over one found in the text. The link is taken out of the title so the
     * card does not say it twice, and a bare link with nothing else becomes its own readable title,
     * because a task always needs one — the widgets show nothing else.
     */
    fun split(text: String, url: String? = null): Pair<String, String?> {
        val link = sanitize(url) ?: find(text)
            ?: return TodoJson.sanitizeTaskTitle(text) to null
        return labelWithout(text, link).ifEmpty { fallbackTitle(link) } to link
    }

    /** [text] with [url] taken out and tidied into a title; empty when the link was all it said. */
    fun labelWithout(text: String, url: String): String =
        TodoJson.sanitizeTaskTitle(text.replace(url, " ").replace(EXTRA_SPACE, " "))

    /** `youtu.be/dQw4w9WgXcQ` for `https://youtu.be/dQw4w9WgXcQ` — the link minus its noise. */
    fun fallbackTitle(url: String): String {
        val rest = url.substringAfter("://").substringBefore('#')
        val start = rest.indexOfFirst { it == '/' || it == '?' }
        val path = if (start < 0) "" else rest.substring(start).trimEnd('/')
        return TodoJson.sanitizeTaskTitle(host(url) + path)
    }
}
