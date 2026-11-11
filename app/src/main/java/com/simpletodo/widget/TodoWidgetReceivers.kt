package com.simpletodo.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.simpletodo.AppGraph
import kotlinx.coroutines.launch

private const val TAG = "TodoWidgetReceivers"

/**
 * The widget provider. Draws on the spot for every system request rather than through Glance.
 *
 * Glance's receiver turns `onUpdate` and `onAppWidgetOptionsChanged` into WorkManager jobs, and a
 * throttled phone may run those late — a freshly placed or freshly resized widget would sit on its
 * old layout, or Glance's loading placeholder, until then. [WidgetRenderer] draws inside the
 * broadcast instead, which the system lets run to completion. Some Samsung/One UI launchers also
 * never send an update after the picker places a widget; `onEnabled` covers that.
 */
abstract class TodoWidgetReceiver(private val kind: WidgetKind) : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = widgetFor(kind)

    // No super call, here or in onAppWidgetOptionsChanged: Glance's version only queues the
    // WorkManager render this receiver exists to avoid (and records bookkeeping for updateAll and
    // getGlanceIds, neither of which the app uses). WidgetRenderer draws instead.
    @SuppressLint("MissingSuperCall")
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        draw(context, appWidgetIds.toList())
    }

    @SuppressLint("MissingSuperCall")
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        draw(context, listOf(appWidgetId))
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        draw(context, WidgetSync.liveWidgetIds(context))
    }

    /** Glance still cleans up its own per-widget state; the binding is released here, at once. */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val ids = appWidgetIds.toList()
        WidgetRenderer.forget(ids)
        val appContext = context.applicationContext
        val pending = goAsync()
        AppGraph.get(appContext).appScope.launch {
            try {
                AppGraph.repository(appContext).unbindWidgets(ids)
            } catch (e: Exception) {
                Log.w(TAG, "Could not release bindings for $ids", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun draw(context: Context, appWidgetIds: Collection<Int>) {
        val appContext = context.applicationContext
        val pending = goAsync()
        AppGraph.get(appContext).appScope.launch {
            try {
                // The half-hourly system update doubles as the morning-reset check for a
                // process nothing else has woken since yesterday.
                DailyResetRunner.run(appContext)
                // The system asked, so its copy may be gone: always a real draw.
                WidgetRenderer.render(appContext, appWidgetIds, force = true)
            } catch (e: Exception) {
                Log.w(TAG, "Could not draw $kind widgets $appWidgetIds", e)
            } finally {
                pending.finish()
            }
        }
    }
}

class SmallTodoWidgetReceiver : TodoWidgetReceiver(WidgetKind.SMALL)

class MediumTodoWidgetReceiver : TodoWidgetReceiver(WidgetKind.MEDIUM)

class LargeTodoWidgetReceiver : TodoWidgetReceiver(WidgetKind.LARGE)

class XLargeTodoWidgetReceiver : TodoWidgetReceiver(WidgetKind.XLARGE)

object WidgetSync {

    /** Bursts of writes (rapid adds, tapping through several tasks) collapse into one redraw. */
    const val COALESCE_MS = 50L

    private val receivers = listOf(
        SmallTodoWidgetReceiver::class.java,
        MediumTodoWidgetReceiver::class.java,
        LargeTodoWidgetReceiver::class.java,
        XLargeTodoWidgetReceiver::class.java,
    )

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
