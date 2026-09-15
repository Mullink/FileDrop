# Verification status

Verified on 2026-09-15. Updater 1.0.0 / versionCode 1 / package com.liquorbee.updater.

## Passed

- Debug and release compilation and APK assembly with Android SDK 35, AGP 8.8.1, Gradle 8.10.2, and Android Studio JDK.
- All 12 JVM unit tests.
- Debug and release lint: no issues found.
- All nine Android instrumentation tests on Android 15 / API 35 x86_64 emulator: installed POS package/version, real HTTPS metadata request, notification once per build, newer-build alert while old notification remains, persistent Later/high-watermark state, failed-check behavior, paused monitoring, persisted hourly network job, notification tap opening review, review-screen Later button, and original APK browser intent.
- UI reported Not installed before installing the original POS APK.
- Original Quantic-signed POS 1.2.1 / versionCode 20260910 installed on the emulator without modification. Package lookup returned that version.
- Downloaded the currently published POS APK once for release verification. Its SHA-256 exactly matches the supplied APK: ef380e68df7c0f2bc22cc57e5ce482c1fb426791552cbd01fa110f3891722770. Signature verification reports OU=Quantic.
- Signed updater release installed and launched. A forced JobScheduler check completed successfully while its activity was in the background.
- Persisted updater job was present after emulator reboot, before reopening the updater; a forced run subsequently worked. Reboot was slow and normal service availability was delayed.
- Dedicated RSA-3072 updater signature verified with apksigner, and ZIP alignment verified. The release is not debuggable.

## Fixes during validation

- New builds can alert even if an older build's notification remains visible.
- A stale Later action cannot lower the remembered notification build.
- Added backup-exclusion settings and removed layout/resource lint warnings.

## Remaining acceptance tests

No physical iMin was connected. The owner will perform further device testing. Natural hourly timing, prolonged Doze/OEM battery behavior, swipe-away behavior, notification denial/channel blocking, large fonts/landscape, and an actual in-place POS upgrade remain to be verified. Force-stop/relaunch commands were exercised, but the emulator's reported waiting state did not establish a complete force-stop suppression test. The download-intent test intercepts the browser launch; it does not claim a full browser download-and-install test.

Checks are approximately hourly, not exact. Android may delay them for power, connectivity, or quotas. Open the updater once after installation. After reboot it resumes through a persisted job, subject to normal boot/user-unlock availability. Force-stop suspends execution until the app is explicitly reopened; reboot persistence does not bypass it.

Private signing keys/passwords are retained outside the source and excluded from GitHub and source archives. The POS APK has never been modified or re-signed.
