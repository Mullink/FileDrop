package com.liquorbee.invoicescanner.capture

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * USB-OTG scanning from the Brother DSmobile DS-640 document scanner, mirroring how the Windows
 * desktop app talks to its scanner over TWAIN via a direct USB/driver connection (see
 * LiquorBeeInvoiceScanner/Scanning/ITwainScanner.cs + NTwainScanner.cs) - same wired-hardware
 * shape.
 *
 * The command protocol below (endpoints, the SSP "Set Scan Parameters" / XSC "Execute Scan" ASCII
 * command format, and the bulk-in read pattern) is NOT from any public Brother spec or SDK -
 * Brother ships none for this model (see UsbScannerProbe's research notes, still accurate re:
 * "no Android SDK exists"). It was reverse-engineered byte-for-byte from a USBPcap capture of the
 * desktop LiquorBeeInvoiceScanner app performing a real scan on Windows, which is how the earlier
 * SCSI/Bulk-Only-Transport probe attempt in this file got superseded - the device doesn't speak
 * USB Mass Storage at all, it uses its own ASCII command protocol over plain bulk endpoints.
 *
 * One instance is meant to live for the Activity's session (see ScanActivity) and is reused across
 * every capture() call - multiple pages per invoice and multiple invoices per session all share the
 * same open USB connection/claimed interface, opened once on first use. A failed transfer drops the
 * connection so the next call reopens fresh instead of wedging.
 *
 * File picker (FilePickerCaptureSource) and camera (CameraCaptureSource, where available) remain
 * the fallback paths and are unaffected by any of this - this matters more than "nice to have" on
 * the camera-less iMin Swan 1 Pro POS terminal this app targets, where Brother-USB is the primary
 * way to get a page in without a working camera.
 */
class BrotherUsbCaptureSource(private val context: Context) : PageCaptureSource {

    override val displayName: String = "Brother DS-640 (USB)"

    private val usbManager: UsbManager
        get() = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // Serializes capture() calls - the device protocol is strictly request/response with no
    // interleaving, so two pages can never be in flight on the same connection at once. This is
    // what makes "scan page 1, then page 2, then page 3" (ScanActivity's multi-page/multi-
    // submission flow) safe without the caller having to coordinate anything itself.
    private val gate = Mutex()

    private var connection: UsbDeviceConnection? = null
    private var claimedInterface: UsbInterface? = null
    private var bulkIn: UsbEndpoint? = null
    private var bulkOut: UsbEndpoint? = null

    private val trace = StringBuilder()
    private var traceStartMs = 0L
    // Every log line gets a "+Nms" elapsed-since-capture()-started prefix - added specifically to
    // find out WHERE a slow scan's time actually goes (device feed/encode vs. USB data transfer vs.
    // our own end-of-stream detection), instead of guessing at another timing constant to tweak.
    private fun log(message: String) { trace.append("[+${System.currentTimeMillis() - traceStartMs}ms] ").append(message).append('\n') }

    /** Full step-by-step account of the most recent capture() call - success or failure - so a
     * caller with no Logcat access (e.g. testing on a sealed/production POS terminal) can still
     * see exactly where things stood. Overwritten at the start of every capture() call. */
    var lastAttemptLog: String = ""
        private set

    override fun isAvailable(): Boolean = findAttachedBrotherDevice() != null

    fun findAttachedBrotherDevice(): UsbDevice? =
        usbManager.deviceList.values.firstOrNull { it.vendorId == BROTHER_USB_VENDOR_ID }

    suspend fun capture(): CaptureResult = gate.withLock {
        trace.clear()
        traceStartMs = System.currentTimeMillis()
        try {
            ensureConnected()
            val jpegBytes = runScanSequence()
            log("Decoding ${jpegBytes.size} JPEG bytes as a bitmap...")
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                ?: run {
                    log("BitmapFactory.decodeByteArray returned null.")
                    return@withLock CaptureResult.Failure("The DS-640 returned data that could not be decoded as an image.").also { lastAttemptLog = trace.toString() }
                }
            log("Bitmap decoded: ${bitmap.width}x${bitmap.height}.")
            lastAttemptLog = trace.toString()
            CaptureResult.Success(CapturedPage(bitmap))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("FAILED: ${e::class.simpleName}: ${e.message}")
            // Leave the device in a known state for the NEXT call rather than repeatedly failing
            // against a possibly-stalled connection.
            closeConnection()
            lastAttemptLog = trace.toString()
            CaptureResult.Failure(e.message ?: "USB scan failed.")
        }
    }

    /** Releases the USB connection. Not required between individual pages - call when done
     * scanning for the session (e.g. logout) or if the scanner is unplugged. */
    fun disconnect() = closeConnection()

    private suspend fun ensureConnected() {
        if (connection != null && bulkIn != null && bulkOut != null) {
            log("Reusing already-open connection from a previous scan.")
            return
        }

        val device = findAttachedBrotherDevice()
            ?: throw IllegalStateException("No Brother DS-640 detected over USB. Connect it via USB-OTG, or use Import Image instead.")
        log("Device found: ${device.deviceName} (VID=0x${device.vendorId.toString(16)}, PID=0x${device.productId.toString(16)})")

        if (!usbManager.hasPermission(device)) {
            log("No USB permission yet - requesting it now (a system dialog may appear).")
            requestPermission(device)
            log("Permission request completed.")
        } else {
            log("USB permission already granted.")
        }
        if (!usbManager.hasPermission(device)) {
            throw IllegalStateException("USB permission for the DS-640 was denied.")
        }

        val (iface, inEp, outEp) = findBulkInterface(device)
            ?: throw IllegalStateException("The DS-640 didn't expose the expected bulk endpoints.")
        log("Bulk interface found: OUT=0x${outEp.address.toString(16)}, IN=0x${inEp.address.toString(16)}")

        val conn = usbManager.openDevice(device)
            ?: throw IllegalStateException("Could not open the DS-640 (already in use by another app?).")
        log("Device opened.")

        if (!conn.claimInterface(iface, true)) {
            conn.close()
            throw IllegalStateException("Could not claim the DS-640's USB interface.")
        }
        log("Interface claimed.")

        connection = conn
        claimedInterface = iface
        bulkIn = inEp
        bulkOut = outEp
    }

    // The DS-640 exposes TWO interfaces with bulk endpoints - confirmed on real hardware via USB
    // Diagnostics: interface 0 is OUT=0x02/IN=0x81 (unrelated - some other function, possibly
    // legacy/status), interface 1 is OUT=0x04/IN=0x83, which is the actual command channel Brother's
    // own Windows driver talks to (confirmed byte-for-byte via the USBPcap capture). Picking "any"
    // bulk in/out pair (the original approach here) silently grabbed interface 0 instead and every
    // command went nowhere - this must match the exact captured endpoint addresses, not just type/
    // direction.
    private fun findBulkInterface(device: UsbDevice): Triple<UsbInterface, UsbEndpoint, UsbEndpoint>? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            var inEp: UsbEndpoint? = null
            var outEp: UsbEndpoint? = null
            for (e in 0 until iface.endpointCount) {
                val endpoint = iface.getEndpoint(e)
                if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (endpoint.address == COMMAND_ENDPOINT_IN) inEp = endpoint
                else if (endpoint.address == COMMAND_ENDPOINT_OUT) outEp = endpoint
            }
            if (inEp != null && outEp != null) return Triple(iface, inEp, outEp)
        }
        return null
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

        // MUTABLE, not IMMUTABLE - see UsbScannerProbe's note: an immutable PendingIntent can't
        // have EXTRA_PERMISSION_GRANTED attached by the system, so the extra never arrives and
        // hasPermission()/the broadcast both silently read as denied regardless of the user's tap.
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        cont.invokeOnCancellation { try { context.unregisterReceiver(receiver) } catch (e: IllegalArgumentException) {} }
        usbManager.requestPermission(device, permissionIntent)
    }

    private suspend fun runScanSequence(): ByteArray = withContext(Dispatchers.IO) {
        val conn = connection!!
        val out = bulkOut!!
        val inEp = bulkIn!!

        val ssp = buildSetScanParametersCommand()
        bulkWrite(conn, out, ssp)
        log("SSP (set scan parameters) sent, ${ssp.size} bytes.")

        // Ack/capability response - not parsed in detail, just drained so it can't be misread as
        // the start of image data on the next transfer.
        val ackBuffer = ByteArray(512)
        val ackLen = bulkRead(conn, inEp, ackBuffer)
        log("SSP ack received: $ackLen bytes" + if (ackLen > 0) " (starts ${hexPrefix(ackBuffer, ackLen)})" else "")

        val xsc = buildExecuteScanCommand()
        bulkWrite(conn, out, xsc)
        log("XSC (execute scan) sent, ${xsc.size} bytes - this is what should trigger the feed motor.")

        readJpegStream(conn, inEp)
    }

    private fun hexPrefix(buffer: ByteArray, len: Int, max: Int = 16): String =
        buffer.take(minOf(len, max)).joinToString(" ") { "%02X".format(it) }

    private fun buildSetScanParametersCommand(): ByteArray = buildCommand(
        "SSP",
        "OS=WIN\n" +
            "PSRC=AUTO\n" +
            "RESO=300,300\n" +
            "CLR=C24BIT\n" +
            "AREA=ATDSKW\n" +
            "MRGN=0,0,0,0\n" +
            "DPLX=OFF\n" +
            "BRIT=50\n" +
            "CONT=50\n" +
            "COMP=JPEG\n" +
            "JSF=420\n" +
            "IPRC=NORMAL\n" +
            "PTYPE=NORMAL\n" +
            "PAGE=0\n" +
            "LONG=OFF\n" +
            "CARR=OFF\n" +
            "RMGC=OFF\n" +
            "DTDF=OFF\n" +
            "DT4V=OFF\n" +
            "DSKW=OFF\n" +
            "LSMD=OFF\n" +
            "RMBP=OFF\n" +
            "RMMR=OFF\n" +
            "GMMA=OFF\n" +
            "TONE=OFF\n" +
            "QTFD=OFF\n" +
            "ATCN=OFF\n" +
            "ATCRP=OFF\n" +
            "USER=\n"
    )

    private fun buildExecuteScanCommand(): ByteArray = buildCommand("XSC", "RESO=300,300\nAREA=ATDSKW\nMODE=NORMAL\n")

    // Byte-for-byte match to the captured driver's format: ESC, the 3-letter command name, a
    // newline, then newline-terminated key=value pairs, closed with a single 0x80 byte.
    private fun buildCommand(name: String, parameters: String): ByteArray {
        val body = "$name\n$parameters".toByteArray(Charsets.US_ASCII)
        return body + 0x80.toByte()
    }

    // A short (non-zero) read used to be treated as "that was the last chunk," based on the one
    // reference (Windows) capture where that happened to be true. Confirmed on real hardware this
    // was wrong: the scanner's feed motor runs to completion regardless, but our host stopped
    // reading (and returned) before all the real image data arrived, producing a short image even
    // though the full page was fed. A short read is NOT a reliable end-of-stream signal in
    // general - it just means the device batched that particular transfer smaller, which can
    // happen mid-stream on longer pages too, not only at the true end.
    //
    // Fix: never end the stream on a short-but-nonzero read - only on sustained silence
    // (MAX_CONSECUTIVE_EMPTY_READS truly empty reads in a row). Deliberately NOT scanning for the
    // JPEG end-of-image marker (FF D9) here either - that approach was tried and caused the
    // scanner to physically jam on real hardware for reasons never fully isolated; reading until
    // sustained silence is a much smaller, lower-risk change. Any trailing non-image status bytes
    // the device sends after the real image data (confirmed harmless in the reference capture -
    // a handful of small packets) just become harmless trailing bytes after the JPEG's own FF D9
    // in the buffer, which every JPEG decoder (including BitmapFactory) ignores.
    private fun readJpegStream(connection: UsbDeviceConnection, endpoint: UsbEndpoint): ByteArray {
        val image = ByteArrayOutputStream()
        val buffer = ByteArray(READ_CHUNK_SIZE)
        var sawJpegStart = false
        var emptyReadsBeforeStart = 0
        var nonEmptyReadsBeforeStart = 0
        val deadline = System.currentTimeMillis() + PRE_IMAGE_TIMEOUT_MS

        // Small status packets (a few bytes) are interleaved between big image chunks in the
        // captured session - skip anything before the JPEG SOI marker (FF D8).
        while (!sawJpegStart) {
            if (System.currentTimeMillis() > deadline) {
                throw IllegalStateException(
                    "Timed out after ${PRE_IMAGE_TIMEOUT_MS / 1000}s waiting for image data " +
                        "($emptyReadsBeforeStart empty + $nonEmptyReadsBeforeStart non-empty reads, " +
                        "none starting with the JPEG marker). The scanner likely never started " +
                        "feeding - check paper is loaded and the feeder tray is seated correctly."
                )
            }

            val read = connection.bulkTransfer(endpoint, buffer, buffer.size, TRANSFER_TIMEOUT_MS)

            if (read <= 0) {
                emptyReadsBeforeStart++
                continue
            }

            if (read < 2 || buffer[0] != 0xFF.toByte() || buffer[1] != 0xD8.toByte()) {
                nonEmptyReadsBeforeStart++
                log("Pre-image read: $read bytes, not a JPEG start (starts ${hexPrefix(buffer, read)})")
                continue
            }

            sawJpegStart = true
            log("JPEG start marker found after $emptyReadsBeforeStart empty + $nonEmptyReadsBeforeStart non-empty reads.")
            image.write(buffer, 0, read)
        }

        // Keep reading regardless of chunk size - only sustained silence ends the stream.
        var consecutiveEmptyReads = 0
        var chunkCount = 0
        var lastRealDataAtMs = System.currentTimeMillis()
        while (true) {
            val read = connection.bulkTransfer(endpoint, buffer, buffer.size, TRANSFER_TIMEOUT_MS)

            if (read <= 0) {
                consecutiveEmptyReads++
                if (consecutiveEmptyReads >= MAX_CONSECUTIVE_EMPTY_READS) {
                    log("Giving up after $consecutiveEmptyReads consecutive empty reads, ${image.size()} bytes total, " +
                        "${System.currentTimeMillis() - lastRealDataAtMs}ms since the last real data chunk.")
                    break
                }
                continue
            }
            consecutiveEmptyReads = 0
            chunkCount++
            lastRealDataAtMs = System.currentTimeMillis()
            image.write(buffer, 0, read)
            // Every chunk, not just failures - this is what actually shows whether a slow scan is
            // spending its time on USB data transfer (steady stream of these) vs. something else
            // entirely (a long gap between this log and the next one).
            log("Chunk #$chunkCount: $read bytes (${image.size()} total so far).")
        }

        log("Image stream complete: ${image.size()} bytes total.")
        return image.toByteArray()
    }

    private fun bulkWrite(connection: UsbDeviceConnection, endpoint: UsbEndpoint, data: ByteArray) {
        val sent = connection.bulkTransfer(endpoint, data, data.size, TRANSFER_TIMEOUT_MS)
        if (sent < 0) throw IllegalStateException("USB write to the DS-640 failed or timed out.")
    }

    private fun bulkRead(connection: UsbDeviceConnection, endpoint: UsbEndpoint, buffer: ByteArray): Int {
        val read = connection.bulkTransfer(endpoint, buffer, buffer.size, TRANSFER_TIMEOUT_MS)
        if (read < 0) throw IllegalStateException("USB read from the DS-640 failed or timed out.")
        return read
    }

    private fun closeConnection() {
        val conn = connection
        val iface = claimedInterface
        if (conn != null && iface != null) conn.releaseInterface(iface)
        conn?.close()
        connection = null
        claimedInterface = null
        bulkIn = null
        bulkOut = null
    }

    companion object {
        private const val BROTHER_USB_VENDOR_ID = 0x04F9

        // Confirmed via the USBPcap capture (and matching interface 1 in on-device USB
        // Diagnostics) - NOT the same as interface 0's 0x02/0x81 pair.
        private const val COMMAND_ENDPOINT_OUT = 0x04
        private const val COMMAND_ENDPOINT_IN = 0x83
        private const val ACTION_USB_PERMISSION = "com.liquorbee.invoicescanner.USB_PERMISSION"
        // Shortened from 10_000: this is a ceiling on how long one bulkTransfer call waits for data
        // that might not come, NOT a floor on how fast real data returns - a chunk that's already
        // ready comes back in milliseconds regardless of this value. It only matters for how long
        // completion-detection (readJpegStream's "N consecutive empty reads" check) waits per
        // attempt once the device has actually stopped sending - at 10s x MAX_CONSECUTIVE_EMPTY_READS
        // that was up to 50s of pure dead waiting after the image was already fully received. 1s
        // still gives generous headroom for a real momentary pause mid-feed while capping that
        // worst case at ~5s.
        private const val TRANSFER_TIMEOUT_MS = 1_000
        private const val PRE_IMAGE_TIMEOUT_MS = 20_000L
        private const val POST_IMAGE_TIMEOUT_MS = 30_000L
        private const val MAX_CONSECUTIVE_EMPTY_READS = 5

        // Matches the captured driver's bulk-in read size (11 full 262144-byte chunks plus one
        // shorter final chunk per page in the reference capture).
        private const val READ_CHUNK_SIZE = 256 * 1024
    }
}
