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

`https://health.kangen.fun:7894/`

This is the current default server address. You can still change it on the login screen.

## Features

- Login with the fixed password
- Configure API base URL
- List and create health entries
- Edit entries
- Delete entries
- View dashboard insights
- View weight/body-fat/BMI trend charts
- Home screen widget with 14-day weight sparkline

## Build

```bash
cd /Users/kang_en/codex/health-dashboard/android
./gradlew assembleDebug
```
