<!-- Thanks for contributing to Revline! -->

## What does this PR do?

<!-- A short summary of the change. -->

## Related issue

<!-- e.g. Closes #12 -->

## How was it tested?

<!-- Device(s), Android version, and what you exercised. -->

- [ ] `./gradlew assembleDebug` passes
- [ ] Tested on a real device / emulator

## Checklist

- [ ] Data access goes through `TripRepository` (no DB calls in Activities/Services bypassing it)
- [ ] New entities carry `deviceId` / `userId` if persisted
- [ ] No out-of-scope features added (auth, networking, leaderboards, OBD2, events, social feed)
