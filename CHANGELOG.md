# Changelog

All notable changes to Revline are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [3.11.0]

### Added

- **Profile pictures.** Tap your avatar in Profile to set a photo (or remove it).
  It's cropped square and scaled down on your phone before upload — which also
  strips any location data the camera saved. Photos show on your profile, other
  people's profiles, and in comments and follower lists; everywhere else falls
  back to the letter chip.
  - Server: `POST` / `DELETE /api/users/me/avatar`, files served from `/uploads`,
    `DELETE /api/admin/users/:id/avatar` for moderation. No new database migration
    (the `avatar_url` column already existed).
- **First-run walkthrough.** New users get a four-screen intro (recording a drive,
  the timing slip, the leaderboard, the social side) before the sign-in screen.
- **"How Revline works"** — a reference page in Profile covering recording, the
  timing slip, auto-detect, the leaderboard and car requirement, following, profile
  pictures, and how updates work.

### Changed

- Back buttons on the trip summary and profile screens — both are opened from the
  main screen but previously had no on-screen way back.

## [3.10.1]

### Fixed

- **App crash on launch during a drive (`FOREIGN KEY constraint failed`).** The
  launch-time "ghost trip" cleanup (`DELETE FROM trips WHERE endTime IS NULL`) could
  delete the drive that was *currently recording* — its row has no end time until the
  drive finishes — most easily by tapping the tracking notification, which opens the
  main screen. The tracking service's next GPS/G-force write then hit a broken foreign
  key and crashed the app, repeatedly. Now the active trip is always excluded from
  cleanup, and point writes can no longer crash the app if their trip disappears.
  This also means **an in-progress drive is no longer silently deleted** when you open
  the app mid-drive.

## [3.10.0]

### Added

- **Real push notifications.** Follows, likes and comments now reach your phone
  even when Revline is closed, and a new announcement goes out to everyone
  immediately instead of waiting for their next app open. Two notification
  channels (Social / Announcements) so each can be tuned or muted separately.
  - Server: migration 010 (`device_tokens`), `POST /api/devices/register` +
    `/unregister`, `src/services/push.js` (Firebase Admin). A push is sent
    alongside every notification row and on every new announcement; stale tokens
    are pruned automatically.
  - Requires a one-time Firebase setup — a project with an Android app
    (`com.revline.tracker`), `app/google-services.json` in the app repo, and
    `FIREBASE_SERVICE_ACCOUNT` on the server. Without it the build still works and
    push is simply off (notifications appear on next open, as before).

## [3.9.0]

### Added

- **Notification centre.** A bell in the top bar with an unread badge → follows,
  likes and comments on your runs. Tap a row to jump to the person or the run;
  opening the list marks everything read.
  - Server: migration 009, notification rows written on follow / like / comment
    (best-effort, never self-notify); `GET /api/notifications`,
    `GET /api/notifications/unread-count`, `POST /api/notifications/read`.
- **Announcements.** An admin can post an app-wide message (Admin → Overview →
  Announcements) that every user sees on their next app open — meet notices,
  "apply for VIP parking" with a link button, general news. Each has a kind
  (info / event / update) and a dismiss behaviour (normal / 5-second timer /
  blocking).
- **Update prompt.** An announcement with a minimum version code shows to anyone
  on an older build, with an "update" link and a 5-second timer (or no way past,
  for a hard requirement). It repeats until they update.
  - Server: migration 008, `GET /api/announcements?versionCode=N`,
    `POST /api/announcements/:id/ack`, admin CRUD under `/api/announcements/admin`.

## [3.8.2]

### Added

- Profile → Account shows the running app version.

## [3.8.1]

### Added

- **Your car is now part of your account.** Registration asks for make + model
  (year optional) and saves it to the server. Trip uploads use the account car,
  taken server-side — so an "unknown car" can never reach the leaderboard.
  Existing accounts add theirs in Profile → My Car (it also syncs to the server
  now). Uploading a run with no car set prompts you to add one first.
  - Server: migration `007_user_car`, `PUT /api/users/me/car`, `GET /api/users/me`,
    upload returns `422 NO_CAR` when the account has none, leaderboard queries
    exclude carless trips. Backfill pulls each user's car from their most recent
    trip that had one.
- **Admin: "Clean unknown cars off the leaderboard."** In the admin Overview tab —
  finds every leaderboard run with no car and takes it off (`POST
  /api/admin/leaderboard/purge-unknown`; marks them rejected, an admin can restore
  individual runs).

### Fixed

- **Updates install in place now — no more uninstall/reinstall.** The build had no
  signing config, so every machine (and every fresh CI runner) generated its own
  random debug key; APKs signed with different keys can't update each other. A
  shared debug keystore (`app/revline-debug.keystore`) is now committed and wired
  into `signingConfigs`, so every build signs identically. *One last clean install
  is needed to move off the v3.8.0 key; every update after that just works.*
- `reset-password` now wraps its three writes (new password, mark code used, clear
  sessions) in a transaction.

## [3.8.0]

### Changed

- **Full UI redesign — "Time Slip".** Every drive is presented like a drag-strip
  timing printout: torn-ticket perforated edges, a run stub, trap speed in a truer
  red (`#F5121C`), and every stat as a ruled row with a dotted leader to a
  monospace value. The leaderboard becomes a timing sheet; the shareable card is a
  literal printout. New palette (blue-slate ink, staging-green as a second accent
  used only for live/PB), Barlow Condensed + JetBrains Mono + Inter.
  - **Fixes a latent bug:** the bundled Barlow Condensed / Inter fonts were never
    actually loading — every screen silently fell back to Roboto. They now load via
    `font-family` resources (`rl_display`, `rl_mono`).
  - Route map knocked back to dark greyscale so it recedes; route line runs cold
    slate → redline by speed. G-force graph on the palette with a ±0.5 G grid.

### Fixed

- Bug-review pass: `textFontWeight` (API 28+) replaced with weight-specific font
  families for minSdk 26; the tracking screen's dashed rule swapped for a tiled
  drawable that renders on a hardware canvas.

## [3.7.0]

### Added

- **Crash & error reporting (Sentry).** `sentry-android` auto-initialises from the
  manifest and captures uncaught exceptions + ANRs. The DSN is a build-time flag
  (`-PrevlineSentryDsn=...` / `REVLINE_SENTRY_DSN`), never committed; a blank DSN
  disables the SDK entirely. The release workflow takes a `sentryDsn` input.
- **Admin-assisted password reset.** Long-press a user in the admin dashboard's Users
  tab to issue a one-time reset code (`POST /api/admin/users/:id/reset-code`). The user
  redeems it from the sign-in screen via **Forgot password?**
  (`POST /api/auth/reset-password`), choosing their own new password; all their
  sessions are invalidated on success. Self-service email delivery is still to come.

### Changed

- **Full UI redesign — dark motorsport aesthetic (car-meet demo).** New design-token
  system (`colors.xml` racing palette, `styles.xml` typography/components, dark
  `Theme.Revline`), bundled Barlow Condensed + Inter fonts. Signature racing red
  (`#E8000D`) reserved for speed numbers, ranks, and CTAs.
  - New branded **SplashActivity** (red-line sweep) as launcher; routes to Main/Login.
  - **Trip list:** redesigned cards (red top-speed, white distance, upload badge),
    date-grouped section headers, persistent Start Drive button, empty state; ghost +
    0-stat trips filtered out (`TripDao.observeVisible` / `deleteGhostTrips`).
  - **Leaderboard:** tabbed (red underline), red ranks, #1 accent treatment,
    red-tinted pull-to-refresh, designed empty state.
  - **Trip summary:** 96sp red hero top-speed, 2×3 stat grid, conditional G-force
    section, colored upload status strip, Share + Re-upload actions.
  - **During-drive:** live dashboard — huge red live speed, elapsed, live G, GPS dot,
    Stop with confirm dialog (live speed exposed via `TrackingService.liveSpeedKmh`).
  - **Login/Register/Profile** restyled; profile gains an initials avatar + a server
    stats row (drives / best top speed / best distance) and an exclusive admin button.

### Fixed

- **Hotfix — null leaderboard stats, re-upload, and trip restore (demo prep).**
  - **Null stats on upload:** uploads are now gated on real computed stats
    (`distanceKm > 0 && topSpeedKmh > 0`), so trips that were never finalized (e.g. the
    service was killed mid-drive) can't create empty/null server rows. The server INSERT
    already covered every column — verified end-to-end.
  - **Re-upload button:** `TripSummaryActivity` shows "Re-upload to leaderboard" for any
    trip with valid local stats; it clears the local uploaded stamp and re-sends. (Delete
    the bad null server row first so dedup doesn't block the corrected insert.)
  - **Server-side trip restore:** on login / app start, `GET /api/trips/mine` is pulled
    and any trip missing locally is re-inserted (keyed on deviceTripId), so reinstalling or
    switching phones brings history back. Restored trips are stats-only (no GPS/G
    breadcrumbs): the map + G graph are hidden and a "restored from server" note is shown.
    New `Trip.restoredFromServer` (Room v3→v4 migration); `/api/trips/mine` extended with
    the prediction fields needed to reconstruct a trip.

### Added

- **Phase 3.3 (part 1) — start-flow + G-force refinements.**
  - **Speed-gated G-force:** post-trip G stats (max lateral/accel/braking, graph, hardest
    braking) and the uploaded G figures now exclude readings captured while stopped — each
    `GForcePoint` is cross-referenced against interpolated GPS speed and dropped below
    5 km/h, removing phone-handling spikes at trip start/end. Filter applied at calc time;
    raw points kept. The live in-drive G-meter still shows everything.
  - **One-tap Start Drive:** removed the manual pre-drive entry screen (`NewTripActivity`
    deleted). The home-screen button starts tracking immediately into a new
    `TrackingActivity`; the staged location-permission flow moved to `MainActivity`.
  - **Optional post-drive prediction:** a lightweight inline field on the trip summary to
    add a Maps prediction (minutes) after the fact; sets the predicted-vs-actual banner, or
    stays hidden if skipped. `predictedMinutes` is now "0 = not set" (kept non-null to avoid
    a trips-table recreate that would risk cascade-deleting breadcrumb/G data).
  - *(Feature 4 — profile avatars / edit / stats — lands next with its server endpoints.)*

- **Phase 3.2 — full admin dashboard (admin-only).** The Admin Panel button now opens a
  tabbed `AdminDashboardActivity` (ViewPager2 + TabLayout) replacing the single flagged
  screen:
  - **Overview** — total users/trips/distance/drive-time, trips today, flagged pending,
    and active-now (green dot).
  - **Users** — all users with trip count, joined date, relative last-seen, active dot,
    and an Admin badge; tapping a user filters the Trips tab to them.
  - **Trips** — all trips (not just flagged) with stats, car, trust score, FLAGGED badge,
    and verdict; pull-to-refresh; user filter banner.
  - **Flagged** — the Phase 3.1 review queue (Approve/Reject), unchanged.
  - Presence: `POST /api/users/heartbeat` every 3 min while the tracking service runs and
    once on app foreground; new `last_seen` column (server migration `002_last_seen.sql`).
  - New server endpoints: `/api/admin/stats`, `/api/admin/users`, `/api/admin/trips`
    (`?flagged`, `?userId`). All still admin-gated; regular users see nothing different.

- **Phase 3 — accounts, trip upload, and leaderboard.** Revline now syncs to a
  self-hosted backend (companion `revline-server`):
  - Email/password auth (`LoginActivity` / `RegisterActivity`) with JWT access +
    refresh tokens in `EncryptedSharedPreferences`; `AuthInterceptor` auto-refreshes
    on 401.
  - New networking layer (`SyncRepository`, `RevlineApi`, `ApiClient`, `RemoteModels`)
    alongside the local `TripRepository` — UI never calls the network directly.
  - Best-effort trip upload from `TripSummaryActivity` (with Phase 2 stats + car),
    deduped server-side; new local `Trip.uploadedAt` (Room v3 migration) prevents
    re-uploads. Upload status shown on the summary.
  - Public `LeaderboardActivity` (top speed / 0–100 / longest stretch, pull-to-refresh).
  - `ProfileActivity` with account actions and a locally-stored "My Car" (make/model/year)
    sent with uploads.
  - Server base URL is build-configurable (`-PrevlineApiBaseUrl`), not hardcoded.

### Fixed

- **G-force section absent on some devices (Phase 2.2).** Devices that don't provide
  the fused `TYPE_LINEAR_ACCELERATION` sensor captured zero G-points, so the whole
  G-Force section was hidden. The service now falls back to the raw `TYPE_ACCELEROMETER`
  (the same sensor third-party G-meters use) when the fused one is missing — baseline
  calibration already removes gravity, so readings are correct under the fixed-mount
  assumption. Added diagnostic logging at sensor selection, registration, calibration,
  and first write.
- **Blank/white route map on some devices (Phase 2.3).** OSMDroid is now configured in
  `RevlineApp.onCreate()` (a new `Application`) so the OSM-required user agent is set
  before any `MapView` is constructed, and the tile cache is pinned to app-private
  storage — fixing tile-load failures from late user-agent timing or non-writable cache
  paths on some OEM devices.

### Added

- **Enhanced trip summary stats (Phase 2 Feature 3).** Idle/stopped time, fastest
  0–100 and 0–60 km/h, longest continuous stretch above 100 km/h, a hardest-braking
  callout (G + time into the drive), and moving-average speed vs overall average — all
  computed on read in `TripStatsCalculator` from the existing cleaned data.

### Fixed

- **Empty/sparse trip handling (Phase 2.1).** Trips with too few usable GPS points
  (e.g. an indoor smoke test) now show a route placeholder instead of a world-zoomed
  map and read "—" for distance/speed instead of `0.00`. Genuinely slow-but-tracked
  drives still show real low numbers; the empty state only triggers on too-few-points,
  not low speed.

## [2.0.0] - 2026-06-19

### Added

- **Route map (Phase 2).** `TripSummaryActivity` now renders the trip's GPS trail on
  an OSMDroid / OpenStreetMap map as speed-colored polyline segments (green → red,
  relative to the trip's own 5th–95th percentile speeds), auto-fit to the route's
  bounding box, with OpenStreetMap attribution.
- **G-force tracking (Phase 2).** The tracking service samples the linear-acceleration
  sensor (calibrated baseline over the first ~1s), records lateral/forward G as new
  `GForcePoint` rows, shows a live readout on the in-progress screen, and summarizes
  max lateral / accel / braking plus a G-over-time graph on the summary screen.
- `GForcePoint` entity + DAO, `GForceCalculator`, and `GForceGraphView`.
- JSON export now includes G-force readings (`Trip.toJson(trackPoints, gForcePoints)`).

### Fixed

- **GPS outlier rejection.** Phantom speed spikes from inaccurate GPS fixes in
  low-reception areas (observed: 402 km/h) no longer corrupt top/avg speed or the route
  map. Raw points are kept; `SpeedCalculator` filters points worse than 30 m accuracy
  and rejects/bridges segments implying over 250 km/h.

### Changed

- `TrackPoint` gains `accuracyMeters: Float?`; Room schema bumped to v2 with a real
  migration (existing V1.0 trips and breadcrumbs preserved).
- New permissions: `INTERNET`, `ACCESS_NETWORK_STATE` (map tiles). Motion sensors need
  no runtime permission.

## [1.0.0] - 2026-06-19

### Added

- Initial drive-tracker MVP.
- Manual start/stop GPS drive tracking via a `location`-type foreground service
  (`TrackingService`) using FusedLocationProviderClient at ~2s,
  `PRIORITY_HIGH_ACCURACY`.
- Trip history (`MainActivity`), prediction entry + live in-progress screen
  (`NewTripActivity`), and a post-trip summary (`TripSummaryActivity`) showing
  distance, duration, avg/top speed, and the predicted-vs-actual delta.
- Room persistence for `Trip` and `TrackPoint`, with each GPS breadcrumb written
  immediately so a killed process can't lose the trail.
- Trip-end stats: haversine distance, average speed, and top speed (raw provider
  speed preferred, derived speed fallback).
- Staged runtime permission flow (fine location + notifications, then background
  location as a separate prompt).
- Future-proofing seams: `TripRepository` abstraction, `deviceId`/`userId` on
  every entity, `toJson()` export, commented `carId` placeholder, exported Room
  schema.

[Unreleased]: https://github.com/bigfatmeagaooff/Revline/compare/v2.0.0...HEAD
[2.0.0]: https://github.com/bigfatmeagaooff/Revline/compare/v1.0.0...v2.0.0
[1.0.0]: https://github.com/bigfatmeagaooff/Revline/releases/tag/v1.0.0
