package dev.cantacard.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.cantacard.R
import dev.cantacard.app
import dev.cantacard.image.Bitmaps
import dev.cantacard.ui.Brand
import dev.cantacard.ui.CantaCardTheme
import dev.cantacard.ui.components.CARD_RATIO
import kotlinx.coroutines.awaitCancellation
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Foto del fronte della tessera con la fotocamera dell'app (CameraX, già inclusa per lo
 * scanner). Restituisce il percorso di un file provvisorio da ritagliare.
 */
class PhotoActivity : ComponentActivity() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            CantaCardTheme {
                CameraPermissionGate(
                    hasPermission = {
                        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                    },
                    shouldExplain = { shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) },
                    onOpenSettings = {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                        )
                    },
                    onClose = ::finish,
                ) {
                    PhotoScreen(
                        back = intent.getBooleanExtra(EXTRA_BACK, false),
                        onShoot = ::shoot,
                        onClose = ::finish,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun shoot(capture: ImageCapture, onFailed: () -> Unit) {
        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val path = try {
                        val rotation = image.imageInfo.rotationDegrees
                        val bitmap = image.use { it.toBitmap() }
                        val upright = Bitmaps.fit(Bitmaps.rotate(bitmap, rotation), Bitmaps.SOURCE_MAX_SIDE)
                        val file = app.images.newTemp("jpg")
                        Bitmaps.writeJpeg(upright, file)
                        file.path
                    } catch (e: IOException) {
                        null
                    } catch (e: RuntimeException) {
                        null
                    }
                    runOnUiThread {
                        if (path == null) {
                            onFailed()
                        } else {
                            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_PATH, path))
                            finish()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread(onFailed)
                }
            },
        )
    }

    /** Scatta una foto e restituisce il percorso del file provvisorio, o `null` se si chiude. */
    /** Input: `true` per fotografare il retro della tessera. */
    class Contract : ActivityResultContract<Boolean, String?>() {
        override fun createIntent(context: Context, input: Boolean): Intent =
            Intent(context, PhotoActivity::class.java).putExtra(EXTRA_BACK, input)

        override fun parseResult(resultCode: Int, intent: Intent?): String? =
            if (resultCode == Activity.RESULT_OK) intent?.getStringExtra(EXTRA_PATH) else null
    }

    private companion object {
        const val EXTRA_PATH = "dev.cantacard.extra.PHOTO_PATH"
        const val EXTRA_BACK = "dev.cantacard.extra.PHOTO_BACK"
    }
}

@Composable
private fun PhotoScreen(back: Boolean, onShoot: (ImageCapture, onFailed: () -> Unit) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var torch by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        try {
            val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                android.util.Size(1920, 1080),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            ),
                        )
                        .build(),
                )
                .build()
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            capture = imageCapture
            awaitCancellation()
        } catch (e: IllegalArgumentException) {
            error = true
        } finally {
            provider.unbindAll()
        }
    }
    LaunchedEffect(camera, torch) { camera?.cameraControl?.enableTorch(torch) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
        CardFrame(back, Modifier.fillMaxSize())
        if (error) {
            Text(stringResource(R.string.camera_error), color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        TopControls(
            onClose = onClose,
            torch = torch.takeIf { camera?.cameraInfo?.hasFlashUnit() == true },
            onToggleTorch = { torch = !torch },
        )
        val shutterLabel = stringResource(R.string.photo_shutter)
        Surface(
            onClick = {
                val c = capture ?: return@Surface
                busy = true
                onShoot(c) { busy = false }
            },
            enabled = capture != null && !busy,
            shape = CircleShape,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 32.dp)
                .size(76.dp)
                .border(5.dp, Brand.Coral, CircleShape)
                .semantics { contentDescription = shutterLabel },
        ) {
            if (busy) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Brand.Teal700, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

/** Sagoma della tessera da riempire con la foto (il ritaglio fine si fa dopo). */
@Composable
private fun CardFrame(back: Boolean, modifier: Modifier = Modifier) {
    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            val width = size.width * 0.86f
            val height = width / CARD_RATIO
            val left = (size.width - width) / 2
            val top = (size.height - height) / 2
            val radius = CornerRadius(18.dp.toPx())
            drawRect(Color.Black.copy(alpha = 0.5f))
            drawRoundRect(Color.Transparent, Offset(left, top), Size(width, height), radius, blendMode = BlendMode.Clear)
            drawRoundRect(
                Color.White.copy(alpha = 0.9f), Offset(left, top), Size(width, height), radius,
                style = Stroke(width = 3.dp.toPx()),
            )
        }
        Text(
            text = stringResource(if (back) R.string.photo_hint_back else R.string.photo_hint),
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(top = 72.dp, start = 24.dp, end = 24.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.16f))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
