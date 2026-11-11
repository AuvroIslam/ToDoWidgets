package com.simpletodo.data

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** An in-memory stand-in for the DataStore file. */
private class MemoryStore(initial: TodoSnapshot) : DataStore<TodoSnapshot> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<TodoSnapshot> = state
    override suspend fun updateData(transform: suspend (t: TodoSnapshot) -> TodoSnapshot): TodoSnapshot {
        state.value = transform(state.value)
        return state.value
    }
}

class NewFeaturesTest {

    private var nextId = 0
    private fun repository(vararg lists: TodoList) = TodoRepository(
        store = MemoryStore(TodoSnapshot(lists = lists.toList(), revision = 1)),
        now = { 1_000L },
        newId = { "id${nextId++}" },
    )

    private val zone = ZoneId.of("Asia/Dhaka")
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    // ------------------------------------------------------------ pasted lists

    @Test
    fun pastedListBecomesOneTaskPerLineWithoutItsBullets() {
        assertEquals(
            listOf("Milk", "Eggs", "Bread", "Call mom", "Done item"),
            TaskLines.parse("- Milk\n• Eggs\n\n3. Bread\r\n[ ] Call mom\n[x] Done item\n   "),
        )
    }

    @Test
    fun ordinaryTextIsStillOneTask() {
        assertEquals(listOf("Buy milk - the oat one"), TaskLines.parse("  Buy milk - the oat one "))
        assertFalse(TaskLines.isSeveral("Buy milk"))
        assertTrue(TaskLines.isSeveral("Milk\nEggs"))
    }

    @Test
    fun addTasksKeepsPastedOrderAboveExistingTasks() = runTest {
        val repo = repository(TodoList(id = "l", name = "Shop", tasks = listOf(Task("old", "Old"))))
        assertEquals(3, repo.addTasks("l", listOf("Milk", "Eggs https://example.com/eggs", "Bread")))
        val tasks = repo.current().listById("l")!!.tasks
        assertEquals(listOf("Milk", "Eggs", "Bread", "Old"), tasks.map { it.title })
        assertEquals("https://example.com/eggs", tasks[1].url)
    }

    @Test
    fun addTasksStopsAtThePasteCap() = runTest {
        val repo = repository(TodoList(id = "l", name = "Shop"))
        val added = repo.addTasks("l", (1..80).map { "Item $it" })
        assertEquals(TodoLimits.MAX_TASKS_PER_PASTE, added)
    }

    // ------------------------------------------------------------- toggle

    @Test
    fun toggleReportsTheStateItLeftTheTaskIn() = runTest {
        val repo = repository(TodoList(id = "l", name = "L", tasks = listOf(Task("t", "T"))))
        assertEquals(true, repo.toggleTask("l", "t"))
        assertEquals(false, repo.toggleTask("l", "t"))
        assertNull(repo.toggleTask("l", "missing"))
    }

    // -------------------------------------------------------- morning reset

    @Test
    fun theDayTurnsOverAtFourInTheMorningNotMidnight() {
        val evening = DailyReset.dayOf(at(10, 23, 30), zone)
        assertEquals("ten past midnight is still last night", evening, DailyReset.dayOf(at(11, 0, 10), zone))
        assertEquals(evening, DailyReset.dayOf(at(11, 3, 59), zone))
        assertEquals(evening + 1, DailyReset.dayOf(at(11, 4, 0), zone))
    }

    @Test
    fun nextResetIsAlwaysAhead() {
        assertEquals(30 * 60_000L, DailyReset.millisUntilNext(at(11, 3, 30), zone))
        assertEquals(24 * 3_600_000L, DailyReset.millisUntilNext(at(11, 4, 0), zone))
        assertEquals(6 * 3_600_000L, DailyReset.millisUntilNext(at(11, 22, 0), zone))
    }

    @Test
    fun turningDailyOnDoesNotUntickAnythingUntilTheNextMorning() = runTest {
        val done = Task("t", "Vitamins", isDone = true, completedAt = 5L)
        val repo = repository(TodoList(id = "l", name = "Routine", tasks = listOf(done)))
        val today = 100L
        repo.setListDaily("l", true, today)

        assertFalse(repo.resetDailyLists(today))
        assertTrue(repo.current().listById("l")!!.tasks.single().isDone)

        assertTrue(repo.resetDailyLists(today + 1))
        val after = repo.current().listById("l")!!
        assertFalse(after.tasks.single().isDone)
        assertNull(after.tasks.single().completedAt)
        assertEquals(1, after.tasks.size)
        assertFalse("a second call the same morning does nothing", repo.resetDailyLists(today + 1))
    }

    @Test
    fun onlyDailyListsAreReset() = runTest {
        val done = Task("t", "Pay rent", isDone = true, completedAt = 5L)
        val repo = repository(TodoList(id = "l", name = "Personal", tasks = listOf(done)))
        assertFalse(repo.resetDailyLists(999L))
        assertTrue(repo.current().listById("l")!!.tasks.single().isDone)
    }

    @Test
    fun dailySettingSurvivesSaveAndLoad() {
        val list = TodoList(id = "l", name = "Routine", resetsDaily = true, lastResetDay = 42L)
        val decoded = TodoJson.decode(TodoJson.encode(TodoSnapshot(lists = listOf(list))))
        assertTrue(decoded.lists.single().resetsDaily)
        assertEquals(42L, decoded.lists.single().lastResetDay)
        // Files written before this feature have no such keys and must load as ordinary lists.
        val old = TodoJson.decode("""{"lists":[{"id":"a","name":"Old","tasks":[]}]}""")
        assertFalse(old.lists.single().resetsDaily)
    }
}
