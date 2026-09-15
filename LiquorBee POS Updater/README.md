# LiquorBee POS Updater

- [Download LiquorBee Updater](LiquorBee-Updater.apk?raw=true)
- [Android source](UpdaterSource) · [Source archive](UpdaterSource.zip)
- [Build and test status](UpdaterSource/BUILD-STATUS.md) · [APK SHA-256](SHA256SUMS.txt)

## Daily check at 10:30 AM Central Time

LiquorBee Updater 1.0.3 checks once a day at **10:30 AM Chicago time**, following daylight saving time. Tap **Daily check: 10:30 AM · Change** in the app to choose another time. The app displays the next check. Changing the time after today's check takes effect tomorrow.

At the scheduled time, it reads the installed LiquorBee POS build and the small public version file. It opens the updater only if the installed POS is out of date and automatic opening is enabled and permitted. Current, missing or unverifiable POS versions do not auto-open. Later dismisses today's review; tomorrow's check stays scheduled.

### Setup after installation

1. Open the updater. Keep **Check for updates daily** and **Open updater when an update is available** enabled.
2. On Android 12+, tap **Allow scheduled checks** and allow **Alarms & reminders**.
3. On Android 10+, tap **Allow automatic opening** and allow **Display over other apps** for LiquorBee Updater.
4. Return to the updater, allow notifications as a fallback and confirm the next check time.

The register must be awake and unlocked for the review screen. Android/OEM/kiosk restrictions can delay checks or block opening. Force-stop pauses the updater until it is opened again. Reboot restores the next future daily alarm. Choose a suitable store time because automatic opening can interrupt an active sale.

The app uses a brief foreground service only while the scheduled check runs. It fetches only [LiquorBeePOS/version.txt](../LiquorBeePOS/version.txt), not the full POS APK. Download opens the original Quantic-signed POS APK in the browser; Android handles installation. The POS APK is never modified or re-signed.

This folder's version.txt is updater build **4**. POS metadata remains **20260910** (POS 1.2.1). These are separate version numbers.

## Publishing convention

Always overwrite **LiquorBee-Updater.apk**. Do not create versioned APK filenames. Increase Android's internal versionCode, keep the same private updater key, and refresh version.txt, SHA256SUMS.txt and both source copies. No keys or credentials belong in this folder.

Clean compilation, both lint variants and 30 unit tests passed. All 13 Android emulator cases passed across the initial run and a targeted rerun of two opening checks; see the [verification status](UpdaterSource/BUILD-STATUS.md) for the initial failures and test limits. Physical iMin testing remains.

