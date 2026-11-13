package dev.cantacard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import dev.cantacard.MainActivity
import dev.cantacard.R
import dev.cantacard.ShowCardActivity
import dev.cantacard.app
import dev.cantacard.data.Card
import dev.cantacard.data.CardQuery
import dev.cantacard.image.Bitmaps
import dev.cantacard.image.CardBitmaps
import dev.cantacard.image.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Widget classici (`AppWidgetProvider` + `RemoteViews`, niente Glance): le tessere sono disegnate
 * come bitmap con [CardBitmaps]; un tocco apre la tessera a schermo intero.
 */
object Widgets {

    private const val SLOTS_PER_ROW = 4
    private const val SLOT_DP = 72
    private const val PREFS = "widgets"

    private val slotIds = intArrayOf(R.id.slot_0, R.id.slot_1, R.id.slot_2, R.id.slot_3, R.id.slot_4, R.id.slot_5, R.id.slot_6, R.id.slot_7)
    private val tileIds = intArrayOf(R.id.tile_0, R.id.tile_1, R.id.tile_2, R.id.tile_3, R.id.tile_4, R.id.tile_5, R.id.tile_6, R.id.tile_7)
    private val nameIds = intArrayOf(R.id.name_0, R.id.name_1, R.id.name_2, R.id.name_3, R.id.name_4, R.id.name_5, R.id.name_6, R.id.name_7)

    /** Ridisegna tutti i widget (fuori dal thread principale). */
    fun updateAll(context: Context, cards: List<Card>, images: ImageStore) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val decoded = HashMap<String, Bitmap?>()
        val imageOf = { card: Card -> decoded.getOrPut(card.id) { card.image?.let { Bitmaps.decodeFile(images.file(it)) } } }
        for (id in manager.getAppWidgetIds(ComponentName(context, FavoritesWidget::class.java))) {
            updateFavorites(context, manager, id, cards, imageOf)
        }
        for (id in manager.getAppWidgetIds(ComponentName(context, CardWidget::class.java))) {
            updateCard(context, manager, id, cards, imageOf)
        }
    }

    /** Dal sistema (aggiunta, ridimensionamento, aggiornamento dell'app): legge le tessere e ridisegna. */
    fun refresh(receiver: AppWidgetProvider, context: Context) {
        val app = context.app
        val pending = receiver.goAsync()
        app.appScope.launch(Dispatchers.IO) {
            try {
                updateAll(context, app.cards.cards.filterNotNull().first(), app.images)
            } finally {
                pending.finish()
            }
        }
    }

    fun setCardFor(context: Context, widgetId: Int, cardId: String) {
        prefs(context).edit().putString(key(widgetId), cardId).apply()
    }

    fun cardFor(context: Context, widgetId: Int): String? = prefs(context).getString(key(widgetId), null)

    fun forget(context: Context, widgetIds: IntArray) {
        val editor = prefs(context).edit()
        widgetIds.forEach { editor.remove(key(it)) }
        editor.apply()
    }

    private fun updateFavorites(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        cards: List<Card>,
        imageOf: (Card) -> Bitmap?,
    ) {
        val (widthDp, heightDp) = sizeDp(manager, widgetId, defaultWidth = 250, defaultHeight = 70)
        val columns = ((widthDp - 12) / SLOT_DP).coerceIn(1, SLOTS_PER_ROW)
        val rows = if (heightDp >= 130) 2 else 1
        val shown = CardQuery.sorted(cards).take(columns * rows)
        val views = RemoteViews(context.packageName, R.layout.widget_favorites)
        if (shown.isEmpty()) {
            views.setViewVisibility(R.id.empty, View.VISIBLE)
            views.setViewVisibility(R.id.row_0, View.GONE)
            views.setViewVisibility(R.id.row_1, View.GONE)
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
            manager.updateAppWidget(widgetId, views)
            return
        }
        views.setViewVisibility(R.id.empty, View.GONE)
        views.setViewVisibility(R.id.row_0, View.VISIBLE)
        views.setViewVisibility(R.id.row_1, if (shown.size > columns) View.VISIBLE else View.GONE)
        val density = context.resources.displayMetrics.density
        val tileWidth = (((widthDp - 12f) / columns - 8f) * density).roundToInt().coerceIn(60, 360)
        for (slot in slotIds.indices) {
            val row = slot / SLOTS_PER_ROW
            val column = slot % SLOTS_PER_ROW
            if (column >= columns || row >= rows) {
                views.setViewVisibility(slotIds[slot], View.GONE)
                continue
            }
            val card = shown.getOrNull(row * columns + column)
            if (card == null) {
                // Posto vuoto: resta lo spazio, così le tessere non si allargano.
                views.setViewVisibility(slotIds[slot], View.INVISIBLE)
                continue
            }
            views.setViewVisibility(slotIds[slot], View.VISIBLE)
            views.setImageViewBitmap(tileIds[slot], CardBitmaps.tile(card, imageOf(card), tileWidth, withName = false))
            views.setTextViewText(nameIds[slot], card.name)
            views.setContentDescription(slotIds[slot], card.name)
            views.setOnClickPendingIntent(slotIds[slot], openCard(context, card))
        }
        manager.updateAppWidget(widgetId, views)
    }

    private fun updateCard(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        cards: List<Card>,
        imageOf: (Card) -> Bitmap?,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_card)
        val card = cardFor(context, widgetId)?.let { id -> cards.firstOrNull { it.id == id } }
        if (card == null) {
            views.setViewVisibility(R.id.card, View.GONE)
            views.setViewVisibility(R.id.missing, View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        } else {
            val (widthDp, heightDp) = sizeDp(manager, widgetId, defaultWidth = 180, defaultHeight = 110)
            val density = context.resources.displayMetrics.density
            val fit = minOf(widthDp.toFloat(), heightDp * 1.586f)
            val width = (fit * density).roundToInt().coerceIn(120, 720)
            views.setViewVisibility(R.id.missing, View.GONE)
            views.setViewVisibility(R.id.card, View.VISIBLE)
            views.setImageViewBitmap(R.id.card, CardBitmaps.tile(card, imageOf(card), width, withName = true))
            views.setContentDescription(R.id.card, card.name)
            views.setOnClickPendingIntent(R.id.widget_root, openCard(context, card))
        }
        manager.updateAppWidget(widgetId, views)
    }

    /** Dimensione in verticale (larghezza minima, altezza massima), con valori di riserva. */
    private fun sizeDp(manager: AppWidgetManager, widgetId: Int, defaultWidth: Int, defaultHeight: Int): Pair<Int, Int> {
        val options = manager.getAppWidgetOptions(widgetId)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: defaultWidth
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: defaultHeight
        return width to height
    }

    private fun openCard(context: Context, card: Card): PendingIntent {
        // L'uri rende ogni PendingIntent diverso (gli extra da soli non bastano).
        val intent = ShowCardActivity.intent(context, card.id)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("cantacard://card/${card.id}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(widgetId: Int) = "card_$widgetId"
}

/** Widget delle tessere preferite (poi le più usate), quante ne stanno. */
class FavoritesWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refresh(this, context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) =
        Widgets.refresh(this, context)
}

/** Widget di una sola tessera, scelta quando lo si aggiunge ([CardWidgetConfigActivity]). */
class CardWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refresh(this, context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) =
        Widgets.refresh(this, context)

    override fun onDeleted(context: Context, ids: IntArray) = Widgets.forget(context, ids)
}
