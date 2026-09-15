# LiquorBee Updater

A separate Android app that checks for newer LiquorBee POS iMin builds. It does not modify, re-sign, or run inside the POS app.

## Behavior

- Reads the installed version of `com.liquorbee.liquorbeepos` with Android's package manager.
- Fetches only the small [public version.txt file](https://raw.githubusercontent.com/Mullink/FileDrop/main/LiquorBeePOS/version.txt).
- Checks on opening the updater and approximately hourly in the background with Android JobScheduler.
- Background checks continue when the updater screen is closed or swiped away, and the job persists after reboot. Android/OEM battery restrictions can delay work. Force-stop suspends the app until the user launches it again.
- Shows Up-to-date, Out-of-date, Unable to check, or Not installed. A failed request never reports a successful current-version check.
- Posts a notification once for each newly discovered build; tapping it opens the updater. Newer builds can generate a new alert. With automatic opening enabled and permitted, it also opens the review screen at most once every 24 hours while an update remains available. Later remembers the notified build, closes the screen, and postpones automatic opening for 24 hours.
- Opens the [original Quantic-signed iMin APK](https://portal.liquorbee.com/download/liquorbeepos/imin.apk) in the browser. Android and the browser handle download/installation and any required install-source permission.
- Rechecks the installed version on returning. Starting a download is never treated as a successful installation.
- Supports Android 9 (API 28) or later. Android 13+ requires notification permission; denied notifications are explained in the app with an Enable notifications action.

There is no portal HTML scraping, periodic APK download, always-running foreground service, silent installation, or POS transaction access. Automatic opening can interrupt a sale: this independent app cannot reliably know whether the POS has an active transaction. The cashier still chooses when to download and install.

## Version file contract

`LiquorBeePOS/version.txt` must contain the offered POS APK's integer Android `versionCode`, followed by an optional newline. It is not the updater's version, a release counter, or the human-readable `versionName`.

The inspected POS APK was version `1.2.1`, build `20260910`. Its matching version.txt is:

```text
20260910
```

Publish the Quantic-signed APK at the download URL first, then publish the matching version.txt. Keep the same POS application ID and compatible signing certificate so Android can update the installed POS while preserving its data. The updater has its own signing identity.

### Read the build from an APK

The helper uses Android SDK `aapt2` and checks that the package is LiquorBee POS before writing. It never commits or uploads files and refuses to lower a valid published build.

From `LiquorBee POS Updater/UpdaterSource` in PowerShell:

```powershell
# Inspect a local APK and update the sibling POS version file:
.\tools\Update-PosVersion.ps1 -ApkPath 'C:\Downloads\imin.apk' -VersionFile '..\..\LiquorBeePOS\version.txt'

# Or download the published iMin APK, inspect it, and update version.txt:
.\tools\Update-PosVersion.ps1 -DownloadLatest -VersionFile '..\..\LiquorBeePOS\version.txt'
```

The second command downloads the full POS APK (about 306 MiB for the inspected build). Run it when publishing/checking a release, not on every terminal every hour. Use `-AndroidSdk` if the SDK is installed elsewhere. Omitting `-VersionFile` inspects without writing.

## Build

Requires JDK 17 or later, Android SDK platform 35, and Android build tools. Android Studio's bundled JDK is suitable. Open this directory in Android Studio and let it configure the SDK, or set `ANDROID_HOME` / an untracked `local.properties` file.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Its package is `com.liquorbee.updater`; installing it does not replace the POS. For deployment, use a dedicated, backed-up updater signing key and retain it for future updater releases. Do not upload keystores, passwords, local.properties, or Gradle caches to public GitHub.

The `release` build is intentionally unsigned until a private signing configuration is supplied by the release operator. No Quantic private signing key is needed to build this separate updater.

### Publish each release

Always overwrite `LiquorBee-Updater.apk` in the GitHub updater folder and local deliverables. Do not retain versioned APK filenames. Keep increasing Android's internal `versionCode`, sign with the same private updater key, and refresh `version.txt`, `SHA256SUMS.txt`, and `UpdaterSource.zip` with each release.

## Verification

The 20 unit tests cover numeric parsing, invalid/offline version responses, the single lightweight request, first-release notification, remembered dismissal, permissions, paused monitoring, missing POS, and numeric comparisons. Run them with the build command above.

Eleven Android instrumentation tests cover actual package-version lookup, HTTPS fetching, persisted scheduling, notifications, a notification tap opening the review screen, Later, and the browser download intent. Synthetic future releases exist only in the tests; they never change the public version file. Run on a disposable Android 15 emulator with the original POS 1.2.1/build 20260910 installed:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant com.liquorbee.updater android.permission.POST_NOTIFICATIONS
# Disposable test emulator only: enable Android's background-launch exemption.
adb shell appops set com.liquorbee.updater SYSTEM_ALERT_WINDOW allow
adb shell am instrument -w com.liquorbee.updater.test/androidx.test.runner.AndroidJUnitRunner
```

The tests clear updater preferences and notifications on this test device. They do not modify the installed POS APK or POS data.

On an iMin test device, verify:

1. Current/newer/lower version responses and failure states. Use a test endpoint/build for simulated newer releases; do not publish fictitious builds in production version.txt.
2. Notifications allowed/denied and channel disabled; tap opens the updater.
3. Swipe away the updater, then verify a scheduled job still runs. Reboot and verify its persisted job. Force-stop, relaunch, and verify scheduling recovers.
4. Later suppresses repeat notifications for that build, closes the screen, and postpones automatic opening for 24 hours. A later build can notify again but cannot bypass the automatic daily limit.
5. Download the actual Quantic-signed APK, install over the existing POS, return to the updater, and confirm the installed build refreshes and the alert clears.
6. Portrait/landscape and large font sizes, including the system bars on Android 15.

Compilation, both lint variants, all 20 unit tests, and all 11 instrumentation tests passed on 2026-09-15. Physical iMin acceptance testing remains. See BUILD-STATUS.md for the verification record and limits.

Android's [periodic jobs](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setPeriodic(long)) are inexact and can be delayed by Doze, network availability, quotas, and OEM battery policies. Launch the updater at least once after installation. A [force-stopped app](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state) cannot keep checking; explicitly reopening it restores scheduling. Reboot persistence does not bypass force-stop.

## Daily automatic opening (1.0.2)

1. Install the latest `LiquorBee-Updater.apk`, open it, and keep automatic checks and **Open update screen once a day** enabled.
2. On Android 10+, tap **Allow automatic opening**, select LiquorBee Updater if Android shows an app list, and enable **Display over other apps**. Return to the updater. Android 9 does not require this exemption.
3. Allow notifications as a fallback. If the device does not offer the special permission or an OEM/kiosk policy still blocks opening, use the notification to open the review screen.

The existing approximately hourly check opens the screen when a fresh successful check finds a newer installed-POS build, the terminal is awake and unlocked, and at least 24 hours have passed since the last displayed update review or Later action. The daily limit applies across all builds and survives restarts. It is a rolling 24-hour interval, not a fixed clock time. A network error, current installation, disabled monitoring, disabled automatic opening, missing permission, sleeping device, or lock screen prevents automatic launch.

The app requests the documented SYSTEM_ALERT_WINDOW exemption only after the operator chooses the settings button. It does not draw an overlay, use accessibility services, or use full-screen notification privileges. Android can silently block background launches; the app records a successful opening only when its activity resumes with a matching private request token. A blocked request does not consume the daily allowance; a later check may retry after a 15-minute attempt cooldown. Manual review of a fresh update also starts the 24-hour delay.

[Android background activity rules](https://developer.android.com/guide/components/activities/secure-bal). Force-stop and battery/scheduling restrictions still apply. This is a request to open when allowed, not a guarantee of exact daily timing.
