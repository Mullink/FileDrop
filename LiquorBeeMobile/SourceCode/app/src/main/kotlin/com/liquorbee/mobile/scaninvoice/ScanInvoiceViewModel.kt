package com.liquorbee.mobile.scaninvoice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.CreateHumanQueueVendorRequest
import com.liquorbee.mobile.core.network.HumanQueueVendorDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

data class ScanInvoicePage(
    val uri: Uri,
    val thumbnail: Bitmap?
)

data class ScanInvoiceUiState(
    val vendorsLoading: Boolean = true,
    val vendors: List<HumanQueueVendorDto> = emptyList(),
    val selectedVendorId: Int? = null,
    val showNewVendorDialog: Boolean = false,
    val newVendorName: String = "",
    val creatingVendor: Boolean = false,
    val pages: List<ScanInvoicePage> = emptyList(),
    val processingPhoto: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
) {
    val canSubmit: Boolean
        get() = selectedVendorId != null && pages.isNotEmpty() && !submitting && !processingPhoto
}

class ScanInvoiceViewModel(
    private val apiService: ApiService,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanInvoiceUiState())
    val uiState: StateFlow<ScanInvoiceUiState> = _uiState.asStateFlow()

    // The camera-capture target created just before launching the system camera app - a page is
    // only added for it once onCameraResult confirms the capture actually succeeded.
    private var pendingCameraUri: Uri? = null

    init {
        loadVendors()
    }

    private fun loadVendors() {
        _uiState.value = _uiState.value.copy(vendorsLoading = true)
        viewModelScope.launch {
            val vendors = runCatching { apiService.getHumanQueueVendors() }
                .getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()
                .orEmpty()
            _uiState.value = _uiState.value.copy(vendors = vendors, vendorsLoading = false)
        }
    }

    fun onVendorSelected(vendorId: Int) {
        _uiState.value = _uiState.value.copy(selectedVendorId = vendorId)
    }

    fun openNewVendorDialog() {
        _uiState.value = _uiState.value.copy(showNewVendorDialog = true, newVendorName = "")
    }

    fun dismissNewVendorDialog() {
        _uiState.value = _uiState.value.copy(showNewVendorDialog = false)
    }

    fun onNewVendorNameChanged(value: String) {
        _uiState.value = _uiState.value.copy(newVendorName = value)
    }

    fun createVendor() {
        val name = _uiState.value.newVendorName.trim()
        if (name.isBlank()) return

        _uiState.value = _uiState.value.copy(creatingVendor = true)
        viewModelScope.launch {
            try {
                val response = apiService.createHumanQueueVendor(CreateHumanQueueVendorRequest(name))
                val vendor = response.body()
                if (response.isSuccessful && vendor != null) {
                    _uiState.value = _uiState.value.copy(
                        creatingVendor = false,
                        showNewVendorDialog = false,
                        vendors = _uiState.value.vendors + vendor,
                        selectedVendorId = vendor.id
                    )
                } else {
                    _uiState.value = _uiState.value.copy(creatingVendor = false, error = "Failed to create vendor. Please try again.")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(creatingVendor = false, error = "Failed to create vendor: ${e.message ?: e::class.simpleName}")
            }
        }
    }

    // Creates the file:// -> content:// target the camera app writes the photo into. Called right
    // before launching the camera Activity; the actual page is added in onCameraResult once the
    // capture is confirmed successful.
    fun createCameraCaptureUri(): Uri {
        val dir = File(appContext.cacheDir, "scan_invoice").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        pendingCameraUri = uri
        return uri
    }

    fun onCameraResult(success: Boolean) {
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            addPages(listOf(uri))
        }
    }

    private fun addPages(uris: List<Uri>) {
        _uiState.value = _uiState.value.copy(processingPhoto = true, error = null)
        viewModelScope.launch {
            val newPages = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching { ScanInvoicePage(uri, decodeThumbnail(uri)) }.getOrNull()
                }
            }
            _uiState.value = _uiState.value.copy(
                pages = _uiState.value.pages + newPages,
                processingPhoto = false
            )
        }
    }

    private fun decodeThumbnail(uri: Uri): Bitmap? {
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        return appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    fun removePage(index: Int) {
        val pages = _uiState.value.pages.toMutableList()
        if (index in pages.indices) pages.removeAt(index)
        _uiState.value = _uiState.value.copy(pages = pages)
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }

    fun submit() {
        val state = _uiState.value
        val vendorId = state.selectedVendorId
        if (vendorId == null) {
            _uiState.value = state.copy(error = "Select a vendor.")
            return
        }
        if (state.pages.isEmpty()) {
            _uiState.value = state.copy(error = "Add at least one invoice photo.")
            return
        }

        _uiState.value = state.copy(submitting = true, error = null)
        viewModelScope.launch {
            try {
                val imageParts = withContext(Dispatchers.IO) {
                    state.pages.mapIndexed { index, page ->
                        val bytes = compressToJpeg(page.uri)
                        val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                        MultipartBody.Part.createFormData("images", "page_${index + 1}.jpg", body)
                    }
                }
                val vendorIdBody = vendorId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val batchIdBody = UUID.randomUUID().toString().toRequestBody("text/plain".toMediaTypeOrNull())

                val response = apiService.submitInvoiceToHumanQueue(vendorIdBody, batchIdBody, imageParts)
                if (response.isSuccessful) {
                    _uiState.value = ScanInvoiceUiState(
                        vendors = _uiState.value.vendors,
                        vendorsLoading = false,
                        successMessage = "Successfully submitted — your invoice will be staged soon."
                    )
                } else {
                    val serverMessage = runCatching { response.errorBody()?.string() }.getOrNull()?.trim()
                    _uiState.value = _uiState.value.copy(
                        submitting = false,
                        error = "Failed to submit invoice (HTTP ${response.code()})" +
                            if (!serverMessage.isNullOrBlank()) ": $serverMessage" else ". Please try again."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    submitting = false,
                    error = "Failed to submit invoice: ${e.message ?: e::class.simpleName}"
                )
            }
        }
    }

    // Downsamples to a max 1600px edge and re-encodes as JPEG - the same rough budget the web
    // app's compressImageFile targets, keeping multi-page uploads reasonable over mobile data.
    private fun compressToJpeg(uri: Uri): ByteArray {
        val maxEdge = 1600
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sampleSize = 1
        while ((bounds.outWidth / sampleSize) > maxEdge || (bounds.outHeight / sampleSize) > maxEdge) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: throw IllegalStateException("Could not read the selected photo.")

        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
        bitmap.recycle()
        return output.toByteArray()
    }
}
