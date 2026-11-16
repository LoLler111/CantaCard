package dev.cantacard.data

/**
 * Unione tra lo stato del telefono e quello del file di sincronizzazione (funzioni pure, testate
 * in JVM). Il risultato si scrive sia sul telefono sia nel file, così i due convergono.
 *
 * Regole:
 * 1. stessa tessera (stesso id): vince la versione con `updatedAt` più recente, a parità il
 *    telefono; ultima apertura e conteggio d'uso si uniscono prendendo il massimo;
 * 2. tessere cancellate: l'elenco è l'unione dei due; una cancellazione vince se è avvenuta dopo
 *    l'ultima modifica della tessera (altrimenti la tessera è stata modificata dopo e resta);
 * 3. la stessa tessera fisica aggiunta su due telefoni (id diversi, stesso numero e formato, una
 *    nuova da ciascuna parte): ne resta una, la creata prima; l'altra diventa cancellata.
 */
object SyncMerge {

    class Result(
        val snapshot: CardSnapshot,
        /** Tessere la cui versione (e quindi l'immagine) arriva dal file. */
        val fromRemote: Set<String>,
    )

    fun merge(local: CardSnapshot, remote: CardSnapshot, now: Long): Result {
        val deleted = HashMap(local.deleted)
        remote.deleted.forEach { (id, at) -> deleted[id] = maxOf(at, deleted[id] ?: 0L) }

        val remoteById = remote.cards.associateBy { it.id }
        val localIds = local.cards.mapTo(HashSet()) { it.id }
        val merged = LinkedHashMap<String, Card>()
        val fromRemote = HashSet<String>()
        for (mine in local.cards) {
            val theirs = remoteById[mine.id]
            merged[mine.id] = when {
                theirs == null -> mine
                theirs.updatedAt > mine.updatedAt -> {
                    fromRemote += mine.id
                    theirs.withStatsOf(mine)
                }
                else -> mine.withStatsOf(theirs)
            }
        }
        for (theirs in remote.cards) {
            if (theirs.id !in merged) {
                merged[theirs.id] = theirs
                fromRemote += theirs.id
            }
        }

        // Cancellazioni.
        val iterator = merged.values.iterator()
        while (iterator.hasNext()) {
            val card = iterator.next()
            val deletedAt = deleted[card.id] ?: continue
            if (deletedAt >= card.updatedAt) {
                iterator.remove()
                fromRemote -= card.id
            } else {
                deleted -= card.id
            }
        }

        // Stessa tessera aggiunta su entrambi i telefoni.
        val localOnly = localIds - remoteById.keys
        val remoteOnly = remoteById.keys - localIds
        merged.values
            .filter { it.id in localOnly || it.id in remoteOnly }
            .groupBy(::codeKey)
            .values
            .filter { group -> group.any { it.id in localOnly } && group.any { it.id in remoteOnly } }
            .forEach { group ->
                val keep = group.minWith(compareBy<Card>({ it.createdAt }, { it.id }))
                for (card in group) {
                    if (card.id == keep.id) continue
                    merged -= card.id
                    fromRemote -= card.id
                    deleted[card.id] = maxOf(now, card.updatedAt)
                }
            }

        return Result(CardSnapshot(merged.values.toList(), deleted), fromRemote)
    }

    /**
     * `true` se i due stati hanno lo stesso contenuto, ignorando l'ordine e il nome dei file
     * immagine (diverso tra telefono e file): serve per non riscrivere il file senza motivo.
     */
    fun sameContent(a: CardSnapshot, b: CardSnapshot): Boolean =
        a.deleted == b.deleted && normalized(a.cards) == normalized(b.cards)

    private fun normalized(cards: List<Card>): List<Card> =
        cards.map {
            it.copy(image = if (it.image != null) "*" else null, backImage = if (it.backImage != null) "*" else null)
        }.sortedBy { it.id }

    private fun Card.withStatsOf(other: Card): Card =
        copy(lastUsed = maxOf(lastUsed, other.lastUsed), useCount = maxOf(useCount, other.useCount))

    private fun codeKey(card: Card): String = card.formatName + "|" + card.number.trim()
}
