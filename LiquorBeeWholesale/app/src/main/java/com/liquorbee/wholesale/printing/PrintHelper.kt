package com.liquorbee.wholesale.printing

import android.app.AlertDialog
import android.content.Context
import android.hardware.usb.UsbDevice
import com.liquorbee.wholesale.network.WholesaleOpenOrderDto

/** Shared entry point for both Order Detail's "Print" button and Place Order's post-submit print
 * - picks a USB receipt printer (auto if there's an obvious match, otherwise asks) and sends the
 * ESC/POS receipt built by ReceiptFormatter. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
object PrintHelper {

    suspend fun printOrder(
        context: Context,
        order: WholesaleOpenOrderDto,
        storeName: String?,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val manager = UsbPrinterManager(context)
        val device = manager.findLikelyPrinter() ?: run {
            val chosen = pickDeviceManually(context, manager.attachedDevices())
            chosen
        }
        if (device == null) {
            onResult(false, "No USB printer found. Connect the receipt printer and try again.")
            return
        }

        try {
            val data = ReceiptFormatter.buildOrderReceipt(order, storeName)
            manager.print(device, data)
            onResult(true, "Sent to printer.")
        } catch (e: Exception) {
            onResult(false, "Failed to print: ${e.message}")
        }
    }

    // Falls back to this when no device matches a known printer name or the standard USB Printer
    // Class - lets the user pick from whatever's actually plugged in rather than failing outright,
    // same reasoning as the receiving app's USB Diagnostics screen existing at all.
    private suspend fun pickDeviceManually(context: Context, devices: List<UsbDevice>): UsbDevice? {
        if (devices.isEmpty()) return null
        if (devices.size == 1) return devices[0]

        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val labels = devices.map { "${it.productName ?: "Unknown device"} (VID ${it.vendorId}, PID ${it.productId})" }.toTypedArray()
            AlertDialog.Builder(context)
                .setTitle("Choose the receipt printer")
                .setItems(labels) { _, which -> cont.resume(devices[which], null) }
                .setOnCancelListener { cont.resume(null, null) }
                .show()
        }
    }
}
