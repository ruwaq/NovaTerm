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
 * Confirmation dialog shown before opening a URL detected in terminal output.
 * Shows the domain prominently and the full URL for verification.
 * Offers: Open in browser, Copy to clipboard, Cancel.
 *
 * Security: prevents blind navigation to potentially malicious URLs
 * that could be injected into terminal output.
 */
@Composable
fun UrlConfirmDialog(
    url: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val domain = try {
        Uri.parse(url).host ?: url
    } catch (_: Exception) {
        url
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open link?") },
        text = {
            Column {
                Text(
                    text = domain,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = url,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                openUrl(context, url)
                onDismiss()
            }) {
                Text("Open")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    copyToClipboard(context, url)
                    onDismiss()
                }) {
                    Text("Copy")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
    }
}

private fun copyToClipboard(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
