package com.liquorbee.invoicescanner.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.liquorbee.invoicescanner.annotation.AnnotationCompositor
import com.liquorbee.invoicescanner.capture.BrotherUsbCaptureSource
import com.liquorbee.invoicescanner.capture.CaptureResult
import com.liquorbee.invoicescanner.capture.UsbDiagnostics
import com.liquorbee.invoicescanner.capture.UsbScannerProbe
import android.hardware.usb.UsbManager
import android.widget.LinearLayout
import android.widget.TextView
import com.liquorbee.invoicescanner.databinding.ActivityScanBinding
import com.liquorbee.invoicescanner.imaging.ImagePipeline
import com.liquorbee.invoicescanner.network.ApiClient
import com.liquorbee.invoicescanner.network.ApiConfig
import com.liquorbee.invoicescanner.network.CustomerInvoiceTemplateListItemDto
import com.liquorbee.invoicescanner.network.EmailDiagnosticLogRequest
import com.liquorbee.invoicescanner.network.InvoiceOcrScanBatchStatusDto
import com.liquorbee.invoicescanner.network.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

private const val MAX_SCAN_PAGES = 100 // mirrors the desktop app's ScannerConfig.MaxPages / the server's MaxScanPages
private const val MAX_LONG_EDGE_PX = 2200
private const val JPEG_QUALITY = 85

// Matches ocr-scan-upload.component.ts's READY_FOR_REVIEW_POLL_MS exactly - this app follows the
// same async pattern as the web "purple button" flow: submit returns immediately (extraction runs
// as a Hangfire background job either way), and a separate periodic check surfaces whatever has
// since finished, rather than blocking the just-submitted batch on its own inline poll loop.
private const val READY_FOR_REVIEW_POLL_MS = 60_000L

class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private lateinit var session: SessionManager
    private lateinit var brotherSource: BrotherUsbCaptureSource
    private lateinit var adapter: PageThumbnailAdapter

    private var templates: List<CustomerInvoiceTemplateListItemDto> = emptyList()
    private var pendingInvoiceNumber: String? = null
    private var lastSubmittedBatchId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Explicit, unambiguous screen title - this IS the "scan invoice" screen, reached after
        // every login (deep-link or plain launcher icon tap alike, see LoginActivity.goToScan()).
        title = "Scan Invoice"

        session = SessionManager(this)
        if (!session.isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        brotherSource = BrotherUsbCaptureSource(this)

        pendingInvoiceNumber = intent.getStringExtra(EXTRA_INVOICE_NUMBER)
        if (pendingInvoiceNumber != null) {
            binding.textPendingInvoice.visibility = View.VISIBLE
            binding.textPendingInvoice.text = "Scanning for PO #$pendingInvoiceNumber"
        }

        adapter = PageThumbnailAdapter(
            PageStore.pages,
            onDrawClicked = { index -> startActivity(Intent(this, DrawActivity::class.java).putExtra(EXTRA_PAGE_INDEX, index)) },
            onRemoveClicked = { index -> PageStore.pages.removeAt(index); adapter.notifyDataSetChanged() }
        )
        binding.recyclerPages.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerPages.adapter = adapter

        // Vendor is required before a scan can start - the button stays tappable rather than
        // disabled (a disabled button gives zero feedback on tap; this way the user always gets an
        // explicit "select a vendor first" message instead of wondering why nothing happened).
        binding.buttonBrother.setOnClickListener {
            if (binding.spinnerTemplate.selectedItemPosition <= 0) {
                Toast.makeText(this, "Select a vendor first.", Toast.LENGTH_SHORT).show()
            } else {
                captureFromBrother()
            }
        }
        // "Manual" is a shortcut into CreatePurchaseOrderActivity, not a second real content mode
        // on this screen - Scan stays the only thing ScanActivity itself ever shows, tapping Manual
        // just launches Create PO and this screen is exactly as it was when the user backs out of it.
        binding.buttonModeManual.setOnClickListener { startActivity(Intent(this, CreatePurchaseOrderActivity::class.java)) }
        binding.buttonSubmit.setOnClickListener { submitBatch() }
        binding.buttonUsbDiagnostics.setOnClickListener { showUsbDiagnostics() }
        binding.linkManageVendors.setOnClickListener { startActivity(Intent(this, VendorManagementActivity::class.java)) }
        binding.textBack.setOnClickListener { finish() }

        loadTemplates()
        applyAdminModeVisibility()

        // Matches the web page's constructor: an immediate check, then poll every 60s for the rest
        // of this screen's lifetime - lifecycleScope cancels this automatically on destroy.
        lifecycleScope.launch {
            while (isActive) {
                loadReadyForReview()
                delay(READY_FOR_REVIEW_POLL_MS)
            }
        }
    }

    // Opens an existing, already-built HennyAdminOnline web page rather than reimplementing it
    // natively - same site the app already authenticates against, just a different route.
    private fun openWebPage(route: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ApiConfig.BASE_URL + route)))
    }

    override fun onResume() {
        super.onResume()
        // Picks up a page just annotated in DrawActivity (same PageStore list, mutated in place).
        adapter.notifyDataSetChanged()
        // Picks up an admin-mode change made on HomeActivity while this screen was backgrounded.
        applyAdminModeVisibility()
        // Picks up a vendor added/toggled on VendorManagementActivity while this screen was
        // backgrounded - loadTemplates() itself preserves the current selection, so this is a
        // no-op for the far more common case of just resuming from DrawActivity mid-scan.
        loadTemplates()
    }

    // USB Diagnostics is the one place raw hardware/log detail leaks to a standard user - only
    // shown once Admin Mode is unlocked (see HomeActivity's 5-tap + password gate).
    private fun applyAdminModeVisibility() {
        binding.buttonUsbDiagnostics.visibility = if (AdminModeState.isEnabled) View.VISIBLE else View.GONE
    }

    private fun loadTemplates() {
        // Preserve whatever's currently picked (e.g. resuming from DrawActivity mid-scan) - only a
        // genuinely stale/removed selection falls back to the placeholder.
        val previouslySelectedId = templates.getOrNull(binding.spinnerTemplate.selectedItemPosition - 1)?.id

        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                templates = api.getTemplates().filter { it.isActive }
                // A real placeholder at index 0 (not just relying on submitBatch's own check) -
                // Android's Spinner otherwise auto-selects index 0, which used to silently make
                // the first real vendor the default with no explicit choice ever required.
                val names = listOf("Select a vendor...") + templates.map { it.vendorName }
                binding.spinnerTemplate.adapter = ArrayAdapter(this@ScanActivity, android.R.layout.simple_spinner_dropdown_item, names)
                val restoredIndex = templates.indexOfFirst { it.id == previouslySelectedId }
                binding.spinnerTemplate.setSelection(if (restoredIndex >= 0) restoredIndex + 1 else 0)
            } catch (e: Exception) {
                setStatus("Could not load vendor templates: ${e.message}")
            }
        }
    }

    // Real USB hardware can't be exercised in this dev environment - this surfaces exactly what
    // Android's USB host stack sees, in Logcat AND on-screen (the POS terminal this ships to may
    // not have easy Logcat access in the field). If a Brother-vendor-ID device is attached, runs
    // the FULL probe (permission -> open -> claim interface) - not just enumeration - since that's
    // the one thing that actually proves whether real communication with the scanner is possible
    // on this exact hardware/Android version. Deliberately does NOT attempt any scan-trigger
    // command sequence - that depends entirely on what this probe reveals on the real device.
    private fun showUsbDiagnostics() {
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        val brotherDevice = usbManager.deviceList.values.firstOrNull { it.vendorId == 0x04F9 }

        if (brotherDevice == null) {
            AlertDialog.Builder(this)
                .setTitle("USB Diagnostics")
                .setMessage(UsbDiagnostics.describeAttachedDevices(this))
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("USB Diagnostics")
            .setMessage("Probing Brother DS-640 - a system permission dialog may appear, respond to it to continue...")
            .setCancelable(false)
            .show()

        UsbScannerProbe(this).probe(brotherDevice) { report ->
            progressDialog.dismiss()
            AlertDialog.Builder(this)
                .setTitle("USB Diagnostics")
                .setMessage(report)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun captureFromBrother() {
        // Immediate, unmissable feedback the instant the button is tapped - a slow/stuck USB
        // operation should never look indistinguishable from the tap not registering at all.
        Toast.makeText(this, "Starting scan...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val result = try {
                brotherSource.capture()
            } catch (e: Exception) {
                // Belt-and-suspenders: capture() already catches its own exceptions, but if
                // anything else throws here, still show it instead of silently doing nothing.
                CaptureResult.Failure("Unexpected error: ${e.message}")
            }

            when (result) {
                is CaptureResult.Success -> {
                    PageStore.pages.add(ScanPage(result.page.bitmap))
                    adapter.notifyDataSetChanged()
                    // Standard-mode users don't need the raw diagnostic log on a successful scan -
                    // a brief toast is enough; Admin Mode still gets the full dialog + log/email option.
                    if (AdminModeState.isEnabled) {
                        showScanLogDialog("Scan succeeded", brotherSource.lastAttemptLog)
                    } else {
                        Toast.makeText(this@ScanActivity, "Page scanned", Toast.LENGTH_SHORT).show()
                    }
                }
                is CaptureResult.Failure -> showScanLogDialog("Scan failed: ${result.message}", brotherSource.lastAttemptLog)
                CaptureResult.Cancelled -> {}
            }
        }
    }

    // Always shown (success or failure) so a real problem is visible on-screen without needing
    // Logcat access - the iMin Swan 1 Pro this ships to can't always be tethered to a dev machine.
    private fun showScanLogDialog(title: String, log: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(log.ifBlank { "(no steps recorded)" })
            .setPositiveButton("OK", null)
            .setNegativeButton("Email Log") { _, _ -> emailScanLog(title, log) }
            .show()
    }

    // Sent server-side (InvoiceOcrScan/EmailDiagnosticLog) rather than via a device mailto: intent
    // - the bare POS terminals this ships to (e.g. the iMin Swan 1 Pro) have no mail app installed
    // for a mailto: intent to hand off to, so that approach silently couldn't work on real hardware.
    private fun emailScanLog(subject: String, log: String) {
        Toast.makeText(this, "Sending log...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                val response = api.emailDiagnosticLog(EmailDiagnosticLogRequest(subject, log))
                if (response.isSuccessful) {
                    Toast.makeText(this@ScanActivity, "Log emailed to quinten@liquorbee.com", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@ScanActivity, "Failed to send log: ${response.code()} ${response.message()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ScanActivity, "Failed to send log: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun submitBatch() {
        // Index 0 is the "Select a vendor..." placeholder prepended in loadTemplates() - never a
        // real choice, so it must be rejected here the same as no selection at all.
        val templateIndex = binding.spinnerTemplate.selectedItemPosition - 1
        if (templateIndex < 0 || templateIndex >= templates.size) {
            setStatus("Pick a vendor template first.")
            return
        }
        if (PageStore.pages.isEmpty()) {
            setStatus("Capture at least one page first.")
            return
        }

        val templateId = templates[templateIndex].id
        val batchId = UUID.randomUUID().toString()

        setSubmitting(true)
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)

                val imageParts = PageStore.pages.mapIndexed { i, page ->
                    val composited = AnnotationCompositor.composite(page.bitmap, page.strokes, page.notes)
                    val processed = ImagePipeline.process(composited, MAX_LONG_EDGE_PX, JPEG_QUALITY)
                    val body = processed.bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("images", "page_${i + 1}.jpg", body)
                }

                val templateIdBody = templateId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val batchIdBody = batchId.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = api.scanInvoice(templateIdBody, batchIdBody, imageParts)

                // Matches ocr-scan-upload.component.ts's submit(): show success and clear the page
                // list immediately, WITHOUT blocking on this batch's own extraction status - the
                // Hangfire job keeps running server-side regardless, and the periodic
                // loadReadyForReview() poll (started in onCreate) is what surfaces it once done,
                // exactly like the web page's separate 60s "ready for review" check. This lets
                // whoever is scanning start the next invoice immediately instead of watching a
                // status line for this one. Not tappable - the "ready for review" list below is
                // where a finished batch actually gets opened, once it's actually ready.
                lastSubmittedBatchId = response.batchId
                setStatus("Invoice submitted - it's processing in the background.")
                PageStore.clear()
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                setStatus("Submit failed: ${e.message}")
            } finally {
                setSubmitting(false)
            }
        }
    }

    private suspend fun loadReadyForReview() {
        // Notification-firing itself lives in ReadyForReviewNotifier (shared with HomeActivity) -
        // this screen's own polling loop only drives the on-screen banner below.
        val ready = ReadyForReviewNotifier.checkAndNotify(this, session)
        showReadyForReview(ready)
    }

    private fun showReadyForReview(batches: List<InvoiceOcrScanBatchStatusDto>) {
        binding.readyForReviewContainer.visibility = if (batches.isEmpty()) View.GONE else View.VISIBLE
        if (batches.isEmpty()) return

        binding.textReadyForReviewHeader.text =
            "${batches.size} invoice${if (batches.size == 1) "" else "s"} ready for review"

        binding.readyForReviewList.removeAllViews()
        for (batch in batches) {
            val label = "${batch.extractedVendorName ?: "Unknown Vendor"} - ${batch.extractedInvoiceNumber ?: batch.batchId.take(8)}"
            val chip = TextView(this).apply {
                text = label
                setPadding(0, 8, 0, 8)
                setOnClickListener { openWebPage("scan-invoice-ocr/${batch.batchId}") }
            }
            binding.readyForReviewList.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun setSubmitting(submitting: Boolean) {
        binding.progressSubmit.visibility = if (submitting) View.VISIBLE else View.GONE
        binding.buttonSubmit.isEnabled = !submitting
    }

    private fun setStatus(message: String) {
        binding.textStatus.text = message
    }
}
