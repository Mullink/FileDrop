package com.liquorbee.wholesale.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.Query

/** Matches the Angular /wholesale/management page's exact contract (SubCustomersController). */
interface HennyAdminApi {

    // Basic auth passed explicitly per-call - this ONE call authenticates differently than
    // everything else in the API.
    @GET("login/GetUserToken")
    suspend fun getUserToken(@Header("Authorization") basicAuthHeader: String): String

    @GET("Profile/GetAccountSettings")
    suspend fun getAccountSettings(): AccountSettingsDto

    @GET("Login/GetCurrentCustomerId")
    suspend fun getCurrentCustomerId(): Int

    // "My terminal only" filter on View Orders reads wholesaleSettings.preferredOrderingTerminal.
    @GET("Profile/GetPosCustomerSettings")
    suspend fun getPosCustomerSettings(): PosCustomerSettingsDto

    // Backs Send Requests / View Requests / Manage Links - one shared payload for all three tabs.
    @GET("SubCustomers/GetManagementView")
    suspend fun getManagementView(@Query("radiusMiles") radiusMiles: Int = 50): SubCustomerManagementDto

    @GET("SubCustomers/GetAvailablePriceLists")
    suspend fun getAvailablePriceLists(): List<QuanticPriceListDto>

    // key is restricted server-side to "Wholesale_OrderPlaced" - the celebratory GIF URL shown
    // after a successful order submit (dbo.Settings, database-driven, not a bundled asset).
    @GET("SubCustomers/GetWholesaleSetting")
    suspend fun getWholesaleSetting(@Query("key") key: String): WholesaleSettingValueDto

    @retrofit2.http.POST("SubCustomers/CreateRequest")
    suspend fun createSubCustomerRequest(@Body request: CreateSubCustomerRequestDto): SubCustomerRequestDto

    // Same endpoint cancels a Proposed request AND unlinks a Linked account - status: "Deleted" in
    // both cases (see SubCustomerService - status transition is the only mechanism for either).
    @retrofit2.http.POST("SubCustomers/UpdateRequestStatus")
    suspend fun updateRequestStatus(@Body request: UpdateSubCustomerRequestStatusDto): SubCustomerRequestDto

    @GET("SubCustomers/GetWholesaleQuanticCustomers")
    suspend fun getWholesaleQuanticCustomers(): List<WholesaleQuanticCustomerDto>

    // priceListId is "" for the General Customer (retail) catalog.
    @GET("SubCustomers/GetCatalog")
    suspend fun getCatalog(@Query("customerId") customerId: Int, @Query("priceListId") priceListId: String): List<SubCustomerCatalogItemDto>

    @retrofit2.http.POST("SubCustomers/CreateDraftOrder")
    suspend fun createDraftOrder(@Body request: CreateSubCustomerDraftOrderDto): SubCustomerDraftOrderDto

    @retrofit2.http.POST("SubCustomers/CreateGeneralOrder")
    suspend fun createGeneralOrder(@Body request: CreateGeneralOrderDto): SubCustomerDraftOrderDto

    @GET("SubCustomers/GetWholesaleOpenOrders")
    suspend fun getWholesaleOpenOrders(
        @Query("includeAll") includeAll: Boolean = true,
        @Query("headersOnly") headersOnly: Boolean = true
    ): List<WholesaleOpenOrderDto>

    @GET("SubCustomers/GetManagementOrderDetail")
    suspend fun getManagementOrderDetail(@Query("orderId") orderId: String): WholesaleOpenOrderDto

    @PATCH("SubCustomers/PatchManagementOrder")
    suspend fun patchManagementOrder(@Body request: PatchManagementOrderDto): Response<Unit>

    @DELETE("SubCustomers/DeleteManagementOrder")
    suspend fun deleteManagementOrder(@Query("orderId") orderId: String): Response<Unit>
}
