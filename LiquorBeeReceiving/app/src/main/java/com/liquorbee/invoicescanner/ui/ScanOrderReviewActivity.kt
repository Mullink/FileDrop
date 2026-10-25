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
    private var pageNumber = 1
    private var pendingLineId = 0L
    private val tableWidths = listOf(84,260,100,90,110,110,110,300,260)
    private var itemsExpanded = false
    private lateinit var pageText: TextView
    private lateinit var previousPage: Button
    private lateinit var nextPage: Button
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
        val root = column()
        val toolbar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL };root.addView(toolbar)
        val context=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val back=action("‹ Order",toolbar) { finish() }
        val refresh=action("Refresh",toolbar) { load() }
        lateinit var expand: Button
        expand=action("Expand items",toolbar) {
            itemsExpanded=!itemsExpanded
            context.visibility=if(itemsExpanded) View.GONE else View.VISIBLE
            expand.text=if(itemsExpanded) "Exit expanded" else "Expand items"
        }
        listOf(back,refresh,expand).forEach { it.layoutParams=LinearLayout.LayoutParams(0,-2,1f);it.textSize=12f }
        root.addView(context)
        context.addView(label("1. Review scan",20f))
        summary=label("Loading invoice…"); context.addView(summary)
        message=label(""); message.setTextColor(Color.rgb(180,35,35)); root.addView(message)
        totalsButton=action("Review invoice totals",context) { editTotals() }
        val filters=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val search=EditText(this).apply { hint="Search name, SKU or barcode"; setSingleLine() }
        filters.addView(search,LinearLayout.LayoutParams(0,-2,1f))
        val find=Button(this).apply { text="Search"; isAllCaps=false; setOnClickListener {
            query=search.text.toString();onlyIssues=false;pageNumber=1;renderRows();list.scrollToPosition(0)
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(search.windowToken,0)
        } };filters.addView(find,LinearLayout.LayoutParams(-2,dp(48)))
        root.addView(filters)
        val choices=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        needsReviewButton=Button(this).apply { text="Needs review"; isAllCaps=false; textSize=12f; setOnClickListener { onlyIssues=true; pageNumber=1; renderRows(); list.scrollToPosition(0) } }
        allItemsButton=Button(this).apply { text="All items"; isAllCaps=false; textSize=12f; setOnClickListener { onlyIssues=false; pageNumber=1; renderRows(); list.scrollToPosition(0) } }
        choices.addView(needsReviewButton,LinearLayout.LayoutParams(0,-2,1f));choices.addView(allItemsButton,LinearLayout.LayoutParams(0,-2,1f));root.addView(choices)
        search.doAfterTextChanged { query=it.toString(); pageNumber=1; renderRows(); list.scrollToPosition(0) }
        list=RecyclerView(this).apply { layoutManager=LinearLayoutManager(this@ScanOrderReviewActivity); adapter=this@ScanOrderReviewActivity.adapter }
        root.addView(label("Tap an item name to correct it. Swipe sideways for more columns.",12f))
        val table=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val headings=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setBackgroundColor(0xFFF1EDFA.toInt()) }
        listOf("Code","Name / barcode","Total units","Units / case","Base / unit","Discount / unit","Line total","Match / review","Actions").forEachIndexed { index,title ->
            headings.addView(tableCell(title,tableWidths[index],true))
        }
        table.addView(headings,LinearLayout.LayoutParams(-1,dp(52)))
        table.addView(list,LinearLayout.LayoutParams(-1,0,1f))
        val horizontal=HorizontalScrollView(this).apply { isFillViewport=true
            addView(table,ViewGroup.LayoutParams(dp(tableWidths.sum()),-1))
        }
        root.addView(horizontal,LinearLayout.LayoutParams(-1,0,1f))
        pageText=label("",12f).apply { gravity=android.view.Gravity.CENTER }; root.addView(pageText)
        val navigation=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        previousPage=Button(this).apply { text="Previous"; isAllCaps=false; setOnClickListener { pageNumber--; renderRows(); list.scrollToPosition(0) } }
        nextPage=Button(this).apply { text="Next"; isAllCaps=false; setOnClickListener { pageNumber++; renderRows(); list.scrollToPosition(0) } }
        navigation.addView(previousPage,LinearLayout.LayoutParams(0,-2,1f)); navigation.addView(nextPage,LinearLayout.LayoutParams(0,-2,1f));root.addView(navigation)
        setContentView(root)
        load()
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
        busy=true; message.text="Working…"; render()
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
        summary.text=if (!data.isEditable) "This order is no longer staged. Saved scan details are shown below."
            else if(data.readyForPricing) "Ready for receiving. Return to the order to save retail prices and receive."
            else "$count item(s) need review. Correct highlighted values, confirm item matches, then check the invoice totals."
        totalsButton.text=if(totals.isBalanced) "Totals match • ${money(totals.calculatedTotal)}" else "Check totals • difference ${money(totals.difference)}"
        totalsButton.isEnabled=!busy
        renderRows()
        if(!busy && pendingLineId!=0L) {
            val row=data.lines.find { it.lineId==pendingLineId };pendingLineId=0L
            if(row!=null && data.isEditable) editLine(row)
        }
    }
    private fun renderRows() {
        val data=review ?: return
        val matches=data.lines.filter { (!onlyIssues || query.isNotBlank() || it.needsReview) &&
            (query.isBlank() || listOf(it.source.text("description"),it.source.text("sku"),it.source.text("upc"), products[it.lineId]?.itemName.orEmpty()).any { value -> value.contains(query,true) }) }
        val page=invoicePage(matches,pageNumber)
        pageNumber=page.number
        adapter.rows=page.items
        pageText.text=page.description
        previousPage.isEnabled=!busy && page.number>1
        nextPage.isEnabled=!busy && page.number<page.count
        needsReviewButton.text="Needs review ("+data.lines.count { it.needsReview }+")"
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
    private fun tableCell(value:String,width:Int,heading:Boolean=false)=label(value,if(heading) 12f else 13f).apply {
        layoutParams=LinearLayout.LayoutParams(dp(width),if(heading) -1 else -2)
        setPadding(dp(8),dp(10),dp(8),dp(10))
        if(heading) { setTypeface(typeface,android.graphics.Typeface.BOLD);gravity=android.view.Gravity.CENTER_VERTICAL }
    }
    private inner class ReviewAdapter : RecyclerView.Adapter<ReviewAdapter.Holder>() {
        var rows=emptyList<ScanLineReview>()
        inner class Holder(val box: LinearLayout): RecyclerView.ViewHolder(box)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int)=Holder(LinearLayout(this@ScanOrderReviewActivity).apply {
            orientation=LinearLayout.HORIZONTAL;layoutParams=RecyclerView.LayoutParams(-1,-2);minimumHeight=dp(80)
        })
        override fun getItemCount()=rows.size
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row=rows[position];val source=row.source;val item=products[row.lineId];val box=holder.box
            box.removeAllViews();box.setBackgroundColor(if(row.needsReview) 0xFFFFF6D8.toInt() else if(position%2==0) Color.WHITE else 0xFFF8FAFC.toInt())
            val match=if(row.isMiscCharge) "MISC charge: no inventory received" else
                "Matched: ${item?.displayName ?: item?.itemName ?: source.text("matchedItemName").ifBlank { "New / unmatched item" }}"
            val unitValues=ScanUnitCorrection.from(source)
            val values=listOf(source.text("sku"),source.text("description")+"\n"+source.text("upc"),
                unitValues.totalUnitsReceived?.toString().orEmpty(),unitValues.unitsPerCase?.toString().orEmpty(),
                unitValues.baseUnitCost?.setScale(4,java.math.RoundingMode.HALF_UP).entryText(),unitValues.discountPerUnit?.setScale(4,java.math.RoundingMode.HALF_UP).entryText(),source.text("extendedTotal"),
                match+"\n"+row.issues.values.distinct().joinToString("\n"))
            values.forEachIndexed { index,value ->
                val cell=tableCell(value.ifBlank { "—" },tableWidths[index])
                if(index==1 && review?.isEditable==true) {
                    cell.setTextColor(0xFF6743A5.toInt())
                    cell.contentDescription="Correct " + source.text("description")
                    cell.setOnClickListener { if(!busy) editLine(row) }
                }
                box.addView(cell)
            }
            val actions=LinearLayout(this@ScanOrderReviewActivity).apply { orientation=LinearLayout.HORIZONTAL;setPadding(dp(4),dp(8),dp(4),dp(8)) }
            box.addView(actions,LinearLayout.LayoutParams(dp(tableWidths.last()),-2))
            if(review?.isEditable==true) {
                val edit=action("Edit",actions) { editLine(row) };edit.isEnabled=!busy
                edit.layoutParams=LinearLayout.LayoutParams(0,dp(48),1f)
                if(!row.isMiscCharge) {
                    val confirm=action(if(row.itemConfirmed) "Matched" else "Confirm",actions) { confirmItem(row) }
                    confirm.isEnabled=!busy && !row.itemConfirmed && item!=null
                    confirm.layoutParams=LinearLayout.LayoutParams(0,dp(48),1f)
                    val find=action("Find",actions) { searchItem(row) };find.isEnabled=!busy
                    find.layoutParams=LinearLayout.LayoutParams(0,dp(48),1f)
                }
            }
        }
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
        val initial=ScanUnitCorrection.from(row.source);val box=column()
        box.addView(label("Count individual bottles or retail packs received. For example, 1 case of 24 = 24 total units. Costs below are for ONE sellable unit."))
        val units=field(box,"Total units received",initial.totalUnitsReceived?.toString().orEmpty(),true)
        val pack=field(box,"Units per case",initial.unitsPerCase?.toString().orEmpty(),true)
        val base=field(box,"Base cost per unit (before discount)",initial.baseUnitCost.entryText())
        val discount=field(box,"Discount per unit (0 if none)",initial.discountPerUnit.entryText())
        val total=field(box,"Net line amount (whole line after discount)",initial.netLineAmount.entryText())
        fun draft():ScanUnitCorrection {
            fun whole(input:EditText)=try { input.text.toString().trim().toBigDecimalOrNull()?.intValueExact() } catch(_:ArithmeticException) { null }
            return ScanUnitCorrection(whole(units),whole(pack),base.text.toString().trim().toBigDecimalOrNull(),discount.text.toString().trim().toBigDecimalOrNull(),total.text.toString().trim().toBigDecimalOrNull())
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
            else { base.setText(cost.perUnits(count).entryText());discount.setText(disc.perUnits(count).entryText());helper.visibility=View.GONE;toggle.text="Invoice shows prices per case?";helperError.text="" }
        }
        helper.addView(helperError)
        val calculation=label("");box.addView(calculation)
        fun updateCalculation() {
            val edit=draft();calculation.text="Calculated line total: " + (edit.calculatedAmount?.setScale(2,java.math.RoundingMode.HALF_UP)?.let { "$"+it } ?: "—") + "\nTotal units × (base cost − discount)"
        }
        listOf(units,pack,base,discount,total).forEach { it.doAfterTextChanged { updateCalculation() } };updateCalculation()
        editor("Edit line ${row.source.text("lineNumber")}",box) {
            val edit=draft();edit.validate()
            review=api.saveOrderCorrection(invoiceId,row.copy(source=edit.legacySource(row.source),unitEdit=edit))
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
    private fun confirmItem(row: ScanLineReview) {
        val item=products[row.lineId] ?: return
        val newItem=item.itemId.isNullOrBlank()
        AlertDialog.Builder(this).setTitle(if(newItem) "Confirm new item" else "Confirm item match")
            .setMessage("${item.itemName}\nSKU: ${item.itemCode}\nUPC: ${item.upcCode}\n\n${if(newItem) "Confirm these details for the new item." else "Does this match the product on the invoice?"}")
            .setNegativeButton("Cancel",null).setPositiveButton("Confirm") { _,_ -> runAction {
                review=api.confirmOrderItem(invoiceId,row.lineId,ScanItemChoice(item.itemId.orEmpty(),if(newItem) ScanNewItem(item.itemCode,item.itemName,item.upcCode) else null))
            } }.show()
    }
    private fun searchItem(row: ScanLineReview) {
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
                        AlertDialog.Builder(this@ScanOrderReviewActivity).setTitle("Use this item?").setMessage(item.name).setNegativeButton("Cancel",null).setPositiveButton("Use item") { _,_ ->
                            dialog.dismiss(); runAction {
                                api.changeOrderItem(invoiceId,row.lineId,ScanItemChoice(item.itemId)).requireSuccess()
                                products=api.getStagedPurchaseOrderDetails(invoiceId).associateBy { it.lineId }
                                review=api.getOrderCorrections(invoiceId)
                            }
                        }.show()
                    } }
                } catch(e: CancellationException) { throw e } catch(e: Exception) { status.text=apiError(e) }
            }
        }
        dialog.setOnDismissListener { job?.cancel() }; dialog.show()
    }
    private fun money(value: Double?)=value?.let { "$%.2f".format(java.util.Locale.US,it) } ?: "unknown"
}
