package com.simpletodo.quickadd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.simpletodo.AppGraph
import com.simpletodo.data.TodoList
import com.simpletodo.ui.theme.SimpleTodoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The "+" on every widget lands here: a translucent sheet that appears over the home screen with
 * the keyboard already up. Widgets cannot host a text field, so this is as close to typing
 * directly into the widget as the platform allows — and it never shows the full app.
 */
class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val requestedListId = intent?.getStringExtra(EXTRA_LIST_ID)
        val repository = AppGraph.repository(this)

        setContent {
            SimpleTodoTheme {
                val snapshot by repository.snapshot.collectAsStateWithLifecycle(initialValue = null)
                val lists = snapshot?.lists.orEmpty()

                var activeListId by remember { mutableStateOf(requestedListId) }
                var addedCount by remember { mutableStateOf(0) }
                var lastAdded by remember { mutableStateOf<String?>(null) }

                // The requested list may have been deleted between the widget rendering and the
                // tap landing here; fall back rather than showing a dead sheet.
                LaunchedEffect(lists) {
                    if (lists.isNotEmpty() && lists.none { it.id == activeListId }) {
                        activeListId = lists.first().id
                    }
                }

                val activeList = lists.firstOrNull { it.id == activeListId }
                val sheet = rememberSheetState()
                val view = LocalView.current

                QuickAddSheet(
                    sheet = sheet,
                    onDismissed = { finishWithoutTransition() },
                    lists = lists,
                    activeList = activeList,
                    loaded = snapshot != null,
                    addedCount = addedCount,
                    lastAdded = lastAdded,
                    onSelectList = { activeListId = it },
                    onSubmit = { text ->
                        val listId = activeListId ?: return@QuickAddSheet
                        lifecycleScope.launch {
                            val id = repository.addTask(listId, text)
                            if (id != null) {
                                view.confirmHaptic()
                                addedCount += 1
                                lastAdded = text.trim()
                                // Let the confirmation land, then let the sheet slide away.
                                delay(650)
                                sheet.dismiss()
                            }
                        }
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_LIST_ID = "com.simpletodo.extra.LIST_ID"
        const val EXTRA_APP_WIDGET_ID = "com.simpletodo.extra.APP_WIDGET_ID"

        /**
         * The [Intent.setData] uri keeps each widget's "+" a distinct PendingIntent; without it
         * two widgets would share one and the second would silently add to the first one's list.
         */
        fun intent(context: Context, listId: String, appWidgetId: Int): Intent =
            Intent(context, QuickAddActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = "simpletodo://quickadd/$appWidgetId/$listId".toUri()
                putExtra(EXTRA_LIST_ID, listId)
                putExtra(EXTRA_APP_WIDGET_ID, appWidgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
}

object QuickAddTags {
    const val FIELD = "quick_add_field"
    const val SUBMIT = "quick_add_submit"
    const val SHEET = "quick_add_sheet"
    const val LIST_CHIP = "quick_add_list_chip"
}

@Composable
private fun QuickAddSheet(
    sheet: SheetState,
    onDismissed: () -> Unit,
    lists: List<TodoList>,
    activeList: TodoList?,
    loaded: Boolean,
    addedCount: Int,
    lastAdded: String?,
    onSelectList: (String) -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(activeList?.id) {
        if (activeList != null) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    fun submit() {
        val value = text.trim()
        if (value.isEmpty()) return
        onSubmit(value)
        text = ""
    }

    SheetHost(
        state = sheet,
        onDismissed = onDismissed,
        modifier = Modifier.testTag(QuickAddTags.SHEET),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ListPicker(
                    lists = lists,
                    activeList = activeList,
                    loaded = loaded,
                    onSelectList = onSelectList,
                    modifier = Modifier.testTag(QuickAddTags.LIST_CHIP),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = sheet::dismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(300) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag(QuickAddTags.FIELD),
                    placeholder = { Text("Add a task…") },
                    singleLine = true,
                    enabled = activeList != null,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { submit() },
                    enabled = text.isNotBlank() && activeList != null,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag(QuickAddTags.SUBMIT),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Add task")
                }
            }

            AnimatedVisibility(
                visible = addedCount > 0,
                enter = fadeIn(tween(180)) + expandVertically(spring(dampingRatio = 0.85f)),
            ) {
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    PoppingCheck()
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (addedCount == 1) {
                            "Added “${lastAdded.orEmpty()}”"
                        } else {
                            "Added $addedCount tasks · keep typing"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = sheet::dismiss) { Text("Done") }
                }
            }

            if (loaded && lists.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "You don't have any lists yet. Open the app to create one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
