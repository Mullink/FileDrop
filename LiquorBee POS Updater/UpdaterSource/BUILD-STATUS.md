# Verification status

2026-09-15: LiquorBee Updater 1.0.2 / versionCode 3 / com.liquorbee.updater.

## Current release

- Stable distribution filename: LiquorBee-Updater.apk. Overwrite it for every release.
- Supplied LiquorBee-Updater.png used unchanged for the launcher icon and header. Shared Labels/Receiving/Wholesale navy palette retained.
- Added optional daily automatic review opening while a newer POS build remains available. Opens at most once every 24 hours, only while awake/unlocked and with Android's background-launch permission. Later closes the screen and defers opening for 24 hours.
- Failed or silently blocked launch attempts do not consume the daily allowance. Confirmation requires the resumed activity to return a private request token. A 15-minute retry cooldown prevents rapid attempts.

## Validation

- Clean debug/release compilation and assembly passed with AGP 8.8.1, Gradle 8.10.2, SDK 35, Android Studio JDK.
- All 20 JVM unit tests passed, including daily rate limiting across releases, Later, disabled modes, missing/current/invalid versions, blocked-attempt retries and backward clock changes.
- Debug/release lint passed: zero errors, two cosmetic launcher warnings (obsolete empty v26 folder and no optional monochrome icon).
- All 11 instrumentation tests passed on the signed release APK on Android 15 / API 35 x86_64 emulator. Tests covered original POS package/version detection, live HTTPS fetching, one notification per newer build, fresh-release alerting, persisted scheduling, Later, notification tap, original download intent, persisted attempt/confirmation state and automatic daily opening with the special permission granted. The second automatic attempt for an even newer build was suppressed by the same daily limit.
- Release 1.0.2 installed over 1.0.1 with the same private signer. apksigner verification passed; certificate SHA-256: b2b601fc07882e8aaa11849546b5fd625175ee5571bc0a297d039d7ee610d44c.
- Tests use synthetic future releases only inside the emulator test process. They never publish fictitious POS builds or modify/re-sign the POS APK.

## POS and earlier scheduling verification

The currently served Quantic-signed POS APK was independently downloaded once and matched the supplied original: package com.liquorbee.liquorbeepos, version 1.2.1, versionCode 20260910. SHA-256: ef380e68df7c0f2bc22cc57e5ce482c1fb426791552cbd01fa110f3891722770. The published LiquorBeePOS/version.txt was corrected to 20260910. Scheduled checks fetch only that small version file.

Earlier release testing verified Not installed before POS installation, successful background job completion with the activity closed, and the persisted job present after reboot. Force-stop/relaunch commands were exercised, but a full force-stop suppression test remains.

## Remaining device acceptance

No physical iMin was connected. Verify the Display over other apps setup, OEM/kiosk background-launch behavior, screen-off/locked behavior, natural 24-hour timing, extended Doze, permission revocation/channel blocking, swipe-away, large fonts/landscape and an actual in-place POS upgrade on the iMin. Instrumentation launch tests do not establish every OEM background policy. The download test intercepts the browser intent; it does not claim a complete browser download/install flow.

Android can delay hourly work. Reboot persistence does not override force-stop: the operator must reopen a force-stopped updater. Automatic review may interrupt an active sale; the operator still chooses whether to install.

Signing keys/passwords are retained privately and excluded from source archives and GitHub. No off-device key backup was made.
