package com.liquorbee.invoicescanner

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.liquorbee.invoicescanner.databinding.ActivityPurchaseOrderDetailBinding
import com.liquorbee.invoicescanner.databinding.ItemPurchaseOrderLineBinding
import com.liquorbee.invoicescanner.network.PurchaseOrderLineDto
import com.liquorbee.invoicescanner.ui.LoginActivity
import com.liquorbee.invoicescanner.ui.PurchaseOrderLineAdapter
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Uses synthetic rows only: no login, invoice requests, or writes to a store. */
class InvoiceTableLayoutInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            val activity=startActivitySync(Intent(targetContext,LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            lateinit var b: ActivityPurchaseOrderDetailBinding
            var corrected=0L
            val lines=(1..20).map { n -> Gson().fromJson("""{"lineId":$n,"itemCode":"${53000+n}","itemName":"Sample beverage $n 12-pack","upcCode":"012345678901","itemCategory":"Beer","currentQty":12,"unitsPerCase":2,"shippedCases":1,"shippedUnits":2,"caseCost":32.0,"unitCost":16.0,"currentUnitPrice":19.99,"suggestedUnitPrice":21.99,"caseDiscount":0.0,"averageCost":15.0}""",PurchaseOrderLineDto::class.java) }
            runOnMainSync {
                b=ActivityPurchaseOrderDetailBinding.inflate(activity.layoutInflater)
                activity.setContentView(b.root)
                check(!b.buttonSave.isEnabled && !b.buttonReceive.isEnabled) { "Actions must wait for a loaded order" }
                b.buttonSave.isEnabled=true; b.buttonReceive.isEnabled=true
                b.detailContent.visibility=View.VISIBLE
                b.detailBody.visibility=View.GONE
                b.textVendorOrder.text="Invoice 12345"
                b.textStatusBadge.text="STAGED"
                b.buttonAddItem.visibility=View.GONE
                b.scanReviewPanel.visibility=View.VISIBLE
                b.textScanReview.text="Scan checked. Review prices, then receive."
                b.buttonExpandItems.visibility=View.VISIBLE
                b.invoiceReviewFilters.visibility=View.VISIBLE
                b.buttonAllItems.text="All items (65)"
                b.buttonNeedsReview.text="Needs review (0)"
                b.invoicePageControls.visibility=View.VISIBLE
                b.textInvoicePage.text="1–20 of 65 items • Page 1 of 4"
                b.recyclerLines.layoutManager=LinearLayoutManager(activity)
                b.recyclerLines.adapter=PurchaseOrderLineAdapter(lines,false,{}, {},true,{ corrected=it })
            }
            awaitLayout(b.root)
            var headerY=0
            runOnMainSync {
                check(b.recyclerLines.height>150) { "Table has insufficient height: ${b.recyclerLines.height}" }
                val header=(b.invoiceTableScroll.getChildAt(0) as ViewGroup).getChildAt(0)
                headerY=location(header)[1]
                val row=b.recyclerLines.findViewHolderForAdapterPosition(0)!!.itemView
                val bound=ItemPurchaseOrderLineBinding.bind(row)
                bound.textItemName.performClick()
                check(corrected==1L) { "Name did not open the matching correction row" }
                bound.editCurrentUnitPrice.setText("22.49")
                check(lines[0].currentUnitPrice==22.49) { "Price edit did not update original invoice row" }
                check(!bound.editQty.isEnabled && !bound.editUnitCost.isEnabled)
            }
            screenshot("invoice-table-phone.png")
            runOnMainSync { b.recyclerLines.scrollBy(0,900); b.invoiceTableScroll.scrollTo(850,0) }
            awaitLayout(b.root)
            runOnMainSync {
                val header=(b.invoiceTableScroll.getChildAt(0) as ViewGroup).getChildAt(0)
                check(location(header)[1]==headerY) { "Header moved during vertical scroll" }
                val row=b.recyclerLines.getChildAt(0)
                check(location(header)[0]==location(row)[0]) { "Column headings are misaligned" }
                check((b.recyclerLines.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()>0) { "Rows did not scroll" }
            }
            screenshot("invoice-table-scrolled.png")
            finish(Activity.RESULT_OK,Bundle().apply { putString("stream","Invoice table device checks passed: visible rows, sticky/aligned header, correction action and retained price edit.\n") })
        } catch(e:Throwable) {
            finish(Activity.RESULT_CANCELED,Bundle().apply { putString("stream",e.stackTraceToString()) })
        }
    }
    private fun location(view:View)=IntArray(2).also { view.getLocationOnScreen(it) }
    private fun awaitLayout(view:View) {
        val ready=CountDownLatch(1)
        runOnMainSync { view.postDelayed({ready.countDown()},300) }
        check(ready.await(10,TimeUnit.SECONDS))
        waitForIdleSync()
    }
    private fun screenshot(name:String) {
        uiAutomation.takeScreenshot()?.let { image ->
            File(targetContext.getExternalFilesDir(null),name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
            image.recycle()
        }
    }
}
