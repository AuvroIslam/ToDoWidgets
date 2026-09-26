package com.simpletodo.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLingerTest {

    private val prefs = mutablePreferencesOf()

    @Test
    fun tickedTaskLingersUntilItsDeadline() {
        WidgetLinger.record(prefs, "a", nowDone = true, now = 1_000)
        assertEquals(setOf("a"), WidgetLinger.lingering(prefs, 1_000 + WidgetLinger.DURATION_MS - 1))
        assertEquals(emptySet<String>(), WidgetLinger.lingering(prefs, 1_000 + WidgetLinger.DURATION_MS))
    }

    @Test
    fun tickingAnotherTaskKeepsTheFirstAndRestartsTheClock() {
        WidgetLinger.record(prefs, "a", nowDone = true, now = 1_000)
        WidgetLinger.record(prefs, "b", nowDone = true, now = 3_000)
        assertEquals(setOf("a", "b"), WidgetLinger.lingering(prefs, 3_000 + WidgetLinger.DURATION_MS - 1))
    }

    @Test
    fun untickingTakesTheTaskOutOfTheSet() {
        WidgetLinger.record(prefs, "a", nowDone = true, now = 1_000)
        WidgetLinger.record(prefs, "b", nowDone = true, now = 1_100)
        WidgetLinger.record(prefs, "a", nowDone = false, now = 1_200)
        assertEquals(setOf("b"), WidgetLinger.lingering(prefs, 1_300))
    }

    @Test
    fun expiredTasksDoNotComeBackWithANewTick() {
        WidgetLinger.record(prefs, "a", nowDone = true, now = 1_000)
        WidgetLinger.record(prefs, "b", nowDone = true, now = 60_000)
        assertEquals(setOf("b"), WidgetLinger.lingering(prefs, 60_001))
    }
}
