package dev.cantacard.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.PlanarYUVLuminanceSource
import dev.cantacard.barcode.BarcodeDecoder
import dev.cantacard.barcode.ScannedCode

/**
 * Analizza i frame della fotocamera con ZXing. Un frame su due viene provato ruotato di 90°,
 * così si legge anche una tessera tenuta di traverso. Chiama [onCode] una sola volta.
 */
class BarcodeAnalyzer(private val onCode: (ScannedCode) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = BarcodeDecoder.newReader(tryHarder = false)
    private val confirmer = ScanConfirmer()
    private var plane = ByteArray(0)
    private var rotated = ByteArray(0)
    private var frame = 0L

    @Volatile
    private var done = false

    override fun analyze(image: ImageProxy) {
        image.use {
            if (done) return
            val yPlane = image.planes[0]
            val buffer = yPlane.buffer
            if (plane.size < buffer.remaining()) plane = ByteArray(buffer.remaining())
            buffer.get(plane, 0, buffer.remaining())

            val extra = if (frame++ % 2 == 1L) 90 else 0
            val luma = Luma.rotate(
                plane = plane,
                width = image.width,
                height = image.height,
                rowStride = yPlane.rowStride,
                degrees = image.imageInfo.rotationDegrees + extra,
                out = rotated,
            )
            rotated = luma.data
            val source = PlanarYUVLuminanceSource(
                luma.data, luma.width, luma.height, 0, 0, luma.width, luma.height, false,
            )
            val code = BarcodeDecoder.decode(reader, source) ?: return
            if (confirmer.offer(code)) {
                done = true
                onCode(code)
            }
        }
    }
}
