package com.novaterm.app.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novaterm.app.R
import java.io.File

/**
 * Status line showing CWD (shortened) + git branch + prompt navigation.
 *
 * The ▲/▼ buttons jump between shell prompts using OSC 133 semantic zones.
 * They only appear when [onPromptUp]/[onPromptDown] are provided.
 */
@Composable
fun StatusLine(
    cwd: String,
    onPromptUp: (() -> Unit)? = null,
    onPromptDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shortPath = remember(cwd) { shortenPath(cwd) }
    val gitBranch = remember(cwd) { readGitBranch(cwd) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Prompt navigation: jump up
        if (onPromptUp != null) {
            Text(
                text = "▲",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onPromptUp)
                    .padding(horizontal = 6.dp),
            )
        }

        // Prompt navigation: jump down
        if (onPromptDown != null) {
            Text(
                text = "▼",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onPromptDown)
                    .padding(end = 6.dp),
            )
        }

        // CWD
        Text(
            text = shortPath,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Git branch
        if (gitBranch != null) {
            Text(
                text = " $gitBranch",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
            )
        }
    }
}

/**
 * Shorten a path for the status line. Replaces home with ~,
 * truncates intermediate segments to 2 chars (Powerlevel10k style).
 */
internal fun shortenPath(path: String): String {
    if (path.isBlank()) return "~"
    var short = path
        .replace(Regex("/data/data/[^/]+/home"), "~")
        .replace(Regex("/data/data/[^/]+/files/home"), "~")
        .replace("/storage/emulated/0", "/sdcard")

    val parts = short.split("/").filter { it.isNotEmpty() }
    if (parts.size <= 2) return short

    return buildString {
        append(parts.first())
        for (i in 1 until parts.size - 1) {
            append("/")
            append(parts[i].take(2))
        }
        append("/")
        append(parts.last())
    }
}

/** Read the current git branch from .git/HEAD. Null if not in a git repo. */
internal fun readGitBranch(cwd: String): String? {
    if (cwd.isBlank()) return null
    try {
        var dir = File(cwd)
        repeat(10) {
            val gitDir = File(dir, ".git")
            if (gitDir.isDirectory) {
                val head = File(gitDir, "HEAD")
                if (head.exists()) {
                    val content = head.readText().trim()
                    return if (content.startsWith("ref: refs/heads/")) {
                        content.removePrefix("ref: refs/heads/")
                    } else {
                        content.take(7)
                    }
                }
            }
            dir = dir.parentFile ?: return null
        }
    } catch (e: Exception) {
        Log.w("NovaTerm", "Git branch detection failed for $cwd", e)
    }
    return null
}
