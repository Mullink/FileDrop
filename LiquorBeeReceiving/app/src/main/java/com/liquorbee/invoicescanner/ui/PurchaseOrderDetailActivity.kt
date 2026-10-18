package com.liquorbee.invoicescanner.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.content.Intent
import com.liquorbee.invoicescanner.network.requireSuccess
import com.liquorbee.invoicescanner.network.apiError
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.liquorbee.invoicescanner.R
import com.liquorbee.invoicescanner.databinding.ActivityPurchaseOrderDetailBinding
import com.liquorbee.invoicescanner.network.AddInvoiceLineRequestDto
import com.liquorbee.invoicescanner.network.ApiClient
import com.liquorbee.invoicescanner.network.PurchaseOrderHeaderDto
import com.liquorbee.invoicescanner.network.PurchaseOrderLineDto
import com.liquorbee.invoicescanner.network.QuanticInventorySearchResultDto
import com.liquorbee.invoicescanner.network.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private var itemsExpanded = false
    private var invoicePageNumber = 1
    private var showOnlyReview = false
    private var reviewLineIds = emptySet<Long>()
    private fun needsReview(line: PurchaseOrderLineDto) = line.lineId in reviewLineIds || line.needsDoubleCheck || line.isNotLinked || line.hasDuplicates
    private var isScanned = false
    private var scanReady = false
    private var returningFromReview = false
    private fun openScanReview() {
        if (hasUnsavedChanges()) { showMessage("Save your current changes before reviewing the scan.", true); return }
        returningFromReview = true
        startActivity(Intent(this, ScanOrderReviewActivity::class.java).putExtra(PurchaseOrderListActivity.EXTRA_INVOICE_ID, invoiceId))
    }
    override fun onResume() {
        super.onResume()
        if (returningFromReview) { returningFromReview = false; load() }
    }
    private var saving = false
    private var receiving = false

    private data class LineSnapshot(val shippedCases: Int?, val shippedUnits: Int?, val currentUnitPrice: Double?, val unitCost: Double?, val caseCost: Double?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPurchaseOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.textBack.setOnClickListener { finish() }
        title = "Order Detail"

        session = SessionManager(this)
        invoiceId = intent.getLongExtra(PurchaseOrderListActivity.EXTRA_INVOICE_ID, 0)

        binding.recyclerLines.layoutManager = LinearLayoutManager(this)
        binding.buttonExpandItems.setOnClickListener { itemsExpanded=!itemsExpanded; applyItemExpansion() }
        binding.buttonPreviousPage.setOnClickListener { invoicePageNumber--; renderInvoicePage() }
        binding.buttonNextPage.setOnClickListener { invoicePageNumber++; renderInvoicePage() }
        binding.buttonNeedsReview.setOnClickListener { showOnlyReview=true; invoicePageNumber=1; renderInvoicePage() }
        binding.buttonAllItems.setOnClickListener { showOnlyReview=false; invoicePageNumber=1; renderInvoicePage() }
        binding.buttonReviewScan.setOnClickListener { openScanReview() }
        binding.buttonSave.setOnClickListener { saveChanges() }
        binding.buttonReceive.setOnClickListener { confirmReceive() }
        binding.buttonSaveCheckNumber.setOnClickListener { saveCheckNumber() }
        binding.buttonMoreOptions.setOnClickListener { showMoreOptionsMenu() }
        binding.detailToggleRow.setOnClickListener {
            setDetailCollapsed(binding.detailBody.visibility == View.VISIBLE)
        }
        binding.buttonAddItem.setOnClickListener {
            binding.addItemContainer.visibility = if (binding.addItemContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        binding.editItemSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { renderItemSearchResults(s?.toString() ?: "") }
        })

        load()
    }

    private var searchJob: Job? = null

    // Same server-side-search-per-keystroke pattern as StagedInvoiceDetailActivity's Add Item.
    private fun renderItemSearchResults(query: String) {
        searchJob?.cancel()
        binding.itemSearchResults.removeAllViews()
        val q = query.trim()
        if (q.length < 2) return

        searchJob = lifecycleScope.launch {
            delay(300)
            val results = try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.searchQuanticInventory(q)
            } catch (e: Exception) {
                return@launch
            }
            for (result in results.take(15)) {
                val priceText = result.price?.let { "$%.2f".format(it) } ?: "$--"
                val row = TextView(this@PurchaseOrderDetailActivity).apply {
                    text = "${result.name ?: result.sku ?: result.itemId} — $priceText"
                    setPadding(12, 12, 12, 12)
                    setOnClickListener { showAddItemDialog(result) }
                }
                binding.itemSearchResults.addView(row)
            }
        }
    }

    // Reuses the exact same Cases/Bottles confirm dialog as StagedInvoiceDetailActivity's Add
    // Item - same fields, same computed-per-unit-cost preview, just posting to AddInvoiceLine
    // (a real InvoiceLines row) instead of AddStagedLineItem.
    private fun showAddItemDialog(item: QuanticInventorySearchResultDto) {
        val view = layoutInflater.inflate(R.layout.dialog_add_po_item, null)
        val textItemName = view.findViewById<TextView>(R.id.textItemName)
        val radioGroup = view.findViewById<RadioGroup>(R.id.radioReceiveType)
        val radioCases = view.findViewById<RadioButton>(R.id.radioCases)
        val textQtyLabel = view.findViewById<TextView>(R.id.textQtyLabel)
        val editQty = view.findViewById<EditText>(R.id.editQty)
        val textUnitCostLabel = view.findViewById<TextView>(R.id.textUnitCostLabel)
        val editUnitCost = view.findViewById<EditText>(R.id.editUnitCost)
        val editPrice = view.findViewById<EditText>(R.id.editPrice)
        val unitsPerCaseContainer = view.findViewById<View>(R.id.unitsPerCaseContainer)
        val editUnitsPerCase = view.findViewById<EditText>(R.id.editUnitsPerCase)
        val textComputedUnitCost = view.findViewById<TextView>(R.id.textComputedUnitCost)

        textItemName.text = item.name ?: item.sku ?: item.itemId
        // Prefill from QuanticInventoryMetaDataReport (via SearchQuanticInventory's join) - the
        // real configured units/case for the item, not a guess; user can still edit it.
        editUnitsPerCase.setText((item.unitsPerCase ?: 1).toString())
        editPrice.setText(item.price?.let { "%.2f".format(it) } ?: "")

        fun refreshCasesMode() {
            val isCases = radioGroup.checkedRadioButtonId == radioCases.id
            unitsPerCaseContainer.visibility = if (isCases) View.VISIBLE else View.GONE
            textUnitCostLabel.text = if (isCases) "Case Cost ($)" else "Unit Cost ($)"
            textQtyLabel.text = if (isCases) "Cases" else "Bottles"
        }
        fun refreshComputedUnitCost() {
            if (radioGroup.checkedRadioButtonId != radioCases.id) {
                textComputedUnitCost.text = ""
                return
            }
            val caseCost = editUnitCost.text.toString().toDoubleOrNull()
            val unitsPerCase = editUnitsPerCase.text.toString().toIntOrNull()
            textComputedUnitCost.text = if (caseCost != null && unitsPerCase != null && unitsPerCase > 0)
                "= $%.4f per unit".format(caseCost / unitsPerCase) else ""
        }
        refreshCasesMode()
        refreshComputedUnitCost()
        radioGroup.setOnCheckedChangeListener { _, _ -> refreshCasesMode(); refreshComputedUnitCost() }
        editUnitCost.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { refreshComputedUnitCost() }
        })
        editUnitsPerCase.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { refreshComputedUnitCost() }
        })

        AlertDialog.Builder(this)
            .setTitle("Add Item")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add") { _, _ ->
                val isBottles = radioGroup.checkedRadioButtonId != radioCases.id
                val qty = editQty.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
                val enteredCost = editUnitCost.text.toString().toDoubleOrNull()
                val price = editPrice.text.toString().toDoubleOrNull()
                val unitsPerCase = if (isBottles) 1 else (editUnitsPerCase.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1)
                val unitCost = enteredCost?.let { if (isBottles) it else it / unitsPerCase }
                val caseCost = enteredCost?.let { if (isBottles) it * unitsPerCase else it }

                addInvoiceLineItem(item, isBottles, unitsPerCase, qty, caseCost, unitCost, price)
            }
            .show()
    }

    private fun addInvoiceLineItem(
        item: QuanticInventorySearchResultDto, isBottles: Boolean, unitsPerCase: Int, qty: Int,
        caseCost: Double?, unitCost: Double?, price: Double?
    ) {
        val request = AddInvoiceLineRequestDto(
            itemId = item.itemId,
            itemCode = item.sku,
            itemName = item.name,
            displayName = item.name,
            upcCode = item.upc,
            unitsPerCase = if (isBottles) null else unitsPerCase,
            shippedCases = if (isBottles) null else qty,
            shippedUnits = if (isBottles) qty else qty * unitsPerCase,
            caseCost = caseCost,
            unitCost = unitCost,
            currentUnitPrice = price,
            suggestedUnitPrice = price
        )

        binding.editItemSearch.setText("")
        binding.itemSearchResults.removeAllViews()

        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.addInvoiceLine(invoiceId, request)
                showMessage("Item added.", isError = false)
                load()
            } catch (e: Exception) {
                showMessage("Failed to add item: ${apiError(e)}", isError = true)
            }
        }
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

                originalLineSnapshots = lines.associate { it.lineId to LineSnapshot(it.shippedCases, it.shippedUnits, it.currentUnitPrice, it.unitCost, it.caseCost) }

                val h = header
                binding.editCheckNumber.setText(h?.checkNumber ?: "")

                isScanned = !h?.ocrBatchId.isNullOrBlank() || intent.getBooleanExtra("scannedInvoice", false)
                scanReady = false
                binding.scanReviewPanel.visibility = if(isScanned) View.VISIBLE else View.GONE
                if(isScanned) {
                    try {
                        val review = api.getOrderCorrections(invoiceId)
                        reviewLineIds = review.lines.filter { it.needsReview }.map { it.lineId }.toSet()
                        val count = reviewLineIds.size
                        scanReady = review.isEditable && count == 0 && review.reconciliation.isBalanced
                        binding.textScanReview.text = if(scanReady) "Scan checked. Save retail price changes, then receive." else "$count item(s) need review. Check scanned values and invoice totals before receiving."
                    } catch(e: kotlinx.coroutines.CancellationException) { throw e }
                    catch(e: Exception) { binding.textScanReview.text = "Scan review could not load: " + apiError(e) }
                }
                renderInvoicePage()

                renderHeader()
                refreshButtonStates()

                binding.progressLoading.visibility = View.GONE
                binding.detailContent.visibility = View.VISIBLE
            } catch (e: Exception) {
                binding.progressLoading.visibility = View.GONE
                binding.textLoadError.visibility = View.VISIBLE
                binding.textLoadError.text = "Failed to load this order: ${apiError(e)}"
            }
        }
    }

    private fun renderInvoicePage() {
        val filtered = if(isScanned && showOnlyReview) lines.filter { needsReview(it) } else lines
        val page = invoicePage(filtered,invoicePageNumber,if(isScanned) 20 else maxOf(1,filtered.size))
        invoicePageNumber=page.number
        binding.invoicePageControls.visibility=if(isScanned) View.VISIBLE else View.GONE
        binding.invoiceReviewFilters.visibility=if(isScanned) View.VISIBLE else View.GONE
        binding.textInvoicePage.text=page.description
        binding.buttonPreviousPage.isEnabled=page.number>1
        binding.buttonNextPage.isEnabled=page.number<page.count
        binding.buttonNeedsReview.text="Needs review ("+lines.count { needsReview(it) }+")"
        binding.buttonAllItems.text="All items ("+lines.size+")"
        binding.buttonNeedsReview.isSelected=showOnlyReview
        binding.buttonAllItems.isSelected=!showOnlyReview
        styleFilterButton(binding.buttonNeedsReview,showOnlyReview)
        styleFilterButton(binding.buttonAllItems,!showOnlyReview)
        applyItemExpansion()
        // A page is only a view; save, receive, snapshots and totals retain all lines.
        val visible=page.items.toList()
        lineAdapter=PurchaseOrderLineAdapter(visible,readOnly=header?.status!="STAGED",scanned=isScanned,
            onReview={ openScanReview() },onChanged={ updateActualTotal();refreshButtonStates() },
            onRemoved={ position ->
                val item=visible[position]
                pendingRemovedLineIds.add(item.lineId)
                lines.removeAll { it.lineId==item.lineId }
                renderInvoicePage();updateActualTotal();refreshButtonStates()
            })
        binding.recyclerLines.adapter=lineAdapter
        binding.recyclerLines.scrollToPosition(0)
    }

    private fun applyItemExpansion() {
        binding.orderDetailsScroll.visibility=if(itemsExpanded) View.GONE else View.VISIBLE
        binding.scanReviewPanel.visibility=if(isScanned && !itemsExpanded) View.VISIBLE else View.GONE
        binding.buttonExpandItems.visibility=if(isScanned) View.VISIBLE else View.GONE
        binding.buttonExpandItems.text=if(itemsExpanded) "Exit expanded" else "Expand items"
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
            original.currentUnitPrice != line.currentUnitPrice || original.unitCost != line.unitCost || original.caseCost != line.caseCost
    }

    private fun hasNegativeReceiveQuantities(): Boolean = lines.any {
        (it.shippedCases ?: 0) < 0 || (it.shippedUnits ?: 0) < 0
    }

    private fun refreshButtonStates() {
        val h = header
        val isStaged = h?.status == "STAGED"
        binding.buttonSave.isEnabled = isStaged && !saving && !receiving
        // Matches the web exactly: Receive is blocked while there are unsaved edits - save first.
        binding.buttonReceive.isEnabled = isStaged && !saving && !receiving && !hasUnsavedChanges() && (!isScanned || scanReady)
        binding.buttonReviewScan.isEnabled = !saving && !receiving && !hasUnsavedChanges()
        binding.buttonAddItem.visibility = if (isStaged) View.VISIBLE else View.GONE
        if (!isStaged) binding.addItemContainer.visibility = View.GONE
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
                for (lineId in pendingRemovedLineIds.toList()) {
                    api.removeInvoiceLine(lineId).requireSuccess()
                    pendingRemovedLineIds.remove(lineId)
                }
                pendingRemovedLineIds.clear()
                val changed = if(isScanned) lines.filter { originalLineSnapshots[it.lineId]?.currentUnitPrice != it.currentUnitPrice } else lines
                if(changed.isNotEmpty()) api.updateStagedPurchaseOrderDetails(changed).requireSuccess()
                saving = false
                showMessage("Changes saved.", isError = false)
                load()
            } catch (e: Exception) {
                saving = false
                refreshButtonStates()
                showMessage("Failed to save changes: ${apiError(e)}", isError = true)
            }
        }
    }

    private fun confirmReceive() {
        if (hasUnsavedChanges() || (isScanned && !scanReady)) { showMessage("Save changes and finish scan review before receiving.", true); return }
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
                api.receiveOrder(invoiceId, false).requireSuccess()
                receiving = false
                showMessage("Order received.", isError = false)
                load()
            } catch (e: Exception) {
                receiving = false
                refreshButtonStates()
                showMessage("Failed to receive this order: ${apiError(e)}", isError = true)
            }
        }
    }

    private fun saveCheckNumber() {
        val checkNumber = binding.editCheckNumber.text.toString()
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.updateInvoiceCheckNumber(invoiceId, checkNumber).requireSuccess()
                Toast.makeText(this@PurchaseOrderDetailActivity, "Check number saved.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@PurchaseOrderDetailActivity, "Failed to save check number: ${apiError(e)}", Toast.LENGTH_LONG).show()
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
                api.updateStagedPurchaseOrderStatus(invoiceId, status).requireSuccess()
                showMessage("Order status updated.", isError = false)
                load()
            } catch (e: Exception) {
                showMessage("Failed to update this order's status: ${apiError(e)}", isError = true)
            }
        }
    }

    // The API returns full ISO datetimes (e.g. "2026-09-05T00:00:00") for what are really just
    // dates - a receiving time-of-day was never meaningful here, so only the date part is shown.
    private fun dateOnly(raw: String): String = raw.substringBefore('T').substringBefore(' ')
}
