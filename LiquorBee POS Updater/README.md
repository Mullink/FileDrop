# LiquorBee POS Updater

A separate Android app for checking and downloading LiquorBee POS updates.

- [Download LiquorBee Updater](LiquorBee-Updater.apk?raw=true)
- [Expanded Android source](UpdaterSource)
- [Complete source archive](UpdaterSource.zip)
- [Build and test results](UpdaterSource/BUILD-STATUS.md)
- [APK SHA-256](SHA256SUMS.txt)

Install the updater APK and open it once. Allow notifications to receive new-build alerts. It reads the installed `com.liquorbee.liquorbeepos` versionCode and checks [the POS release version](../LiquorBeePOS/version.txt) approximately hourly, including when the screen is closed. A notification opens a review/download screen. Automatic opening can bring up the review screen at most once every 24 hours while a newer POS build remains available. Later closes it and postpones opening for 24 hours. Download opens the original Quantic-signed POS APK in your browser; Android handles installation.

Android can delay background jobs. Reboot resumes scheduling after normal boot/unlock availability. Force-stop pauses execution until you reopen the updater.

This folder's `version.txt` is the updater's own build number (`3`). The POS release metadata remains at `LiquorBeePOS/version.txt` (`20260910`, matching POS 1.2.1). The updater never uses its own build number to compare POS versions.

## Publishing convention

Always overwrite `LiquorBee-Updater.apk` with the latest signed updater. Keep this download filename stable; do not add versioned APK filenames. Update `SHA256SUMS.txt`, the source archive, and the updater's `version.txt` for each release. Continue increasing Android's internal `versionCode` and use the retained signing key so installed copies can update normally.

Compilation, debug/release lint, 20 unit tests and 11 Android 15 emulator tests passed. Physical iMin acceptance testing remains. The release APK is signed with a dedicated updater key retained privately; no keys or credentials are included here. The POS APK is never modified or re-signed.

Open `UpdaterSource` in Android Studio, or see its README for command-line build/test instructions. Scheduled checks fetch only the small POS version file; they do not download the full POS APK or scrape portal HTML.

## Enable daily automatic opening

After installing, open the updater and keep **Open update screen once a day** enabled. On Android 10+, tap **Allow automatic opening**, select LiquorBee Updater, and allow **Display over other apps**. Return to the app. This one-time Android permission is necessary to open over the POS; notification permission alone does not allow it. Android 9 does not require this exemption.

The terminal must be awake and unlocked. Hourly checks remain inexact; the daily limit is a rolling 24-hour interval, not a fixed time. Automatic opening may interrupt a sale. If an OEM/kiosk policy blocks opening or the permission is unavailable, tap the notification instead. A blocked launch is not counted as a successful daily opening. See the source README and build status for details and remaining physical-iMin acceptance tests.
