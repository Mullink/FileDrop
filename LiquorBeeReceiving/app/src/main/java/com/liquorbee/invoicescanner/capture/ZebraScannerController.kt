package com.liquorbee.invoicescanner.capture

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
 *
 * Why this exists: the store's scanners are configured per ScannerSetupGuide.docx.pdf to run in
 * "Symbol Native API (SNAPI)" mode, not USB keyboard-wedge mode - switching them to keyboard mode
 * was the workaround being used before this, and the store doesn't want to keep doing that. SNAPI
 * mode delivers decoded barcodes through this SDK's callback interface instead of typed keystrokes.
 *
 * The SDK/AAR itself was identified by decompiling the sibling "Liquor Bee POS" app's own APK
 * (which already scans successfully against this exact hardware) - its bundled classes reference
 * com.zebra.scannercontrol.SDKHandler, confirming this specific SDK rather than DataWedge or an
 * OEM-proprietary intent scheme. The AAR is pulled directly from Zebra's own MIT-licensed
 * https://github.com/ZebraDevs/Scanner-SDK-for-Android (checked into that repo as a prebuilt
 * release artifact) - see app/build.gradle.kts.
 *
 * The "Allow POS to access Symbol Bar Code Scanner::EA?" prompt from the setup guide is Android's
 * own standard USB-device runtime permission dialog (not a custom app permission) - the scanner's
 * own USB descriptor self-reports as "Symbol Bar Code Scanner", and the SDK requests access to it
 * via the normal UsbManager flow the first time it's used. No manifest permission declaration is
 * needed beyond the existing android.hardware.usb.host feature (already declared for the Brother
 * scanner - see BrotherUsbCaptureSource).
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

    /** Call from the owning Activity's onDestroy - releases the SDK's USB/event registration. */
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
