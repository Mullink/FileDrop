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

    // GET (not POST) with a Basic-auth header, returning the raw JWT as plain text - matches the
    // web app's login/GetUserToken exactly (see LoginViewModel). Returned as ResponseBody rather
    // than a String/@Serializable type since the body isn't JSON, so the kotlinx.serialization
    // converter must not try to parse it.
    @GET("login/GetUserToken")
    suspend fun login(@Header("Authorization") basicAuthHeader: String): Response<ResponseBody>

    @GET("Login/GetCanSeeInvoiceOcrScan")
    suspend fun canSeeInvoiceOcrScan(): Response<Boolean>

    @GET("Login/GetHideSalesDashboard")
    suspend fun getHideSalesDashboard(): Response<Boolean>

    @GET("Login/GetCurrentCustomerId")
    suspend fun getCurrentCustomerId(): Response<Int>

    @GET("Profile/GetAccountSettings")
    suspend fun getAccountSettings(): Response<AccountSettingsDto>

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

    // ---- Scan Invoice (human-automation queue) ----

    @GET("HumanQueue/GetIsHumanAutomationOn")
    suspend fun getIsHumanAutomationOn(): Response<Boolean>

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
