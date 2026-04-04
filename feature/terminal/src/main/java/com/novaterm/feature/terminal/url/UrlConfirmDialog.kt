package com.novaterm.feature.terminal.url

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.novaterm.feature.terminal.R
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Confirmation dialog for detected entities: URLs, IP addresses, file paths.
 * Actions adapt based on entity type:
 * - URL: Open in browser + Copy
 * - IP_ADDRESS: Open as HTTP + Copy
 * - FILE_PATH: Copy to clipboard
 *
 * Security: prevents blind navigation to potentially malicious URLs
 * that could be injected into terminal output.
 */
@Composable
fun EntityConfirmDialog(
    entity: UrlDetector.Entity,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val value = entity.value

    val (title, displayLabel) = when (entity.type) {
        UrlDetector.Entity.Type.URL -> {
            val domain = try { Uri.parse(value).host ?: value } catch (_: Exception) { value }
            Pair(stringResource(R.string.url_dialog_title), domain)
        }
        UrlDetector.Entity.Type.IP_ADDRESS -> {
            Pair(stringResource(R.string.entity_dialog_title_ip), value)
        }
        UrlDetector.Entity.Type.FILE_PATH -> {
            Pair(stringResource(R.string.entity_dialog_title_path), value)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = displayLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (displayLabel != value) {
                    Text(
                        text = value,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            when (entity.type) {
                UrlDetector.Entity.Type.URL -> {
                    TextButton(onClick = {
                        openUrl(context, value)
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.url_dialog_open))
                    }
                }
                UrlDetector.Entity.Type.IP_ADDRESS -> {
                    TextButton(onClick = {
                        // Open as HTTP — most common use case for tapping an IP
                        val httpUrl = if (value.startsWith("http")) value else "http://$value"
                        openUrl(context, httpUrl)
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.url_dialog_open))
                    }
                }
                UrlDetector.Entity.Type.FILE_PATH -> {
                    // File paths: primary action is copy (can't open arbitrary files from terminal)
                    TextButton(onClick = {
                        copyToClipboard(context, value)
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.url_dialog_copy))
                    }
                }
            }
        },
        dismissButton = {
            Row {
                // Copy is always available as secondary action (except FILE_PATH where it's primary)
                if (entity.type != UrlDetector.Entity.Type.FILE_PATH) {
                    TextButton(onClick = {
                        copyToClipboard(context, value)
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.url_dialog_copy))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.url_dialog_cancel))
                }
            }
        },
    )
}

/**
 * Legacy dialog — delegates to [EntityConfirmDialog] with URL type.
 */
@Composable
fun UrlConfirmDialog(
    url: String,
    onDismiss: () -> Unit,
) {
    EntityConfirmDialog(
        entity = UrlDetector.Entity(UrlDetector.Entity.Type.URL, url),
        onDismiss = onDismiss,
    )
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.url_cannot_open), Toast.LENGTH_SHORT).show()
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("NovaTerm", text))
    Toast.makeText(context, context.getString(R.string.url_copied), Toast.LENGTH_SHORT).show()
}
