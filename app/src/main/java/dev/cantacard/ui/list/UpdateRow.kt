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
import dev.cantacard.update.AppRelease

/** Riga «Nuova versione disponibile» in cima alla lista: il download parte solo se si tocca. */
@Composable
fun UpdateRow(release: AppRelease?, onClick: (AppRelease) -> Unit, modifier: Modifier = Modifier) {
    if (release == null) return
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.secondaryContainer)
            .clickable { onClick(release) }
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
