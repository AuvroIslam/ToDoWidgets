package com.simpletodo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.simpletodo.AppGraph
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "WidgetRenderer"

/**
 * Draws widgets directly, in this process, the moment something changes.
 *
 * Glance's own `update()` does not draw: it hands the work to a session that runs as a WorkManager
 * job. JobScheduler is free to hold that job back — and does, hard, on phones that throttle
 * background work or on apps it considers rarely used — so a tick on the widget, or an edit made in
 * the app, could take seconds to show. Every tap also started a fresh session, which first drew a
 * "Loading…" frame. Here the composition runs on the spot via [compose] and the result goes
 * straight to [AppWidgetManager.updateAppWidget]; nothing waits on a scheduler.
 *
 * Renders are serialised, and a widget whose inputs have not changed since it was last drawn is
 * skipped, so the several callers that react to one write (the tap itself, the repository's change
 * stream) cost one render, not three.
 */
object WidgetRenderer {

    private val mutex = Mutex()

    /** What each widget was last drawn from. Process-local: a fresh process simply redraws. */
    private val lastDrawn = HashMap<Int, List<Any?>>()

    suspend fun renderAll(context: Context) {
        render(context, WidgetSync.liveWidgetIds(context))
    }

    suspend fun render(context: Context, appWidgetIds: Collection<Int>) {
        if (appWidgetIds.isEmpty()) return
        val appContext = context.applicationContext
        mutex.withLock {
            val manager = AppWidgetManager.getInstance(appContext) ?: return
            val glanceManager = GlanceAppWidgetManager(appContext)
            // All at once, not one after another. Most of a composition's time is Glance's frame
            // clock pacing it (a couple of frames at its idle 5 Hz), not work, so widgets drawn
            // side by side finish together — and the one that was tapped is never left waiting
            // behind the others.
            coroutineScope {
                appWidgetIds.filter(TodoWidget::isRealWidget).forEach { appWidgetId ->
                    launch {
                        runCatching { renderOne(appContext, manager, glanceManager, appWidgetId) }
                            .onFailure { Log.w(TAG, "Could not draw widget $appWidgetId", it) }
                    }
                }
            }
        }
    }

    /** Forgets a removed widget, so a later widget reusing its id is drawn from scratch. */
    fun forget(appWidgetIds: Collection<Int>) {
        // Called from a receiver with no coroutine to hand; a racing render only costs a redraw.
        synchronized(lastDrawn) { appWidgetIds.forEach { lastDrawn.remove(it) } }
    }

    private suspend fun renderOne(
        context: Context,
        manager: AppWidgetManager,
        glanceManager: GlanceAppWidgetManager,
        appWidgetId: Int,
    ) {
        val provider = manager.getAppWidgetInfo(appWidgetId)?.provider ?: return
        val kind = WidgetKind.entries.firstOrNull {
            WidgetPinning.receiverFor(it).name == provider.className
        } ?: return

        val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val options = manager.getAppWidgetOptions(appWidgetId) ?: Bundle()
        // What this widget shows, not the whole snapshot's revision: a write to another list,
        // or to another widget's binding, leaves this one exactly as it was and costs it nothing.
        val shows = resolveWidgetState(AppGraph.repository(context).current(), appWidgetId)

        val inputs = listOf(
            shows,
            state,
            describe(options),
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
            context.resources.configuration.locales.toLanguageTags(),
        )
        if (synchronized(lastDrawn) { lastDrawn[appWidgetId] } == inputs) return

        val views = widgetFor(kind).compose(context, glanceId, options = options, state = state)
        manager.updateAppWidget(appWidgetId, views)
        synchronized(lastDrawn) { lastDrawn[appWidgetId] = inputs }
    }

    /** A Bundle has no value equality; its size keys are what decide the layout. */
    @Suppress("DEPRECATION")
    private fun describe(options: Bundle): String =
        options.keySet().sorted().joinToString(";") { key -> "$key=${options.get(key)}" }
}
