package com.simpletodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.simpletodo.data.ACCENT_COUNT
import com.simpletodo.data.TodoLimits
import com.simpletodo.ui.theme.TodoAccents
import com.simpletodo.widget.WidgetKind

object DialogTags {
    const val NAME_FIELD = "dialog_name_field"
    const val CONFIRM = "dialog_confirm"
    const val CANCEL = "dialog_cancel"

    fun accentSwatch(index: Int) = "dialog_accent_$index"
}

/**
 * Names a list and picks its colour in one step, which is how the two are thought about: a list
 * is "the green one" as much as it is "Shopping".
 */
@Composable
fun NameListDialog(
    title: String,
    confirmLabel: String,
    initialValue: String,
    initialAccent: Int,
    onDismiss: () -> Unit,
    onConfirm: (name: String, accent: Int) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    var accent by remember { mutableIntStateOf(initialAccent.mod(ACCENT_COUNT)) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { CatMascot(pose = CatPose.Walk, size = 92.dp) },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(TodoLimits.LIST_NAME_MAX) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag(DialogTags.NAME_FIELD),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    placeholder = { Text("e.g. Work") },
                    supportingText = { Text("${value.length}/${TodoLimits.LIST_NAME_MAX}") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (value.isNotBlank()) onConfirm(value, accent) },
                    ),
                )
                Spacer(Modifier.height(4.dp))
                AccentRow(current = accent, onPick = { accent = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value, accent) },
                enabled = value.isNotBlank(),
                modifier = Modifier.testTag(DialogTags.CONFIRM),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(DialogTags.CANCEL)) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(DialogTags.CONFIRM),
            ) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(DialogTags.CANCEL)) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun AccentPickerDialog(
    current: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("List colour") },
        text = { AccentRow(current = current, onPick = onPick) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun AccentRow(current: Int, onPick: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TodoAccents.colors.forEachIndexed { index, color ->
            val chosen = index == current
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (chosen) 3.dp else 0.dp,
                        color = if (chosen) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable(role = Role.RadioButton) { onPick(index) }
                    .testTag(DialogTags.accentSwatch(index))
                    .semantics {
                        contentDescription = TodoAccents.nameAt(index)
                        selected = chosen
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (chosen) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = TodoAccents.onAccent,
                    )
                }
            }
        }
    }
}

private data class WidgetSizeOption(
    val kind: WidgetKind,
    val label: String,
    val cells: String,
    val blurb: String,
)

private val widgetSizeOptions = listOf(
    WidgetSizeOption(WidgetKind.SMALL, "Small", "2 × 2", "What's next, at a glance."),
    WidgetSizeOption(WidgetKind.MEDIUM, "Medium", "4 × 2", "A few tasks, tick and delete."),
    WidgetSizeOption(WidgetKind.LARGE, "Large", "4 × 4", "Scrollable list with controls."),
    WidgetSizeOption(WidgetKind.XLARGE, "Extra large", "5 × 5", "Manage the whole list."),
)

@Composable
fun AddWidgetDialog(
    listName: String,
    supported: Boolean,
    onDismiss: () -> Unit,
    onPick: (WidgetKind) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add “$listName” to home screen") },
        text = {
            if (!supported) {
                Text(
                    "Your launcher doesn't support adding widgets from inside apps. " +
                        "Long-press your home screen, choose Widgets, then pick Todo.",
                )
            } else {
                Column {
                    Text(
                        "Pick a size. You can resize it on the home screen afterwards.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    widgetSizeOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { onPick(option.kind) }
                                .padding(14.dp)
                                .testTag("widget_size_${option.kind.key}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(option.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    option.blurb,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                option.cells,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
