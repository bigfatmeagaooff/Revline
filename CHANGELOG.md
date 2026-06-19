# Changelog

All notable changes to Revline are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
