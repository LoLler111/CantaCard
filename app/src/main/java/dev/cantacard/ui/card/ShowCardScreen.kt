package dev.cantacard.ui.card

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.cantacard.R
import dev.cantacard.barcode.BarcodeEncoder
import dev.cantacard.barcode.BarcodeLayout
import dev.cantacard.barcode.BarcodeView
import dev.cantacard.data.Card
import dev.cantacard.ui.components.CARD_RATIO
import dev.cantacard.ui.components.StoreMark
import dev.cantacard.app
import dev.cantacard.image.rememberCardImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import dev.cantacard.ui.components.cardSurface
import dev.cantacard.ui.contentColorOn

private val Ink = Color(0xFF111111)
private val Muted = Color(0xFF5F6B69)
private val Warning = Color(0xFFB3261E)

/**
 * Tessera da mostrare in cassa: in alto il colore del negozio con nome e iniziale, sotto un
 * "biglietto" bianco con il codice il più grande possibile (quiet zone sempre bianca) e il
 * numero leggibile per l'inserimento manuale.
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
    val color = Color(card.color)
    val onColor = contentColorOn(color)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Icone di sistema leggibili sopra il colore del negozio.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = color.luminance() > 0.55f
            isAppearanceLightNavigationBars = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .cardSurface(color, corner = 0.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                card = card,
                tint = onColor,
                showTitle = landscape,
                onBack = onBack,
                onToggleFavorite = onToggleFavorite,
                onEdit = onEdit,
                onDelete = { confirmDelete = true },
            )
            if (!landscape) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 28.dp),
                ) {
                    val image = rememberCardImage(card.image?.let(LocalContext.current.app.images::file))
                    if (image != null && image.isPhoto) {
                        Image(
                            bitmap = image.bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .height(60.dp)
                                .aspectRatio(CARD_RATIO)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    } else {
                        StoreMark(card.initial, color, image, 60.dp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = card.name,
                            color = onColor,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = format?.label ?: card.formatName,
                            color = onColor.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            Surface(
                color = Color.White,
                contentColor = Ink,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(top = 20.dp, bottom = 16.dp),
                ) {
                    BrightnessHint()
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
                        Spacer(Modifier.height(12.dp))
                        SelectionContainer {
                            Text(
                                text = card.number,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = if (matrix == null) 34.sp else 26.sp,
                                letterSpacing = 2.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        card.note?.let { note ->
                            Text(
                                text = note,
                                color = Muted,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
                            )
                        }
                    }
                }
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

/** Promemoria discreto: la luminosità è già al massimo, basta avvicinare il telefono. */
@Composable
private fun BrightnessHint() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFFFF4D6))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_sun),
            contentDescription = null,
            tint = Color(0xFFB7791F),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.show_hint),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF7A5212),
        )
    }
}

@Composable
private fun TopBar(
    card: Card,
    tint: Color,
    showTitle: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back), tint = tint)
        }
        if (showTitle) {
            Text(
                text = card.name,
                color = tint,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                painter = painterResource(if (card.favorite) R.drawable.ic_star else R.drawable.ic_star_outline),
                contentDescription = stringResource(
                    if (card.favorite) R.string.favorite_remove else R.string.favorite_add,
                ),
                tint = tint,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.action_edit), tint = tint)
        }
        IconButton(onClick = onDelete) {
            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete), tint = tint)
        }
    }
}
