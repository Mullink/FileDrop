package com.liquorbee.mobile.core.network

import kotlinx.serialization.Serializable

@Serializable
data class TopSellerItemDto(
    val name: String? = null,
    val totalSold: Int = 0
)

@Serializable
data class TopSellersDto(
    val startDate: String? = null,
    val endDate: String? = null,
    val items: List<TopSellerItemDto> = emptyList()
)

@Serializable
data class DashboardSummaryDto(
    val grossSale: Double? = null,
    val netCreditSale: Double? = null,
    val netCashSale: Double? = null,
    val taxCollected: Double? = null,
    val netTotal: Double? = null
)

@Serializable
data class DashboardResponseDto(
    val summary: DashboardSummaryDto? = null
)

// Only the one field this app needs (the profile-menu store name) - the real
// Profile/GetAccountSettings response carries plenty else, kotlinx.serialization just ignores
// whatever isn't declared here.
@Serializable
data class AccountSettingsDto(
    val storeName: String? = null
)

@Serializable
data class CardPricingSettingsDto(
    val pricingByCardPrice: Boolean = false,
    val nickelRounding: Boolean = false,
    val dualPricing: Double = 1.0
)

@Serializable
data class InventoryCategoryDto(
    val categoryId: String,
    val name: String? = null
)

@Serializable
data class LabelPrintingItemDto(
    val itemCode: String,
    val itemName: String? = null,
    val upc: String? = null,
    val qtyOnHand: Int = 0,
    val itemPrice: Double = 0.0,
    val printed: Int = 0,
    val cashPrice: Double = 0.0,
    val cardPrice: Double = 0.0,
    val labelPrinterFormat: String? = null,
    val labelFormat: String? = null
)

@Serializable
data class PrintLabelRequestItem(
    val itemCode: String,
    val itemName: String?,
    val displayName: String?,
    val isSelected: Boolean = true,
    val upc: String?,
    val sync: Boolean,
    val labelFormat: String = "",
    val labelPrinterFormat: String = ""
)

@Serializable
data class InventorySessionHeaderDto(
    val sessionId: Int,
    val itemCountBefore: Int = 0,
    val itemCountAfter: Int? = null,
    val totalBeforeUnits: Int = 0,
    val totalAfterUnits: Int? = null,
    val inventoryAmountTotalBefore: Double = 0.0,
    val inventoryAmountTotalAfter: Double? = null,
    val sessionStartDateCst: String? = null,
    val status: String? = null,
    val isCategorySession: Boolean = false,
    val sessionName: String? = null,
    val createdBy: String? = null,
    val countedItems: Int? = null
)

@Serializable
data class CreateCategorySessionRequest(
    val categoryIds: List<String>,
    val sessionName: String,
    val createdBy: String
)

@Serializable
data class ReviewSessionItemDto(
    val itemId: String,
    val itemName: String? = null,
    val itemCode: String? = null,
    val barcode: String? = null,
    val qtyBefore: Int = 0,
    val qtyAfter: Int = 0,
    val price: Double = 0.0,
    val cog: Double = 0.0
)

@Serializable
data class CategorySessionItemRequest(
    val itemId: String,
    val afterQty: Int
)

@Serializable
data class SaveCategorySessionCountsRequest(
    val sessionId: Int,
    val items: List<CategorySessionItemRequest>,
    val moveToReview: Boolean
)

@Serializable
data class InventorySessionItemNameDto(
    val itemId: String,
    val itemName: String? = null,
    val currentQty: Int = 0,
    val itemCode: String? = null,
    val barcode: String? = null
)

@Serializable
data class LogItemInventorySessionRequest(
    val itemId: String,
    val sessionId: Int,
    val action: String,
    val quantity: Int,
    val addedBy: String
)

@Serializable
data class CreateNewItemInSessionRequest(
    val sessionId: Int,
    val itemName: String,
    val itemCode: String,
    val barcode: String
)

@Serializable
data class InventoryHistoryDto(
    val itemId: String,
    val itemName: String? = null,
    val qtyAdjustment: Int = 0,
    val addedBy: String? = null
)

@Serializable
data class ReviewSessionItemDetailDto(
    val itemId: String,
    val itemName: String? = null,
    val itemCode: String? = null,
    val barcode: String? = null,
    val qtyMatched: Int = 0,
    val newItem: Int = 0,
    val qtyDelta: Int = 0,
    val qtyBefore: Int = 0,
    val qtyAfter: Int = 0,
    val finalQty: Int = 0,
    val price: Double = 0.0,
    val cog: Double = 0.0,
    val counted: Boolean = false,
    val salesDuringSession: Int? = null
)

@Serializable
data class FinalizeReviewItemRequest(
    val sessionId: Int,
    val itemId: String,
    val itemName: String? = null,
    val itemCode: String? = null,
    val barcode: String? = null,
    val finalQty: Int,
    val price: Double,
    val cog: Double,
    val counted: Boolean,
    val overWriteQty: Boolean
)

@Serializable
data class HumanQueueVendorDto(
    val id: Int,
    val vendorName: String
)

@Serializable
data class CreateHumanQueueVendorRequest(
    val vendorName: String
)

@Serializable
data class SubmitInvoiceResponseDto(
    val batchId: String? = null,
    val status: String? = null
)

@Serializable
data class PriceCheckItemDto(
    val itemName: String? = null,
    val itemCode: String? = null,
    val barcode: String? = null,
    val msrp: Double? = null,
    val cashPrice: Double? = null,
    val cardPrice: Double? = null,
    val cog: Double? = null,
    val markupPercent: Double? = null,
    val marginPercent: Double? = null,
    val qtyInHand: Int? = null,
    val vendorName: String? = null,
    val categoryName: String? = null,
    val superCategoryName: String? = null,
    val discountEligible: Boolean? = null,
    val pricingByCardPrice: Boolean? = null
)
