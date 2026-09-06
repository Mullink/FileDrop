package com.liquorbee.invoicescanner.network

// Field names mirror HennyAdmin.Domain.Dtos.InvoiceOcrScanDtos.cs / ocr-invoice-scan.model.ts
// exactly (verified against both before writing this) - this app talks to the same live API the
// web app's purple "Scan Invoice" button and InvoiceOcrScanController already use, unmodified.

data class CustomerInvoiceTemplateListItemDto(
    val id: Int,
    val vendorName: String,
    val isActive: Boolean,
    val insertDateUtc: String?,
    val updateDateUtc: String?
)

data class SetTemplateActiveRequest(val isActive: Boolean)

data class CreateVendorOnlyRequest(val vendorName: String)

// habitKey matches the web's HabitKey union exactly - this app only ever sends "Receiving".
data class MarkDailyHabitDoneRequest(val habitKey: String)

data class CreateVendorOnlyResponse(val id: Int)

data class AccountSettingsDto(val storeName: String?)

data class ScanInvoiceResponse(
    val batchId: String,
    val status: String
)

data class EmailDiagnosticLogRequest(
    val subject: String,
    val body: String
)

data class OcrScannedInvoiceHeaderDto(
    val id: Long,
    val vendorName: String,
    val invoiceNumber: String,
    val invoiceDate: String?,
    val checkNumber: String?,
    val invoiceTotal: Double?,
    val lineItemCount: Int,
    // Staged | Received | PartiallyReceived | Cancelled
    val status: String,
    val insertDateUtc: String,
    val insertDateDisplay: String,
    val legacyInvoiceId: Long?,
    val pushedToPurchaseOrdersUtc: String?,
    val pushedToPurchaseOrdersDisplay: String?
)

// unitsPerCase/shippedCases/shippedUnits/shippedAs/sellBy/unitCost are `var` - the detail screen
// edits these in place (mirrors ocr-staged-invoice-detail.component.ts's [(ngModel)] bindings
// directly mutating the same row objects) before diffing against a snapshot to build the Save
// payload - see StagedInvoiceDetailActivity.
data class OcrScannedInvoiceLineItemDto(
    val id: Long,
    val itemId: String?,
    val itemCode: String?,
    val itemName: String?,
    val displayName: String?,
    val upcCode: String?,
    val itemCategory: String?,
    val subCategory: String?,
    val size: String?,
    var unitsPerCase: Int?,
    var shippedCases: Int?,
    var shippedUnits: Int,
    var shippedAs: String,
    var sellBy: String,
    val caseCost: Double?,
    val caseDiscount: Double,
    var unitCost: Double?,
    val currentUnitPrice: Double?,
    val suggestedUnitPrice: Double?,
    val suggestedCardPrice: Double?,
    val desiredMarkUp: Double?,
    val currentMarkUp: Double?,
    val marginStatus: String?,
    val isNotLinked: Boolean,
    val isNewItem: Boolean,
    val hasDuplicates: Boolean,
    val hasPriceChange: Boolean,
    val isMarkupRateDefaulted: Boolean,
    val matchMethod: String
)

data class StagedInvoiceDetailResponse(
    val header: OcrScannedInvoiceHeaderDto,
    val lines: List<OcrScannedInvoiceLineItemDto>
)

data class UpdateStagedLineItemRequestDto(
    val lineId: Long,
    val unitsPerCase: Int?,
    val shippedCases: Int?,
    val shippedUnits: Int,
    val shippedAs: String,
    val sellBy: String,
    val unitCost: Double?
)

data class UpdateStagedInvoiceRequestDto(
    val invoiceDate: String?,
    val checkNumber: String?,
    val edits: List<UpdateStagedLineItemRequestDto>
)

// Products/SearchQuanticInventory result row - the same catalog search the live-PO "fix an
// unlinked line" typeahead already uses on the web, reused here for the staged-invoice "Add Item"
// flow's item lookup.
data class QuanticInventorySearchResultDto(
    val itemId: String,
    val sku: String?,
    val name: String?,
    val upc: String?,
    val qtyInHand: Double?,
    val price: Double?,
    val vendorName: String?,
    val unitsPerCase: Int?
)

// Matches AddStagedLineItemRequestDto server-side. ItemCategory/SubCategory/Size/UnitsPerCase
// (unless overridden here)/SellBy are resolved server-side from QuanticInventoryReport_Meta, not
// sent by the client - keeps a manually-added line consistent with how every other line's markup
// lookup already works.
data class AddStagedLineItemRequestDto(
    val itemId: String,
    val itemCode: String?,
    val itemName: String?,
    val displayName: String?,
    val upcCode: String?,
    val shippedAs: String,
    val unitsPerCase: Int?,
    val shippedCases: Int?,
    val shippedUnits: Int,
    val caseCost: Double?,
    val unitCost: Double?,
    val currentUnitPrice: Double?
)

data class AddStagedLineItemResponse(
    val lineId: Long
)

// Matches Products/AddInvoiceLine's request body (HennyAdmin.DataAccess.Dtos.InvoiceLines) - same
// endpoint/shape the web's pos-purchase-orders.ts submitAddItem() uses against a REAL (non-OCR)
// purchase order. Only the fields the client actually computes are sent; everything else
// (IsNotLinked/HasDuplicates/HasPriceChange/IsNewItem/BeforePrice/OriginalShippedCases) is set
// server-side in ProductManagerRepository.AddInvoiceLineAsync.
data class AddInvoiceLineRequestDto(
    val itemId: String?,
    val itemCode: String?,
    val itemName: String?,
    val displayName: String?,
    val upcCode: String?,
    val unitsPerCase: Int?,
    val shippedCases: Int?,
    val shippedUnits: Int?,
    val caseCost: Double?,
    val unitCost: Double?,
    val currentUnitPrice: Double?,
    val suggestedUnitPrice: Double?
)

data class AddInvoiceLineResponse(
    val lineId: Long
)

data class PushToPurchaseOrdersResponse(
    val legacyInvoiceId: Long
)

// Mirrors HennyAdmin.DataAccess.Dtos.InvoiceHeader.cs field-for-field - the real Purchase Orders
// page's list row (pos-purchase-orders.ts's local `InvoiceHeader` interface). Named
// PurchaseOrderHeaderDto here (not InvoiceHeader) only to avoid confusion with the OCR staged-
// invoice header above - same backing table/concept the web page calls "Purchase Orders".
data class PurchaseOrderHeaderDto(
    val invoiceId: Long,
    val customerId: Int,
    val orderId: String?,
    val status: String?,
    val vendor: String?,
    val lineItems: Int,
    val totalCases: Int,
    val netAmount: Double,
    val actualTotal: Double?,
    val discounts: Double?,
    val submittedDate: String?,
    val shippedDate: String?,
    val receivedDate: String?,
    val hasHazard: Boolean,
    val customerState: String?,
    val checkNumber: String?
)

// Mirrors HennyAdmin.DataAccess.Dtos.InvoiceLines.cs field-for-field. unitsPerCase/shippedCases/
// shippedUnits/currentUnitPrice are `var` - PurchaseOrderLineAdapter mutates these in place while
// receiving, exactly like the web's [(ngModel)] bindings on the same line objects, before the
// whole list is POSTed back as-is to UpdateStagedPurchaseOrderDetails/ReceiveOrder. caseCost/
// unitCost are ALSO `var` - CreatePoLineAdapter (manual PO creation) edits these too, matching
// the web's updateManualPOUnitCost (CreateManualStagedOrder allows editing cost pre-submit,
// unlike the receive workflow which only edits quantity/price).
data class PurchaseOrderLineDto(
    val lineId: Long,
    val invoiceId: Long,
    val itemId: String?,
    val itemCode: String?,
    val itemName: String?,
    val displayName: String?,
    val upcCode: String?,
    val itemCategory: String?,
    val subCategory: String?,
    var unitsPerCase: Int?,
    var shippedCases: Int?,
    var shippedUnits: Int?,
    var caseCost: Double?,
    val caseDiscount: Double?,
    val averageCost: Double?,
    var unitCost: Double?,
    var currentUnitPrice: Double?,
    val suggestedUnitPrice: Double?,
    val desiredMarkUp: Double?,
    val currentMarkUp: Double?,
    val currentMargin: Double?,
    val marginStatus: String?,
    val isNotLinked: Boolean,
    val hasDuplicates: Boolean,
    val isNewItem: Boolean,
    val hasPriceChange: Boolean,
    val marketAvg: Double?,
    val oldCog: Double?,
    val originalShippedCases: Int?,
    val currentQty: Int?
)

// Manual PO creation - mirrors pos-purchase-orders.ts's manualPO* flow (GetItemsForManualPO,
// CreateManualStagedOrder) and their C# DTOs in ManualPOItemDto.cs exactly. Vendor selection reuses
// CustomerInvoiceTemplateListItemDto (same source as ScanActivity's vendor picker) instead of the
// web's separate GetVendorsForManualPO list - one vendor list, managed in one place.
data class ManualPOItemDto(
    val itemId: String,
    val itemCode: String?,
    val itemName: String?,
    val displayName: String?,
    val upcCode: String?,
    val itemCategory: String?,
    val unitsPerCase: Int,
    val caseCost: Double,
    val unitCost: Double,
    val currentUnitPrice: Double,
    val suggestedUnitPrice: Double,
    val desiredMarkUpMargin: Double
)

// "Scan Mode" on Create PO - GetInfoToCreateNewItem prefill for a scanned UPC with no catalog
// match. unitsPerCase defaults to 12 server-side when nothing is known about it anywhere - the
// client always gets a usable suggestion, never has to fall back to its own default.
data class NewItemSuggestionDto(
    val name: String?,
    val upc: String?,
    val sku: String?,
    val size: String?,
    val unitsPerCase: Int
)

data class CreateItemForPORequestDto(
    val name: String,
    val sku: String,
    val upc: String?,
    val unitsPerCase: Int,
    val unitCost: Double,
    val price: Double,
    val msrp: Double,
    val qtyInHand: Int
)

// Lines reuse PurchaseOrderLineDto - the server's CreateManualPORequestDto.Lines is typed
// List<InvoiceLines>, the exact same shape PurchaseOrderLineDto already mirrors field-for-field.
data class CreateManualPORequestDto(
    val purchaseOrder: String,
    // Always "MANUAL-<vendor text the user entered/picked>" - matches the web's submitManualPO().
    val vendor: String,
    val submittedDate: String?,
    val shippedDate: String?,
    val lines: List<PurchaseOrderLineDto>
)

data class InvoiceOcrScanBatchStatusDto(
    val batchId: String,
    // Uploaded | Extracting | ReadyForReview | Staged | ExtractionFailed | Duplicate
    val status: String,
    val errorMessage: String?,
    val extractedInvoiceNumber: String?,
    val extractedVendorName: String?,
    val lineItemCount: Int?,
    val unresolvedLineItemCount: Int?,
    val stagedInvoiceHeaderId: Int?,
    val duplicateOfBatchId: String?,
    val extractedCount: Int?
)
