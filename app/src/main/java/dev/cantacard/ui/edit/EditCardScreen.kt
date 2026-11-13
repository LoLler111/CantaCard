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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import dev.cantacard.ui.SectionLabelStyle
import dev.cantacard.ui.components.CARD_RATIO
import dev.cantacard.ui.components.CardArt
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
import dev.cantacard.StorePickerActivity
import dev.cantacard.data.Card
import dev.cantacard.data.Catalog
import dev.cantacard.data.Store
import dev.cantacard.ui.store.StoreBadge
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
    // Colore automatico: quello del negozio in catalogo, altrimenti uno stabile dal nome.
    fun autoColor(cardName: String, id: String?): Int = catalog.find(id)?.color ?: CardColors.forName(cardName)
    val originalAuto = original != null &&
        (original.color == autoColor(original.name, original.storeId) || original.color == CardColors.forName(original.name))
    // Colore scelto a mano dall'utente; `null` = automatico.
    var colorChoice by rememberSaveable {
        mutableStateOf(if (original != null && !originalAuto) original.color else null)
    }
    val automaticColor = autoColor(name.trim(), storeId)
    val cardColor = colorChoice ?: automaticColor

    val imageFile: File? = pendingImage?.let(::File)
        ?: original?.image?.takeUnless { imageRemoved }?.let(images::file)
    val cardImage = rememberCardImage(imageFile)

    val dirty = if (original == null) {
        name.trim() != prefilledName || number.isNotBlank() || note.isNotBlank() || storeId != initialStoreId ||
            pendingImage != null || colorChoice != null
    } else {
        name != original.name || number != original.number || storeId != original.storeId ||
            formatName != original.formatName || note != original.note.orEmpty() ||
            pendingImage != null || imageRemoved || cardColor != original.color
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
     * sempre riscaricata (pulsante "Aggiorna icona").
     */
    fun fetchIcon(domain: String, refresh: Boolean = false) {
        if (iconStatus == IconStatus.LOADING) return
        scope.launch {
            iconStatus = IconStatus.LOADING
            val file = siteIcons.get(domain, refresh)
            iconStatus = if (file != null && usePendingCopy(file)) IconStatus.IDLE else IconStatus.FAILED
        }
    }

    // Negozio scelto dal catalogo prima di aprire il modulo: icona scaricata da sola, una volta.
    LaunchedEffect(Unit) {
        val domain = store?.domain
        if (original == null && !autoIconDone && domain != null && cropSource == null) {
            autoIconDone = true
            fetchIcon(domain)
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
            if (changed && domain != null && cardImage?.isPhoto != true) fetchIcon(domain)
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

    fun save() {
        val trimmedName = name.trim()
        val savedFormat = format?.name ?: formatName ?: return
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
                Button(
                    onClick = ::save,
                    enabled = canSave,
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text(stringResource(R.string.action_save)) }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                // Anteprima della tessera, aggiornata mentre si scrive.
                val previewColor = Color(cardColor)
                CardArt(
                    name = name.trim().ifEmpty { stringResource(R.string.field_name) },
                    initial = Card.initialOf(name),
                    color = previewColor,
                    favorite = original?.favorite == true,
                    image = cardImage,
                    compact = false,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = 340.dp)
                        .fillMaxWidth()
                        .aspectRatio(CARD_RATIO),
                )
                Spacer(Modifier.height(4.dp))

                StoreField(
                    store = store,
                    onClick = {
                        focusManager.clearFocus()
                        storeLauncher.launch(storeId)
                    },
                )

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

                SectionTitle(R.string.section_code)
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
                Text(
                    text = stringResource(R.string.edit_or_manual),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 4.dp),
                )

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
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                    )
                }

                SectionTitle(R.string.section_image)
                ImageSection(
                    hasImage = cardImage != null,
                    iconStatus = iconStatus,
                    domain = store?.domain,
                    hasCamera = hasCamera,
                    // "Aggiorna icona" la riscarica; "Icona dal sito" (nessuna immagine) va bene dalla cache.
                    onIcon = { domain -> fetchIcon(domain, refresh = cardImage != null) },
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
                    onSelect = { color -> colorChoice = color.takeUnless { it == automaticColor } },
                )

                SectionTitle(R.string.section_details)
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

@Composable
private fun SectionTitle(text: Int) {
    Text(
        text = stringResource(text).uppercase(),
        style = SectionLabelStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

/** Pulsante grande con icona in un tondo, titolo e sottotitolo. */
@Composable
private fun ActionTile(
    icon: Int,
    title: Int,
    subtitle: Int,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (primary) colors.primary else colors.surfaceContainerHigh
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
                    .background(content.copy(alpha = 0.14f)),
            ) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
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

/** Riga del negozio scelto (o "Altro negozio") con il comando per cambiarlo. */
@Composable
private fun StoreField(store: Store?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainerLowest,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (store != null) {
                StoreBadge(store, 40.dp)
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.secondaryContainer),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        tint = colors.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
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
            Text(
                stringResource(R.string.store_change),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
            )
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
                ImageButton(
                    icon = R.drawable.ic_refresh,
                    label = if (hasImage) R.string.image_icon_refresh else R.string.image_icon,
                    enabled = iconStatus != IconStatus.LOADING,
                    onClick = { onIcon(domain) },
                )
            }
            if (hasCamera) ImageButton(R.drawable.ic_photo_camera, R.string.image_photo, onClick = onCamera)
            ImageButton(R.drawable.ic_image, R.string.image_gallery, onClick = onGallery)
            if (hasImage) ImageButton(R.drawable.ic_delete, R.string.image_remove, onClick = onRemove)
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

@Composable
private fun ImageButton(icon: Int, label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(label))
    }
}

/** Tavolozza del colore della tessera: il primo è quello automatico (negozio o nome). */
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            colors.forEach { value ->
                val color = Color(value)
                val isSelected = value == selected
                val label = if (value == automatic) stringResource(R.string.color_automatic) else CardColors.toHex(value)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier,
                        )
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
