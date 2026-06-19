# Revline — Drive Tracker (MVP)

Android app for manually tracking GPS drives and comparing your actual time
against the Google Maps prediction. This is the **MVP** of a larger car-community
product; this repo intentionally builds *only* the drive tracker, but its seams
are kept open for what comes next (leaderboards, OBD2, events, social feed).

- **Package:** `com.revline.tracker`
- **Min SDK:** 26 · **Target/Compile SDK:** 35
- **Language:** Kotlin · **UI:** plain Views + ViewBinding (no Compose)
- **Persistence:** Room (SQLite)
- **Location:** FusedLocationProviderClient in a foreground service

---

## What it does

1. **MainActivity** — history of past drives, most recent first. Tap a drive to
   review it; tap **+** to start a new one.
2. **NewTripActivity** — enter what Google Maps predicted (minutes, required) and
   optionally a predicted distance, then **Start Drive**. The screen flips to a
   dead-simple in-progress view: a ticking elapsed timer and a big **Stop Drive**
   button. A persistent notification keeps tracking alive through screen-lock and
   backgrounding.
3. **TripSummaryActivity** — distance, duration, avg/top speed, and the headline
   predicted-vs-actual delta (e.g. *"Maps said 22 min — took 26 min (+4 min)"*).

---

## Architecture & future-proofing

Every decision here is made so the bigger product can be bolted on **without a
rewrite or data migration**:

- **Repository pattern, no DB calls in Activities.** All persistence goes through
  `data/TripRepository`. A future `SyncRepository` (networking/leaderboard upload)
  can sit alongside — or wrap — it without touching any UI code. The networking
  layer is deliberately absent for now.
- **Ownership fields on every entity now.** `Trip` (and eventually `Car`/`User`)
  carries `deviceId: String` + `userId: String?`. `deviceId` is a locally
  generated UUID persisted in SharedPreferences (`util/DeviceId`) as a
  pseudo-user-id. When accounts arrive, we **backfill** `userId` instead of
  migrating schema.
- **Clean JSON export.** `Trip.toJson(trackPoints)` and `TrackPoint.toJson()`
  serialize a full drive (including the raw GPS breadcrumb trail). This is the
  hook that makes "upload trip to leaderboard" trivial later.
- **Car identity left as an extension point.** `Trip` has a commented
  `// future: carId: String?` placeholder. The Car table is **not** built yet —
  but adding it (and a migration to add the column) is unawkward.
- **GPS breadcrumbs retained.** Every `TrackPoint` is kept even though there's no
  map UI yet, so a future route-map view has the data it needs.

### Module layout

```
app/src/main/java/com/revline/tracker/
├── MainActivity.kt              # trip history (most recent first)
├── NewTripActivity.kt           # prediction entry + in-progress tracking UI
├── TripSummaryActivity.kt       # post-trip / read-only stats
├── service/
│   └── TrackingService.kt       # foreground service, GPS polling, stat compute
├── data/
│   ├── AppDatabase.kt           # Room database
│   ├── Trip.kt / TrackPoint.kt  # entities (+ toJson)
│   ├── TripDao.kt / TrackPointDao.kt
│   └── TripRepository.kt        # the abstraction layer (sync seam)
├── util/
│   ├── DeviceId.kt              # pseudo-user-id (SharedPreferences UUID)
│   └── SpeedCalculator.kt       # haversine distance, avg/top speed (pure Kotlin)
└── ui/
    └── TripListAdapter.kt
```

### How tracking works

- `TrackingService` is a `location`-type foreground service. On start it creates
  the `Trip` row, then requests location updates at ~2s intervals with
  `PRIORITY_HIGH_ACCURACY`.
- **Each location update is written to Room immediately** as a `TrackPoint` — the
  trail is never buffered in memory only, so a killed process can't lose the drive.
- On stop, it computes from the stored points:
  - **distance** — sum of haversine distance between consecutive points
  - **avg speed** — distance / actual duration
  - **top speed** — max of the raw provider `speedMps` values (preferred), falling
    back to derived speed between points where raw speed is missing
  - **actual duration** — `endTime − startTime`
- UI and service communicate via an observable `TrackingService.state` flow, so the
  in-progress screen reacts to start/stop without binding to the service.

---

## Permissions

Requested in a staged flow, as modern Android requires:

1. `ACCESS_FINE_LOCATION` (+ `POST_NOTIFICATIONS` on Android 13+) — first prompt.
2. `ACCESS_BACKGROUND_LOCATION` — **separate, second prompt** after fine location is
   granted (Android won't grant both in one dialog). Tracking still starts if
   background is denied; it's best-effort for surviving long backgrounding.

Also declared: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` (Android 14+).

---

## Building

This is a standard Gradle project distributed as a **sideloaded APK** — no Google
Play, no App Bundle, no Play-specific APIs.

### Debug APK (fine for v1 testing)

```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Install on a device with USB debugging on:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

You'll need an Android SDK installed and either `ANDROID_HOME` set or a
`local.properties` with `sdk.dir=/path/to/android-sdk`.

### Signed release APK (for real sideloading later)

1. Generate a keystore (once):

   ```bash
   keytool -genkey -v -keystore revline-release.jks \
     -keyalg RSA -keysize 2048 -validity 10000 -alias revline
   ```

2. Add the signing config to `app/build.gradle.kts` (keep secrets out of git —
   read them from `~/.gradle/gradle.properties` or env vars):

   ```kotlin
   android {
       signingConfigs {
           create("release") {
               storeFile = file(System.getenv("REVLINE_KEYSTORE") ?: "revline-release.jks")
               storePassword = System.getenv("REVLINE_STORE_PASSWORD")
               keyAlias = "revline"
               keyPassword = System.getenv("REVLINE_KEY_PASSWORD")
           }
       }
       buildTypes {
           getByName("release") {
               signingConfig = signingConfigs.getByName("release")
           }
       }
   }
   ```

3. Build:

   ```bash
   ./gradlew assembleRelease
   # → app/build/outputs/apk/release/app-release.apk
   ```

---

## Explicitly out of scope (v1)

Not built, and intentionally not stubbed — only the seams above are left open:
accounts/auth, any networking/backend, leaderboards, Bluetooth/OBD2, car
meets/cruise events, social feed, and route-map rendering.
