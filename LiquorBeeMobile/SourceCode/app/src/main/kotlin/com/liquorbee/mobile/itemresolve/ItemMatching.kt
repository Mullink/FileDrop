package com.liquorbee.mobile.itemresolve

/**
 * Fuzzy name matching between a master-catalog product name (e.g. "OLD KENTUCKY TAVERN (PET)
 * (750ml) - 19366") and the store's own POS item names, which are typed inconsistently
 * ("100 PIPERS 1.75L 9368", "100 PIPERS (1.75L) - 9368"). Used to spot a POS item that is
 * really the scanned product but is missing (or has a different) UPC.
 */
object ItemMatching {
    private val STOP_WORDS = setOf("THE", "OF", "AND", "W", "WITH", "A", "PET", "ML", "L", "LT", "OZ", "PROOF", "PRF", "PR")

    /** Minimum score for a POS item to be offered as a likely match. */
    const val CANDIDATE_THRESHOLD = 0.45
    private val SIZE_REGEX = Regex("""(\d+(?:\.\d+)?)\s*(ML|LTR|LT|L|OZ)\b""")

    data class Normalized(val tokens: Set<String>, val size: String?)

    /** Size as a comparable string in ml ("750", "1750"), or null when the name has none. */
    // Store-typed names often drop the unit: "PARROT BAY COCONUT 750", "TITOS HANDMADE VODKA 1.75".
    private val BARE_ML = setOf("50", "100", "200", "375", "750")
    private val BARE_L = setOf("1.75", "1.5", "1.0")

    fun size(name: String): String? {
        val upper = name.uppercase()
        val m = SIZE_REGEX.find(upper) ?: run {
            val bare = upper.split(Regex("[^0-9.]+")).lastOrNull { it in BARE_ML || it in BARE_L } ?: return null
            return if (bare in BARE_ML) bare else Math.round(bare.toDouble() * 1000).toString()
        }
        val value = m.groupValues[1].toDoubleOrNull() ?: return null
        val ml = when (m.groupValues[2]) {
            "ML" -> value
            "OZ" -> value * 29.5735
            else -> value * 1000 // L / LT / LTR
        }
        return Math.round(ml).toString()
    }

    /** [fallbackSize] (e.g. the item's metadata size, "1.75L") is used only when the name itself has none. */
    fun normalize(name: String, ownCode: String? = null, fallbackSize: String? = null): Normalized {
        val upper = name.uppercase()
        val size = size(upper) ?: fallbackSize?.let { size(it) }
        val withoutSize = SIZE_REGEX.replace(upper, " ").split(" ")
            .filterNot { it in BARE_ML || it in BARE_L }.joinToString(" ")
        val code = ownCode?.trim()?.uppercase()
        val tokens = withoutSize.split(Regex("[^A-Z0-9]+"))
            .filter { it.isNotBlank() && it !in STOP_WORDS && it != code }
            .toSet()
        return Normalized(tokens, size)
    }

    /**
     * 0..1 similarity: the better of token overlap (Jaccard) and how much of the shorter name is
     * covered (so an extra brand prefix like "CAPTAIN MORGAN PARROT BAY COCONUT" still matches
     * "PARROT BAY COCONUT"), with partial credit for one token being a prefix of the other
     * ("CAB" / "CABERNET"). A size mismatch cuts the score hard - same brand, different bottle is
     * a different item.
     */
    fun score(a: Normalized, b: Normalized): Double {
        if (a.tokens.isEmpty() || b.tokens.isEmpty()) return 0.0
        var matched = 0.0
        val remaining = b.tokens.toMutableSet()
        for (t in a.tokens) {
            if (remaining.remove(t)) { matched += 1.0; continue }
            val partial = remaining.firstOrNull { o -> t.length >= 3 && o.length >= 3 && (o.startsWith(t) || t.startsWith(o)) }
            if (partial != null) { remaining.remove(partial); matched += 0.7 }
        }
        val union = a.tokens.size + b.tokens.size - matched
        val jaccard = if (union <= 0) 0.0 else matched / union
        val shorter = minOf(a.tokens.size, b.tokens.size)
        // Coverage only counts for 2+ word names - "VODKA" alone shouldn't match every vodka.
        val containment = if (shorter >= 2) 0.85 * matched / shorter else 0.0
        var s = maxOf(jaccard, containment)
        if (a.size != null && b.size != null && a.size != b.size) s *= 0.4
        return s
    }

    /** POS UPCs like "MISSING-10001290" (or blank) mean the item has no real barcode yet. */
    fun isMissingUpc(upc: String?): Boolean =
        upc.isNullOrBlank() || upc.trim().startsWith("MISSING", ignoreCase = true) || upc.none { it.isDigit() }

    /** A value that looks like a scanned barcode (vs. a typed name or SKU). */
    fun looksLikeBarcode(value: String): Boolean {
        val v = value.trim()
        return v.length >= 8 && v.all { it.isDigit() }
    }
}
