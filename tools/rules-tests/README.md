# Realtime Database rules tests

The rules in [`../../database.rules.json`](../../database.rules.json) are the only thing standing
between a room and anyone on the internet with an account, so they are tested rather than reasoned
about.

```sh
cd tools/rules-tests
npm ci
npm test
```

That starts the Firebase database emulator against the real rules file — not a copy, so it cannot
test something stale — and asserts each write and read that should be allowed and each that should
not. Java is required, because the emulator is a Java process.

## Why it exists

Run against the rules as they stood before the tests were written, **17 of the 33 assertions
fail**. Among them: a stranger could overwrite any room's `meta` and make themselves host, drive
any room's playback, post in any room's chat, and read every other user's playlists, history and
saved rooms.

## Deploying

Tightened rules do nothing until they are deployed:

```sh
firebase deploy --only database
```

`firebase.json` in the repo root points at the rules file, so that command needs no arguments.
