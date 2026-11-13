package dev.cantacard.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.cantacard.R
import dev.cantacard.data.Sync
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Sincronizzazione tra telefoni: se è spenta spiega come funziona e fa scegliere il file (nuovo o
 * già usato da un altro telefono); se è accesa mostra file, ultimo esito e i comandi.
 */
@Composable
fun SyncDialog(
    state: Sync.State,
    onCreateFile: () -> Unit,
    onUseExisting: () -> Unit,
    onSyncNow: () -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.enabled) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.sync_title_off)) },
            text = {
                Column {
                    Text(stringResource(R.string.sync_body_off))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.sync_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = onCreateFile) { Text(stringResource(R.string.sync_create)) }
                    TextButton(onClick = onUseExisting) { Text(stringResource(R.string.sync_use_existing)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_title_on)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.fileName?.let { Text(stringResource(R.string.sync_file, it)) }
                when {
                    state.running -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sync_running))
                    }
                    state.error == Sync.Error.UNREACHABLE ->
                        Text(stringResource(R.string.sync_failed_unreachable), color = MaterialTheme.colorScheme.error)
                    state.error == Sync.Error.INVALID_FILE ->
                        Text(stringResource(R.string.sync_invalid), color = MaterialTheme.colorScheme.error)
                    state.lastSync > 0 -> Text(stringResource(R.string.sync_last, whenText(state.lastSync)))
                    else -> Text(stringResource(R.string.sync_never))
                }
                Text(
                    stringResource(R.string.sync_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDisable) {
                    Text(stringResource(R.string.sync_disable), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onSyncNow, enabled = !state.running) {
                    Text(stringResource(R.string.sync_now))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/** "adesso", "5 minuti fa", "2 ore fa", poi la data: in italiano, come il resto dell'app. */
@Composable
private fun whenText(time: Long): String {
    val minutes = ((System.currentTimeMillis() - time).coerceAtLeast(0L) / 60_000L).toInt()
    return when {
        minutes < 1 -> stringResource(R.string.sync_just_now)
        minutes < 60 -> pluralStringResource(R.plurals.sync_minutes_ago, minutes, minutes)
        minutes < 24 * 60 -> pluralStringResource(R.plurals.sync_hours_ago, minutes / 60, minutes / 60)
        else -> DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.ITALIAN)
            .format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))
    }
}
