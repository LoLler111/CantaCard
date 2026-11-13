package dev.cantacard.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import dev.cantacard.data.Card
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Le tessere disegnate come bitmap, con lo stesso aspetto dell'app (tinta piatta, angoli
 * arrotondati, icona nel tondo bianco o iniziale): servono alle scorciatoie e ai widget, che non
 * possono usare Compose.
 */
object CardBitmaps {

    private const val CARD_RATIO = 1.586f

    /** Tessera larga [width] px; con [withName] il nome in basso a sinistra (se non c'è una foto). */
    fun tile(card: Card, image: Bitmap?, width: Int, withName: Boolean): Bitmap {
        val height = (width / CARD_RATIO).roundToInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val corner = width * 0.08f
        canvas.clipPath(Path().apply { addRoundRect(rect, corner, corner, Path.Direction.CW) })
        canvas.drawColor(card.color or 0xFF000000.toInt())

        if (image != null && ImageStore.isPhoto(image.width, image.height)) {
            drawCrop(canvas, image, rect)
            return bitmap
        }
        val margin = width * 0.065f
        val mark = height * 0.34f
        drawMark(canvas, card, image, RectF(margin, margin, margin + mark, margin + mark))
        if (withName) {
            val paint = textPaint(onColor(card.color), height * 0.135f)
            val name = TextUtils.ellipsize(card.name, paint, width - 2 * margin, TextUtils.TruncateAt.END)
            canvas.drawText(name, 0, name.length, margin, height - margin, paint)
        }
        return bitmap
    }

    /**
     * Icona adattiva delle scorciatoie: [size] px pieni (108 dp), con il contenuto importante nel
     * cerchio centrale (72 dp), perché il launcher ritaglia la forma.
     */
    fun shortcutIcon(card: Card, image: Bitmap?, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val full = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawColor(card.color or 0xFF000000.toInt())
        if (image != null && ImageStore.isPhoto(image.width, image.height)) {
            drawCrop(canvas, image, full)
            return bitmap
        }
        val inner = size * 0.5f
        val offset = (size - inner) / 2f
        drawMark(canvas, card, image, RectF(offset, offset, offset + inner, offset + inner), initialScale = 0.62f)
        return bitmap
    }

    /** Icona nel tondo bianco, oppure iniziale su un tondo chiaro trasparente (come nell'app). */
    private fun drawMark(canvas: Canvas, card: Card, image: Bitmap?, box: RectF, initialScale: Float = 0.5f) {
        val circle = Paint(Paint.ANTI_ALIAS_FLAG)
        if (image != null) {
            circle.color = Color.WHITE
            canvas.drawOval(box, circle)
            val pad = box.width() * 0.14f
            val target = RectF(box.left + pad, box.top + pad, box.right - pad, box.bottom - pad)
            drawFit(canvas, image, target)
            return
        }
        val on = onColor(card.color)
        circle.color = on
        circle.alpha = 0x33
        canvas.drawOval(box, circle)
        val paint = textPaint(on, box.height() * initialScale)
        val bounds = Rect()
        val letter = card.initial
        paint.getTextBounds(letter, 0, letter.length, bounds)
        canvas.drawText(
            letter,
            box.centerX() - paint.measureText(letter) / 2f,
            box.centerY() + bounds.height() / 2f,
            paint,
        )
    }

    private fun drawFit(canvas: Canvas, image: Bitmap, target: RectF) {
        val scale = minOf(target.width() / image.width, target.height() / image.height)
        val w = image.width * scale
        val h = image.height * scale
        val dst = RectF(target.centerX() - w / 2, target.centerY() - h / 2, target.centerX() + w / 2, target.centerY() + h / 2)
        canvas.drawBitmap(image, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    private fun drawCrop(canvas: Canvas, image: Bitmap, target: RectF) {
        val scale = max(target.width() / image.width, target.height() / image.height)
        val w = target.width() / scale
        val h = target.height() / scale
        val left = ((image.width - w) / 2).roundToInt()
        val top = ((image.height - h) / 2).roundToInt()
        val src = Rect(left, top, (left + w).roundToInt(), (top + h).roundToInt())
        canvas.drawBitmap(image, src, target, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    private fun textPaint(color: Int, size: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    /** Testo bianco sulle tinte scure, quasi nero su quelle chiare (stessa soglia di `contentColorOn`). */
    private fun onColor(background: Int): Int {
        val r = Color.red(background) / 255.0
        val g = Color.green(background) / 255.0
        val b = Color.blue(background) / 255.0
        fun lin(c: Double) = if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        val luminance = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
        return if (luminance > 0.55) 0xFF111111.toInt() else Color.WHITE
    }
}
