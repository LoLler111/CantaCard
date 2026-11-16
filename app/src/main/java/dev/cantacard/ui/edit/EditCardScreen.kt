package dev.cantacard.ui.edit

import dev.cantacard.ui.contentColorOn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import dev.cantacard.scan.PhotoActivity
import dev.cantacard.image.rememberCardImage
import dev.cantacard.image.SiteIconCache
import dev.cantacard.image.ImageStore
import dev.cantacard.image.Bitmaps
import dev.cantacard.image.IconColors
import dev.cantacard.image.ImageLoader
import java.io.IOException
import java.io.File
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import dev.cantacard.ui.components.CARD_RATIO
import dev.cantacard.ui.components.CardArt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.shadow
import dev.cantacard.ui.Brand
import dev.cantacard.ui.headerColor
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
import dev.cantacard.StorePickerActivity
import dev.cantacard.data.Card
import dev.cantacard.data.Catalog
import dev.cantacard.data.Store
import dev.cantacard.ui.store.StoreBadge
import dev.cantacard.data.CardColors
import dev.cantacard.data.CardInput
import dev.cantacard.data.CardQuery
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
    /** Tutte le tessere salvate, per avvisare dei doppioni. */
    cards: List<Card>,
    catalog: Catalog,
    /** Negozio scelto prima di aprire il modulo (solo per una tessera nuova). */
    initialStoreId: String?,
    initialName: String?,
    /** Immagine condivisa da un'altra app, da ritagliare subito (file provvisorio). */
    initialCrop: String?,
    /** Codice letto dall'immagine condivisa. */
    initialCode: ScannedCode?,
    images: ImageStore,
    hasCamera: Boolean,
    /** Tessera da salvare, eventuale nuova immagine provvisoria, `true` se l'immagine va tolta. */
    onSave: (Card, File?, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val prefilledName = remember {
        original?.name ?: initialName ?: catalog.find(initialStoreId)?.name.orEmpty()
    }
    var storeId by rememberSaveable { mutableStateOf(original?.storeId ?: initialStoreId) }
    var name by rememberSaveable { mutableStateOf(prefilledName) }
    var number by rememberSaveable { mutableStateOf(original?.number ?: initialCode?.text.orEmpty()) }
    // Formato suggerito dal catalogo per una tessera nuova; quello scansionato prevale.
    var formatName by rememberSaveable {
        mutableStateOf(original?.formatName ?: initialCode?.format?.name ?: catalog.find(initialStoreId)?.format?.name)
    }
    // Immagine: nuova provvisoria (WebP già pronto), tolta, o da ritagliare.
    var pendingImage by rememberSaveable { mutableStateOf<String?>(null) }
    var imageRemoved by rememberSaveable { mutableStateOf(false) }
    var cropSource by rememberSaveable { mutableStateOf(initialCrop) }
    var autoIconDone by rememberSaveable { mutableStateOf(false) }
    var iconStatus by remember { mutableStateOf(IconStatus.IDLE) }
    var note by rememberSaveable { mutableStateOf(original?.note.orEmpty()) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    // Nome della tessera già salvata con lo stesso numero e negozio (conferma prima di salvare).
    var duplicateOf by rememberSaveable { mutableStateOf<String?>(null) }

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
    val store = catalog.find(storeId)

    val imageFile: File? = pendingImage?.let(::File)
        ?: original?.image?.takeUnless { imageRemoved }?.let(images::file)
    val cardImage = rememberCardImage(imageFile)

    // Colore automatico: quello dell'icona del negozio, altrimenti quello del catalogo, altrimenti
    // uno stabile dal nome. Il colore del catalogo vince se compare nel logo (es. Lidl resta blu).
    val iconColor = remember(cardImage, store?.color) { IconColors.of(cardImage, store?.color) }
    val automaticColor = iconColor ?: store?.color ?: CardColors.forName(name.trim())
    // Colore scelto a mano dall'utente; `null` = automatico.
    var colorChoice by rememberSaveable { mutableStateOf<Int?>(null) }
    var colorPicked by rememberSaveable { mutableStateOf(false) }
    // Se il colore di una tessera salvata era automatico (anche un colore del catalogo di una
    // versione precedente). Con un'icona salvata serve leggerla: fino ad allora resta il suo colore.
    var colorKnown by rememberSaveable {
        mutableStateOf(original == null || original.color in autoColorsWithoutIcon(original, catalog))
    }
    LaunchedEffect(Unit) {
        if (colorKnown || original == null) return@LaunchedEffect
        val saved = original.image?.let(images::file)?.let { ImageLoader.load(it) }
        val savedIconColor = withContext(Dispatchers.Default) {
            IconColors.of(saved, catalog.find(original.storeId)?.color)
        }
        if (!colorKnown) {
            if (savedIconColor != original.color) colorChoice = original.color
            colorKnown = true
        }
    }
    val cardColor = if (!colorKnown && original != null) original.color else colorChoice ?: automaticColor

    val dirty = if (original == null) {
        name.trim() != prefilledName || number.isNotBlank() || note.isNotBlank() || storeId != initialStoreId ||
            pendingImage != null || colorChoice != null
    } else {
        name != original.name || number != original.number || storeId != original.storeId ||
            formatName != original.formatName || note != original.note.orEmpty() ||
            // Il colore conta solo se scelto a mano: quello automatico può cambiare da solo
            // (catalogo corretto, icona letta) senza che l'utente abbia modificato niente.
            pendingImage != null || imageRemoved || colorPicked
    }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val siteIcons = remember { SiteIconCache(File(context.cacheDir, "site-icons")) }
    val snackbar = remember { SnackbarHostState() }
    val noCodeMessage = stringResource(R.string.photo_no_code)
    val unreadableMessage = stringResource(R.string.photo_error)

    fun apply(code: ScannedCode) {
        number = code.text
        formatName = code.format.name
    }

    /** Salva [bitmap] come immagine provvisoria della tessera. */
    suspend fun usePending(bitmap: Bitmap): Boolean {
        val file = images.newTemp("webp")
        val ok = withContext(Dispatchers.IO) {
            try {
                Bitmaps.writeWebp(bitmap, file)
                true
            } catch (e: IOException) {
                false
            }
        }
        if (ok) {
            pendingImage?.let { File(it).delete() }
            pendingImage = file.path
            imageRemoved = false
        }
        return ok
    }

    /** Copia [source] (già in WebP) come immagine provvisoria della tessera. */
    suspend fun usePendingCopy(source: File): Boolean {
        val file = images.newTemp("webp")
        val ok = withContext(Dispatchers.IO) {
            try {
                source.copyTo(file, overwrite = true)
                true
            } catch (e: IOException) {
                false
            }
        }
        if (ok) {
            pendingImage?.let { File(it).delete() }
            pendingImage = file.path
            imageRemoved = false
        }
        return ok
    }

    /**
     * Icona dal sito del negozio, dalla cache se recente (scelta dal catalogo); con [refresh]
     * sempre riscaricata (pulsante "Aggiorna icona"). [iconUrl]: logo indicato dal catalogo.
     */
    fun fetchIcon(domain: String, iconUrl: String?, refresh: Boolean = false) {
        if (iconStatus == IconStatus.LOADING) return
        scope.launch {
            iconStatus = IconStatus.LOADING
            val file = siteIcons.get(domain, refresh, iconUrl)
            iconStatus = if (file != null && usePendingCopy(file)) IconStatus.IDLE else IconStatus.FAILED
        }
    }

    // Negozio scelto dal catalogo prima di aprire il modulo: icona scaricata da sola, una volta.
    LaunchedEffect(Unit) {
        val domain = store?.domain
        if (original == null && !autoIconDone && domain != null && cropSource == null) {
            autoIconDone = true
            fetchIcon(domain, store?.icon)
        }
    }

    val storeLauncher = rememberLauncherForActivityResult(StorePickerActivity.Pick()) { choice ->
        if (choice != null) {
            val oldName = store?.name
            val newStore = catalog.find(choice.storeId)
            val changed = choice.storeId != storeId
            storeId = choice.storeId
            val newName = newStore?.name ?: choice.name
            if (newName != null && (name.isBlank() || name.trim() == oldName)) name = newName
            newStore?.format?.let { if (number.isBlank()) formatName = it.name }
            // Cambio negozio: nuova icona, ma senza mai sostituire una foto della tessera.
            val domain = newStore?.domain
            if (changed && domain != null && cardImage?.isPhoto != true) fetchIcon(domain, newStore?.icon)
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(PhotoActivity.Contract()) { path ->
        if (path != null) cropSource = path
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) {
                    val bitmap = Bitmaps.decode(context, uri) ?: return@withContext null
                    try {
                        images.newTemp("jpg").also { Bitmaps.writeJpeg(bitmap, it) }.path
                    } catch (e: IOException) {
                        null
                    }
                }
                if (path != null) cropSource = path else snackbar.showSnackbar(unreadableMessage)
            }
        }
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

    fun save(force: Boolean = false) {
        val trimmedName = name.trim()
        val savedFormat = format?.name ?: formatName ?: return
        // Stesso numero per lo stesso negozio: si chiede prima di salvare un doppione. In modifica
        // solo se è la modifica a crearlo (un doppione già esistente non viene richiesto ogni volta).
        if (!force) {
            val wasDuplicate = original != null &&
                CardQuery.findDuplicate(cards, original.number, original.storeId, original.name, original.id) != null
            val duplicate = if (wasDuplicate) {
                null
            } else {
                CardQuery.findDuplicate(cards, cleanNumber, storeId, trimmedName, original?.id)
            }
            if (duplicate != null) {
                duplicateOf = duplicate.name
                return
            }
        }
        val card = if (original == null) {
            Card(
                id = Card.newId(),
                name = trimmedName,
                number = cleanNumber,
                formatName = savedFormat,
                color = cardColor,
                storeId = storeId,
                createdAt = System.currentTimeMillis(),
                note = note.trim().ifEmpty { null },
            )
        } else {
            original.copy(
                name = trimmedName,
                number = cleanNumber,
                formatName = savedFormat,
                // Il colore automatico segue nome e negozio; uno scelto dall'utente resta.
                color = cardColor,
                storeId = storeId,
                note = note.trim().ifEmpty { null },
            )
        }
        onSave(card, pendingImage?.let(::File), imageRemoved)
    }

    BackHandler(enabled = dirty) { confirmDiscard = true }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            FormTopBar(
                title = stringResource(if (original == null) R.string.add_card_title else R.string.edit_card_title),
                onClose = { if (dirty) confirmDiscard = true else onClose() },
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Anteprima della tessera, aggiornata mentre si scrive, a cavallo dell'intestazione verde.
                PreviewHeader {
                    CardArt(
                        name = name.trim().ifEmpty { stringResource(R.string.field_name) },
                        initial = Card.initialOf(name),
                        color = Color(cardColor),
                        favorite = original?.favorite == true,
                        image = cardImage,
                        compact = false,
                        modifier = it,
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
                ) {
                    FormSection(icon = R.drawable.ic_store, title = R.string.section_store) {
                        StoreField(
                            store = store,
                            onClick = {
                                focusManager.clearFocus()
                                storeLauncher.launch(storeId)
                            },
                        )
                        FormTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = R.string.field_name,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next,
                            ),
                        )
                    }

                    FormSection(icon = R.drawable.ic_scan, title = R.string.section_code) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            if (hasCamera) {
                                ActionTile(
                                    icon = R.drawable.ic_scan,
                                    title = R.string.scan_camera,
                                    subtitle = R.string.scan_camera_hint,
                                    primary = true,
                                    onClick = {
                                        focusManager.clearFocus()
                                        scanLauncher.launch(Unit)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            ActionTile(
                                icon = R.drawable.ic_image,
                                title = R.string.scan_photo,
                                subtitle = R.string.scan_photo_hint,
                                primary = false,
                                onClick = {
                                    focusManager.clearFocus()
                                    photoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OrDivider(stringResource(R.string.edit_or_manual))

                        val showError = cleanNumber.isNotEmpty() && format != null && matrix == null
                        FormTextField(
                            value = number,
                            onValueChange = { number = it },
                            label = R.string.field_number,
                            isError = showError,
                            supportingText = if (showError) stringResource(R.string.number_invalid, format.label) else null,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Next,
                            ),
                        )

                        FormatChips(
                            selected = format,
                            unknownName = formatName.takeIf { format == null },
                            onSelect = { formatName = it.name },
                        )

                        if (matrix != null) {
                            BarcodeView(
                                matrix = matrix,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (matrix.isLinear) 110.dp else 170.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                            )
                        }
                    }

                    FormSection(icon = R.drawable.ic_palette, title = R.string.section_image, accent = Brand.Coral) {
                        ImageSection(
                            hasImage = cardImage != null,
                            iconStatus = iconStatus,
                            domain = store?.domain,
                            hasCamera = hasCamera,
                            // "Aggiorna icona" la riscarica; "Icona dal sito" (nessuna immagine) va bene dalla cache.
                            onIcon = { domain -> fetchIcon(domain, store?.icon, refresh = cardImage != null) },
                            onCamera = {
                                focusManager.clearFocus()
                                cameraLauncher.launch(Unit)
                            },
                            onGallery = {
                                focusManager.clearFocus()
                                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onRemove = {
                                pendingImage?.let { File(it).delete() }
                                pendingImage = null
                                imageRemoved = original?.image != null
                                iconStatus = IconStatus.IDLE
                            },
                        )
                        ColorPicker(
                            automatic = automaticColor,
                            selected = cardColor,
                            onSelect = { color ->
                                colorChoice = color.takeUnless { it == automaticColor }
                                colorPicked = true
                                colorKnown = true
                            },
                        )
                    }

                    FormSection(icon = R.drawable.ic_notes, title = R.string.section_details) {
                        FormTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = R.string.field_note,
                            singleLine = false,
                            minLines = 2,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                    }
                }
            }
            SaveBar(
                text = stringResource(if (original == null) R.string.save_new else R.string.save_edit),
                enabled = canSave,
                onClick = { save() },
            )
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 80.dp),
        )
    }

    cropSource?.let { path ->
        val source = remember(path) { File(path) }
        CropDialog(
            source = source,
            onDone = { bitmap ->
                cropSource = null
                source.delete()
                scope.launch { if (!usePending(bitmap)) snackbar.showSnackbar(unreadableMessage) }
            },
            onCancel = {
                cropSource = null
                source.delete()
            },
        )
    }

    duplicateOf?.let { other ->
        AlertDialog(
            onDismissRequest = { duplicateOf = null },
            title = { Text(stringResource(R.string.duplicate_title)) },
            text = { Text(stringResource(R.string.duplicate_body, other)) },
            confirmButton = {
                TextButton(onClick = {
                    duplicateOf = null
                    save(force = true)
                }) {
                    Text(stringResource(if (original == null) R.string.duplicate_add else R.string.duplicate_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { duplicateOf = null }) { Text(stringResource(R.string.action_cancel)) }
            },
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

/** Barra in alto, verde come l'intestazione della lista. */
@Composable
private fun FormTopBar(title: String, onClose: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(headerColor)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close), tint = Color.White)
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

/**
 * Prosecuzione dell'intestazione verde, con gli angoli in basso arrotondati come nella lista, e la
 * tessera che ci sta sopra a cavallo del bordo. [card] riceve il modificatore con le dimensioni.
 */
@Composable
private fun PreviewHeader(card: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardWidth = minOf(maxWidth - 72.dp, 340.dp)
        val cardHeight = cardWidth / CARD_RATIO
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp + cardHeight * 0.62f)
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(headerColor),
        )
        card(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .shadow(10.dp, RoundedCornerShape(18.dp))
                .width(cardWidth)
                .height(cardHeight),
        )
    }
}

/** Pulsante di salvataggio grande, fisso in basso (sopra la tastiera quando è aperta). */
@Composable
private fun SaveBar(text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
            contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** I formati più comuni come pillole, gli altri in un menu ("Altri formati"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatChips(selected: CodeFormat?, unknownName: String?, onSelect: (CodeFormat) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.field_format),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            commonFormats.forEach { format ->
                Pill(text = format.label, selected = format == selected, onClick = { onSelect(format) })
            }
            Box {
                val other = selected?.takeUnless { it in commonFormats }
                Pill(
                    text = other?.label ?: unknownName ?: stringResource(R.string.format_more),
                    selected = other != null || unknownName != null,
                    trailingIcon = R.drawable.ic_expand_more,
                    onClick = { expanded = true },
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    CodeFormat.entries.filterNot { it in commonFormats }.forEach { format ->
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
    }
}

private val commonFormats = listOf(CodeFormat.EAN_13, CodeFormat.CODE_128, CodeFormat.QR_CODE, CodeFormat.EAN_8)

/** Riga del negozio scelto (o "Altro negozio") con il comando per cambiarlo. */
@Composable
private fun StoreField(store: Store?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = fieldColor(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (store != null) {
                StoreBadge(store, 44.dp)
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.secondaryContainer),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_store),
                        contentDescription = null,
                        tint = colors.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    stringResource(R.string.field_store),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    store?.name ?: stringResource(R.string.store_other),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Surface(color = colors.primary.copy(alpha = 0.12f), contentColor = colors.primary, shape = CircleShape) {
                Text(
                    stringResource(R.string.store_change),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Stato del download dell'icona dal sito del negozio. */
private enum class IconStatus { IDLE, LOADING, FAILED }

/** Comandi per l'immagine: icona dal sito (o aggiornamento), foto della tessera, galleria. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageSection(
    hasImage: Boolean,
    iconStatus: IconStatus,
    domain: String?,
    hasCamera: Boolean,
    onIcon: (String) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (domain != null) {
                Pill(
                    text = stringResource(if (hasImage) R.string.image_icon_refresh else R.string.image_icon),
                    icon = R.drawable.ic_refresh,
                    enabled = iconStatus != IconStatus.LOADING,
                    onClick = { onIcon(domain) },
                )
            }
            if (hasCamera) Pill(stringResource(R.string.image_photo), onCamera, icon = R.drawable.ic_photo_camera)
            Pill(stringResource(R.string.image_gallery), onGallery, icon = R.drawable.ic_image)
            if (hasImage) Pill(stringResource(R.string.image_remove), onRemove, icon = R.drawable.ic_delete)
        }
        when (iconStatus) {
            IconStatus.LOADING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.image_icon_loading, domain.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconStatus.FAILED -> Text(
                stringResource(R.string.image_icon_failed, domain.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconStatus.IDLE -> Unit
        }
    }
}

/**
 * Colori automatici possibili di una tessera salvata senza guardare la sua icona: dal nome, dal
 * catalogo attuale o da una versione precedente del catalogo ([dev.cantacard.data.Store.formerColors]).
 */
private fun autoColorsWithoutIcon(card: Card, catalog: Catalog): Set<Int> {
    val store = catalog.find(card.storeId)
    return buildSet {
        add(CardColors.forName(card.name))
        store?.color?.let(::add)
        store?.formerColors?.let(::addAll)
    }
}

/** Tavolozza del colore della tessera: il primo è quello automatico (icona, negozio o nome). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(automatic: Int, selected: Int, onSelect: (Int) -> Unit) {
    val colors = remember(automatic) { (listOf(automatic) + CardColors.palette).distinct() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.field_color),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            colors.forEach { value ->
                val color = Color(value)
                val isSelected = value == selected
                val label = if (value == automatic) stringResource(R.string.color_automatic) else CardColors.toHex(value)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .then(
                            if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier,
                        )
                        .padding(if (isSelected) 5.dp else 2.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable(onClickLabel = label) { onSelect(value) }
                        .semantics {
                            contentDescription = label
                            this.selected = isSelected
                        },
                ) {
                    if (isSelected) {
                        Icon(
                            painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = contentColorOn(color),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
