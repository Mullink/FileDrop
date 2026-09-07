package com.liquorbee.wholesale.capture

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.zebra.barcode.sdk.sms.ConfigurationUpdateEvent
import com.zebra.scannercontrol.DCSSDKDefs
import com.zebra.scannercontrol.DCSScannerInfo
import com.zebra.scannercontrol.FirmwareUpdateEvent
import com.zebra.scannercontrol.IDcsSdkApiDelegate
import com.zebra.scannercontrol.SDKHandler

/**
 * Real USB SNAPI-mode Zebra/Symbol scanner input (DS4608-SR and similar), via Zebra's official
 * "Scanner SDK for Android" (com.zebra.scannercontrol / SDKHandler) - NOT keyboard-wedge HID input.
 * Ported verbatim from LiquorBeeInvoiceScannerAndroid's capture/ZebraScannerController.kt, which
 * already scans successfully against this exact hardware - see that file's own doc comment for
 * how the SDK/AAR was identified (decompiling the sibling "Liquor Bee POS" app's APK) and why
 * SNAPI mode (not keyboard-wedge) is what this store's scanners are actually configured for.
 */
class ZebraScannerController(
    context: Context,
    private val onBarcodeScanned: (String) -> Unit
) : IDcsSdkApiDelegate {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sdkHandler = SDKHandler(context.applicationContext, true, false)

    init {
        sdkHandler.dcssdkSetDelegate(this)
        sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_SNAPI)

        val events = DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_APPEARANCE.value or
            DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_DISAPPEARANCE.value or
            DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_ESTABLISHMENT.value or
            DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_TERMINATION.value or
            DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_BARCODE.value
        sdkHandler.dcssdkSubsribeForEvents(events)
        sdkHandler.dcssdkEnableAvailableScannersDetection(true)

        // A scanner already plugged in before this controller was created won't fire a fresh
        // "appeared" event - connect to whatever's already attached immediately.
        sdkHandler.dcssdkGetAvailableScannersList()?.forEach { tryConnect(it) }
    }

    private fun tryConnect(scanner: DCSScannerInfo) {
        val type = scanner.connectionType
        if (type == DCSSDKDefs.DCSSDK_CONN_TYPES.DCSSDK_CONNTYPE_USB_SNAPI ||
            type == DCSSDKDefs.DCSSDK_CONN_TYPES.DCSSDK_CONNTYPE_USB_CDC
        ) {
            sdkHandler.dcssdkEstablishCommunicationSession(scanner.scannerID)
        }
    }

    /** Call when scan mode is turned off / the owning screen goes away - releases the SDK's
     * USB/event registration. */
    fun close() {
        sdkHandler.dcssdkClose()
    }

    override fun dcssdkEventScannerAppeared(availableScanner: DCSScannerInfo?) {
        availableScanner?.let { tryConnect(it) }
    }

    override fun dcssdkEventScannerDisappeared(scannerID: Int) {}
    override fun dcssdkEventCommunicationSessionEstablished(activeScanner: DCSScannerInfo?) {}
    override fun dcssdkEventCommunicationSessionTerminated(scannerID: Int) {}

    // The one event this app actually cares about - fires with the decoded barcode text the
    // instant a scan completes. No "Enter" keystroke concept needed at all: unlike keyboard-wedge
    // input, this callback IS the completion signal, so the caller can act immediately.
    override fun dcssdkEventBarcode(barcodeData: ByteArray?, barcodeType: Int, fromScannerID: Int) {
        val decoded = barcodeData?.toString(Charsets.UTF_8)?.trim().orEmpty()
        if (decoded.isEmpty()) return
        // SDK callbacks arrive on a background thread - hop to main before touching any UI.
        mainHandler.post { onBarcodeScanned(decoded) }
    }

    override fun dcssdkEventImage(imageData: ByteArray?, fromScannerID: Int) {}
    override fun dcssdkEventVideo(videoFrame: ByteArray?, fromScannerID: Int) {}
    override fun dcssdkEventBinaryData(binaryData: ByteArray?, fromScannerID: Int) {}
    override fun dcssdkEventFirmwareUpdate(firmwareUpdateEvent: FirmwareUpdateEvent?) {}
    override fun dcssdkEventAuxScannerAppeared(auxScanner: DCSScannerInfo?, parentScanner: DCSScannerInfo?) {}
    override fun dcssdkEventConfigurationUpdate(configurationUpdateEvent: ConfigurationUpdateEvent?) {}
}
