package com.liquorbee.invoicescanner.capture

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * Enumerates every USB device Android currently sees attached in host mode, with enough detail
 * (VID/PID/interface count) to identify the Brother DS-640 - or diagnose why it ISN'T being seen
 * (wrong cable/port, needs a powered OTG hub, wrong VID assumption, etc.) - directly on the real
 * iMin Swan 1 Pro hardware, since none of this can be tested against real USB hardware in this
 * sandboxed dev environment. Logs to Logcat AND returns a display string, since the POS terminal
 * this ships to may not have easy Logcat access in the field.
 */
object UsbDiagnostics {

    private const val TAG = "USB"

    fun describeAttachedDevices(context: Context): String {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = usbManager.deviceList.values

        if (devices.isEmpty()) {
            Log.d(TAG, "No USB devices currently attached.")
            return "No USB devices currently attached.\n\nIf the Brother DS-640 is plugged in, check the cable/port and that this device supports USB host mode (OTG)."
        }

        val lines = StringBuilder("Attached USB devices (${devices.size}):\n\n")
        for (device in devices) {
            val line = "Device=${device.deviceName}, " +
                "VID=0x${device.vendorId.toString(16).uppercase()} (${device.vendorId}), " +
                "PID=0x${device.productId.toString(16).uppercase()} (${device.productId}), " +
                "name=${device.productName ?: "?"}, " +
                "manufacturer=${device.manufacturerName ?: "?"}, " +
                "interfaces=${device.interfaceCount}"
            Log.d(TAG, line)
            lines.append(line).append("\n\n")

            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                val ifaceLine = "  Interface $i: class=${iface.interfaceClass}, subclass=${iface.interfaceSubclass}, " +
                    "protocol=${iface.interfaceProtocol}, endpoints=${iface.endpointCount}"
                Log.d(TAG, ifaceLine)
                lines.append(ifaceLine).append("\n")
            }
            lines.append("\n")
        }

        val brotherHit = devices.any { it.vendorId == 0x04F9 }
        lines.append(if (brotherHit) "-> A Brother-vendor-ID (0x04F9) device IS present above." else "-> No device with Brother's vendor ID (0x04F9) found.")

        return lines.toString()
    }
}
