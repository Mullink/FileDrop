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
data class WholesaleSettingsDto(val wholesaleManagerEnabled: Boolean = false)

/** Only the Wholesale switch from Profile/GetPosCustomerSettings. */
@Serializable
data class PosCustomerSettingsDto(val wholesaleSettings: WholesaleSettingsDto? = null)

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
data class QueuedLabelItemDto(
    val id: Int = 0,
    val itemCode: String? = null,
    val itemName: String? = null,
    val price: Double? = null
)

@Serializable
data class LabelPrintingItemDto(
    val itemCode: String,
    val caseSizeRatio: Double? = null,
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
    val itemId: String? = null,
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

@Serializable
data class PriceCheckEditDto(
    val itemId: String,
    val revision: String,
    val name: String,
    val upc: String,
    val sku: String,
    val price: Double,
    val cost: Double,
    val quantity: Int,
    val vendorId: String,
    val description: String = "",
    val categoryId: String = "",
    val status: Int = 1,
    val trackInventory: Int = 1,
    val cardPrice: Double? = null,
    val msrp: Double? = null,
    val discountEligible: Boolean? = null,
    val categoryName: String = "",
    val vendorName: String = "",
    val hasMultipleStockRecords: Boolean = false
)

@Serializable
data class PriceCheckVendorDto(val vendorId: String, val vendorName: String)

@Serializable
data class InvoiceTemplateDto(val id: Int, val vendorName: String, val isActive: Boolean)

// ---- Create Order (same SubCustomers contract the Wholesale app / web management page uses) ----

@Serializable
data class SubCustomerCatalogItemDto(
    /** Quantic ItemId - lets wholesale order lines (keyed by ItemId) find their SKU and UPC. */
    val itemId: String? = null,
    val itemCode: String? = null,
    val itemName: String? = null,
    val qtyOnHand: Int = 0,
    val pendingSaleQty: Int = 0,
    val cost: Double = 0.0,
    val retailPrice: Double = 0.0,
    val subCustomerPrice: Double = 0.0,
    val upc: String? = null
)

/** Units ordered from the distributor that haven't shipped yet (Restock/GetQtyOnReserve). */
@Serializable
data class QtyOnReserveDto(val itemCode: String? = null, val qtyOnReserve: Int = 0)

@Serializable
data class SubCustomerOrderItemDto(
    val itemCode: String,
    val qty: Int,
    val unitPrice: Double
)

@Serializable
data class CreateGeneralOrderDto(
    val notes: String? = null,
    val items: List<SubCustomerOrderItemDto>
)

// Only the fields the phone needs from SubCustomers/GetManagementView (linked wholesale accounts).
@Serializable
data class SubCustomerManagementDto(
    val requests: List<SubCustomerRequestDto> = emptyList()
)

@Serializable
data class SubCustomerRequestDto(
    val requestId: String = "",
    val fromId: Int = 0,
    val toBusinessName: String? = null,
    val recipientBusinessName: String? = null,
    val toEmailAddress: String? = null,
    val status: String? = null,
    val priceListId: String? = null,
    val priceListName: String? = null
)

@Serializable
data class CreateSubCustomerDraftOrderDto(
    val requestId: String,
    val subCustomerEmail: String? = null,
    val requestedDeliveryDate: String = "",
    val notes: String? = null,
    // The store places this order for the linked customer (like the web's own Place Order popup), so the
    // buyer-side Delivery/Pickup choice isn't required.
    val onBehalf: Boolean = true,
    val items: List<SubCustomerOrderItemDto>
)

// Products/AddSmartItems - the web Shopping Cart's "Add to LiquorBee". Sent exactly as the web
// sends it for a non-Alabama store: nulls for the Alabama-only fields. ClearCartBeforeStart must be
// sent as an explicit null, since the server-side default is true.
@Serializable
data class SmartProductAddDto(
    val addedBy: String? = null,
    val username: String? = null,
    val code: String,
    val desiredQty: Int,
    val minQty: Int,
    val maxQty: Int,
    val purchaseNow: Boolean = false,
    val warehouseCode: String? = null,
    val clearCartBeforeStart: Boolean? = null,
    val requestedDatePreference: String? = null,
    val requestedDayOfWeek: String? = null,
    val requestedDateFallbackBehavior: String? = null,
    val requestedUnitType: String = "Case"
)

@Serializable
data class AlabamaWholesaleStoreDto(
    val storeNumber: String = "",
    val city: String? = null,
    val addressLine1: String? = null,
    val isCustomerCity: Boolean = false
)

// ---- Unknown barcode: master catalog lookup + create item (same as the web's manual PO) ----

@Serializable
data class ItemCatalogSearchDto(
    val itemName: String? = null,
    val sku: String? = null,
    val barcode: String? = null,
    val cost: Double? = null,
    val unitsPerCase: Int? = null
)

@Serializable
data class CreateItemForPORequest(
    val name: String,
    val sku: String,
    val upc: String,
    val unitsPerCase: Int,
    val unitCost: Double,
    val price: Double,
    val msrp: Double,
    val qtyInHand: Int
)

// Products/GetCreateItemDefaults - every field may be null (no price book match / no markup set up).
@Serializable
data class CreateItemDefaultsDto(
    val sku: String? = null,
    val skuSource: String? = null,
    val unitCost: Double? = null,
    val costSource: String? = null,
    val bookUnitCost: Double? = null,
    val unitsPerCase: Int? = null,
    val markupPercent: Double? = null,
    val suggestedPrice: Double? = null
)

// Products/GetItemMetaDataList - dbo.QuanticInventoryMetaDataReport rows for this store (size and
// units per case backfilled from the distribution dictionary / web lookups).
@Serializable
data class ItemMetaDataDto(
    val itemId: String? = null,
    val size: String? = null,
    val unitsPerCase: Int = 0,
    val barcode: String? = null,
    val itemName: String? = null,
    val source: String? = null
)

// Products/UpdateItemMetaData - upserts Size/UnitsPerCase (source "Manual"). Size must be non-empty
// and unitsPerCase > 0 (server validation).
@Serializable
data class UpdateItemMetaDataRequest(val itemId: String, val unitsPerCase: Int, val size: String)

// ---- Universal products (UniversalProductsController / Items/Resolve) ----

@Serializable
data class ItemResolveRequest(
    val name: String? = null,
    val upc: String? = null,
    val size: String? = null,
    val unitsPerCase: Int? = null,
    // false = read-only catalog match; true = may run the paid web lookup and save a new product.
    val allowLookup: Boolean = false,
    // true = look up online even if this barcode recently failed a lookup ("Try again online").
    val forceLookup: Boolean = false
)

@Serializable
data class UniversalProductDto(
    val universalSku: String = "",
    val name: String? = null,
    val size: String? = null,
    val unitsPerCase: Int? = null,
    val barcodes: List<String> = emptyList()
)

@Serializable
data class ItemResolutionDto(
    val status: String = "",
    val method: String? = null,
    val reason: String? = null,
    val product: UniversalProductDto? = null,
    val candidates: List<UniversalProductDto>? = null,
    val persisted: Boolean = false
)

@Serializable
data class UniversalUpcAction(val upc: String, val note: String? = null, val isActive: Boolean = true)

// ---- Restock list (Restock controller) ----

@Serializable
data class RestockItemDto(
    val id: Int,
    val itemCode: String = "",
    val itemName: String? = null,
    val upc: String? = null,
    val qty: Int = 0,
    val addedBy: String? = null,
    val createdDate: String? = null,
    val modDate: String? = null
)

@Serializable
data class AddRestockItemRequest(
    val itemCode: String,
    val itemName: String? = null,
    val upc: String? = null,
    val qty: Int
)

@Serializable
data class SetRestockQtyRequest(val id: Int, val qty: Int)

@Serializable
data class SubCustomerDraftOrderDto(
    val orderId: String? = null,
    val orderNumber: String? = null,
    val notes: String? = null,
    val createdDate: String? = null
)

// ---- Pick lists ----

object PickListSource {
    const val RESTOCK = "Restock"
    const val WHOLESALE = "Wholesale"
    const val ENTERPRISE = "Enterprise"
    const val MANUAL = "Manual"
}

@Serializable
data class PickListSummaryDto(
    val id: Int,
    val name: String = "",
    val source: String = "",
    val sourceRef: String? = null,
    val notes: String? = null,
    val createdBy: String? = null,
    val createdDate: String? = null,
    val modDate: String? = null,
    val completedDate: String? = null,
    val completedBy: String? = null,
    val itemCount: Int = 0,
    val totalQty: Int = 0,
    val pickedQty: Int = 0,
    val checkedCount: Int = 0,
    val missingCount: Int = 0
) {
    val isCompleted: Boolean get() = !completedDate.isNullOrBlank()
}

@Serializable
data class PickListItemDto(
    val id: Int,
    val itemCode: String = "",
    val itemName: String? = null,
    val upc: String? = null,
    val qty: Int = 0,
    val pickedQty: Int = 0,
    val isMissing: Boolean = false,
    val isChecked: Boolean = false,
    val checkedBy: String? = null,
    val checkedDate: String? = null,
    val createdDate: String? = null
)

@Serializable
data class PickListDto(
    val id: Int,
    val name: String = "",
    val source: String = "",
    val sourceRef: String? = null,
    val notes: String? = null,
    val createdBy: String? = null,
    val createdDate: String? = null,
    val modDate: String? = null,
    val completedDate: String? = null,
    val completedBy: String? = null,
    val itemCount: Int = 0,
    val totalQty: Int = 0,
    val pickedQty: Int = 0,
    val checkedCount: Int = 0,
    val missingCount: Int = 0,
    val items: List<PickListItemDto> = emptyList()
) {
    val isCompleted: Boolean get() = !completedDate.isNullOrBlank()
}

@Serializable
data class CreatePickListItem(val itemCode: String, val itemName: String? = null, val upc: String? = null, val qty: Int)

@Serializable
data class CreatePickListRequest(
    val name: String,
    val source: String,
    val sourceRef: String? = null,
    val notes: String? = null,
    val items: List<CreatePickListItem>
)

@Serializable
data class PickItemRequest(val pickedQty: Int, val isMissing: Boolean)


// ---- LB cart staged orders (same endpoints as the web Shopping Cart's "Stage Order") ----

/** One line of a staged order. Products/GetAllStagedOrdersAsync returns these flat, one row per item. */
@Serializable
data class StagedOrderLineDto(
    val orderId: Int = 0,
    val orderName: String? = null,
    val createdDate: String? = null,
    val code: String? = null,
    val caseQty: Int = 0,
    val name: String? = null,
    val caseCost: Double = 0.0,
    val availableCases: Double = 0.0
)

/** Body line for Products/CreateBulkStageOrder and Products/AddToExistingStageOrder (server reads OrderId, OrderName, Code, CaseQty). */
@Serializable
data class StageOrderLineRequest(
    val orderId: Int = 0,
    val orderName: String,
    val code: String,
    val caseQty: Int,
    val name: String,
    val createdDate: String
)

/** Products/GetReorderCartConfig - only the fields the app uses. */
@Serializable
data class ReorderCartConfigDto(
    val addReorderReportInCart: Boolean = false,
    val useInventoryCategories: Boolean = false,
    val enableTwelveWeekForecast: Boolean = false
)

// ---- Wholesale orders (store side; same endpoints as the web Wholesale Manager) ----

@Serializable
data class WholesaleOrderItemDto(
    val itemId: String? = null,
    val itemCode: String? = null,
    val size: String? = null,
    val itemName: String? = null,
    val quantity: Double = 0.0,
    val price: Double = 0.0,
    val note: String? = null
)

/** SubCustomers/GetWholesaleOpenOrders rows and SubCustomers/GetManagementOrderDetail. */
@Serializable
data class WholesaleOrderDto(
    val orderId: String? = null,
    val orderNumber: String? = null,
    val orderStatus: Int = 0,
    val total: Double = 0.0,
    val balance: Double = 0.0,
    val customerName: String? = null,
    val customerEmail: String? = null,
    val notes: String? = null,
    val createdDate: String? = null,
    val closedDate: String? = null,
    val referenceNumber: String? = null,
    val invoicePrinted: Boolean = false,
    val items: List<WholesaleOrderItemDto>? = null
) {
    /** Same labels as the web Wholesale Manager. */
    val statusLabel: String get() = when (orderStatus) { 0, 1 -> "Open"; 2 -> "Ready"; 3, 4 -> "Closed"; else -> "Status $orderStatus" }
    val isClosed: Boolean get() = orderStatus == 3 || orderStatus == 4
    val label: String get() = orderNumber?.takeIf { it.isNotBlank() } ?: referenceNumber?.takeIf { it.isNotBlank() } ?: orderId.orEmpty()
}

/** PickLists/Email/{id}: up to 5 addresses separated by commas; message is an optional note. */
@Serializable
data class EmailPickListRequest(val toEmail: String, val message: String? = null)

// ---- Item insights on Scan: Margin Monitoring + Shelf Review (same endpoints as those web pages) ----

/** Products/GetAllItemsForPricing/0 (ABC stores) or Products/GetGeneralMarkupItems (General stores). Margins are percents. */
@Serializable
data class PricingItemDto(
    val itemId: String? = null,
    val itemCode: String? = null,
    val upc: String? = null,
    val currentPrice: Double = 0.0,
    val currentCost: Double = 0.0,
    val suggestedPrice: Double = 0.0,
    val currentMargin: Double = 0.0,
    val desiredMargin: Double = 0.0
)

/** Products/GetShelfReview/{lookbackDays}/{onlyReceivedRecently}. DaysOfSupplyRemaining >= 9999 means none sell. */
@Serializable
data class ShelfReviewItemDto(
    val itemId: String? = null,
    val sku: String? = null,
    val qtyInHand: Double = 0.0,
    val totalSold: Double = 0.0,
    val daysOfSupplyRemaining: Double = 0.0,
    val sellThruPct: Double = 0.0,
    val currentPrice: Double = 0.0,
    val alreadyMarkedDown: Boolean = false
)

/** UniversalProducts/{sku}/StoreItem - the barcode and SKU a store item was just created with. */
@Serializable
data class UniversalStoreItemLink(val upc: String, val sku: String, val note: String? = null)
