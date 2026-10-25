package com.liquorbee.invoicescanner.network

import com.google.gson.JsonObject
import java.math.BigDecimal
import java.math.RoundingMode

/** Individual sellable units are the editing unit, including retail multi-packs. */
data class ScanUnitCorrection(val totalUnitsReceived: Int?, val unitsPerCase: Int?,
    val baseUnitCost: BigDecimal?, val discountPerUnit: BigDecimal?, val netLineAmount: BigDecimal?) {
    val calculatedAmount: BigDecimal? get() = if(totalUnitsReceived==null || baseUnitCost==null || discountPerUnit==null) null
        else (baseUnitCost-discountPerUnit)*totalUnitsReceived.toBigDecimal()
    fun validate() {
        require(totalUnitsReceived!=null && totalUnitsReceived in 0..99999) { "Enter total units received as a whole number from 0 to 99,999." }
        require(unitsPerCase!=null && unitsPerCase in 1..10000) { "Enter units per case as a whole number from 1 to 10,000." }
        require(baseUnitCost!=null && baseUnitCost.signum()>=0) { "Enter the base cost of one unit before discount." }
        require(discountPerUnit!=null && discountPerUnit.signum()>=0) { "Enter discount for one unit (0 if none)." }
        require(discountPerUnit<=baseUnitCost) { "Discount per unit cannot exceed base cost per unit." }
        require(netLineAmount!=null && netLineAmount.signum()>=0) { "Enter the net amount printed for this whole line." }
        require(listOf(baseUnitCost,discountPerUnit,netLineAmount).all { it<=BigDecimal("99999999") }) { "An amount exceeds the supported range." }
        require((calculatedAmount!!-netLineAmount).abs()<=BigDecimal("0.02")) {
            "Calculated total is $${calculatedAmount!!.setScale(2,RoundingMode.HALF_UP)}; the invoice line says $${netLineAmount.setScale(2,RoundingMode.HALF_UP)}. Check total units and unit costs."
        }
        val byCase=totalUnitsReceived>0 && unitsPerCase>1 && totalUnitsReceived%unitsPerCase==0
        require(baseUnitCost*(if(byCase) unitsPerCase else 1).toBigDecimal()<=BigDecimal("99999999")) { "The resulting case cost exceeds the supported range." }
    }
    // Send canonical source as well as unitEdit for compatibility with the existing API.
    // Deep-copy keeps unknown metadata and never changes the displayed row until save succeeds.
    fun legacySource(original: JsonObject): JsonObject {
        validate()
        val units=totalUnitsReceived!!;val pack=unitsPerCase!!
        val byCase=units>0 && pack>1 && units%pack==0
        return original.deepCopy().apply {
            addProperty("caseQty",if(byCase) units/pack else 0);addProperty("bottleQty",if(byCase) 0 else units)
            addProperty("packSize",pack);addProperty("effectiveUnitsPerCase",pack)
            addProperty("caseCost",baseUnitCost!!*(if(byCase) pack else 1).toBigDecimal())
            addProperty("unitCost",baseUnitCost-discountPerUnit!!);addProperty("lineDiscount",discountPerUnit)
            addProperty("discountBasis",if(discountPerUnit.signum()==0) "none" else "per_unit")
            addProperty("unitCostBasis","net_per_unit");addProperty("extendedTotal",netLineAmount)
        }
    }
    companion object {
        fun from(source:JsonObject):ScanUnitCorrection {
            fun amount(key:String)=source.text(key).toBigDecimalOrNull()
            fun whole(key:String)=try { amount(key)?.intValueExact() } catch(_:ArithmeticException) { null }
            val cases=whole("caseQty");val bottles=whole("bottleQty");val byCase=(cases ?: 0)>0
            val pack=whole("effectiveUnitsPerCase") ?: whole("packSize") ?: whole("suggestedUnitsPerCase") ?: if(byCase) null else 1
            val total=if(cases==null && bottles==null || byCase && (pack==null || pack<=0)) null else (cases ?: 0)*(pack ?: 1)+(bottles ?: 0)
            val raw=amount("lineDiscount")
            val discount=if(source.text("discountBasis")=="none" || raw?.signum()==0) BigDecimal.ZERO else when(source.text("discountBasis")) {
                "per_unit" -> raw
                "per_case" -> if(pack!=null && pack>0) raw?.perUnits(pack) else null
                "line_total" -> if(total!=null && total>0) raw?.perUnits(total) else null
                else -> null
            }
            val divisor=if(byCase) pack else 1
            val base=if(divisor==null || divisor<=0) null else when(source.text("unitCostBasis")) {
                "net_per_unit" -> amount("caseCost")?.perUnits(divisor)
                "gross" -> amount("unitCost")?.perUnits(divisor)
                "net" -> if(discount!=null) amount("unitCost")?.perUnits(divisor)?.plus(discount) else null
                else -> null
            }
            return ScanUnitCorrection(total,pack,base,discount,amount("extendedTotal"))
        }
    }
}
fun BigDecimal.perUnits(units:Int):BigDecimal = divide(units.toBigDecimal(),12,RoundingMode.HALF_UP)
fun BigDecimal?.entryText():String = this?.stripTrailingZeros()?.toPlainString().orEmpty()
