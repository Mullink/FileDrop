package com.liquorbee.invoicescanner.capture

/**
 * DS-640 single-sided long-paper scan, 8.5 x 72 inches at 300 dpi.
 *
 * The installed Brother BsScanCoreApi.dll's MakeSSPcmdString / MakeXSCcmdString
 * use different AREA encodings: SSP selects NORMAL for a numeric scan window;
 * XSC sends left, top, right, bottom in scan pixels. ATDSKW selects automatic
 * deskew instead and does not send the requested long-paper rectangle.
 * LONG=ON alone did not resolve the store's long-invoice feed stop.
 * These commands still require verification on the iMin/DS-640 combination.
 */
internal object BrotherScanCommands {
    const val DPI = 300
    const val WIDTH_PIXELS = 2550
    const val HEIGHT_PIXELS = 21600

    fun setup(): ByteArray = command("SSP", listOf(
        "OS=WIN", // Preserve the command dialect from the working DS-640 USB capture.
        "PSRC=AUTO",
        "RESO=$DPI,$DPI",
        "CLR=C24BIT",
        "AREA=NORMAL",
        "MRGN=0,0,0,0",
        "DPLX=OFF",
        "BRIT=50",
        "CONT=50",
        "COMP=JPEG",
        "JSF=420",
        "IPRC=NORMAL",
        "PTYPE=NORMAL",
        "PAGE=0",
        "LONG=ON",
        "CARR=OFF",
        "RMGC=OFF",
        "DTDF=OFF",
        "DT4V=OFF",
        "DSKW=OFF",
        "LSMD=OFF",
        "RMBP=OFF",
        "RMMR=OFF",
        "GMMA=OFF",
        "TONE=OFF",
        "QTFD=OFF",
        "ATCN=OFF",
        "ATCRP=OFF",
        "USER="
    ))

    fun execute(): ByteArray = command("XSC", listOf(
        "RESO=$DPI,$DPI",
        "AREA=0,0,$WIDTH_PIXELS,$HEIGHT_PIXELS",
        "MODE=NORMAL"
    ))

    fun validateSetupReply(bytes: ByteArray, count: Int) {
        require(count in 0..bytes.size)
        val prefix = bytes.take(count.coerceAtMost(16)).joinToString(" ") { "%02X".format(it) }
        check(count >= 4 && bytes[1] == 'S'.code.toByte() &&
            bytes[2] == 'S'.code.toByte() && bytes[3] == 'P'.code.toByte()) {
            "The scanner did not acknowledge its scan settings ($prefix). Disconnect and reconnect the scanner, then retry."
        }
        check(bytes[0] == 0.toByte()) {
            "The scanner rejected its scan settings (status %02X). Clear the stopped paper and reconnect the scanner before retrying."
                .format(bytes[0])
        }
    }

    private fun command(name: String, fields: List<String>): ByteArray =
        ("\u001b$name\n" + fields.joinToString("\n", postfix = "\n")).toByteArray(Charsets.US_ASCII) + 0x80.toByte()
}
