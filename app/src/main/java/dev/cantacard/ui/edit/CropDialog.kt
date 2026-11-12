package dev.cantacard.ui.edit

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.cantacard.R
import dev.cantacard.image.Bitmaps
import dev.cantacard.image.ImageStore
import dev.cantacard.ui.Brand
import dev.cantacard.ui.components.CARD_RATIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Ritaglio della foto della tessera nella sagoma di una carta (rapporto ID-1): si sposta e si
 * ingrandisce con le dita, si ruota di 90°. Restituisce l'immagine già ridotta per l'app.
 */
@Composable
fun CropDialog(source: File, onDone: (Bitmap) -> Unit, onCancel: () -> Unit) {
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(source) { mutableStateOf(false) }
    LaunchedEffect(source) {
        val decoded = withContext(Dispatchers.IO) { Bitmaps.decodeFile(source) }
        if (decoded == null) failed = true else bitmap = decoded
    }
    LaunchedEffect(failed) { if (failed) onCancel() }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val current = bitmap
            if (current == null) {
                CircularProgressIndicator(color = Brand.Teal300, modifier = Modifier.align(Alignment.Center))
            } else {
                CropArea(
                    bitmap = current,
                    onRotate = { bitmap = Bitmaps.rotate(current, 90) },
                    onDone = onDone,
                    onCancel = onCancel,
                )
            }
        }
    }
}

@Composable
private fun CropArea(bitmap: Bitmap, onRotate: () -> Unit, onDone: (Bitmap) -> Unit, onCancel: () -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Text(
            text = stringResource(R.string.crop_hint),
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        )
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val density = LocalDensity.current
            val boxW = with(density) { maxWidth.toPx() }
            val boxH = with(density) { maxHeight.toPx() }
            val frameW = minOf(boxW * 0.88f, boxH * 0.9f * CARD_RATIO)
            val frameH = frameW / CARD_RATIO
            val frameLeft = (boxW - frameW) / 2
            val frameTop = (boxH - frameH) / 2
            // Scala minima: l'immagine copre sempre tutta la sagoma.
            val minScale = max(frameW / bitmap.width, frameH / bitmap.height)
            var scale by remember(bitmap, frameW) { mutableFloatStateOf(minScale) }
            var offset by remember(bitmap, frameW) { mutableStateOf(Offset.Zero) }

            fun clamp(s: Float, o: Offset): Offset {
                val maxX = max(0f, (bitmap.width * s - frameW) / 2)
                val maxY = max(0f, (bitmap.height * s - frameH) / 2)
                return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
            }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(bitmap, frameW) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val s = (scale * zoom).coerceIn(minScale, minScale * 6f)
                            scale = s
                            offset = clamp(s, offset + pan)
                        }
                    },
            ) {
                val w = bitmap.width * scale
                val h = bitmap.height * scale
                val left = frameLeft + frameW / 2 + offset.x - w / 2
                val top = frameTop + frameH / 2 + offset.y - h / 2
                drawImage(
                    image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
                val radius = CornerRadius(16.dp.toPx())
                // Scurisce solo fuori dalla sagoma (rettangolo pieno meno la sagoma, regola pari-dispari).
                val shade = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addRoundRect(RoundRect(Rect(Offset(frameLeft, frameTop), Size(frameW, frameH)), radius))
                }
                drawPath(shade, Color.Black.copy(alpha = 0.55f))
                drawRoundRect(
                    Color.White, Offset(frameLeft, frameTop), Size(frameW, frameH), radius,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            CropActions(
                onCancel = onCancel,
                onRotate = onRotate,
                onDone = {
                    val w = bitmap.width * scale
                    val h = bitmap.height * scale
                    val imageLeft = frameLeft + frameW / 2 + offset.x - w / 2
                    val imageTop = frameTop + frameH / 2 + offset.y - h / 2
                    onDone(
                        cropToCard(
                            bitmap,
                            left = (frameLeft - imageLeft) / scale,
                            top = (frameTop - imageTop) / scale,
                            width = frameW / scale,
                        ),
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun CropActions(onCancel: () -> Unit, onRotate: () -> Unit, onDone: () -> Unit, modifier: Modifier) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel), color = Color.White) }
        TextButton(onClick = onRotate) { Text(stringResource(R.string.crop_rotate), color = Color.White) }
        Button(onClick = onDone) { Text(stringResource(R.string.crop_use)) }
    }
}

/** Ritaglia un rettangolo col rapporto della tessera e lo porta a [ImageStore.PHOTO_WIDTH]. */
private fun cropToCard(bitmap: Bitmap, left: Float, top: Float, width: Float): Bitmap {
    val w = width.roundToInt().coerceIn(1, bitmap.width)
    val h = (width / CARD_RATIO).roundToInt().coerceIn(1, bitmap.height)
    val x = left.roundToInt().coerceIn(0, bitmap.width - w)
    val y = top.roundToInt().coerceIn(0, bitmap.height - h)
    val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
    val targetW = minOf(ImageStore.PHOTO_WIDTH, w)
    val targetH = (targetW / CARD_RATIO).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
}
