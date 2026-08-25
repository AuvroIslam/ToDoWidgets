package com.simpletodo.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import com.simpletodo.AppGraph
import kotlinx.coroutines.launch

private const val TAG = "TodoWidgetReceivers"

/**
 * Base receiver that nudges Glance to render as soon as the first widget of this size is enabled.
 * Some Samsung/One UI launchers do not send a follow-up APPWIDGET_UPDATE after the picker places
 * the widget, so the first composition can sit on the loading placeholder until something else
 * pokes it. Pushing one update from onEnabled covers that gap.
 */
abstract class TodoWidgetReceiver(private val kind: WidgetKind) : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = widgetFor(kind)

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val appContext = context.applicationContext
        AppGraph.get(appContext).appScope.launch {
            runCatching { widgetFor(kind).updateAll(appContext) }
                .onFailure { Log.w(TAG, "onEnabled update failed for $kind", it) }
        }
    }
}

class SmallTodoWidgetReceiver : TodoWidgetReceiver(WidgetKind.SMALL)

class MediumTodoWidgetReceiver : TodoWidgetReceiver(WidgetKind.MEDIUM)

class LargeTodoWidgetReceiver : TodoWidgetReceiver(WidgetKind.LARGE)

class XLargeTodoWidgetReceiver : TodoWidgetReceiver(WidgetKind.XLARGE)

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
