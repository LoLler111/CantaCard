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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.cantacard.ui.edit.formatDate
import java.io.File
import java.time.LocalDate

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
    onArchive: (Boolean) -> Unit,
    /** Pagina visibile (con lo scorrimento tra tessere ne sono composte due alla volta). */
    isCurrent: Boolean = true,
    /** Letto solo quando si chiede la conferma: la schermata della tessera resta leggera. */
    isSynced: () -> Boolean = { false },
    /** "Aggiungi alla schermata Home", `null` se il launcher non lo permette. */
    onPin: (() -> Unit)? = null,
) {
    val format = card.format
    val matrix = remember(card.number, format) { format?.let { BarcodeEncoder.encode(card.number, it) } }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var rotated by rememberSaveable(card.id) { mutableStateOf(false) }
    var showBack by rememberSaveable(card.id) { mutableStateOf(false) }
    val color = Color(card.color)
    val onColor = contentColorOn(color)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val images = LocalContext.current.app.images

    // Icone di sistema leggibili sopra il colore del negozio.
    val view = LocalView.current
    SideEffect {
        if (!isCurrent) return@SideEffect
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
                onArchive = onArchive,
                onPin = onPin,
            )
            if (!landscape) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 28.dp),
                ) {
                    val image = rememberCardImage(card.image?.let(images::file))
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
                            text = (listOf(format?.label ?: card.formatName) + card.labels).joinToString(" · "),
                            color = onColor.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                        if (matrix != null && rotated) {
                            RotatedBarcode(matrix, Modifier.weight(1f).fillMaxWidth())
                        } else if (matrix != null) {
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
                        card.expiryDate?.let { ExpiryLine(it) }
                    }
                    val canRotate = matrix?.isLinear == true
                    if (canRotate || card.backImage != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            if (canRotate) {
                                CodeAction(
                                    icon = R.drawable.ic_rotate,
                                    text = stringResource(if (rotated) R.string.code_straighten else R.string.code_rotate),
                                    selected = rotated,
                                    onClick = { rotated = !rotated },
                                )
                            }
                            if (card.backImage != null) {
                                CodeAction(
                                    icon = R.drawable.ic_card_back,
                                    text = stringResource(R.string.show_back),
                                    selected = false,
                                    onClick = { showBack = true },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBack) {
        card.backImage?.let { name ->
            BackPhotoDialog(file = images.file(name), onDismiss = { showBack = false })
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = {
                val synced = remember { isSynced() }
                Text(stringResource(if (synced) R.string.delete_message_synced else R.string.delete_message, card.name))
            },
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
    onArchive: (Boolean) -> Unit,
    onPin: (() -> Unit)?,
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
        Box {
            var menu by remember { mutableStateOf(false) }
            IconButton(onClick = { menu = true }) {
                Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.more_options), tint = tint)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (onPin != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pin_home)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_add_home), contentDescription = null) },
                        onClick = {
                            menu = false
                            onPin()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(if (card.archived) R.string.unarchive else R.string.archive)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_archive), contentDescription = null) },
                    onClick = {
                        menu = false
                        onArchive(!card.archived)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/**
 * Codice a barre girato di 90°: le barre diventano lunghe quanto l'altezza disponibile, più
 * facili da leggere per i lettori laser vecchi. Il telefono resta in verticale.
 */
@Composable
private fun RotatedBarcode(matrix: dev.cantacard.barcode.BarcodeMatrix, modifier: Modifier = Modifier) {
    BoxWithConstraints(contentAlignment = Alignment.Center, modifier = modifier) {
        val ratio = BarcodeLayout.aspectRatio(matrix)
        // Larghezza del codice prima della rotazione: al massimo l'altezza disponibile.
        val length = minOf(maxHeight, maxWidth * ratio)
        val thickness = length / ratio
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(width = thickness, height = length)) {
            BarcodeView(
                matrix = matrix,
                modifier = Modifier
                    .requiredSize(width = length, height = thickness)
                    .graphicsLayer { rotationZ = 90f },
            )
        }
    }
}

@Composable
private fun ExpiryLine(date: LocalDate) {
    val expired = date.isBefore(LocalDate.now())
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
        Icon(
            painterResource(R.drawable.ic_event),
            contentDescription = null,
            tint = if (expired) Warning else Muted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(if (expired) R.string.expired_on else R.string.expires_on, formatDate(date)),
            color = if (expired) Warning else Muted,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Pulsante a pillola nella parte bianca (codice ruotato, retro). */
@Composable
private fun CodeAction(icon: Int, text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) Ink else Color(0xFFF0F2F2),
        contentColor = if (selected) Color.White else Ink,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Foto del retro a tutto schermo, su sfondo bianco; un tocco la chiude. */
@Composable
private fun BackPhotoDialog(file: File, onDismiss: () -> Unit) {
    val image = rememberCardImage(file)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .clickable(onClick = onDismiss)
                .padding(16.dp),
        ) {
            if (image != null) {
                Image(
                    bitmap = image.bitmap,
                    contentDescription = stringResource(R.string.show_back),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(image.bitmap.width.toFloat() / image.bitmap.height.coerceAtLeast(1))
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
        }
    }
}
