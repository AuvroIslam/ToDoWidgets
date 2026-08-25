package com.simpletodo.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Hand-rolled JSON codec for [TodoSnapshot].
 *
 * Decoding is deliberately forgiving: a single malformed task must never cost the user their
 * whole file, so unreadable entries are skipped rather than thrown. Only a document that cannot
 * be parsed at all is reported as corrupt.
 */
object TodoJson {

    const val SCHEMA_VERSION = 1

    private const val KEY_VERSION = "v"
    private const val KEY_REVISION = "rev"
    private const val KEY_LISTS = "lists"
    private const val KEY_WIDGETS = "widgets"

    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_ACCENT = "accent"
    private const val KEY_CREATED = "createdAt"
    private const val KEY_TASKS = "tasks"

    private const val KEY_TITLE = "title"
    private const val KEY_DONE = "done"
    private const val KEY_COMPLETED = "completedAt"

    fun encode(snapshot: TodoSnapshot): String {
        val root = JSONObject()
        root.put(KEY_VERSION, SCHEMA_VERSION)
        root.put(KEY_REVISION, snapshot.revision)

        val lists = JSONArray()
        for (list in snapshot.lists) {
            val listObj = JSONObject()
            listObj.put(KEY_ID, list.id)
            listObj.put(KEY_NAME, list.name)
            listObj.put(KEY_ACCENT, list.accent)
            listObj.put(KEY_CREATED, list.createdAt)

            val tasks = JSONArray()
            for (task in list.tasks) {
                val taskObj = JSONObject()
                taskObj.put(KEY_ID, task.id)
                taskObj.put(KEY_TITLE, task.title)
                taskObj.put(KEY_DONE, task.isDone)
                taskObj.put(KEY_CREATED, task.createdAt)
                if (task.completedAt != null) taskObj.put(KEY_COMPLETED, task.completedAt)
                tasks.put(taskObj)
            }
            listObj.put(KEY_TASKS, tasks)
            lists.put(listObj)
        }
        root.put(KEY_LISTS, lists)

        val widgets = JSONObject()
        for ((appWidgetId, listId) in snapshot.widgetBindings) {
            widgets.put(appWidgetId.toString(), listId)
        }
        root.put(KEY_WIDGETS, widgets)

        return root.toString()
    }

    /** @throws JSONException when the document is not parseable JSON at all. */
    @Throws(JSONException::class)
    fun decode(text: String): TodoSnapshot {
        if (text.isBlank()) return TodoSnapshot.EMPTY
        val root = JSONObject(text)

        val lists = ArrayList<TodoList>()
        val seenListIds = HashSet<String>()
        val listsArray = root.optJSONArray(KEY_LISTS) ?: JSONArray()
        for (i in 0 until listsArray.length()) {
            val listObj = listsArray.optJSONObject(i) ?: continue
            val id = listObj.optString(KEY_ID).takeIf { it.isNotBlank() } ?: continue
            if (!seenListIds.add(id)) continue

            val name = sanitizeListName(listObj.optString(KEY_NAME))
            val tasks = ArrayList<Task>()
            val seenTaskIds = HashSet<String>()
            val tasksArray = listObj.optJSONArray(KEY_TASKS) ?: JSONArray()
            for (j in 0 until tasksArray.length()) {
                val taskObj = tasksArray.optJSONObject(j) ?: continue
                val taskId = taskObj.optString(KEY_ID).takeIf { it.isNotBlank() } ?: continue
                if (!seenTaskIds.add(taskId)) continue
                val title = sanitizeTaskTitle(taskObj.optString(KEY_TITLE))
                if (title.isEmpty()) continue
                val isDone = taskObj.optBoolean(KEY_DONE, false)
                tasks += Task(
                    id = taskId,
                    title = title,
                    isDone = isDone,
                    createdAt = taskObj.optLong(KEY_CREATED, 0L),
                    completedAt = if (taskObj.has(KEY_COMPLETED) && !taskObj.isNull(KEY_COMPLETED)) {
                        taskObj.optLong(KEY_COMPLETED)
                    } else if (isDone) {
                        taskObj.optLong(KEY_CREATED, 0L)
                    } else {
                        null
                    },
                )
                if (tasks.size >= TodoLimits.MAX_TASKS_PER_LIST) break
            }

            lists += TodoList(
                id = id,
                name = name,
                accent = listObj.optInt(KEY_ACCENT, 0).mod(ACCENT_COUNT),
                createdAt = listObj.optLong(KEY_CREATED, 0L),
                tasks = tasks,
            )
            if (lists.size >= TodoLimits.MAX_LISTS) break
        }

        val bindings = LinkedHashMap<Int, String>()
        val widgetsObj = root.optJSONObject(KEY_WIDGETS)
        if (widgetsObj != null) {
            val keys = widgetsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val appWidgetId = key.toIntOrNull() ?: continue
                val listId = widgetsObj.optString(key).takeIf { it.isNotBlank() } ?: continue
                // Drop bindings that point at lists that no longer exist.
                if (seenListIds.contains(listId)) bindings[appWidgetId] = listId
            }
        }

        return TodoSnapshot(
            lists = lists,
            widgetBindings = bindings,
            revision = root.optLong(KEY_REVISION, 0L),
        )
    }

    fun sanitizeTaskTitle(raw: String?): String =
        (raw ?: "").replace('\n', ' ').replace('\r', ' ').trim().take(TodoLimits.TASK_TITLE_MAX)

    fun sanitizeListName(raw: String?): String {
        val cleaned = (raw ?: "").replace('\n', ' ').replace('\r', ' ').trim()
            .take(TodoLimits.LIST_NAME_MAX)
        return cleaned.ifEmpty { "Untitled list" }
    }
}
