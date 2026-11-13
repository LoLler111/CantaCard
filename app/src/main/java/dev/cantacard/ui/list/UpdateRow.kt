package dev.cantacard.ui.list

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.cantacard.R
import dev.cantacard.update.AppUpdater

/** Riga «Nuova versione disponibile» in cima alla lista: l'aggiornamento parte solo se si tocca. */
@Composable
fun UpdateRow(state: AppUpdater.State, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val release = when (state) {
        AppUpdater.State.None -> return
        is AppUpdater.State.Available -> state.release
        is AppUpdater.State.Downloading -> state.release
        is AppUpdater.State.Installing -> state.release
    }
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.secondaryContainer)
            .clickable(enabled = state is AppUpdater.State.Available, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.secondary),
        ) {
            Icon(
                painterResource(R.drawable.ic_system_update),
                contentDescription = null,
                tint = colors.onSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            when (state) {
                is AppUpdater.State.Downloading -> {
                    Text(
                        text = stringResource(R.string.update_downloading, release.version),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSecondaryContainer,
                    )
                    val progressModifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                    val progress = state.progress
                    if (progress == null) {
                        LinearProgressIndicator(
                            modifier = progressModifier,
                            color = colors.secondary,
                            trackColor = colors.surface,
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = progressModifier,
                            color = colors.secondary,
                            trackColor = colors.surface,
                        )
                    }
                }
                is AppUpdater.State.Installing -> Text(
                    text = stringResource(R.string.update_installing),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSecondaryContainer,
                )
                else -> {
                    Text(
                        text = stringResource(R.string.update_available, release.version),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSecondaryContainer,
                    )
                    val context = LocalContext.current
                    Text(
                        text = if (release.apkSize > 0) {
                            stringResource(R.string.update_tap, Formatter.formatShortFileSize(context, release.apkSize))
                        } else {
                            stringResource(R.string.update_tap_no_size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}
