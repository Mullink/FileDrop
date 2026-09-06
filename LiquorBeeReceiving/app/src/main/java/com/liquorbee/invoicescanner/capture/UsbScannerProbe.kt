package com.liquorbee.invoicescanner.capture

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
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Full USB probe for the Brother DS-640: detect -> enumerate interfaces/endpoints -> request
 * Android's USB permission -> open the device -> claim each interface -> (see below) attempt a
 * SAFE, read-only SCSI INQUIRY over USB Bulk-Only Transport on each. Every step's real
 * success/failure is reported, nothing faked.
 *
 * PROTOCOL RESEARCH DONE BEFORE WRITING ANY OF THIS (documented here since it drove the design):
 * searched for existing reverse-engineering of the DS-640/DSmobile protocol before guessing.
 * Found: (1) a real open-source SANE backend exists for the DSmobile family ("dsseries", covering
 * DS-620/720D/820W/920DW) - but it is a thin wrapper around Brother's own PROPRIETARY closed
 * compiled binaries (dsdrv_x64.so, NvUSBScan_x64.so); no actual protocol logic is in inspectable
 * open source there. (2) a real, fully reverse-engineered open-source Brother protocol project
 * exists (dmikushin/brscan, implementing "brscan4") - but that targets Brother's MFC/DCP
 * multi-function office printer-scanner line over a completely different USB framing (10-byte
 * wrapper + packbits/JPEG blocks) - unrelated to the DSmobile portable scanner family and not
 * something to reuse here. Net result: no public documentation of the DS-640's actual scan-trigger
 * command sequence was found. Implementing a GUESSED command sequence and calling it "scanning"
 * was rejected as irresponsible - real scanner hardware can enter a bad state (documented
 * elsewhere as requiring a power cycle) from a malformed/incorrect command, and there is no way to
 * test a guess against real hardware from this dev environment.
 *
 * What IS implemented instead: the DS-620 write-up that led to the dsseries backend explicitly
 * says it talks over Linux's SCSI Generic (/dev/sg) layer - meaning some real SCSI-CDB-over-USB
 * transport is genuinely how this device family works, most likely the same industry-standard USB
 * Bulk-Only Transport (BOT) used by USB mass-storage devices (CBW command wrapper -> data phase ->
 * CSW status wrapper). SCSI INQUIRY (opcode 0x12) is universally supported, always safe, and
 * READ-ONLY - it never actuates anything on real scanner hardware, just returns identification
 * bytes - making it a safe way to test whether the BOT-transport hypothesis is even correct on the
 * real DS-640, without risking a guessed "start scanning" command. If this succeeds with a
 * plausible response, it's real evidence the transport shape is right and a genuine scan-command
 * implementation could follow later; if it fails, that's equally real evidence to rule the
 * hypothesis out. Either way this is NOT wired into the "Scan with Brother DS-640" capture button
 * - it's diagnostic only, surfaced via the USB Diagnostics screen, until real image data can
 * actually be read back and verified.
 */
class UsbScannerProbe(private val context: Context) {

    companion object {
        private const val TAG = "USB"
        private const val ACTION_USB_PERMISSION = "com.liquorbee.invoicescanner.USB_PERMISSION"
    }

    private var receiver: BroadcastReceiver? = null

    /** Synchronous: device presence, VID/PID, and every interface's endpoints classified as
     * Bulk/Interrupt/Control/Isochronous + IN/OUT. No permission needed just to enumerate this. */
    fun describeStatic(device: UsbDevice): String {
        val sb = StringBuilder()
        sb.append("Brother DS-640 detected\n")
        sb.append("VID: 0x${device.vendorId.toString(16).uppercase()}\n")
        sb.append("PID: 0x${device.productId.toString(16).uppercase()}\n\n")

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            sb.append("Interface $i (class=${iface.interfaceClass}, subclass=${iface.interfaceSubclass}, protocol=${iface.interfaceProtocol})\n")
            for (e in 0 until iface.endpointCount) {
                val endpoint = iface.getEndpoint(e)
                val dir = if (endpoint.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
                val type = when (endpoint.type) {
                    UsbConstants.USB_ENDPOINT_XFER_BULK -> "Bulk"
                    UsbConstants.USB_ENDPOINT_XFER_INT -> "Interrupt"
                    UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "Control"
                    UsbConstants.USB_ENDPOINT_XFER_ISOC -> "Isochronous"
                    else -> "Unknown"
                }
                val line = "  $type $dir: 0x${endpoint.address.toString(16).uppercase()}"
                Log.d(TAG, line)
                sb.append(line).append("\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    /**
     * Full async probe: request permission, then attempt open + claim. Calls [onResult] with the
     * complete step-by-step report once every step has run (or failed) - always on the same
     * thread this was invoked from for the already-granted path, and on the BroadcastReceiver's
     * thread (main, since registered with no Handler) for the permission-prompt path.
     * [preferredInterface] lets the caller target a specific interface index once the static
     * enumeration above identifies which one looks like the scanning interface; defaults to 0.
     */
    fun probe(device: UsbDevice, preferredInterface: Int = 0, onResult: (String) -> Unit) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val staticReport = describeStatic(device)

        if (usbManager.hasPermission(device)) {
            onResult(staticReport + "\n" + attemptOpenAndClaim(usbManager, device, preferredInterface, permissionGranted = true))
            return
        }

        // MUTABLE, not IMMUTABLE - this is the actual fix for a real bug found here. UsbManager
        // .requestPermission() has the system attach EXTRA_PERMISSION_GRANTED to this exact
        // PendingIntent's Intent when it broadcasts the result back; an immutable PendingIntent
        // cannot have extras added to it by the system, so that extra silently never arrives and
        // getBooleanExtra(EXTRA_PERMISSION_GRANTED, false) always reads its false default -
        // regardless of what the user actually tapped in the permission dialog. This exactly
        // matches a real-device report of "USB Permission: NO" appearing every time. The control
        // flow itself (receiver registered before requestPermission() is called; onResult only
        // ever invoked from inside onReceive or the already-has-permission branch, never read back
        // synchronously) was already correct on inspection - this was the one real defect.
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != ACTION_USB_PERMISSION) return
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                try { context.unregisterReceiver(this) } catch (e: Exception) { /* already unregistered */ }
                receiver = null
                onResult(staticReport + "\n" + attemptOpenAndClaim(usbManager, device, preferredInterface, permissionGranted = granted))
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        // API 33+ requires an explicit export flag on a dynamically-registered receiver. This
        // action string is private to this app (only our own PendingIntent triggers it via the
        // system's permission-result delivery), so NOT_EXPORTED is correct here - flag as the
        // first thing to flip if the permission callback never arrives on the real device.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        usbManager.requestPermission(device, permissionIntent)
    }

    private fun attemptOpenAndClaim(
        usbManager: UsbManager,
        device: UsbDevice,
        interfaceIndex: Int,
        permissionGranted: Boolean
    ): String {
        val sb = StringBuilder()
        sb.append("USB Permission: ${if (permissionGranted) "YES" else "NO"}\n")
        if (!permissionGranted) {
            sb.append("Open Device: SKIPPED (no permission)\n")
            sb.append("Claim Interface: SKIPPED (no permission)\n")
            return sb.toString()
        }

        val connection: UsbDeviceConnection? = try {
            usbManager.openDevice(device)
        } catch (e: Exception) {
            sb.append("Open Device: FAILURE (${e.message})\n")
            return sb.toString()
        }

        if (connection == null) {
            sb.append("Open Device: FAILURE (openDevice returned null)\n")
            return sb.toString()
        }
        sb.append("Open Device: SUCCESS\n")

        if (device.interfaceCount == 0) {
            sb.append("Claim Interface: SKIPPED (device reports no interfaces)\n")
            connection.close()
            return sb.toString()
        }

        // Try every interface, not just the preferred one - we don't know from static enumeration
        // alone which one (if either) is the command channel, and this is cheap to check on real
        // hardware in one pass rather than requiring the user to re-run this per interface.
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            val claimed = try {
                connection.claimInterface(iface, true)
            } catch (e: Exception) {
                sb.append("Interface $i Claim: FAILURE (${e.message})\n")
                continue
            }
            sb.append("Interface $i Claim: ${if (claimed) "SUCCESS" else "FAILURE"}\n")
            if (claimed) {
                sb.append(tryBotInquiry(connection, iface, i))
                connection.releaseInterface(iface)
            }
        }

        connection.close()
        return sb.toString()
    }

    /**
     * Safe, read-only probe: sends a standard SCSI INQUIRY (opcode 0x12) wrapped in a USB
     * Bulk-Only Transport Command Block Wrapper (the same framing USB mass-storage devices use),
     * reads back the data phase, then reads the Command Status Wrapper. INQUIRY never actuates
     * anything on real SCSI-class hardware - it only returns identification bytes - so this cannot
     * put the scanner in a bad state even if the BOT-transport hypothesis turns out to be wrong for
     * this device. See the class doc for why this specific approach was chosen. Every step's real
     * outcome is reported; a failure at any step just means this interface either isn't BOT-based
     * or isn't the command channel - not a bug to fix blindly.
     */
    private fun tryBotInquiry(connection: UsbDeviceConnection, iface: UsbInterface, interfaceIndex: Int): String {
        val sb = StringBuilder()
        val outEndpoint = findEndpoint(iface, UsbConstants.USB_DIR_OUT)
        val inEndpoint = findEndpoint(iface, UsbConstants.USB_DIR_IN)

        if (outEndpoint == null || inEndpoint == null) {
            sb.append("  Interface $interfaceIndex SCSI INQUIRY: SKIPPED (missing bulk IN/OUT endpoint)\n")
            return sb.toString()
        }

        val tag = 0x12345678
        val inquiryAllocationLength = 36 // standard SCSI INQUIRY response size

        // 31-byte Command Block Wrapper: signature "USBC", tag, expected data-phase length,
        // direction flag (0x80 = device-to-host), LUN, CDB length, then the 6-byte INQUIRY CDB
        // (opcode 0x12, EVPD=0, page code=0, reserved, allocation length, control) zero-padded to 16.
        val cbw = ByteBuffer.allocate(31).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x43425355.toInt()) // dCBWSignature "USBC"
            putInt(tag)                // dCBWTag
            putInt(inquiryAllocationLength) // dCBWDataTransferLength
            put(0x80.toByte())         // bmCBWFlags: IN
            put(0x00)                  // bCBWLUN
            put(6)                     // bCBWCBLength
            put(byteArrayOf(0x12, 0x00, 0x00, 0x00, inquiryAllocationLength.toByte(), 0x00)) // CDB
            put(ByteArray(10))         // pad CBWCB to 16 bytes total
        }.array()

        // 5000ms, not 2000ms - a 0/-1 result can mean genuine non-response, but can also mean a
        // premature timeout if the device is just slow to answer. This costs nothing to rule out.
        val timeoutMs = 5000

        val cbwSent = connection.bulkTransfer(outEndpoint, cbw, cbw.size, timeoutMs)
        sb.append("  Interface $interfaceIndex CBW (command) sent: ${if (cbwSent == cbw.size) "SUCCESS ($cbwSent bytes)" else "FAILURE (returned $cbwSent)"}\n")
        if (cbwSent != cbw.size) return sb.toString()

        val dataBuffer = ByteArray(inquiryAllocationLength)
        val dataReceived = bulkTransferWithStallRecovery(connection, inEndpoint, dataBuffer, dataBuffer.size, timeoutMs, sb, interfaceIndex, "INQUIRY data")
        if (dataReceived > 0) {
            val hex = dataBuffer.take(dataReceived).joinToString(" ") { "%02X".format(it) }
            val ascii = dataBuffer.take(dataReceived).map { if (it in 32..126) it.toInt().toChar() else '.' }.joinToString("")
            sb.append("  Interface $interfaceIndex INQUIRY data ($dataReceived bytes): $hex\n")
            sb.append("  Interface $interfaceIndex INQUIRY data (ascii): $ascii\n")
        } else {
            sb.append("  Interface $interfaceIndex INQUIRY data: FAILURE (returned $dataReceived)\n")
        }

        val csw = ByteArray(13)
        val cswReceived = bulkTransferWithStallRecovery(connection, inEndpoint, csw, csw.size, timeoutMs, sb, interfaceIndex, "CSW")
        if (cswReceived == 13) {
            val cswBuf = ByteBuffer.wrap(csw).order(ByteOrder.LITTLE_ENDIAN)
            val signature = cswBuf.int
            val cswTag = cswBuf.int
            val residue = cswBuf.int
            val status = csw[12].toInt()
            val signatureOk = signature == 0x53425355.toInt()
            val tagOk = cswTag == tag
            sb.append(
                "  Interface $interfaceIndex CSW (status): signature=${if (signatureOk) "OK" else "MISMATCH"}, " +
                    "tag=${if (tagOk) "OK" else "MISMATCH"}, residue=$residue, status=$status " +
                    "(${if (status == 0) "SUCCESS" else "COMMAND FAILED"})\n"
            )
        } else {
            sb.append("  Interface $interfaceIndex CSW (status): FAILURE (returned $cswReceived bytes, expected 13)\n")
        }

        return sb.toString()
    }

    /**
     * Per the actual USB Mass Storage Bulk-Only Transport spec: if a bulk endpoint stalls mid
     * transaction, the host is expected to clear that stall (a standard CLEAR_FEATURE/
     * ENDPOINT_HALT control request) before the endpoint will respond again - a real, spec-defined
     * recovery step, not a guess, and one my first pass skipped. On a non-positive result, attempts
     * that clear and retries the same transfer exactly once; reports what happened at each step so
     * a "still fails after clearing the stall" result is real evidence, not silently swallowed.
     */
    private fun bulkTransferWithStallRecovery(
        connection: UsbDeviceConnection,
        endpoint: UsbEndpoint,
        buffer: ByteArray,
        length: Int,
        timeoutMs: Int,
        log: StringBuilder,
        interfaceIndex: Int,
        label: String
    ): Int {
        val first = connection.bulkTransfer(endpoint, buffer, length, timeoutMs)
        if (first != null && first > 0) return first

        log.append("  Interface $interfaceIndex $label: first attempt returned $first - clearing endpoint 0x${endpoint.address.toString(16).uppercase()} stall and retrying once\n")

        // Standard control request: bmRequestType=0x02 (host-to-device, standard, endpoint
        // recipient), bRequest=0x01 (CLEAR_FEATURE), wValue=0x0000 (ENDPOINT_HALT), wIndex=endpoint
        // address. Its own return value is just informational here - proceed to retry regardless,
        // since some devices don't ack this cleanly even when the retry then succeeds.
        val clearResult = connection.controlTransfer(0x02, 0x01, 0x0000, endpoint.address, null, 0, timeoutMs)
        log.append("  Interface $interfaceIndex $label: CLEAR_FEATURE(ENDPOINT_HALT) returned $clearResult\n")

        val retry = connection.bulkTransfer(endpoint, buffer, length, timeoutMs)
        log.append("  Interface $interfaceIndex $label: retry after clear returned $retry\n")
        return retry ?: -1
    }

    private fun findEndpoint(iface: UsbInterface, direction: Int): UsbEndpoint? {
        for (e in 0 until iface.endpointCount) {
            val endpoint = iface.getEndpoint(e)
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK && endpoint.direction == direction) {
                return endpoint
            }
        }
        return null
    }
}
