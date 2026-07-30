# Inbound-Only Plugin Hub Design

**Status:** Approved in conversation on 2026-07-28
**Applies to:** `feature/unified-plugin-hub` at and after `e95d4aa`
**Companion app:** `OSRS-Fate-Locked` GitHub Pages application
**Relay:** `https://fate-relay.fatelocked.workers.dev`

## Context

The unified plugin currently has a complete one-click pairing flow, a single
RuneLite sidebar, and the Strict Travel Guardian. It also sends RuneLite-
observed account and gameplay progression to the relay through `/events`,
`/acks`, and `/suggest`, and confirms imports through `/state`.

RuneLite's current rejected-features guidance says that plugins which expose
player information over HTTP are not being considered for the Plugin Hub:

- https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features
- https://github.com/runelite/plugin-hub#reviewing

The event payload includes the logged-in account and gameplay-derived skill,
quest, diary, boss, raid, Slayer, Collection Log, clue, pet, minigame, and
combat-achievement data. Explicit connection consent, a fixed HTTPS endpoint,
and pairing-scoped tokens do not create a documented exception.

This addendum changes the Hub candidate to an inbound-only connection. It
preserves one-click pairing and automatic companion-to-RuneLite rule updates,
while keeping RuneLite-detected progress on the local machine.

## Goals

1. Preserve the one-click **Connect tracker** experience.
2. Keep automatic companion-app-to-RuneLite v4 bundle delivery.
3. Ensure the plugin sends no RuneLite-observed player or gameplay data over
   HTTP.
4. Preserve useful local event detection and Roll Inbox reminders.
5. Keep the single sidebar, all 30 retained settings, and the complete Travel
   Guardian behavior.
6. Make the network boundary obvious to Plugin Hub reviewers and users.
7. Keep legacy relay routes temporarily available for already-installed older
   builds without making the new app or plugin depend on them.

## Non-Goals

- Automatic RuneLite-to-companion progress synchronisation.
- A localhost bridge, subprocess, browser extension, JNI integration, or
  dynamically downloaded code.
- Encoding player data in URL fragments or another transport to evade the
  Plugin Hub rule.
- Replacing the GitHub Pages companion app.
- Claiming that Strict Mode click prevention is pre-approved by RuneLite.

Automatic outbound progress may be reconsidered only after RuneLite reviewers
approve a specific transport. A browser-authorised local-file workflow is a
possible future design, not part of this release.

## Architecture

### Pairing and inbound data flow

1. RuneLite generates a fresh 32-character lowercase hexadecimal pairing code.
2. RuneLite stores the code internally and opens:
   `https://nubles.github.io/OSRS-Fate-Locked/#runelite-pair=<code>`.
3. The user confirms the run in the companion app.
4. The companion app publishes the run's v4 rules bundle to the fixed relay.
5. RuneLite polls only `GET /r/<code>` with the accepted ETag when available.
6. RuneLite parses and validates the complete v4 bundle before replacing the
   active rules.
7. RuneLite locally publishes `Connected` only after a successful import.

The plugin does not POST a connection receipt. The companion app therefore
does not claim that RuneLite is connected. Its onboarding copy instructs the
user to return to RuneLite and confirm the **Connected** state there.

### Removed plugin-to-relay flows

The Hub candidate must not call:

- `POST /r/<code>/state`;
- `POST /r/<code>/events`;
- `GET /r/<code>/acks`;
- `GET /r/<code>/suggest`; or
- `POST /r/<code>/suggest`.

The new plugin contains no event-relay client, relay write-token storage,
event flush schedule, acknowledgement handling, or suggestion HTTP client.
On startup and after re-pairing, migration removes every Fate Locked config
entry whose key starts with `eventToken.`, `stateToken.`, `suggestToken.`, or
`ackToken.`. The pairing code is retained because it is the read scope for the
bundle GET, not a write credential. Any legacy token field received in a relay
envelope or bundle is ignored and is never persisted.

### Exhaustive plugin-process network boundary

The only HTTP request the shipped RuneLite plugin may construct is:

`GET https://fate-relay.fatelocked.workers.dev/r/<32-lowercase-hex-code>`

It may add only the standard `If-None-Match` request header. Production code
has no injectable or user-configurable host. Test code may substitute a local
server for deterministic controller tests, but that seam is not exposed by the
shipped plugin.

Opening the fixed GitHub Pages tracker and Roll Inbox URLs delegates navigation
to the user's browser; those browser handoffs contain no RuneLite-observed
player or gameplay data.

### Connection controller

`TrackerConnectionController` remains responsible for:

- one active bundle poll;
- pairing generation and code isolation;
- canonical ETag/body-version agreement;
- strictly increasing accepted versions;
- matching 304 freshness updates;
- client-thread bundle import;
- stale callback invalidation;
- local connection snapshots; and
- retryable browser-launch failure.

It no longer posts a state acknowledgement or persists a state write token.
Successful import atomically updates only its local accepted version, last
sync time, and `Connected` snapshot.

## Local Event History

### Storage

The relay outbox is replaced by a clearly local `FateEventHistory` component.
It stores the newest 250 detected events in:

`<RuneLite data directory>/fate-locked/event-history.json`

Rules:

- deduplicate by stable event ID;
- append a newly detected event;
- when a 251st event is accepted, discard the oldest event first;
- persist with the existing temporary-file plus atomic-move pattern;
- recover a corrupt file by moving it aside with a timestamped `.corrupt-*`
  name; and
- never contain relay tokens, sent states, or acknowledgement state.

### Migration

If `event-history.json` does not exist but `event-outbox.json` does, load up to
the newest 250 entries from the old file's `pending` array and create the new
history. Leave the legacy file untouched so migration is non-destructive.

### Detection boundary

Local detection runs when:

- a valid bundle is loaded;
- the bundle has a non-blank run ID;
- the logged-in account is available; and
- the detected event is non-null.

Pairing is not required. Clipboard- and file-imported bundles therefore retain
local history and reminders. Local event storage never changes the active
bundle, connection state, or Guardian decision.

## Sidebar

The approved seven-section order and expansion defaults remain unchanged.

The **Roll inbox** section changes to:

- `Local events` — number of retained local events;
- `Needs review` — local events with uncertain confidence;
- `Warnings` — the existing active warning count;
- disclosure: `Local only — RuneLite does not upload gameplay data.`; and
- **Open web Roll Inbox** — opens the separate companion view with
  `?open=roll-inbox`, without a pairing code or local event data, and explicitly
  states in its tooltip that local history is not transferred.

The connection area remains above the sections:

- **Open web tracker**;
- **Connect tracker**;
- local connection status;
- tracker account from the imported bundle; and
- last successful import time.

The network disclosure becomes:

`RuneLite retrieves rules from the Fate Locked relay. Your IP address is
visible to the relay, but RuneLite does not upload gameplay data.`

## Companion App and Relay

### Companion onboarding

The companion consumes the pairing fragment, confirms the run, and publishes
the bundle as before. It stops polling `/state` to determine whether RuneLite
imported the bundle.

Successful onboarding copy becomes:

`Profile sent. Return to RuneLite; its Fate Locked panel will show Connected
after the first valid import.`

No app state may describe the plugin as connected based only on a successful
bundle upload.

### Legacy server routes

The relay may retain `/state`, `/events`, `/acks`, and `/suggest` temporarily
for compatibility with installed older clients. They are:

- not called by the Hub candidate;
- not required by the new companion onboarding;
- documented as legacy compatibility routes; and
- removable after the older client compatibility window.

## Failure and Concurrency Behavior

- A network failure publishes `Offline` locally and keeps the previous rules.
- A 404 publishes `Expired` and keeps the previous rules.
- Invalid, malformed, stale, equal, or mismatched bundles never replace the
  active rules.
- Re-pairing invalidates every callback belonging to the previous pairing.
- A stopped controller cannot commit queued client-thread work.
- Local-history updates are copy-on-write: persist the bounded candidate
  history atomically before publishing it as current.
- A failed local-history write leaves the previous durable history and displayed
  event counts unchanged. The Roll Inbox shows the non-counted status
  `Local history save failed` and the plugin writes a warning to its log. The
  status clears after the next successful history write.
- A local-history write failure does not affect connection state, active rules,
  the Roll Inbox `Warnings` count, or any Guardian decision.
- A corrupt local history is preserved under a recovery filename and starts a
  new empty history.
- No reminder, suggestion, alternative, or local event performs a gameplay
  action.

## Strict Mode Review Position

Strict Mode remains optional and conservative:

- only exact, fresh, account-bound, known locked actions can be consumed;
- Allowed, Unknown, stale, malformed, wrong-account, missing-destination,
  ambiguous, and ordinary walking cases fail open;
- the overlay explains the block and may show a verified alternative;
- alternatives are never activated automatically; and
- the user can pause the Guardian for 60 seconds.

Click cancellation is behaviorally adjacent to RuneLite's concerns about
conditional menu-entry removal. Release documentation must request reviewer
pre-clearance and must not claim guaranteed Plugin Hub acceptance.

## Testing

### Plugin unit and integration tests

Add or update tests proving:

1. pairing still opens the production companion URL;
2. the controller performs bundle GETs and no state POST;
3. success updates local version/time/state after a client-thread import;
4. stale/replaced callbacks cannot commit;
5. production source contains no event/ack/suggestion/state relay client;
6. local history deduplicates, rolls at 250, persists, recovers corruption, and
   migrates legacy pending events without deleting the old file;
7. local detection works with paired, clipboard, and file bundles;
8. Roll Inbox uses the exact local-only copy and labels;
9. all 30 settings and seven sections remain unchanged;
10. failed local-history persistence preserves the previous durable history,
    shows the non-counted local failure status, clears it after a later success,
    and leaves rules, connection state, warning count, and Guardian decisions
    unchanged;
11. all legacy relay-token config keys are removed and legacy response token
    fields are ignored; and
12. the complete Travel Guardian regression matrix remains green.

### Companion tests

Verify:

1. pairing confirmation still publishes the v4 bundle;
2. onboarding no longer polls `/state`;
3. successful upload directs the user to verify RuneLite's local Connected
   state;
4. failure remains retryable; and
5. existing non-pairing companion behavior is unchanged.

### Static compliance

The release gate searches production plugin source and inspects the built jar
to prove that its only HTTP request construction is the fixed-host bundle GET.
It rejects every other HTTP method, host, and path. In particular, it searches
for:

- `/events`, `/acks`, `/suggest`, and `/state`;
- player-data POST construction;
- `RequestBody`, `.post(`, `.put(`, `.patch(`, and `.delete(`;
- `FateEventRelayClient`;
- event, state, suggestion, and acknowledgement token reads, writes, accessors,
  or request fields (the token-prefix literals are permitted only in the
  one-way deletion migration);
- reflection, JNI, subprocesses, dynamic class loading, and local servers;
- `localhost` and `127.0.0.1`; and
- more than one `@PluginDescriptor`.

An instrumented controller test also records every request and permits only
`GET /r/<32-lowercase-hex-code>` with the optional `If-None-Match` header.

Expected result: exactly one production HTTP request construction, matching the
fixed relay GET contract; none of the forbidden/outbound items; and exactly one
descriptor.

### Build and manual validation

- Run the full plugin test suite and Guardian matrix.
- Build and inspect the standard non-shaded jar.
- Run companion type-check and tests.
- Sideload the jar on the same PC.
- Confirm one non-blank sidebar and seven independent sections.
- Confirm Connect opens the GitHub Pages app and a published bundle becomes
  Connected in RuneLite.
- Confirm no `/state`, `/events`, `/acks`, or `/suggest` requests originate
  from RuneLite.
- Confirm local events appear in Roll Inbox without appearing in the web app.
- Confirm Guardian block/fail-open/pause behavior.

## Acceptance Criteria

The design is complete when:

- one-click pairing and inbound automatic rules work;
- RuneLite sends no observed player/gameplay data over HTTP;
- the fixed relay bundle GET is the only HTTP request constructed by the
  shipped plugin;
- all legacy write/acknowledgement tokens are removed or ignored;
- local event history remains useful, bounded, and explicitly local;
- the companion no longer depends on a plugin heartbeat;
- the standard jar contains no shaded dependencies or prohibited runtime
  mechanisms;
- all plugin, companion, and Guardian tests pass;
- the same-PC manual matrix passes; and
- release notes disclose that Strict Mode requires reviewer pre-clearance.
