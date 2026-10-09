# GN GPS Trip Meter — Huawei Android 9

Start, Pause, Reset GPS distance tracking for personal use, with a foreground location service that continues while switching to PickMe or locking the screen. Uses device GPS, no network requests or tracking server.

- Android: minSdk 23, targetSdk 28, build on SDK 35.
- Android 9 EMUI 9.1 (Huawei PAR-LX9) target device.
- Start tracking while app is visible, allow precise Location, and keep the persistent notification enabled.
- Huawei may stop GPS unless you enable manual background app launch: Settings > Battery > App launch > GN GPS Trip Meter > Manage manually > Allow run in background.
- Distance is an approximate GPS measurement; tests on a real Android phone are still required.
- Separate package id from earlier nonworking APKs to prevent signing conflicts.

Build with Gradle 8.7 / JDK 17:
    gradle :app:assembleDebug
Generated APK:
    app/build/outputs/apk/debug/app-debug.apk

The GitHub Actions artifact named GN-GPS-Trip-Meter-APK contains the installable debug APK.
