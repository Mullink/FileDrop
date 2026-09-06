package com.liquorbee.invoicescanner.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.liquorbee.invoicescanner.R
import com.liquorbee.invoicescanner.databinding.ActivityStagedInvoiceDetailBinding
import com.liquorbee.invoicescanner.network.AddStagedLineItemRequestDto
import com.liquorbee.invoicescanner.network.ApiClient
import com.liquorbee.invoicescanner.network.OcrScannedInvoiceHeaderDto
import com.liquorbee.invoicescanner.network.OcrScannedInvoiceLineItemDto
import com.liquorbee.invoicescanner.network.QuanticInventorySearchResultDto
import com.liquorbee.invoicescanner.network.SessionManager
import com.liquorbee.invoicescanner.network.UpdateStagedInvoiceRequestDto
import com.liquorbee.invoicescanner.network.UpdateStagedLineItemRequestDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Native equivalent of ocr-staged-invoice-detail.component.ts - same endpoints, same edit/save/
// reprice/push-to-PO actions and enablement rules (canRecalculate/canPushToPurchaseOrders/
// hasUnsavedChanges), reimplemented against the shared OcrScannedInvoiceLineItemDto model that
// StagedInvoiceLineAdapter mutates in place exactly like the web's [(ngModel)] bindings do.
class StagedInvoiceDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStagedInvoiceDetailBinding
    private lateinit var session: SessionManager
    private var invoiceHeaderId: Long = 0

    private var header: OcrScannedInvoiceHeaderDto? = null
    private var lines: List<OcrScannedInvoiceLineItemDto> = emptyList()
    private var originalInvoiceDate: String? = null
    private var originalCheckNumber: String? = null
    private var originalLineSnapshots: Map<Long, LineSnapshot> = emptyMap()

    private var saving = false
    private var pushing = false
    private var repricing = false

    private data class LineSnapshot(
        val unitsPerCase: Int?, val shippedCases: Int?, val shippedUnits: Int,
        val shippedAs: String, val sellBy: String, val unitCost: Double?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStagedInvoiceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.textBack.setOnClickListener { finish() }
        title = "Invoice Detail"

        session = SessionManager(this)
        invoiceHeaderId = intent.getLongExtra(StagedInvoiceListActivity.EXTRA_INVOICE_HEADER_ID, 0)

        binding.recyclerLines.layoutManager = LinearLayoutManager(this)
        binding.buttonSave.setOnClickListener { saveChanges() }
        binding.buttonPush.setOnClickListener { pushToPurchaseOrders() }
        binding.buttonRepriceBaseCost.setOnClickListener { repriceInvoice("DiscountReprice") }
        binding.buttonRepriceTrueCost.setOnClickListener { repriceInvoice("TrueCostReprice") }
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

    // Server-side search per keystroke (Products/SearchQuanticInventory) rather than a preloaded
    // whole-catalog index - this screen only needs occasional single-item lookups, not the
    // continuous heavy search Create PO's manual-item flow does.
    private fun renderItemSearchResults(query: String) {
        searchJob?.cancel()
        binding.itemSearchResults.removeAllViews()
        val q = query.trim()
        if (q.length < 2) return

        searchJob = lifecycleScope.launch {
            delay(300) // debounce - only search once typing pauses
            val results = try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.searchQuanticInventory(q)
            } catch (e: Exception) {
                return@launch // a search hiccup shouldn't interrupt typing with an error
            }
            for (result in results.take(15)) {
                val priceText = result.price?.let { "$%.2f".format(it) } ?: "$--"
                val row = TextView(this@StagedInvoiceDetailActivity).apply {
                    text = "${result.name ?: result.sku ?: result.itemId} — $priceText"
                    setPadding(12, 12, 12, 12)
                    setOnClickListener { showAddItemDialog(result) }
                }
                binding.itemSearchResults.addView(row)
            }
        }
    }

    // Reuses Create PO's Cases/Bottles confirm dialog layout exactly (dialog_add_po_item) - same
    // fields, same computed-per-unit-cost preview, just against a QuanticInventorySearchResultDto
    // instead of a ManualPOItemDto (this screen searches the live catalog directly rather than a
    // preloaded list, so there's no existing unitsPerCase/cost to prefill from - the user enters
    // this invoice's actual cost fresh).
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
        // Prefill from QuanticInventoryMetaDataReport (via SearchQuanticInventory's join) - this is
        // the real configured units/case for the item, not a guess; user can still edit it.
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
                // Bottles mode: entered cost IS the per-unit cost already. Cases mode: entered cost
                // is the CASE cost, so the real per-unit cost is that divided by unitsPerCase -
                // same convention as Create PO's identical dialog.
                val unitsPerCase = if (isBottles) 1 else (editUnitsPerCase.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1)
                val unitCost = enteredCost?.let { if (isBottles) it else it / unitsPerCase }
                val caseCost = enteredCost?.let { if (isBottles) it * unitsPerCase else it }

                addStagedLineItem(item, isBottles, unitsPerCase, qty, caseCost, unitCost, price)
            }
            .show()
    }

    private fun addStagedLineItem(
        item: QuanticInventorySearchResultDto, isBottles: Boolean, unitsPerCase: Int, qty: Int,
        caseCost: Double?, unitCost: Double?, price: Double?
    ) {
        val request = AddStagedLineItemRequestDto(
            itemId = item.itemId,
            itemCode = item.sku,
            itemName = item.name,
            displayName = item.name,
            upcCode = item.upc,
            shippedAs = if (isBottles) "Individual" else "Case",
            unitsPerCase = if (isBottles) null else unitsPerCase,
            shippedCases = if (isBottles) null else qty,
            shippedUnits = if (isBottles) qty else qty * unitsPerCase,
            caseCost = caseCost,
            unitCost = unitCost,
            currentUnitPrice = price
        )

        binding.editItemSearch.setText("")
        binding.itemSearchResults.removeAllViews()

        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.addStagedLineItem(invoiceHeaderId, request)
                showMessage("Item added.", isError = false)
                load()
            } catch (e: Exception) {
                showMessage("Failed to add item: ${e.message}", isError = true)
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
                val result = api.getStagedInvoiceDetail(invoiceHeaderId)
                header = result.header
                lines = result.lines

                originalInvoiceDate = result.header.invoiceDate?.take(10)
                originalCheckNumber = result.header.checkNumber
                originalLineSnapshots = result.lines.associate {
                    it.id to LineSnapshot(it.unitsPerCase, it.shippedCases, it.shippedUnits, it.shippedAs, it.sellBy, it.unitCost)
                }

                binding.editInvoiceDate.setText(originalInvoiceDate ?: "")
                binding.editCheckNumber.setText(originalCheckNumber ?: "")

                binding.recyclerLines.adapter = StagedInvoiceLineAdapter(lines, readOnly = result.header.legacyInvoiceId != null) {
                    refreshButtonStates()
                }

                renderHeader(result.header)
                refreshButtonStates()

                binding.progressLoading.visibility = View.GONE
                binding.detailContent.visibility = View.VISIBLE
            } catch (e: Exception) {
                binding.progressLoading.visibility = View.GONE
                binding.textLoadError.visibility = View.VISIBLE
                binding.textLoadError.text = "Failed to load this invoice: ${e.message}"
            }
        }
    }

    private fun renderHeader(h: OcrScannedInvoiceHeaderDto) {
        binding.textVendorInvoice.text = "${h.vendorName} #${h.invoiceNumber}"
        val total = h.invoiceTotal?.let { "$%.2f".format(it) } ?: "—"
        binding.textStatusLine.text = "${h.status} • ${h.lineItemCount} line(s) • $total" +
            if (h.legacyInvoiceId != null) " • Staged to PO #${h.legacyInvoiceId} on ${h.pushedToPurchaseOrdersDisplay}" else ""

        val locked = h.legacyInvoiceId != null
        binding.editInvoiceDate.isEnabled = !locked
        binding.editCheckNumber.isEnabled = !locked
    }

    private fun hasUnsavedChanges(): Boolean {
        val dateChanged = binding.editInvoiceDate.text.toString().ifBlank { null } != originalInvoiceDate
        val checkChanged = binding.editCheckNumber.text.toString().ifBlank { null } != originalCheckNumber
        val lineChanged = lines.any { line ->
            val original = originalLineSnapshots[line.id] ?: return@any false
            original.unitsPerCase != line.unitsPerCase || original.shippedCases != line.shippedCases ||
                original.shippedUnits != line.shippedUnits || original.shippedAs != line.shippedAs ||
                original.sellBy != line.sellBy || original.unitCost != line.unitCost
        }
        return dateChanged || checkChanged || lineChanged
    }

    private fun canPushToPurchaseOrders(): Boolean =
        header != null && header?.legacyInvoiceId == null && !pushing && !hasUnsavedChanges()

    private fun canRecalculate(): Boolean =
        header?.status == "Staged" && header?.legacyInvoiceId == null && !repricing

    private fun refreshButtonStates() {
        val h = header ?: return
        val unsaved = hasUnsavedChanges()
        binding.buttonSave.isEnabled = unsaved && !saving && h.legacyInvoiceId == null
        binding.buttonPush.isEnabled = canPushToPurchaseOrders()
        binding.buttonPush.text = if (h.legacyInvoiceId != null) "Already in Purchase Orders" else "Stage to PO"
        binding.buttonRepriceBaseCost.isEnabled = canRecalculate()
        binding.buttonRepriceTrueCost.isEnabled = canRecalculate()
        // Adding a line to an already-pushed invoice would silently desync it from the real
        // dbo.InvoiceLines row it became - same lock as every other edit on this screen.
        val canAddItem = h.legacyInvoiceId == null
        binding.buttonAddItem.visibility = if (canAddItem) View.VISIBLE else View.GONE
        if (!canAddItem) binding.addItemContainer.visibility = View.GONE
    }

    private fun showMessage(text: String, isError: Boolean) {
        binding.textMessage.visibility = View.VISIBLE
        binding.textMessage.text = text
        binding.textMessage.setTextColor(if (isError) 0xFFC62828.toInt() else 0xFF2E7D32.toInt())
    }

    private fun totalShipped(line: OcrScannedInvoiceLineItemDto): Int =
        if (line.shippedAs == "Case") (line.unitsPerCase ?: 0) * (line.shippedCases ?: 0) else line.shippedUnits

    private fun saveChanges() {
        saving = true
        refreshButtonStates()

        val edits = lines.map {
            UpdateStagedLineItemRequestDto(
                lineId = it.id,
                unitsPerCase = it.unitsPerCase,
                shippedCases = it.shippedCases,
                shippedUnits = totalShipped(it),
                shippedAs = it.shippedAs,
                sellBy = it.sellBy,
                unitCost = it.unitCost
            )
        }
        val request = UpdateStagedInvoiceRequestDto(
            invoiceDate = binding.editInvoiceDate.text.toString().ifBlank { null },
            checkNumber = binding.editCheckNumber.text.toString().ifBlank { null },
            edits = edits
        )

        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.updateStagedLineItems(invoiceHeaderId, request)
                saving = false
                showMessage("Changes saved. Pricing has been recalculated.", isError = false)
                load()
            } catch (e: Exception) {
                saving = false
                refreshButtonStates()
                showMessage("Failed to save changes: ${e.message}", isError = true)
            }
        }
    }

    private fun pushToPurchaseOrders() {
        pushing = true
        refreshButtonStates()
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.pushToPurchaseOrders(invoiceHeaderId)
                pushing = false
                showMessage("Staged to Purchase Orders.", isError = false)
                load()
            } catch (e: Exception) {
                pushing = false
                refreshButtonStates()
                showMessage("Failed to stage this invoice to Purchase Orders: ${e.message}", isError = true)
            }
        }
    }

    private fun repriceInvoice(mode: String) {
        repricing = true
        refreshButtonStates()
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.repriceInvoice(invoiceHeaderId, mode)
                repricing = false
                showMessage(
                    if (mode == "DiscountReprice")
                        "Repriced using pre-discount cost - the discount is kept as extra margin."
                    else
                        "Repriced using your actual net cost - the discount savings are passed to the consumer.",
                    isError = false
                )
                load()
            } catch (e: Exception) {
                repricing = false
                refreshButtonStates()
                showMessage("Failed to reprice this invoice: ${e.message}", isError = true)
            }
        }
    }
}
