package com.simpletodo.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.simpletodo.AppGraph

private const val TAG = "WidgetActions"

/** Per-widget-instance UI state. Ephemeral, and intentionally not shared between widgets. */
object WidgetPrefs {
    val SHOW_COMPLETED = booleanPreferencesKey("show_completed")

    /**
     * Completed tasks are on show until the user hides them. Hiding them by default made a tick
     * look like the task had been thrown away, with nothing on screen to say where it went.
     */
    const val SHOW_COMPLETED_DEFAULT = true

    /** Tasks ticked here a moment ago, still drawn in their own rows. See [WidgetLinger]. */
    val JUST_DONE = stringSetPreferencesKey("just_done_task_ids")
    val JUST_DONE_UNTIL = longPreferencesKey("just_done_until")
}

object WidgetParams {
    val LIST_ID = ActionParameters.Key<String>("listId")
    val TASK_ID = ActionParameters.Key<String>("taskId")
    val KIND = ActionParameters.Key<String>("widgetKind")
    val APP_WIDGET_ID = ActionParameters.Key<Int>("appWidgetId")
}

private suspend fun refreshWidget(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
) {
    val kind = WidgetKind.fromKey(parameters[WidgetParams.KIND])
    runCatching { widgetFor(kind).update(context, glanceId) }
        .onFailure { Log.w(TAG, "Could not refresh widget", it) }
}

private suspend fun editState(
    context: Context,
    glanceId: GlanceId,
    block: (MutablePreferences) -> Unit,
) {
    runCatching { updateAppWidgetState(context, glanceId) { prefs -> block(prefs) } }
        .onFailure { Log.w(TAG, "Could not update widget state", it) }
}

/**
 * Ticks a task off (or back on).
 *
 * The repository flips the stored value rather than applying an absolute one, so two widgets
 * showing the same list cannot overwrite each other with a stale idea of the task's state.
 *
 * A task ticked here stays in its row for a few seconds rather than dropping straight into
 * "completed" ([WidgetLinger]), so a tap that landed on the wrong row can be seen and taken back.
 */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val listId = parameters[WidgetParams.LIST_ID] ?: return
        val taskId = parameters[WidgetParams.TASK_ID] ?: return
        val repository = AppGraph.repository(context)
        runCatching { repository.toggleTask(listId, taskId) }
            .onFailure { Log.w(TAG, "Toggle failed", it) }
        val nowDone = runCatching {
            repository.current().listById(listId)?.tasks?.firstOrNull { it.id == taskId }?.isDone
        }.getOrNull() ?: false

        editState(context, glanceId) { prefs ->
            WidgetLinger.record(prefs, taskId, nowDone, System.currentTimeMillis())
        }
        if (nowDone) {
            WidgetLinger.scheduleClear(context, glanceId, WidgetKind.fromKey(parameters[WidgetParams.KIND]))
        }
        refreshWidget(context, glanceId, parameters)
    }
}

class ToggleShowCompletedAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        editState(context, glanceId) { prefs ->
            prefs[WidgetPrefs.SHOW_COMPLETED] =
                !(prefs[WidgetPrefs.SHOW_COMPLETED] ?: WidgetPrefs.SHOW_COMPLETED_DEFAULT)
        }
        refreshWidget(context, glanceId, parameters)
    }
}

class ClearCompletedAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val listId = parameters[WidgetParams.LIST_ID] ?: return
        runCatching { AppGraph.repository(context).clearCompleted(listId) }
            .onFailure { Log.w(TAG, "Clear completed failed", it) }
        refreshWidget(context, glanceId, parameters)
    }
}

/** Manual retry offered on the error/empty states. */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        refreshWidget(context, glanceId, parameters)
    }
}
