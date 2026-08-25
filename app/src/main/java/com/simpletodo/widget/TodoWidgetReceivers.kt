package com.simpletodo.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import com.simpletodo.AppGraph

private const val TAG = "TodoWidgetReceivers"

class SmallTodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallTodoWidget()
}

class MediumTodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MediumTodoWidget()
}

class LargeTodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LargeTodoWidget()
}

class XLargeTodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = XLargeTodoWidget()
}

/**
 * Pushes the current state to every widget instance.
 *
 * Called after every write. `updateAll` restarts a session for widgets whose process was killed,
 * which is the mechanism that makes the widget correct again after a device restart or a
 * low-memory kill — collecting a flow inside a live composition only covers the easy case.
 */
object WidgetSync {

    /** Bursts of writes (rapid adds, tapping through several tasks) collapse into one update. */
    const val COALESCE_MS = 90L

    private val receivers = listOf(
        SmallTodoWidgetReceiver::class.java,
        MediumTodoWidgetReceiver::class.java,
        LargeTodoWidgetReceiver::class.java,
        XLargeTodoWidgetReceiver::class.java,
    )

    suspend fun updateAllWidgets(context: Context) {
        for (kind in WidgetKind.entries) {
            runCatching { widgetFor(kind).updateAll(context) }
                .onFailure { Log.w(TAG, "Update failed for ${kind.key} widgets", it) }
        }
    }

    /** Every app widget id currently known to the launcher, across all four providers. */
    fun liveWidgetIds(context: Context): Set<Int> {
        val manager = AppWidgetManager.getInstance(context) ?: return emptySet()
        return receivers.flatMapTo(mutableSetOf()) { receiver ->
            runCatching {
                manager.getAppWidgetIds(ComponentName(context, receiver)).toList()
            }.getOrDefault(emptyList())
        }
    }

    /**
     * Drops bindings for widgets that no longer exist. `onDeleted` normally handles this, but it
     * can be missed if the app was force-stopped or the launcher was replaced.
     */
    suspend fun reconcileBindings(context: Context) {
        runCatching { AppGraph.repository(context).pruneBindings(liveWidgetIds(context)) }
            .onFailure { Log.w(TAG, "Binding reconciliation failed", it) }
    }
}
