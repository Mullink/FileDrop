package com.liquorbee.mobile.scaninvoice

import com.liquorbee.mobile.core.network.ApiService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class InvoiceScanGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var gateway: InvoiceScanGateway
    private val batch = "87ef6889-416c-49ec-9fe5-9127802e7b22"
    @Before fun setup() {
        server = MockWebServer()
        server.start()
        gateway = InvoiceScanGateway(Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build().create(ApiService::class.java))
    }
    @After fun teardown() { server.shutdown() }
    private fun enqueue(json: String, status: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(json))
    }
    private fun images() = listOf(MultipartBody.Part.createFormData("images", "page_1.jpg",
        "photo-content".toRequestBody("image/jpeg".toMediaType())))
    private fun enqueueConfiguration(human: Boolean) {
        enqueue("true")
        enqueue(human.toString())
        enqueue(if (human) """[{"id":54,"vendorName":"M.K."}]""" else
            """[{"id":54,"vendorName":"M.K.","isActive":true},{"id":55,"vendorName":"Old","isActive":false}]""")
    }
    @Test fun ocrUsesActiveAccountTemplatesAndTemplateMultipartField() = runBlocking {
        enqueueConfiguration(false)
        enqueue("""{"batchId":"$batch","status":"Uploaded"}""")
        val configuration = gateway.loadConfiguration()
        assertEquals(InvoiceScanMode.OCR_TEMPLATE, configuration.mode)
        assertEquals(listOf(54), configuration.vendors.map { it.id })
        gateway.submit(configuration, 54, batch, images())
        assertEquals("/Login/GetCanSeeInvoiceOcrScan", server.takeRequest().path)
        assertEquals("/HumanQueue/GetIsHumanAutomationOn", server.takeRequest().path)
        assertEquals("/CustomerInvoiceTemplates/GetTemplates", server.takeRequest().path)
        val upload = server.takeRequest()
        assertEquals("POST", upload.method)
        assertEquals("/InvoiceOcrScan/ScanInvoice", upload.path)
        val body = upload.body.readUtf8()
        assertTrue(body.contains("name=\"templateId\""))
        assertFalse(body.contains("name=\"vendorId\""))
        assertTrue(body.contains(batch))
        assertTrue(body.contains("name=\"images\"; filename=\"page_1.jpg\""))
        assertTrue(body.contains("photo-content"))
    }
    @Test fun humanQueueUsesVendorMultipartField() = runBlocking {
        enqueueConfiguration(true)
        enqueue("""{"batchId":"$batch","status":"Uploaded"}""")
        val configuration = gateway.loadConfiguration()
        assertEquals(InvoiceScanMode.HUMAN_QUEUE, configuration.mode)
        gateway.submit(configuration, 54, batch, images())
        server.takeRequest()
        server.takeRequest()
        assertEquals("/HumanQueue/GetActiveVendors", server.takeRequest().path)
        val upload = server.takeRequest()
        assertEquals("/HumanQueue/SubmitInvoice", upload.path)
        val body = upload.body.readUtf8()
        assertTrue(body.contains("name=\"vendorId\""))
        assertFalse(body.contains("name=\"templateId\""))
        assertTrue(body.contains(batch))
    }
    @Test fun settingFailureDoesNotFallBackToAnotherQueue() = runBlocking {
        enqueue("true")
        enqueue("unavailable", 503)
        assertTrue(runCatching { gateway.loadConfiguration() }.isFailure)
        assertEquals(2, server.requestCount)
    }
    @Test fun disabledPermissionStopsBeforeLoadingAccountConfiguration() = runBlocking {
        enqueue("false")
        assertTrue(runCatching { gateway.loadConfiguration() }.isFailure)
        assertEquals(1, server.requestCount)
    }
    @Test fun inactiveOrUnknownTemplateCannotBeSubmitted() = runBlocking {
        enqueueConfiguration(false)
        val configuration = gateway.loadConfiguration()
        assertTrue(runCatching { gateway.submit(configuration, 55, batch, images()) }.isFailure)
        assertEquals(3, server.requestCount)
    }
    @Test fun failedUploadCanRetryWithSameBatchWithoutSwitchingRoutes() = runBlocking {
        enqueueConfiguration(false)
        val configuration = gateway.loadConfiguration()
        enqueue("failure", 500)
        assertTrue(runCatching { gateway.submit(configuration, 54, batch, images()) }.isFailure)
        enqueue("""{"batchId":"$batch","status":"Uploaded"}""")
        gateway.submit(configuration, 54, batch, images())
        repeat(3) { server.takeRequest() }
        repeat(2) {
            val upload = server.takeRequest()
            assertEquals("/InvoiceOcrScan/ScanInvoice", upload.path)
            assertTrue(upload.body.readUtf8().contains(batch))
        }
    }
    @Test fun unconfirmedUploadIsNotReportedAsSuccess() = runBlocking {
        enqueueConfiguration(false)
        val configuration = gateway.loadConfiguration()
        enqueue("{}")
        assertTrue(runCatching { gateway.submit(configuration, 54, batch, images()) }.isFailure)
    }
}
