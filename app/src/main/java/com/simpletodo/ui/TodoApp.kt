package com.simpletodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simpletodo.widget.WidgetKind
import com.simpletodo.widget.WidgetPinning
import kotlinx.coroutines.launch

object AppTags {
    const val HOME_SCREEN = "home_screen"
    const val NEW_LIST = "new_list"
    const val TASK_INPUT = "task_input"
    const val TASK_SUBMIT = "task_submit"
    const val EMPTY_LISTS = "empty_lists"
    const val EMPTY_TASKS = "empty_tasks"

    fun listChip(id: String) = "list_chip_$id"
    fun taskItem(id: String) = "task_item_$id"
    fun taskCheckbox(id: String) = "task_checkbox_$id"
    fun taskDelete(id: String) = "task_delete_$id"
}

@Composable
fun TodoApp(
    viewModel: TodoViewModel,
    requestedListId: String?,
    requestedNewList: Boolean,
    requestNonce: Int,
    onRequestHandled: () -> Unit,
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showNewListDialog by remember { mutableStateOf(false) }
    var pinTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(requestNonce) {
        if (requestedListId != null) viewModel.openList(requestedListId)
        if (requestedNewList) showNewListDialog = true
        if (requestedListId != null || requestedNewList) onRequestHandled()
    }

    val undo = viewModel.pendingUndo
    LaunchedEffect(undo) {
        val pending = undo ?: return@LaunchedEffect
        val message = when (pending) {
            is PendingUndo.DeletedTask -> "Task deleted"
            is PendingUndo.DeletedList -> "List “${pending.list.name}” deleted"
        }
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
            withDismissAction = false,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undo(pending)
        viewModel.clearUndo()
    }

    // Deliberately not a Scaffold: HomeScreen owns its own Scaffold and window insets, and
    // nesting them would apply the status/navigation bar padding twice.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        val current = snapshot
        if (current == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            // One column at every width. On a tablet the chips and cards stay a comfortable
            // reading width instead of stretching a checkbox and its title a foot apart.
            HomeScreen(
                snapshot = current,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                onNewList = { showNewListDialog = true },
                onAddWidget = { pinTarget = it },
                modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
            )
        }
    }

    if (showNewListDialog) {
        NameListDialog(
            title = "New list",
            confirmLabel = "Create",
            initialValue = "",
            initialAccent = snapshot?.lists?.size ?: 0,
            onDismiss = { showNewListDialog = false },
            onConfirm = { name, accent ->
                showNewListDialog = false
                viewModel.createList(name, accent) { id -> viewModel.openList(id) }
            },
        )
    }

    val pinListId = pinTarget
    if (pinListId != null) {
        val list = snapshot?.listById(pinListId)
        AddWidgetDialog(
            listName = list?.name ?: "this list",
            supported = WidgetPinning.isSupported(context),
            onDismiss = { pinTarget = null },
            onPick = { kind: WidgetKind ->
                pinTarget = null
                scope.launch { WidgetPinning.requestPin(context, pinListId, kind) }
            },
        )
    }
}
