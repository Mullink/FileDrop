package com.liquorbee.mobile.pricecheck

import com.liquorbee.mobile.core.network.PriceCheckEditDto
import java.math.BigDecimal
import java.math.RoundingMode

internal fun priceFromPercentage(cost: String, percent: String, margin: Boolean): String? {
    val c = cost.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: return null
    val p = percent.toBigDecimalOrNull() ?: return null
    val factor = p.divide(BigDecimal(100))
    val price = if (margin) {
        if (p >= BigDecimal(100)) return null
        c.divide(BigDecimal.ONE - factor, 2, RoundingMode.HALF_UP)
    } else (c * (BigDecimal.ONE + factor)).setScale(2, RoundingMode.HALF_UP)
    return price.takeIf { it > BigDecimal.ZERO }?.toPlainString()
}

internal fun pricePercentage(price: String, cost: String, margin: Boolean): String {
    val p = price.toBigDecimalOrNull() ?: return ""
    val c = cost.toBigDecimalOrNull() ?: return ""
    val divisor = if (margin) p else c
    if (divisor <= BigDecimal.ZERO) return ""
    return ((p - c) * BigDecimal(100)).divide(divisor, 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

internal data class PriceCheckDraft(
    val name: String, val upc: String, val sku: String, val description: String,
    val price: String, val cost: String, val quantity: String, val vendorId: String, val categoryId: String,
    val active: Boolean, val tracking: Boolean, val markup: String, val margin: String, val percentageError: String = ""
) {
    companion object {
        fun from(item: PriceCheckEditDto) = PriceCheckDraft(item.name, item.upc, item.sku, item.description,
            item.price.toString(), item.cost.toString(), item.quantity.toString(), item.vendorId, item.categoryId,
            item.status == 1, item.trackInventory == 1,
            pricePercentage(item.price.toString(), item.cost.toString(), false), pricePercentage(item.price.toString(), item.cost.toString(), true))
    }
    fun isDirty(item: PriceCheckEditDto) = name != item.name || upc != item.upc || sku != item.sku || description != item.description ||
        price.toDoubleOrNull() != item.price || cost.toDoubleOrNull() != item.cost || quantity.toIntOrNull() != item.quantity ||
        vendorId != item.vendorId || categoryId != item.categoryId || active != (item.status == 1) || tracking != (item.trackInventory == 1) || percentageError.isNotEmpty()

    fun withPricing(price: String = this.price, cost: String = this.cost) = copy(price = price, cost = cost,
        markup = pricePercentage(price, cost, false), margin = pricePercentage(price, cost, true), percentageError = "")

    fun withPercent(value: String, isMargin: Boolean): PriceCheckDraft {
        val next = priceFromPercentage(cost, value, isMargin)
        if (next == null) return if (isMargin) copy(margin = value, percentageError = "Use a positive cost and margin below 100%.")
            else copy(markup = value, percentageError = "Use a positive cost and markup above -100%.")
        return if (isMargin) copy(price = next, margin = value, markup = pricePercentage(next, cost, false), percentageError = "")
            else copy(price = next, markup = value, margin = pricePercentage(next, cost, true), percentageError = "")
    }

    fun error(item: PriceCheckEditDto): String? {
        val p = price.toBigDecimalOrNull()
        val c = cost.toBigDecimalOrNull()
        return when {
            name.isBlank() || name.length > 200 -> "Enter an item name, up to 200 characters."
            sku.isBlank() || sku.length > 50 -> "Enter a SKU, up to 50 characters."
            upc.length > 100 -> "UPC must be at most 100 characters."
            description.length > 2000 -> "Description must be at most 2,000 characters."
            categoryId.isBlank() && categoryId != item.categoryId -> "Choose a category."
            p == null || p <= BigDecimal.ZERO || p > BigDecimal("99999999.99") || (p.toDouble() != item.price && p.stripTrailingZeros().scale() > 2) -> "Enter a positive price with at most two decimal places."
            c == null || c < BigDecimal.ZERO || c > BigDecimal("99999999.99") || (c.toDouble() != item.cost && c.stripTrailingZeros().scale() > 2) -> "Enter a non-negative cost with at most two decimal places."
            quantity.toIntOrNull() == null -> "Quantity must be a whole number."
            percentageError.isNotEmpty() -> percentageError
            else -> null
        }
    }

    fun request(item: PriceCheckEditDto): PriceCheckEditDto {
        require(error(item) == null)
        return item.copy(name = name.trim(), upc = upc.trim(), sku = sku.trim(), description = description,
            price = price.toDouble(), cost = cost.toDouble(), quantity = quantity.toInt(), vendorId = vendorId,
            categoryId = categoryId, status = if (active) 1 else 0, trackInventory = if (tracking) 1 else 0)
    }
}
