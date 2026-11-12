package dev.cantacard.data

/** Colori delle tessere senza immagine e conversioni da/verso `#RRGGBB`. */
object CardColors {

    /** Tavolozza di ripiego, scelta in modo stabile dal nome della tessera. */
    val palette: List<Int> = listOf(
        0xFFC62828, 0xFFAD1457, 0xFF6A1B9A, 0xFF4527A0,
        0xFF283593, 0xFF1565C0, 0xFF00838F, 0xFF00695C,
        0xFF2E7D32, 0xFF9E9D24, 0xFFEF6C00, 0xFF4E342E,
    ).map { it.toInt() }

    fun forName(name: String): Int {
        val key = name.trim().lowercase()
        return palette[Math.floorMod(key.hashCode(), palette.size)]
    }

    /** `#RRGGBB` se opaco, altrimenti `#AARRGGBB`. */
    fun toHex(color: Int): String =
        if (color ushr 24 == 0xFF) "#%06X".format(color and 0xFFFFFF)
        else "#%08X".format(color)

    /** Accetta `#RRGGBB` o `#AARRGGBB` (il `#` è facoltativo); `null` se non valido. */
    fun parse(text: String?): Int? {
        val hex = text?.trim()?.removePrefix("#") ?: return null
        val value = hex.toLongOrNull(16) ?: return null
        return when (hex.length) {
            6 -> (0xFF000000 or value).toInt()
            8 -> value.toInt()
            else -> null
        }
    }
}
