package com.liquorbee.invoicescanner.network
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import retrofit2.HttpException

class ScanOrderReviewTest {
    private val json="""{"lineId":53030,"version":"saved-version","itemConfirmed":false,"isMiscCharge":true,"issues":{"item":"Confirm this item"},"source":{"caseQty":1,"bottleQty":0,"packSize":24,"unitCostBasis":"net_per_unit","extendedTotal":217.2,"futureField":"keep me"}}"""
    @Test fun correctionsKeepServerVersionUnknownFieldsAndOriginalValues() {
        val row=Gson().fromJson(json,ScanLineReview::class.java)
        val source=row.source.deepCopy();source.addProperty("caseQty",2)
        val edited=Gson().toJsonTree(row.copy(source=source)).asJsonObject
        assertEquals("saved-version",edited["version"].asString)
        assertEquals("keep me",edited["source"].asJsonObject["futureField"].asString)
        assertEquals("1",row.source.text("caseQty"))
        assertTrue(row.needsReview);assertTrue(row.isMiscCharge)
    }
    @Test fun pricingStepRequiresRealRowsNoIssuesAndBalancedTotals() {
        val row=Gson().fromJson(json,ScanLineReview::class.java)
        val clean=row.copy(issues=emptyMap())
        val totals=ScanReconciliation(ScanTotals(100.0,0.0,0.0,0.0),100.0,100.0,0.0,true,emptyList())
        val review=ScanOrderReview(true,"batch",totals,listOf(clean))
        assertTrue(review.readyForPricing)
        assertFalse(review.copy(lines=listOf(row)).readyForPricing)
        assertFalse(review.copy(lines=emptyList()).readyForPricing)
        assertFalse(review.copy(reconciliation=totals.copy(isBalanced=false)).readyForPricing)
        assertFalse(review.copy(reconciliation=totals.copy(issues=listOf("Check fee"))).readyForPricing)
    }
    @Test fun successfulEmptyResponseIsAccepted() { Response.success<Unit>(null).requireSuccess() }
    @Test fun serverRejectionIsNotReportedAsSuccess() {
        try { Response.error<Unit>(400,"Please correct the printed amount".toResponseBody()).requireSuccess();fail("Expected server rejection") }
        catch(e:HttpException) { assertEquals("Please correct the printed amount",apiError(e)) }
    }
    @Test fun oldResponseDoesNotInferMiscFromProductName() {
        val row=Gson().fromJson("""{"lineId":1,"source":{"description":"MISC"},"issues":{}}""",ScanLineReview::class.java)
        assertFalse(row.isMiscCharge);assertFalse(row.needsReview)
    }
}
