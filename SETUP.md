# SyncBeats — Setup

Private app for 3 friends. This file walks through the one-time Firebase setup
so the APK actually runs once you build it.

## 1. Create a Firebase project

1. Open https://console.firebase.google.com and click **Add project**.
2. Name it whatever (`syncbeats`). Disable Analytics — we don't need it.
3. In the project, click **Add app → Android**.
   - Package name: `com.example.myapplicationmusicsharing`
   - App nickname: anything (e.g. `SyncBeats`)
   - Leave the SHA-1 field blank (we don't use Google sign-in).
4. Download **`google-services.json`** and drop it into `app/`
   (next to `app/build.gradle.kts`). Do NOT commit it.

## 2. Enable Authentication

1. In the Firebase console, go to **Build → Authentication → Get started**.
2. Under **Sign-in method**, enable **Email/Password**.

## 3. Enable Realtime Database

1. **Build → Realtime Database → Create database**.
2. Pick a location near you. Start in **Test mode** for the very first launch
   (we'll tighten the rules in step 5 once we know everyone's UIDs).

## 4. First launch on a phone

1. Build the app (Android Studio → Run, or `./gradlew assembleDebug`).
2. Open it on phone #1 → tap **Alice** on the auth screen.
   This creates the Firebase user `user1@syncbeats.local` with password `syncbeats1`.
3. Same on phone #2 (**Bob**) and phone #3 (**Charlie**).

The seed accounts are defined in `Constants.kt`:

```
user1@syncbeats.local  syncbeats1  Alice
user2@syncbeats.local  syncbeats2  Bob
user3@syncbeats.local  syncbeats3  Charlie
```

Change those before first run if you want real emails / stronger passwords.

## 5. Lock down the Realtime Database

After the 3 sign-ins, go to **Authentication → Users** and copy each UID.
Then in **Realtime Database → Rules** paste:

```json
{
  "rules": {
    ".read":  "auth != null && (auth.uid === 'UID_ALICE' || auth.uid === 'UID_BOB' || auth.uid === 'UID_CHARLIE')",
    ".write": "auth != null && (auth.uid === 'UID_ALICE' || auth.uid === 'UID_BOB' || auth.uid === 'UID_CHARLIE')"
  }
}
```

Replace the three placeholders with the real UIDs and publish. Done.

## 6. Build the APK

```
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Sideload to your three phones.

## Notes

- Single room ID: `the-crew` (set in `Constants.kt`). All three phones auto-join.
- Anyone can tap **Take control** to become the host. The host's play/pause/seek/skip
  is authoritative; the other two phones drift-correct against it every 3s.
- Stream URLs from NewPipeExtractor expire after ~6h — if a song pauses for hours
  and won't resume, just skip and play it again.
- `usesCleartextTraffic="true"` is set in the manifest only because some YouTube
  CDN endpoints occasionally return non-HTTPS URLs. Strip it later if you'd rather not.

## Deploying the database rules

The rules in `database.rules.json` are not applied by anything in the app — they have to be
deployed, and until they are, the database is running on whatever rules were last pushed:

```sh
firebase deploy --only database
```

`firebase.json` in the repo root points at the rules file, so no arguments are needed. The rules
are covered by tests (`tools/rules-tests`), which CI runs on every push.
