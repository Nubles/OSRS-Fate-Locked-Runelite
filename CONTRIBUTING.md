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

The controller uses RuneLite's injected `OkHttpClient` asynchronously. A
relay result is dispatched to the client thread and replaces the current
rules only after complete parsing, strict v4 validation, and panel refresh
succeed. Malformed payloads, incompatible versions, ETag/body disagreement,
stale callbacks, stopped sessions, offline requests, and failed UI refreshes
retain the previous valid snapshot.

## Local event history

Detected events are local observations, not network messages. The history
file has the shape:

```json
{
  "events": []
}
```

`FateEventHistory` keeps the newest 250 unique event IDs. Writes use a sibling
temporary file and atomic replace where supported. The in-memory list changes
only after persistence succeeds.

When the new history is absent, the newest 250 unique `pending` entries from
the former local queue are migrated once. The legacy bytes are never modified
or deleted. If the new history is malformed, it is renamed with a
`.corrupt-<millis>` suffix and a fresh history starts. The panel exposes a
local save-failure state and clears it after a later successful write.

Detectors record facts only. They never roll, mutate the tracker, or transfer
the local history to the web Roll Inbox.

## Bundle and rule ownership

The current network import accepts only complete non-legacy v4 bundles.
Clipboard and file recovery retain compatibility parsing, but Unknown is
never promoted to Locked.

The app-authored rules manifest carries run, account, and revision identity,
unlock families, bank state, and category-first chunk permissions. Guardian
logic consumes only these authored decisions; it must not invent a Locked
decision from missing or ambiguous data.

## Strict Mode invariant

Keep Travel Guardian under the sole `strictMode` setting. A click may be
consumed only when all of these are true:

- Strict Mode is enabled and not paused.
- The rules are current, valid, non-legacy, and fresh.
- The rules belong to the logged-in, correctly bound account.
- The selected action and destination are recognised with exact confidence.
- The authored destination decision is Locked.

Allowed, Unknown, stale, wrong-account, missing, invalid, future, ambiguous,
same-chunk, and unresolved inputs fail open. Stage the four-second
explanation and bounded local audit entry before consuming the player's
click. Never click, activate, select, reorder, remove, path to, or perform an
alternative.

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
