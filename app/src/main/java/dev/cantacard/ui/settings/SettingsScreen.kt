package dev.cantacard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.cantacard.R
import dev.cantacard.data.SortOrder
import dev.cantacard.data.Sync
import dev.cantacard.ui.edit.FormSection
import dev.cantacard.ui.headerColor
import dev.cantacard.ui.list.sortLabel
import dev.cantacard.ui.list.whenText

/** Cosa mostra la pagina delle impostazioni (tutto già letto dalla schermata). */
data class SettingsState(
    val favoritesOrder: SortOrder,
    val sortOrder: SortOrder,
    val favoriteCount: Int,
    val hasCards: Boolean,
    val sync: Sync.State,
    val versionName: String,
)

/**
 * Impostazioni: ordine della lista e delle preferite, backup e sincronizzazione, informazioni.
 * Riquadri arrotondati come nel modulo della tessera.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onBack: () -> Unit,
    onSort: () -> Unit,
    onReorder: () -> Unit,
    onSync: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onImport: () -> Unit,
    onReport: () -> Unit,
    onSource: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SettingsHeader(stringResource(R.string.settings_menu), onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            FormSection(icon = R.drawable.ic_sort, title = R.string.settings_section_list) {
                Column {
                    SettingRow(
                        icon = R.drawable.ic_star_outline,
                        title = stringResource(R.string.settings_sort_favorites),
                        subtitle = stringResource(sortLabel(state.favoritesOrder)),
                        onClick = onSort,
                        enabled = state.hasCards,
                    )
                    SettingRow(
                        icon = R.drawable.ic_card_back,
                        title = stringResource(R.string.settings_sort_others),
                        subtitle = stringResource(sortLabel(state.sortOrder)),
                        onClick = onSort,
                        enabled = state.hasCards,
                    )
                    // Il riordino a mano serve solo ai gruppi con l'ordine personalizzato.
                    val favoritesManual = state.favoritesOrder == SortOrder.MANUAL
                    val othersManual = state.sortOrder == SortOrder.MANUAL
                    if (favoritesManual || othersManual) {
                        val noFavorites = !othersManual && state.favoriteCount == 0
                        SettingRow(
                            icon = R.drawable.ic_drag,
                            title = stringResource(if (othersManual) R.string.reorder_menu else R.string.reorder_favorites_menu),
                            subtitle = if (noFavorites) stringResource(R.string.settings_no_favorites) else null,
                            onClick = onReorder,
                            enabled = state.hasCards && !noFavorites,
                        )
                    }
                }
            }

            FormSection(icon = R.drawable.ic_backup, title = R.string.settings_section_backup) {
                Column {
                    SettingRow(
                        icon = R.drawable.ic_refresh,
                        title = stringResource(R.string.sync_menu),
                        subtitle = syncSubtitle(state.sync),
                        onClick = onSync,
                    )
                    SettingRow(
                        icon = R.drawable.ic_upload,
                        title = stringResource(R.string.backup_export),
                        subtitle = stringResource(R.string.settings_export_body),
                        onClick = onExport,
                        enabled = state.hasCards,
                    )
                    SettingRow(
                        icon = R.drawable.ic_download,
                        title = stringResource(R.string.backup_restore),
                        subtitle = stringResource(R.string.settings_restore_body),
                        onClick = onRestore,
                    )
                    SettingRow(icon = R.drawable.ic_import, title = stringResource(R.string.import_menu), onClick = onImport)
                }
            }

            FormSection(icon = R.drawable.ic_info, title = R.string.settings_section_info) {
                Column {
                    SettingRow(
                        icon = R.drawable.ic_bug,
                        title = stringResource(R.string.report_menu),
                        subtitle = stringResource(R.string.settings_report_body),
                        onClick = onReport,
                    )
                    SettingRow(
                        icon = R.drawable.ic_code,
                        title = stringResource(R.string.settings_version, state.versionName),
                        subtitle = stringResource(R.string.settings_source),
                        onClick = onSource,
                    )
                }
            }
        }
    }
}

@Composable
private fun syncSubtitle(sync: Sync.State): String = when {
    !sync.enabled -> stringResource(R.string.settings_sync_off)
    sync.running -> stringResource(R.string.sync_running)
    sync.error != null -> stringResource(R.string.sync_failed)
    else -> stringResource(
        R.string.settings_sync_on,
        if (sync.lastSync > 0) stringResource(R.string.sync_last, whenText(sync.lastSync))
        else stringResource(R.string.sync_never),
    )
}

/** Intestazione verde con la freccia indietro, come la scelta del negozio. */
@Composable
fun SettingsHeader(title: String, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(headerColor),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 20.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back), tint = Color.White)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun SettingRow(
    icon: Int,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.45f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        RowTexts(title, subtitle, enabled, Modifier.weight(1f))
    }
}

@Composable
private fun RowTexts(title: String, subtitle: String?, enabled: Boolean, modifier: Modifier) {
    val alpha = if (enabled) 1f else 0.45f
    Column(modifier) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
    }
}
