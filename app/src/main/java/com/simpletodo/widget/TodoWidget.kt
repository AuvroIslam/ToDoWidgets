package com.simpletodo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.material3.ColorProviders
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.simpletodo.AppGraph
import com.simpletodo.ui.theme.TodoDarkColors
import com.simpletodo.ui.theme.TodoLightColors
import com.simpletodo.data.Task
import com.simpletodo.data.TodoList
import com.simpletodo.data.TodoSnapshot

private const val TAG = "TodoWidget"

/** What a single widget instance should be rendering right now. */
sealed interface WidgetUiState {
    /** Data has not arrived yet. Shown for a frame or two at most. */
    data object Loading : WidgetUiState

    /** The user has no lists at all. */
    data object NoLists : WidgetUiState

    /** This widget was pointed at a list that has since been deleted. */
    data object ListDeleted : WidgetUiState

    /**
     * @param defaulted true when this widget has no explicit binding and is falling back to the
     *   first list, so the UI can offer an obvious way to pick a different one.
     */
    data class Ready(val list: TodoList, val defaulted: Boolean) : WidgetUiState
}

/**
 * Resolves the snapshot into a state for one widget.
 *
 * A widget is never silently re-pointed at a different list: if its list is gone it says so.
 */
fun resolveWidgetState(snapshot: TodoSnapshot?, appWidgetId: Int): WidgetUiState = when {
    snapshot == null -> WidgetUiState.Loading
    snapshot.lists.isEmpty() -> WidgetUiState.NoLists
    snapshot.hasBinding(appWidgetId) -> snapshot.listForWidget(appWidgetId)
        ?.let { WidgetUiState.Ready(it, defaulted = false) }
        ?: WidgetUiState.ListDeleted

    else -> WidgetUiState.Ready(snapshot.lists.first(), defaulted = true)
}

/**
 * The widgets take the app's brand palette rather than Glance's default dynamic colours. A widget
 * sits on the home screen right beside the app icon, and the amber is what ties the two together;
 * letting the system recolour one but not the other would break the pairing.
 */
private val WidgetColors = ColorProviders(light = TodoLightColors, dark = TodoDarkColors)

/**
 * One composition shared by all four widget sizes.
 *
 * Data is collected as a flow inside the composition so a tick from the widget re-renders
 * immediately, without waiting for a round trip through the update worker. A push update from
 * [WidgetSync] still happens for every write, which is what keeps widgets correct after the
 * process (or the device) has been restarted and no session is alive to collect anything.
 */
abstract class TodoWidget(val kind: WidgetKind) : GlanceAppWidget() {

    /**
     * `Exact` rather than `Responsive`: responsive mode composes at the *bucket* size and lets the
     * launcher stretch the result, which on a real 4x2 (401x217dp on a Galaxy A34) wastes half the
     * widget. Exact hands the composition the measured size, so the row count and the feature set
     * are chosen for the space that actually exists.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // The picker cannot tell us how big the widget will be, so previews use a representative size.
    override val previewSizeMode: PreviewSizeMode =
        SizeMode.Responsive(setOf(WidgetPreviewSizes.forKind(kind)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = resolveAppWidgetId(context, id)
        val repository = AppGraph.repository(context)
        claimPinHintIfUnbound(context, repository, appWidgetId)

        provideContent {
            val snapshot by repository.snapshot.collectAsState(initial = null)
            GlanceTheme(colors = WidgetColors) {
                TodoWidgetBody(
                    kind = kind,
                    appWidgetId = appWidgetId,
                    state = resolveWidgetState(snapshot, appWidgetId),
                )
            }
        }
    }

    /** Rendered in the widget picker on Android 15+, so users see real structure, not a mock. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            GlanceTheme(colors = WidgetColors) {
                TodoWidgetBody(
                    kind = kind,
                    appWidgetId = INVALID_WIDGET_ID,
                    state = WidgetUiState.Ready(previewList(), defaulted = false),
                    interactive = false,
                )
            }
        }
    }

    /** Frees the binding as soon as the launcher tells us the widget is gone. */
    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val appWidgetId = resolveAppWidgetId(context, glanceId)
        if (isRealWidget(appWidgetId)) {
            runCatching { AppGraph.repository(context).unbindWidgets(listOf(appWidgetId)) }
                .onFailure { Log.w(TAG, "Could not release binding for $appWidgetId", it) }
        }
        super.onDelete(context, glanceId)
    }

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable,
    ) {
        Log.e(TAG, "Composition failed for widget $appWidgetId", throwable)
        super.onCompositionError(context, glanceId, appWidgetId, throwable)
    }

    /**
     * Binds a freshly pinned widget to the list the user chose in the app.
     *
     * Some launchers (One UI among them) do not run the configuration activity for a widget added
     * via `requestPinAppWidget`, and the pin API cannot carry the choice through. So the first
     * unbound render claims the short-lived hint the app left behind. Without this, "add Work to
     * my home screen" would silently produce a widget showing whatever list happens to be first.
     */
    private suspend fun claimPinHintIfUnbound(
        context: Context,
        repository: com.simpletodo.data.TodoRepository,
        appWidgetId: Int,
    ) {
        if (!isRealWidget(appWidgetId)) return
        runCatching {
            if (repository.current().hasBinding(appWidgetId)) return
            val hinted = WidgetPinning.consumeHint(context) ?: return
            repository.bindWidget(appWidgetId, hinted)
        }.onFailure { Log.w(TAG, "Could not claim pin hint for $appWidgetId", it) }
    }

    companion object {
        /**
         * Matches the platform's own sentinel. Getting this wrong is not cosmetic: the pin dialog
         * renders a *live* preview through `provideGlance` with id 0, and treating that as a real
         * widget let the preview consume the pending list choice before the widget it was meant
         * for even existed.
         */
        const val INVALID_WIDGET_ID = AppWidgetManager.INVALID_APPWIDGET_ID

        /** App widget ids issued by the platform are strictly positive. */
        fun isRealWidget(appWidgetId: Int): Boolean = appWidgetId > INVALID_WIDGET_ID

        fun resolveAppWidgetId(context: Context, glanceId: GlanceId): Int =
            runCatching { GlanceAppWidgetManager(context).getAppWidgetId(glanceId) }
                .getOrDefault(INVALID_WIDGET_ID)

        private fun previewList() = TodoList(
            id = "preview",
            name = "Personal",
            accent = 0,
            tasks = listOf(
                Task(id = "p1", title = "Call Mom"),
                Task(id = "p2", title = "Buy groceries"),
                Task(id = "p3", title = "Book train tickets"),
                Task(id = "p4", title = "Pay electricity", isDone = true, completedAt = 1L),
            ),
        )
    }
}

class SmallTodoWidget : TodoWidget(WidgetKind.SMALL)

class MediumTodoWidget : TodoWidget(WidgetKind.MEDIUM)

class LargeTodoWidget : TodoWidget(WidgetKind.LARGE)

class XLargeTodoWidget : TodoWidget(WidgetKind.XLARGE)

/** Factory used by action callbacks to refresh exactly the widget that was tapped. */
fun widgetFor(kind: WidgetKind): TodoWidget = when (kind) {
    WidgetKind.SMALL -> SmallTodoWidget()
    WidgetKind.MEDIUM -> MediumTodoWidget()
    WidgetKind.LARGE -> LargeTodoWidget()
    WidgetKind.XLARGE -> XLargeTodoWidget()
}
