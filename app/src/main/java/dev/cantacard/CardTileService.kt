package dev.cantacard

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.cantacard.data.CardQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Riquadro "Tessere" nelle impostazioni rapide: un tocco dalla tendina e si apre la prima tessera
 * (preferite in cima), poi si scorre alle altre. Sotto il nome mostra quale tessera si aprirà.
 * Solo API del framework (TileService, API 24+).
 */
class CardTileService : TileService() {

    private var subtitleJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.updateTile()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        // Le tessere si leggono in background; se arrivano dopo che la tendina si è chiusa, niente.
        subtitleJob = app.appScope.launch(Dispatchers.Main) {
            val cards = app.cards.cards.filterNotNull().first()
            val first = CardQuery.sorted(cards.filterNot { it.archived }, app.listPrefs.sortOrder.value).firstOrNull()
            qsTile?.apply {
                subtitle = first?.name ?: getString(R.string.tile_subtitle_empty)
                updateTile()
            }
        }
    }

    override fun onStopListening() {
        subtitleJob?.cancel()
        subtitleJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        // Con il telefono bloccato si chiede prima lo sblocco: le tessere non si mostrano sopra il blocco.
        if (isLocked) unlockAndRun(::open) else open()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun open() {
        val intent = ShowCardActivity.first(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
