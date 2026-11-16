package com.liquorbee.invoicescanner.capture

import org.junit.Assert.*
import org.junit.Test

class BrotherScanCommandsTest {
    private fun fields(bytes: ByteArray, name: String): Map<String, String> {
        assertEquals(0x1b.toByte(), bytes.first())
        assertEquals(0x80.toByte(), bytes.last())
        val text = bytes.copyOfRange(1, bytes.size - 1).toString(Charsets.US_ASCII)
        assertTrue(text.startsWith("$name\n")); assertTrue(text.endsWith("\n"))
        return text.trimEnd('\n').lines().drop(1).associate {
            val parts = it.split('=', limit = 2); parts[0] to parts[1]
        }
    }

    @Test fun longPaperUsesExplicitFullLengthWindowInsteadOfAutomaticDeskew() {
        val setup = fields(BrotherScanCommands.setup(), "SSP")
        val execute = fields(BrotherScanCommands.execute(), "XSC")
        assertEquals("ON", setup["LONG"])
        assertEquals("OFF", setup["DPLX"])
        assertEquals("OFF", setup["DSKW"])
        assertEquals("NORMAL", setup["AREA"])
        assertEquals("NORMAL", execute["MODE"])
        assertEquals(setup["RESO"], execute["RESO"])
        val dpi = execute.getValue("RESO").split(',').map { it.toInt() }
        val window = execute.getValue("AREA").split(',').map { it.toInt() }
        assertEquals(listOf(0, 0), window.take(2))
        assertEquals(8.5, window[2].toDouble() / dpi[0], 0.0)
        assertEquals(72.0, window[3].toDouble() / dpi[1], 0.0)
        assertFalse(setup.values.contains("ATDSKW"))
        assertFalse(execute.values.contains("ATDSKW"))
    }

    @Test fun recognizesTheSetupReplyShownInTheStoreLog() {
        val reply = byteArrayOf(0, 0x53, 0x53, 0x50, 0x1e, 0, 0, 0, 1, 0x20, 2, 0, 1, 0, 2, 0x20)
        BrotherScanCommands.validateSetupReply(reply, reply.size)
    }

    @Test fun rejectedSettingsCannotSilentlyProceedToStartingTheFeedMotor() {
        assertThrows(IllegalStateException::class.java) {
            BrotherScanCommands.validateSetupReply(byteArrayOf(0xb0.toByte(), 0x53, 0x53, 0x50), 4)
        }
        assertThrows(IllegalStateException::class.java) {
            BrotherScanCommands.validateSetupReply(byteArrayOf(0, 2, 1, 0), 4)
        }
        assertThrows(IllegalStateException::class.java) {
            BrotherScanCommands.validateSetupReply(byteArrayOf(0, 0x53, 0x53, 0x50), 2)
        }
    }
}
