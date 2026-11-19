# sharehamster

A native Android stock-tracking app built with Kotlin and Jetpack Compose. It reads market data from ShareSansar's [Live Trading page](https://www.sharesansar.com/live-trading) and keeps personal portfolio holdings locally on the device.

## Features

- NEPSE index overview and live market list
- Search available companies by symbol or name
- Add holdings with share quantity and average buy price
- Portfolio value and profit/loss overview when market prices are available
- Offline-friendly portfolio storage (Android SharedPreferences)
- Clear connection and unavailable-data states

## Run on an Android phone

1. Install **Android Studio** with Android SDK 35 and a JDK 17.
2. Open this project folder in Android Studio and allow Gradle sync to finish.
3. On your phone, enable Developer options and USB debugging, then connect it to the computer. Alternatively, create/start an Android emulator.
4. Select the device in Android Studio and press **Run**.

To build an APK from Android Studio, use **Build → Build Bundle(s) / APK(s) → Build APK(s)**. The debug APK is normally created at `app/build/outputs/apk/debug/app-debug.apk`; install it on your phone to test.

The signed release APK is available at [`releases/sharehamster-1.0.apk`](releases/sharehamster-1.0.apk).

## API and data

The app fetches JSON from `https://nepse-api-gamma.vercel.app/api/market`, a small Vercel backend that uses the unofficial `@rumess/nepse-api` package. Successful backend responses are cached for about 60 seconds. The backend and upstream NEPSE service may be unavailable, and market data may be delayed or inaccurate. The portfolio is for personal tracking and is not investment advice. Holdings are stored only on this device and are not synchronized or backed up by the app.

## Technical details

- Kotlin, Android SDK 35, minimum Android API 24
- Jetpack Compose UI / Material 3
- JSON parsing with Android's `org.json`; market data is provided by the ShareHub Vercel API
