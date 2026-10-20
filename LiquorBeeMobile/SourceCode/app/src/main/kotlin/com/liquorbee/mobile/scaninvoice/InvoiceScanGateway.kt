package com.liquorbee.mobile.scaninvoice

import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.HumanQueueVendorDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Response

enum class InvoiceScanMode { HUMAN_QUEUE, OCR_TEMPLATE }
data class InvoiceScanConfiguration(val mode: InvoiceScanMode, val vendors: List<HumanQueueVendorDto>)

/** Uses the same account-scoped setting, vendor lists and upload contracts as the purple web button. */
class InvoiceScanGateway(private val api: ApiService) {
    suspend fun loadConfiguration(): InvoiceScanConfiguration {
        check(api.canSeeInvoiceOcrScan().requiredBody()) { "Invoice scanning is not enabled for this account." }
        return if (api.getIsHumanAutomationOn().requiredBody()) {
            InvoiceScanConfiguration(InvoiceScanMode.HUMAN_QUEUE, api.getHumanQueueVendors().requiredBody())
        } else {
            InvoiceScanConfiguration(InvoiceScanMode.OCR_TEMPLATE,
                api.getInvoiceTemplates().requiredBody().filter { it.isActive }
                    .map { HumanQueueVendorDto(it.id, it.vendorName) })
        }
    }

    suspend fun submit(configuration: InvoiceScanConfiguration, vendorId: Int,
                       batchId: String, images: List<MultipartBody.Part>) {
        require(configuration.vendors.any { it.id == vendorId }) { "Select a configured vendor." }
        require(images.isNotEmpty()) { "Add at least one invoice photo." }
        val id = vendorId.toString().toRequestBody("text/plain".toMediaType())
        val batch = batchId.toRequestBody("text/plain".toMediaType())
        val response = when (configuration.mode) {
            InvoiceScanMode.HUMAN_QUEUE -> api.submitInvoiceToHumanQueue(id, batch, images)
            InvoiceScanMode.OCR_TEMPLATE -> api.submitInvoiceToOcr(id, batch, images)
        }
        val receipt = response.requiredBody()
        check(receipt.batchId == batchId) { "The server did not confirm this upload. Please retry." }
    }

    private fun <T> Response<T>.requiredBody(): T {
        if (!isSuccessful) throw HttpException(this)
        return body() ?: error("The server returned an empty response. Please retry.")
    }
}
