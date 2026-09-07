package com.liquorbee.wholesale.printing

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val ACTION_USB_PERMISSION = "com.liquorbee.wholesale.USB_PRINTER_PERMISSION"
private const val TRANSFER_TIMEOUT_MS = 3_000

/** Raw USB ESC/POS printing - the same underlying mechanism SNBC's own POSAPI SDK and the
 * open-source RTPrinterLibrary (used for Lyntek printers) both wrap: claim the USB interface,
 * bulk-transfer command bytes to its OUT endpoint. No vendor SDK needed for either model - a
 * receipt printer's whole point is that it understands plain ESC/POS over its data endpoint.
 *
 * USB permission flow mirrors BrotherUsbCaptureSource's proven pattern (same iMin hardware
 * family) - MUTABLE PendingIntent is required or EXTRA_PERMISSION_GRANTED never arrives. */
class UsbPrinterManager(private val context: Context) {

    private val usbManager: UsbManager
        get() = context.getSystemService(Context.USB_SERVICE) as UsbManager

    fun attachedDevices(): List<UsbDevice> = usbManager.deviceList.values.toList()

    // Confirmed against SNBC's own official SDK (decompiled from the iMin/Quantic POS app) -
    // SNBC printers report their exact model as the USB product name string, so this list matches
    // that SDK's own auto-detection exactly. Lyntek's "ACE H2" is matched more loosely since no
    // equivalent confirmed name list exists for it.
    private val knownPrinterNames = listOf(
        "BTP-S80", "BTP-L580II", "BTP-R180", "BTP-R180II", "BTP-R580II", "BTP-R681",
        "BTP-R880NP", "BTP-R980", "BTP-R980III", "BTP-R990", "BTP-U60", "BTP-U80", "BTP-M300",
        "ACE H2", "ACE-H2", "ACEH2"
    )

    /** Best-guess automatic pick: a known model name match first, then any device that declares
     * the standard USB Printer Class (7) on one of its interfaces, else null (caller should fall
     * back to a manual device picker - see activity_choose_printer / showPrinterPicker). */
    fun findLikelyPrinter(): UsbDevice? {
        val devices = attachedDevices()
        devices.firstOrNull { device ->
            val name = device.productName ?: return@firstOrNull false
            knownPrinterNames.any { known -> name.contains(known, ignoreCase = true) }
        }?.let { return it }

        return devices.firstOrNull { device ->
            (0 until device.interfaceCount).any { i -> device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_PRINTER }
        }
    }

    suspend fun print(device: UsbDevice, data: ByteArray) = withContext(Dispatchers.IO) {
        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
        }
        if (!usbManager.hasPermission(device)) {
            throw IllegalStateException("USB permission for the printer was denied.")
        }

        val (iface, outEndpoint) = findBulkOutInterface(device)
            ?: throw IllegalStateException("This USB device doesn't expose a printer data endpoint.")

        val connection = usbManager.openDevice(device)
            ?: throw IllegalStateException("Could not open the printer (already in use by another app?).")

        try {
            if (!connection.claimInterface(iface, true)) {
                throw IllegalStateException("Could not claim the printer's USB interface.")
            }
            try {
                // Large receipts can exceed a single USB transfer's practical size on some
                // controllers - chunking is cheap insurance and matches how every ESC/POS USB
                // driver actually sends data in practice.
                var offset = 0
                val chunkSize = 4096
                while (offset < data.size) {
                    val length = minOf(chunkSize, data.size - offset)
                    val chunk = data.copyOfRange(offset, offset + length)
                    val sent = connection.bulkTransfer(outEndpoint, chunk, chunk.size, TRANSFER_TIMEOUT_MS)
                    if (sent < 0) throw IllegalStateException("USB write to the printer failed or timed out.")
                    offset += length
                }
            } finally {
                connection.releaseInterface(iface)
            }
        } finally {
            connection.close()
        }
    }

    // Printer class (7) is standard, but some receipt printers instead expose a vendor-specific
    // class - fall back to "any interface with a bulk OUT endpoint" the same way
    // BrotherUsbCaptureSource does for the scanner, rather than only trusting the class byte.
    private fun findBulkOutInterface(device: UsbDevice): Pair<UsbInterface, UsbEndpoint>? {
        var fallback: Pair<UsbInterface, UsbEndpoint>? = null
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            for (e in 0 until iface.endpointCount) {
                val endpoint = iface.getEndpoint(e)
                if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (endpoint.direction != UsbConstants.USB_DIR_OUT) continue
                if (iface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) return iface to endpoint
                if (fallback == null) fallback = iface to endpoint
            }
        }
        return fallback
    }

    private suspend fun requestPermission(device: UsbDevice) = suspendCancellableCoroutine<Unit> { cont ->
        var receiver: BroadcastReceiver? = null
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != ACTION_USB_PERMISSION) return
                try { context.unregisterReceiver(this) } catch (e: IllegalArgumentException) { /* already unregistered */ }
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (granted) cont.resume(Unit) else cont.resumeWithException(IllegalStateException("USB permission denied by user."))
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        // MUTABLE, not IMMUTABLE - an immutable PendingIntent can't have EXTRA_PERMISSION_GRANTED
        // attached by the system, so the extra never arrives and hasPermission()/the broadcast
        // both silently read as denied regardless of the user's tap (same gotcha documented in
        // BrotherUsbCaptureSource).
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        cont.invokeOnCancellation { try { context.unregisterReceiver(receiver) } catch (e: IllegalArgumentException) {} }
        usbManager.requestPermission(device, permissionIntent)
    }
}
