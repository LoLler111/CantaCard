package dev.cantacard.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.cantacard.R
import dev.cantacard.data.RestoreMode

/**
 * Conferma del ripristino. Se nel telefono ci sono già tessere si sceglie ogni volta tra
 * aggiungere (senza doppioni) e sostituire tutto (scelta dell'utente).
 */
@Composable
fun RestoreDialog(
    backupCount: Int,
    currentCount: Int,
    isNewer: Boolean,
    onChoose: (RestoreMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_title)) },
        text = {
            Column {
                Text(
                    if (backupCount == 0) stringResource(R.string.restore_empty)
                    else pluralStringResource(R.plurals.restore_count, backupCount, backupCount),
                )
                if (isNewer) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.restore_newer), color = MaterialTheme.colorScheme.error)
                }
                if (backupCount > 0 && currentCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(pluralStringResource(R.plurals.restore_current, currentCount, currentCount))
                }
            }
        },
        confirmButton = {
            when {
                backupCount == 0 -> Unit
                currentCount == 0 -> TextButton(onClick = { onChoose(RestoreMode.ADD) }) {
                    Text(stringResource(R.string.restore_confirm))
                }
                else -> Row {
                    TextButton(onClick = { onChoose(RestoreMode.REPLACE) }) {
                        Text(stringResource(R.string.restore_replace), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { onChoose(RestoreMode.ADD) }) {
                        Text(stringResource(R.string.restore_add))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(if (backupCount == 0) R.string.action_close else R.string.action_cancel))
            }
        },
    )
}
