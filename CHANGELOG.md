# Changelog

All notable changes to Revline are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/bigfatmeagaooff/Revline/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/bigfatmeagaooff/Revline/releases/tag/v1.0.0
