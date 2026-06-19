# Contributing to Revline

Thanks for your interest in Revline. This document covers how to get set up and
the conventions the project follows.

## Getting started

1. Install [Android Studio](https://developer.android.com/studio) (or just an
   Android SDK with platform/build-tools 35) and JDK 17.
2. Clone the repo and open it in Android Studio, or build from the CLI:

   ```bash
   ./gradlew assembleDebug
   ```

   The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

3. To run on a device, enable USB debugging and:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

You'll need either `ANDROID_HOME` set or a `local.properties` containing
`sdk.dir=/path/to/android-sdk` (this file is git-ignored).

## Project conventions

Revline is the MVP of a larger car-community product, so the codebase is
deliberately structured to grow without rewrites. Please keep these intact:

- **Repository pattern.** All persistence goes through `data/TripRepository` —
  no Room/DAO calls directly from Activities or Services. A future
  `SyncRepository` (networking) must be able to slot in without UI changes.
- **Ownership fields.** Any persisted entity carries `deviceId: String` and
  `userId: String?` so accounts can be backfilled later instead of migrated.
- **JSON-exportable trips.** Keep `Trip.toJson()` / `TrackPoint.toJson()` in sync
  with the schema so trips stay trivially uploadable.
- **Scope discipline.** Don't add out-of-scope features yet (auth, networking,
  leaderboards, Bluetooth/OBD2, car meets, social feed, route-map rendering).
  Leave seams open; don't build the rooms.

## Branching & commits

- Branch off `main`: `feature/<short-name>` or `fix/<short-name>`.
- Write clear, imperative commit messages ("Add top-speed fallback", not "fixed
  stuff").
- Open a PR against `main`; CI must be green (`Android CI` builds the debug APK).

## Code style

Kotlin official style (`kotlin.code.style=official`). Match the surrounding code:
ViewBinding for views, coroutines for async, no new dependencies without reason.
