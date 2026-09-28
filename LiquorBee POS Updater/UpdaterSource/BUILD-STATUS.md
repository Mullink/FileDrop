# Verification status

2026-09-28: LiquorBee Updater 1.0.8 / versionCode 9 / com.liquorbee.updater.

## Prominent YouTube tutorial link

The top tutorial control now uses a red background, white play icon, bold Watch on YouTube label, and a setup tutorial/tap-to-watch caption. The existing YouTube URL and click handler are unchanged.

Release compilation, release lint, APK signature verification, build metadata verification and installation on an Android 15 emulator passed. The actual emulator screen was visually checked for readable button text, icon and caption. No new automated tests were added for this styling-only change. POS update behavior is unchanged.

## Previous release verification (1.0.7 / build 8)

2026-09-28: LiquorBee Updater 1.0.7 / versionCode 8 / com.liquorbee.updater.

## Tutorial button release

Watch setup tutorial appears above the logo at the top of the screen and opens the user-provided YouTube URL: https://www.youtube.com/watch?v=bq4rl7faP2k. It uses the existing external-link handler. POS and updater version-check logic are unchanged.

Release compilation, release lint, APK signature verification and manifest/build metadata checks passed. This small UI-link change did not rerun the prior release's emulator suite. The signing certificate remains b2b601fc07882e8aaa11849546b5fd625175ee5571bc0a297d039d7ee610d44c. Physical register acceptance remains with the operator.

## Previous release verification (1.0.6 / build 7)

2026-09-28: LiquorBee Updater 1.0.6 / versionCode 7 / com.liquorbee.updater.

## Self-update release

The Updater checks its own GitHub version metadata when the screen opens/resumes and every five minutes while visible. A dedicated card supports manual checks and downloads. Later persists separately for each updater release; a manual check can reopen the prompt. Browser download is not treated as installation: the app rereads its installed package version on return.

The existing POS download URL, POS version metadata, daily schedule, permissions, notifications and daily dismissal behavior are unchanged. The POS metadata remains 20260910. Self-update preferences are separate from POS preferences.

## Release validation

- 48 JVM tests passed, with zero failures/errors.
- Debug/release lint and release compilation passed.
- All 17 existing POS instrumentation tests passed on the Android 15 / API 35 emulator.
- All 3 new self-update instrumentation tests passed after correcting test timing for Android's asynchronous dialog callbacks. They cover persistent Later/manual recheck, the exact updater download URL, unchanged installed version until installation, preserved POS scheduling/dismissal settings, and explicit network failure status. Existing POS checks may refresh their cached results normally on activity resume.
- Installed the original build 6 and then upgraded it in place to the signed build 7 successfully.
- APK signature verification passed using the original updater certificate. SHA-256: b2b601fc07882e8aaa11849546b5fd625175ee5571bc0a297d039d7ee610d44c.
- Release APK manifest and version.txt both report build 7; display version is 1.0.6.

No physical iMin was connected for this release. Install this APK once over the existing updater on a register and verify normal POS use and the new Updater app card. Download and installation remain user-controlled by the browser and Android.

Signing keys/passwords remain private and excluded from the source archive and GitHub.

## Previous release verification (1.0.5 / build 6)
2026-09-15: LiquorBee Updater 1.0.5 / versionCode 6 / com.liquorbee.updater.

## Permission setup and scheduled-check evidence

The operator confirmed that enabling Allow automatic opening resolved the missed opening on the register with version 1.0.4. Version 1.0.5 adds a first-launch setup prompt to explain and open the missing Android permission settings. Existing installations also receive the prompt on their next launch after this upgrade if a required permission is missing. The operator can choose Not now and use the existing permission buttons later.

The app now retains a separate Last scheduled check result with the Chicago timestamp, installed/published build numbers when available, and a reason for opening or skipping it. Opening the app, running Check now and saving a new daily time do not erase this record. The result distinguishes current/missing/failed version checks, disabled opening, missing Display over other apps permission, sleeping/locked screen, daily dismissal/cooldown, Android rejecting the launch, an unconfirmed opening request and a focused review screen. Service-start rejection and timeout are also recorded.

A successful automatic opening now requires window focus with the private request token. Activity resume alone is no longer considered proof that the screen appeared. Delayed results cannot overwrite a confirmed opening; tokens from an older alarm cannot consume a newer day's reminder. No diagnostic data is uploaded.

Older installed versions did not save the opening outcome. When available, their scheduled-check timestamp is shown as a legacy record, without inventing a result. If no scheduled check was recorded, the alarm has not reached the claimed-check stage.

## Validation

- Debug/release compilation, release assembly and both lint variants passed with AGP 8.8.1, Gradle 8.10.2, SDK 35 and Android Studio JDK.
- All 35 JVM tests passed, including same-day time changes, the 4:01 PM regression cases, daily limits, delayed deliveries and daylight saving changes.
- All 17 instrumentation tests passed on an Android 15 / API 35 x86_64 emulator; see DEVICE-TEST-RESULTS.txt in the local deliverables for the final release run.
- First-launch UI verification confirmed the setup explanation, navigation to Display over other apps, and continuation to Alarms & reminders after returning. Permission grants remain under Android's control.
- Device tests cover actual exact-alarm execution and persisted current-build outcome, original POS package/version, live HTTPS metadata, notifications, focused automatic opening, notification tap, Later, download intent, schedule rearming, outcome persistence across manual checks/time changes, delayed confirmation ordering and rejection of old confirmations.
- Same-signer update installation and apksigner verification passed. Certificate SHA-256: b2b601fc07882e8aaa11849546b5fd625175ee5571bc0a297d039d7ee610d44c.
- Lint: zero errors; two unchanged cosmetic icon warnings (empty v26 folder and optional monochrome icon).

## Register acceptance

Install the latest APK and review both permission controls. If Allow scheduled checks or Allow automatic opening remains visible, the corresponding Android permission is missing. Save a time two or three minutes ahead and confirm today's date. Return to POS using Home and keep the register awake/unlocked. Later deliberately dismisses today's opening. If opening fails, read the separate Last scheduled check result after the scheduled time.

The operator confirmed automatic opening after granting the missing permission. No physical iMin is connected to this development environment; the new guided setup still needs operator acceptance on the register. Natural daily timing, restart/force-stop recovery and a complete POS download/install remain broader acceptance work. The app cannot identify an exact OEM restriction from a silently blocked launch. Android can delay or stop background work; a force-stopped updater must be reopened.

## Existing behavior and release integrity

Default daily time remains 10:30 AM America/Chicago, following daylight saving time. The chosen time is configurable. Saving a time explicitly rearms its next occurrence, including today; normal repeats run once per day. Checks use only the small POS version file. The scheduled service is brief and bounded by a 90-second timeout. No app-usage monitor or continuously running service is used.

Stable filename LiquorBee-Updater.apk is overwritten, with the internal versionCode increased. Logo, navy palette and original download URL are retained. The Quantic-signed POS is unchanged: com.liquorbee.liquorbeepos, version 1.2.1/build 20260910, SHA-256 ef380e68df7c0f2bc22cc57e5ce482c1fb426791552cbd01fa110f3891722770. Tests never modify POS data or public release metadata. No POS re-signing, periodic full-APK download or silent installation.

Signing keys/passwords remain private and excluded from source archives/GitHub. No off-device signing-key backup was made.
