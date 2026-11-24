package com.liquorbee.mobile.core.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("Products/PriceCheckEdit/Categories")
    suspend fun getPriceCheckCategories(): Response<List<InventoryCategoryDto>>

    @GET("Products/PriceCheckEdit/Lookup")
    suspend fun lookupEditableItem(@Query("upc") upc: String): Response<List<PriceCheckItemDto>>

    @GET("Products/PriceCheckEdit/Search")
    suspend fun searchEditableItems(@Query("query") query: String): Response<List<PriceCheckItemDto>>

    @GET("Products/PriceCheckEdit")
    suspend fun getPriceCheckEdit(@Query("itemId") itemId: String): Response<PriceCheckEditDto>

    @GET("Products/PriceCheckEdit/Vendors")
    suspend fun getPriceCheckVendors(): Response<List<PriceCheckVendorDto>>

    @POST("Products/PriceCheckEdit")
    suspend fun savePriceCheckEdit(@Body item: PriceCheckEditDto): Response<PriceCheckEditDto>

    // GET (not POST) with a Basic-auth header, returning the raw JWT as plain text - matches the
    // web app's login/GetUserToken exactly (see LoginViewModel). Returned as ResponseBody rather
    // than a String/@Serializable type since the body isn't JSON, so the kotlinx.serialization
    // converter must not try to parse it.
    @GET("login/GetUserToken")
    suspend fun login(@Header("Authorization") basicAuthHeader: String): Response<ResponseBody>

    @GET("Login/GetCanSeeInvoiceOcrScan")
    suspend fun canSeeInvoiceOcrScan(): Response<Boolean>

    @GET("Login/GetHideSalesDashOnMobile")
    suspend fun getHideSalesDashOnMobile(): Response<Boolean>

    @GET("Login/GetCurrentCustomerId")
    suspend fun getCurrentCustomerId(): Response<Int>

    @GET("Profile/GetAccountSettings")
    suspend fun getAccountSettings(): Response<AccountSettingsDto>

    // Flag-only call (no live service-area lookup): wholesaleSettings.wholesaleManagerEnabled gates Wholesale.
    @GET("Profile/GetPosCustomerSettings")
    suspend fun getPosCustomerSettings(@Query("includeServiceAreas") includeServiceAreas: Boolean = false): Response<PosCustomerSettingsDto>

    @GET("Admin/GetLastWeekTopSellers/{customerId}")
    suspend fun getLastWeekTopSellers(@Path("customerId") customerId: Int): Response<TopSellersDto>

    @GET("Admin/GetQuanticDashboard/{customerId}/today")
    suspend fun getQuanticDashboard(@Path("customerId") customerId: Int): Response<DashboardResponseDto>

    // Can return 0 (not found), 1 (exact match), or many (ambiguous UPC shared by multiple items)
    // results - see PriceCheckScreen's handling of all three cases.
    @GET("Products/LookupPriceCheckItemByUpc")
    suspend fun lookupPriceCheckItemByUpc(@Query("upc") upc: String): Response<List<PriceCheckItemDto>>

    @GET("Products/SearchPriceCheckItemsByName")
    suspend fun searchPriceCheckItemsByName(@Query("query") query: String): Response<List<PriceCheckItemDto>>

    @GET("Login/GetCardPricingSettings")
    suspend fun getCardPricingSettings(): Response<CardPricingSettingsDto>

    @GET("Products/GetAllItemsForPrinting/{filterCode}")
    suspend fun getAllItemsForPrinting(@Path("filterCode") filterCode: Int): Response<List<LabelPrintingItemDto>>

    @GET("Products/GetInventoryCategories")
    suspend fun getInventoryCategories(): Response<List<InventoryCategoryDto>>

    @POST("Products/GetItemsForPrintingByCategories")
    suspend fun getItemsForPrintingByCategories(@Body categoryIds: List<String>): Response<List<LabelPrintingItemDto>>

    @GET("Products/GetPrintQueueCount")
    suspend fun getPrintQueueCount(): Response<Int>

    @GET("Products/GetQueuedItems")
    suspend fun getQueuedItems(): Response<List<QueuedLabelItemDto>>

    @retrofit2.http.DELETE("Products/ClearQueueItem/{id}")
    suspend fun removeQueuedItem(@Path("id") id: Int): Response<ResponseBody>

    // Response<ResponseBody> rather than a typed/Unit body - we only need isSuccessful, and an
    // empty or non-JSON success body would otherwise trip up the kotlinx.serialization converter.
    @POST("Products/PrintLabels")
    suspend fun printLabels(@Body items: List<PrintLabelRequestItem>): Response<ResponseBody>

    @GET("Products/GetInventorySessionHeaderData")
    suspend fun getInventorySessionHeaderData(): Response<List<InventorySessionHeaderDto>>

    @POST("Products/CreateCategorySession/{zeroOut}")
    suspend fun createCategorySession(
        @Path("zeroOut") zeroOut: Boolean,
        @Body request: CreateCategorySessionRequest
    ): Response<Int>

    @GET("Products/GetSessionReviewItems/{sessionId}")
    suspend fun getSessionReviewItems(@Path("sessionId") sessionId: Int): Response<List<ReviewSessionItemDto>>

    @POST("Products/SaveCategorySessionCounts")
    suspend fun saveCategorySessionCounts(@Body request: SaveCategorySessionCountsRequest): Response<ResponseBody>

    @POST("Products/CreateInventorySession/{zeroOut}")
    suspend fun createInventorySession(
        @Path("zeroOut") zeroOut: Boolean,
        @Query("createdBy") createdBy: String
    ): Response<Int>

    @GET("Products/LookupInventoryItemByBarcode/{sessionId}")
    suspend fun lookupInventoryItemByBarcode(
        @Path("sessionId") sessionId: Int,
        @Query("barcode") barcode: String
    ): Response<List<InventorySessionItemNameDto>>

    @GET("Products/SearchInventoryItemsBySession/{sessionId}")
    suspend fun searchInventoryItemsBySession(
        @Path("sessionId") sessionId: Int,
        @Query("query") query: String
    ): Response<List<InventorySessionItemNameDto>>

    @POST("Products/LogItemInventorySession")
    suspend fun logItemInventorySession(@Body request: LogItemInventorySessionRequest): Response<ResponseBody>

    @POST("Products/CreateNewItemInSession")
    suspend fun createNewItemInSession(@Body request: CreateNewItemInSessionRequest): Response<ResponseBody>

    @GET("Products/GetRecentInventorySubmissions/{sessionId}/{addedBy}")
    suspend fun getRecentInventorySubmissions(
        @Path("sessionId") sessionId: Int,
        @Path("addedBy") addedBy: String
    ): Response<List<InventoryHistoryDto>>

    @POST("Products/SubmitSessionStatus/{sessionId}/{status}")
    suspend fun submitSessionStatus(@Path("sessionId") sessionId: Int, @Path("status") status: String): Response<ResponseBody>

    @GET("Products/GetSessionReviewItems/{sessionId}")
    suspend fun getSessionReviewItemDetails(@Path("sessionId") sessionId: Int): Response<List<ReviewSessionItemDetailDto>>

    @POST("Products/FinalizeSessionItems/{overwrite}")
    suspend fun finalizeSessionItems(
        @Path("overwrite") overwrite: Boolean,
        @Body items: List<FinalizeReviewItemRequest>
    ): Response<ResponseBody>

    @POST("Products/CompleteSessionStatus/{sessionId}/{updateInventory}")
    suspend fun completeSessionStatus(
        @Path("sessionId") sessionId: Int,
        @Path("updateInventory") updateInventory: Boolean
    ): Response<ResponseBody>

    // ---- Create Order (SubCustomers - same endpoints the Wholesale app's Place Order uses) ----

    // priceListId "" = the General Customer (retail) catalog for this store.
    @GET("SubCustomers/GetCatalog")
    suspend fun getOrderCatalog(
        @Query("customerId") customerId: Int,
        @Query("priceListId") priceListId: String = ""
    ): Response<List<SubCustomerCatalogItemDto>>

    // Creates an open General Customer order on the store's POS register.
    @POST("SubCustomers/CreateGeneralOrder")
    suspend fun createGeneralOrder(@Body request: CreateGeneralOrderDto): Response<SubCustomerDraftOrderDto>

    @POST("SubCustomers/CreateDraftOrder")
    suspend fun createDraftOrder(@Body request: CreateSubCustomerDraftOrderDto): Response<SubCustomerDraftOrderDto>

    // Store-side order list (headers only) and one order with its lines - web Wholesale Manager.
    @GET("SubCustomers/GetWholesaleOpenOrders")
    suspend fun getWholesaleOrders(
        @Query("includeAll") includeAll: Boolean = true,
        @Query("headersOnly") headersOnly: Boolean = true,
        // cached = the server's last synced list from memory (no POS round trip); 204 when it has none yet.
        @Query("cached") cached: Boolean = false
    ): Response<List<WholesaleOrderDto>>

    @GET("SubCustomers/GetManagementOrderDetail")
    suspend fun getWholesaleOrderDetail(@Query("orderId") orderId: String): Response<WholesaleOrderDto>

    @GET("SubCustomers/GetManagementView")
    suspend fun getManagementView(@Query("radiusMiles") radiusMiles: Int = 50): Response<SubCustomerManagementDto>

    // ---- LB cart (web Shopping Cart's "Add to LiquorBee") ----

    // Plain-text two-letter state ("MS", "AL", ...), not JSON.
    @GET("Products/GetCustomerState")
    suspend fun getCustomerState(): Response<ResponseBody>

    @GET("Products/GetAlabamaWholesaleStores")
    suspend fun getAlabamaWholesaleStores(): Response<List<AlabamaWholesaleStoreDto>>

    @GET("Login/GetRestrictedPosUser")
    suspend fun getRestrictedPosUser(): Response<Boolean>

    // ---- Unknown barcode (same endpoints as the web manual PO's "Create Item") ----

    // Master liquor catalog search - matches name, state code, or (substring) barcode.
    @GET("Products/GetItemCatalogSearch")
    suspend fun getItemCatalogSearch(@Query("searchTerm") searchTerm: String): Response<List<ItemCatalogSearchDto>>

    @GET("Products/GetItemMetaDataList")
    suspend fun getItemMetaDataList(): Response<List<ItemMetaDataDto>>

    @POST("Products/UpdateItemMetaData")
    suspend fun updateItemMetaData(@Body request: UpdateItemMetaDataRequest): Response<ResponseBody>

    // Universal product resolution. Currently restricted server-side to the qmullink admin login
    // (403 for everyone else) - callers treat any failure as "no universal data".
    @POST("Items/Resolve")
    suspend fun resolveItem(@Body request: ItemResolveRequest): Response<ItemResolutionDto>

    // Backfill: attach a human-confirmed barcode to a universal product.
    @POST("UniversalProducts/{sku}/Upcs")
    suspend fun saveUniversalUpc(@Path("sku") universalSku: String, @Body action: UniversalUpcAction): Response<ResponseBody>

    // Map a store item just created from a universal product to it (store from the token; found by barcode + SKU).
    @POST("UniversalProducts/{sku}/StoreItem")
    suspend fun linkUniversalStoreItem(@Path("sku") universalSku: String, @Body link: UniversalStoreItemLink): Response<ResponseBody>

    // Create Item pre-fill: price book SKU / unit cost and a suggested price at the store's desired markup.
    @GET("Products/GetCreateItemDefaults")
    suspend fun getCreateItemDefaults(
        @Query("sku") sku: String?,
        @Query("universalSku") universalSku: String?,
        @Query("cost") cost: Double?
    ): Response<CreateItemDefaultsDto>

    @POST("Products/CreateItemForManualPO")
    suspend fun createItemForManualPO(@Body request: CreateItemForPORequest): Response<ResponseBody>

    @POST("Products/AddSmartItems")
    suspend fun addSmartItems(@Body items: List<SmartProductAddDto>): Response<ResponseBody>

    // ---- LB cart staged orders + reorder report (web Shopping Cart) ----

    @GET("Products/GetAllStagedOrdersAsync")
    suspend fun getStagedOrders(): Response<List<StagedOrderLineDto>>

    @POST("Products/CreateBulkStageOrder")
    suspend fun createStagedOrder(@Body lines: List<StageOrderLineRequest>): Response<ResponseBody>

    @POST("Products/AddToExistingStageOrder")
    suspend fun addToStagedOrder(@Body lines: List<StageOrderLineRequest>): Response<ResponseBody>

    @GET("Products/GetAllItemsForPricing/{code}")
    suspend fun getItemsForPricing(@Path("code") code: Int = 0): Response<List<PricingItemDto>>

    // General / Full stores only (403 elsewhere).
    @GET("Products/GetGeneralMarkupItems")
    suspend fun getGeneralMarkupItems(): Response<List<PricingItemDto>>

    @GET("Products/GetShelfReview/{lookbackDays}/{onlyReceivedRecently}")
    suspend fun getShelfReview(@Path("lookbackDays") lookbackDays: Int, @Path("onlyReceivedRecently") onlyReceivedRecently: Boolean): Response<List<ShelfReviewItemDto>>

    @GET("Products/GetReorderCartConfig")
    suspend fun getReorderCartConfig(): Response<ReorderCartConfigDto>

    // Plain-text MM/DD/YYYY of the store's last POS sync.
    @GET("Products/GetLastSyncDate")
    suspend fun getLastSyncDate(): Response<ResponseBody>

    // The Shopping Cart report: one dynamic row per item (PascalCase keys). weeksOut 1-8/12 = forecast,
    // 0 = reorder point (with a *-REORDERPOINT vendor). startDate is MM-dd-yyyy.
    @GET("Products/GetDynamicSalesReportData/{weeksOut}/{startDate}/{allData}/{vendor}")
    suspend fun getDynamicSalesReport(
        @Path("weeksOut") weeksOut: Int,
        @Path("startDate") startDate: String,
        @Path("allData") allData: Boolean,
        @Path("vendor") vendor: String
    ): Response<kotlinx.serialization.json.JsonArray>

    // ---- Restock list ----

    @GET("Restock/GetItems")
    suspend fun getRestockItems(): Response<List<RestockItemDto>>

    // ---- Pick lists (Restock list, wholesale orders, Enterprise lists) ----

    @GET("PickLists/GetLists")
    suspend fun getPickLists(
        @Query("status") status: String,
        @Query("source") source: String? = null,
        @Query("search") search: String? = null,
        @Query("days") days: Int? = null,
        @Query("take") take: Int = 200
    ): Response<List<PickListSummaryDto>>

    @GET("PickLists/Get/{id}")
    suspend fun getPickList(@Path("id") id: Int): Response<PickListDto>

    @POST("PickLists/Create")
    suspend fun createPickList(@Body request: CreatePickListRequest): Response<PickListDto>

    @POST("PickLists/Pick/{listId}/{itemId}")
    suspend fun pickItem(@Path("listId") listId: Int, @Path("itemId") itemId: Int, @Body request: PickItemRequest): Response<PickListItemDto>

    @POST("PickLists/Complete/{id}")
    suspend fun completePickList(@Path("id") id: Int): Response<PickListSummaryDto>

    @POST("PickLists/Reopen/{id}")
    suspend fun reopenPickList(@Path("id") id: Int): Response<PickListSummaryDto>

    // Emails the list from no-reply@liquorbee.com; message is an optional note at the top. 204 on success.
    @POST("PickLists/Email/{id}")
    suspend fun emailPickList(@Path("id") id: Int, @Body request: EmailPickListRequest): Response<ResponseBody>

    @retrofit2.http.DELETE("PickLists/Delete/{id}")
    suspend fun deletePickList(@Path("id") id: Int): Response<ResponseBody>

    @GET("Restock/GetQtyOnReserve")
    suspend fun getQtyOnReserve(@Query("code") code: String): Response<QtyOnReserveDto>

    @POST("Restock/AddItem")
    suspend fun addRestockItem(@Body request: AddRestockItemRequest): Response<RestockItemDto>

    // 200 with the row, or 204 when qty <= 0 deleted it.
    @POST("Restock/SetQty")
    suspend fun setRestockQty(@Body request: SetRestockQtyRequest): Response<ResponseBody>

    @retrofit2.http.DELETE("Restock/RemoveItem/{id}")
    suspend fun removeRestockItem(@Path("id") id: Int): Response<ResponseBody>

    @retrofit2.http.DELETE("Restock/Clear")
    suspend fun clearRestock(): Response<ResponseBody>

    // ---- Scan Invoice (human-automation queue) ----

    @GET("HumanQueue/GetIsHumanAutomationOn")
    suspend fun getIsHumanAutomationOn(): Response<Boolean>

    @GET("CustomerInvoiceTemplates/GetTemplates")
    suspend fun getInvoiceTemplates(): Response<List<InvoiceTemplateDto>>

    @Multipart
    @POST("InvoiceOcrScan/ScanInvoice")
    suspend fun submitInvoiceToOcr(
        @Part("templateId") templateId: RequestBody,
        @Part("batchId") batchId: RequestBody,
        @Part images: List<MultipartBody.Part>
    ): Response<SubmitInvoiceResponseDto>

    @GET("HumanQueue/GetActiveVendors")
    suspend fun getHumanQueueVendors(): Response<List<HumanQueueVendorDto>>

    @POST("HumanQueue/CreateVendor")
    suspend fun createHumanQueueVendor(@Body request: CreateHumanQueueVendorRequest): Response<HumanQueueVendorDto>

    // Mirrors the web app's HumanQueue/SubmitInvoice multipart contract exactly (vendorId, batchId,
    // images[]) - see HumanQueueController.SubmitInvoiceAsync on the backend.
    @Multipart
    @POST("HumanQueue/SubmitInvoice")
    suspend fun submitInvoiceToHumanQueue(
        @Part("vendorId") vendorId: RequestBody,
        @Part("batchId") batchId: RequestBody,
        @Part images: List<MultipartBody.Part>
    ): Response<SubmitInvoiceResponseDto>
}
