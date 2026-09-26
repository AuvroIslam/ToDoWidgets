package com.simpletodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskLinksTest {

    @Test
    fun bareLinkBecomesItsOwnReadableTitle() {
        assertEquals(
            "youtu.be/dQw4w9WgXcQ" to "https://youtu.be/dQw4w9WgXcQ",
            TaskLinks.split("https://youtu.be/dQw4w9WgXcQ"),
        )
    }

    @Test
    fun linkIsLiftedOutOfSurroundingText() {
        assertEquals(
            "Watch later" to "https://www.youtube.com/watch?v=abc",
            TaskLinks.split("Watch later https://www.youtube.com/watch?v=abc"),
        )
    }

    @Test
    fun trailingPunctuationIsNotPartOfTheLink() {
        assertEquals("https://example.com/post", TaskLinks.find("read (https://example.com/post)."))
    }

    @Test
    fun plainTextHasNoLink() {
        assertEquals("Buy milk" to null, TaskLinks.split("  Buy milk "))
    }

    @Test
    fun explicitUrlWinsAndIsRemovedFromLabel() {
        assertEquals(
            "Great post" to "https://lnkd.in/xyz",
            TaskLinks.split("Great post https://lnkd.in/xyz", "https://lnkd.in/xyz"),
        )
    }

    @Test
    fun onlyWebLinksAreAccepted() {
        assertNull(TaskLinks.sanitize("javascript:alert(1)"))
        assertNull(TaskLinks.sanitize("file:///sdcard/x"))
        assertNull(TaskLinks.sanitize("https://"))
        assertNull(TaskLinks.sanitize("https://" + "a".repeat(TodoLimits.TASK_URL_MAX)))
    }

    @Test
    fun typedLinkWithoutSchemeIsTakenAsHttps() {
        assertEquals("https://youtube.com/watch?v=1", TaskLinks.parseTyped(" youtube.com/watch?v=1 "))
        assertEquals("http://example.com", TaskLinks.parseTyped("http://example.com"))
    }

    @Test
    fun typedTextThatIsNotALinkIsRejected() {
        assertNull(TaskLinks.parseTyped("buy milk"))
        assertNull(TaskLinks.parseTyped("hello"))
        assertNull(TaskLinks.parseTyped("ftp://example.com"))
    }

    @Test
    fun hostDropsMobileAndWwwPrefixes() {
        assertEquals("youtube.com", TaskLinks.host("https://m.youtube.com/watch?v=1"))
        assertEquals("facebook.com", TaskLinks.host("https://www.facebook.com/share/p/1/"))
        assertEquals("example.com", TaskLinks.host("http://user@example.com:8080/a"))
    }
}
