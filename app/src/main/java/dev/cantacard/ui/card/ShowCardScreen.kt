package dev.cantacard.ui.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cantacard.R
import dev.cantacard.barcode.BarcodeEncoder
import dev.cantacard.barcode.BarcodeLayout
import dev.cantacard.barcode.BarcodeView
import dev.cantacard.data.Card

private val Ink = Color.Black
private val Muted = Color(0xFF5F6368)
private val Warning = Color(0xFFB3261E)

/**
 * Tessera da mostrare in cassa: sempre nero su bianco (anche col tema scuro), codice il più
 * grande possibile e numero leggibile sotto, per l'inserimento manuale.
 */
@Composable
fun ShowCardScreen(
    card: Card,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val format = card.format
    val matrix = remember(card.number, format) { format?.let { BarcodeEncoder.encode(card.number, it) } }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Surface(color = Color.White, contentColor = Ink, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            TopBar(
                card = card,
                onBack = onBack,
                onToggleFavorite = onToggleFavorite,
                onEdit = onEdit,
                onDelete = { confirmDelete = true },
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                if (matrix != null) {
                    BarcodeView(
                        matrix = matrix,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .aspectRatio(BarcodeLayout.aspectRatio(matrix)),
                    )
                } else {
                    Text(
                        text = if (format == null) {
                            stringResource(R.string.code_unknown_format, card.formatName)
                        } else {
                            stringResource(R.string.code_invalid, format.label)
                        },
                        color = Warning,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        text = card.number,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = if (matrix == null) 34.sp else 26.sp,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                Text(
                    text = format?.label ?: card.formatName,
                    color = Muted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message, card.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun TopBar(
    card: Card,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
        }
        Text(
            text = card.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        )
        IconButton(onClick = onToggleFavorite) {
            Icon(
                painter = painterResource(if (card.favorite) R.drawable.ic_star else R.drawable.ic_star_outline),
                contentDescription = stringResource(
                    if (card.favorite) R.string.favorite_remove else R.string.favorite_add,
                ),
            )
        }
        IconButton(onClick = onEdit) {
            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.action_edit))
        }
        IconButton(onClick = onDelete) {
            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete))
        }
    }
}
