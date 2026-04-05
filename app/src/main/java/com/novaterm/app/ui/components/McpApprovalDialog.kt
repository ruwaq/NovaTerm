package com.novaterm.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novaterm.app.ui.theme.LocalNovaTermColors
import com.novaterm.core.mcp.security.ApprovalRequest

/**
 * Dialog shown when an MCP client requests execution of a DANGEROUS tool
 * (e.g., run_command, file_write). The user must explicitly approve or deny.
 */
@Composable
fun McpApprovalDialog(
    request: ApprovalRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    val novaColors = LocalNovaTermColors.current

    AlertDialog(
        onDismissRequest = onDeny,
        title = {
            Text(
                text = "MCP: ${request.toolName}",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "An AI tool wants to execute a dangerous operation.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Risk: ${request.riskLevel.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = novaColors.destructive,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Show arguments (command, path, etc.)
                val args = request.arguments
                    .filterValues { it != null }
                    .entries
                    .joinToString("\n") { (k, v) -> "$k: $v" }
                if (args.isNotBlank()) {
                    Text(
                        text = args,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApprove) {
                Text("Allow", color = novaColors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text("Deny", color = novaColors.destructive)
            }
        },
    )
}
