package com.simpletodo.quickadd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.simpletodo.AppGraph
import com.simpletodo.data.TodoList
import com.simpletodo.ui.theme.SimpleTodoTheme
import com.simpletodo.ui.theme.TodoAccents
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

                QuickAddSheet(
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
                                addedCount += 1
                                lastAdded = text.trim()
                            }
                        }
                    },
                    onDismiss = { finish() },
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
    lists: List<TodoList>,
    activeList: TodoList?,
    loaded: Boolean,
    addedCount: Int,
    lastAdded: String?,
    onSelectList: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .semantics { contentDescription = "Close quick add" },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(10.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .testTag(QuickAddTags.SHEET),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(
                            onClick = { if (lists.size > 1) menuOpen = true },
                            modifier = Modifier.testTag(QuickAddTags.LIST_CHIP),
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(TodoAccents.colorAt(activeList?.accent ?: 0)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = activeList?.name ?: if (loaded) "No list" else "…",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (lists.size > 1) {
                                Icon(
                                    Icons.Default.ExpandMore,
                                    contentDescription = "Change list",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            lists.forEach { list ->
                                DropdownMenuItem(
                                    text = { Text(list.name) },
                                    onClick = {
                                        menuOpen = false
                                        onSelectList(list.id)
                                    },
                                    leadingIcon = {
                                        Box(
                                            Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(TodoAccents.colorAt(list.accent)),
                                        )
                                    },
                                    trailingIcon = {
                                        if (list.id == activeList?.id) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
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

                AnimatedVisibility(visible = addedCount > 0) {
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
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
                        TextButton(onClick = onDismiss) { Text("Done") }
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
}
