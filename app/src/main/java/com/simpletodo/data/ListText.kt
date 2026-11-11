package com.simpletodo.data

/**
 * A list written out as plain text, for sending to someone: the shopping list into a family chat.
 *
 * One task per line rather than a comma-run, because that is how a list reads in a message and it
 * survives long task names. What is still to do comes first; what is already done follows, marked,
 * so the person at the shop can see both what to get and what has been got.
 */
object ListText {

    fun format(list: TodoList): String = buildString {
        append(list.name)
        for (task in list.activeTasks) append("\n• ").append(line(task))
        for (task in list.completedTasks) append("\n✓ ").append(line(task))
    }

    private fun line(task: Task): String =
        if (task.url != null && task.url != task.title) "${task.title} — ${task.url}" else task.title
}
