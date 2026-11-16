package dev.cantacard.ui.edit

import androidx.compose.foundation.Image

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.cantacard.R
import dev.cantacard.data.Card
import dev.cantacard.image.CardImage
import dev.cantacard.ui.components.CARD_RATIO
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Data della scadenza nel formato della lingua del telefono (es. "31 mar 2027"). */
fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/** Foto del retro della tessera: anteprima e comandi per scattarla, sceglierla o toglierla. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BackSection(
    image: CardImage?,
    hasCamera: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (image != null) {
            Image(
                bitmap = image.bitmap,
                contentDescription = stringResource(R.string.section_back),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .fillMaxWidth()
                    .aspectRatio(CARD_RATIO)
                    .clip(RoundedCornerShape(14.dp)),
            )
        } else {
            Text(
                stringResource(R.string.back_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hasCamera) {
                Pill(
                    stringResource(if (image != null) R.string.back_photo_again else R.string.back_photo),
                    onCamera,
                    icon = R.drawable.ic_photo_camera,
                )
            }
            Pill(stringResource(R.string.image_gallery), onGallery, icon = R.drawable.ic_image)
            if (image != null) Pill(stringResource(R.string.image_remove), onRemove, icon = R.drawable.ic_delete)
        }
    }
}

/** Riga della scadenza: tocca per scegliere la data, la X la toglie. */
@Composable
internal fun ExpiryRow(expiry: LocalDate?, onPick: () -> Unit, onClear: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onPick, shape = RoundedCornerShape(16.dp), color = fieldColor(), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Icon(painterResource(R.drawable.ic_event), contentDescription = null, tint = colors.onSurfaceVariant)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.field_expiry), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(
                    expiry?.let(::formatDate) ?: stringResource(R.string.expiry_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (expiry != null) colors.onSurface else colors.onSurfaceVariant,
                )
            }
            if (expiry != null) {
                IconButton(onClick = onClear) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.expiry_clear))
                }
            } else {
                Spacer(Modifier.size(12.dp))
            }
        }
    }
}

/** Calendario per la scadenza (Material 3, già incluso nella libreria usata dall'app). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpiryDialog(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val start = initial ?: LocalDate.now().plusYears(1)
    val state = rememberDatePickerState(initialSelectedDateMillis = start.toEpochDay() * DAY_MS)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) { Text(stringResource(R.string.expiry_set)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

private const val DAY_MS = 86_400_000L

/** Etichette: quelle già usate come pillole da accendere e spegnere, più "Nuova etichetta". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LabelsPicker(all: List<String>, selected: List<String>, onToggle: (String) -> Unit, onAdd: () -> Unit) {
    // Le etichette appena aggiunte compaiono anche se nessuna tessera salvata le usa ancora.
    val shown = remember(all, selected) { Card.cleanLabels(all + selected) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.field_labels),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            shown.forEach { label ->
                val on = selected.any { it.equals(label, ignoreCase = true) }
                Pill(label, onClick = { onToggle(label) }, selected = on, icon = if (on) R.drawable.ic_check else R.drawable.ic_label)
            }
            Pill(stringResource(R.string.label_new), onClick = onAdd, icon = R.drawable.ic_add)
        }
    }
}

@Composable
internal fun NewLabelDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_new)) },
        text = {
            Box {
                FormTextField(
                    value = text,
                    onValueChange = { if (it.length <= Card.MAX_LABEL_LENGTH) text = it },
                    label = R.string.label_name,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onAdd(text) }),
                    modifier = Modifier.focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onAdd(text) }) { Text(stringResource(R.string.label_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

