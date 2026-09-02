const fs = require("fs");
const path = require("path");

/** The one rules file. Read from the repo root so this can never test a stale copy. */
const RULES_PATH = path.join(__dirname, "..", "..", "database.rules.json");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");
const { ref, set, update, remove, get } = require("firebase/database");

const HOST = "hostUid";
const MEMBER = "memberUid";
const STRANGER = "strangerUid";
const ROOM = "ABC234";

let env;
let passed = 0;
let failed = 0;

async function check(name, promise) {
  try {
    await promise;
    console.log("  ok   " + name);
    passed++;
  } catch (error) {
    console.log("  FAIL " + name + "  --> " + (error && error.message ? error.message.split("\n")[0] : error));
    failed++;
  }
}

/** Seeds data past the rules, the way the app's own history would have created it. */
async function seedRoom() {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.database();
    await set(ref(db, `rooms/${ROOM}`), {
      meta: { hostId: HOST, name: "Listening room", createdAt: 1756000000000 },
      playback: {
        videoId: "vid1", title: "t", artist: "a", thumbnailUrl: "",
        durationMs: 210000, isPlaying: true, positionMs: 1000, updatedAt: 1756000000000,
      },
      members: {
        [HOST]: { username: "merab", joinedAt: 1756000000000 },
        [MEMBER]: { username: "nika", joinedAt: 1756000000001 },
      },
      queue: {
        item_member: { videoId: "v2", title: "x", artist: "y", thumbnailUrl: "", durationMs: 1000, addedBy: MEMBER, addedAt: 1756000000002 },
        item_host: { videoId: "v3", title: "x", artist: "y", thumbnailUrl: "", durationMs: 1000, addedBy: HOST, addedAt: 1756000000003 },
      },
      chat: {
        msg1: { userId: MEMBER, username: "nika", text: "hi", sentAt: 1756000000004 },
      },
    });
  });
}

const asHost = () => env.authenticatedContext(HOST).database();
const asMember = () => env.authenticatedContext(MEMBER).database();
const asStranger = () => env.authenticatedContext(STRANGER).database();
const asAnon = () => env.unauthenticatedContext().database();

const playback = (overrides) => Object.assign({
  videoId: "vid9", title: "t", artist: "a", thumbnailUrl: "",
  durationMs: 200000, isPlaying: true, positionMs: 0, updatedAt: 1756000009000,
}, overrides || {});

const queueItem = (addedBy) => ({
  videoId: "v9", title: "t", artist: "a", thumbnailUrl: "",
  durationMs: 1000, addedBy, addedAt: 1756000009000,
});

const chatMsg = (userId, text) => ({
  userId,
  username: "who",
  text: text === undefined ? "hello" : text,
  sentAt: 1756000009000,
});

async function main() {
  env = await initializeTestEnvironment({
    projectId: "syncbeats-rules-test",
    database: {
      rules: fs.readFileSync(RULES_PATH, "utf8"),
      host: "127.0.0.1",
      port: 9100,
    },
  });

  // ─────────────── creating a room
  await env.clearDatabase();
  console.log("creating a room");
  await check("a signed-in user may create a room naming themselves host",
    assertSucceeds(set(ref(asHost(), `rooms/NEW111/meta`), { hostId: HOST, name: "n", createdAt: 1 })));
  await check("nobody may create a room naming someone else as host",
    assertFails(set(ref(asStranger(), `rooms/NEW222/meta`), { hostId: HOST, name: "n", createdAt: 1 })));
  await check("an anonymous user may not create a room",
    assertFails(set(ref(asAnon(), `rooms/NEW333/meta`), { hostId: STRANGER, name: "n", createdAt: 1 })));

  // ─────────────── hijacking an existing room
  await env.clearDatabase();
  await seedRoom();
  console.log("seizing an existing room");
  await check("a stranger may not overwrite a room's meta (the hijack this closes)",
    assertFails(set(ref(asStranger(), `rooms/${ROOM}/meta`), { hostId: STRANGER, name: "mine now", createdAt: 1 })));
  await check("a stranger may not set themselves as host",
    assertFails(set(ref(asStranger(), `rooms/${ROOM}/meta/hostId`), STRANGER)));
  await check("a member may take control",
    assertSucceeds(set(ref(asMember(), `rooms/${ROOM}/meta/hostId`), MEMBER)));
  await env.clearDatabase(); await seedRoom();
  await check("a member may not hand the room to a third party",
    assertFails(set(ref(asMember(), `rooms/${ROOM}/meta/hostId`), STRANGER)));
  await check("createdAt cannot be rewritten",
    assertFails(update(ref(asHost(), `rooms/${ROOM}/meta`), { createdAt: 999 })));

  // ─────────────── playback
  console.log("playback");
  await check("the host may drive playback",
    assertSucceeds(set(ref(asHost(), `rooms/${ROOM}/playback`), playback())));
  await check("a member who has not taken control may not drive playback",
    assertFails(set(ref(asMember(), `rooms/${ROOM}/playback`), playback())));
  await check("a stranger may not drive playback (previously allowed)",
    assertFails(set(ref(asStranger(), `rooms/${ROOM}/playback`), playback())));
  await check("the host may update just the play flag and position",
    assertSucceeds(update(ref(asHost(), `rooms/${ROOM}/playback`), { isPlaying: false, positionMs: 5000, updatedAt: 2 })));
  await check("a negative position is rejected",
    assertFails(update(ref(asHost(), `rooms/${ROOM}/playback`), { positionMs: -1, updatedAt: 3 })));
  await check("an unknown playback field is rejected",
    assertFails(update(ref(asHost(), `rooms/${ROOM}/playback`), { somethingElse: true })));

  // ─────────────── queue
  console.log("queue");
  await check("a member may add a track stamped with their own id",
    assertSucceeds(set(ref(asMember(), `rooms/${ROOM}/queue/new_a`), queueItem(MEMBER))));
  await check("a member may not add a track attributed to someone else",
    assertFails(set(ref(asMember(), `rooms/${ROOM}/queue/new_b`), queueItem(HOST))));
  await check("a stranger may not add to the queue",
    assertFails(set(ref(asStranger(), `rooms/${ROOM}/queue/new_c`), queueItem(STRANGER))));
  await check("whoever added a track may remove it",
    assertSucceeds(remove(ref(asMember(), `rooms/${ROOM}/queue/item_member`))));
  await check("the host may remove anyone's track (the auto-advance needs this)",
    assertSucceeds(remove(ref(asHost(), `rooms/${ROOM}/queue/new_a`))));
  await env.clearDatabase(); await seedRoom();
  await check("a member may not remove someone else's track",
    assertFails(remove(ref(asMember(), `rooms/${ROOM}/queue/item_host`))));
  await check("an existing queue entry cannot be edited",
    assertFails(update(ref(asMember(), `rooms/${ROOM}/queue/item_member`), { title: "changed" })));

  // ─────────────── chat
  console.log("chat");
  await check("a member may send a message as themselves",
    assertSucceeds(set(ref(asMember(), `rooms/${ROOM}/chat/m2`), chatMsg(MEMBER))));
  await check("nobody may send a message as someone else",
    assertFails(set(ref(asMember(), `rooms/${ROOM}/chat/m3`), chatMsg(HOST))));
  await check("a stranger may not post into a room (previously allowed)",
    assertFails(set(ref(asStranger(), `rooms/${ROOM}/chat/m4`), chatMsg(STRANGER))));
  await check("a sent message cannot be edited",
    assertFails(update(ref(asMember(), `rooms/${ROOM}/chat/msg1`), { text: "rewritten" })));
  await check("an empty message is rejected",
    assertFails(set(ref(asMember(), `rooms/${ROOM}/chat/m5`), chatMsg(MEMBER, ""))));

  // ─────────────── presence
  console.log("presence");
  await check("a user may publish their own presence",
    assertSucceeds(set(ref(asStranger(), `rooms/${ROOM}/members/${STRANGER}`), { username: "s", joinedAt: 1 })));
  await check("a user may not publish presence for someone else",
    assertFails(set(ref(asStranger(), `rooms/${ROOM}/members/${MEMBER}`), { username: "s", joinedAt: 1 })));
  await check("presence without a username is rejected",
    assertFails(set(ref(asHost(), `rooms/${ROOM}/members/${HOST}`), { joinedAt: 1 })));

  // ─────────────── reads
  console.log("reads");
  await check("a signed-in user may look a room up by code",
    assertSucceeds(get(ref(asStranger(), `rooms/${ROOM}/meta`))));
  await check("an anonymous user may not read a room",
    assertFails(get(ref(asAnon(), `rooms/${ROOM}/meta`))));
  await check("nobody may read another user's playlists, history or saved rooms",
    assertFails(get(ref(asStranger(), `users/${HOST}`))));
  await check("a user may read their own profile",
    assertSucceeds(get(ref(asHost(), `users/${HOST}`))));

  await env.cleanup();
  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed === 0 ? 0 : 1);
}

main().catch((error) => { console.error(error); process.exit(1); });
