package com.liquorbee.invoicescanner.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.liquorbee.invoicescanner.R
import com.liquorbee.invoicescanner.databinding.ActivityPurchaseOrderDetailBinding
import com.liquorbee.invoicescanner.network.ApiClient
import com.liquorbee.invoicescanner.network.PurchaseOrderHeaderDto
import com.liquorbee.invoicescanner.network.PurchaseOrderLineDto
import com.liquorbee.invoicescanner.network.SessionManager
import kotlinx.coroutines.launch

// Native equivalent of pos-purchase-orders.ts's receive/detail flow (getInvoiceDetails,
// saveChanges, receiveOrder, saveCheckNumber, updatePOStatus). NOT ported: the missing-price/markup
// validation block in saveChanges (requires the markup-rule lookups the web page has already
// loaded), price-history/margin-slider editing, printLabels, and the Undo Delete/Undo Error
// variants (Mark as Deleted/Mark as Error themselves ARE wired up, via the More Options menu). See
// the negative-quantity guard (hasNegativeReceiveQuantities) which IS ported exactly.
class PurchaseOrderDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPurchaseOrderDetailBinding
    private lateinit var session: SessionManager
    private var invoiceId: Long = 0

    private var header: PurchaseOrderHeaderDto? = null
    private var lines: MutableList<PurchaseOrderLineDto> = mutableListOf()
    private var originalLineSnapshots: Map<Long, LineSnapshot> = emptyMap()
    private lateinit var lineAdapter: PurchaseOrderLineAdapter

    // Removing a line only hides it and adjusts the total on-screen - the actual
    // Products/RemoveInvoiceLine call for each of these goes out from saveChanges(), matching the
    // web's removeLine()/saveChanges() split exactly (see pos-purchase-orders.ts).
    private val pendingRemovedLineIds = mutableListOf<Long>()

    private var saving = false
    private var receiving = false

    private data class LineSnapshot(val shippedCases: Int?, val shippedUnits: Int?, val currentUnitPrice: Double?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPurchaseOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.textBack.setOnClickListener { finish() }
        title = "Order Detail"

        session = SessionManager(this)
        invoiceId = intent.getLongExtra(PurchaseOrderListActivity.EXTRA_INVOICE_ID, 0)

        binding.recyclerLines.layoutManager = LinearLayoutManager(this)
        binding.buttonSave.setOnClickListener { saveChanges() }
        binding.buttonReceive.setOnClickListener { confirmReceive() }
        binding.buttonSaveCheckNumber.setOnClickListener { saveCheckNumber() }
        binding.buttonMoreOptions.setOnClickListener { showMoreOptionsMenu() }
        binding.detailToggleRow.setOnClickListener {
            setDetailCollapsed(binding.detailBody.visibility == View.VISIBLE)
        }

        load()
    }

    private fun load() {
        binding.progressLoading.visibility = View.VISIBLE
        binding.textLoadError.visibility = View.GONE
        binding.detailContent.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                // The order list already has the header (vendor/status/etc.) - there's no
                // "get one header" endpoint, so re-fetch the list and find this one, same data
                // GetAllStagedPurchaseOrders returns either way.
                val headers = api.getAllStagedPurchaseOrders(50)
                header = headers.firstOrNull { it.invoiceId == invoiceId }
                lines = api.getStagedPurchaseOrderDetails(invoiceId).toMutableList()
                pendingRemovedLineIds.clear()

                originalLineSnapshots = lines.associate { it.lineId to LineSnapshot(it.shippedCases, it.shippedUnits, it.currentUnitPrice) }

                val h = header
                binding.editCheckNumber.setText(h?.checkNumber ?: "")

                val readOnly = h?.status != "STAGED"
                lineAdapter = PurchaseOrderLineAdapter(
                    lines,
                    readOnly = readOnly,
                    onChanged = { refreshButtonStates() },
                    onRemoved = { position ->
                        pendingRemovedLineIds.add(lines[position].lineId)
                        lines.removeAt(position)
                        lineAdapter.notifyItemRemoved(position)
                        updateActualTotal()
                        refreshButtonStates()
                    }
                )
                binding.recyclerLines.adapter = lineAdapter

                renderHeader()
                refreshButtonStates()

                binding.progressLoading.visibility = View.GONE
                binding.detailContent.visibility = View.VISIBLE
            } catch (e: Exception) {
                binding.progressLoading.visibility = View.GONE
                binding.textLoadError.visibility = View.VISIBLE
                binding.textLoadError.text = "Failed to load this order: ${e.message}"
            }
        }
    }

    private fun renderHeader() {
        val h = header ?: return
        binding.textVendorOrder.text = "${h.vendor ?: "(Unknown vendor)"} — Order #${h.orderId ?: h.invoiceId}"
        applyStatusBadge(h.status)
        updateActualTotal()

        val locked = h.status != "STAGED"
        binding.buttonMoreOptions.isEnabled = !locked
    }

    // Recomputes from the live `lines` list rather than the header's static lineItems/netAmount -
    // removing a line updates this immediately even though nothing has been saved to the server
    // yet, matching the web's actualTotal getter (pos-purchase-orders.ts).
    private fun updateActualTotal() {
        val h = header ?: return
        val total = lines.sumOf { (it.shippedCases ?: 0) * (it.caseCost ?: 0.0) }
        val totalCases = lines.sumOf { it.shippedCases ?: 0 }
        binding.textStatusLine.text = "${lines.size} line(s) • $totalCases case(s) • ${"$%.2f".format(total)}" +
            (h.submittedDate?.let { " • Submitted ${dateOnly(it)}" } ?: "") +
            (h.shippedDate?.let { " • Shipped ${dateOnly(it)}" } ?: "")
    }

    // Starts expanded (see the XML's default detailChevron rotation="180") - collapsing just
    // frees up screen space for Line Items on a small POS terminal, it's not gating anything.
    private fun setDetailCollapsed(collapsed: Boolean) {
        binding.detailBody.visibility = if (collapsed) View.GONE else View.VISIBLE
        binding.detailChevron.rotation = if (collapsed) 0f else 180f
    }

    // Same orange/green convention as PurchaseOrderListAdapter.applyStatusColor - matches the web's
    // pos-purchase-orders.css .status-staged/.status-received colors exactly.
    private fun applyStatusBadge(status: String?) {
        binding.textStatusBadge.text = status ?: "—"
        val colorRes = when (status?.trim()?.lowercase()) {
            "staged" -> R.color.status_staged
            "received" -> R.color.status_received
            else -> null
        }
        if (colorRes == null) {
            binding.textStatusBadge.background = null
            binding.textStatusBadge.setTextColor(Color.BLACK)
            return
        }
        val pill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(ContextCompat.getColor(this@PurchaseOrderDetailActivity, colorRes))
        }
        binding.textStatusBadge.background = pill
        binding.textStatusBadge.setTextColor(Color.WHITE)
    }

    private fun hasUnsavedChanges(): Boolean = pendingRemovedLineIds.isNotEmpty() || lines.any { line ->
        val original = originalLineSnapshots[line.lineId] ?: return@any false
        original.shippedCases != line.shippedCases || original.shippedUnits != line.shippedUnits ||
            original.currentUnitPrice != line.currentUnitPrice
    }

    private fun hasNegativeReceiveQuantities(): Boolean = lines.any {
        (it.shippedCases ?: 0) < 0 || (it.shippedUnits ?: 0) < 0
    }

    private fun refreshButtonStates() {
        val h = header
        val isStaged = h?.status == "STAGED"
        binding.buttonSave.isEnabled = isStaged && !saving && !receiving
        // Matches the web exactly: Receive is blocked while there are unsaved edits - save first.
        binding.buttonReceive.isEnabled = isStaged && !saving && !receiving && !hasUnsavedChanges()
    }

    private fun showMessage(text: String, isError: Boolean) {
        binding.textMessage.visibility = View.VISIBLE
        binding.textMessage.text = text
        binding.textMessage.setTextColor(if (isError) 0xFFC62828.toInt() else 0xFF2E7D32.toInt())
    }

    private fun saveChanges() {
        if (hasNegativeReceiveQuantities()) {
            showMessage("Shipped cases/units can't be negative.", isError = true)
            return
        }
        saving = true
        refreshButtonStates()
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                // Lines removed on-screen were only hidden/excluded from the total up to this
                // point - their actual RemoveInvoiceLine deletes go out first, matching the web's
                // saveChanges() (pos-purchase-orders.ts).
                for (lineId in pendingRemovedLineIds) {
                    api.removeInvoiceLine(lineId)
                }
                pendingRemovedLineIds.clear()
                api.updateStagedPurchaseOrderDetails(lines)
                saving = false
                showMessage("Changes saved.", isError = false)
                load()
            } catch (e: Exception) {
                saving = false
                refreshButtonStates()
                showMessage("Failed to save changes: ${e.message}", isError = true)
            }
        }
    }

    private fun confirmReceive() {
        AlertDialog.Builder(this)
            .setTitle("Receive Order")
            .setMessage("Mark this order as received? This updates on-hand quantities from the shipped cases/units above.")
            .setPositiveButton("Receive") { _, _ -> receiveOrder() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun receiveOrder() {
        if (hasNegativeReceiveQuantities()) {
            showMessage("Shipped cases/units can't be negative.", isError = true)
            return
        }
        receiving = true
        refreshButtonStates()
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                // printLabel hard-coded false - no native label-printer integration yet.
                api.receiveOrder(invoiceId, false)
                receiving = false
                showMessage("Order received.", isError = false)
                load()
            } catch (e: Exception) {
                receiving = false
                refreshButtonStates()
                showMessage("Failed to receive this order: ${e.message}", isError = true)
            }
        }
    }

    private fun saveCheckNumber() {
        val checkNumber = binding.editCheckNumber.text.toString()
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.updateInvoiceCheckNumber(invoiceId, checkNumber)
                Toast.makeText(this@PurchaseOrderDetailActivity, "Check number saved.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@PurchaseOrderDetailActivity, "Failed to save check number: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Mirrors the web's "More Options" dropdown (pos-purchase-orders.html) - Mark as Deleted and
    // Mark as Error are the same single status-update call (updateStagedPurchaseOrderStatus) with
    // a different status string, so no new backend/API surface was needed for this. Undo variants
    // are NOT ported here, matching this Activity's existing scope note at the top of the file.
    private fun showMoreOptionsMenu() {
        val popup = PopupMenu(this, binding.buttonMoreOptions)
        popup.menuInflater.inflate(com.liquorbee.invoicescanner.R.menu.menu_purchase_order_more_options, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                com.liquorbee.invoicescanner.R.id.menuMarkAsDeleted -> confirmSetStatus("DELETED", "Mark as Deleted", "Mark this order as deleted? This cannot be undone from this screen.")
                com.liquorbee.invoicescanner.R.id.menuMarkAsError -> confirmSetStatus("ERROR", "Mark as Error", "Mark this order as having an error? This flags it for review.")
            }
            true
        }
        popup.show()
    }

    private fun confirmSetStatus(status: String, title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(title) { _, _ -> setStatus(status) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setStatus(status: String) {
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.updateStagedPurchaseOrderStatus(invoiceId, status)
                showMessage("Order status updated.", isError = false)
                load()
            } catch (e: Exception) {
                showMessage("Failed to update this order's status: ${e.message}", isError = true)
            }
        }
    }

    // The API returns full ISO datetimes (e.g. "2026-09-05T00:00:00") for what are really just
    // dates - a receiving time-of-day was never meaningful here, so only the date part is shown.
    private fun dateOnly(raw: String): String = raw.substringBefore('T').substringBefore(' ')
}
