# Verification status

2026-09-15: LiquorBee Updater 1.0.3 / versionCode 4 / com.liquorbee.updater.

## Current release

- Stable distribution filename: LiquorBee-Updater.apk, overwritten each release.
- Daily scheduled check defaults to 10:30 AM America/Chicago and follows daylight saving time. The time is configurable in the app; the next scheduled check is displayed.
- Replaces the legacy hourly job and rolling 24-hour opening allowance. Scheduled checks and automatic reviews run at most once per Chicago calendar day. Editing the time after today's check applies tomorrow.
- Checks the real POS metadata at the scheduled time and opens the review only for an installed, out-of-date POS when automatic opening is enabled, the register is awake/unlocked and Android permits opening. Current/missing/failed checks do not auto-open.
- Alarms & reminders permission is required on Android 12+. Display over other apps is required for background opening on Android 10+. Setup buttons and explanations are in the app.
- Restores the daily alarm after reboot, package replacement, clock/time-zone changes and alarm permission grants. The short foreground check stops after completion, with a 90-second timeout. No app-usage monitor or continuously running service.
- Existing logo, navy branding, stable URLs, preferences and private signing identity retained. POS APK and public POS build metadata unchanged.

## Completed verification

- Clean debug/release compilation, release assembly and instrumentation compilation passed with AGP 8.8.1, Gradle 8.10.2, SDK 35 and Android Studio JDK.
- All 30 JVM unit tests passed. Coverage includes version parsing/comparison, failures, notification state, daily eligibility, schedule edits, duplicate-day prevention, winter/summer offsets, spring's 23-hour day, autumn's 25-hour day, missing/repeated DST times and invalid hours.
- Debug/release lint: zero errors; two cosmetic icon warnings (empty v26 folder and optional monochrome icon).
- Signed release installed over 1.0.2 on Android 15 / API 35 x86_64 emulator. apksigner verification passed. Certificate SHA-256: b2b601fc07882e8aaa11849546b5fd625175ee5571bc0a297d039d7ee610d44c.
- The first 13-test instrumentation run has two unresolved activity-opening assertions (automatic review and notification tap) in the slow emulator. They are not claimed as passing; investigation continues after check-in. The preceding 1.0.2 release passed its 11-test suite, which does not establish this release's new behavior.

## Device acceptance

No physical iMin is connected. Verify the selected daily time, both Android setup permissions, actual out-of-date automatic opening, current/offline behavior, reboot, force-stop/relaunch and kiosk restrictions on the register. Confirm natural daily timing and a real POS download/install. Synthetic future builds are confined to tests, never public version.txt. An instrumentation run does not establish every OEM background policy.

The scheduled check depends on network access. Android/device restrictions can delay or block it. The register must be awake and unlocked for a review screen; update notifications remain a fallback when allowed. If powered off at the scheduled time, the next future scheduled time is used after restart. Force-stop requires opening the updater again.

The original Quantic-signed POS remains package com.liquorbee.liquorbeepos, version 1.2.1/build 20260910, SHA-256 ef380e68df7c0f2bc22cc57e5ce482c1fb426791552cbd01fa110f3891722770. Checks read only its small version file. No POS changes, re-signing, full-APK background downloads or silent installs.

Signing keys/passwords remain private and excluded from source archives/GitHub. No off-device signing-key backup was made.
