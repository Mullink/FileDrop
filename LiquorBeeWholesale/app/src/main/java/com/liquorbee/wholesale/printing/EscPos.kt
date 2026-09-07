package com.liquorbee.wholesale.printing

import java.io.ByteArrayOutputStream

/** Minimal ESC/POS command builder - the standard raw-text protocol both the SNBC BTP-R180II and
 * the Lyntek ACE H2 (and virtually every USB thermal receipt printer) speak. No codepage
 * switching - plain US-ASCII/Latin-1 covers every character an invoice actually needs (names,
 * digits, "$", basic punctuation), and skipping it avoids a whole class of mojibake bugs on
 * printers with different default codepages. */
class EscPos {
    private val out = ByteArrayOutputStream()

    fun init(): EscPos = apply { out.write(byteArrayOf(0x1B, 0x40)) } // ESC @

    fun align(mode: Align): EscPos = apply { out.write(byteArrayOf(0x1B, 0x61, mode.value)) } // ESC a n

    fun bold(on: Boolean): EscPos = apply { out.write(byteArrayOf(0x1B, 0x45, if (on) 1 else 0)) } // ESC E n

    // GS ! n - bit 0-3 width multiplier, bit 4-7 height multiplier (0x11 = double both).
    fun doubleSize(on: Boolean): EscPos = apply { out.write(byteArrayOf(0x1D, 0x21, if (on) 0x11 else 0x00)) }

    fun text(s: String): EscPos = apply { out.write(s.toByteArray(Charsets.US_ASCII)) }

    fun line(s: String = ""): EscPos = apply { text(s); out.write('\n'.code) }

    // A run of a repeated char across the receipt width - used for section dividers.
    fun divider(width: Int, char: Char = '-'): EscPos = line(char.toString().repeat(width))

    fun feed(lines: Int = 1): EscPos = apply { repeat(lines) { out.write('\n'.code) } }

    // GS V m - 0 = full cut, 1 = partial cut. Most receipt printers (including both target
    // models) only physically support partial cut even when full-cut is requested.
    fun cut(): EscPos = apply { out.write(byteArrayOf(0x1D, 0x56, 0x01)) }

    fun build(): ByteArray = out.toByteArray()

    enum class Align(val value: Byte) { LEFT(0), CENTER(1), RIGHT(2) }
}
