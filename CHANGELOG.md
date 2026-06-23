# Changelog

All notable changes to Revline are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
