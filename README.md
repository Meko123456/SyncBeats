# SyncBeats — listen together

[![CI](https://github.com/Meko123456/SyncBeats/actions/workflows/ci.yml/badge.svg)](https://github.com/Meko123456/SyncBeats/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

A Compose Multiplatform app (Android + iOS) where two or more people listen to
the same music at the same time.

## Project layout

Ten Gradle modules on Now-in-Android-style convention plugins. Every module is Kotlin
Multiplatform (Android, `iosArm64`, `iosSimulatorArm64`) so nothing can quietly exclude iOS.

- `build-logic/` — the convention plugins. `syncbeats.kmp.library` sets the targets, SDK
  levels and JVM 17 once; `syncbeats.kmp.library.compose` adds the Compose runtime; and
  `syncbeats.kmp.feature` makes a screen module out of a one-line build file (domain +
  design system + Material 3, lifecycle, Koin, and `:core:testing` for its tests).
- `core/model` — plain data: `AuthUser`, rooms and members, `PlaybackState`, `RoomCode`,
  and the drift constants.
- `core/domain` — the ports every screen talks to (`AuthGateway`, `RoomRepository`,
  `MusicSource`, `PlayerController`, …) and the sync engine (`SyncEngine`, `DriftMath`,
  `ListenerStatus`). No Firebase, no Ktor, no platform code — which is why its tests run on
  an iOS simulator in CI, not just the JVM.
- `core/data` — the adapters behind those ports: Firebase Auth and Realtime Database through
  the GitLive SDK, NewPipe Extractor (Android) and a small InnerTube client (iOS) as music
  sources, the Media3 `PlaybackService` (Android) and an AVPlayer controller (iOS), plus the
  Koin data module that binds them.
- `core/designsystem` — theme, shared error copy, the platform back handler.
- `core/testing` — in-memory fakes of the ports, so a ViewModel test needs neither Firebase
  nor a device.
- `feature/auth`, `feature/lobby`, `feature/home`, `feature/room` — one MVI module per
  screen: a `Contract` (single state + sealed intents), a `ViewModel`, a `Screen`, and a Koin
  module. A feature depends on `:core:domain` and never on `:core:data`, so a screen cannot
  reach Firebase or a player even by accident.
- `composeApp/` — the app shell: `SyncBeatsApp` assembles the Koin graph, `AppRoot` hosts
  navigation, and `MainActivity` / `MainViewController` are the two entry points. Also
  builds the iOS framework.
- `iosApp/` — Xcode project (regenerate with `xcodegen generate` after editing `project.yml`).
  The iOS targets compile in CI but cannot link yet — see #13.
- `tools/rules-tests/` — the Realtime Database security rules, run against the Firebase
  emulator on every push.

## Building and testing

```sh
./gradlew :composeApp:installDebug                 # Android, to a connected device or emulator
./gradlew testDebugUnitTest                        # every module's JVM tests
./gradlew :core:domain:iosSimulatorArm64Test       # the same domain tests, on an iOS simulator
./gradlew :core:data:connectedDebugAndroidTest \
          :composeApp:connectedDebugAndroidTest    # playback + Koin-graph tests, needs a device
(cd tools/rules-tests && npm ci && npm test)       # the database rules, on the Firebase emulator
```

iOS: open `iosApp/iosApp.xcodeproj` in Xcode and run, or
`xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
(blocked on #13 until the Firebase iOS SDK is integrated).

Firebase config lives at `composeApp/google-services.json` (Android) and
`iosApp/iosApp/GoogleService-Info.plist` (iOS) — both from the same Firebase project, both
git-ignored. [SETUP.md](SETUP.md) walks through creating them.

Users sign up, create a listening room (or join one with a 6-character code), search
YouTube for tracks, and everyone in the room hears the same track at the same position.

## How it works

- **Firebase Realtime Database** is the sync backbone — no custom server. The host
  writes the shared `PlaybackState` (`videoId`, `positionMs`, `isPlaying`, `updatedAt`
  server timestamp) under `rooms/{code}/playback`; every device observes it.
- **`SyncEngine`** computes where the track should be right now
  (`positionMs + (serverNow − updatedAt)`), corrects device clocks via Firebase's
  `.info/serverTimeOffset`, and re-seeks if a device drifts more than 400 ms.
  When a track ends, the host auto-advances to the next queued item.
- **Music sources**: Android resolves YouTube audio via NewPipe Extractor;
  iOS uses a small built-in client for YouTube's InnerTube API (same underlying
  API, pure Kotlin). Both are unofficial — when YouTube changes internals, bump
  `newpipe` in the version catalog (Android) or the client versions in
  `InnerTubeMusicSource.kt` (iOS).
- **Players**: Media3/ExoPlayer in a foreground `MediaSessionService` on Android
  (playback survives backgrounding); AVPlayer with the `audio` background mode
  on iOS.
- Rooms also have presence (live member list), a shared queue, chat, and
  "take control" so any member can become host.
- The **Home tab** shows recently played tracks, your imported YouTube playlists
  (paste a public/unlisted playlist link once; it's saved to your profile), and
  trending music. Anything there can start a fresh room ("Play in room"), and
  inside a room the library button queues playlists/history for everyone.
- **Saved rooms**: rooms you create from the Rooms tab are bookmarked
  automatically under the name you gave them (e.g. Family, Friends, Work), and
  any room you join can be bookmarked with the ★ icon in its top bar. Saved
  rooms appear in "My rooms" for one-tap rejoining — codes never expire.

## One-time setup (required before the app can run)

The project builds without Firebase credentials, but it needs them at runtime.

1. Go to <https://console.firebase.google.com> and create a project.
2. Add an **Android app** with package name `com.example.myapplicationmusicsharing`.
   (Yes, that differs from the `io.github.meko123456.syncbeats` package in the source. The
   applicationId is what Firebase keys on, so it changes only once the new one is
   registered there — see issue #3.)
3. Download `google-services.json` and put it in the `composeApp/` directory.
4. In **Build → Authentication → Sign-in method**, enable **Email/Password**.
5. In **Build → Realtime Database**, create a database, then open the **Rules** tab
   and paste the contents of [`database.rules.json`](database.rules.json).

Then build and install as usual (Android Studio, or `./gradlew :composeApp:installDebug`).

## Trying it with two people

1. On device A: create an account, tap **Create room**, and share the code shown
   in the room's top bar.
2. On device B: create another account, enter the code, tap **Join**.
3. Either person can search and queue tracks; the host controls play/pause/seek/skip,
   and anyone can tap **Take control** to become host.

## Roadmap — Phase 2: connect your Google/YouTube account

Not built yet; planned design so it isn't forgotten:

1. **Sign in with Google** replacing (or alongside) email/password — one tap gets
   both the Firebase identity and a YouTube API token. Setup: enable the Google
   provider in Firebase Auth, add the SHA-1 to the Firebase project, enable the
   **YouTube Data API v3** in the same Google Cloud project, and configure the
   OAuth consent screen (keep it in "testing" mode for personal use — up to 100
   test users, no Google review).
2. With the `youtube.readonly` scope, add Home rails for **private playlists**,
   **Liked songs**, and **new uploads from subscriptions** via the Data API
   (playback still resolves through NewPipe).
3. Not possible even then: YouTube's algorithmic "My Mix"/recommendations —
   Google exposes no API for them.
4. Watch the API quota (10k units/day free): list endpoints cost 1 unit,
   search costs 100 — prefer playlist/subscription list calls.

## Caveats

- Resolved YouTube stream URLs expire after a few hours and NewPipe extraction can
  break when YouTube changes internals — bump the `newpipe` version in
  `gradle/libs.versions.toml` when that happens. This approach sits in a gray area
  of YouTube's terms of service; treat this as a personal/hobby project.
- The security rules require sign-in and room membership conventions but any
  authenticated user who knows a room code can write to that room's playback/queue —
  fine for friends, tighten before anything public.