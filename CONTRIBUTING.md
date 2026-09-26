# Contributing to Fate Locked Ironman

This repository owns the Java RuneLite plugin. The companion web app lives in
the separate
[OSRS-Fate-Locked repository](https://github.com/Nubles/OSRS-Fate-Locked).

## Build and test

Use JDK 11 and Gradle:

```powershell
gradle clean check --no-daemon
```

The standard jar is produced in `build/libs/`. For a local developer-mode
test, copy that jar to RuneLite's `sideloaded-plugins` directory. Do not add a
fat-jar or shading plugin.

## Run it in a real client

```powershell
gradle runClient --no-daemon
```

starts RuneLite in developer mode with the plugin loaded from source through
`FateLockedPluginDevLauncher` (under `src/test`). From an IDE, run that
class's `main` with the VM option `-ea`.

To try it without touching your own RuneLite (its settings, its Hub plugins
and `.runelite/fate-locked`), give the client an empty home folder:

```powershell
$env:GRADLE_USER_HOME = "$HOME\.gradle"
$env:JAVA_TOOL_OPTIONS = "-Duser.home=C:\path\to\empty-folder"
gradle runClient --no-daemon
```

Settings then live in that folder's `.runelite/profiles2`. Set
`fatelocked.trackerNetworkAccess=true` and `fatelocked.trackerPairingCode` in
the profile's `.properties` file, with the client closed, to pair it with a
test code. The plugin's own lines appear in `.runelite/logs/client.log`.

The unit tests run against mocks. Before every Plugin Hub update, run the
[in-game release checklist](docs/in-game-release-checklist.md) on the commit
you are releasing and paste the result into the release pull request.

## Releasing to the Plugin Hub

The Hub builds the commit pinned in
[`plugins/fate-locked-ironman`](https://github.com/runelite/plugin-hub/blob/master/plugins/fate-locked-ironman)
in `runelite/plugin-hub`, not `main`. To release:

1. Merge to `main` with CI green, then run the in-game checklist on that
   commit.
2. Open a pull request on `runelite/plugin-hub` that sets `commit=` in that
   file to the full hash of the commit. If its `repository=` still names
   `RS3-Fate-Locked-Runelite` (which works only through GitHub's rename
   redirect), change it to
   `https://github.com/Nubles/OSRS-Fate-Locked-Runelite.git` in the same
   pull request.
3. Say what players will notice, and point reviewers to
   [the review notes](docs/plugin-hub-review-notes.md) for anything that
   changes what the plugin can block.
4. Once it merges, update the commit named at the top of the review notes.

## Plugin Hub architecture

The shipped plugin constructs one HTTP request:

```text
GET https://fate-relay.fatelocked.workers.dev/r/<32-lowercase-hex-code>
```

The optional `If-None-Match` header allows an unchanged bundle to return
`304`. There is no configurable host, plugin POST, event relay, receipt,
acknowledgement, suggestion write, status callback, or relay write token. Browser
handoffs open only the fixed GitHub Pages tracker URL and contain the random
pairing code, never RuneLite-observed gameplay.

The controller uses RuneLite's injected `OkHttpClient` asynchronously, with a
20-second limit on the whole call and a 1 MiB cap on the reply; stopping the
plugin, withdrawing consent or re-pairing cancels the call in flight. A
separate, default-off `trackerNetworkAccess` setting gates pairing and every
relay check. The Connect action and sidebar toggle show the same third-party
IP-address warning as the native RuneLite config item. Existing pairing codes
and the retired `onlineSync` setting do not imply consent. Revoking access
invalidates pending imports and pauses checks without deleting the pairing.

What each reply means is decided in one pure `RelayContract`; the state and
timing that follow are `SyncMachine`'s, and `SyncView` turns them into the
sidebar's words. Checks come every minute while logged in, every 5 minutes at
the login screen, at once on login, and on **Check now** (at most every 10
seconds); a failure retries within 5 minutes, and a `Retry-After` is honoured
as given, from 30 seconds to an hour. Relay rules are parsed on the thread
that read the reply, strictly as complete non-legacy v4, and switched to on
the client thread. Malformed payloads, compressed payloads that inflate past
8 MiB (a real bundle is about 1.3 MiB, and about 200 KiB compressed),
incompatible versions, ETag/body disagreement, stale callbacks, stopped
sessions and offline checks retain the previous valid rules.

## Threads

Plugin state changes only on the client thread, through the one
`ClientThreadGate` each start opens; Swing, the executor and HTTP callbacks
hand their work to it. A `PluginSession` token makes work queued before the
plugin was turned off do nothing afterwards. Bundles are parsed off the game
thread, and every local file write runs in order on RuneLite's executor
through `SerialFileWriter`.

## Local files

Each OSRS account's files live in `accounts/<account hash>/` in the data
directory: `event-history.json` (the newest 250 detected events),
`strict-mode-events.json` (the newest 100 Strict Mode audit entries),
`slayer-assignment.json` and `diary-tiers.json` (the diary tiers the account
has finished). They open when the account logs in; until then its detections
are dropped rather than written into another account's files. A file that
fails to open leaves its feature off for that account and never stops the
plugin.

Every write goes through `LocalFileMerge`: under an exclusive lock on a
`<file>.lock` sidecar it re-reads the file, merges this client's change into
it, writes a temp file of its own, flushes it to the disk and moves it into
place, so two RuneLites sharing a data folder keep each other's changes. The
in-memory state changes only after the write succeeds. A damaged file is
renamed with a `.corrupt-<millis>` suffix and a fresh one starts; it is never
written over.

An account's folder starts, once, from the shared files earlier versions
kept (`event-history.json`, or the older `event-outbox.json` queue, and the
shared audit log and Slayer task). The shared files are only read: the
history gives only that character's events, and the audit log and Slayer
task, which name no account, come along only for the character the rules are
bound to.

Detectors record facts only, through one `DetectionGate`: rules for a run are
loaded, bound to the logged-in character, on a world that saves to that
account. They never roll, mutate the tracker, or transfer the local history
to the web Roll Inbox.

## Bundle and rule ownership

The current network import accepts only complete non-legacy v4 bundles.
Clipboard and file recovery retain compatibility parsing, but Unknown is
never promoted to Locked.

The rules in force are one `ActiveRules` value: a bundle and where it came
from. `RulesPrecedence` decides which arrivals replace it: the saved rules and
the startup backup file only fill an empty slot, while the relay and an
explicit import always apply. New rules are worked out completely before the
switch, and a failed sidebar update does not undo them. The last accepted
rules are kept compressed in `saved-rules.json`, with their source, save time,
relay version and a tag of the pairing that sent them (never the code), and
come back at the next start, even offline.

The app-authored rules manifest carries run, account, and revision identity,
unlock families, bank state, and category-first chunk permissions. Guardian
logic consumes only these authored decisions; it must not invent a Locked
decision from missing or ambiguous data.

## Contracts with the web app

The web app writes golden bundles, with its own answers for every chunk,
area and bank, and the bundle cases an import must refuse or shrug off
(`contracts/golden-bundles/`), and the relay's replies to the plugin's request
with the outcome of each (`contracts/relay/relay-get.json`).
`scripts/pin-web-contracts.sh <web commit>` copies them into
`src/test/resources/contracts/` and records the commit in `PINNED`; CI runs it
with `--check` and fails if the copy differs. `GoldenBundleContractTest`,
`GoldenBundleCasesTest`, `RelayContractFixtureTest`,
`RelayTransportFixtureTest` and `RelayFixtureStatesTest` check the plugin
against them.

## Strict Mode invariant

Keep Strict Mode under the sole `strictMode` setting. The plugin consumes a
game click in exactly one place, `StrictModeClickHandler.handleTravel`, and
only when all of these are true:

- Strict Mode is enabled and not paused.
- The rules are current, valid, non-legacy, and fresh: relay rules the
  tracker confirmed in the last 15 minutes, or file and clipboard rules
  exported in the last 15 minutes.
- The rules name a bound account, and it matches the logged-in character.
- The click is travel recognised with exact confidence to one destination.
- The authored destination decision is Locked.

Walking, NPC, object, bank and equipment clicks are never consumed.
`PluginHubClickBoundaryTest` pins the single consume site and that no menu
entry is removed, reordered or created. Allowed, Unknown, stale, unbound,
wrong-character, missing, invalid, future, ambiguous, and unresolved inputs
fail open. Stage the four-second explanation and bounded local audit entry
before consuming the player's click. Never click, activate, select, reorder,
remove, path to, or perform an alternative.

Strict Mode requires RuneLite reviewer pre-clearance; contributors must not
describe it as approved.

## Automated compliance gates

The source boundary:

```powershell
gradle test --no-daemon --tests com.fatelocked.PluginHubNetworkBoundaryTest --tests com.fatelocked.UnifiedPluginContractTest
```

The clean source, test, and standard-jar gate:

```powershell
gradle clean check --no-daemon
```

`PluginHubNetworkBoundaryTest` proves that production source has one request
builder, one descriptor, one navigation button, the fixed relay path, and no
prohibited runtime mechanism. `verifyPluginHubJar` rejects shaded dependency
trees, legacy relay routes, retired relay classes, local hosts, and request
body support.

All dependencies remain `compileOnly` for production. Keep the plugin Java
only. Do not add reflection, JNI, subprocesses, dynamic class loading, an
embedded server, vendored runtime code, or arbitrary filesystem access.

## Review references

Before submission, compare the candidate with RuneLite's
[Plugin Hub review process](https://github.com/runelite/plugin-hub#reviewing),
[rejected or rolled-back features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features),
and [third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1).
The candidate-specific explanation is in
[docs/plugin-hub-review-notes.md](docs/plugin-hub-review-notes.md).
