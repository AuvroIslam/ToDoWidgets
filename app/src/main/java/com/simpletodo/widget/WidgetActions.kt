package com.simpletodo.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.simpletodo.AppGraph

private const val TAG = "WidgetActions"

/** Per-widget-instance UI state. Ephemeral, and intentionally not shared between widgets. */
object WidgetPrefs {
    /** Row currently asking "delete?" — the two-step guard against fat-fingering a delete. */
    val PENDING_DELETE = stringPreferencesKey("pending_delete_task_id")
    val SHOW_COMPLETED = booleanPreferencesKey("show_completed")
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
 * Cancels an armed delete, but only when one is actually armed.
 *
 * [updateAppWidgetState] commits whatever the block does — including nothing — and each commit is
 * a state change Glance repaints for. Clearing unconditionally therefore cost a second write and a
 * second repaint on *every* tick, when the overwhelmingly common case is that nothing is armed.
 * Fewer repaints per tap means less time spent rebuilding the row a thumb is aiming at.
 */
private suspend fun clearPendingDelete(context: Context, glanceId: GlanceId) {
    val armed = runCatching {
        getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[
            WidgetPrefs.PENDING_DELETE,
        ] != null
    }.getOrDefault(false)
    if (armed) editState(context, glanceId) { it.remove(WidgetPrefs.PENDING_DELETE) }
}

/**
 * Ticks a task off (or back on).
 *
 * The repository flips the stored value rather than applying an absolute one, so two widgets
 * showing the same list cannot overwrite each other with a stale idea of the task's state.
 */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val listId = parameters[WidgetParams.LIST_ID] ?: return
        val taskId = parameters[WidgetParams.TASK_ID] ?: return
        runCatching { AppGraph.repository(context).toggleTask(listId, taskId) }
            .onFailure { Log.w(TAG, "Toggle failed", it) }
        // Any tap elsewhere in the widget cancels a pending delete confirmation.
        clearPendingDelete(context, glanceId)
        refreshWidget(context, glanceId, parameters)
    }
}

/** First tap on the bin: arms the row instead of destroying anything. */
class RequestDeleteTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val taskId = parameters[WidgetParams.TASK_ID] ?: return
        editState(context, glanceId) { it[WidgetPrefs.PENDING_DELETE] = taskId }
        refreshWidget(context, glanceId, parameters)
    }
}

class CancelDeleteTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        editState(context, glanceId) { it.remove(WidgetPrefs.PENDING_DELETE) }
        refreshWidget(context, glanceId, parameters)
    }
}

/** Second tap: the row is already armed, so this is an explicit confirmation. */
class ConfirmDeleteTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val listId = parameters[WidgetParams.LIST_ID] ?: return
        val taskId = parameters[WidgetParams.TASK_ID] ?: return
        runCatching { AppGraph.repository(context).deleteTask(listId, taskId) }
            .onFailure { Log.w(TAG, "Delete failed", it) }
        editState(context, glanceId) { it.remove(WidgetPrefs.PENDING_DELETE) }
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
            prefs[WidgetPrefs.SHOW_COMPLETED] = !(prefs[WidgetPrefs.SHOW_COMPLETED] ?: false)
            prefs.remove(WidgetPrefs.PENDING_DELETE)
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
        editState(context, glanceId) { it.remove(WidgetPrefs.PENDING_DELETE) }
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
        editState(context, glanceId) { it.remove(WidgetPrefs.PENDING_DELETE) }
        refreshWidget(context, glanceId, parameters)
    }
}
