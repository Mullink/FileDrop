package com.liquorbee.invoicescanner.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Almost every route here already existed and was live in HennyAdminOnline before this app - one
 * exception: CreateVendorOnly, added specifically for this app's simplified "Add Vendor" (just a
 * name, no template/sample-image flow) - see CustomerInvoiceTemplatesController.CreateVendorOnlyAsync. */
interface HennyAdminApi {

    // Basic auth passed explicitly per-call (rather than via the app-wide bearer interceptor)
    // because this ONE call authenticates differently than everything else in the API - see
    // login.component.ts's onSubmit for the exact same pattern on the web side.
    @GET("login/GetUserToken")
    suspend fun getUserToken(@Header("Authorization") basicAuthHeader: String): String

    @GET("CustomerInvoiceTemplates/GetTemplates")
    suspend fun getTemplates(): List<CustomerInvoiceTemplateListItemDto>

    @PUT("CustomerInvoiceTemplates/SetTemplateActive/{id}")
    suspend fun setTemplateActive(@Path("id") id: Int, @Body request: SetTemplateActiveRequest): Response<Unit>

    @POST("CustomerInvoiceTemplates/CreateVendorOnly")
    suspend fun createVendorOnly(@Body request: CreateVendorOnlyRequest): CreateVendorOnlyResponse

    // Just the one field this app needs ("Store: {value}") - Gson ignores every other field the
    // web's fuller AccountSettingFilter response carries, see account-settings.component.ts:534-544.
    @GET("Profile/GetAccountSettings")
    suspend fun getAccountSettings(): AccountSettingsDto

    @Multipart
    @POST("InvoiceOcrScan/ScanInvoice")
    suspend fun scanInvoice(
        @Part("templateId") templateId: RequestBody,
        @Part("batchId") batchId: RequestBody,
        @Part images: List<MultipartBody.Part>
    ): ScanInvoiceResponse

    @GET("InvoiceOcrScan/GetScanStatus/{batchId}")
    suspend fun getScanStatus(@Path("batchId") batchId: String): InvoiceOcrScanBatchStatusDto

    // Same endpoint ocr-ready-for-review.service.ts polls every 60s on the web - every batch for
    // this customer, filtered client-side to ReadyForReview (see ScanActivity.pollReadyForReview).
    @GET("InvoiceOcrScan/GetMyBatches")
    suspend fun getMyBatches(): List<InvoiceOcrScanBatchStatusDto>

    // Server-side relay so a bare POS terminal with no mail app configured (e.g. the iMin Swan 1
    // Pro) can still get a diagnostic log out - see ScanActivity.emailScanLog. Response<Unit>
    // rather than a plain suspend Unit return - the endpoint responds 200 with an empty body,
    // which a bare Unit return type fails to parse under Retrofit's Kotlin coroutine support.
    @POST("InvoiceOcrScan/EmailDiagnosticLog")
    suspend fun emailDiagnosticLog(@Body request: EmailDiagnosticLogRequest): Response<Unit>

    // Native "Scanned Invoices" screens (StagedInvoiceListActivity/StagedInvoiceDetailActivity) -
    // same endpoints ocr-staged-invoice-list/-detail.component.ts already use on the web.
    @GET("InvoiceOcrScan/GetStagedInvoices")
    suspend fun getStagedInvoices(): List<OcrScannedInvoiceHeaderDto>

    @GET("InvoiceOcrScan/GetStagedInvoiceDetail/{invoiceHeaderId}")
    suspend fun getStagedInvoiceDetail(@Path("invoiceHeaderId") invoiceHeaderId: Long): StagedInvoiceDetailResponse

    @POST("InvoiceOcrScan/UpdateStagedLineItems/{invoiceHeaderId}")
    suspend fun updateStagedLineItems(@Path("invoiceHeaderId") invoiceHeaderId: Long, @Body request: UpdateStagedInvoiceRequestDto): Response<Unit>

    // "Add Item" on the staged-invoice review screen - same catalog search the live-PO "fix an
    // unlinked line" typeahead uses (Products/SearchQuanticInventory), then
    // InvoiceOcrScan/AddStagedLineItem to actually insert the picked item as a new line.
    @GET("Products/SearchQuanticInventory")
    suspend fun searchQuanticInventory(@Query("searchTerm") searchTerm: String): List<QuanticInventorySearchResultDto>

    @POST("InvoiceOcrScan/AddStagedLineItem/{invoiceHeaderId}")
    suspend fun addStagedLineItem(@Path("invoiceHeaderId") invoiceHeaderId: Long, @Body request: AddStagedLineItemRequestDto): AddStagedLineItemResponse

    @POST("InvoiceOcrScan/PushToPurchaseOrders/{invoiceHeaderId}")
    suspend fun pushToPurchaseOrders(@Path("invoiceHeaderId") invoiceHeaderId: Long): PushToPurchaseOrdersResponse

    // mode is "DiscountReprice" | "TrueCostReprice" - matches RepriceInvoice's route constraint server-side.
    @POST("InvoiceOcrScan/RepriceInvoice/{invoiceHeaderId}/{mode}")
    suspend fun repriceInvoice(@Path("invoiceHeaderId") invoiceHeaderId: Long, @Path("mode") mode: String): Response<Unit>

    // Native "Purchase Orders" screens (PurchaseOrderListActivity/PurchaseOrderDetailActivity) -
    // same ProductsController endpoints pos-purchase-orders.ts already uses on the web.
    // Matches the web home page's Receiving tile badge exactly (home-page.ts's getStagedOrdersCount).
    @GET("Products/GetStagedOrdersCount")
    suspend fun getStagedOrdersCount(): Int

    @GET("Products/GetAllStagedPurchaseOrders/{fetchOrders}")
    suspend fun getAllStagedPurchaseOrders(@Path("fetchOrders") fetchOrders: Int): List<PurchaseOrderHeaderDto>

    @GET("Products/GetAllStagedPurchaseOrderDetails/{invoiceId}")
    suspend fun getStagedPurchaseOrderDetails(@Path("invoiceId") invoiceId: Long): List<PurchaseOrderLineDto>

    // Body is the FULL line list (same shape GetStagedPurchaseOrderDetails returned, edited in
    // place) - matches the web's saveChanges(), which POSTs this.invoiceLines wholesale rather
    // than a diff.
    @POST("Products/UpdateStagedPurchaseOrderDetails")
    suspend fun updateStagedPurchaseOrderDetails(@Body lines: List<PurchaseOrderLineDto>): Response<Unit>

    // Matches the web's removeLine()/saveChanges() - a line removed on-screen is only hidden and
    // excluded from the total until Save is actually tapped; THIS call (one per pending removal) is
    // what fires at that point, right before updateStagedPurchaseOrderDetails posts what remains.
    @DELETE("Products/RemoveInvoiceLine/{lineId}")
    suspend fun removeInvoiceLine(@Path("lineId") lineId: Long): Response<Unit>

    // Same endpoint web's pos-purchase-orders.ts "Add Item" modal uses - inserts a real
    // dbo.InvoiceLines row directly (no OCR staging involved), unlike AddStagedLineItem above.
    @POST("Products/AddInvoiceLine/{invoiceId}")
    suspend fun addInvoiceLine(@Path("invoiceId") invoiceId: Long, @Body request: AddInvoiceLineRequestDto): AddInvoiceLineResponse

    // No request body - confirmed from the C# signature (ReceiveOrder(int invoiceId, bool
    // printLabel), no [FromBody] parameter) that this receives whatever was last persisted via
    // UpdateStagedPurchaseOrderDetails above, NOT whatever the client happens to post; the web
    // client posts its in-memory lines too but the server ignores that body entirely.
    @POST("Products/ReceiveOrder/{invoiceId}/{printLabel}")
    suspend fun receiveOrder(@Path("invoiceId") invoiceId: Long, @Path("printLabel") printLabel: Boolean): Response<Unit>

    @POST("Products/UpdateInvoiceCheckNumber/{invoiceId}")
    suspend fun updateInvoiceCheckNumber(@Path("invoiceId") invoiceId: Long, @Body checkNumber: String): Response<Unit>

    // status is one of the values UpdateStagedPurchaseOrderStatus accepts server-side - this app
    // only ever sends "DELETED" (Mark as Deleted), matching the one action wired up in
    // PurchaseOrderDetailActivity; ERROR/other statuses are set via other flows on the web this
    // app doesn't yet reimplement.
    @POST("Products/UpdateStagedPurchaseOrderStatus/{invoiceId}/{status}")
    suspend fun updateStagedPurchaseOrderStatus(@Path("invoiceId") invoiceId: Long, @Path("status") status: String): Response<Unit>

    // Manual PO creation (CreatePurchaseOrderActivity) - vendor selection reuses getTemplates()
    // above (same source as ScanActivity's vendor picker) rather than a separate vendor endpoint.
    // Matches the web's DailyHabitsService.markDone('Receiving') - fired the moment the Receive
    // screen loads its staged orders, same as pos-purchase-orders.ts's ngOnInit. Response is
    // ignored (the web's only use of it is the celebration animation on the 4th habit of the day,
    // not worth porting here).
    @POST("Products/MarkDailyHabitDone")
    suspend fun markDailyHabitDone(@Body request: MarkDailyHabitDoneRequest): Response<Unit>

    @GET("Products/GetItemsForManualPO")
    suspend fun getItemsForManualPO(): List<ManualPOItemDto>

    // Response body is a bare JSON number (the new invoiceId) - matches the web's
    // `this.http.post<number>(url, payload)`, not a wrapped object.
    @POST("Products/CreateManualStagedOrder")
    suspend fun createManualStagedOrder(@Body request: CreateManualPORequestDto): Long

    // "Scan Mode" on Create PO - only called when a scanned UPC has no match in the already-loaded
    // catalog (getItemsForManualPO), to prefill the create-new-item form.
    @GET("Products/GetInfoToCreateNewItem")
    suspend fun getInfoToCreateNewItem(@Query("upc") upc: String): NewItemSuggestionDto

    @POST("Products/CreateItemForManualPO")
    suspend fun createItemForManualPO(@Body request: CreateItemForPORequestDto): Response<Unit>
}
