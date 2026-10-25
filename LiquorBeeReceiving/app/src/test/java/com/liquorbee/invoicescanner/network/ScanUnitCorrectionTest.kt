package com.liquorbee.invoicescanner.network
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class ScanUnitCorrectionTest {
    private fun edit(units:Int=24,pack:Int=24,base:String="2.2425",discount:String="0",total:String="53.82") =
        ScanUnitCorrection(units,pack,BigDecimal(base),BigDecimal(discount),BigDecimal(total))
    @Test fun wholeCasePreservesPrecisionAndMetadata() {
        val original=JsonParser.parseString("""{"sku":"96813","futureField":"keep","caseQty":9,"matchedItemId":null}""").asJsonObject
        val source=edit().legacySource(original)
        assertEquals("9",original.text("caseQty"));assertEquals("1",source.text("caseQty"));assertEquals("0",source.text("bottleQty"))
        assertEquals("keep",source.text("futureField"));assertEquals("96813",source.text("sku"))
        assertEquals(0,source.text("caseCost").toBigDecimal().compareTo(BigDecimal("53.82")))
        assertEquals("2.2425",source.text("unitCost"))
    }
    @Test fun partialCaseDoesNotLoseLooseUnits() {
        val source=edit(30,24,"2.5","0.25","67.50").legacySource(com.google.gson.JsonObject())
        assertEquals("0",source.text("caseQty"));assertEquals("30",source.text("bottleQty"));assertEquals("24",source.text("packSize"))
        assertEquals(0,source.text("caseCost").toBigDecimal().compareTo(BigDecimal("2.5")))
    }
    @Test fun rejectsIncorrectExtensionAndUnknownPack() {
        for(value in listOf(edit(total="1291.68"),edit().copy(unitsPerCase=null),edit(discount="3"),edit().copy(baseUnitCost=null))) {
            try { value.validate();fail("Expected validation failure") } catch(_:IllegalArgumentException) {}
        }
    }
    @Test fun readsPerCaseDiscountAndRetailPacks() {
        val source=JsonParser.parseString("""{"caseQty":1,"bottleQty":0,"packSize":6,"caseCost":37.50,"unitCost":5.14,"lineDiscount":6.66,"extendedTotal":30.84,"unitCostBasis":"net_per_unit","discountBasis":"per_case"}""").asJsonObject
        val value=ScanUnitCorrection.from(source);value.validate()
        assertEquals(6,value.totalUnitsReceived);assertEquals("6.25",value.baseUnitCost.entryText());assertEquals("1.11",value.discountPerUnit.entryText())
    }
    @Test fun missingValuesStayUnknown() {
        val value=ScanUnitCorrection.from(JsonParser.parseString("""{"caseQty":1,"unitCost":10}""").asJsonObject)
        assertNull(value.totalUnitsReceived);assertNull(value.baseUnitCost);assertNull(value.unitsPerCase)
    }
    @Test fun wirePayloadPreservesConcurrencyAndIncludesBothForms() {
        val row=ScanLineReview(12,false,"saved-version",com.google.gson.JsonObject(),emptyMap())
        val value=edit();val payload=Gson().toJsonTree(row.copy(source=value.legacySource(row.source),unitEdit=value)).asJsonObject
        assertEquals("saved-version",payload["version"].asString)
        assertEquals(24,payload["unitEdit"].asJsonObject["totalUnitsReceived"].asInt)
        assertEquals("net_per_unit",payload["source"].asJsonObject["unitCostBasis"].asString)
    }
}
