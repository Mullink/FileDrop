# LiquorBee POS Updater

- [Download LiquorBee Updater](LiquorBee-Updater.apk?raw=true)
- [Android source](UpdaterSource) · [Source archive](UpdaterSource.zip)
- [Build and test status](UpdaterSource/BUILD-STATUS.md) · [APK SHA-256](SHA256SUMS.txt)

## Daily check at 10:30 AM Central Time

LiquorBee Updater 1.0.5 checks once a day at **10:30 AM Chicago time**, following daylight saving time. Tap **Daily check: 10:30 AM · Change** in the app to choose another time. The app displays the next check. Saving a time schedules its next occurrence, including today even if an earlier check already ran. Saving the current minute checks shortly. It then repeats daily.

At the scheduled time, it reads the installed LiquorBee POS build and the small public version file. It opens the updater only if the installed POS is out of date and automatic opening is enabled and permitted. Current, missing or unverifiable POS versions do not auto-open. Later dismisses today's review; tomorrow's check stays scheduled.

### Setup after installation

1. Open the updater. Keep **Check for updates daily** and **Open updater when an update is available** enabled.
2. Tap **Set up now** in the first-launch **Enable daily update reminders** prompt. Existing installations upgrading to this release also see it if a required permission is missing.
3. Allow **Display over other apps** for LiquorBee Updater, then return to continue to **Alarms & reminders** on Android 12+. Already-granted permissions are skipped. **Not now** leaves the existing permission buttons available for later setup.
4. Return to the updater, allow notifications as a fallback and confirm the next check time.

The register must be awake and unlocked for the review screen. Android/OEM/kiosk restrictions can delay checks or block opening. Force-stop pauses the updater until it is opened again. Reboot restores the alarm; a pending check delayed by up to 15 minutes may run on recovery. Choose a suitable store time because automatic opening can interrupt an active sale.

The app uses a brief foreground service only while the scheduled check runs. It fetches only [LiquorBeePOS/version.txt](../LiquorBeePOS/version.txt), not the full POS APK. Download opens the original Quantic-signed POS APK in the browser; Android handles installation. The POS APK is never modified or re-signed.

This folder's version.txt is updater build **6**. POS metadata remains **20260910** (POS 1.2.1). These are separate version numbers.

## If a scheduled opening is missed

Read **Last scheduled check** in the app. This separate result survives reopening the app and records why opening was skipped, requested or confirmed. Manual checks do not replace it. The operator confirmed that allowing automatic opening resolved the reported register issue; the new setup prompt helps make this requirement clear. See the source README for troubleshooting.

## Publishing convention

Always overwrite **LiquorBee-Updater.apk**. Do not create versioned APK filenames. Increase Android's internal versionCode, keep the same private updater key, and refresh version.txt, SHA256SUMS.txt and both source copies. No keys or credentials belong in this folder.

Clean compilation, both lint variants and 35 unit tests passed. See the [verification status](UpdaterSource/BUILD-STATUS.md) for the emulator results and test limits. Physical iMin testing remains.
