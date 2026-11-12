package dev.cantacard.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import dev.cantacard.ui.Brand
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.cantacard.R
import dev.cantacard.barcode.ScannedCode
import dev.cantacard.data.CodeFormat
import dev.cantacard.ui.CantaCardTheme
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.Executors

/**
 * Scansione offline con CameraX + ZXing. Restituisce numero e formato a chi l'ha aperta
 * (vedi [Contract]); il permesso della fotocamera si chiede solo qui.
 */
class ScanActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            CantaCardTheme {
                ScanScreen(
                    hasPermission = { hasCameraPermission() },
                    shouldExplain = { shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) },
                    onOpenSettings = ::openAppSettings,
                    onCode = ::deliver,
                    onClose = ::finish,
                )
            }
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
    }

    private fun deliver(code: ScannedCode) {
        window.decorView.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.VIRTUAL_KEY,
        )
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(EXTRA_TEXT, code.text).putExtra(EXTRA_FORMAT, code.format.name),
        )
        finish()
    }

    /** Apre lo scanner e restituisce il codice letto, o `null` se l'utente chiude. */
    class Contract : ActivityResultContract<Unit, ScannedCode?>() {
        override fun createIntent(context: Context, input: Unit): Intent =
            Intent(context, ScanActivity::class.java)

        override fun parseResult(resultCode: Int, intent: Intent?): ScannedCode? {
            if (resultCode != Activity.RESULT_OK || intent == null) return null
            val text = intent.getStringExtra(EXTRA_TEXT) ?: return null
            val format = CodeFormat.fromName(intent.getStringExtra(EXTRA_FORMAT)) ?: return null
            return ScannedCode(text, format)
        }
    }

    private companion object {
        const val EXTRA_TEXT = "dev.cantacard.extra.SCAN_TEXT"
        const val EXTRA_FORMAT = "dev.cantacard.extra.SCAN_FORMAT"
    }
}

@Composable
private fun ScanScreen(
    hasPermission: () -> Boolean,
    shouldExplain: () -> Boolean,
    onOpenSettings: () -> Unit,
    onCode: (ScannedCode) -> Unit,
    onClose: () -> Unit,
) {
    var granted by remember { mutableStateOf(hasPermission()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    // Dopo un rifiuto definitivo il sistema non mostra più la richiesta: si va alle impostazioni.
    var blocked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        blocked = !ok && !shouldExplain()
    }
    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
    // Il permesso può essere concesso dalle impostazioni mentre l'app è in pausa.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        granted = hasPermission()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            granted -> {
                var torch by rememberSaveable { mutableStateOf(false) }
                var camera by remember { mutableStateOf<Camera?>(null) }
                CameraPreview(onCode = onCode, onCamera = { camera = it }, modifier = Modifier.fillMaxSize())
                ScanOverlay(Modifier.fillMaxSize())
                LaunchedEffect(camera, torch) { camera?.cameraControl?.enableTorch(torch) }
                TopControls(
                    onClose = onClose,
                    torch = torch.takeIf { camera?.cameraInfo?.hasFlashUnit() == true },
                    onToggleTorch = { torch = !torch },
                )
            }
            asked -> PermissionRationale(
                blocked = blocked,
                onGrant = {
                    if (blocked) onOpenSettings() else launcher.launch(Manifest.permission.CAMERA)
                },
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun CameraPreview(
    onCode: (ScannedCode) -> Unit,
    onCamera: (Camera) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val provider = ProcessCameraProvider.awaitInstance(context)
        try {
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider { request -> surfaceRequest = request }
            }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                android.util.Size(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            ),
                        )
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            val mainExecutor = ContextCompat.getMainExecutor(context)
            analysis.setAnalyzer(executor, BarcodeAnalyzer { code -> mainExecutor.execute { onCode(code) } })
            provider.unbindAll()
            onCamera(
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis),
            )
            awaitCancellation()
        } catch (e: IllegalArgumentException) {
            // Nessuna fotocamera posteriore utilizzabile.
            error = true
        } finally {
            provider.unbindAll()
            executor.shutdown()
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
        if (error) {
            Text(stringResource(R.string.camera_error), color = Color.White)
        }
    }
}

/** Oscura tutto tranne un riquadro centrale, dove inquadrare il codice. */
@Composable
private fun ScanOverlay(modifier: Modifier = Modifier) {
    val hint = stringResource(R.string.scan_hint)
    Box(modifier) {
        val sweep by rememberInfiniteTransition(label = "scan").animateFloat(
            initialValue = 0.08f,
            targetValue = 0.92f,
            animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "sweep",
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            val frameWidth = size.width * 0.84f
            val frameHeight = (frameWidth / 1.586f).coerceAtMost(size.height * 0.5f)
            val left = (size.width - frameWidth) / 2
            val top = (size.height - frameHeight) / 2
            val radius = 20.dp.toPx()
            drawRect(Color.Black.copy(alpha = 0.6f))
            drawRoundRect(
                Color.Transparent, Offset(left, top), Size(frameWidth, frameHeight), CornerRadius(radius),
                blendMode = BlendMode.Clear,
            )
            // Angoli del mirino.
            val arm = 34.dp.toPx()
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            val right = left + frameWidth
            val bottom = top + frameHeight
            listOf(
                Triple(Offset(left, top), 180f, Offset(1f, 1f)),
                Triple(Offset(right - 2 * radius, top), 270f, Offset(-1f, 1f)),
                Triple(Offset(right - 2 * radius, bottom - 2 * radius), 0f, Offset(-1f, -1f)),
                Triple(Offset(left, bottom - 2 * radius), 90f, Offset(1f, -1f)),
            ).forEach { (arcTopLeft, start, _) ->
                drawArc(Brand.Teal300, start, 90f, false, arcTopLeft, Size(2 * radius, 2 * radius), style = stroke)
            }
            val strokeWidth = 5.dp.toPx()
            fun line(from: Offset, to: Offset) =
                drawLine(Brand.Teal300, from, to, strokeWidth, cap = StrokeCap.Round)
            line(Offset(left + radius, top), Offset(left + radius + arm, top))
            line(Offset(left, top + radius), Offset(left, top + radius + arm))
            line(Offset(right - radius, top), Offset(right - radius - arm, top))
            line(Offset(right, top + radius), Offset(right, top + radius + arm))
            line(Offset(left + radius, bottom), Offset(left + radius + arm, bottom))
            line(Offset(left, bottom - radius), Offset(left, bottom - radius - arm))
            line(Offset(right - radius, bottom), Offset(right - radius - arm, bottom))
            line(Offset(right, bottom - radius), Offset(right, bottom - radius - arm))
            // Linea di scansione che scorre su e giù.
            val y = top + frameHeight * sweep
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Brand.Coral.copy(alpha = 0.35f), Color.Transparent),
                    startY = y - 18.dp.toPx(),
                    endY = y + 18.dp.toPx(),
                ),
                topLeft = Offset(left + 12.dp.toPx(), y - 18.dp.toPx()),
                size = Size(frameWidth - 24.dp.toPx(), 36.dp.toPx()),
            )
            drawLine(
                Brand.Coral, Offset(left + 16.dp.toPx(), y), Offset(right - 16.dp.toPx(), y),
                2.dp.toPx(), cap = StrokeCap.Round,
            )
        }
        Text(
            text = hint,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 56.dp, start = 24.dp, end = 24.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.16f))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun TopControls(onClose: () -> Unit, torch: Boolean?, onToggleTorch: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .systemBarsPadding()
            .padding(8.dp),
    ) {
        val round = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
        IconButton(onClick = onClose, modifier = round) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close), tint = Color.White)
        }
        if (torch != null) {
            IconButton(
                onClick = onToggleTorch,
                modifier = if (torch) Modifier.clip(CircleShape).background(Brand.Sun) else round,
            ) {
                Icon(
                    painter = painterResource(if (torch) R.drawable.ic_flash_off else R.drawable.ic_flash_on),
                    contentDescription = stringResource(if (torch) R.string.torch_off else R.string.torch_on),
                    tint = if (torch) Color.Black else Color.White,
                )
            }
        }
    }
}

@Composable
private fun PermissionRationale(blocked: Boolean, onGrant: () -> Unit, onClose: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(32.dp),
    ) {
        Text(
            stringResource(R.string.camera_permission_title),
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.camera_permission_body),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) {
            Text(
                stringResource(
                    if (blocked) R.string.camera_permission_settings else R.string.camera_permission_grant,
                ),
            )
        }
        TextButton(onClick = onClose) { Text(stringResource(R.string.action_close), color = Color.White) }
    }
}
