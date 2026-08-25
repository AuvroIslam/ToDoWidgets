package com.simpletodo.widget

import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.LocalState
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
// Two same-named factories: androidx.glance.unit takes one colour, androidx.glance.color takes a
// day/night pair. The alias keeps both usable in one file.
import androidx.glance.color.ColorProvider as dayNightColorProvider
import androidx.glance.unit.ColorProvider
import com.simpletodo.R
import com.simpletodo.data.Task
import com.simpletodo.data.TodoList
import com.simpletodo.quickadd.QuickAddActivity
import com.simpletodo.ui.MainActivity
import com.simpletodo.ui.theme.TodoAccents

private const val TAG = "WidgetContent"

/** Stable handles for instrumentation. */
object WidgetTags {
    const val TITLE = "widget_title"
    const val ADD = "widget_add"
    const val EMPTY = "widget_empty"
    const val ALL_DONE = "widget_all_done"
    const val LOADING = "widget_loading"
    const val NO_LISTS = "widget_no_lists"
    const val LIST_DELETED = "widget_list_deleted"
    const val CHANGE_LIST = "widget_change_list"
    const val FOOTER = "widget_footer"
    const val SHOW_COMPLETED = "widget_show_completed"
    const val CLEAR_COMPLETED = "widget_clear_completed"

    fun delete(taskId: String) = "delete_$taskId"
    fun confirmDelete(taskId: String) = "confirm_delete_$taskId"
    fun cancelDelete(taskId: String) = "cancel_delete_$taskId"
    fun row(taskId: String) = "row_$taskId"
}

@Composable
fun TodoWidgetBody(
    kind: WidgetKind,
    appWidgetId: Int,
    state: WidgetUiState,
    interactive: Boolean = true,
) {
    val spec = WidgetSpec.forSize(LocalSize.current)
    val prefs = if (interactive) LocalState.current as? Preferences else null
    val showCompleted = prefs?.get(WidgetPrefs.SHOW_COMPLETED) ?: false
    val pendingDeleteId = prefs?.get(WidgetPrefs.PENDING_DELETE)

    // The padding lives inside each state rather than on this Box: the ready state's header is a
    // colour band that has to run edge to edge, right up under the widget's rounded corners.
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .widgetCornerRadius(),
    ) {
        val padded = GlanceModifier.fillMaxSize().padding(spec.outerPadding)
        when (state) {
            WidgetUiState.Loading -> Box(padded) { LoadingState() }
            WidgetUiState.NoLists -> Box(padded) { NoListsState(spec, interactive) }
            WidgetUiState.ListDeleted -> Box(padded) {
                ListDeletedState(appWidgetId, spec, interactive)
            }

            is WidgetUiState.Ready -> ReadyState(
                list = state.list,
                defaulted = state.defaulted,
                kind = kind,
                appWidgetId = appWidgetId,
                spec = spec,
                interactive = interactive,
                showCompleted = showCompleted,
                pendingDeleteId = pendingDeleteId,
            )
        }
    }
}

// ------------------------------------------------------------------ states

@Composable
private fun LoadingState() {
    Log.d(TAG, "Rendering widget Loading state")
    Box(
        modifier = GlanceModifier.fillMaxSize().semantics { testTag = WidgetTags.LOADING },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Loading…",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
        )
    }
}

@Composable
private fun NoListsState(
    spec: WidgetSpec,
    interactive: Boolean,
) {
    val context = LocalContext.current
    CenteredMessage(
        testTag = WidgetTags.NO_LISTS,
        icon = R.drawable.ic_widget_checklist,
        title = "No lists yet",
        body = if (spec.layout == WidgetLayout.COMPACT) null else "Create your first list to get going.",
        actionLabel = "Create a list",
        action = actionStartActivity(MainActivity.newListIntent(context)),
        spec = spec,
        interactive = interactive,
    )
}

@Composable
private fun ListDeletedState(
    appWidgetId: Int,
    spec: WidgetSpec,
    interactive: Boolean,
) {
    val context = LocalContext.current
    CenteredMessage(
        testTag = WidgetTags.LIST_DELETED,
        icon = R.drawable.ic_widget_swap,
        title = "List removed",
        body = if (spec.layout == WidgetLayout.COMPACT) null else "The list this widget showed no longer exists.",
        actionLabel = "Pick a list",
        action = actionStartActivity(WidgetConfigActivity.intent(context, appWidgetId)),
        spec = spec,
        interactive = interactive,
    )
}

@Composable
private fun CenteredMessage(
    testTag: String,
    @DrawableRes icon: Int,
    title: String,
    body: String?,
    actionLabel: String,
    action: Action,
    spec: WidgetSpec,
    interactive: Boolean,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().semantics { this.testTag = testTag },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(if (spec.layout == WidgetLayout.COMPACT) 20.dp else 28.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = title,
            maxLines = 2,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = spec.titleSize,
                fontWeight = FontWeight.Medium,
            ),
        )
        if (body != null) {
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = body,
                maxLines = 2,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
        Spacer(GlanceModifier.height(10.dp))
        PillButton(label = actionLabel, action = action, interactive = interactive)
    }
}

@Composable
private fun ReadyState(
    list: TodoList,
    defaulted: Boolean,
    kind: WidgetKind,
    appWidgetId: Int,
    spec: WidgetSpec,
    interactive: Boolean,
    showCompleted: Boolean,
    pendingDeleteId: String?,
) {
    val context = LocalContext.current
    val addAction = actionStartActivity(
        QuickAddActivity.intent(context, list.id, appWidgetId),
    )

    Column(modifier = GlanceModifier.fillMaxSize()) {
        WidgetHeader(
            list = list,
            defaulted = defaulted,
            appWidgetId = appWidgetId,
            spec = spec,
            interactive = interactive,
            addAction = addAction,
        )

        WidgetBody(list = list, spec = spec, modifier = GlanceModifier.defaultWeight()) {
        val active = list.activeTasks
        val completed = list.completedTasks
        val visible = buildVisibleTasks(spec, active, completed, showCompleted)

        if (list.tasks.isEmpty()) {
            EmptyListBody(spec = spec, addAction = addAction, interactive = interactive)
        } else if (visible.isEmpty()) {
            AllDoneBody(
                spec = spec,
                doneCount = completed.size,
                accentIndex = list.accent,
            )
        } else {
            // Every size scrolls, including the 2x2. A widget is a fixed window onto a list that
            // is not: capping the rows to whatever happened to fit meant the tasks past the cap
            // were simply unreachable, with nothing on screen to say they existed.
            LazyColumn(modifier = GlanceModifier.fillMaxSize().defaultWeight()) {
                items(visible, itemId = { it.id.hashCode().toLong() }) { task ->
                    Column {
                        TaskRow(
                            task = task,
                            listId = list.id,
                            accentIndex = list.accent,
                            kind = kind,
                            spec = spec,
                            interactive = interactive,
                            pendingDelete = pendingDeleteId == task.id,
                        )
                        Spacer(GlanceModifier.height(2.dp))
                    }
                }
            }
        }

        // Nothing has been ticked off yet, so a footer would only repeat the header.
        if (spec.showFooter && list.doneCount > 0) {
            WidgetFooter(
                list = list,
                kind = kind,
                spec = spec,
                interactive = interactive,
                showCompleted = showCompleted,
            )
        }
        }
    }
}

/**
 * Everything under the colour band: the counts line, the progress bar, then whatever the caller
 * puts in. This is where the widget's own padding starts, since the band above it is full-bleed.
 */
@Composable
private fun WidgetBody(
    list: TodoList,
    spec: WidgetSpec,
    modifier: GlanceModifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spec.outerPadding, vertical = 8.dp),
    ) {
        if (spec.layout != WidgetLayout.COMPACT) {
            Text(
                text = subtitleFor(list),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
            Spacer(GlanceModifier.height(4.dp))
        }
        if (spec.layout == WidgetLayout.PANEL) {
            LinearProgressIndicator(
                progress = list.progress ?: 0f,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
                color = accentFill(list.accent),
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
            Spacer(GlanceModifier.height(8.dp))
        }
        content()
    }
}

/**
 * Chooses which tasks a given size shows: outstanding work first, most recently finished after it.
 *
 * Completed tasks stay on screen rather than disappearing the instant they are ticked. That is what
 * turns a tap into visible feedback — the row becomes a struck-through ✓ — and it leaves the user
 * somewhere to tap to undo a mistake. The two large sizes, which have a proper show/hide control
 * and a footer, honour [showCompleted] instead.
 */
fun buildVisibleTasks(
    spec: WidgetSpec,
    active: List<Task>,
    completed: List<Task>,
    showCompleted: Boolean,
): List<Task> {
    val mostRecentlyDone = completed.sortedByDescending { it.completedAt ?: it.createdAt }
    val tail = when {
        !spec.showCompletedSection -> mostRecentlyDone
        showCompleted -> mostRecentlyDone
        else -> emptyList()
    }
    return (active + tail).take(spec.maxTasks)
}

// ------------------------------------------------------------------ pieces

/**
 * The list's accent as a fill — the add button, the title bar, the progress track.
 *
 * The same pastel works on both the light and the dark widget background, so this needs no
 * day/night pair the way [accentText] does.
 */
private fun accentFill(accentIndex: Int): ColorProvider =
    ColorProvider(TodoAccents.colorAt(accentIndex))

/**
 * The list's accent as text *on the widget's own surface* — a tick mark, the all-done glyph. That
 * surface flips with the theme and a widget cannot ask which one it is in, so both tones go to the
 * platform as a day/night pair: the deep hue on white, the pastel on near-black.
 */
private fun accentText(accentIndex: Int): ColorProvider = dayNightColorProvider(
    day = TodoAccents.deepAt(accentIndex),
    night = TodoAccents.colorAt(accentIndex),
)

/**
 * The list's accent as text *on the colour band*. Fixed, not a day/night pair: the band is the same
 * pastel in either theme, so the ink on it has to be the deep tone either way — handing it the
 * night variant would paint the pastel onto itself.
 */
private fun accentOnBand(accentIndex: Int): ColorProvider =
    ColorProvider(TodoAccents.deepAt(accentIndex))

/** Ink for anything drawn on top of a pastel fill. */
private val onAccentProvider = ColorProvider(TodoAccents.onAccent)

/** Accent the product wears where no single list is in play — the no-list and deleted states. */
private const val BRAND_ACCENT = 0

@Composable
private fun WidgetHeader(
    list: TodoList,
    defaulted: Boolean,
    appWidgetId: Int,
    spec: WidgetSpec,
    interactive: Boolean,
    addAction: Action,
) {
    val context = LocalContext.current
    val accent = accentFill(list.accent)
    val accentInk = accentOnBand(list.accent)
    val titleAction = if (defaulted) {
        actionStartActivity(WidgetConfigActivity.intent(context, appWidgetId))
    } else {
        actionStartActivity(MainActivity.listIntent(context, list.id))
    }

    val compact = spec.layout == WidgetLayout.COMPACT

    // A band of the list's own colour across the top, with the name and its controls sitting on it
    // in the deep tone of that same hue. A green list is green all the way through — the bar, the
    // name and the add button — which is what makes two widgets side by side tellable apart at a
    // glance, before either one is read.
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(accent)
            .padding(horizontal = spec.outerPadding, vertical = if (compact) 5.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.cat_badge),
            contentDescription = null,
            modifier = GlanceModifier.size(if (compact) 18.dp else 22.dp),
        )
        Spacer(GlanceModifier.width(7.dp))

        Text(
            text = list.name,
            maxLines = 1,
            modifier = GlanceModifier
                .defaultWeight()
                .clickableIf(interactive, titleAction)
                .semantics {
                    testTag = WidgetTags.TITLE
                    contentDescription = "${list.name}, ${list.activeCount} tasks left. Open list."
                },
            style = TextStyle(
                color = accentInk,
                fontSize = spec.titleSize,
                fontWeight = FontWeight.Bold,
            ),
        )

        if (spec.showListControls) {
            IconAction(
                icon = R.drawable.ic_widget_swap,
                label = "Change the list this widget shows",
                action = actionStartActivity(WidgetConfigActivity.intent(context, appWidgetId)),
                size = spec.controlSize * 0.72f,
                tint = accentInk,
                testTag = WidgetTags.CHANGE_LIST,
                interactive = interactive,
            )
            Spacer(GlanceModifier.width(2.dp))
        }

        IconAction(
            icon = R.drawable.ic_widget_add,
            label = "Add a task to ${list.name}",
            action = addAction,
            size = if (compact) 28.dp else spec.controlSize * 0.72f,
            tint = accentInk,
            testTag = WidgetTags.ADD,
            interactive = interactive,
        )
    }
}

private fun subtitleFor(list: TodoList): String = when {
    list.tasks.isEmpty() -> "No tasks yet"
    list.activeCount == 0 -> "All ${list.doneCount} done"
    else -> "${list.activeCount} left · ${list.doneCount} done"
}

@Composable
private fun TaskRow(
    task: Task,
    listId: String,
    accentIndex: Int,
    kind: WidgetKind,
    spec: WidgetSpec,
    interactive: Boolean,
    pendingDelete: Boolean,
) {
    if (pendingDelete) {
        ConfirmDeleteRow(task = task, listId = listId, kind = kind, spec = spec, interactive = interactive)
        return
    }

    val params = actionParametersOf(
        WidgetParams.LIST_ID to listId,
        WidgetParams.TASK_ID to task.id,
        WidgetParams.KIND to kind.key,
    )

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(spec.rowHeight)
            // The whole row ticks the task, rather than a small circle and the label carrying
            // separate clicks. Inside a LazyColumn every click is delivered by launching Glance's
            // invisible trampoline activity, and one target per item is both the most forgiving
            // thing to hit with a thumb and the most reliable thing for that route to deliver:
            // on a 2x2 the circle alone was a 36dp box holding an 18dp glyph.
            .clickableIf(interactive, actionRunCallback<ToggleTaskAction>(params))
            .semantics {
                testTag = WidgetTags.row(task.id)
                contentDescription = if (task.isDone) {
                    "${task.title}, completed. Mark as not done."
                } else {
                    "${task.title}, not completed. Mark as done."
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Now purely the state indicator: the row around it carries the click.
        Box(
            modifier = GlanceModifier.size(spec.controlSize),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(
                    if (task.isDone) R.drawable.ic_widget_circle_check else R.drawable.ic_widget_circle,
                ),
                contentDescription = null,
                modifier = GlanceModifier.size(spec.controlSize * 0.52f),
                colorFilter = ColorFilter.tint(
                    if (task.isDone) accentText(accentIndex) else GlanceTheme.colors.onSurfaceVariant,
                ),
            )
        }

        Text(
            text = task.title,
            maxLines = spec.taskMaxLines,
            modifier = GlanceModifier
                .defaultWeight()
                .padding(end = 4.dp),
            style = TextStyle(
                color = if (task.isDone) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
                fontSize = spec.taskSize,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
            ),
        )

        if (spec.showDeleteButton) {
            IconAction(
                icon = R.drawable.ic_widget_delete,
                label = "Delete ${task.title}",
                action = actionRunCallback<RequestDeleteTaskAction>(params),
                size = spec.controlSize * 0.86f,
                iconScale = 0.48f,
                tint = GlanceTheme.colors.onSurfaceVariant,
                testTag = WidgetTags.delete(task.id),
                interactive = interactive,
            )
        }
    }
}

/**
 * The armed state of a row. Deleting takes two deliberate taps and the confirm control sits on the
 * opposite side of the row from the completion circle, so a mis-tap costs nothing.
 */
@Composable
private fun ConfirmDeleteRow(
    task: Task,
    listId: String,
    kind: WidgetKind,
    spec: WidgetSpec,
    interactive: Boolean,
) {
    val params = actionParametersOf(
        WidgetParams.LIST_ID to listId,
        WidgetParams.TASK_ID to task.id,
        WidgetParams.KIND to kind.key,
    )
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(spec.rowHeight)
            .background(GlanceTheme.colors.errorContainer)
            .cornerRadius(12.dp)
            .padding(start = 10.dp)
            .semantics { testTag = WidgetTags.row(task.id) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Delete “${task.title}”?",
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = GlanceTheme.colors.onErrorContainer,
                fontSize = spec.taskSize,
                fontWeight = FontWeight.Medium,
            ),
        )
        IconAction(
            icon = R.drawable.ic_widget_close,
            label = "Keep ${task.title}",
            action = actionRunCallback<CancelDeleteTaskAction>(params),
            size = spec.controlSize * 0.86f,
            iconScale = 0.46f,
            tint = GlanceTheme.colors.onErrorContainer,
            testTag = WidgetTags.cancelDelete(task.id),
            interactive = interactive,
        )
        IconAction(
            icon = R.drawable.ic_widget_check,
            label = "Confirm deleting ${task.title}",
            action = actionRunCallback<ConfirmDeleteTaskAction>(params),
            size = spec.controlSize * 0.86f,
            iconScale = 0.46f,
            tint = GlanceTheme.colors.onError,
            background = GlanceTheme.colors.error,
            testTag = WidgetTags.confirmDelete(task.id),
            interactive = interactive,
        )
    }
}

@Composable
private fun EmptyListBody(spec: WidgetSpec, addAction: Action, interactive: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxSize().semantics { testTag = WidgetTags.EMPTY },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Nothing here yet",
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (spec.layout == WidgetLayout.COMPACT) 13.sp else 15.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = if (spec.layout == WidgetLayout.COMPACT) "Tap + to add" else "Add your first task and it shows up right here.",
            maxLines = 2,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
        if (spec.layout != WidgetLayout.COMPACT) {
            Spacer(GlanceModifier.height(10.dp))
            PillButton(label = "Add a task", action = addAction, interactive = interactive)
        }
    }
}

@Composable
private fun AllDoneBody(
    spec: WidgetSpec,
    doneCount: Int,
    accentIndex: Int,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().semantics { testTag = WidgetTags.ALL_DONE },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_circle_check),
            contentDescription = null,
            modifier = GlanceModifier.size(if (spec.layout == WidgetLayout.COMPACT) 22.dp else 30.dp),
            colorFilter = ColorFilter.tint(accentText(accentIndex)),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = "All done",
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (spec.layout == WidgetLayout.COMPACT) 13.sp else 15.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        if (spec.layout != WidgetLayout.COMPACT) {
            Text(
                text = "$doneCount finished. Nice.",
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun WidgetFooter(
    list: TodoList,
    kind: WidgetKind,
    spec: WidgetSpec,
    interactive: Boolean,
    showCompleted: Boolean,
) {
    val params = actionParametersOf(
        WidgetParams.LIST_ID to list.id,
        WidgetParams.KIND to kind.key,
    )
    Spacer(GlanceModifier.height(4.dp))
    Row(
        modifier = GlanceModifier.fillMaxWidth().semantics { testTag = WidgetTags.FOOTER },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (showCompleted) {
                "Showing ${list.doneCount} completed"
            } else {
                "${list.doneCount} completed hidden"
            },
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
        )
        IconAction(
            icon = if (showCompleted) R.drawable.ic_widget_eye_off else R.drawable.ic_widget_eye,
            label = if (showCompleted) "Hide completed tasks" else "Show completed tasks",
            action = actionRunCallback<ToggleShowCompletedAction>(params),
            size = 34.dp,
            iconScale = 0.5f,
            tint = GlanceTheme.colors.onSurfaceVariant,
            testTag = WidgetTags.SHOW_COMPLETED,
            interactive = interactive,
        )
        IconAction(
            icon = R.drawable.ic_widget_broom,
            label = "Clear ${list.doneCount} completed tasks",
            action = actionRunCallback<ClearCompletedAction>(params),
            size = 34.dp,
            iconScale = 0.5f,
            tint = GlanceTheme.colors.onSurfaceVariant,
            testTag = WidgetTags.CLEAR_COMPLETED,
            interactive = interactive,
        )
    }
}

// ----------------------------------------------------------------- helpers

/**
 * A square-ish tap target with a centred glyph.
 *
 * Written by hand rather than using Glance's `CircleIconButton` because the touch target has to
 * shrink with the widget: a 2x2 widget cannot afford the component's fixed 48dp.
 */
@Composable
private fun IconAction(
    @DrawableRes icon: Int,
    label: String,
    action: Action,
    size: Dp,
    tint: ColorProvider,
    interactive: Boolean,
    iconScale: Float = 0.5f,
    background: ColorProvider? = null,
    testTag: String? = null,
) {
    var modifier = GlanceModifier.size(size)
    if (background != null) {
        modifier = modifier.background(background).cornerRadius(size / 2)
    }
    modifier = modifier.clickableIf(interactive, action).semantics {
        contentDescription = label
        if (testTag != null) this.testTag = testTag
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(size * iconScale),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

@Composable
private fun PillButton(label: String, action: Action, interactive: Boolean) {
    Box(
        modifier = GlanceModifier
            .background(accentFill(BRAND_ACCENT))
            .cornerRadius(18.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clickableIf(interactive, action)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(
                color = onAccentProvider,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

private fun GlanceModifier.clickableIf(enabled: Boolean, action: Action): GlanceModifier =
    if (enabled) this.clickable(action) else this

private fun GlanceModifier.widgetCornerRadius(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        cornerRadius(16.dp)
    }
