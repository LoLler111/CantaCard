package dev.cantacard

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.cantacard.barcode.BarcodeDecoder
import dev.cantacard.barcode.ScannedCode
import dev.cantacard.image.Bitmaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Riceve un'immagine condivisa da un'altra app (`ACTION_SEND image/...`), senza interfaccia:
 * la copia tra i file provvisori, prova a leggerci un codice e apre il modulo di una nuova
 * tessera con l'immagine da ritagliare.
 */
class ShareImageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val uri = sharedUri()
        if (uri == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { prepare(uri) }
            if (result == null) {
                Toast.makeText(this@ShareImageActivity, R.string.share_unreadable, Toast.LENGTH_SHORT).show()
            } else {
                startActivity(EditCardActivity.addFromImage(this@ShareImageActivity, result.first, result.second))
            }
            finish()
        }
    }

    /** Percorso del file provvisorio e codice letto (se c'è); `null` se l'immagine non si apre. */
    private fun prepare(uri: Uri): Pair<String, ScannedCode?>? {
        val bitmap = Bitmaps.decode(this, uri) ?: return null
        val file = app.images.newTemp("jpg")
        try {
            Bitmaps.writeJpeg(bitmap, file)
        } catch (e: IOException) {
            return null
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return file.path to BarcodeDecoder.decodeImage(pixels, bitmap.width, bitmap.height)
    }

    private fun sharedUri(): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}
