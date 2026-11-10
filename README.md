# LoudBooster

LoudBooster is an Android app that adds up to 6 dB of digital gain to compatible Bluetooth media playback. It was tested on a Samsung Galaxy Z Fold7 with Google Pixel Buds Pro 2.

## Features

- Gain control from 0 to +6 dB in 0.5 dB steps
- German interface with the current Bluetooth output and boost status
- Background operation with a stop action in the Android notification when notifications are allowed
- Automatic shutoff when Android reports that the selected Bluetooth output has disconnected or changed

## Use

1. Connect your Bluetooth earbuds and select them as the media output.
2. Start playing audio at a low volume.
3. Open LoudBooster, set the slider to **+1 dB**, and tap **Booster einschalten**.
4. Tap **Booster ausschalten** in the app or notification when you are done.

LoudBooster uses Android's output-mix audio effect. Audible gain depends on the phone, its audio routing, and the playback app. The slider shows the requested digital gain in dB.

## Privacy

LoudBooster runs offline. It checks the media output and applies the effect through Android's audio framework on the device. The app stores whether it has already asked for notification permission.

## Tech stack

- Java and native Android views
- Android `LoudnessEnhancer` and `AudioManager`
- Gradle wrapper and JUnit

## Requirements

- Android 13 or later
- Bluetooth audio output
- JDK 17, Android SDK Platform 36, and Android SDK Build Tools 35.0.0 to build from source

## Build locally

Clone the repository and run these commands from the project directory on Windows.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

On macOS or Linux, use `./gradlew` with the same tasks. Install the resulting debug APK on an ADB-connected Android device with the following command.

```shell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Project status

For compatibility reports, include your phone model, Android version, Bluetooth device, and playback app in a GitHub issue.

## License

This repository currently has no open-source license. All rights reserved. Third-party components retain their respective licenses.
