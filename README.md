# Revline

[![Android CI](https://github.com/bigfatmeagaooff/Revline/actions/workflows/android.yml/badge.svg)](https://github.com/bigfatmeagaooff/Revline/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%2026%2B-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)

A native Android app for car enthusiasts. Record a drive, get a shareable "timing
slip" (route map, speed, G-force, 0–100, cornering), rank it on a public leaderboard,
and follow other drivers. Backed by a self-hosted Node/Express/PostgreSQL API
(companion repo: **revline-server**).

Distributed as a signed APK on the [Releases](https://github.com/bigfatmeagaooff/Revline/releases)
page — not on Google Play (yet). Updates install in place.

- **Package:** `com.revline.tracker`
- **Min / target / compile SDK:** 26 / 35 / 35
- **Language:** Kotlin, coroutines + StateFlow
- **UI:** Views + ViewBinding (no Compose), Material 3
- **Local storage:** Room (SQLite), schema v6
- **Networking:** Retrofit + OkHttp; auth tokens in EncryptedSharedPreferences
- **Location:** FusedLocationProvider in a foreground service
- **Sensors:** linear-acceleration G-force capture in the same service
- **Maps:** OSMDroid / OpenStreetMap (no API key, no billing)
- **Images:** Coil · **Crash reporting:** Sentry · **Push:** Firebase Cloud Messaging (optional)

---

## What it does

### Record a drive

One tap on **Start Drive**. A `location`-type foreground service records a GPS
breadcrumb, live speed, and G-force for the whole drive, writing every point to Room
as it happens — a killed process or flat battery can't lose the trail. It survives
screen-lock and backgrounding. Optional **automatic detection** (off by default)
starts and stops drives on its own using Play Services activity recognition.

Battery Saver is detected — recording still works, but a drive taken with it on is
kept off the leaderboard (throttled GPS makes the numbers unreliable).

### The timing slip

Every finished drive becomes a drag-strip-style printout:

- **Trap (top) speed**, distance, duration, moving time, **0–100 km/h**, peak G
- A **route map** with the line coloured by speed (cold slate → redline), knocked back
  to greyscale so it recedes
- A **G-force trace** (lateral + accel/brake over the drive) with a ±0.5 G grid
- **Drive detail:** stop count, cornering G (peak + average), elevation gain/loss,
  hardest-braking callout, idle time, longest continuous stretch above 100 km/h
- A generated, brand-styled **share card**

Speed and G data are cleaned before any stat is computed — GPS accuracy worse than
30 m is dropped and segments implying > 250 km/h are bridged, so a bad fix can't fake
a sub-second 0–100.

### Accounts, leaderboard, social

The app is **local-first** — every drive is fully usable with no account and no
network. An account (`LoginActivity` / `RegisterActivity`, JWT access + hashed
refresh tokens) adds:

- **Sync** — finished drives upload best-effort to the server (`SyncRepository`,
  alongside the local `TripRepository`); failures are non-fatal, an `uploadedAt` stamp
  prevents re-sends, and history restores on a new device.
- **Leaderboard** (`LeaderboardActivity`, public — no login to view) — top speed /
  fastest 0–100 / longest stretch over 100 km/h. Implausible runs are trust-scored and
  held for admin review, not auto-posted.
- **Your car** — make + model is **required on the account** (asked at registration,
  editable in Profile) and stamped on every upload server-side, so no "unknown car"
  reaches the board.
- **Social** — follow / unfollow, public profiles, **likes and comments** on drives,
  a **notification centre** (bell + unread badge), and **profile pictures** (cropped
  and re-encoded on-device, which also strips the photo's location data).
- **Announcements** — an admin can post an app-wide message shown on next open, and
  gate on a minimum version to prompt an update.

### Onboarding

First launch shows a four-screen walkthrough. **Profile → "How Revline works"** is a
permanent reference covering recording, the timing slip, auto-detect, the leaderboard,
following, and updates.

### Admin

`AdminDashboardActivity` (Overview / Users / Trips / Flagged) — trust-score review,
user management, one-time password-reset codes, "clean unknown cars off the
leaderboard", and announcement compose/manage.

---

## Design — "Time Slip"

Every drive is presented like a drag-strip timing printout: torn-ticket perforated
card edges, a run stub, trap speed up top, each stat on its own ruled line with a
dotted leader to a right-aligned monospace value.

- **Palette:** blue-slate ink `#0E0F12`, slip `#16181D`, hairline `#262A31`, redline
  accent `#F5121C` (speed / ranks / primary CTA), staging-green `#D8FF3E` (a second
  accent, for live / personal-best only), thermal-print text `#ECEEF2` / `#8A9099` /
  `#565C66`.
- **Type:** Barlow Condensed (hero numerals), JetBrains Mono (the readout column),
  Inter (body). All bundled.

---

## Architecture

- **Local-first.** A drive is written to Room point-by-point; upload is a separate,
  best-effort step. The networking layer (`data/remote/*`, `SyncRepository`) sits
  *alongside* the local `TripRepository` — the repository seam, finally used. No DB
  calls in Activities.
- **Additive migrations only.** Every Room migration to date is an `ALTER TABLE … ADD
  COLUMN`; no destructive fallback has ever been used. Schemas are exported to
  `app/schemas/`.
- **Server URL is a build flag** — `-PrevlineApiBaseUrl=http://<host>/` (or the
  `REVLINE_API_BASE_URL` env var / repo variable). CI defaults it to the production
  server.
- **Ownership fields carried from day one** — `Trip.deviceId` + `Trip.userId?`, so
  accounts backfill rather than migrate.

### Module layout

```
app/src/main/java/com/revline/tracker/
├── SplashActivity · MainActivity · TrackingActivity · TripSummaryActivity
├── LoginActivity · RegisterActivity · ProfileActivity · LeaderboardActivity
├── SearchActivity · UserListActivity · UserProfileActivity · CommentsActivity
├── service/
│   ├── TrackingService.kt          # foreground GPS + G-force, stat compute on stop
│   ├── AutoDetectManager.kt · ActivityTransitionReceiver.kt
│   └── RevlineMessagingService.kt  # FCM entry point (inert without config)
├── data/
│   ├── AppDatabase.kt · Trip/TrackPoint/GForcePoint (+ DAOs)
│   ├── TripRepository.kt           # local persistence seam
│   ├── SyncRepository.kt           # server sync seam
│   └── remote/                     # Retrofit API, models, token store, interceptor
├── ui/
│   ├── NotificationsActivity · OnboardingActivity · HowItWorksActivity
│   ├── TripListAdapter · CommentAdapter · UserAdapter · LeaderboardAdapter
│   ├── GForceGraphView.kt          # Canvas graph of G over trip time
│   └── admin/                      # AdminDashboardActivity + fragments + AnnouncementsActivity
└── util/
    ├── SpeedCalculator · GForceCalculator · TripStatsCalculator
    ├── Avatars.kt (crop/encode + render) · Push.kt · PushNotifications.kt
    ├── Announcements.kt · DeviceId · EdgeToEdge · RelativeTime · CarProfile
    └── TripCardGenerator.kt        # the shareable timing-slip card
```

---

## Backend

The API lives in the **revline-server** repo (Node.js + Express + PostgreSQL, raw
`pg`, JWT auth, pm2, deployed to one VM behind nginx). The app never leaks Retrofit
details upward — everything goes through `SyncRepository`.

**Note:** app ↔ server traffic is currently cleartext HTTP on a raw IP
(`usesCleartextTraffic="true"`). HTTPS with a domain + Let's Encrypt is the top
follow-up — see the server README.

---

## Building

```bash
# JDK 17 required (the Gradle version predates newer JDKs)
./gradlew assembleDebug -PrevlineApiBaseUrl=http://<server-host>/
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Needs an Android SDK with `ANDROID_HOME` set (or `sdk.dir=` in `local.properties`).

### Signing

`app/revline-debug.keystore` is a **committed shared debug key** (standard
`android` / `androiddebugkey` credentials — *not* a Play release key). It's wired into
`signingConfigs.debug` so every build — CI or any machine — signs identically and
updates install in place. Before this was added, each build got a random debug key and
users had to uninstall/reinstall to update.

A real Play-Store release keystore does not exist yet and is required before any
Play submission.

### Releases

The **Release APK** workflow (`.github/workflows/release.yml`) builds the APK and
attaches it to a GitHub Release. Trigger it from the Actions tab with a `tag` input
(e.g. `v3.11.0`), or by pushing a `v*` tag. `android.yml` runs a compile check on
every push to `main`.

---

## Push notifications (optional)

Off by default. Without Firebase config the app still works — follows / likes /
comments and announcements just appear on next app open (poll-on-open). To enable real
push (wakes a closed phone):

1. Create a Firebase project, add an Android app with package `com.revline.tracker`.
2. Put the generated `google-services.json` at `app/google-services.json`. The Gradle
   build detects it and switches push on (`BuildConfig.PUSH_CONFIGURED`); the file is
   safe to commit but for a public repo prefer injecting it in CI, and restrict its
   API key to the app in the Google Cloud console.
3. Set up the server side (`FIREBASE_SERVICE_ACCOUNT`) — see the server README.

`util/Push.kt` (token lifecycle), `util/PushNotifications.kt` (channels + rendering),
`service/RevlineMessagingService.kt` (FCM entry point).

---

## Roadmap

- **Meets & cruises** — a board to find and RSVP to local car meets with an exact
  location, and live coordination during an organised drive. In development.
- **HTTPS** on the server.
- **Google Play Store** — needs a release keystore, an `.aab` build, a privacy policy,
  and the data-safety / content-rating paperwork.

## Permissions

- `ACCESS_FINE_LOCATION` (+ `POST_NOTIFICATIONS` on Android 13+) — first prompt.
- `ACCESS_BACKGROUND_LOCATION` — a separate second prompt after fine location.
  Tracking still starts if it's denied.
- `ACTIVITY_RECOGNITION` — only requested if automatic trip detection is turned on.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `INTERNET`,
  `ACCESS_NETWORK_STATE`.
