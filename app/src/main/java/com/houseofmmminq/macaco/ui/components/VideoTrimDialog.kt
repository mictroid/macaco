package com.houseofmmminq.macaco.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.util.VideoTranscoder

/**
 * Shown when a gallery-picked video is > 15 seconds. Lets the user choose which 15-second window to
 * keep by dragging a slider over the clip's duration. Shared by NewEditEntryScreen (adding video to a
 * draft) and EntryDetailScreen (adding video to an existing entry).
 */
@Composable
internal fun VideoTrimDialog(
    sourceUri: Uri,
    durationMs: Long,
    onTrimConfirmed: (trimStartMs: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val maxStart = (durationMs - VideoTranscoder.MAX_DURATION_MS).coerceAtLeast(0L)
    var trimStartMs by remember { mutableStateOf(0L) }

    // First-frame thumbnail preview.
    val thumbnail = remember(sourceUri) { VideoTranscoder.getFirstFrame(context, sourceUri) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.video_trim_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (thumbnail != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Text(
                    text = stringResource(
                        R.string.video_trim_window,
                        formatMmSs(trimStartMs),
                        formatMmSs(trimStartMs + VideoTranscoder.MAX_DURATION_MS)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = trimStartMs.toFloat() / maxStart.coerceAtLeast(1L),
                    onValueChange = { trimStartMs = (it * maxStart).toLong() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onTrimConfirmed(trimStartMs) }) {
                Text(stringResource(R.string.video_trim_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

private fun formatMmSs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
