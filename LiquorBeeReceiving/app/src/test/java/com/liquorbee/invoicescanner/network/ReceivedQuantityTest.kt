package com.liquorbee.invoicescanner.network

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class ReceivedQuantityTest {
    private fun line(isReturn: Boolean=false) = Gson().fromJson("""{"lineId":1,"invoiceId":2,"unitsPerCase":12,"shippedCases":1,"shippedUnits":12,"unitCost":10.0,"caseCost":120.0,"currentUnitPrice":20.0,"isReturn":$isReturn}""",PurchaseOrderLineDto::class.java)

    @Test fun negativeCasesRemainSignedInSavedRequest() {
        val row=line();applyReceivedQuantity(row,-2,false)
        val json=Gson().toJsonTree(row).asJsonObject
        assertEquals(-2,json["shippedCases"].asInt);assertEquals(-24,json["shippedUnits"].asInt)
        assertEquals(-240.0,row.shippedCases!!*row.caseCost!!,0.001)
        assertEquals(20.0,row.currentUnitPrice!!,0.001)
    }
    @Test fun directUnitsRemainSignedWithoutChangingPackOrCases() {
        val row=line();applyReceivedQuantity(row,-5,true)
        assertEquals(-5,row.shippedUnits);assertEquals(1,row.shippedCases);assertEquals(12,row.unitsPerCase)
    }
    @Test fun manualBottleCreditsHaveNegativeUnitsAndTotalButPositiveCost() {
        val row=line().copy(unitsPerCase=1,caseCost=10.0);applyReceivedQuantity(row,-3,false)
        assertEquals(-3,row.shippedUnits);assertEquals(-30.0,row.shippedCases!!*row.caseCost!!,0.001)
        assertEquals(10.0,row.unitCost!!,0.001)
    }
    @Test fun positiveAndZeroReceiptsKeepTheirBehavior() {
        val row=line()
        applyReceivedQuantity(row,2,false);assertEquals(24,row.shippedUnits)
        applyReceivedQuantity(row,0,false);assertEquals(0,row.shippedUnits);assertEquals(0,row.shippedCases)
    }
    @Test fun explicitReturnsAlwaysDeductAndRetainReturnIdentity() {
        val row=line(true)
        applyReceivedQuantity(row,2,false);assertEquals(-24,row.shippedUnits)
        applyReceivedQuantity(row,-3,true);assertEquals(-3,row.shippedUnits)
        assertTrue(Gson().toJsonTree(row).asJsonObject["isReturn"].asBoolean)
    }
}
