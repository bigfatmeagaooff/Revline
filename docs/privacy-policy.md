---
title: Revline Privacy Policy
permalink: /privacy-policy/
---



## Introduction

Revline ("we", "us", "the app") is an Android application for recording and sharing drive tracking data. This policy explains what information we collect, how we use it, and your rights regarding your data.

Revline is built on a local-first model: drives can be recorded and viewed entirely offline on your device. Account creation and upload to our server is optional and adds syncing, leaderboards, and social features.

**Effective Date:** 2026-09-02  
**Last Updated:** 2026-09-01  
**Contact:** [CONTACT EMAIL]

---

## What We Collect

### Location Data

**Precise Location (GPS)**
- Collected when you actively record a drive using the foreground tracking service.
- Includes latitude, longitude, altitude, speed, and accuracy; recorded as breadcrumb GPS points every ~2 seconds while tracking.
- Stops automatically when you tap "Stop Drive" or the app is killed; not collected when the app is closed or not actively tracking.

**Background Location**
- Collected only if you **opt-in** to automatic drive-start detection in Settings.
- Uses Google Play Services' ActivityRecognitionClient to detect when you enter a vehicle (low-power sensor-based, not GPS-based background tracking).
- Permission: `ACCESS_BACKGROUND_LOCATION` is requested separately on Android 11+.
- In the Google Play build, background location is disabled; the optional auto-detect feature is only available in the sideload build.

**Usage:**
- Route recording: GPS points are used to display a map of your drive on the trip summary screen.
- Speed and acceleration measurement: breadcrumb points feed distance, speed, and G-force calculations.
- Local only until upload: precise location data is stored locally on your device until (optionally) synced to your account.

### Account Data

When you create an account, we collect:
- Email address
- Username (unique)
- Car details: make, model, and year (required to post to the leaderboard)
- Password (transmitted to the server, stored as a bcryptjs hash — **never stored in plain text**)

When you update your profile:
- Profile picture (optional): uploaded as a base64-encoded JPEG. The app crops, scales, and re-encodes the image on your device before sending, which strips any EXIF metadata and GPS information embedded by your camera.

### Drive Data Sent to Server

When you tap "Upload" or the app automatically syncs a completed drive to your account, the following is sent to the Revline server:
- Trip summary: distance, duration, top speed, average speed, 0–100 and 0–60 times, cornering G-force (max lateral, max acceleration, max braking), stop count, elevation gain/loss
- Route: the GPS breadcrumb trail (full polyline of points)
- Car: make, model, year (read from your account, not the client payload)
- Timestamps: start and end time

This data is stored on the Revline backend and associated with your account. Drives recorded locally without signing in remain on your device only and are never sent to any server.

### Social Data

If your account is signed in:
- Your profile (username, car, profile picture if set) is public and visible to other users.
- Your drive statistics (trips, top speeds, comments) are public on the leaderboard.
- Follows, likes, and comments you make are visible to other users (attribution by username).
- Other users' follows, likes, and comments on your drives are stored and shown to you.

### Crash and Diagnostic Data (Sentry)

We use **Sentry** (a third-party error monitoring service) to automatically capture and report:
- App crashes and unhandled exceptions
- Application Not Responding (ANR) errors
- App version and build information
- Device model and operating system version
- Breadcrumbs (in-app events leading up to a crash)

**Optional bug reports:** If you choose to submit a bug report in-app, Sentry also receives:
- The text you write in the report
- Your username and email (if you're signed in)
- Your Revline user ID (if you're signed in)
- Your device model and Android OS version, and the app version

Sentry is a data processor; we do not share this data with third parties beyond Sentry itself. Sentry's privacy policy is at https://sentry.io/privacy/.

### Push Notification Tokens (Firebase Cloud Messaging)

When you sign in, the app registers a Firebase Cloud Messaging (FCM) token with the Revline server. This token:
- Ties your device's push registration to your account
- Is used to send you notifications about follows, likes, comments, and announcements (when this feature is enabled)
- Is deleted when you log out or delete your account

**Note:** As of v4.0.0, push notifications are dormant (no Firebase project is configured), so this feature is not yet active. The code is shipped but inert. When Firebase is configured in the future, notifications will be delivered via this token without requiring code changes.

### Device Identifiers

- A pseudonymous **device ID** (a UUID generated on first app launch) is stored on your device and used to track local trip history.
- When you create an account, this device ID is linked to your user ID server-side.
- Local-only drives (no account) use only the device ID; the server never knows about them.

### Profile Pictures

Profile pictures are:
- Cropped to a square, centre-cropped, scaled to 256×256 pixels, and re-encoded as JPEG quality 82 on your device.
- Transmitted as base64-encoded data in an `AvatarUploadRequest`.
- Stored on the Revline server and served publicly to other users who view your profile.

The re-encoding process **strips EXIF and GPS metadata** that cameras embed; only the visible image data is sent to the server.

---

## How We Use Your Data

### For the Core Features
- **Recording drives:** GPS points, speed, G-force are used to compute trip stats and display your route map.
- **Leaderboard and social:** Trip stats, your car, username, and profile picture are used to rank drives, show your profile, and enable follows/likes/comments.
- **Sync:** Your account email and password authenticate your session; tokens are stored securely on your device.

### For Reliability and Safety
- **Crash reporting (Sentry):** Crashes and ANRs are analyzed to identify bugs and improve app stability.
- **Error tracking:** Server-side errors are reported to Sentry to catch and fix issues quickly.

### Optional Features
- **Push notifications (when enabled):** FCM tokens are used to deliver notifications about follows, likes, comments, and announcements to your device.

### We Do NOT
- Sell your data to advertisers or third parties.
- Use location data for any purpose other than recording your drives and (optionally) auto-detecting when you start driving.
- Share your account data, trips, or social activity with any party other than the processors listed below.
- Track your location when you are not actively recording a drive.

---

## Legal Basis

**For users in Australia:** We rely on your consent (when you sign up and choose to upload data) and legitimate interest (crash reporting for app stability). The Australian Privacy Principles (APPs) govern our handling of personal information.

**For users in the EU:** Processing is based on your consent (account creation, uploads) and our legitimate interest (crash reporting, fraud prevention). You have rights under the General Data Protection Regulation (GDPR); see "Your Rights" below.

**For users in California:** We comply with the California Consumer Privacy Act (CCPA). You have the right to request access to, deletion of, and opt-out of the sale of your personal information (though we do not sell data).

---

## Data Sharing and Third-Party Processors

### Sentry (Crash Reporting)
- **What we share:** App crashes, ANRs, app version, device model, OS version, and (for bug reports) the text you submit.
- **Why:** Error monitoring and debugging.
- **Sentry's Privacy:** https://sentry.io/privacy/

### Firebase Cloud Messaging (Push, when enabled)
- **What we share:** Your FCM token (a device registration identifier) and the contents of push messages (follows, likes, comments, announcements).
- **Why:** Delivering real-time notifications.
- **Google's Privacy:** https://policies.google.com/privacy

### Our Server
- **Where:** Self-hosted on a single Google Cloud Platform virtual machine in Australia (region `australia-southeast1`).
- **Data:** Your account (email, username, car, hashed password), uploaded trips and stats, profile picture, social data (follows, likes, comments, notifications), and FCM tokens.
- **Encryption:** All traffic between your device and our server is encrypted using HTTPS (TLS 1.2+).

We do not share this data with any other third parties.

---

## Security

- **In transit:** All communication between your device and the Revline server is encrypted with HTTPS (TLS).
- **At rest:** Passwords are hashed using bcryptjs before storage; they are never stored in plain text.
- **Auth tokens:** Access tokens (short-lived, 15 minutes) and refresh tokens (30 days, hashed and stored in the database) are used for authentication. Tokens are stored in `EncryptedSharedPreferences` on your device (encrypted by the Android Keystore).
- **Profile pictures:** Are re-encoded on-device before upload, removing EXIF/GPS metadata.

---

## Data Retention and Deletion

### Local Data
- Drives recorded on your device without an account stay on your device indefinitely.
- You can delete individual local trips from the History screen in-app.
- Uninstalling the app removes local trip history.

### Server Data (Account-Linked Trips)
- Uploaded trips and associated stats (route, speed, G-force) are stored on the Revline server indefinitely.
- Social data (followers, comments, likes) is retained as long as your account exists.

### Account Deletion
- You can **delete your account** in-app: go to Profile → Account → Delete Account, enter your password, and confirm.
- This action:
  - Removes your account from the server (email, username, hashed password, car).
  - Deletes all trips, comments, and followers associated with your account.
  - Removes your profile picture from the server.
  - Unregisters your device from push notifications.
  - **Does NOT** delete local drive history on your device (local-first design; it stays for your own records).

### Manual Deletion Requests
- To request deletion of your data or obtain a copy of your personal information, email [CONTACT EMAIL] with:
  - Your username or email
  - A description of your request (e.g. "delete my account and all associated data")
- We will respond within 30 days.

### Sentry Retention
- Crash reports and error data are retained by Sentry for 90 days by default (configurable in Sentry's settings). See https://sentry.io/privacy/ for details.

---

## Your Rights

### Access
You have the right to request a copy of your personal data. We can export your account data, trip history, and social interactions on request.

### Correction
You can update your account information (email, username, car, profile picture) directly in the app's Profile screen.

### Deletion
You can delete your account in-app (Profile → Account → Delete Account) or request manual deletion by emailing [CONTACT EMAIL].

### Portability
Trip data can be exported from the app (local Room database) or accessed via your account on the server.

### Withdrawal of Consent
If you withdraw consent for data collection (e.g. you no longer want to upload trips), you can:
- Stop recording drives with the app.
- Sign out of your account (which stops automatic syncing).
- Delete your account (which removes server-side data).

### Right to Object (EU/GDPR)
If you are in the EU, you have the right to object to certain processing. Contact [CONTACT EMAIL] to discuss.

---

## Children's Privacy

Revline is **not directed at children under 13**. We do not knowingly collect personal information from children under 13. If we become aware that a child under 13 has created an account or provided data, we will delete that account and data promptly. If you are a parent or guardian concerned about a child's account, please contact [CONTACT EMAIL].

---

## Changes to This Policy

We may update this policy from time to time. We will post the updated policy here and update the "Last Updated" date. If changes are material, we will notify you by email (if you have provided one) or via an in-app announcement.

---

## Contact Us

For privacy questions, data requests, or complaints:

**Email:** [CONTACT EMAIL]

We will respond to all requests within 30 days.

---

## Jurisdiction and Disputes

This policy and our privacy practices are governed by the laws of **Australia** (South Australia). If you have a complaint about our privacy practices, you may:

1. Contact us directly (see "Contact Us" above).
2. File a complaint with the **Office of the Australian Information Commissioner (OAIC)** at https://www.oaic.gov.au/ if you are in Australia.
3. File a complaint with your local data protection authority if you are in the EU or another jurisdiction with a data protection authority.

---

**End of Privacy Policy**
