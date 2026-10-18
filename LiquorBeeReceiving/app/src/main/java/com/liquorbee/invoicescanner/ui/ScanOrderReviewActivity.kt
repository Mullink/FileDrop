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
        context.addView(label("Review scanned invoice",20f))
        summary=label("Loading invoice…"); context.addView(summary)
        message=label(""); message.setTextColor(Color.rgb(180,35,35)); root.addView(message)
        totalsButton=action("Review invoice totals",context) { editTotals() }
        val filters=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val search=EditText(this).apply { hint="Search name, SKU or barcode"; setSingleLine() }
        filters.addView(search,LinearLayout.LayoutParams(0,-2,1f))
        root.addView(filters)
        val choices=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        needsReviewButton=Button(this).apply { text="Needs review"; isAllCaps=false; textSize=12f; setOnClickListener { onlyIssues=true; pageNumber=1; renderRows(); list.scrollToPosition(0) } }
        allItemsButton=Button(this).apply { text="All items"; isAllCaps=false; textSize=12f; setOnClickListener { onlyIssues=false; pageNumber=1; renderRows(); list.scrollToPosition(0) } }
        choices.addView(needsReviewButton,LinearLayout.LayoutParams(0,-2,1f));choices.addView(allItemsButton,LinearLayout.LayoutParams(0,-2,1f));root.addView(choices)
        search.doAfterTextChanged { query=it.toString(); pageNumber=1; renderRows(); list.scrollToPosition(0) }
        list=RecyclerView(this).apply { layoutManager=LinearLayoutManager(this@ScanOrderReviewActivity); adapter=this@ScanOrderReviewActivity.adapter }
        root.addView(list,LinearLayout.LayoutParams(-1,0,1f))
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
        val count=data.lines.count { it.needsReview }
        val totals=data.reconciliation
        summary.text=if (!data.isEditable) "This order is no longer staged. Saved scan details are shown below."
            else if(count==0 && totals.isBalanced) "Ready for receiving. Return to the order to save retail prices and receive."
            else "$count item(s) need review. Correct highlighted values, confirm item matches, then check the invoice totals."
        totalsButton.text=if(totals.isBalanced) "Totals match • ${money(totals.calculatedTotal)}" else "Check totals • difference ${money(totals.difference)}"
        totalsButton.isEnabled=!busy
        renderRows()
    }
    private fun renderRows() {
        val data=review ?: return
        val matches=data.lines.filter { (!onlyIssues || it.needsReview) &&
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
    private inner class ReviewAdapter : RecyclerView.Adapter<ReviewAdapter.Holder>() {
        var rows=emptyList<ScanLineReview>()
        inner class Holder(val box: LinearLayout): RecyclerView.ViewHolder(box)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int)=Holder(column().apply { layoutParams=RecyclerView.LayoutParams(-1,-2) })
        override fun getItemCount()=rows.size
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row=rows[position]; val source=row.source; val item=products[row.lineId]; val box=holder.box
            box.removeAllViews(); box.setBackgroundColor(if(row.needsReview) 0xFFFFF3E0.toInt() else Color.WHITE)
            box.addView(label(source.text("description").ifBlank { item?.itemName ?: "Scanned item" },18f))
            box.addView(label("SKU ${source.text("sku")} • UPC ${source.text("upc")}"))
            box.addView(label("Matched to: ${item?.displayName ?: item?.itemName ?: source.text("matchedItemName").ifBlank { "New / unmatched item" }}"))
            box.addView(label("Cases ${source.text("caseQty").ifBlank { "—" }} • Individual units ${source.text("bottleQty").ifBlank { "—" }} • Units/case ${source.text("packSize").ifBlank { "—" }}\nPrinted total ${source.text("extendedTotal").ifBlank { "—" }}"))
            if(row.isMiscCharge) box.addView(label("MISC charge • Included in invoice totals; no inventory will be received."))
            if(row.issues.isNotEmpty()) box.addView(label(row.issues.values.distinct().joinToString("\n")).apply { setTextColor(0xFFB45309.toInt()) })
            if(review?.isEditable==true) {
                action("Edit scanned values",box) { editLine(row) }.isEnabled=!busy
                if(!row.isMiscCharge) {
                    action(if(row.itemConfirmed) "Item confirmed" else "Confirm this item",box) { confirmItem(row) }.isEnabled=!busy && !row.itemConfirmed && item!=null
                    action("Find a different item",box) { searchItem(row) }.isEnabled=!busy
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
        val source=row.source.deepCopy(); val box=column(); val fields=linkedMapOf<String,EditText>()
        box.addView(label("Use the printed invoice. Enter 0 when a quantity or discount is zero. Blank values remain unknown. Saved changes recalculate costs."))
        val fixedNet=source.text("unitCostBasis")=="net_per_unit"
        fields["caseQty"]=field(box,"Cases received",source.text("caseQty"),true)
        fields["bottleQty"]=field(box,"Individual units received",source.text("bottleQty"),true)
        fields["packSize"]=field(box,"Sellable units per case",source.text("packSize").ifBlank { source.text("effectiveUnitsPerCase") },true)
        fields["unitCost"]=field(box,if(fixedNet) "Net cost per individual unit" else "Printed price",source.text("unitCost"))
        if(fixedNet) fields["caseCost"]=field(box,"Base price per case (per unit for individual shipments)",source.text("caseCost"))
        fields["lineDiscount"]=field(box,"Printed discount",source.text("lineDiscount"))
        fields["extendedTotal"]=field(box,"Printed net line total",source.text("extendedTotal"))
        val discounts=listOf("unknown" to "Choose discount type", "none" to "No discount", "per_case" to "Discount per case", "per_unit" to "Discount per individual unit", "line_total" to "Discount for the entire line")
        val discount=choice(box,"How is the discount printed?",discounts,source.text("discountBasis"))
        val prices=listOf("unknown" to "Choose price type", "gross" to "Before discount", "net" to "After discount")
        val price=if(!fixedNet) choice(box,"Printed price is",prices,source.text("unitCostBasis")) else null
        editor("Correct scanned values",box) {
            for((key,input) in fields) {
                val integer=key in listOf("caseQty","bottleQty","packSize")
                val value=number(input,integer)
                if(value==null) source.add(key,com.google.gson.JsonNull.INSTANCE)
                else if(integer) source.addProperty(key,value.toInt()) else source.addProperty(key,value)
            }
            source.addProperty("discountBasis",if(fixedNet && (source.text("lineDiscount").toDoubleOrNull() ?: 0.0)==0.0) "none" else discounts[discount.selectedItemPosition].first)
            if(price!=null) source.addProperty("unitCostBasis",prices[price.selectedItemPosition].first)
            review=api.saveOrderCorrection(invoiceId,row.copy(source=source))
        }
    }
    private fun editTotals() {
        val data=review ?: return; val current=data.reconciliation; val box=column()
        box.addView(label("Line total: ${money(current.lineTotal)}\nCalculated total: ${money(current.calculatedTotal)}\n${current.issues.joinToString("\n")}"))
        if(!data.isEditable) { AlertDialog.Builder(this).setTitle("Invoice totals").setView(box).setPositiveButton("Close",null).show(); return }
        box.addView(label("Enter 0 when none. Additional invoice discount excludes discounts already included on individual lines."))
        val total=field(box,"Printed invoice total",current.totals.invoiceTotal?.toString().orEmpty())
        val tax=field(box,"Tax",current.totals.tax?.toString().orEmpty())
        val fees=field(box,"Fees (not already included in line totals)",current.totals.fees?.toString().orEmpty())
        val discount=field(box,"Additional invoice discount",current.totals.invoiceDiscount?.toString().orEmpty())
        editor("Check invoice totals",box) {
            val updated=api.updateScanTotals(data.batchId,ScanTotals(number(total),number(tax),number(fees),number(discount)))
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
