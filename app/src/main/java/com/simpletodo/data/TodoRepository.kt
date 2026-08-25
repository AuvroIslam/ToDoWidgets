package com.simpletodo.data

import androidx.datastore.core.DataStore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.UUID

/**
 * The single source of truth shared by the app UI, the quick-add sheet and every widget instance.
 *
 * Every mutation is a read-modify-write inside [DataStore.updateData], which is atomic and
 * serialised, so rapid taps from several widgets at once cannot interleave into a lost update.
 */
class TodoRepository(
    private val store: DataStore<TodoSnapshot>,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    /** Never throws: an unreadable store surfaces as an empty snapshot instead of a crash. */
    val snapshot: Flow<TodoSnapshot> = store.data.catch { cause ->
        if (cause is IOException) emit(TodoSnapshot.EMPTY) else throw cause
    }

    private val _changes = MutableSharedFlow<Long>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Emits the new revision after every successful write. Used to push widget updates. */
    val changes: SharedFlow<Long> = _changes

    suspend fun current(): TodoSnapshot = snapshot.first()

    // ---------------------------------------------------------------- lists

    /** Creates the starter list the first time the app runs, and only then. */
    suspend fun seedIfFirstRun() = mutate { current ->
        if (current.revision == 0L && current.lists.isEmpty()) {
            defaultSnapshot(now(), newId)
        } else {
            current
        }
    }

    /** @return the id of the new list, or null when the list cap has been reached. */
    suspend fun createList(name: String, accent: Int? = null): String? {
        var created: String? = null
        mutate { current ->
            if (current.lists.size >= TodoLimits.MAX_LISTS) return@mutate current
            val id = newId()
            created = id
            val list = TodoList(
                id = id,
                name = TodoJson.sanitizeListName(name),
                accent = accent?.mod(ACCENT_COUNT) ?: (current.lists.size % ACCENT_COUNT),
                createdAt = now(),
            )
            current.copy(lists = current.lists + list)
        }
        return created
    }

    suspend fun renameList(listId: String, name: String) = mutate { current ->
        val cleaned = TodoJson.sanitizeListName(name)
        current.mapList(listId) { it.copy(name = cleaned) }
    }

    suspend fun setListAccent(listId: String, accent: Int) = mutate { current ->
        current.mapList(listId) { it.copy(accent = accent.mod(ACCENT_COUNT)) }
    }

    /**
     * Deletes a list and unbinds every widget that pointed at it, in the same atomic write, so a
     * widget can never end up rendering a list that is half-deleted.
     */
    suspend fun deleteList(listId: String) = mutate { current ->
        if (current.lists.none { it.id == listId }) return@mutate current
        current.copy(
            lists = current.lists.filterNot { it.id == listId },
            widgetBindings = current.widgetBindings.filterValues { it != listId },
        )
    }

    /** Re-inserts a previously deleted list (undo), keeping its original position when possible. */
    suspend fun restoreList(list: TodoList, index: Int) = mutate { current ->
        if (current.lists.any { it.id == list.id }) return@mutate current
        if (current.lists.size >= TodoLimits.MAX_LISTS) return@mutate current
        val target = index.coerceIn(0, current.lists.size)
        current.copy(lists = current.lists.toMutableList().apply { add(target, list) })
    }

    suspend fun moveList(from: Int, to: Int) = mutate { current ->
        val lists = current.lists
        if (from !in lists.indices || to !in lists.indices || from == to) return@mutate current
        val mutable = lists.toMutableList()
        mutable.add(to, mutable.removeAt(from))
        current.copy(lists = mutable)
    }

    // ---------------------------------------------------------------- tasks

    /**
     * Adds a task at the top of the list so it is immediately visible even in the smallest widget.
     *
     * @return the new task id, or null when the input was empty or the list is full/missing.
     */
    suspend fun addTask(listId: String, title: String): String? {
        val cleaned = TodoJson.sanitizeTaskTitle(title)
        if (cleaned.isEmpty()) return null
        var created: String? = null
        mutate { current ->
            val list = current.listById(listId) ?: return@mutate current
            if (list.tasks.size >= TodoLimits.MAX_TASKS_PER_LIST) return@mutate current
            val id = newId()
            created = id
            val task = Task(id = id, title = cleaned, createdAt = now())
            current.mapList(listId) { it.copy(tasks = listOf(task) + it.tasks) }
        }
        return created
    }

    suspend fun editTask(listId: String, taskId: String, title: String) {
        val cleaned = TodoJson.sanitizeTaskTitle(title)
        if (cleaned.isEmpty()) return
        mutate { current ->
            current.mapTask(listId, taskId) { it.copy(title = cleaned) }
        }
    }

    suspend fun setTaskDone(listId: String, taskId: String, done: Boolean) = mutate { current ->
        current.mapTask(listId, taskId) { task ->
            if (task.isDone == done) task
            else task.copy(isDone = done, completedAt = if (done) now() else null)
        }
    }

    /**
     * Flips the task's state. Toggling by current value (rather than sending an absolute value
     * from the widget) keeps two widgets showing the same list from fighting each other.
     */
    suspend fun toggleTask(listId: String, taskId: String) = mutate { current ->
        current.mapTask(listId, taskId) { task ->
            task.copy(
                isDone = !task.isDone,
                completedAt = if (!task.isDone) now() else null,
            )
        }
    }

    suspend fun deleteTask(listId: String, taskId: String) = mutate { current ->
        val list = current.listById(listId) ?: return@mutate current
        if (list.tasks.none { it.id == taskId }) return@mutate current
        current.mapList(listId) { l -> l.copy(tasks = l.tasks.filterNot { it.id == taskId }) }
    }

    suspend fun restoreTask(listId: String, task: Task, index: Int) = mutate { current ->
        val list = current.listById(listId) ?: return@mutate current
        if (list.tasks.any { it.id == task.id }) return@mutate current
        if (list.tasks.size >= TodoLimits.MAX_TASKS_PER_LIST) return@mutate current
        current.mapList(listId) { l ->
            l.copy(tasks = l.tasks.toMutableList().apply { add(index.coerceIn(0, size), task) })
        }
    }

    suspend fun clearCompleted(listId: String) = mutate { current ->
        val list = current.listById(listId) ?: return@mutate current
        if (list.tasks.none { it.isDone }) return@mutate current
        current.mapList(listId) { l -> l.copy(tasks = l.tasks.filterNot { it.isDone }) }
    }

    /**
     * Moves a task within the *active* portion of the list; completed tasks keep their relative
     * order and are never displaced by a drag in the active section.
     */
    suspend fun moveActiveTask(listId: String, from: Int, to: Int) = mutate { current ->
        val list = current.listById(listId) ?: return@mutate current
        val activeIndices = list.tasks.indices.filter { !list.tasks[it].isDone }
        if (from !in activeIndices.indices || to !in activeIndices.indices || from == to) {
            return@mutate current
        }
        val active = activeIndices.map { list.tasks[it] }.toMutableList()
        active.add(to, active.removeAt(from))
        val rebuilt = list.tasks.toMutableList()
        activeIndices.forEachIndexed { slot, position -> rebuilt[position] = active[slot] }
        current.mapList(listId) { it.copy(tasks = rebuilt) }
    }

    /**
     * Applies a whole new order for the active tasks, committed once when a drag ends.
     *
     * Tasks that appeared during the drag (added from a widget, say) are not in the supplied
     * order; they keep their place at the top rather than being dropped.
     */
    suspend fun reorderActiveTasks(listId: String, orderedActiveIds: List<String>) = mutate { current ->
        val list = current.listById(listId) ?: return@mutate current
        val activeIndices = list.tasks.indices.filter { !list.tasks[it].isDone }
        val active = activeIndices.map { list.tasks[it] }
        val requested = orderedActiveIds.toSet()
        val byId = active.associateBy { it.id }
        val ordered = orderedActiveIds.distinct().mapNotNull { byId[it] }
        val appeared = active.filter { it.id !in requested }
        val finalOrder = appeared + ordered
        if (finalOrder.size != active.size) return@mutate current
        if (finalOrder.map { it.id } == active.map { it.id }) return@mutate current

        val rebuilt = list.tasks.toMutableList()
        activeIndices.forEachIndexed { slot, position -> rebuilt[position] = finalOrder[slot] }
        current.mapList(listId) { it.copy(tasks = rebuilt) }
    }

    // -------------------------------------------------------------- widgets

    suspend fun bindWidget(appWidgetId: Int, listId: String) = mutate { current ->
        // 0 is the platform's INVALID_APPWIDGET_ID and shows up in widget previews.
        if (appWidgetId <= 0) return@mutate current
        if (current.lists.none { it.id == listId }) return@mutate current
        if (current.widgetBindings[appWidgetId] == listId) return@mutate current
        current.copy(widgetBindings = current.widgetBindings + (appWidgetId to listId))
    }

    suspend fun unbindWidgets(appWidgetIds: Collection<Int>) = mutate { current ->
        if (appWidgetIds.none { current.widgetBindings.containsKey(it) }) return@mutate current
        current.copy(widgetBindings = current.widgetBindings - appWidgetIds.toSet())
    }

    /** Drops bindings for widgets the launcher no longer knows about (uninstalled launcher, etc.). */
    suspend fun pruneBindings(liveAppWidgetIds: Set<Int>) = mutate { current ->
        val stale = current.widgetBindings.keys.filter { it <= 0 || it !in liveAppWidgetIds }.toSet()
        if (stale.isEmpty()) return@mutate current
        current.copy(widgetBindings = current.widgetBindings - stale)
    }

    // --------------------------------------------------------------- helper

    private suspend inline fun mutate(crossinline block: (TodoSnapshot) -> TodoSnapshot) {
        val updated = store.updateData { current ->
            val next = block(current)
            if (next == current) current else next.copy(revision = current.revision + 1)
        }
        _changes.tryEmit(updated.revision)
    }
}

private inline fun TodoSnapshot.mapList(listId: String, transform: (TodoList) -> TodoList): TodoSnapshot {
    var changed = false
    val updated = lists.map { list ->
        if (list.id != listId) {
            list
        } else {
            transform(list).also { if (it != list) changed = true }
        }
    }
    return if (changed) copy(lists = updated) else this
}

private inline fun TodoSnapshot.mapTask(
    listId: String,
    taskId: String,
    transform: (Task) -> Task,
): TodoSnapshot = mapList(listId) { list ->
    var changed = false
    val tasks = list.tasks.map { task ->
        if (task.id != taskId) {
            task
        } else {
            transform(task).also { if (it != task) changed = true }
        }
    }
    if (changed) list.copy(tasks = tasks) else list
}
