package dev.cantacard.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.cantacard.ui.Brand

/** Colore dei riquadri del modulo: bianco sullo sfondo verde grigio, più chiaro dello sfondo nel tema scuro. */
@Composable
internal fun sectionColor(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest

/** Colore dei campi dentro un riquadro. */
@Composable
internal fun fieldColor(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer

/**
 * Riquadro di una sezione del modulo: angoli molto arrotondati, intestazione con l'icona in un
 * quadratino colorato. Niente ombre (come le tessere), il contrasto lo dà lo sfondo.
 */
@Composable
internal fun FormSection(
    icon: Int,
    title: Int,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = sectionColor(),
        shape = RoundedCornerShape(26.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accent.copy(alpha = 0.14f)),
                ) {
                    Icon(painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
            }
            content()
        }
    }
}

/** Campo di testo pieno e arrotondato, senza la linea sotto del Material standard. */
@Composable
internal fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    leadingIcon: Int? = null,
) {
    val container = fieldColor()
    Column(modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(label)) },
            singleLine = singleLine,
            minLines = minLines,
            isError = isError,
            keyboardOptions = keyboardOptions,
            shape = RoundedCornerShape(16.dp),
            leadingIcon = leadingIcon?.let { { Icon(painterResource(it), contentDescription = null) } },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = container,
                unfocusedContainerColor = container,
                errorContainerColor = container,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isError) Modifier.border(1.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(16.dp))
                    else Modifier,
                ),
        )
        if (supportingText != null) {
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, top = 6.dp),
            )
        }
    }
}

/** Pulsante a pillola: [selected] pieno nel colore del marchio, altrimenti tenue. */
@Composable
internal fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    trailingIcon: Int? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (selected) colors.primary else fieldColor()
    val content = if (selected) colors.onPrimary else colors.onSurface
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = container,
        contentColor = if (enabled) content else content.copy(alpha = 0.4f),
        shape = CircleShape,
        modifier = modifier.heightIn(min = 40.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                PaddingValues(start = if (icon != null) 12.dp else 16.dp, end = if (trailingIcon != null) 10.dp else 16.dp),
            ),
        ) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 10.dp))
            if (trailingIcon != null) {
                Spacer(Modifier.width(4.dp))
                Icon(painterResource(trailingIcon), contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Pulsante grande con icona in un tondo, titolo e sottotitolo (scansione e lettura da foto). */
@Composable
internal fun ActionTile(
    icon: Int,
    title: Int,
    subtitle: Int,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (primary) colors.primary else fieldColor()
    val content = if (primary) colors.onPrimary else colors.onSurface
    Surface(
        onClick = onClick,
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (primary) Brand.Coral else content.copy(alpha = 0.10f)),
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = if (primary) Color.White else content,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.75f),
            )
        }
    }
}

/** Separatore "oppure ..." con due righe sottili ai lati. */
@Composable
internal fun OrDivider(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val line = MaterialTheme.colorScheme.outlineVariant
        Box(Modifier.weight(1f).height(1.dp).background(line))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(line))
    }
}
