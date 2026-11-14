package com.liquorbee.invoicescanner.ui

import android.os.Bundle
import android.graphics.Color
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import com.liquorbee.invoicescanner.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Corrections use the same versioned, customer-scoped endpoints as HennyAdmin. */
class ScanOrderReviewActivity : AppCompatActivity() {
    private val api by lazy { ApiClient.buildAuthenticatedApi(SessionManager(this)) }
    private var invoiceId = 0L
    private var review: ScanOrderReview? = null
    private var products = emptyMap<Long, PurchaseOrderLineDto>()
    private var busy = false
    private var onlyIssues = true
    private var query = ""
    private var pendingLineId = 0L
    private lateinit var pageText: TextView
    private lateinit var needsReviewButton: Button
    private lateinit var allItemsButton: Button
    private lateinit var summary: TextView
    private lateinit var message: TextView
    private lateinit var list: RecyclerView
    private lateinit var totalsButton: Button
    private val adapter = ReviewAdapter()
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)) }
    private fun label(value: String, size: Float = 15f) = TextView(this).apply { text=value; textSize=size; setPadding(0,dp(5),0,dp(5)); setTextColor(Color.rgb(40,40,40)) }
    private fun action(value: String, parent: LinearLayout, click: () -> Unit): Button = Button(this).apply {
        text=value; isAllCaps=false; minHeight=dp(48); parent.addView(this, ViewGroup.LayoutParams(-1,-2)); setOnClickListener { if (!busy) click() }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        invoiceId = intent.getLongExtra(PurchaseOrderListActivity.EXTRA_INVOICE_ID, 0)
        pendingLineId=intent.getLongExtra("reviewLineId",0L)
        val root = column().apply { setPadding(dp(12),0,dp(12),0);setBackgroundColor(Color.WHITE) }
        val toolbar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL }
        root.addView(toolbar)
        val back=action("‹ Order",toolbar) { finish() }
        back.layoutParams=LinearLayout.LayoutParams(dp(88),dp(48));back.textSize=13f
        toolbar.addView(label("Review scan",18f).apply { setTypeface(typeface,android.graphics.Typeface.BOLD) },LinearLayout.LayoutParams(0,dp(48),1f))
        val refresh=action("Refresh",toolbar) { load() };refresh.layoutParams=LinearLayout.LayoutParams(dp(88),dp(48));refresh.textSize=12f
        val context=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL }
        summary=label("Loading…",13f);context.addView(summary,LinearLayout.LayoutParams(0,-2,1f))
        totalsButton=action("Totals (optional)",context) { editTotals() }
        totalsButton.layoutParams=LinearLayout.LayoutParams(dp(140),dp(48));totalsButton.textSize=12f
        root.addView(context)
        message=label("",13f).apply { setTextColor(0xFFB45309.toInt());visibility=View.GONE };root.addView(message)
        val choices=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        needsReviewButton=Button(this).apply { isAllCaps=false;textSize=12f;setOnClickListener { if(!busy){onlyIssues=true;renderRows();list.scrollToPosition(0)} } }
        allItemsButton=Button(this).apply { isAllCaps=false;textSize=12f;setOnClickListener { if(!busy){onlyIssues=false;renderRows();list.scrollToPosition(0)} } }
        choices.addView(needsReviewButton,LinearLayout.LayoutParams(0,dp(48),1f));choices.addView(allItemsButton,LinearLayout.LayoutParams(0,dp(48),1f));root.addView(choices)
        val search=EditText(this).apply { hint="Search name, SKU or barcode";textSize=14f;setSingleLine();imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH }
        root.addView(search,LinearLayout.LayoutParams(-1,dp(48)))
        list=RecyclerView(this).apply {
            tag="review-items";layoutManager=LinearLayoutManager(this@ScanOrderReviewActivity);adapter=this@ScanOrderReviewActivity.adapter;itemAnimator=null
            isVerticalScrollBarEnabled=true;scrollBarStyle=View.SCROLLBARS_INSIDE_OVERLAY;setPadding(0,0,0,dp(12));clipToPadding=false
            addItemDecoration(androidx.recyclerview.widget.DividerItemDecoration(this@ScanOrderReviewActivity,LinearLayoutManager.VERTICAL))
        }
        root.addView(list,LinearLayout.LayoutParams(-1,0,1f))
        pageText=label("",12f).apply { gravity=android.view.Gravity.CENTER };root.addView(pageText,LinearLayout.LayoutParams(-1,dp(28)))
        search.doAfterTextChanged { query=it.toString();renderRows();list.scrollToPosition(0) }
        search.setOnEditorActionListener { _,_,_ ->
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(search.windowToken,0);search.clearFocus();true
        }
        setContentView(root)
        if(invoiceId>0)load() else { summary.text="No invoice selected";totalsButton.isEnabled=false }
    }
    override fun onBackPressed() {
        if(busy) { Toast.makeText(this,"Please wait for the current request to finish.",Toast.LENGTH_SHORT).show(); return }
        super.onBackPressed()
    }
    private fun load() = runAction {
        val fresh=api.getOrderCorrections(invoiceId)
        products=api.getStagedPurchaseOrderDetails(invoiceId).associateBy { it.lineId }
        review=fresh
    }
    private fun runAction(block: suspend () -> Unit) {
        if (busy) return
        busy=true; message.text="Working…";message.visibility=View.VISIBLE; render()
        lifecycleScope.launch {
            try { block(); message.text="" }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { message.text=apiError(e) }
            finally { busy=false; render() }
        }
    }
    private fun render() {
        val data=review ?: return
        if(!busy && pendingLineId==0L && intent.getBooleanExtra("advanceWhenClear",false) && data.readyForPricing) {
            Toast.makeText(this,"Scan checked. Review item matches and prices.",Toast.LENGTH_SHORT).show()
            finish();return
        }
        val count=data.lines.count { it.needsReview }
        val totals=data.reconciliation
        summary.text=if (!data.isEditable) "Received order · view only"
            else if(data.readyForPricing) "Items checked · return to order"
            else "${data.lines.size} items · $count need attention"
        totalsButton.text="Totals (optional)"
        totalsButton.isEnabled=!busy
        renderRows()
        if(!busy && pendingLineId!=0L) {
            val row=data.lines.find { it.lineId==pendingLineId };pendingLineId=0L
            if(row!=null && data.isEditable) editLine(row)
        }
    }
    private fun renderRows() {
        val data=review ?: return
        val matches=data.lines.filter { (!onlyIssues || it.needsReview) &&
            (query.isBlank() || listOf(it.source.text("description"),it.source.text("sku"),it.source.text("upc"), products[it.lineId]?.itemName.orEmpty()).any { value -> value.contains(query,true) }) }
        adapter.rows=matches
        pageText.text="${matches.size} items · swipe up to see more"
        needsReviewButton.text="Needs attention ("+data.lines.count { it.needsReview }+")"
        allItemsButton.text="All items ("+data.lines.size+")"
        needsReviewButton.isEnabled=!busy;allItemsButton.isEnabled=!busy
        needsReviewButton.isSelected=onlyIssues;allItemsButton.isSelected=!onlyIssues
        styleFilterButton(needsReviewButton,onlyIssues)
        styleFilterButton(allItemsButton,!onlyIssues)
        adapter.notifyDataSetChanged()
        if(adapter.rows.isEmpty() && !busy && message.text.isBlank()) message.text=if(onlyIssues) "No matching issues. Choose All items to edit any scanned line." else "No matching items."
        else if (message.text.startsWith("No matching")) message.text=""
        message.visibility=if(message.text.isBlank()) View.GONE else View.VISIBLE
    }
    private inner class ReviewAdapter : RecyclerView.Adapter<ReviewAdapter.Holder>() {
        init { setHasStableIds(true) }
        var rows=emptyList<ScanLineReview>()
        inner class Holder(val box: LinearLayout): RecyclerView.ViewHolder(box)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int)=Holder(column().apply {
            setPadding(dp(8),dp(6),dp(8),dp(6));layoutParams=RecyclerView.LayoutParams(-1,-2)
        })
        override fun getItemCount()=rows.size
        override fun getItemId(position:Int)=rows[position].lineId
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row=rows[position];val source=row.source;val item=products[row.lineId];val box=holder.box
            box.removeAllViews();box.setBackgroundColor(if(row.needsReview) 0xFFFFF8EB.toInt() else Color.WHITE)
            box.addView(label("${source.text("lineNumber")}. ${source.text("description").ifBlank { item?.itemName ?: "Scanned item" }}",15f).apply {
                maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;setTypeface(typeface,android.graphics.Typeface.BOLD)
            })
            box.addView(label("SKU ${row.itemCode ?: source.text("sku")} · UPC ${row.itemUpc ?: item?.upcCode ?: source.text("upc")}",12f).apply { maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END })
            val units=ScanUnitCorrection.from(source)
            box.addView(label("${units.totalUnitsReceived ?: "—"} units · ${units.unitsPerCase ?: "—"}/case · ${money(units.netLineAmount?.toDouble())}",13f))
            val actions=LinearLayout(this@ScanOrderReviewActivity).apply { orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL };box.addView(actions)
            if(!row.isMiscCharge && (row.issues.containsKey("item") || row.itemConfirmed)) {
                val confirm=CheckBox(this@ScanOrderReviewActivity).apply {
                    text="Confirm item & UPC";textSize=12f;minHeight=dp(48);isChecked=row.itemConfirmed
                    isEnabled=review?.isEditable==true && !busy && !row.itemConfirmed && row.confirmChoice(item)!=null
                    setOnClickListener { isChecked=row.itemConfirmed;confirmItem(row) }
                };actions.addView(confirm,LinearLayout.LayoutParams(0,dp(48),1f))
            } else actions.addView(label(if(row.isMiscCharge) "Charge only" else if(row.needsReview) "Check values" else "✓ Checked",12f),LinearLayout.LayoutParams(0,-2,1f))
            val edit=action(if(review?.isEditable==true) "Edit item" else "Details",actions) { if(review?.isEditable==true)editLine(row) else showDetails(row) }
            edit.layoutParams=LinearLayout.LayoutParams(dp(104),dp(48));edit.textSize=12f;edit.isEnabled=!busy
            if(review?.isEditable==true) actions.addView(ImageButton(this@ScanOrderReviewActivity).apply {
                setImageResource(android.R.drawable.ic_menu_delete)
                imageTintList=android.content.res.ColorStateList.valueOf(0xFFB42318.toInt())
                background=null;contentDescription="Remove item from review: "+source.text("description")
                isEnabled=!busy;setOnClickListener { removeItem(row) }
            },LinearLayout.LayoutParams(dp(48),dp(48)))
        }
    }
    private fun showDetails(row:ScanLineReview) {
        AlertDialog.Builder(this).setTitle(row.source.text("description")).setMessage(row.issues.values.distinct().joinToString("\n").ifBlank { "Item checked." }).setPositiveButton("Done",null).show()
    }
    private fun field(box: LinearLayout, title: String, value: String, integer: Boolean=false): EditText {
        box.addView(label(title))
        return EditText(this).apply {
            hint=title; setText(value); setSingleLine(); inputType=InputType.TYPE_CLASS_NUMBER or if(integer) 0 else InputType.TYPE_NUMBER_FLAG_DECIMAL
            box.addView(this, ViewGroup.LayoutParams(-1,-2))
        }
    }
    private fun choice(box: LinearLayout, title: String, options: List<Pair<String,String>>, selected: String): Spinner {
        box.addView(label(title))
        return Spinner(this).apply {
            adapter=ArrayAdapter(this@ScanOrderReviewActivity,android.R.layout.simple_spinner_dropdown_item, options.map { it.second })
            setSelection(options.indexOfFirst { it.first==selected }.coerceAtLeast(0)); box.addView(this,ViewGroup.LayoutParams(-1,dp(48)))
        }
    }
    private fun editor(title: String, box: LinearLayout, save: suspend () -> Unit) {
        val error=label("").apply { setTextColor(0xFFB91C1C.toInt()) }; box.addView(error)
        val scroll=ScrollView(this).apply { addView(box) }
        val dialog=AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if(busy) return@setOnClickListener
                busy=true; error.text="Saving…"; dialog.setCancelable(false)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false; dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled=false
                lifecycleScope.launch {
                    try {
                        save(); dialog.dismiss(); message.text="Saved."
                    } catch(e: CancellationException) { throw e }
                    catch(e: Exception) { error.text=apiError(e) }
                    finally {
                        busy=false; dialog.setCancelable(true)
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true; dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled=true
                        render()
                    }
                }
            }
        }
        dialog.show()
    }
    private fun number(input: EditText, integer: Boolean=false): Double? {
        val raw=input.text.toString().trim()
        if(raw.isEmpty()) return null
        val value=raw.toDoubleOrNull()
        if(value==null || !value.isFinite() || value<0 || (integer && (value%1.0!=0.0 || value>99999))) {
            input.error="Enter a valid non-negative ${if(integer) "whole " else ""}number"; input.requestFocus(); throw IllegalArgumentException("Please correct the highlighted value.")
        }
        return value
    }
    private fun editLine(row: ScanLineReview) {
        var initial=ScanUnitCorrection.from(row.source).let { if(row.isReturn)it.copy(netLineAmount=it.netLineAmount?.abs()) else it };val box=column()
        box.addView(label(row.source.text("description"),16f))
        if(row.issues.isNotEmpty())box.addView(label(row.issues.values.distinct().joinToString("\n"),12f))
        var selected:QuanticInventorySearchResultDto?=null
        val selectedLabel=label("",13f)
        val selectedConfirm=CheckBox(this).apply { text="Correct product and package";visibility=View.GONE }
        if(!row.isMiscCharge)action("Change item",box) { searchItem { item -> selected=item;selectedLabel.text="${item.name}\nSKU ${item.sku.orEmpty()} · UPC ${item.upc.orEmpty()}";selectedConfirm.visibility=View.VISIBLE;selectedConfirm.isChecked=false } }
        box.addView(selectedLabel);box.addView(selectedConfirm)
        val canEditIds=row.canEditIdentifiers ?: row.isNewItem
        val sku=if(canEditIds)field(box,"Item SKU",row.itemCode ?: row.source.text("sku")).apply { inputType=InputType.TYPE_CLASS_TEXT } else null
        val barcode=if(canEditIds)field(box,"Barcode",row.itemUpc ?: row.source.text("upc")).apply { inputType=InputType.TYPE_CLASS_TEXT;hint="Scan or type the sellable-unit barcode" } else null
        box.addView(label("Enter individual units: 1 case of 24 = 24 units.",13f))
        val units=field(box,if(row.isReturn) "Total units returned" else "Total units received",initial.totalUnitsReceived?.toString().orEmpty(),true)
        val pack=field(box,"Units per case",initial.unitsPerCase?.toString().orEmpty(),true)
        fun cents(value:java.math.BigDecimal?)=value?.setScale(2,java.math.RoundingMode.HALF_UP)?.toPlainString().orEmpty()
        val base=field(box,"Base cost / unit",cents(initial.baseUnitCost))
        val discount=field(box,"Discount / unit (0 if none)",cents(initial.discountPerUnit))
        val total=field(box,"Printed line total",cents(initial.netLineAmount))
        fun draft():ScanUnitCorrection {
            fun whole(input:EditText)=try { input.text.toString().trim().toBigDecimalOrNull()?.intValueExact() } catch(_:ArithmeticException) { null }
            fun precise(input:EditText,original:java.math.BigDecimal?):java.math.BigDecimal? {
                val text=input.text.toString().trim()
                return if(text==cents(original))original else text.toBigDecimalOrNull()
            }
            return ScanUnitCorrection(whole(units),whole(pack),precise(base,initial.baseUnitCost),
                if(discount.text.isBlank())java.math.BigDecimal.ZERO else precise(discount,initial.discountPerUnit),precise(total,initial.netLineAmount))
        }
        val helper=column().apply { visibility=View.GONE }
        lateinit var toggle:Button
        toggle=action("Invoice shows prices per case?",box) {
            val open=helper.visibility!=View.VISIBLE;helper.visibility=if(open) View.VISIBLE else View.GONE
            toggle.text=if(open) "Hide case-price helper" else "Invoice shows prices per case?"
        }
        box.addView(helper)
        helper.addView(label("Enter amounts for ONE case. We divide by units per case for you.",13f))
        val caseBase=field(helper,"Base price per case","")
        val caseDiscount=field(helper,"Discount per case (0 if none)","0")
        val helperError=label("").apply { setTextColor(0xFFB91C1C.toInt()) }
        action("Convert to unit costs",helper) {
            val count=draft().unitsPerCase;val cost=caseBase.text.toString().toBigDecimalOrNull();val disc=caseDiscount.text.toString().toBigDecimalOrNull()
            if(count==null || count !in 1..10000 || cost==null || cost.signum()<0 || disc==null || disc.signum()<0 || disc>cost) helperError.text="Enter units per case, its base price, and discount (0 if none)."
            else { initial=initial.copy(baseUnitCost=cost.perUnits(count),discountPerUnit=disc.perUnits(count));base.setText(cents(initial.baseUnitCost));discount.setText(cents(initial.discountPerUnit));helper.visibility=View.GONE;toggle.text="Invoice shows prices per case?";helperError.text="" }
        }
        helper.addView(helperError)
        val calculation=label("");box.addView(calculation)
        fun updateCalculation() {
            val edit=draft();calculation.text="Calculated line total: " + (edit.calculatedAmount?.setScale(2,java.math.RoundingMode.HALF_UP)?.let { "$"+it } ?: "—") + "\n" + edit.warning.orEmpty()
        }
        listOf(units,pack,base,discount,total).forEach { it.doAfterTextChanged { updateCalculation() } };updateCalculation()
        editor("Edit line ${row.source.text("lineNumber")}",box) {
            val edit=draft();edit.validate()
            if(selected!=null && !selectedConfirm.isChecked)throw IllegalArgumentException("Confirm the selected product and package before saving.")
            review=api.saveSimpleOrderCorrection(invoiceId,ScanCorrectionSave(row.lineId,row.version,edit,selected?.itemId,
                if(selected==null)barcode?.text?.toString() else null,if(selected==null)sku?.text?.toString() else null))
        }
    }
    private fun editTotals() {
        val data=review ?: return; val current=data.reconciliation; val box=column()
        box.addView(label("Line total: ${money(current.lineTotal)}\nCalculated total: ${money(current.calculatedTotal)}\n${current.issues.joinToString("\n")}"))
        if(!data.isEditable) { AlertDialog.Builder(this).setTitle("Invoice totals").setView(box).setPositiveButton("Close",null).show(); return }
        box.addView(label("Enter 0 when none. A blank additional invoice discount defaults to 0. It excludes discounts already included on individual lines."))
        val total=field(box,"Printed invoice total",current.totals.invoiceTotal?.toString().orEmpty())
        val tax=field(box,"Tax",current.totals.tax?.toString().orEmpty())
        val fees=field(box,"Fees (not already included in line totals)",current.totals.fees?.toString().orEmpty())
        val discount=field(box,"Additional invoice discount",(current.totals.invoiceDiscount ?: 0.0).toString())
        editor("Check invoice totals",box) {
            val updated=api.updateScanTotals(data.batchId,ScanTotals(number(total),number(tax),number(fees),number(discount) ?: 0.0))
            review=data.copy(reconciliation=updated)
        }
    }
    private fun removeItem(row: ScanLineReview) {
        if(busy || review?.isEditable!=true) return
        val name=row.source.text("description").ifBlank { products[row.lineId]?.itemName ?: "this item" }
        AlertDialog.Builder(this).setTitle("Remove item from review?")
            .setMessage("$name\n\nThis item will be removed from the staged invoice and will not be received into inventory. The original scan and printed invoice total are kept.")
            .setNegativeButton("Cancel",null).setPositiveButton("Remove item") { _,_ ->
                if(review?.isEditable==true) runAction {
                    review=api.removeOrderItem(invoiceId,row.lineId,ScanItemRemoval(row.version))
                    products=products - row.lineId
                }
            }.show()
    }
    private fun confirmItem(row: ScanLineReview) {
        if(busy || review?.isEditable!=true || row.itemConfirmed)return
        val choice=row.confirmChoice(products[row.lineId]) ?: return
        runAction { review=api.confirmOrderItem(invoiceId,row.lineId,choice) }
    }
    private fun searchItem(onSelected:(QuanticInventorySearchResultDto)->Unit) {
        val box=column(); val input=EditText(this).apply { hint="Name, SKU or barcode"; setSingleLine() }; box.addView(input)
        val status=label("Enter at least 2 characters"); box.addView(status)
        val results=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; val scroll=ScrollView(this).apply { addView(results) }; box.addView(scroll,ViewGroup.LayoutParams(-1,dp(280)))
        val dialog=AlertDialog.Builder(this).setTitle("Match invoice item").setView(box).setNegativeButton("Cancel",null).create()
        var job: Job?=null
        input.doAfterTextChanged {
            job?.cancel(); results.removeAllViews(); val term=it.toString().trim()
            if(term.length<2) { status.text="Enter at least 2 characters"; return@doAfterTextChanged }
            job=lifecycleScope.launch {
                delay(300); status.text="Searching…"
                try {
                    val found=api.searchOrderItems(invoiceId,term)
                    status.text=if(found.isEmpty()) "No matches. Try a name, SKU or barcode." else "Select the matching product"
                    found.take(30).forEach { item -> action("${item.name}\nSKU ${item.sku.orEmpty()} • UPC ${item.upc.orEmpty()}",results) {
                        dialog.dismiss();onSelected(item)
                    } }
                } catch(e: CancellationException) { throw e } catch(e: Exception) { status.text=apiError(e) }
            }
        }
        dialog.setOnDismissListener { job?.cancel() }; dialog.show()
    }
    private fun money(value: Double?)=value?.let { "$%.2f".format(java.util.Locale.US,it) } ?: "unknown"
}
