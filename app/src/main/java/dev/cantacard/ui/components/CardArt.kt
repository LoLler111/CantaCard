package dev.cantacard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cantacard.R
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

/**
 * Disegno di una tessera (senza codice): usato nella lista e come anteprima nel modulo.
 * Le dimensioni seguono il modificatore; il contenuto scala con [compact].
 */
@Composable
fun CardArt(
    name: String,
    initial: String,
    color: Color,
    favorite: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    val content = contentColorOn(color)
    Box(modifier.cardSurface(color)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(if (compact) 12.dp else 18.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                InitialBadge(initial, color, if (compact) 36.dp else 48.dp)
                Spacer(Modifier.weight(1f))
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
