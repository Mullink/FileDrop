# Brother DS-640 image framing

Receiving 2.25 could feed the complete invoice and then report "Scanner image band is too large." That message came from rejecting any nonzero byte at offsets 10–13 of a 14-byte image-band header. It did not mean the 64 MiB image limit had been reached.

The installed Brother Windows driver `BsScanCoreApi.dll`, x86 image base `0x10000000`, confirms two independent little-endian 32-bit fields in `bscore::CParseExeCmdRes_v2::ParseXSCcmdImageDataInfo`:

- `0x100dae8e`: first field is read into the parsed structure at `+0x14`.
- `0x100dae9f`: second field is read separately into `+0x18`.
- `0x100daeb4` through `0x100daec7`: only the first field determines payload length and advances the input cursor.

For the observed block record `00 02 01 00 15 00`, the first field starts at wire offset 6 and the second at offset 10. Normal bands observed in the store contain `00 00 08 00` (524288 payload bytes) followed by four zero metadata bytes. The zeros are not a guaranteed part of the byte count. The application does not need to interpret the second field to retain the JPEG correctly.

Version 2.26 reads the payload length as an unsigned 32-bit value, logs the second field independently, and still bounds actual image bytes to 64 MiB. Empty bands add no image bytes and cannot make an incomplete JPEG valid. JPEG completion checks and USB draining remain in place.

Regression coverage includes a multi-megabyte decodable JPEG delivered in 524288-byte bands with two 262144-byte USB reads, nonzero final-band metadata, split/coalesced headers, all four metadata bytes including high bits, empty metadata bands, incomplete JPEGs, invalid payload lengths, and the full USB read loop. Tests use synthetic streams; a store scan remains the physical-device verification.
