package com.liquorbee.invoicescanner.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.liquorbee.invoicescanner.capture.ZebraScannerController
import com.liquorbee.invoicescanner.databinding.ActivityCreatePurchaseOrderBinding
import com.liquorbee.invoicescanner.network.ApiClient
import com.liquorbee.invoicescanner.network.CreateItemForPORequestDto
import com.liquorbee.invoicescanner.network.CreateManualPORequestDto
import com.liquorbee.invoicescanner.network.CustomerInvoiceTemplateListItemDto
import com.liquorbee.invoicescanner.network.ManualPOItemDto
import com.liquorbee.invoicescanner.network.PurchaseOrderLineDto
import com.liquorbee.invoicescanner.network.SessionManager
import kotlinx.coroutines.launch
import java.util.Calendar

// Native equivalent of pos-purchase-orders.ts's manual-PO creation flow (submitManualPO() and its
// supporting loadManualPOVendors/loadManualPOItems/addManualPOItem), plus a Zebra DS4608-SR "Scan
// Mode" the web doesn't have: scanning a UPC looks it up in the already-loaded catalog, and
// prompts to create a brand-new item (Products/CreateItemForManualPO + the new
// Products/GetInfoToCreateNewItem prefill) when nothing matches. Still does NOT port the
// margin/markup-slider editing on a line - only adding items with editable cases/unit cost/sell
// price, matching the fields that actually make it into the submitted order.
class CreatePurchaseOrderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatePurchaseOrderBinding
    private lateinit var session: SessionManager

    // Same vendor source/UX as ScanActivity's vendor picker (CustomerInvoiceTemplates) rather than
    // the separate GetVendorsForManualPO list - one vendor list, managed in one place
    // (VendorManagementActivity), consistent everywhere in the app.
    private var templates: List<CustomerInvoiceTemplateListItemDto> = emptyList()
    private var allItems: List<ManualPOItemDto> = emptyList()
    private val lines = mutableListOf<PurchaseOrderLineDto>()
    private lateinit var lineAdapter: CreatePoLineAdapter
    private lateinit var zebraScanner: ZebraScannerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatePurchaseOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Create Purchase Order"

        session = SessionManager(this)
        binding.textBack.setOnClickListener { finish() }
        markRequired(binding.labelPurchaseOrder)
        markRequired(binding.labelVendor)
        markRequired(binding.labelSubmittedDate)
        markRequired(binding.labelShippedDate)

        lineAdapter = CreatePoLineAdapter(
            lines,
            onRemoved = { position ->
                lines.removeAt(position)
                lineAdapter.notifyItemRemoved(position)
                refreshLinesEmptyState()
                updateInvoiceTotal()
            },
            onLineChanged = { updateInvoiceTotal() }
        )
        binding.recyclerLines.layoutManager = LinearLayoutManager(this)
        binding.recyclerLines.adapter = lineAdapter
        refreshLinesEmptyState()
        updateInvoiceTotal()

        binding.editSubmittedDate.setOnClickListener { showDatePicker(binding.editSubmittedDate) }
        binding.editShippedDate.setOnClickListener { showDatePicker(binding.editShippedDate) }
        val today = Calendar.getInstance()
        val todayText = "%04d-%02d-%02d".format(today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH))
        binding.editSubmittedDate.setText(todayText)
        binding.editShippedDate.setText(todayText)
        binding.linkManageVendorsFromPo.setOnClickListener { startActivity(Intent(this, VendorManagementActivity::class.java)) }
        binding.buttonSubmit.setOnClickListener { submit() }

        binding.editItemSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { renderItemSearchResults(s?.toString() ?: "") }
        })

        binding.buttonModeManual.setOnClickListener { setAddItemMode(scanMode = false) }
        binding.buttonModeScan.setOnClickListener { setAddItemMode(scanMode = true) }
        // Scan Mode is the default (also reflected in the XML's initial visibility/colors) - this
        // call just keeps the two from ever drifting apart, it's not what makes Scan the default.
        setAddItemMode(scanMode = true)
        // editScanUpc stays as a manual-typing fallback (e.g. a damaged barcode) - the real scan
        // path below never touches it at all.
        binding.editScanUpc.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                val upc = binding.editScanUpc.text.toString().trim()
                if (upc.isNotEmpty()) handleScannedUpc(upc)
                true
            } else {
                false
            }
        }

        // Real USB SNAPI scanner input - works regardless of which Add-Items mode is currently
        // showing, so a scan is never lost just because Manual mode happened to be selected.
        zebraScanner = ZebraScannerController(this) { upc -> handleScannedUpc(upc) }

        // PO# + Vendor are required before Add Items unlocks - see canAddItems(). Collapsing the
        // header once both are filled in is a separate, one-time transition (tryAutoCollapse,
        // triggered on losing focus / picking a vendor) rather than something that re-runs on
        // every keystroke, which would be annoying mid-type.
        binding.editPurchaseOrder.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { updateAddItemsEnabled() }
        })
        // Keyboard "Next" jumps straight to the Vendor dropdown and opens it, instead of the user
        // having to tap it themselves - PO# -> Vendor -> (see below) Submitted/Shipped Date is one
        // continuous flow now, not four separate taps.
        binding.editPurchaseOrder.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                binding.spinnerVendor.performClick()
                true
            } else {
                false
            }
        }
        binding.spinnerVendor.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateAddItemsEnabled()
                promptDatesIfReady()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = updateAddItemsEnabled()
        }
        binding.headerToggleRow.setOnClickListener {
            setHeaderCollapsed(binding.headerFieldsContainer.visibility == View.VISIBLE)
        }
        binding.addItemsToggleRow.setOnClickListener {
            setAddItemsCollapsed(binding.addItemsBody.visibility == View.VISIBLE)
        }
        // Add Items starts collapsed - nothing to do there until PO#/Vendor/dates are set anyway
        // (see promptDatesIfReady, which auto-expands it the moment they are).
        setAddItemsCollapsed(true)
        updateAddItemsEnabled()

        loadVendorsAndItems()
    }

    private fun canAddItems(): Boolean =
        binding.editPurchaseOrder.text.toString().isNotBlank() && binding.spinnerVendor.selectedItemPosition > 0

    private fun updateAddItemsEnabled() {
        val enabled = canAddItems()
        binding.buttonModeManual.isEnabled = enabled
        binding.buttonModeScan.isEnabled = enabled
        binding.editItemSearch.isEnabled = enabled
        binding.editScanUpc.isEnabled = enabled
        binding.textAddItemsHint.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    // One-time transition, triggered the moment PO#+Vendor both become valid together (right after
    // picking a vendor - see the spinner's onItemSelected), so it never collapses the header out
    // from under someone mid-type. The header row itself (headerToggleRow) stays tappable at all
    // times after that, so the user can always re-expand to double check or fix the PO#/vendor,
    // unlike the old one-way auto-collapse-then-Edit-link version.
    //
    // Rather than silently keep whatever date happened to be prefilled (today), picking the vendor
    // walks the user straight into confirming/adjusting Submitted Date then Shipped Date - each
    // date picker opens already showing the current value, so accepting it is a single tap, but
    // it's an explicit tap rather than a value nobody ever looked at.
    private fun promptDatesIfReady() {
        if (canAddItems() && binding.headerFieldsContainer.visibility == View.VISIBLE) {
            showDatePicker(binding.editSubmittedDate) {
                showDatePicker(binding.editShippedDate) {
                    setHeaderCollapsed(true)
                    setAddItemsCollapsed(false)
                }
            }
        }
    }

    private fun setAddItemsCollapsed(collapsed: Boolean) {
        binding.addItemsBody.visibility = if (collapsed) View.GONE else View.VISIBLE
        binding.addItemsChevron.rotation = if (collapsed) 0f else 180f
    }

    private fun setHeaderCollapsed(collapsed: Boolean) {
        binding.headerFieldsContainer.visibility = if (collapsed) View.GONE else View.VISIBLE
        binding.textHeaderChevron.rotation = if (collapsed) 0f else 180f
        binding.textHeaderTitle.text = if (collapsed) headerSummaryText() else "Purchase Order Details"
    }

    private fun headerSummaryText(): String {
        val po = binding.editPurchaseOrder.text.toString().trim()
        val vendorIndex = binding.spinnerVendor.selectedItemPosition - 1
        val vendorName = templates.getOrNull(vendorIndex)?.vendorName ?: ""
        return if (po.isEmpty() && vendorName.isEmpty()) "Purchase Order Details" else "PO #$po  •  $vendorName"
    }

    override fun onDestroy() {
        zebraScanner.close()
        super.onDestroy()
    }

    // Manual (typed search) vs Scan Mode (shows the scan-ready hint + manual-UPC fallback field) -
    // purely a UI toggle for which input hint is visible. The real scanner (ZebraScannerController)
    // delivers barcodes via a direct SDK callback regardless of which mode is showing.
    private fun setAddItemMode(scanMode: Boolean) {
        binding.manualModeContainer.visibility = if (scanMode) View.GONE else View.VISIBLE
        binding.scanModeContainer.visibility = if (scanMode) View.VISIBLE else View.GONE

        val white = resources.getColor(com.liquorbee.invoicescanner.R.color.white, theme)

        // setBackgroundResource() (a real drawable), not backgroundTintList - this app's
        // Theme.MaterialComponents auto-inflates plain <Button> tags as MaterialButton, which
        // doesn't reliably pick up a tint list applied this way; only swapping the actual
        // background drawable is guaranteed to render (see bg_button_primary.xml's comment).
        // Text stays white either way - the inactive state uses bg_button_muted (a solid gray
        // fill), never a light background with dark text (that read as unreadable black). The
        // active side also gets a white border ring (bg_button_primary_active) so it reads as
        // "selected" beyond just its fill color.
        binding.buttonModeManual.setBackgroundResource(if (scanMode) com.liquorbee.invoicescanner.R.drawable.bg_button_muted else com.liquorbee.invoicescanner.R.drawable.bg_button_primary_active)
        binding.buttonModeManual.setTextColor(white)
        binding.buttonModeScan.setBackgroundResource(if (scanMode) com.liquorbee.invoicescanner.R.drawable.bg_button_primary_active else com.liquorbee.invoicescanner.R.drawable.bg_button_muted)
        binding.buttonModeScan.setTextColor(white)

        // Deliberately no requestFocus() on editScanUpc here - the real scanner
        // (ZebraScannerController) delivers barcodes via a direct SDK callback and needs no
        // focused view at all. Forcing focus (and the keyboard that comes with it) was fighting
        // the user's ability to scroll down to Order Lines: a ScrollView auto-scrolls back to
        // whatever child currently holds focus every time its content changes, which happens on
        // every single scan.
    }

    private fun handleScannedUpc(upc: String) {
        binding.editScanUpc.setText("")
        // The physical scanner delivers barcodes independent of the UI's enabled/disabled state -
        // this guard is what actually stops a scan from doing anything before PO#/Vendor are set,
        // not just the Scan/Manual buttons being greyed out.
        if (!canAddItems()) {
            Toast.makeText(this, "Fill in Purchase Order # and Vendor first.", Toast.LENGTH_SHORT).show()
            return
        }
        val existing = allItems.firstOrNull { it.upcCode == upc }
        if (existing != null) {
            binding.textScanStatus.text = ""
            addItem(existing)
            return
        }

        binding.progressScanLookup.visibility = View.VISIBLE
        binding.textScanStatus.text = "No match yet for $upc - looking it up..."
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                val suggestion = api.getInfoToCreateNewItem(upc)
                binding.progressScanLookup.visibility = View.GONE
                binding.textScanStatus.text = ""
                showCreateNewItemDialog(upc, suggestion.name, suggestion.sku, suggestion.size, suggestion.unitsPerCase)
            } catch (e: Exception) {
                rethrowIfCancelled(e)
                binding.progressScanLookup.visibility = View.GONE
                // Lookup itself failing (network hiccup) shouldn't block creating the item by
                // hand - still open the form, just with nothing prefilled beyond the scanned UPC.
                showCreateNewItemDialog(upc, null, null, null, 12)
            }
        }
    }

    private fun showCreateNewItemDialog(upc: String, suggestedName: String?, suggestedSku: String?, suggestedSize: String?, suggestedUnitsPerCase: Int) {
        val view = layoutInflater.inflate(com.liquorbee.invoicescanner.R.layout.dialog_create_new_item, null)
        val textUpc = view.findViewById<TextView>(com.liquorbee.invoicescanner.R.id.textNewItemUpc)
        val editName = view.findViewById<android.widget.EditText>(com.liquorbee.invoicescanner.R.id.editNewItemName)
        val editSku = view.findViewById<android.widget.EditText>(com.liquorbee.invoicescanner.R.id.editNewItemSku)
        val editSize = view.findViewById<android.widget.EditText>(com.liquorbee.invoicescanner.R.id.editNewItemSize)
        val editUnitsPerCase = view.findViewById<android.widget.EditText>(com.liquorbee.invoicescanner.R.id.editNewItemUnitsPerCase)
        val editUnitCost = view.findViewById<android.widget.EditText>(com.liquorbee.invoicescanner.R.id.editNewItemUnitCost)
        val editPrice = view.findViewById<android.widget.EditText>(com.liquorbee.invoicescanner.R.id.editNewItemPrice)

        textUpc.text = upc
        editName.setText(suggestedName ?: "")
        editSku.setText(suggestedSku ?: "")
        editSize.setText(suggestedSize ?: "")
        editUnitsPerCase.setText(suggestedUnitsPerCase.toString())

        AlertDialog.Builder(this)
            .setTitle("Create New Item")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                val name = editName.text.toString().trim()
                val sku = editSku.text.toString().trim()
                val unitsPerCase = editUnitsPerCase.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 12
                val unitCost = editUnitCost.text.toString().toDoubleOrNull() ?: 0.0
                val price = editPrice.text.toString().toDoubleOrNull() ?: 0.0

                if (name.isEmpty() || sku.isEmpty() || price <= 0.0) {
                    Toast.makeText(this, "Name, SKU, and Sell Price are required.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                createNewItem(CreateItemForPORequestDto(
                    name = name, sku = sku, upc = upc, unitsPerCase = unitsPerCase,
                    unitCost = unitCost, price = price, msrp = 0.0, qtyInHand = 0
                ))
            }
            .show()
    }

    // CreateItemForManualPO creates the item directly in Quantic POS (not our own DB) and runs a
    // full inventory sync before returning - by the time it succeeds the new item is already in
    // GetItemsForManualPO's catalog, so refetching it is how its real ItemId is found (the create
    // endpoint itself returns no ItemId - see the endpoint's own comment on the backend).
    private fun createNewItem(request: CreateItemForPORequestDto) {
        binding.progressScanLookup.visibility = View.VISIBLE
        binding.textScanStatus.text = "Creating ${request.name}..."
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.createItemForManualPO(request)
                allItems = api.getItemsForManualPO()
                binding.progressScanLookup.visibility = View.GONE
                binding.textScanStatus.text = ""
                val created = allItems.firstOrNull { it.upcCode == request.upc }
                if (created != null) {
                    addItem(created)
                } else {
                    Toast.makeText(this@CreatePurchaseOrderActivity, "Item created, but it hasn't synced into the catalog yet - scan it again in a moment.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                rethrowIfCancelled(e)
                binding.progressScanLookup.visibility = View.GONE
                binding.textScanStatus.text = "Failed to create item: ${e.message}"
            }
        }
    }

    // Appends a red " *" to a field label - same required-field convention as ScanActivity's
    // Vendor label, just done via a span here since these labels are plain single TextViews.
    private fun markRequired(label: TextView) {
        val text = android.text.SpannableString("${label.text} *")
        text.setSpan(
            android.text.style.ForegroundColorSpan(resources.getColor(com.liquorbee.invoicescanner.R.color.error_red, theme)),
            text.length - 1, text.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        label.text = text
    }

    private fun loadVendorsAndItems() {
        loadTemplates()
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                allItems = api.getItemsForManualPO()
                // Off the main thread - building this index is itself an O(n) pass over the whole
                // catalog (15-30k items) and has no reason to block the UI while it runs.
                searchIndex = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    allItems.map { Triple(it, "${it.itemName.orEmpty()} ${it.itemCode.orEmpty()}".lowercase(), it.upcCode.orEmpty().lowercase()) }
                }
            } catch (e: Exception) {
                rethrowIfCancelled(e)
                Toast.makeText(this@CreatePurchaseOrderActivity, "Failed to load item catalog: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Mirrors ScanActivity.loadTemplates() exactly - same vendor source, same mandatory-selection
    // placeholder, same selection-preserving reload on resume (so returning from Manage Vendors
    // after adding one doesn't reset whatever was already picked here).
    private fun loadTemplates() {
        val previouslySelectedId = templates.getOrNull(binding.spinnerVendor.selectedItemPosition - 1)?.id

        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                templates = api.getTemplates().filter { it.isActive }
                val names = listOf("Select a vendor...") + templates.map { it.vendorName }
                binding.spinnerVendor.adapter = ArrayAdapter(this@CreatePurchaseOrderActivity, android.R.layout.simple_spinner_dropdown_item, names)
                val restoredIndex = templates.indexOfFirst { it.id == previouslySelectedId }
                binding.spinnerVendor.setSelection(if (restoredIndex >= 0) restoredIndex + 1 else 0)
            } catch (e: Exception) {
                rethrowIfCancelled(e)
                Toast.makeText(this@CreatePurchaseOrderActivity, "Could not load vendor list: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up a vendor added/toggled on VendorManagementActivity while this screen was
        // backgrounded.
        loadTemplates()
    }

    // onConfirmed lets the vendor-selection flow chain Submitted Date -> Shipped Date -> collapse
    // (see promptDatesIfReady) - a plain tap on either field (their existing onClickListener) still
    // just opens the picker with no chaining, since it passes no callback.
    private fun showDatePicker(target: android.widget.EditText, onConfirmed: (() -> Unit)? = null) {
        val cal = Calendar.getInstance()
        // Open already showing whatever the field currently holds (e.g. today's prefilled default)
        // rather than always resetting to today, so re-confirming an already-correct date is a
        // single tap on today's highlighted day.
        target.text.toString().split("-").takeIf { it.size == 3 }?.let { (y, m, d) ->
            y.toIntOrNull()?.let { cal.set(Calendar.YEAR, it) }
            m.toIntOrNull()?.let { cal.set(Calendar.MONTH, it - 1) }
            d.toIntOrNull()?.let { cal.set(Calendar.DAY_OF_MONTH, it) }
        }
        DatePickerDialog(this, { _, year, month, day ->
            target.setText("%04d-%02d-%02d".format(year, month + 1, day))
            onConfirmed?.invoke()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    // Precomputed once per catalog load (see loadVendorsAndItems) rather than re-lowercasing 3
    // string fields per item on every single keystroke - with a 15-30k item catalog that repeated
    // work was the actual cause of the search crashing/freezing the app (see searchJob below for
    // the other half of the fix).
    private var searchIndex: List<Triple<ManualPOItemDto, String, String>> = emptyList()
    private var searchJob: kotlinx.coroutines.Job? = null

    // Matches onManualPOSearch()'s filter fields exactly (name/code/UPC), capped at a reasonable
    // number of on-screen results rather than the web's scrollable dropdown. Debounced + run off
    // the main thread + lazy-evaluated (asSequence + take, so it stops scanning as soon as 15
    // matches are found instead of always walking the entire catalog) - a plain
    // allItems.filter{}.take(15) on every keystroke over 15-30k items was blocking the UI thread
    // long enough to crash/ANR on the POS terminal hardware this ships to.
    private fun renderItemSearchResults(query: String) {
        searchJob?.cancel()
        binding.itemSearchResults.removeAllViews()

        val q = query.lowercase().trim()
        if (q.length < 2) return // avoid a full-catalog scan on a single keystroke

        searchJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(250) // debounce - only search once typing pauses
            val matches = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                searchIndex.asSequence()
                    .filter { (_, nameCodeLower, upcLower) -> nameCodeLower.contains(q) || upcLower.contains(q) }
                    .take(15)
                    .map { it.first }
                    .toList()
            }

            for (item in matches) {
                // One item with unexpected data (catalogs this size routinely have a few) must
                // never take the whole search results list down with it.
                try {
                    val priceText = try { "$%.2f".format(item.unitCost) } catch (e: Exception) { "$--" }
                    val row = TextView(this@CreatePurchaseOrderActivity).apply {
                        text = "${item.displayName ?: item.itemName ?: item.itemCode} — $priceText/unit"
                        setPadding(12, 12, 12, 12)
                        setOnClickListener { addItem(item) }
                    }
                    binding.itemSearchResults.addView(row)
                } catch (e: Exception) {
                    // Skip this one row rather than crash the whole search.
                }
            }
        }
    }

    // Matches addManualPOItem()/confirmManualPOItemModal: selecting a search result doesn't add it
    // straight to the order - it opens a focused Receive Type (Cases/Bottles) + quantity/cost/price
    // confirm dialog first, matching setManualPOUnitMode()'s exact semantics (Bottles mode pins
    // unitsPerCase to 1 so the quantity entered IS the bottle count; Cases mode uses the item's real
    // catalog unitsPerCase). Only added to the order on "Add" - Cancel discards it, same as the web's
    // cancelAddManualPOItem().
    private fun addItem(item: ManualPOItemDto) {
        binding.editItemSearch.setText("")
        binding.itemSearchResults.removeAllViews()

        val existingIndex = lines.indexOfFirst { it.itemId == item.itemId }
        if (existingIndex >= 0) {
            // Re-scanning/re-selecting an item already on the order merges into that line
            // (+1 whatever unit it's already tracked in - cases or bottles) instead of silently
            // doing nothing or adding a confusing duplicate row.
            val line = lines[existingIndex]
            val unitsPerCase = line.unitsPerCase ?: 1
            line.shippedCases = (line.shippedCases ?: 0) + 1
            line.shippedUnits = (line.shippedCases ?: 0) * unitsPerCase
            lineAdapter.notifyItemChanged(existingIndex)
            updateInvoiceTotal()
            Toast.makeText(this, "${item.displayName ?: item.itemName ?: item.itemCode}: now ${line.shippedCases} on this order.", Toast.LENGTH_SHORT).show()
            return
        }

        showAddItemDialog(item)
    }

    private fun showAddItemDialog(item: ManualPOItemDto) {
        val view = layoutInflater.inflate(com.liquorbee.invoicescanner.R.layout.dialog_add_po_item, null)
        val textItemName = view.findViewById<TextView>(com.liquorbee.invoicescanner.R.id.textItemName)
        val radioGroup = view.findViewById<android.widget.RadioGroup>(com.liquorbee.invoicescanner.R.id.radioReceiveType)
        val radioCases = view.findViewById<android.widget.RadioButton>(com.liquorbee.invoicescanner.R.id.radioCases)
        val textQtyLabel = view.findViewById<TextView>(com.liquorbee.invoicescanner.R.id.textQtyLabel)
        val editQty = view.findViewById<android.widget.EditText>(com.liquorbee.invoicescanner.R.id.editQty)
        val textUnitCostLabel = view.findViewById<TextView>(com.liquorbee.invoicescanner.R.id.textUnitCostLabel)
        val editUnitCost = view.findViewById<android.widget.EditText>(com.liquorbee.invoicescanner.R.id.editUnitCost)
        val editPrice = view.findViewById<android.widget.EditText>(com.liquorbee.invoicescanner.R.id.editPrice)
        val unitsPerCaseContainer = view.findViewById<View>(com.liquorbee.invoicescanner.R.id.unitsPerCaseContainer)
        val editUnitsPerCase = view.findViewById<android.widget.EditText>(com.liquorbee.invoicescanner.R.id.editUnitsPerCase)
        val textComputedUnitCost = view.findViewById<TextView>(com.liquorbee.invoicescanner.R.id.textComputedUnitCost)

        textItemName.text = item.displayName ?: item.itemName ?: item.itemCode
        editUnitsPerCase.setText((item.unitsPerCase.takeIf { it > 0 } ?: 1).toString())
        editPrice.setText("%.2f".format(item.currentUnitPrice))

        // Cases mode: the field is the CASE cost (what's printed on the invoice) and Unit Cost is
        // derived by dividing it by Units Per Case - never a number the user has to pre-divide by
        // hand. Bottles mode: the field IS the per-bottle cost directly, no division, no Units Per
        // Case field at all (it's pinned to 1 and doesn't apply).
        fun refreshCasesMode() {
            val isCases = radioGroup.checkedRadioButtonId == radioCases.id
            unitsPerCaseContainer.visibility = if (isCases) View.VISIBLE else View.GONE
            textUnitCostLabel.text = if (isCases) "Case Cost ($)" else "Unit Cost ($)"
            textQtyLabel.text = if (isCases) "Cases" else "Bottles"
        }

        fun refreshComputedUnitCost() {
            if (radioGroup.checkedRadioButtonId != radioCases.id) return
            val caseCost = editUnitCost.text.toString().toDoubleOrNull()
            val unitsPerCase = editUnitsPerCase.text.toString().toIntOrNull()
            textComputedUnitCost.text = if (caseCost != null && unitsPerCase != null && unitsPerCase > 0) {
                "= $%.4f per unit".format(caseCost / unitsPerCase)
            } else {
                ""
            }
        }

        editUnitCost.setText("%.2f".format(item.unitCost * (item.unitsPerCase.takeIf { it > 0 } ?: 1)))
        refreshCasesMode()
        refreshComputedUnitCost()

        radioGroup.setOnCheckedChangeListener { _, _ ->
            refreshCasesMode()
            // Switching mode changes what the same number in the field means (case vs. per-unit) -
            // reset it to a sensible starting point for the newly-selected mode rather than
            // carrying over a value entered under the other mode's meaning.
            editUnitCost.setText("%.2f".format(
                if (radioGroup.checkedRadioButtonId == radioCases.id) item.unitCost * (item.unitsPerCase.takeIf { it > 0 } ?: 1)
                else item.unitCost
            ))
            refreshComputedUnitCost()
        }
        editUnitCost.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { refreshComputedUnitCost() }
        })
        editUnitsPerCase.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { refreshComputedUnitCost() }
        })

        AlertDialog.Builder(this)
            .setTitle("Add Item")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add") { _, _ ->
                val isBottles = radioGroup.checkedRadioButtonId != radioCases.id
                val qty = editQty.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
                val enteredCost = editUnitCost.text.toString().toDoubleOrNull() ?: item.unitCost
                val price = editPrice.text.toString().toDoubleOrNull() ?: item.currentUnitPrice
                // Bottles mode pins unitsPerCase to 1, exactly like setManualPOUnitMode() - the
                // "cases" input is then a direct bottle count, and shippedUnits = qty either way.
                val unitsPerCase = if (isBottles) 1 else (editUnitsPerCase.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1)
                // Cases mode: enteredCost is the CASE cost, so the real per-unit cost is that
                // divided by unitsPerCase. Bottles mode: enteredCost already IS the per-unit cost.
                val unitCost = if (isBottles) enteredCost else enteredCost / unitsPerCase

                // Most-recently-added item goes at the TOP of the list, not the bottom - easiest
                // to spot what you just scanned/added without hunting through a long order.
                lines.add(0,
                    PurchaseOrderLineDto(
                        lineId = 0,
                        invoiceId = 0,
                        itemId = item.itemId,
                        itemCode = item.itemCode,
                        itemName = item.itemName,
                        displayName = item.displayName,
                        upcCode = item.upcCode,
                        itemCategory = item.itemCategory,
                        subCategory = null,
                        unitsPerCase = unitsPerCase,
                        shippedCases = qty,
                        shippedUnits = qty * unitsPerCase,
                        caseCost = unitCost * unitsPerCase,
                        caseDiscount = null,
                        averageCost = null,
                        unitCost = unitCost,
                        currentUnitPrice = price,
                        suggestedUnitPrice = item.suggestedUnitPrice,
                        desiredMarkUp = item.desiredMarkUpMargin,
                        currentMarkUp = null,
                        currentMargin = null,
                        marginStatus = null,
                        isNotLinked = false,
                        hasDuplicates = false,
                        isNewItem = false,
                        hasPriceChange = false,
                        marketAvg = null,
                        oldCog = null,
                        originalShippedCases = null,
                        currentQty = null
                    )
                )
                lineAdapter.notifyItemInserted(0)
                refreshLinesEmptyState()
                updateInvoiceTotal()
            }
            .show()
    }

    private fun refreshLinesEmptyState() {
        binding.textNoLines.visibility = if (lines.isEmpty()) View.VISIBLE else View.GONE
    }

    // Running tab shown above Order Lines - sum of every line's (cases/bottles shipped x case
    // cost), recomputed on every add/remove/edit so it's always accurate, not just at submit time.
    private fun updateInvoiceTotal() {
        val total = lines.sumOf { (it.shippedCases ?: 0) * (it.caseCost ?: 0.0) }
        binding.textInvoiceTotal.text = "Total: $%.2f".format(total)
    }

    // Matches submitManualPO()'s validation order exactly.
    private fun submit() {
        binding.textError.visibility = View.GONE
        val purchaseOrder = binding.editPurchaseOrder.text.toString().trim()
        // Index 0 is the "Select a vendor..." placeholder - never a real choice, same rule as
        // ScanActivity's spinner.
        val vendorIndex = binding.spinnerVendor.selectedItemPosition - 1
        val vendorSuffix = templates.getOrNull(vendorIndex)?.vendorName ?: ""
        val submittedDate = binding.editSubmittedDate.text.toString().trim()
        val shippedDate = binding.editShippedDate.text.toString().trim()

        val error = when {
            purchaseOrder.isEmpty() -> "Purchase Order is required."
            vendorSuffix.isEmpty() -> "Pick a vendor first."
            submittedDate.isEmpty() -> "Submitted Date is required."
            shippedDate.isEmpty() -> "Shipped Date is required."
            lines.isEmpty() -> "Add at least one item before submitting."
            else -> null
        }
        if (error != null) {
            binding.textError.visibility = View.VISIBLE
            binding.textError.text = error
            return
        }

        binding.buttonSubmit.isEnabled = false
        val request = CreateManualPORequestDto(
            purchaseOrder = purchaseOrder,
            vendor = "MANUAL-$vendorSuffix",
            submittedDate = submittedDate,
            shippedDate = shippedDate,
            lines = lines
        )

        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                val newInvoiceId = api.createManualStagedOrder(request)
                Toast.makeText(this@CreatePurchaseOrderActivity, "Purchase order created.", Toast.LENGTH_LONG).show()
                AppNotifications.show(
                    this@CreatePurchaseOrderActivity,
                    "Purchase order created",
                    "PO #$purchaseOrder - $vendorSuffix",
                    PurchaseOrderListActivity::class.java
                )
                promptForCheckNumber(newInvoiceId)
            } catch (e: Exception) {
                rethrowIfCancelled(e)
                binding.buttonSubmit.isEnabled = true
                binding.textError.visibility = View.VISIBLE
                binding.textError.text = "Failed to create purchase order: ${e.message}"
            }
        }
    }

    // Asked AFTER the PO already exists, not on the create form itself - a check number usually
    // isn't known/written yet at the moment of creating the order, and it's genuinely optional
    // (Skip just finishes with no call at all).
    private fun promptForCheckNumber(invoiceId: Long) {
        val input = android.widget.EditText(this).apply {
            hint = "Check number (optional)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle("Check Number")
            .setMessage("Add a check number for this order? You can skip this and add it later.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val checkNumber = input.text.toString().trim()
                if (checkNumber.isEmpty()) {
                    finish()
                } else {
                    saveCheckNumberThenFinish(invoiceId, checkNumber)
                }
            }
            .setNegativeButton("Skip") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun saveCheckNumberThenFinish(invoiceId: Long, checkNumber: String) {
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.updateInvoiceCheckNumber(invoiceId, checkNumber)
            } catch (e: Exception) {
                rethrowIfCancelled(e)
                Toast.makeText(this@CreatePurchaseOrderActivity, "Order created, but failed to save check number: ${e.message}", Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    // A CancellationException reaches here whenever this activity finishes (finish()/back/nav
    // away) while one of its lifecycleScope coroutines is still in flight - e.g. the item-catalog
    // load kicked off in onCreate hasn't finished yet when the user creates the PO and it navigates
    // back. That's normal structured-concurrency cancellation, not a real failure, so it must never
    // be shown to the user as "Failed to load item catalog: Job was cancelled" - it has to be
    // rethrown so the coroutine actually finishes cancelling instead of swallowed by the generic
    // catch (e: Exception) below it.
    private fun rethrowIfCancelled(e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
    }
}
