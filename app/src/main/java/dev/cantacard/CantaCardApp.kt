package dev.cantacard

import android.app.Application
import android.content.Context
import dev.cantacard.data.CardRepository
import dev.cantacard.data.CardStore
import dev.cantacard.data.CatalogRepository
import dev.cantacard.image.ImageStore
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

    /** Letto solo quando serve (scelta del negozio, modulo), non all'avvio. */
    val catalog: CatalogRepository by lazy { CatalogRepository(this, appScope) }

    override fun onCreate() {
        super.onCreate()
        // Avvia subito la lettura in background, così la lista è pronta al primo frame utile.
        cards
    }
}

val Context.app: CantaCardApp get() = applicationContext as CantaCardApp
