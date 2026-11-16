package dev.cantacard

import android.app.Application
import android.content.Context
import dev.cantacard.data.Backup
import dev.cantacard.data.Sync
import dev.cantacard.data.CardRepository
import dev.cantacard.data.CardStore
import dev.cantacard.data.CatalogRepository
import dev.cantacard.image.ImageStore
import dev.cantacard.update.AppUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Contenitore delle dipendenze (DI manuale, niente framework). */
class CantaCardApp : Application() {

    val appScope = CoroutineScope(SupervisorJob())

    val cards: CardRepository by lazy {
        CardRepository(CardStore(File(filesDir, "cards.json")), appScope)
    }

    /** File delle immagini delle tessere (definitive e provvisorie). */
    val images: ImageStore by lazy { ImageStore(this) }

    /** Backup e ripristino su file (creato solo quando si usa il menu). */
    val backup: Backup by lazy { Backup(this, cards, images) }

    /** Sincronizzazione tra telefoni (creata alla prima apertura della lista, fuori dal thread principale). */
    val sync: Sync by lazy { Sync(this, cards, images, appScope) }

    /** Scorciatoie e widget allineati alle tessere. */
    private val quickAccess by lazy { QuickAccess(this, cards, images, appScope) }

    /** Letto solo quando serve (scelta del negozio, modulo), non all'avvio. */
    val catalog: CatalogRepository by lazy { CatalogRepository(this, appScope) }

    /** Controllo degli aggiornamenti dell'app (GitHub Releases). */
    val updater: AppUpdater by lazy { AppUpdater(appScope, cacheDir) }

    override fun onCreate() {
        super.onCreate()
        // Avvia subito la lettura in background, così la lista è pronta al primo frame utile.
        cards
        quickAccess.start()
    }
}

val Context.app: CantaCardApp get() = applicationContext as CantaCardApp
