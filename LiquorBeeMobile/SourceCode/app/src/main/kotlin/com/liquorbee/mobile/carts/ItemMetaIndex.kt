package com.liquorbee.mobile.carts

import com.liquorbee.mobile.core.network.ItemMetaDataDto
import com.liquorbee.mobile.core.network.SubCustomerCatalogItemDto

/**
 * Looks up a catalog item's metadata row. The order catalog has no ItemId, so match on barcode
 * (leading zeros ignored - stored as both 12 and 13 digits) and fall back to the exact item name.
 * When several rows share a key, prefer one that actually has a size.
 */
class ItemMetaIndex(rows: List<ItemMetaDataDto>) {
    private fun barcodeKey(value: String?): String? =
        value?.filter { it.isDigit() }?.trimStart('0')?.takeIf { it.length >= 6 }
    private fun nameKey(value: String?): String? = value?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

    private val preferSized = compareByDescending<ItemMetaDataDto> { !it.size.isNullOrBlank() }
    private val byBarcode = rows.filter { barcodeKey(it.barcode) != null }.groupBy { barcodeKey(it.barcode)!! }
        .mapValues { (_, v) -> v.sortedWith(preferSized).first() }
    private val byName = rows.filter { nameKey(it.itemName) != null }.groupBy { nameKey(it.itemName)!! }
        .mapValues { (_, v) -> v.sortedWith(preferSized).first() }

    fun find(item: SubCustomerCatalogItemDto): ItemMetaDataDto? {
        val byCode = barcodeKey(item.upc)?.let { byBarcode[it] }
        // A barcode hit for a differently-named row is still the same POS item (renames happen);
        // only fall back to the name when the barcode found nothing.
        return byCode ?: nameKey(item.itemName)?.let { byName[it] }
    }
}
