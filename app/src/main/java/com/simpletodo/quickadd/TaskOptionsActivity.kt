package com.simpletodo.quickadd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.simpletodo.AppGraph
import com.simpletodo.data.TodoLimits
import com.simpletodo.ui.LinkDialog
import com.simpletodo.ui.copyToClipboard
import com.simpletodo.ui.openLink
import com.simpletodo.ui.theme.SimpleTodoTheme
import com.simpletodo.ui.theme.TodoAccents
import kotlinx.coroutines.launch

/**
 * The ⋮ on a widget row lands here: the same options a task's menu offers in the app, as a sheet
 * over the home screen.
 *
 * A widget cannot open a real menu — RemoteViews has no popup — and it cannot host a text field
 * for editing either, so like [QuickAddActivity] this is the closest the platform allows to doing
 * it on the widget itself. Every action closes the sheet; the widget repaints from the write.
 */
class TaskOptionsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val listId = intent?.getStringExtra(EXTRA_LIST_ID)
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID)
        if (listId == null || taskId == null) {
            finish()
            return
        }
        enableEdgeToEdge()
        val repository = AppGraph.repository(this)

        setContent {
            SimpleTodoTheme {
                val snapshot by repository.snapshot.collectAsStateWithLifecycle(initialValue = null)
                val list = snapshot?.listById(listId)
                val task = list?.tasks?.firstOrNull { it.id == taskId }

                val sheet = rememberSheetState()
                val view = LocalView.current

                // Deleted elsewhere (the app, another widget) between the tap and the sheet.
                LaunchedEffect(snapshot, task) {
                    if (snapshot != null && task == null) finishWithoutTransition()
                }
                if (list == null || task == null) return@SimpleTodoTheme

                var mode by remember { mutableStateOf(SheetMode.MENU) }
                val url = task.url

                // Writes, taps the "done" haptic, and lets the sheet slide away.
                fun commit(write: suspend () -> Unit) {
                    lifecycleScope.launch {
                        write()
                        view.confirmHaptic()
                        sheet.dismiss()
                    }
                }

                SheetHost(
                    state = sheet,
                    onDismissed = { finishWithoutTransition() },
                    modifier = Modifier.testTag(TaskOptionsTags.SHEET),
                ) {
                    Column(modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)) {
                        SheetHeader(title = task.title, listName = list.name, accent = list.accent)
                        Spacer(Modifier.height(8.dp))

                        // The link dialog floats over the menu, so the menu stays put beneath it.
                        AnimatedContent(
                            targetState = if (mode == SheetMode.LINK) SheetMode.MENU else mode,
                            transitionSpec = {
                                (fadeIn(tween(durationMillis = 200, delayMillis = 70)) togetherWith
                                    fadeOut(tween(durationMillis = 90)))
                                    .using(SizeTransform(clip = false))
                            },
                            label = "sheet_mode",
                        ) { shown ->
                            Column {
                                when (shown) {
                                    SheetMode.MENU, SheetMode.LINK -> {
                                        SheetAction(Icons.Rounded.Edit, "Edit") { mode = SheetMode.EDIT }
                                        SheetAction(
                                            Icons.Rounded.AddLink,
                                            if (url == null) "Add link" else "Change link",
                                        ) { mode = SheetMode.LINK }
                                        if (url != null) {
                                            SheetAction(Icons.AutoMirrored.Rounded.OpenInNew, "Open link") {
                                                if (!openLink(this@TaskOptionsActivity, url)) {
                                                    toast("No app on this phone can open that link")
                                                }
                                                finishWithoutTransition()
                                            }
                                        }
                                        SheetAction(Icons.Rounded.ContentCopy, "Copy text") {
                                            copy(task.title, sheet)
                                        }
                                        if (url != null) {
                                            SheetAction(Icons.Rounded.Link, "Copy link") { copy(url, sheet) }
                                        }
                                        SheetAction(
                                            Icons.Rounded.DeleteOutline,
                                            "Delete",
                                            tint = MaterialTheme.colorScheme.error,
                                        ) { mode = SheetMode.CONFIRM_DELETE }
                                    }

                                    SheetMode.EDIT -> EditTitle(
                                        initial = task.title,
                                        accent = list.accent,
                                        onCancel = { mode = SheetMode.MENU },
                                        onSave = { title -> commit { repository.editTask(list.id, task.id, title) } },
                                    )

                                    SheetMode.CONFIRM_DELETE -> ConfirmDelete(
                                        onCancel = { mode = SheetMode.MENU },
                                        onDelete = { commit { repository.deleteTask(list.id, task.id) } },
                                    )
                                }
                            }
                        }
                    }
                }

                if (mode == SheetMode.LINK) {
                    LinkDialog(
                        initial = url,
                        onDismiss = { mode = SheetMode.MENU },
                        onSave = { link ->
                            mode = SheetMode.MENU
                            commit { repository.setTaskUrl(list.id, task.id, link) }
                        },
                        onRemove = url?.let {
                            {
                                mode = SheetMode.MENU
                                commit { repository.setTaskUrl(list.id, task.id, null) }
                            }
                        },
                    )
                }
            }
        }
    }

    /** The sheet closes on a copy, so on Android 12 and older the confirmation is a toast. */
    private fun copy(text: String, sheet: SheetState) {
        if (copyToClipboard(this, text)) toast("Copied")
        sheet.dismiss()
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private enum class SheetMode { MENU, EDIT, LINK, CONFIRM_DELETE }

    companion object {
        const val EXTRA_LIST_ID = "com.simpletodo.extra.LIST_ID"
        const val EXTRA_TASK_ID = "com.simpletodo.extra.TASK_ID"

        /**
         * One distinct [Intent.setData] per widget and task: rows in a widget list share a single
         * PendingIntent template, and only the data uri keeps one row's ⋮ from opening another's.
         */
        fun intent(context: Context, listId: String, taskId: String, appWidgetId: Int): Intent =
            Intent(context, TaskOptionsActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = "simpletodo://task/$appWidgetId/$listId/$taskId".toUri()
                putExtra(EXTRA_LIST_ID, listId)
                putExtra(EXTRA_TASK_ID, taskId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
}

object TaskOptionsTags {
    const val SHEET = "task_options_sheet"
    const val EDIT_FIELD = "task_options_edit_field"
    const val CONFIRM_DELETE = "task_options_confirm_delete"
}

@Composable
private fun SheetHeader(title: String, listName: String, accent: Int) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(TodoAccents.colorAt(accent)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = listName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
private fun EditTitle(
    initial: String,
    accent: Int,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun save() {
        if (value.isNotBlank()) onSave(value.trim())
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.take(TodoLimits.TASK_TITLE_MAX) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .testTag(TaskOptionsTags.EDIT_FIELD),
            maxLines = 4,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { save() },
                enabled = value.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TodoAccents.colorAt(accent),
                    contentColor = TodoAccents.onAccent,
                ),
            ) { Text("Save") }
        }
    }
}

/**
 * Deleting from a widget has no snackbar to undo it from, so it keeps the widget's old two-step
 * guard: the menu item only asks, and this is the explicit confirmation.
 */
@Composable
private fun ConfirmDelete(onCancel: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "Delete this task? This can't be undone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onDelete,
                modifier = Modifier.testTag(TaskOptionsTags.CONFIRM_DELETE),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Delete") }
        }
    }
}
