package dev.cantacard.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cantacard.R
import dev.cantacard.image.CardImage
import dev.cantacard.ui.contentColorOn

/** Rapporto larghezza/altezza di una carta di credito (ISO/IEC 7810 ID-1). */
const val CARD_RATIO = 1.586f

/** Sfondo "carta": colore pieno del negozio, piatto come l'intestazione (niente sfumature né riflessi). */
fun Modifier.cardSurface(color: Color, corner: Dp = 18.dp): Modifier = this
    .clip(RoundedCornerShape(corner))
    .background(color)

/** Tondo con l'iniziale, come un piccolo logo sulla carta. */
@Composable
fun InitialBadge(initial: String, background: Color, size: Dp, modifier: Modifier = Modifier) {
    val content = contentColorOn(background)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(content.copy(alpha = 0.18f)),
    ) {
        Text(
            text = initial,
            color = content,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.46f).sp,
        )
    }
}

/** Icona del negozio in un tondo bianco, al posto dell'iniziale. */
@Composable
fun IconBadge(image: CardImage, size: Dp, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White),
    ) {
        Image(
            bitmap = image.bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.Medium,
            modifier = Modifier
                .padding(size * 0.14f)
                .fillMaxSize()
                .clip(RoundedCornerShape(size * 0.12f)),
        )
    }
}

/** Icona se l'immagine è un'icona, altrimenti l'iniziale. */
@Composable
fun StoreMark(initial: String, color: Color, image: CardImage?, size: Dp, modifier: Modifier = Modifier) {
    if (image != null && !image.isPhoto) IconBadge(image, size, modifier) else InitialBadge(initial, color, size, modifier)
}

/**
 * Disegno di una tessera (senza codice): usato nella lista e come anteprima nel modulo.
 * Con la foto della tessera la foto la riempie tutta; con l'icona del negozio l'icona prende il
 * posto dell'iniziale. Le dimensioni seguono il modificatore; il contenuto scala con [compact].
 */
@Composable
fun CardArt(
    name: String,
    initial: String,
    color: Color,
    favorite: Boolean,
    modifier: Modifier = Modifier,
    image: CardImage? = null,
    compact: Boolean = true,
    corner: Dp = 18.dp,
    /** Dimensione dell'iniziale o dell'icona, per le miniature; `null` = secondo [compact]. */
    markSize: Dp? = null,
    /** Avviso in alto (es. scadenza), `null` se non serve. */
    badge: CardBadge? = null,
) {
    val content = contentColorOn(color)
    Box(modifier.cardSurface(color, corner)) {
        if (image != null && image.isPhoto) {
            Image(
                bitmap = image.bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier.fillMaxSize(),
            )
            if (badge != null) {
                BadgePill(badge, Modifier.align(Alignment.BottomStart).padding(if (compact) 8.dp else 12.dp))
            }
            if (favorite) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(if (compact) 8.dp else 12.dp)
                        .size(if (compact) 28.dp else 34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_star),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(if (compact) 16.dp else 20.dp),
                    )
                }
            }
            return@Box
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(if (markSize != null) 6.dp else if (compact) 12.dp else 18.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                StoreMark(initial, color, image, markSize ?: if (compact) 36.dp else 48.dp)
                Spacer(Modifier.weight(1f))
                if (badge != null) {
                    BadgePill(badge)
                    if (favorite) Spacer(Modifier.width(6.dp))
                }
                if (favorite) {
                    Icon(
                        painter = painterResource(R.drawable.ic_star),
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(if (compact) 18.dp else 22.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (name.isNotEmpty()) {
                Text(
                    text = name,
                    color = content,
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Avviso piccolo sulla tessera: [alert] rosso (scaduta), altrimenti giallo (scade presto). */
class CardBadge(val text: String, val alert: Boolean)

@Composable
private fun BadgePill(badge: CardBadge, modifier: Modifier = Modifier) {
    Text(
        text = badge.text,
        color = if (badge.alert) Color.White else Color(0xFF3D2A00),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(if (badge.alert) Color(0xFFB3261E) else Color(0xFFFFC857))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
