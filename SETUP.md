# SyncBeats — Setup

The repo builds without any Firebase credentials (CI proves that on every push), but the app
needs a Firebase project to run: Firebase Auth for accounts and the Realtime Database for
rooms. This is the one-time setup; after it, `./gradlew :composeApp:installDebug` is all
there is.

To try the app without a project of your own, skip to
[No project at all: the Firebase emulators](#no-project-at-all-the-firebase-emulators).

## 1. Create a Firebase project

1. Open <https://console.firebase.google.com> and click **Add project**. Analytics is not
   needed.
2. **Add app → Android**.
   - Package name: `com.example.myapplicationmusicsharing`
     (Yes, that differs from the `io.github.meko123456.syncbeats` package in the source. The
     applicationId is what Firebase keys on, so it changes only once the new one is
     registered there — see issue #3.)
   - Leave the SHA-1 blank unless you are setting up Google sign-in (#6).
3. Download **`google-services.json`** into `composeApp/` (next to
   `composeApp/build.gradle.kts`). It is git-ignored; never commit it. The build applies the
   Google Services plugin only when the file exists, which is why a fresh clone still
   compiles.
4. For iOS, add an iOS app to the same project and put `GoogleService-Info.plist` in
   `iosApp/iosApp/`. The iOS target does not link yet (#13), so this can wait.

## 2. Enable Authentication

**Build → Authentication → Get started → Sign-in method → Email/Password → Enable.**

Accounts are created from the app's **Create account** tab (username, email, password).
**Continue with Google** needs the OAuth setup described under Phase 2 in the README (#6);
without it the button reports an error and email/password still works.

## 3. Create the Realtime Database and deploy the rules

1. **Build → Realtime Database → Create database.** Any location; start in **locked mode**.
2. Deploy the rules from the repo rather than pasting anything into the console:

   ```sh
   npm install -g firebase-tools                    # once
   firebase login
   firebase deploy --only database --project <your-project-id>
   ```

   `firebase.json` points at `database.rules.json`, so that is the whole command. The rules
   are what stands between a room and anyone with an account: a profile is readable only by
   its owner, a room can be created only by naming yourself host, and only a member can take
   control. They are tested against the Firebase emulator in CI (`tools/rules-tests`) — if
   you change them, run `npm test` there first.

Until the rules are deployed a new database allows nothing at all, so every room action in
the app fails with a permission error. That is the symptom to look for if you skipped this
step.

## 4. Build and install

```sh
./gradlew :composeApp:installDebug      # straight to a connected device or emulator
./gradlew :composeApp:assembleDebug     # APK in composeApp/build/outputs/apk/debug/
```

## 5. First run with two people

1. Both create an account on the **Create account** tab.
2. One opens **Rooms → Start a room**, optionally names it, taps **Create room**, and reads
   out the 6-character code from the room's top bar. Codes skip 0/O/1/I/L so they are easy
   to say aloud.
3. The other types the code under **Join a room**. Either person searches for a track and
   presses play: both hear the same position, and a device that drifts more than 400 ms is
   re-seeked (checked every 3 s).
4. Rooms you create are bookmarked under **My rooms** automatically; a room you joined can be
   bookmarked from its top bar. Codes never expire, so a bookmarked room is a one-tap rejoin.

## No project at all: the Firebase emulators

The Auth and Realtime Database emulators can stand in for a project, with the repo's rules
loaded. There is nothing to sign up for, and nothing the app does leaves the computer.

```sh
(cd tools/rules-tests && npm ci)    # once; the firebase-tools the rules tests use
tools/rules-tests/node_modules/.bin/firebase emulators:start --project demo-syncbeats --only auth,database
```

The database emulator runs on Java, 11 or later. Leave it running, and in another terminal:

```sh
./gradlew :composeApp:installDebug -Psyncbeats.firebaseEmulatorHost=10.0.2.2
```

- `10.0.2.2` is the computer as an Android emulator sees it. For a phone on USB, forward the
  ports with `adb reverse tcp:9099 tcp:9099` and `adb reverse tcp:9100 tcp:9100`, then use
  `-Psyncbeats.firebaseEmulatorHost=localhost`.
- That build leaves `google-services.json` out even when it is there, and gives the app a
  made-up `demo-syncbeats` project, so it cannot reach a real one. Build without the flag to
  go back to your own project.
- Accounts and rooms live in the emulators and are gone when they stop. Create accounts in
  the app as usual. **Continue with Google** needs a real project and reports an error.

## Notes

- Stream URLs resolved from YouTube expire after a few hours. If a track that sat paused for
  a long time will not resume, skip it and play it again.
- NewPipe Extractor (Android) and the InnerTube client (iOS) both talk to unofficial YouTube
  internals. When YouTube changes them, bump `newpipe` in `gradle/libs.versions.toml` or the
  client versions in `InnerTubeMusicSource.kt`.
