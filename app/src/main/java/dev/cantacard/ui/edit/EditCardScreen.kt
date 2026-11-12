package dev.cantacard.ui.edit

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.cantacard.R
import dev.cantacard.barcode.BarcodeEncoder
import dev.cantacard.barcode.BarcodeView
import dev.cantacard.barcode.ScannedCode
import dev.cantacard.data.Card
import dev.cantacard.data.CardColors
import dev.cantacard.data.CardInput
import dev.cantacard.data.CodeFormat
import dev.cantacard.scan.ImageBarcodeReader
import dev.cantacard.scan.ScanActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modulo di aggiunta/modifica: nome, numero (scansionato, letto da foto o digitato), formato
 * e nota. L'anteprima del codice si aggiorna mentre si scrive; non si salva un codice che non
 * si riesce a generare.
 */
@Composable
fun EditCardScreen(
    original: Card?,
    hasCamera: Boolean,
    onSave: (Card) -> Unit,
    onClose: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(original?.name.orEmpty()) }
    var number by rememberSaveable { mutableStateOf(original?.number.orEmpty()) }
    var formatName by rememberSaveable { mutableStateOf(original?.formatName) }
    var note by rememberSaveable { mutableStateOf(original?.note.orEmpty()) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    // Formato scelto (o letto) oppure, per una tessera nuova, dedotto dal numero.
    val format: CodeFormat? = formatName?.let(CodeFormat::fromName)
        ?: if (formatName == null) CardInput.guessFormat(number) else null
    val cleanNumber = CardInput.normalizeNumber(number, format)
    val matrix = remember(cleanNumber, format) {
        format?.let { BarcodeEncoder.encode(cleanNumber, it) }
    }
    // Formato sconosciuto (da un backup più recente): si conserva com'è.
    val codeOk = cleanNumber.isNotEmpty() && (matrix != null || (format == null && formatName != null))
    val canSave = name.isNotBlank() && codeOk
    val dirty = if (original == null) {
        name.isNotBlank() || number.isNotBlank() || note.isNotBlank()
    } else {
        name != original.name || number != original.number ||
            formatName != original.formatName || note != original.note.orEmpty()
    }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val noCodeMessage = stringResource(R.string.photo_no_code)
    val unreadableMessage = stringResource(R.string.photo_error)

    fun apply(code: ScannedCode) {
        number = code.text
        formatName = code.format.name
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanActivity.Contract()) { code ->
        code?.let(::apply)
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.Default) { ImageBarcodeReader.read(context, uri) }
                when (result) {
                    is ImageBarcodeReader.Result.Found -> apply(result.code)
                    ImageBarcodeReader.Result.NotFound -> snackbar.showSnackbar(noCodeMessage)
                    ImageBarcodeReader.Result.Unreadable -> snackbar.showSnackbar(unreadableMessage)
                }
            }
        }
    }

    fun save() {
        val trimmedName = name.trim()
        val savedFormat = format?.name ?: formatName ?: return
        val card = if (original == null) {
            Card(
                id = Card.newId(),
                name = trimmedName,
                number = cleanNumber,
                formatName = savedFormat,
                color = CardColors.forName(trimmedName),
                createdAt = System.currentTimeMillis(),
                note = note.trim().ifEmpty { null },
            )
        } else {
            // Il colore automatico segue il nome; uno scelto dall'utente (fase 5) resta.
            val autoColor = original.color == CardColors.forName(original.name)
            original.copy(
                name = trimmedName,
                number = cleanNumber,
                formatName = savedFormat,
                color = if (autoColor) CardColors.forName(trimmedName) else original.color,
                note = note.trim().ifEmpty { null },
            )
        }
        onSave(card)
    }

    BackHandler(enabled = dirty) { confirmDiscard = true }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
            ) {
                IconButton(onClick = { if (dirty) confirmDiscard = true else onClose() }) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close))
                }
                Text(
                    text = stringResource(if (original == null) R.string.add_card_title else R.string.edit_card_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                )
                TextButton(onClick = ::save, enabled = canSave) { Text(stringResource(R.string.action_save)) }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (hasCamera) {
                        OutlinedButton(
                            onClick = {
                                focusManager.clearFocus()
                                scanLauncher.launch(Unit)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(painterResource(R.drawable.ic_scan), null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.scan_camera))
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            photoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(painterResource(R.drawable.ic_image), null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.scan_photo))
                    }
                }

                val showError = cleanNumber.isNotEmpty() && format != null && matrix == null
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text(stringResource(R.string.field_number)) },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(R.string.number_invalid, format.label)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                FormatPicker(
                    selectedLabel = format?.label ?: formatName.orEmpty(),
                    onSelect = { formatName = it.name },
                )

                if (matrix != null) {
                    BarcodeView(
                        matrix = matrix,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (matrix.isLinear) 120.dp else 180.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.field_note)) },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding(),
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text(stringResource(R.string.discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Campo di sola lettura che apre l'elenco dei formati. */
@Composable
private fun FormatPicker(selectedLabel: String, onSelect: (CodeFormat) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_format)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Copre il campo per aprire il menu con un tocco ovunque.
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Transparent)
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CodeFormat.entries.forEach { format ->
                DropdownMenuItem(
                    text = { Text(format.label) },
                    onClick = {
                        expanded = false
                        onSelect(format)
                    },
                )
            }
        }
    }
}
