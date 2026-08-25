package com.simpletodo.data

/**
 * Domain model for the whole app. The entire state is a single immutable snapshot that is
 * persisted atomically, which keeps the app and every widget instance trivially consistent:
 * there is exactly one writer (the DataStore) and one source of truth.
 */

/** Hard limits, enforced on write so that malformed data can never reach the widgets. */
object TodoLimits {
    const val TASK_TITLE_MAX = 300
    const val LIST_NAME_MAX = 40
    const val MAX_LISTS = 100
    const val MAX_TASKS_PER_LIST = 1000
}

data class Task(
    val id: String,
    val title: String,
    val isDone: Boolean = false,
    val createdAt: Long = 0L,
    val completedAt: Long? = null,
)

data class TodoList(
    val id: String,
    val name: String,
    val accent: Int = 0,
    val createdAt: Long = 0L,
    val tasks: List<Task> = emptyList(),
) {
    val activeTasks: List<Task> get() = tasks.filter { !it.isDone }
    val completedTasks: List<Task> get() = tasks.filter { it.isDone }
    val activeCount: Int get() = tasks.count { !it.isDone }
    val doneCount: Int get() = tasks.count { it.isDone }
    val isEmpty: Boolean get() = tasks.isEmpty()

    /** 0f..1f, or null when the list has no tasks at all. */
    val progress: Float?
        get() = if (tasks.isEmpty()) null else doneCount.toFloat() / tasks.size
}

/**
 * @param lists all user lists, in user-visible order.
 * @param widgetBindings appWidgetId -> list id. Stored alongside the data so that a single
 *   atomic write covers both, and so bindings survive process and device restarts.
 */
data class TodoSnapshot(
    val lists: List<TodoList> = emptyList(),
    val widgetBindings: Map<Int, String> = emptyMap(),
    val revision: Long = 0L,
) {
    fun listById(id: String?): TodoList? = id?.let { lists.firstOrNull { l -> l.id == it } }

    /**
     * Resolves what a widget should display.
     *
     * A widget is only ever "unbound" (never silently repointed) so that deleting a list gives a
     * clear, honest empty state instead of quietly showing somebody else's tasks.
     */
    fun listForWidget(appWidgetId: Int): TodoList? = listById(widgetBindings[appWidgetId])

    fun hasBinding(appWidgetId: Int): Boolean = widgetBindings.containsKey(appWidgetId)

    companion object {
        val EMPTY = TodoSnapshot()
    }
}

/** Accent palette index is stored, not a raw colour, so themes stay swappable. */
const val ACCENT_COUNT = 6

/** Seed content used the very first time the app is launched. */
fun defaultSnapshot(now: Long, idFactory: () -> String): TodoSnapshot {
    val listId = idFactory()
    return TodoSnapshot(
        lists = listOf(
            TodoList(
                id = listId,
                name = "Personal",
                accent = 0,
                createdAt = now,
                tasks = emptyList(),
            ),
        ),
        revision = 1L,
    )
}
