package dev.cantacard.data

/**
 * Negozio del catalogo. [domain] e [formatName] possono mancare: nel catalogo si lasciano vuoti
 * quando non sono certi (mai inventati).
 */
data class Store(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val domain: String? = null,
    val color: Int? = null,
    /** Nome dell'enum ZXing suggerito (es. `"EAN_13"`); il formato scansionato prevale. */
    val formatName: String? = null,
    val countries: List<String> = emptyList(),
) {
    val initial: String get() = Card.initialOf(name)

    /** Formato suggerito, se valido per questa versione dell'app. */
    val format: CodeFormat? get() = formatName?.let(CodeFormat::fromName)
}

/** Catalogo completo, con la sua versione (si usa la copia con versione più alta). */
class Catalog(val version: Int, val stores: List<Store>) {

    private val byId: Map<String, Store> = stores.associateBy { it.id }

    fun find(id: String?): Store? = id?.let(byId::get)

    companion object {
        val EMPTY = Catalog(0, emptyList())
    }
}
