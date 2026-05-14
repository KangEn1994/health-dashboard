# Android App

This Android client only implements:

- Record management
- Chart viewing

## Stack

- Kotlin
- Jetpack Compose
- Retrofit
- DataStore
- MPAndroidChart

## Default API URL

`http://10.0.2.2:18080/`

This is correct for the Android emulator talking to the local FastAPI server on the same machine.

## Features

- Login with the fixed password
- Configure API base URL
- List and create health entries
- Delete entries
- View dashboard insights
- View weight/body-fat/BMI trend charts

## Build

```bash
cd /Users/kang_en/codex/health-dashboard/android
./gradlew assembleDebug
```
