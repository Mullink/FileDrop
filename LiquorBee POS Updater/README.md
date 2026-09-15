# LiquorBee POS Updater

A separate Android app for checking and downloading LiquorBee POS updates.

- [Download LiquorBee Updater 1.0.0](LiquorBee-Updater-1.0.0.apk?raw=true)
- [Expanded Android source](UpdaterSource)
- [Complete source archive](UpdaterSource.zip)
- [Build and test results](UpdaterSource/BUILD-STATUS.md)
- [APK SHA-256](SHA256SUMS.txt)

Install the updater APK and open it once. Allow notifications to receive new-build alerts. It reads the installed `com.liquorbee.liquorbeepos` versionCode and checks [the POS release version](../LiquorBeePOS/version.txt) approximately hourly, including when the screen is closed. A notification opens a review/download screen. Later remembers the build. Download opens the original Quantic-signed POS APK in your browser; Android handles installation.

Android can delay background jobs. Reboot resumes scheduling after normal boot/unlock availability. Force-stop pauses execution until you reopen the updater.

This folder's `version.txt` is the updater's own build number (`1`). The POS release metadata remains at `LiquorBeePOS/version.txt` (`20260910`, matching POS 1.2.1). The updater never uses its own build number to compare POS versions.

Compilation, debug/release lint, 12 unit tests and nine Android 15 emulator tests passed. Physical iMin acceptance testing remains. The release APK is signed with a dedicated updater key retained privately; no keys or credentials are included here. The POS APK is never modified or re-signed.

Open `UpdaterSource` in Android Studio, or see its README for command-line build/test instructions. Scheduled checks fetch only the small POS version file; they do not download the full POS APK or scrape portal HTML.
