# LiquorBee Updater

A separate Android app for checking and downloading the original LiquorBee POS iMin APK.

## Daily check

The default is **10:30 AM Central Time (America/Chicago)**, following daylight saving time automatically. In the updater, tap **Daily check: 10:30 AM · Change** to choose another time.

At that time, the app fetches the small POS version file, reads the installed POS build, and opens the updater only when the installed POS is out of date and automatic opening is enabled and permitted. Current, missing or unverifiable POS versions do not cause automatic opening. A failed check displays an error when reviewed; it never claims the POS is current.

Automatic repeats run once per Chicago calendar day. Saving a time explicitly rearms its next occurrence, including today even if an earlier check ran or was dismissed. Saving the current minute checks shortly; a time already past that minute schedules tomorrow. The next check is displayed in the app. Manual **Check now** and opening the updater still perform a check. **Later** closes the review and dismisses automatic opening for today; tomorrow's scheduled check stays in place. Newer builds alone cannot cause another automatic opening on the same day. An explicit time save clears the earlier dismissal for the newly chosen schedule.

This release replaces the old hourly job and rolling 24-hour popup with a daily scheduled alarm. No app-opening monitor or Usage access permission is used.

## One-time setup

1. Install `LiquorBee-Updater.apk` and open it once. Keep **Check for updates daily** and **Open updater when an update is available** enabled.
2. On Android 12+, tap **Allow scheduled checks** and allow **Alarms & reminders**. Return to the updater. Without this permission, the daily alarm is not scheduled; Check now still works.
3. On Android 10+, tap **Allow automatic opening**, select LiquorBee Updater if Android displays an app list, and allow **Display over other apps**. Return to the updater. Notification permission alone does not permit opening over the POS.
4. Allow notifications as a fallback, and confirm the displayed next check time.

The register must be awake and unlocked for the review screen to appear. A sleeping or locked register can still receive a scheduled version check and update notification; the app does not unlock it. OEM/kiosk policies can block background opening even with permission. An active sale may be interrupted, so choose a suitable time for the store.

The daily alarm is restored after reboot, app replacement, clock changes, and alarm-permission grants. A pending check delayed by up to 15 minutes can still run on recovery. Older missed alarms use the next future daily time. Force-stop suspends the updater and cancels alarms until it is explicitly opened again. Android may delay work under device restrictions; precise display timing also depends on network access.

A brief foreground-service notification is shown only while the scheduled check runs. The service stops after the request and has a 90-second timeout. No continuously running service, accessibility service, overlay window, full-screen notification, silent installer or POS transaction access is used.

## POS release contract

- Installed package: `com.liquorbee.liquorbeepos`.
- Metadata: [LiquorBeePOS/version.txt](https://raw.githubusercontent.com/Mullink/FileDrop/main/LiquorBeePOS/version.txt).
- Download: [original Quantic-signed iMin APK](https://portal.liquorbee.com/download/liquorbeepos/imin.apk).

`LiquorBeePOS/version.txt` must contain the offered POS APK's integer Android `versionCode`, not the updater's version or the human-readable version name. The inspected POS is version 1.2.1, build 20260910. Publish the actual signed POS APK first, then its matching version file. Never publish fictitious future builds for tests.

Scheduled checks fetch only the small version file. The browser downloads the full POS APK only when the operator taps Download. Android handles installation. The updater does not modify or re-sign the POS, and rechecks its installed build after returning; starting a download is not a successful installation.

Inspect a POS release from this source directory with:

```powershell
.\tools\Update-PosVersion.ps1 -ApkPath 'C:\Downloads\imin.apk' -VersionFile '..\..\LiquorBeePOS\version.txt'
# Or independently download and inspect the published POS release:
.\tools\Update-PosVersion.ps1 -DownloadLatest -VersionFile '..\..\LiquorBeePOS\version.txt'
```

The helper checks the package and refuses to lower a valid build. It does not commit or upload files. DownloadLatest fetches the full POS APK once; it is a release-maintenance tool, not a terminal background check. Use `-AndroidSdk` when needed. Omitting VersionFile inspects without writing.

## Build and publish

Android 9/API 28 minimum; compile/target SDK 35. Requires JDK 17+, Android SDK 35 and build tools. Android Studio's bundled JDK is suitable. Configure `ANDROID_HOME` or an untracked `local.properties`.

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug lintRelease assembleRelease assembleDebugAndroidTest
```

The release build is unsigned until signed with the private updater key. No Quantic signing key is required. Retain and back up the updater key for compatible future upgrades.

Always overwrite **LiquorBee-Updater.apk** in the public GitHub folder and local deliverables. Do not create versioned APK filenames. Increase the internal Android versionCode and refresh the updater's `version.txt`, `SHA256SUMS.txt`, expanded source and `UpdaterSource.zip`. Never publish keys, passwords, local.properties or build caches.

## Verification

Unit tests cover version parsing/comparison, failures, notifications, daily eligibility, configurable schedule times, duplicate-day prevention, invalid times, and both daylight saving transitions (23-hour and 25-hour days). Android instrumentation tests cover the actual POS package, live HTTPS metadata, notifications, review/download/Later, private launch confirmation, alarm configuration/persistence, duplicate alarm rejection, and a real exact alarm invoking the background check and scheduling tomorrow.

Use a disposable Android 15 emulator with the original POS 1.2.1/build 20260910 installed. Install the updater and matching-signed instrumentation APK, then:

```powershell
adb shell pm grant com.liquorbee.updater android.permission.POST_NOTIFICATIONS
adb shell appops set com.liquorbee.updater SYSTEM_ALERT_WINDOW allow
adb shell appops set com.liquorbee.updater SCHEDULE_EXACT_ALARM allow
adb shell am instrument -w com.liquorbee.updater.test/androidx.test.runner.AndroidJUnitRunner
```

These grants are for the disposable emulator. Real registers use the setup buttons above. Tests clear updater preferences and notifications, but never modify POS data, re-sign the POS, or change public release metadata. See BUILD-STATUS.md for results and remaining physical-iMin testing.

Android references: [exact alarms](https://developer.android.com/develop/background-work/services/alarms), [background activity rules](https://developer.android.com/guide/components/activities/secure-bal), and [force-stop behavior](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state).
