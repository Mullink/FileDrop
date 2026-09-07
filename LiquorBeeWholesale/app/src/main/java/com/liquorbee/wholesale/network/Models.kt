package com.liquorbee.wholesale.network

import com.google.gson.annotations.SerializedName

data class AccountSettingsDto(val storeName: String?)

// ---- Shared across Send Requests / View Requests / Manage Links (SubCustomers/GetManagementView) ----

data class SubCustomerManagementDto(
    val linkedAccounts: List<SubCustomerDto>,
    val requests: List<SubCustomerRequestDto>,
    val suggestedAccounts: List<SubCustomerDto>,
    val senderProfile: CustomerSenderProfileDto?
)

data class SubCustomerDto(
    val emailAddress: String,
    val username: String?,
    val accountType: String?,
    val firstName: String?,
    val storeName: String?,
    val phoneNumber: String?,
    val city: String?,
    val state: String?,
    val taxExemptPermitNumber: String?
)

// Status is one of: Proposed | Rejected | Linked | Deleted (plain string, not a numeric enum).
data class SubCustomerRequestDto(
    val requestId: String,
    val requestOrigin: String,
    val fromBusinessName: String?,
    val fromId: Int,
    val fromEmailAddress: String?,
    val fromPhoneNumber: String?,
    val toBusinessName: String?,
    val toId: Int?,
    val toEmailAddress: String?,
    val toPhoneNumber: String?,
    val status: String,
    val priceListId: String?,
    val priceListName: String?,
    val showWholesaleDiscount: Boolean,
    val createDate: String,
    val modDate: String,
    val senderContact: SubCustomerContactDto?,
    val recipientBusinessName: String?,
    val invitationLink: String?,
    val notes: String?
)

data class SubCustomerContactDto(
    val name: String?,
    val businessName: String?,
    val email: String?,
    val phoneNumber: String?,
    val city: String?,
    val state: String?
)

data class CustomerSenderProfileDto(
    val firstName: String?,
    val storeName: String?,
    val emailAddress: String?,
    val phoneNumber: String?,
    val city: String?,
    val state: String?
)

data class UpdateSubCustomerRequestStatusDto(
    val requestId: String,
    val status: String,
    val subCustomerEmail: String?
)

// ---- Send Requests ----

// QuanticPriceListDto is serialized snake_case server-side ([JsonPropertyName]) - every other DTO
// in this app is camelCase, this one deliberately isn't.
data class QuanticPriceListDto(
    @SerializedName("price_list_id") val priceListId: String,
    @SerializedName("name") val name: String?,
    @SerializedName("status") val status: Int?,
    @SerializedName("created_at") val createdAt: Long?,
    @SerializedName("start_date") val startDate: Long?,
    @SerializedName("end_date") val endDate: Long?
)

data class CreateSubCustomerRequestDto(
    val requestOrigin: String = "Customer",
    val toEmailAddress: String,
    val recipientBusinessName: String?,
    val priceListName: String?,
    val priceListId: String,
    val notes: String?,
    val senderContact: SubCustomerContactDto
)

// ---- POS Customers ----

data class WholesaleQuanticCustomerDto(
    val quanticCustomerId: String,
    val emailAddress: String?,
    val phoneNumber: String?,
    val displayName: String?,
    val priceListId: String?,
    val isLinked: Boolean,
    val linkedSubCustomerEmail: String?,
    val linkedSubCustomerName: String?,
    val requestId: String?,
    val linkStatus: String?,
    val linkedDate: String?
)

// ---- Place Order ----

data class SubCustomerCatalogItemDto(
    val itemCode: String?,
    val itemName: String?,
    val qtyOnHand: Int,
    val pendingSaleQty: Int = 0,
    val cost: Double,
    val retailPrice: Double,
    val subCustomerPrice: Double,
    val upc: String?
)

data class SubCustomerOrderItemDto(
    val itemCode: String,
    val qty: Int,
    val unitPrice: Double
)

data class CreateSubCustomerDraftOrderDto(
    val requestId: String,
    val subCustomerEmail: String?,
    val requestedDeliveryDate: String = "",
    val notes: String?,
    val items: List<SubCustomerOrderItemDto>
)

data class CreateGeneralOrderDto(
    val notes: String?,
    val items: List<SubCustomerOrderItemDto>
)

data class SubCustomerDraftOrderDto(
    val orderId: String?,
    val orderNumber: String?,
    val requestId: String?,
    val subCustomerEmail: String?,
    val requestedDeliveryDate: String?,
    val notes: String?,
    val createdDate: String?
)

// ---- View Orders ----

// orderStatus: 1=Open, 2=Ready, 3=Closed, 4=Cancelled (int, see LiquorBeePosOrderService.MapStringStatusToInt).
data class WholesaleOpenOrderDto(
    val orderId: String?,
    val orderNumber: String?,
    val requestId: String?,
    val orderStatus: Int,
    val total: Double,
    val balance: Double,
    val amountPaid: Double,
    val taxAmount: Double?,
    val customerName: String?,
    val customerPhone: String?,
    val customerEmail: String?,
    val posCustomerId: String?,
    val notes: String?,
    val createdDate: String?,
    val referenceNumber: String?,
    val serviceAreaId: String?,
    val items: List<WholesaleOpenOrderItemDto>?
)

// ---- Open Orders Printing ----

// SubCustomers/GetLiveOpenOrders - pulls straight from Quantic POS (not the DB-cached
// GetWholesaleOpenOrders above), so it's a slimmer, live-only snapshot: no items/phone/email/
// notes/referenceNumber. Fetch GetManagementOrderDetail(orderId) separately to get line items
// for the printed receipt.
data class WholesaleLiveOpenOrderDto(
    val orderId: String?,
    val orderNumber: String?,
    val customerName: String?,
    val orderStatus: Int,
    val total: Double,
    val balance: Double,
    val createdDate: String?
)

data class WholesaleOpenOrderItemDto(
    val cartId: String?,
    val itemId: String?,
    val itemCode: String?,
    val itemName: String?,
    var quantity: Int,
    var price: Double,
    val note: String?
)

data class PatchManagementOrderDto(
    val orderId: String,
    val items: List<WholesaleOpenOrderItemDto>,
    val notes: String?
)

// GET Profile/GetPosCustomerSettings - only the one field this app needs ("My terminal only" filter).
data class PosCustomerSettingsDto(
    val wholesaleSettings: WholesaleSettingsDto?
)

data class WholesaleSettingsDto(
    val preferredOrderingTerminal: String?
)

data class WholesaleSettingValueDto(val value: String?)
