# LegendMe Android

LegendMe is a local-first Android prototype for recording life experiences, building a personal
knowledge base, and turning selected material into literary works.

This repository contains only the native Android client. The NestJS/Pi Agent backend is maintained
separately in [LegendMeServer](https://github.com/huangyujun1215/LegendMeServer) and must be running
for understanding, companion, embedding, and literary-writing jobs.

This repository builds and tests independently; no parent workspace or backend checkout is needed.
The server owns the API contracts. Client fixture snapshots live in `app/src/test/resources/` and
must be updated explicitly when the wire contract changes.

## Technology

- Kotlin and Jetpack Compose
- Room 15 with tested migrations
- Hilt
- WorkManager
- OkHttp and SSE
- DataStore
- Kotlin serialization
- Minimum Android SDK 26

## Requirements

- JDK 17
- Android SDK 35
- An Android Emulator or device

On macOS with Android Studio installed:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## Configure the Android SDK

```bash
cp local.properties.example local.properties
```

Edit `local.properties` and set your SDK path, for example:

```properties
sdk.dir=/Users/your-name/Library/Android/sdk
```

`local.properties` is intentionally ignored by Git.

## Build

```bash
./gradlew :app:assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Tests

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

With an emulator or device running:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Connect to the backend

For an Android Emulator, configure the server URL in the App settings as:

```text
http://10.0.2.2:3100
```

Enter the same `DEVICE_ACCESS_TOKEN` configured on the LegendMe backend. The Android client never
stores or receives the model provider API key.

Cleartext HTTP is enabled only for the local emulator prototype. Use HTTPS before connecting over
an untrusted network or distributing the App.

## Current capabilities

- Listening and conversation recording modes
- Offline-first raw record persistence
- Background understanding synchronization
- Timeline, correction, search, and interactive knowledge graph
- BM25 with optional server-side embedding retrieval
- Multi-genre literary creation
- Collaborative or autonomous creation mode
- Manuscript versioning, revision, restore, and feedback
- Manual ZIP backup and restore
- Irreversible deletion with a durable server-purge outbox

## Security

- Do not place a model API key in this repository or in the Android App.
- Do not commit `local.properties`, signing keys, device tokens, databases, or build outputs.
- Rotate previously exposed credentials before using real personal records.
