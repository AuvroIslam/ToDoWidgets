package com.simpletodo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.simpletodo.R
import com.simpletodo.data.Task
import com.simpletodo.data.ThemeMode
import com.simpletodo.data.TodoLimits
import com.simpletodo.data.TodoList
import com.simpletodo.data.TodoSnapshot
import com.simpletodo.ui.theme.TodoAccents
import com.simpletodo.ui.theme.accentTextColor

/** A task together with the list it belongs to, which the "All" filter needs and a list view has. */
private data class TaskEntry(val list: TodoList, val task: Task)

/** Accent index the product itself wears, for the parts of the UI that belong to no single list. */
private const val BRAND_ACCENT = 0

/**
 * The whole app on one screen: lists are filter chips across the top, their tasks are cards below.
 *
 * There is deliberately no separate lists screen. Switching lists is a single tap on a chip, which
 * is the operation this app is built around — everything else (renaming, colour, widgets) lives
 * behind the overflow menu because it happens once per list, not once per glance.
 */
@Composable
fun HomeScreen(
    snapshot: TodoSnapshot,
    viewModel: TodoViewModel,
    snackbarHostState: SnackbarHostState,
    onNewList: () -> Unit,
    onAddWidget: (String) -> Unit,
    themeMode: ThemeMode,
    onTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lists = snapshot.lists
    val selected = snapshot.listById(viewModel.selectedListId)

    var renaming by remember { mutableStateOf(false) }
    var accentPicking by remember { mutableStateOf(false) }
    var confirmDeleteList by remember { mutableStateOf(false) }
    var activeCollapsed by remember { mutableStateOf(false) }
    var completedCollapsed by remember { mutableStateOf(false) }
    var editingTaskId by remember(selected?.id) { mutableStateOf<String?>(null) }

    // Back steps out of a single list and into "All" before it leaves the app.
    BackHandler(enabled = selected != null) { viewModel.openList(null) }

    // Local order held only while a drag is in flight; committed once on release.
    var dragOrder by remember(selected?.id) { mutableStateOf<List<Task>?>(null) }

    val scope = lists.filter { selected == null || it.id == selected.id }
    val active: List<TaskEntry> = if (selected != null) {
        (dragOrder ?: selected.activeTasks).map { TaskEntry(selected, it) }
    } else {
        scope.flatMap { list -> list.activeTasks.map { TaskEntry(list, it) } }
    }
    val completed: List<TaskEntry> = scope
        .flatMap { list -> list.completedTasks.map { TaskEntry(list, it) } }
        .sortedByDescending { it.task.completedAt ?: it.task.createdAt }

    // Only worth naming the owning list when a card could plausibly belong to a different one.
    val showListNames = selected == null && lists.size > 1
    val totalCount = scope.sumOf { it.tasks.size }
    val doneCount = scope.sumOf { it.doneCount }
    val accentIndex = selected?.accent ?: BRAND_ACCENT
    val accent = TodoAccents.colorAt(accentIndex)
    val accentInk = accentTextColor(accentIndex)

    // Reordering only makes sense inside one list; across "All" the order is a merge of several.
    val reorderable = selected != null && !activeCollapsed
    val listState = rememberLazyListState()
    val dragState = rememberDragDropState(
        listState = listState,
        firstIndex = 1, // index 0 is the "Tasks (n)" section header
        reorderableCount = if (reorderable) active.size else 0,
        onMove = { from, to ->
            val base = dragOrder ?: selected?.activeTasks ?: return@rememberDragDropState
            if (from in base.indices && to in base.indices) {
                dragOrder = base.toMutableList().apply { add(to, removeAt(from)) }
            }
        },
        onSettle = {
            val list = selected
            dragOrder?.let { order ->
                if (list != null) viewModel.commitActiveOrder(list.id, order.map { it.id })
            }
            dragOrder = null
        },
    )

    Scaffold(
        modifier = modifier.testTag(AppTags.HOME_SCREEN),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HomeHeader(
                lists = lists,
                selected = selected,
                totalCount = totalCount,
                doneCount = doneCount,
                accent = accent,
                onSelect = { viewModel.openList(it) },
                onNewList = onNewList,
                onRename = { renaming = true },
                onAccent = { accentPicking = true },
                onAddWidget = { selected?.let { onAddWidget(it.id) } },
                onClearCompleted = { selected?.let { viewModel.clearCompleted(it.id) } },
                onDeleteList = { confirmDeleteList = true },
                themeMode = themeMode,
                onTheme = onTheme,
                hasCompleted = completed.isNotEmpty(),
            )
        },
        bottomBar = {
            val target = selected ?: lists.firstOrNull()
            if (target != null) {
                Composer(
                    // Naming the destination keeps "All" unambiguous: the task has to land
                    // somewhere, and the user can see where before they type.
                    placeholder = if (showListNames) "Add to ${target.name}" else "What's next?",
                    accent = TodoAccents.colorAt(target.accent),
                    enabled = target.tasks.size < TodoLimits.MAX_TASKS_PER_LIST,
                    onSubmit = { title -> viewModel.addTask(target.id, title) },
                )
            }
        },
    ) { padding ->
        val content = Modifier.fillMaxSize().padding(padding)

        when {
            lists.isEmpty() -> EmptyState(
                modifier = content.testTag(AppTags.EMPTY_LISTS),
                pose = CatPose.Sit,
                title = "Start with a list",
                body = "Personal, Work, Shopping — whatever you like. Each list gets its own " +
                    "tasks and can live on your home screen.",
                action = { Button(onClick = onNewList) { Text("Create your first list") } },
            )

            totalCount == 0 -> EmptyState(
                modifier = content.testTag(AppTags.EMPTY_TASKS),
                pose = CatPose.Pounce,
                title = "Nothing to do here",
                body = "Type your first task below and hit send. It shows up on your widget too.",
            )

            else -> LazyColumn(
                state = listState,
                modifier = content.reorderable(dragState),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "active_header") {
                    SectionHeader(
                        label = "Tasks",
                        count = active.size,
                        collapsed = activeCollapsed,
                        onToggle = { activeCollapsed = !activeCollapsed },
                    )
                }

                if (!activeCollapsed) {
                    if (active.isEmpty()) {
                        item(key = "all_done") { AllDoneCard() }
                    } else {
                        items(count = active.size, key = { active[it].task.id }) { index ->
                            val entry = active[index]
                            // +1 for the header above; the drag state speaks in lazy indices.
                            val dragging = dragState.draggingItemIndex == index + 1
                            val elevation by animateFloatAsState(
                                targetValue = if (dragging) 10f else 0f,
                                label = "drag",
                            )
                            val itemModifier = if (dragging) {
                                Modifier
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        translationY = dragState.draggingItemOffset
                                        shadowElevation = elevation
                                    }
                            } else {
                                Modifier.animateItem()
                            }

                            TaskSlot(
                                entry = entry,
                                showListName = showListNames,
                                editing = editingTaskId == entry.task.id,
                                modifier = itemModifier,
                                viewModel = viewModel,
                                onStartEdit = { editingTaskId = entry.task.id },
                                onEndEdit = { editingTaskId = null },
                                moveUp = if (reorderable && index > 0) {
                                    { viewModel.moveActiveTask(entry.list.id, index, index - 1) }
                                } else {
                                    null
                                },
                                moveDown = if (reorderable && index < active.lastIndex) {
                                    { viewModel.moveActiveTask(entry.list.id, index, index + 1) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }

                if (completed.isNotEmpty()) {
                    item(key = "completed_header") {
                        SectionHeader(
                            label = "Completed",
                            count = completed.size,
                            collapsed = completedCollapsed,
                            onToggle = { completedCollapsed = !completedCollapsed },
                            action = {
                                TextButton(
                                    onClick = { scope.forEach { viewModel.clearCompleted(it.id) } },
                                ) {
                                    Text("Clear all", color = accentInk)
                                }
                            },
                        )
                    }

                    if (!completedCollapsed) {
                        items(count = completed.size, key = { completed[it].task.id }) { index ->
                            val entry = completed[index]
                            TaskSlot(
                                entry = entry,
                                showListName = showListNames,
                                editing = editingTaskId == entry.task.id,
                                modifier = Modifier.animateItem(),
                                viewModel = viewModel,
                                onStartEdit = { editingTaskId = entry.task.id },
                                onEndEdit = { editingTaskId = null },
                                moveUp = null,
                                moveDown = null,
                            )
                        }
                    }
                }
            }
        }
    }

    val target = selected
    if (target != null && renaming) {
        NameListDialog(
            title = "Rename list",
            confirmLabel = "Save",
            initialValue = target.name,
            initialAccent = target.accent,
            onDismiss = { renaming = false },
            onConfirm = { name, chosen ->
                renaming = false
                viewModel.renameList(target.id, name)
                viewModel.setAccent(target.id, chosen)
            },
        )
    }

    if (target != null && accentPicking) {
        AccentPickerDialog(
            current = target.accent,
            onDismiss = { accentPicking = false },
            onPick = { viewModel.setAccent(target.id, it) },
        )
    }

    if (target != null && confirmDeleteList) {
        val widgetCount = snapshot.widgetBindings.count { it.value == target.id }
        ConfirmDialog(
            title = "Delete “${target.name}”?",
            message = buildString {
                append("${target.tasks.size} task")
                if (target.tasks.size != 1) append("s")
                append(" will be removed.")
                if (widgetCount > 0) {
                    append(" ")
                    append(widgetCount)
                    append(if (widgetCount == 1) " widget shows" else " widgets show")
                    append(" this list and will ask you to pick another one.")
                }
            },
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDeleteList = false },
            onConfirm = {
                confirmDeleteList = false
                viewModel.deleteList(target.id)
            },
        )
    }
}

// ------------------------------------------------------------------ header

@Composable
private fun HomeHeader(
    lists: List<TodoList>,
    selected: TodoList?,
    totalCount: Int,
    doneCount: Int,
    accent: Color,
    hasCompleted: Boolean,
    onSelect: (String?) -> Unit,
    onNewList: () -> Unit,
    onRename: () -> Unit,
    onAccent: () -> Unit,
    onAddWidget: () -> Unit,
    onClearCompleted: () -> Unit,
    onDeleteList: () -> Unit,
    themeMode: ThemeMode,
    onTheme: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // On a light theme no badge behind it: the page is the same white the launcher icon
            // sits on, so the cat reads as the mark itself rather than as a sticker pasted onto
            // the header. On a dark theme that would be a black cat on a near-black page, so it
            // gets the brand pastel back as a disc to sit on — the same amber the widget's header
            // band uses, which keeps the two marks recognisably the same one.
            // The launcher icon's round form, reused verbatim: the mark in the app bar and the
            // mark on the home screen are then literally the same image on the same amber, which
            // is what makes the app recognisable as the thing the user tapped. It carries its own
            // ground, so it needs no help from the theme -- and the cat is centred on its *head*
            // rather than its outline, since the tail would otherwise pull the face off-axis.
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(10.dp))
            Text("TodoWidget", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (selected != null) {
                        DropdownMenuItem(
                            text = { Text("Rename list") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text("Colour") },
                            leadingIcon = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                            onClick = { menuOpen = false; onAccent() },
                        )
                        DropdownMenuItem(
                            text = { Text("Add to home screen") },
                            leadingIcon = { Icon(Icons.Rounded.Widgets, contentDescription = null) },
                            onClick = { menuOpen = false; onAddWidget() },
                        )
                        if (hasCompleted) {
                            DropdownMenuItem(
                                text = { Text("Clear completed") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                                },
                                onClick = { menuOpen = false; onClearCompleted() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete list") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; onDeleteList() },
                        )
                        HorizontalDivider()
                    }
                    DropdownMenuItem(
                        text = { Text("New list") },
                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        onClick = { menuOpen = false; onNewList() },
                    )
                    DropdownMenuItem(
                        text = { Text("Theme") },
                        // The icon is the current mode, so the setting can be read off the menu
                        // without opening the dialog to find out what it is.
                        leadingIcon = {
                            Icon(
                                imageVector = when (themeMode) {
                                    ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                    ThemeMode.DARK -> Icons.Rounded.DarkMode
                                    ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                                },
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            Text(
                                text = themeMode.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = { menuOpen = false; onTheme() },
                    )
                }
            }
        }

        if (lists.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item(key = "all") {
                    FilterPill(
                        label = "All",
                        color = TodoAccents.colorAt(BRAND_ACCENT),
                        // Neutral when idle: "All" is not a list, and an amber wash here would be
                        // indistinguishable from an amber list sitting next to it.
                        wash = MaterialTheme.colorScheme.surfaceContainerHigh,
                        selected = selected == null,
                        onClick = { onSelect(null) },
                        modifier = Modifier.testTag(AppTags.listChip("all")),
                    )
                }
                items(lists, key = { it.id }) { list ->
                    FilterPill(
                        label = list.name,
                        color = TodoAccents.colorAt(list.accent),
                        selected = list.id == selected?.id,
                        onClick = { onSelect(list.id) },
                        modifier = Modifier.testTag(AppTags.listChip(list.id)),
                    )
                }
                item(key = "new") {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClick = onNewList)
                            .semantics { contentDescription = "New list" }
                            .testTag(AppTags.NEW_LIST),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (totalCount > 0) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${totalCount - doneCount} left · $doneCount done",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${(doneCount * 100) / totalCount}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { doneCount.toFloat() / totalCount },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun FilterPill(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wash: Color? = null,
) {
    // Unselected chips carry a wash of their own accent, so a list keeps its identity even when
    // it is not the one being looked at.
    val background = when {
        selected -> color
        wash != null -> wash
        // Enough of the accent to say which list this is, but well short of the solid fill — with
        // a pastel palette the selected chip has no saturation left to out-shout a heavy wash.
        else -> color.copy(alpha = 0.22f).compositeOver(MaterialTheme.colorScheme.surfaceContainer)
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick, role = Role.Tab)
            .semantics { this.selected = selected },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) TodoAccents.onAccent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ----------------------------------------------------------------- sections

@Composable
private fun SectionHeader(
    label: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp, horizontal = 2.dp)
                .semantics {
                    contentDescription = "$label, $count, " +
                        if (collapsed) "collapsed" else "expanded"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$label ($count)",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun AllDoneCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatMascot(pose = CatPose.Jump, size = 52.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text("All done for now", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Nothing left in this list.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// -------------------------------------------------------------------- tasks

/** Picks between the read-only card and the inline editor for one task. */
@Composable
private fun TaskSlot(
    entry: TaskEntry,
    showListName: Boolean,
    editing: Boolean,
    modifier: Modifier,
    viewModel: TodoViewModel,
    onStartEdit: () -> Unit,
    onEndEdit: () -> Unit,
    moveUp: (() -> Unit)?,
    moveDown: (() -> Unit)?,
) {
    val (list, task) = entry

    if (editing) {
        EditingTaskCard(
            task = task,
            fill = TodoAccents.colorAt(list.accent),
            ink = accentTextColor(list.accent),
            modifier = modifier,
            onCommit = { newTitle ->
                onEndEdit()
                if (newTitle.isNotBlank() && newTitle != task.title) {
                    viewModel.editTask(list.id, task.id, newTitle)
                }
            },
        )
        return
    }

    TaskCard(
        task = task,
        accent = TodoAccents.colorAt(list.accent),
        listName = list.name.takeIf { showListName },
        modifier = modifier,
        onToggle = { viewModel.toggleTask(list.id, task.id) },
        onEdit = onStartEdit,
        onDelete = { viewModel.deleteTask(list.id, task.id) },
        moveUp = moveUp,
        moveDown = moveDown,
    )
}

@Composable
private fun TaskCard(
    task: Task,
    accent: Color,
    listName: String?,
    modifier: Modifier,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    moveUp: (() -> Unit)?,
    moveDown: (() -> Unit)?,
) {
    val actions = buildList {
        moveUp?.let { add(CustomAccessibilityAction("Move up") { it(); true }) }
        moveDown?.let { add(CustomAccessibilityAction("Move down") { it(); true }) }
        add(CustomAccessibilityAction("Edit task") { onEdit(); true })
        add(CustomAccessibilityAction("Delete task") { onDelete(); true })
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onEdit)
            .heightIn(min = 62.dp)
            .padding(start = 8.dp, end = 4.dp)
            .testTag(AppTags.taskItem(task.id))
            .semantics(mergeDescendants = true) {
                contentDescription = if (task.isDone) {
                    "${task.title}, completed"
                } else {
                    "${task.title}, not completed"
                }
                customActions = actions
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleCheck(
            done = task.isDone,
            accent = accent,
            onToggle = onToggle,
            modifier = Modifier.testTag(AppTags.taskCheckbox(task.id)),
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (task.isDone) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                ),
                color = if (task.isDone) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (listName != null) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = listName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag(AppTags.taskDelete(task.id)),
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete ${task.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun CircleCheck(
    done: Boolean,
    accent: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle, role = Role.Checkbox)
            .semantics {
                contentDescription = if (done) "Mark as not done" else "Mark as done"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .then(
                    if (done) {
                        Modifier.background(accent)
                    } else {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = TodoAccents.onAccent,
                )
            }
        }
    }
}

@Composable
private fun EditingTaskCard(
    task: Task,
    fill: Color,
    ink: Color,
    modifier: Modifier,
    onCommit: (String) -> Unit,
) {
    var draft by remember(task.id) { mutableStateOf(task.title) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(task.id) { focusRequester.requestFocus() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(2.dp, ink, MaterialTheme.shapes.medium)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = draft,
            onValueChange = { draft = it.take(TodoLimits.TASK_TITLE_MAX) },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { if (!it.isFocused) onCommit(draft) }
                .testTag("task_edit_${task.id}"),
            singleLine = false,
            maxLines = 4,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = transparentFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit(draft) }),
        )
        Spacer(Modifier.width(6.dp))
        FilledIconButton(
            onClick = { onCommit(draft) },
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = fill,
                contentColor = TodoAccents.onAccent,
            ),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = "Save task")
        }
    }
}

// ----------------------------------------------------------------- composer

@Composable
private fun Composer(
    placeholder: String,
    accent: Color,
    enabled: Boolean,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    fun submit() {
        val value = text.trim()
        if (value.isEmpty()) return
        onSubmit(value)
        text = ""
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = text,
                onValueChange = { text = it.take(TodoLimits.TASK_TITLE_MAX) },
                modifier = Modifier.weight(1f).testTag(AppTags.TASK_INPUT),
                enabled = enabled,
                placeholder = {
                    Text(
                        text = if (enabled) placeholder else "This list is full",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                singleLine = true,
                shape = CircleShape,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = pillFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Spacer(Modifier.width(10.dp))
            FilledIconButton(
                onClick = { submit() },
                enabled = enabled && text.isNotBlank(),
                modifier = Modifier.size(52.dp).testTag(AppTags.TASK_SUBMIT),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = accent,
                    contentColor = TodoAccents.onAccent,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Add task")
            }
        }
    }
}

@Composable
private fun pillFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

// ------------------------------------------------------------ empty states

@Composable
private fun EmptyState(
    modifier: Modifier,
    pose: CatPose,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CatMascot(pose = pose)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(22.dp))
            action()
        }
    }
}
