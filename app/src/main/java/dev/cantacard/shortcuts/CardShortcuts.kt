package dev.cantacard.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.util.Log
import dev.cantacard.R
import dev.cantacard.app
import dev.cantacard.ShowCardActivity
import dev.cantacard.data.Card
import dev.cantacard.data.CardQuery
import dev.cantacard.image.Bitmaps
import dev.cantacard.image.CardBitmaps
import dev.cantacard.image.ImageStore
import kotlin.math.roundToInt

/**
 * Scorciatoie dell'app (tenendo premuta l'icona): le preferite, poi le più usate di recente
 * (scelta dell'utente), e quelle fissate in Home con "Aggiungi alla schermata Home".
 * L'id di ogni scorciatoia è l'id della tessera, così dinamiche e fissate si aggiornano insieme.
 * Solo API del framework (ShortcutManager, API 25+).
 */
object CardShortcuts {

    private const val MAX_DYNAMIC = 4
    private const val TAG = "CardShortcuts"

    /** Allinea le scorciatoie alle tessere (da chiamare fuori dal thread principale). */
    fun update(context: Context, cards: List<Card>, images: ImageStore) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        try {
            val max = minOf(MAX_DYNAMIC, manager.maxShortcutCountPerActivity)
            val chosen = CardQuery.quickAccess(cards, context.app.listPrefs.favoritesOrder.value).take(max)
            manager.dynamicShortcuts = chosen.mapIndexed { rank, card -> build(context, card, images, rank) }

            // Scorciatoie fissate in Home: aggiornate (nome, icona) o disattivate se la tessera non c'è più
            // (un'archiviata resta: si può ancora aprire, come dalla lista delle archiviate).
            val byId = cards.associateBy { it.id }
            val pinned = manager.pinnedShortcuts
            val revived = pinned.filter { !it.isEnabled && it.id in byId }.map { it.id }
            if (revived.isNotEmpty()) manager.enableShortcuts(revived)
            val existing = pinned.mapNotNull { byId[it.id] }.map { build(context, it, images, 0) }
            if (existing.isNotEmpty()) manager.updateShortcuts(existing)
            val gone = pinned.filter { it.isEnabled && it.id !in byId }.map { it.id }
            if (gone.isNotEmpty()) manager.disableShortcuts(gone, context.getString(R.string.shortcut_card_deleted))
        } catch (e: IllegalStateException) {
            // Limite di frequenza del sistema (app in background): si riprova alla prossima modifica.
            Log.w(TAG, "Scorciatoie non aggiornate", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Scorciatoie non aggiornate", e)
        }
    }

    fun canPin(context: Context): Boolean =
        context.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    /** Chiede al launcher di mettere in Home un'icona che apre [card] (il sistema chiede conferma). */
    fun requestPin(context: Context, card: Card, images: ImageStore): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        return try {
            manager.isRequestPinShortcutSupported && manager.requestPinShortcut(build(context, card, images, 0), null)
        } catch (e: IllegalStateException) {
            false
        }
    }

    /** Aiuta il launcher a proporre le tessere usate davvero. */
    fun reportUsed(context: Context, cardId: String) {
        context.getSystemService(ShortcutManager::class.java)?.reportShortcutUsed(cardId)
    }

    private fun build(context: Context, card: Card, images: ImageStore, rank: Int): ShortcutInfo {
        val image = card.image?.let { Bitmaps.decodeFile(images.file(it)) }
        val size = (108 * context.resources.displayMetrics.density).roundToInt().coerceAtMost(432)
        val label = card.name.ifBlank { card.number }
        return ShortcutInfo.Builder(context, card.id)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(Icon.createWithAdaptiveBitmap(CardBitmaps.shortcutIcon(card, image, size)))
            .setIntent(ShowCardActivity.intent(context, card.id).setAction(Intent.ACTION_VIEW))
            .setRank(rank)
            .build()
    }
}
