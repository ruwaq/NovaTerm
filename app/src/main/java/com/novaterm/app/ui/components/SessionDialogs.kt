package com.novaterm.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.novaterm.app.R
import com.novaterm.app.ui.theme.LocalNovaTermColors

/** Close session confirmation dialog. */
@Composable
fun CloseSessionDialog(
    sessionIndex: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val novaColors = LocalNovaTermColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_close_session_title)) },
        text = { Text(stringResource(R.string.dialog_close_session_message, sessionIndex + 1)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.dialog_close), color = novaColors.destructive)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

/** Session creation failure dialog with retry. */
@Composable
fun SessionCreationFailedDialog(
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_creation_failed)) },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

/** Tab rename dialog. */
@Composable
fun RenameSessionDialog(
    currentName: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_rename_session_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.dialog_rename_placeholder)) },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) onRename(text.trim())
                onDismiss()
            }) {
                Text(stringResource(R.string.dialog_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}
