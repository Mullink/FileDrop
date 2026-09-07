package com.liquorbee.wholesale.printing

import com.liquorbee.wholesale.network.WholesaleOpenOrderDto

// 42 columns is the standard safe default for an 80mm thermal printer at normal font (both the
// SNBC BTP-R180II and Lyntek ACE H2 are 80mm-class receipt printers) - narrower than the true
// ~48-column max so slightly-wider fonts/margins on either model don't wrap mid-line.
private const val WIDTH = 42

/** Reproduces the same invoice content HennyAdmin's server-rendered PDF/HTML shows (see
 * InvoicePdfService), as plain ESC/POS text for a USB thermal receipt printer - a thermal printer
 * can't render a PDF, so this rebuilds the same fields (store name, invoice #/date, customer,
 * line items, note, totals, balance due) as fixed-width text instead. Optional web-side toggles
 * (logo, SKU column, tax breakdown, ref#, house account balance) aren't reproduced - this covers
 * the core fields every invoice has. */
object ReceiptFormatter {

    fun buildOrderReceipt(order: WholesaleOpenOrderDto, storeName: String?): ByteArray {
        val p = EscPos().init()

        p.align(EscPos.Align.CENTER).bold(true).doubleSize(true)
        p.line(storeName?.uppercase() ?: "INVOICE")
        p.doubleSize(false).bold(false)
        p.feed(1)

        p.align(EscPos.Align.LEFT)
        p.divider(WIDTH, '=')
        p.line(twoColumn("Invoice #${order.orderNumber ?: order.orderId ?: "—"}", order.createdDate?.let { dateOnly(it) } ?: ""))
        p.divider(WIDTH, '=')

        val customerLine = listOfNotNull(order.customerName, order.customerPhone, order.customerEmail)
            .filter { it.isNotBlank() }
        if (customerLine.isNotEmpty()) {
            p.bold(true).line("Customer").bold(false)
            customerLine.forEach { p.line(wrap(it, WIDTH)) }
            p.feed(1)
        }

        p.bold(true)
        p.line(itemRow("ITEM", "QTY", "PRICE", "TOTAL"))
        p.bold(false)
        p.divider(WIDTH)

        var computedSubtotal = 0.0
        order.items?.forEach { item ->
            val qty = item.quantity
            val price = item.price
            val lineTotal = qty * price
            computedSubtotal += lineTotal
            val name = item.itemName ?: item.itemCode ?: "(item)"
            // Long item names wrap to their own line above the qty/price/total row rather than
            // being truncated - the web's PDF has room to just widen the row, a 42-column receipt
            // doesn't, and silently cutting off part of a product name is worse than an extra line.
            if (name.length > 20) {
                p.line(wrap(name, WIDTH))
                p.line(itemRow("", qty.toString(), "$%.2f".format(price), "$%.2f".format(lineTotal)))
            } else {
                p.line(itemRow(name, qty.toString(), "$%.2f".format(price), "$%.2f".format(lineTotal)))
            }
        }
        p.divider(WIDTH)

        if (!order.notes.isNullOrBlank()) {
            p.bold(true).line("Note").bold(false)
            p.line(wrap(order.notes, WIDTH))
            p.feed(1)
        }

        p.line(twoColumn("Subtotal", "$%.2f".format(computedSubtotal)))
        p.bold(true).doubleSize(true)
        p.line(twoColumn("TOTAL", "$%.2f".format(order.total)))
        p.doubleSize(false)
        if (order.balance != 0.0) {
            p.line(twoColumn("BALANCE DUE", "$%.2f".format(order.balance)))
        }
        p.bold(false)

        p.feed(1)
        p.align(EscPos.Align.CENTER)
        p.line("Thank you for your order!")
        p.feed(3)
        p.cut()

        return p.build()
    }

    private fun dateOnly(raw: String): String = raw.substringBefore('T').substringBefore(' ')

    private fun twoColumn(left: String, right: String): String {
        val space = (WIDTH - left.length - right.length).coerceAtLeast(1)
        return left + " ".repeat(space) + right
    }

    // ITEM(20) QTY(4) PRICE(8) TOTAL(10) = 42
    private fun itemRow(item: String, qty: String, price: String, total: String): String {
        val itemCol = item.take(20).padEnd(20)
        val qtyCol = qty.take(4).padStart(4)
        val priceCol = price.take(8).padStart(8)
        val totalCol = total.take(10).padStart(10)
        return "$itemCol $qtyCol $priceCol $totalCol"
    }

    private fun wrap(text: String, width: Int): String =
        text.chunked(width).joinToString("\n")
}
