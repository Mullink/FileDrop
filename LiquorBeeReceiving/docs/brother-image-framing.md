# Brother DS-640 image framing

Receiving 2.25 could feed the complete invoice and then report "Scanner image band is too large." That message came from rejecting any nonzero byte at offsets 10–13 of a 14-byte image-band header. It did not mean the 64 MiB image limit had been reached.

The installed Brother Windows driver `BsScanCoreApi.dll`, x86 image base `0x10000000`, confirms two independent little-endian 32-bit fields in `bscore::CParseExeCmdRes_v2::ParseXSCcmdImageDataInfo`:

- `0x100dae8e`: first field is read into the parsed structure at `+0x14`.
- `0x100dae9f`: second field is read separately into `+0x18`.
- `0x100daeb4` through `0x100daec7`: only the first field determines payload length and advances the input cursor.

For the observed block record `00 02 01 00 15 00`, the first field starts at wire offset 6 and the second at offset 10. Normal bands observed in the store contain `00 00 08 00` (524288 payload bytes) followed by four zero metadata bytes. The zeros are not a guaranteed part of the byte count. The application does not need to interpret the second field to retain the JPEG correctly.

Version 2.26 reads the payload length as an unsigned 32-bit value, logs the second field independently, and still bounds actual image bytes to 64 MiB. Empty bands add no image bytes and cannot make an incomplete JPEG valid. JPEG completion checks and USB draining remain in place.

Regression coverage includes a multi-megabyte decodable JPEG delivered in 524288-byte bands with two 262144-byte USB reads, nonzero final-band metadata, split/coalesced headers, all four metadata bytes including high bits, empty metadata bands, incomplete JPEGs, invalid payload lengths, and the full USB read loop. Tests use synthetic streams; a store scan remains the physical-device verification.

## 2.27: loading the completed long scan

The store subsequently reported Android closing the app after the paper finished, while the image loaded before submission. The screenshot alone does not identify the exception. The previous path decoded every pixel and bound the same full bitmap to the thumbnail. A 2550 by 21600 ARGB bitmap allocates 220,320,000 bytes.

The Android regression test reproduces failure of that old path on the emulator's allocation/rendering limit. The corrected decoder uses bounds first, a power-of-two sample, at most 12 million pixels, and at most 8192 pixels on either axis. The 72-inch fixture becomes 638 by 5400 (under 16 MiB), with a separate 320-pixel thumbnail. Ordinary 2550 by 3300 scans retain their resolution. This follows Android's [large bitmap guidance](https://developer.android.com/topic/performance/graphics/load-bitmap).

Image processing runs off the UI thread. Unannotated pages no longer require a full bitmap copy; owned encoding intermediates are released. Memory failures produce a scan error, and unexpected Java crashes leave a bounded local report shown on the next launch. Reports are not sent automatically. Scan and submission cannot overlap.

Validation: 42 JVM tests and three Android tests passed. The Android tests exercise the old failure, corrected hardware-canvas rendering and thumbnail binding, footer retention after JPEG upload preparation, and local crash recording. Physical confirmation on the store's iMin remains necessary.

## 2.28: full-page thumbnail

The store confirmed the page was visible in Draw but the small thumbnail appeared blank. The thumbnail ImageView still used `centerCrop`, which enlarges a narrow receipt until it fills a conventional page-shaped frame and hides both ends. A blank middle section could fill the entire thumbnail. The ImageView now uses `fitCenter` to show the whole page. The decoder and stored scan image are unchanged. The Android regression renders the actual thumbnail view and checks that both header and footer are visible; switching that same fixture back to `centerCrop` reproduces the blank preview.

## 2.29: discard empty scan length before reducing resolution

Review of customer 295 uploads 473 and 474 found the same physical invoice scanned twice into one batch. Both saved JPEGs are 483 by 4200 pixels; paper occupies about half the width and the top quarter, with gray scanner bed beside it and a black suffix below it. Scaling the entire manual 72-inch scan window made the invoice text unnecessarily small. The missing invoice number was accompanied by missing SKUs and unreconciled extracted amounts, so inserting an invoice number alone would not repair this batch.

The scanner decoder now inspects a bounded 1536-pixel preview to locate a substantial contiguous black suffix, retains a safety margin at the edge, and decodes the retained region from the original JPEG. It chooses the memory-safe sample size using that region instead of the unused scan window. It leaves white paper, gray margins, internal black gaps and all-black scans alone. The 12-million-pixel and 8192-edge allocation limits remain unchanged, as do USB feed and JPEG-completion checks. Existing uploaded images have already lost detail and need a fresh scan; no batch values are inferred or changed by this fix.

The synthetic padded-invoice fixture models a 2550 by 21600 scan with 5500 rows of paper/bed. Tests require a 1275-pixel-wide final upload (over 2.6 times the old uploaded width), intact header/footer, a retained gray margin, bounded allocation, and unchanged full-length/non-padding scans. No store invoice images are committed as test assets.
