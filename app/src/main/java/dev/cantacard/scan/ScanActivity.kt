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
import androidx.compose.foundation.Canvas
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
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            val frameWidth = size.width * 0.84f
            val frameHeight = (frameWidth / 1.586f).coerceAtMost(size.height * 0.5f)
            val topLeft = Offset((size.width - frameWidth) / 2, (size.height - frameHeight) / 2)
            val frameSize = Size(frameWidth, frameHeight)
            val radius = CornerRadius(16.dp.toPx())
            drawRect(Color.Black.copy(alpha = 0.55f))
            drawRoundRect(Color.Transparent, topLeft, frameSize, radius, blendMode = BlendMode.Clear)
            drawRoundRect(Color.White, topLeft, frameSize, radius, style = Stroke(width = 3.dp.toPx()))
        }
        Text(
            text = hint,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 64.dp, start = 24.dp, end = 24.dp),
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
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close), tint = Color.White)
        }
        if (torch != null) {
            IconButton(onClick = onToggleTorch) {
                Icon(
                    painter = painterResource(if (torch) R.drawable.ic_flash_off else R.drawable.ic_flash_on),
                    contentDescription = stringResource(if (torch) R.string.torch_off else R.string.torch_on),
                    tint = Color.White,
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
