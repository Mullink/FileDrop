package com.liquorbee.invoicescanner.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.liquorbee.invoicescanner.network.ScanOrderReview
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScanReviewLayoutTest {
    @Test fun multipleVisibleItemsAndScrollToLastWithoutHorizontalScroll() {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val intent=Intent(instrument.targetContext,ScanOrderReviewActivity::class.java)
        ActivityScenario.launch<ScanOrderReviewActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val rows=(1..30).joinToString(",") { """{"lineId":$it,"version":"v1","itemId":"item-$it","itemCode":"1234$it","itemUpc":"012345678905","itemConfirmed":false,"issues":{"item":"Confirm the item and barcode"},"source":{"lineNumber":$it,"description":"Example bourbon 750ml — item $it","sku":"1234$it","upc":"012345678905","caseQty":1,"bottleQty":0,"packSize":12,"unitCost":120,"unitCostBasis":"gross","discountBasis":"none","lineDiscount":0,"extendedTotal":120}}""" }
                val data=Gson().fromJson("""{"isEditable":true,"batchId":"fixture","lines":[$rows],"reconciliation":{"isBalanced":true,"calculatedTotal":3600,"issues":[],"totals":{}}}""",ScanOrderReview::class.java)
                activity.javaClass.getDeclaredField("review").apply { isAccessible=true }.set(activity,data)
                activity.javaClass.getDeclaredMethod("render").apply { isAccessible=true }.invoke(activity)
            }
            instrument.waitForIdleSync()
            android.os.SystemClock.sleep(350) // Let the first rendered frame reach the screenshot surface.
            scenario.onActivity { activity ->
                val list=activity.window.decorView.findViewWithTag<RecyclerView>("review-items")
                val manager=list.layoutManager as LinearLayoutManager
                assertTrue("At least two complete items fit",manager.findLastCompletelyVisibleItemPosition()>=1)
                assertTrue("Items get at least half the screen",list.height>=activity.window.decorView.height*.5)
                assertFalse(list.canScrollHorizontally(1))
            }
            val screenshot=instrument.uiAutomation.takeScreenshot()
            File(instrument.targetContext.getExternalFilesDir(null),"review-layout.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
            scenario.onActivity { activity -> activity.window.decorView.findViewWithTag<RecyclerView>("review-items").scrollToPosition(29) }
            instrument.waitForIdleSync()
            scenario.onActivity { activity ->
                val manager=activity.window.decorView.findViewWithTag<RecyclerView>("review-items").layoutManager as LinearLayoutManager
                assertEquals(29,manager.findLastVisibleItemPosition())
            }
        }
    }
}
