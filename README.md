# Revline — Drive Tracker (MVP)

[![Android CI](https://github.com/bigfatmeagaooff/Revline/actions/workflows/android.yml/badge.svg)](https://github.com/bigfatmeagaooff/Revline/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%2026%2B-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)

Android app for manually tracking GPS drives and comparing your actual time
against the Google Maps prediction. This is an early phase of a larger car-community
product; this repo intentionally builds *only* the drive tracker (plus a route map
and G-force tracking as of Phase 2), but its seams are kept open for what comes next
(leaderboards, OBD2, events, social feed).

- **Package:** `com.revline.tracker`
- **Min SDK:** 26 · **Target/Compile SDK:** 35
- **Language:** Kotlin · **UI:** plain Views + ViewBinding (no Compose)
- **Persistence:** Room (SQLite)
- **Location:** FusedLocationProviderClient in a foreground service
- **Sensors:** linear-accelerometer G-force capture in the same service
- **Map:** OSMDroid / OpenStreetMap (no API key, no billing)

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
   predicted-vs-actual delta (e.g. *"Maps said 22 min — took 26 min (+4 min)"*),
   plus the Phase 2 additions below.

### Phase 2 additions

- **Route map (speed-colored).** The trip's GPS breadcrumb trail is drawn on an
  OSMDroid map in `TripSummaryActivity`, as multiple short polyline segments colored
  by speed (green = slow → yellow → red = fast). The color scale is *relative to that
  trip's* speeds (5th–95th percentile), so a city errand and a highway run each use
  their own range. The map auto-fits the route's bounding box.
- **G-force tracking.** The tracking service samples the device's linear-acceleration
  sensor and records lateral (left/right) and forward (accel/brake) G. The in-progress
  screen shows a live readout; the summary shows max lateral / max accel / max braking
  plus a simple G-over-time graph.
- **Enhanced drive-detail stats.** Computed on read from the same data (no new
  sensors/permissions): idle/stopped time, fastest 0–100 and 0–60 km/h, longest
  continuous stretch above 100 km/h, a hardest-braking callout (G + time into the
  drive), and moving average speed (distance ÷ time actually moving) alongside the
  overall average. All use the cleaned/filtered speed data, so a GPS jump can't fake a
  sub-second 0–100.
- **Empty/sparse trip handling.** A trip with too few usable GPS points (e.g. an indoor
  test) shows a route placeholder instead of a world-zoomed map, and reads "—" for
  distance/speed instead of a misleading `0.00 km`. A genuinely slow-but-tracked drive
  (stuck in traffic) still shows its real low numbers — the empty state only triggers
  when there are too few points to be meaningful, not just because speed was low.

> **Phone mounting assumption (important for meaningful G readings):** mount the phone
> upright in a fixed dash/windshield mount, in **portrait**, and leave it untouched for
> the whole drive. On Start, the first ~1 second of sensor data is captured as a
> zero-reference baseline. Lateral G is read from the device X axis and forward/braking
> G from the Z axis. There is no full road-frame sensor fusion (out of scope for now) —
> if the phone is loose or repositioned mid-trip, G values won't be meaningful.

### GPS outlier rejection (Phase 2 bugfix)

Real-world testing surfaced phantom speed spikes (a 402 km/h reading in a CRV) caused
by inaccurate GPS fixes in low-reception areas. Raw `TrackPoint`s are still stored
exactly as recorded; cleaning happens at calculation/render time in `SpeedCalculator`:
points with reported accuracy worse than 30 m are excluded, and segments implying more
than 250 km/h are rejected and bridged. This feeds top/avg speed **and** the route map
coloring, so neither inherits the garbage.

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
- **Clean JSON export.** `Trip.toJson(trackPoints, gForcePoints)` plus
  `TrackPoint.toJson()` / `GForcePoint.toJson()` serialize a full drive (GPS
  breadcrumb trail and G-force readings included). This is the hook that makes
  "upload trip to leaderboard" trivial later.
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
│   ├── SpeedCalculator.kt       # haversine, avg/top speed, GPS outlier cleaning
│   ├── GForceCalculator.kt      # max lateral / accel / braking, hardest brake
│   └── TripStatsCalculator.kt   # idle, 0–100/0–60, longest stretch, moving avg
└── ui/
    ├── TripListAdapter.kt
    └── GForceGraphView.kt       # Canvas line graph of G over trip time
```

(`data/` also holds `GForcePoint` + `GForcePointDao` as of Phase 2.)

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
- In parallel, the service samples the **linear-acceleration** sensor at
  `SENSOR_DELAY_GAME`, calibrates a baseline over the first ~1s, converts to G, and
  writes throttled (~10 Hz) `GForcePoint` rows immediately (same "don't lose data on
  process death" rule as track points). A separate `TrackingService.gForce` flow feeds
  the live readout.

### Map attribution

OpenStreetMap requires attribution. The map adds an OSMDroid `CopyrightOverlay`
("© OpenStreetMap contributors"), shown on the map in `TripSummaryActivity`. Map tiles
need network access when viewing a summary; offline tile caching is not implemented.

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
