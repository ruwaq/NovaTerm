package com.novaterm.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.novaterm.core.session.persistence.SessionGroup

/**
 * Dialog for creating or editing a session group.
 * Supports naming, picking a color, and deleting (for existing groups).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupEditDialog(
    group: SessionGroup? = null,
    onConfirm: (name: String, color: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val isEditing = group != null
    var name by remember(group?.name) { mutableStateOf(group?.name ?: "") }
    var selectedColor by remember(group?.color) { mutableStateOf(group?.color ?: SessionGroup.DEFAULT_COLOR) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Group" else "New Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Group name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.padding(bottom = 4.dp),
                )

                // Color picker
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SessionGroup.GROUP_COLORS.forEach { (hex, _) ->
                        val color = parseDialogColor(hex)
                        val isSelected = hex == selectedColor
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            width = 3.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape,
                                        )
                                    } else {
                                        Modifier.border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline,
                                            shape = CircleShape,
                                        )
                                    }
                                )
                                .clickable { selectedColor = hex },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), selectedColor) },
                enabled = name.isNotBlank(),
            ) {
                Text(if (isEditing) "Save" else "Create")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isEditing && onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete group",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

/**
 * Bottom sheet for moving a session to a different group.
 * Shows all available groups with their colors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToGroupSheet(
    groups: List<SessionGroup>,
    currentGroupId: String?,
    onMove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Move to Group",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            groups.forEach { group ->
                val isCurrent = group.id == currentGroupId
                val groupColor = parseDialogColor(group.color)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (isCurrent) Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            else Modifier.clickable { onMove(group.id) }
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Color dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(groupColor, CircleShape),
                    )

                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isCurrent) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurface,
                    )

                    if (isCurrent) {
                        Text(
                            text = "(current)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Create new group option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onMove("__new__") }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New group",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "New Group",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun parseDialogColor(hex: String): Color {
    return try {
        val cleaned = hex.removePrefix("#")
        val colorInt = cleaned.toLong(16)
        Color(0xFF000000 or colorInt)
    } catch (_: Exception) {
        Color(0xFFE85D04)
    }
}