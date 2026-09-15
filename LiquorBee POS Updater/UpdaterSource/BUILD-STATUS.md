# Verification status

2026-09-15: LiquorBee Updater 1.0.4 / versionCode 5 / com.liquorbee.updater.

## Same-day schedule correction

Saving the daily time explicitly rearms its next occurrence. A future time runs today even if an earlier scheduled check already ran or an earlier review was dismissed. Saving within the selected minute checks shortly instead of skipping to tomorrow. Times already past that minute use tomorrow. Automatic repeats still run once per Chicago calendar day; explicit time changes are operator overrides.

The new schedule clears the earlier check/dismissal/attempt state. Old launch tokens are invalidated, stale alarm deliveries cannot claim a newly edited schedule, and an old network result cannot launch a review after its schedule is replaced. A pending alarm delayed by up to 15 minutes is preserved when returning to the app, instead of being overwritten with tomorrow's alarm. Older missed alarms use the next future time.

The default remains 10:30 AM America/Chicago, following daylight saving time. The chosen time and next check appear in the updater. Alarms & reminders is required on Android 12+; Display over other apps is required for automatic opening on Android 10+. The register must be awake and unlocked to show the review. Current, missing or failed POS checks never auto-open.

## Validation

- Release compilation/assembly and both lint variants passed with AGP 8.8.1, Gradle 8.10.2, SDK 35 and Android Studio JDK.
- All 35 JVM tests passed, including the reported 4:01 PM case after an earlier check, saving during the current minute, past-minute behavior, delivery-time reconciliation, delayed alarms, normal daily limits and both daylight saving transitions.
- All 14 instrumentation tests passed in one run on the final signed release APK on Android 15 / API 35 x86_64 emulator (32.094 seconds, zero failures). Coverage includes rearming after today's check and dismissal, persistence of the new time, obsolete token rejection, original POS package lookup, live HTTPS metadata, actual exact-alarm background execution, next-day scheduling, automatic review opening, notification tap, notification suppression, Later and the original browser download intent.
- Final release installed over the prior updater using the retained private signer. apksigner verification passed. Certificate SHA-256: b2b601fc07882e8aaa11849546b5fd625175ee5571bc0a297d039d7ee610d44c.
- Lint: zero errors; two unchanged cosmetic icon warnings (empty v26 folder and optional monochrome icon).

## Register testing

Install the latest stable LiquorBee-Updater.apk, open it and save a time two or three minutes ahead. Confirm Next daily check shows today's date, then use Home to return to the POS. The updater opens only if the POS is out of date, automatic opening is enabled, required permissions are allowed and the register is awake/unlocked. Later deliberately dismisses automatic opening for today; saving another time rearms it.

No physical iMin is connected. Verify the corrected schedule, actual outdated-POS opening, OEM/kiosk restrictions, restart, force-stop/relaunch and a real POS upgrade on the register. Emulator tests do not establish every device policy. Force-stop requires reopening the updater. The scheduled check requires network access and Android can delay or block execution.

## Release integrity

Stable filename LiquorBee-Updater.apk is overwritten, with internal versionCode increased. Logo, navy branding, default Central timezone and original POS download URL are retained. No app-usage monitor or continuously running service is used; the scheduled check's brief foreground service has a 90-second timeout.

The Quantic-signed POS is unchanged: com.liquorbee.liquorbeepos, version 1.2.1/build 20260910, SHA-256 ef380e68df7c0f2bc22cc57e5ce482c1fb426791552cbd01fa110f3891722770. Only the small published POS version file is fetched during checks. Tests never modify POS data or public release metadata. No POS re-signing, periodic full-APK download or silent installation.

Signing keys/passwords remain private and excluded from source archives/GitHub. No off-device signing-key backup was made.
