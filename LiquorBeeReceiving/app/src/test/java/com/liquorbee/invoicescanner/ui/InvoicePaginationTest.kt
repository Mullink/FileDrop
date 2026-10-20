package com.liquorbee.invoicescanner.ui
import org.junit.Test
import org.junit.Assert.*

class InvoicePaginationTest {
    data class Item(val id:Int,var quantity:Int=1)
    @Test fun searchIncludesLaterPagesAndKeepsOriginalObjects() {
        val invoice=(1..65).map { Item(it) }
        val results=searchInvoiceItems(invoice,"product 65") { "Product ${it.id}" }
        val page=invoicePage(results,4)
        assertEquals(1,page.number);assertEquals(65,page.items.single().id)
        page.items.single().quantity=9;assertEquals(9,invoice[64].quantity)
        assertEquals(65,searchInvoiceItems(invoice,"") { it.id.toString() }.size)
        assertTrue(searchInvoiceItems(invoice,"missing") { it.id.toString() }.isEmpty())
    }
    @Test fun pagesRetainEditsForTheWholeInvoice() {
        val invoice=(1..105).map { Item(it) }
        val first=invoicePage(invoice,1);assertEquals(20,first.items.size)
        first.items[0].quantity=9
        val second=invoicePage(invoice,2);assertEquals(21,second.items[0].id);second.items[0].quantity=7
        assertEquals(9,invoicePage(invoice,1).items[0].quantity)
        assertEquals(7,invoice[20].quantity);assertEquals(105,invoice.size)
    }
    @Test fun finalPageAndFilteredResultsClampSafely() {
        val invoice=(1..105).map { Item(it) }
        assertEquals(5,invoicePage(invoice,6).items.size)
        val filtered=invoice.filter { it.id>100 }
        val page=invoicePage(filtered,6)
        assertEquals(1,page.number);assertEquals(101,page.items[0].id)
        assertEquals(5,invoicePage(invoice.take(100),6).number)
    }
    @Test fun emptyAndExactPageBoundaries() {
        val empty=invoicePage(emptyList<Item>(),9)
        assertEquals(1,empty.number);assertTrue(empty.items.isEmpty())
        assertEquals("No items match this filter",empty.description)
        assertEquals(1,invoicePage((1..20).toList(),2).number)
        assertEquals(1,invoicePage((1..20).toList(),-3).number)
    }
}
