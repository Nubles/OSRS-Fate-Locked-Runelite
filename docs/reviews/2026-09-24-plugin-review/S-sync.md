# S: Tracker connection, relay sync and imports

> Area report from the 2026-09-24 plugin review. The consolidated report is [`../2026-09-24-plugin-review.md`](../2026-09-24-plugin-review.md). Harness names refer to a throwaway harness that is not in this repository. Web commit `2f1697c` has the same tree as web `main` at `9617a16`. Corrections made while consolidating are marked **[Corrected]**.

Plugin `osrs-fate-locked-runelite@bda88c8`. Web, worker and docs read at `OSRS-Fate-Locked@2f1697c` (branch `claude/quirky-archimedes-aponk4`; the merge commit 9617a16 isn't in this clone). Read-only review: I changed nothing in either repository.

Effort scale: S = under a day, M = 1–3 days, L = more than 3 days.

## Summary

- The protocol core is sound. Pairing codes are validated. Stale-callback invalidation, consent gating and the Hub network boundary all hold. The version jump from PR #45 (counter to seconds since 2026, now about 23,025,600) imports correctly. The existing 37 connection tests pass against the real sources, and the worker suite passes 36 of 36.
- The weak point is the system around the core. Rules the relay delivers live only in memory, and the relay record expires 24 h after the last web publish. After a restart past that point, or while offline, the plugin has **no rules** and shows "Pairing request expired" (S1).
- The "Reload from file" button and the Auto-reload toggle replace relay rules with a file, or with an empty bundle, while the panel still says Connected (S2).
- The player often sees states that are wrong, conflated or silent:
  - A new pairing reads red "Pairing request expired" until the browser publishes (S3).
  - Unusable responses change nothing on screen (S5).
  - A same-version 200 counts as a failure (S4).
  - There is no "Check now" button (S6).
  - Pressing Connect on a working pairing discards it (S7).
- Proposed direction: persist rules in one `RulesStore`, drive the connection from a pure `SyncMachine` reducer whose states carry reasons and actions, and pin the relay contract with fixtures shared by the worker and the plugin.

## Findings

### S1: Rules delivered by the relay are never persisted, so a restart after the 24 h TTL or while offline leaves no rules
- **Severity:** high. **Status:** traced (restart path); reproduced (404-after-expiry behaviour, in the harness and the worker probe).
- **Location:**
  - `FateLockedPlugin.java:1446-1486`: `acceptRelayPayload` only assigns the `bundle` field.
  - `FateLockedPlugin.java:448`, `:1313-1326`: startup loads the newest `fate-locked-bundle-*.json` or `FateLockedBundle.empty()`.
  - `TrackerConnectionController.java:36-40`: `acceptedVersion` and `lastSync` are in-memory only.
  - `workers/fate-relay/worker.js:14,232-233`: `TTL_SECONDS = 86400`.
  - `FateLockedPlugin.java:1193-1198`: freshness.
- **What goes wrong:**
  1. Monday evening the player rolls in the web app.
  2. On Tuesday evening they launch RuneLite without opening the web app first. The record expired 24 h after Monday's publish.
  3. The first GET returns 404, and the sidebar shows red **"Pairing request expired"**.
  4. The active bundle is `FateLockedBundle.empty()`. There are no chunk overlays, no locked warnings, and no menu tags.
  5. Strict Mode silently fails open, because `trackerLastSync()` is null.

  Nothing tells them to open the web tracker, although opening it republishes: `OnlineSyncDriver.tsx:99` schedules a publish on mount. The same happens on any start without internet. Clipboard and paste imports are also lost on every restart, because only dropped files survive. A web tab that stays open but idle for over 24 h also lets the record expire, because the driver has no keep-alive.
- **Evidence:**
  - `grep` finds no bundle write in plugin source. The only `Files.write` calls are the event history, the audit log and the Slayer state.
  - Worker probe: `"GET after the 24h data record expired" → 404 {}`.
  - Harness `[expiry] 404 -> EXPIRED "Pairing request expired" (rules kept, lastSync frozen)`. Rules are kept only within the same session.
- **Fix:**
  - **Plugin:** add a `RulesStore` that writes the last accepted bundle plus metadata atomically to `fate-locked/active-rules.json`: code, relay version, source, runId/runRevision, receivedAt, verifiedAt. Use the same temp-file plus atomic-move pattern as `FateEventHistory`.
    - At startup, load it as "saved rules from <time>", not fresh for Strict Mode until verified.
    - Send its version as `If-None-Match`, so a restart doesn't re-download the full profile.
    - Treat 404 as "no recent publish", not as expiry of the pairing.
  - **Web (optional):** while a lease-owner tab is open, republish when the last publish is more than 12 h old.
  - **Worker (owner decision):** a longer TTL (7 days), weighed against retention.
- **Effort:** M.

### S2: "Reload from file" and the Auto-reload toggle replace relay rules with a file bundle or an empty one; the panel keeps saying Connected
- **Severity:** high. **Status:** traced.
- **Location:**
  - `FateLockedPlugin.java:1313-1326`: with no file, `bundle = FateLockedBundle.empty()`.
  - `:433`: the button wiring.
  - `:497-502`: any `autoReload` change calls `reloadBundle()`, both ON and OFF.
  - `FateLockedPanel.java:457-460`: the "Reload from file" button.
  - `TrackerConnectionController.java:350-391`: the next 304 keeps the state Connected.
- **What goes wrong:**
  1. A connected player has no bundle file.
  2. They open Bundle and press **Reload from file**, hoping to pull a roll they just made. There is no refresh control (S6).
  3. All rules vanish: every chunk becomes unauthored, warnings stop, and Strict Mode fails open.
  4. The controller still holds `acceptedVersion`. The next poll gets 304, so the header stays green **"Connected · 18:02:11 UTC"**.
  5. Rules return only when the web app publishes a new version.

  Toggling "Auto-reload on change" does the same. If a stale `fate-locked-bundle-*.json` exists, the reload applies that file instead of the relay rules. The file watcher also does this whenever the file changes (`:1716-1733`).
- **Evidence:** traced from code (above). Without RuneLite jars, `FateLockedPlugin` can't run here.
- **Fix:**
  - **Plugin (stopgap):** never replace the rules when no file exists. Don't reload on an `autoReload` change. While paired, require confirmation for a file reload.
  - **Plugin (structural):** one rules owner with explicit sources and precedence (see the design below).
- **Effort:** S (stopgap), M (with `RulesStore`).

### S3: A new pairing reads red "Pairing request expired" until the browser publishes, then waits out a backoff
- **Severity:** medium. This is the first thing every new user sees. **Status:** reproduced.
- **Location:**
  - `TrackerConnectionController.java:82`: pairing resets the schedule, so the next 4 s tick polls.
  - `:276-292`: 404 maps to EXPIRED, with 5 s doubling to a 60 s cap.
  - `worker.js:149`: an unknown code returns `404 {}`.
  - `TrackerConnectionControllerTest.java:790-813` pins EXPIRED after `beginPairing()` plus 404.
- **What goes wrong:**
  1. The player clicks Connect. The first GET goes out within 4 s, before the browser has even opened, and the panel turns red: "Pairing request expired".
  2. The web dialog then says *"Return to RuneLite; its Fate Locked panel will show Connected"*. The panel stays red for up to about 60 s after the browser publishes.

  Inference: many users will press Connect again. That mints a new code, and the web warns it will "replace the current RuneLite connection", which becomes a loop. The README's step 4 ("verify … Connected") and the manual matrix ("Waiting to Connected") don't describe what the player actually sees. The matrix marks "Offline and 404 status" as Blocked.
- **Evidence (harness):**

  ```
  [fresh pairing] browser publishes at t=45s
  t=0s GET -> EXPIRED "Pairing request expired"
  t=8s GET -> EXPIRED …   t=20s … t=40s …
  t=80s GET -> CONNECTED "Connected"   (import landed 35 s after the browser published)
  ```

  A publish at t=81 s would land at t≈140 s. Unverified: Cloudflare KV edge caching of the negative lookup could add up to about 60 s more.
- **Fix (plugin):**
  - Before the first successful import of a new code, treat a 404 as **AwaitingBrowser**: amber "Confirm the profile in your browser", with a Reopen browser button.
  - Poll every 5 s for 2 min, then every 15 s. After 10 min, show "No profile received: press Connect tracker again".
  - Update the pinned test.
- **Effort:** S.

### S4: The version and ETag contract is fragile: same-version 200s count as failures, older versions are dropped silently, and the validator survives a 404
- **Severity:** medium. The first two are latent today, but the natural "RFC fix" on the worker would trigger them. **Status:** reproduced.
- **Location:**
  - `TrackerConnectionController.java:523-542`: `acceptableVersion` returns null for equal or older versions, which leads to `scheduleFailure` at `:319-327`.
  - `:276-289`: after a 404, the controller keeps sending the old validator.
  - `worker.js:150`: exact string compare, `If-None-Match === String(version)`.
  - `worker.js:153,159`: the ETag is emitted unquoted.
  - `components/StreamOverlay.tsx:177-181`: the overlay now drops its ETag on 404; the plugin doesn't.
- **What goes wrong:**
  - The plugin strips quotes and `W/` before echoing the validator. The worker matches only the bare number, which gives 304. The worker probe shows a quoted or weak `If-None-Match` gets a **200 with the same version**.
  - The plugin treats that as a failure: no state change, `lastSync` frozen, cadence decaying to 15 min.
  - If the worker ever emits RFC-quoted ETags, or anything between them normalises the header, every connected player hits this. The panel stays green "Connected" while Strict Mode freshness silently lapses.
  - An older version is also dropped silently. Today that only happens on a stale KV edge read. After any server-side regression (an epoch change, a worker rollback or a KV restore), the plugin would stick to old rules until restart.
- **Evidence:**
  - Harness `[equal-version 200] waits (s): [0, 32, 60, 120, 240, 480]; state CONNECTED, lastSync frozen for 16 min (Strict Mode freshness window is 15 min); next poll in 900 s`.
  - Harness `[older version] 23025599 after 23025600: no snapshot, lastSync frozen, next poll in 30 s (failure backoff)`.
  - Probe: quoted and weak validators return 200.
- **Fix:**
  - **Plugin:** treat a 200 whose version equals the held one as not-modified (refresh verifiedAt, healthy cadence). On 404, drop the validator and accept the next valid version, as the overlay does. Surface "relay returned an older profile" after N repeats.
  - **Worker (coordinated, plugin first):** compare leniently by stripping `W/` and quotes, then emit `"<n>"`.
  - **Both:** a shared contract-fixture test (see the design).
- **Effort:** S (plugin) and S (worker).

### S5: Unusable 200 responses change nothing on screen while backoff climbs to 15 min
- **Severity:** medium. **Status:** reproduced.
- **Location:** `TrackerConnectionController.java:310-327` (null envelope or payload, bad version, ETag/body mismatch) and `:342-347` (any exception). All of these call `scheduleFailure` without publishing a state.
- **What goes wrong:** a proxy or captive portal returns an HTML 200, or a relay bug drops `payload`. The panel keeps its previous text, "Waiting for tracker" or "Connected · <old time>", and polling slows to 15 min with no explanation and no log line.
- **Evidence:** harness `[unusable bodies] waits before each poll (s): [0, 32, 60, 120, 240]; still showing 'Waiting for tracker' after ~7 min`. The five bodies were HTML, a missing payload, a string version, an out-of-range version and a missing version. No snapshot was published.
- **Fix (plugin):** every outcome yields a visible, reason-coded state, for example "Relay sent an unreadable response · retrying in 2 min", plus one debug or warn log line per distinct cause.
- **Effort:** S.

### S6: No "Check now"; the failure backoff cap equals the Strict Mode freshness window; Retry-After is scaled; polling continues when logged out
- **Severity:** medium. **Status:** reproduced (backoff); traced (the rest).
- **Location:**
  - `TrackerConnectionController.java:17-20,295-305,490-505`.
  - `FateLockedPlugin.java:1193-1198` (15-min freshness) and `:1744-1751` (4 s tick with no game-state check).
  - `OnlineSyncDriver.tsx:7-10`.
- **What goes wrong:**
  - After a relay blip of about 10 min, the backoff reaches 480 s and then 900 s. Once the relay recovers, the plugin can wait 15 min to notice, and meanwhile Strict Mode treats the rules as stale.
  - After a roll in the web app, there is no way to pull it: the web coalesces for 5–60 s, then the plugin polls every 60 s. Typical delay is about 35 s and the worst case about 2 min.
  - A 429's Retry-After is multiplied by the exponential step, and a Retry-After above 900 s is truncated.
  - Polling runs every 60 s at the login screen, cost about 1,440 GETs per install per day. Inference: that is a real share of Cloudflare's free tier if the relay runs on it (unverified).
- **Evidence:**
  - Harness `[5xx] waits before each poll (s): [0, 32, 60, 120, 240, 480, 900, 900]`.
  - Harness `[429] Retry-After [120, 120, 120, 3600] -> waits before polls (s): [0, 120, 240, 480], then 900 s`.
- **Fix (plugin):**
  - Add a **Check now** button (optionally with a hotkey) that resets backoff, limited to one press every 10 s.
  - Cap failure backoff at about 5 min, with ±20 % jitter.
  - Honour Retry-After exactly, bounded to 1 h.
  - Poll immediately on `LOGGED_IN`, and every 5 min when not logged in.
- **Effort:** S.

### S7: Pressing "Connect tracker" on a working pairing discards it before the browser confirms; paired players with consent off are steered to re-pair
- **Severity:** medium. **Status:** traced; the existing test confirms each click mints a new code.
- **Location:**
  - `FateLockedPlugin.java:1613-1639`: no confirmation once consent exists.
  - `TrackerConnectionController.java:67-89`: `replacePairingCode` runs immediately.
  - `FateLockedPanel.java:74,119-121`: the label never changes.
  - `FateLockedPluginStartupContractTest.java:161-190`.
  - Design drift: `docs/superpowers/specs/2026-07-27-unified-plugin-hub-design.md:54-56` planned the labels `Connecting…`, `Connected` and `Reconnect tracker`.
- **What goes wrong:**
  - A player who is connected, or who sees the misleading red state from S1 or S3, presses Connect. The old code is overwritten at once. If they close the web dialog or pick the wrong profile, RuneLite is stuck on a dead code, showing "Pairing request expired", and the old code, which still worked, is gone.
  - After the consent update, players who were already paired see only grey "Not connected". The prominent button re-pairs them. The way to resume the existing pairing, the "Enable online sync" checkbox, is in the collapsed Bundle section.
- **Fix (plugin):**
  - Label the button by state: "Connect tracker", "Re-pair…" or "Turn on online sync".
  - Confirm before replacing a working pairing.
  - Keep the previous code as the active pairing until the new one imports. Restore it on cancel or timeout.
- **Effort:** M.

### S8: Connection states are conflated and not actionable
- **Severity:** medium. **Status:** reproduced (the messages); traced (the panel).
- **Location:** `TrackerConnectionSnapshot.java:44-65`, `TrackerConnectionController.java:653-695`, `FateLockedPanel.java:516-556`.
- **What goes wrong:**
  - **EXPIRED** ("Pairing request expired", red) covers three situations: not yet published (S3), no web publish for 24 h (S1), and the web pressed Disconnect (S11).
  - **IMPORT_FAILED** ("Could not import tracker data") is the same text for a corrupt payload and for a future bundle format. For a future format the player should be told to update the plugin.
  - **DISCONNECTED** ("Not connected") is the same for "consent off, pairing stored" and "never paired".
  - **CONNECTED** stays green when the logged-in account isn't the bound one (S10), and when manual or file rules have overridden the relay (S2, S9).
  - Times are shown as `HH:mm:ss UTC`, not local time.
  - The chunk card's "Synced Nm ago" is recomputed only when the player changes chunk.
  - No state shows when the next retry is.
- **Evidence:**
  - Harness `[future format] state IMPORT_FAILED "Could not import tracker data"; importer error: Unsupported future bundle version 5`.
  - Harness `[consent off] … message "Not connected" while a pairing code is stored`.
- **Fix (plugin):** reason codes mapped to copy and one primary action; see the UI column in the design below.
- **Effort:** S for copy alone; M as part of the state machine.

### S9: Manual imports accept non-bundles as valid empty bundles, override relay rules without checks, and run on different threads
- **Severity:** medium. **Status:** reproduced (parser); traced (paths).
- **Location:**
  - `FateLockedBundle.java:371-384`: `raw == null || raw.chunks == null` returns `empty()` instead of throwing.
  - `FateLockedPlugin.java:1404-1418`: accepts that with a green "imported 0 regions".
  - `:430-434`: panel paste and clipboard-button imports run on the **EDT**.
  - `:1386`: the hotkey runs on the client thread.
  - `FateLockedBundle.java:322`: `new String(Files.readAllBytes(path))` uses the platform charset.
- **What goes wrong:**
  - Pasting `{}` or other non-bundle JSON, or the watcher catching a zero-byte file mid-copy, wipes the rules and reports success.
  - Pasting yesterday's clipboard bundle over a fresh relay bundle is accepted with no runId or runRevision comparison. The header still says Connected with the relay's time.
  - The EDT import path calls client APIs through `refreshPanel()` off the client thread. That is cross-area; see the lifecycle review.
- **Evidence:** harness `[manual parser] '{}' -> version 0, regions 0, legacy true`. The same holds for `''`, `{"version":3}` and `{"profile":"x"}`. No test pins this.
- **Fix (plugin):**
  - Reject bundles without chunks, and v4 without rules, on every path.
  - Warn when importing an older runRevision of the same runId, or a different runId.
  - Send all imports through one client-thread entry point.
  - Read files as UTF-8.
  - Persist manual imports (S1) and label the active source.
- **Effort:** S.

### S10: The connection ignores which account the rules are for; a mismatch shows only as a once-per-login chat line
- **Severity:** low. The Warnings and Strict Mode parts are cross-area. **Status:** traced.
- **Location:**
  - `FateLockedPlugin.java:1015-1043`: the chat warning reads legacy `state.linkedAccount` only.
  - `:736-761`: two different binding rules. Generic checks treat an unbound account as a match; travel checks require a bound account.
  - `:1277-1311`: locked-entry sound and notification with no account check.
  - `FateLockedPanel.java:516-550`.
  - Web `services/fateEventProtocol.ts:55-57` collapses whitespace; plugin `FateLockedPlugin.java:1006-1009` uses `Text.sanitize`.
- **What goes wrong:**
  - A player who paired their ironman's web profile logs into their main. Every locked chunk they cross plays the "death squelch" and fires a notification, while the header shows green Connected.
  - If a future web release drops the v3 root `state` block, the mismatch chat warning stops silently, because it doesn't read `rules.account`.
  - Unverified (wiki blocked): OSRS may treat `_`, `-` and space as the same in names; neither side normalises them.
- **Fix (plugin):**
  - Show account status in the connection header: "Profile is for X; you're logged in as Y".
  - Use `rules.account`, falling back to `state.linkedAccount`, everywhere.
  - Silence locked-entry alerts on a non-bound account (Warnings owner).
  - Option: store the pairing and cached rules per RS profile, so switching accounts switches rules.
- **Effort:** S–M.

### S11: Revocation and privacy gaps: web Disconnect doesn't remove the published profile, the dialog shows the full code, and the disclosure understates what the relay can link
- **Severity:** low. **Status:** traced.
- **Location:**
  - `services/relaySync.ts:142-149`: `disable()` is local only.
  - `worker.js:134-240`: no delete or tombstone route.
  - `components/RunelitePairingDialog.tsx:85-90`: the full 32-hex code is on screen.
  - `docs/online-relay.md:159-164`.
  - `FateLockedPanel.java:136-139`.
  - `TrackerConnectionSettings.java:12,26-32`: the code, a bearer read credential, lives in global RuneLite config.
- **What goes wrong:**
  - After **Disconnect**, the relay keeps serving the last profile, including the linked RSN, to anyone with the code for up to 24 h. RuneLite keeps importing it, then shows "Pairing request expired", never "disconnected".
  - A streamer confirming a pairing on stream shows the read code.
  - The relay can join the requester IP (with RuneLite's UA and version) to the RSN in the stored profile, and can see client uptime through 60 s polling. The disclosure mentions only the IP address.
  - Unverified: whether RuneLite profile sync or export copies the code off the machine.
- **Fix:**
  - **Worker and web:** add a token-authorised tombstone on Disconnect, so GET returns `410 Gone`. The plugin shows "Disconnected in the web tracker". This is still GET-only, so the Hub boundary is unchanged.
  - **Web:** show only the code's last 4 characters.
  - **Plugin and docs:** widen the disclosure one sentence.
- **Effort:** M.

### S12: HTTP and threading hygiene
- **Severity:** low. **Status:** reproduced (slow body); verified in RuneLite source (executor, client); traced (the rest).
- **Location:**
  - `TrackerConnectionController.java:158-178`: the `Call` isn't kept.
  - `:227-237`: `stop()` doesn't cancel it.
  - `:310-311`: `body().string()` has no cap.
  - `FateLockedPlugin.java:386,1446-1456`: FLGZ inflate plus Gson parse run on the **client thread**.
  - `:1749-1750`.
  - `FateLockedPanel.java:558-568`.
- **What goes wrong:**
  - The injected client has OkHttp's default 10 s connect, read and write timeouts and **no call timeout**. This is `RuneLite.buildHttpClient` in RuneLite master. A slow-dripping body holds the only poll slot indefinitely.
  - RuneLite's shared executor wraps tasks in `RunnableExceptionLogger`, which **rethrows**. Any exception escaping `pollIfDue` cancels sync for the rest of the session, silently. No such throw path was found today.
  - Parsing a real bundle on the game thread can cause a frame hitch. **[Corrected]** A real bundle is about 1.3 MiB,
    not 120 KiB, and a cold parse takes about 180 ms (R13).
  - When the EDT calls `networkAccessChanged()` synchronously (checkbox toggle), `runOnEdt` applies the new snapshot at once. A snapshot an OkHttp thread queued earlier can land after it and show "Connected" for up to 4 s after sync was disabled.
- **Evidence:** harness `[slow body] poll held the single-flight slot for 4968 ms with a 300 ms read timeout (no callTimeout)`.
- **Fix (plugin):**
  - `okHttpClient.newBuilder().callTimeout(20, SECONDS)`.
  - Keep the `Call` and cancel it on stop, revoke and re-pair.
  - Cap the body at 1 MiB.
  - Parse off-thread and only swap on the client thread.
  - Wrap the scheduled task in try/catch.
  - Send UI updates through one serialized path.
- **Effort:** S.

### S13: Dead or noisy paths
- **Severity:** low. **Status:** reproduced (the last three); verified in RuneLite source (the second).
- **Items:**
  - The `PREPARING` state is never published (`TrackerConnectionState.java:6`).
  - The "Could not open the web tracker" path is unreachable in production (`TrackerConnectionController.java:91-95`, `FateLockedPlugin.java:1648-1663`). RuneLite's `LinkBrowser.browse` throws only for non-http(s) schemes. Failures happen on its own thread and show RuneLite's copy-link dialog. The path is tested only through an override.
  - With consent off, every 4 s tick republishes DISCONNECTED and bumps the generation (`:97-103,191-197,213-225`). Harness: 5 ticks produced 5 snapshots.
  - A 304 whose ETag doesn't match clears the poll without scheduling, so the next poll comes 5 s later (`:356-361`; harness `next poll in 5 s`). This is theoretical with today's worker.
  - A rejected payload is downloaded again on every backoff step with no validator (`:423-431`). Harness: 6 full downloads in 15 min, then about 96 a day.
- **Fix (plugin):**
  - Remove the dead state and the dead path.
  - Publish only on change.
  - Schedule after every outcome.
  - Remember a rejected version and send it as the validator.
- **Effort:** S.

## Verified correct

The existing tests ran on javac `--release 11` builds of the real sources, with harness stand-ins and Mockito 4.11. The harness and probe files were in `S-harness/` (not committed).

- **PR #45 version contract:**
  - A jump from 57 to 23,025,600 imports, and the next request sends `If-None-Match: 23025600`.
  - A 304 carrying the unquoted worker ETag refreshes `lastSync`.
  - Republish after expiry (23,112,005) is accepted.
  - ETag parsing accepts `n`, `"n"` and `W/"n"`. It rejects leading zeros, zero, non-digits and values above `Integer.MAX_VALUE`, and requires the ETag to equal the body version. With no ETag it falls back to the body version.
  - The int limit is reached only in 2094.
  - Method: the harness `[version jump]` and `[expiry]` scenarios, plus `TrackerConnectionControllerTest`'s version cases.
- **Worker behaviour the plugin relies on:**
  - An unknown or expired code returns 404 with body `{}`.
  - 200 returns `{version,payload}` with `ETag == String(version)`.
  - 304 is returned only for an exact validator.
  - A KV failure returns a 503 with CORS headers.
  - Every response carries `no-store`.
  - A write in the same second gets `stored + 1`.
  - GET never returns the token.
  - Method: `npx vitest run workers/fate-relay` passed 36 of 36; `worker-probe/probe.mjs` drives a copy of `worker.js`.
- **Stale callbacks and single flight:**
  - Generation, token, code and consent checks guard every commit.
  - `stop()`, re-pairing and revocation discard queued client-thread work.
  - Only one GET is in flight.
  - Method: all 29 `TrackerConnectionControllerTest` cases pass, as do `TrackerConnectionSettingsTest` (6) and `PairingSupportTest` (2).
- **Consent:**
  - `trackerNetworkAccess` defaults to false.
  - The Connect button, the sidebar checkbox and the config item's `warning` all ask before enabling. RuneLite's `ConfigPanel.changeConfiguration` shows the warning on every change, including turning it off; that is standard RuneLite behaviour.
  - Declining leaves the pairing untouched.
  - A stored code or the old `onlineSync` flag makes no request.
  - Revoking consent blocks polls and discards pending imports.
  - Method: code, existing tests, and RuneLite `ConfigPanel.java`.
- **Nothing is uploaded:**
  - One GET to a fixed host, `/r/<code>`, with no body.
  - The only header the plugin adds is `If-None-Match`. RuneLite adds `User-Agent: RuneLite/<ver>-<commit>`, and there is no cookie jar.
  - Legacy token config keys are deleted at startup and on re-pair, and token fields in responses are ignored (`RelayEnvelope` has only version and payload).
  - Method: source, the boundary test, and RuneLite `RuneLite.java` / `RuneLiteModule.java`.
- **Threads:**
  - OkHttp callbacks run on dispatcher threads.
  - The import and the 304 commit reach the client thread through `ClientThread.invoke`, which queues them.
  - Panel updates are marshalled to the EDT.
  - The injected client's "no blocking calls on client thread/EDT" interceptor is satisfied, because calls are enqueued.
  - RuneLite's disk cache doesn't interfere: responses are `no-store`, and a conditional request bypasses the cache.
  - Method: RuneLite source, plus OkHttp's documented cache semantics.
- **Code validation:**
  - `UUID.randomUUID()` gives 122 random bits.
  - `[0-9a-f]{32}` is enforced in the plugin (read and write) and in the web.
  - The URL is built only from a validated code.
  - Pasting a pairing code into the import box gets "use Connect tracker".
- **Web side:**
  - Only complete profiles are published (`requireRulesData`).
  - One publish is in flight at a time.
  - Only the save-lease tab publishes.
  - The pairing is bound to one profile.
  - Coalescing is 5 s quiet, 60 s maximum, and immediate when the tab is hidden.
  - Pairing and Retry publish at once.
  - A web tab that isn't the owner shows why.

## Structural notes for the overhaul

### What makes this area hard to change

1. **Two owners of "the active rules".**
   - `FateLockedPlugin.bundle` and `rulesImportedAt` have four writers: `reloadBundle`, `applyPastedBundle`, `acceptRelayPayload` and `shutDown`, on three threads.
   - The controller separately owns `acceptedVersion` and `lastSync`.
   - Strict Mode freshness switches between the two through `trackerPaired()`, which reads config.
   - Nothing records which source is active. This is the root of S1, S2 and S9.
2. **Concurrency by re-checking.**
   - One lock, a generation counter, token identity, `acceptedStateUnchanged`, and config reads *inside* the lock (`isPollCurrentLocked` reads the consent flag and the code from `ConfigManager`), re-checked at 3–4 points per path.
   - Scheduling decisions are spread across 6 call sites.
   - Listeners run under the lock, and so do config writes, which post `ConfigChanged` to every plugin.
   - Four threads touch the state: the scheduler, OkHttp, the client thread and the EDT.
3. **States are an enum plus free English text built in the controller.** The panel maps enum to colour. There are no reason codes and no next-retry time, and tests assert the English strings.
4. **Tests are coupled to internals.**
   - They reflect into private plugin methods and fields (`FateLockedRelayImportTest`), use real sockets plus `Thread.sleep`, and restate the relay contract three times: in Java tests, in the worker's `expectImportable`, and in the docs.
   - `PluginHubNetworkBoundaryTest` pins the exact source text `TrackerConnectionSettings.RELAY_BASE_URL + "/r/" + token.code`.
   - `verifyPluginHubJar` scans only `TrackerConnectionController*.class` for forbidden routes. If the request code moves to another class, that gate **passes vacuously**, so it has to move with the code.
5. **Three manual import mechanisms** (clipboard button plus hotkey, paste box, file watcher plus Auto-reload plus Reload) with different threads and validation, none of them persisted.

### Current behaviour map (per relay outcome)

| Outcome | State shown | Rules | Next check |
|---|---|---|---|
| 404, never imported this session | EXPIRED "Pairing request expired" (red) | unchanged (empty after restart) | 5, 10, 20, 40, then 60 s |
| 404, holding a version | EXPIRED (red); `lastSync` frozen | kept (memory only) | 60 s, validator kept |
| 304, validator matches | CONNECTED; `lastSync` = now | kept | 60 s |
| 304, validator mismatch | none | kept | 5 s |
| 200, higher version, import ok | IMPORTING, then CONNECTED | replaced | 60 s |
| 200, higher version, import fails | IMPORT_FAILED (red) | kept | 30 s, doubling to 15 min, re-downloaded each time |
| 200, equal or older version, ETag/body mismatch, malformed or no payload | **none** | kept | 30 s, doubling to 15 min |
| 429 | OFFLINE "busy" (grey) | kept | max(30, Retry-After) doubled per step, capped at 15 min |
| 5xx or other non-2xx | OFFLINE "Tracker is unavailable" | kept | 30 s, doubling to 15 min |
| Network error or 10 s read timeout | OFFLINE "Could not reach tracker" | kept | 30 s, doubling to 15 min |
| Slow body | none (slot held) | kept | no deadline |

### Proposed target design

Components, all plain Java 11 and testable without RuneLite:

- **`RelayContract`** (pure): `parse(status, headers, body) → RelayResult`. The result is one of `Fresh(v, payload)`, `Same(v)` (304, or 200 with the held version), `Older(v)`, `NotFound`, `Gone` (410), `RateLimited(retryAfter)`, `ServerError(code)`, `NetworkError`, `Malformed(reason)`.
- **`RelayClient`** (transport): the Hub's one GET. It uses `callTimeout`, caps the body at 1 MiB, is cancellable, and has no policy. The boundary test and jar gate target this class.
- **`RulesStore`** (the single rules owner):
  - Holds `ActiveRules{bundle, source ∈ RELAY|CLIPBOARD|FILE|SAVED, code, relayVersion, runId, runRevision, receivedAt, verifiedAt}`.
  - `offer(candidate, source) → Accepted | Rejected(reason)` applies validation and precedence.
  - Persists atomically, and exposes `freshness()` to Strict Mode.
- **`SyncMachine`** (pure reducer): `(SyncState, Event, now) → (SyncState, Effects)`.
  - Events: `ConsentChanged`, `ConnectClicked`, `RepairConfirmed`, `CancelPairing`, `CodeChangedExternally`, `Tick`, `CheckNow`, `FetchDone(RelayResult, requestId)`, `ImportDone(ok, reason)`, `LoginChanged(account)`, `Shutdown`.
  - Effects: `Fetch(code, validator)`, `Cancel`, `Import(parsed)`, `ScheduleAt(t)`, `OpenBrowser(url)`, `Render(SyncView)`.
  - A stale result is simply a `requestId` that isn't the one in flight. That replaces the lock, generation and token scheme.
- **`SyncRuntime`**: a single-threaded actor that runs effects. The import swap happens on the client thread, and `SyncView` goes to the EDT in order.

States, and what the sidebar shows (each has one primary action):

| State | Header (tone) | Detail and action | Polling |
|---|---|---|---|
| `Off(NO_CONSENT, paired?)` | "Online sync is off" (grey) | Paired: **Turn on sync** (resumes the pairing). Not paired: **Connect tracker** | none |
| `Unpaired` | "Not connected" (grey) | **Connect tracker** | none |
| `AwaitingBrowser(code, since, previous?)` | "Confirm the profile in your browser" (amber) | **Reopen browser** · Cancel (restores `previous`) | 5 s for 2 min, then 15 s; 404 is expected |
| `PairingTimedOut` | "No profile received" (amber) | **Try again** (restores `previous` if one exists) | none |
| `Synced(v, verifiedAt)` | "Up to date · checked 18:03" (green, local time) | **Check now** | 60 s logged in, 5 min logged out |
| `Degraded(cause, since, nextAt)` | "Using saved rules from Mon 21:03" (amber) | Cause line with an action: offline or relay error or busy ("retrying in 2 min", **Check now**); `NotFound` ("No update from the tracker for 24 h", **Open web tracker**); `Gone` ("Disconnected in the web tracker", **Connect tracker**) | backoff 30 s × 2ⁿ, cap 5 min, jitter; Retry-After exact |
| `Rejected(reason, v)` | "Tracker sent rules this plugin can't use" (red) | "Update the plugin (bundle v5)" or "Retry from the tracker"; keeps current rules; the rejected `v` becomes the validator | 60 s |
| `Manual(source, at)` | "Using rules imported from clipboard at 12:05" (blue) | Sync off: **Connect tracker**. Sync on: "until the next tracker update" | as the underlying state |

Badges shown alongside the state: account (`Match` / `Mismatch(bound, current)` in amber / `Unbound`, with a note that Strict Mode can't block) and rules age.

Key transitions:

- **Connect:**
  - From `Unpaired` or `Off`, after consent, `Connect` → `AwaitingBrowser(new, previous=current)` plus `OpenBrowser`.
  - `Fresh` → `Synced`, which commits the new code and drops `previous`.
  - A timeout or Cancel → the previous state, with the previous code.
- **Revalidation:** from `Synced`:
  - `Same` → `Synced(verifiedAt = now)`.
  - `Fresh` → import → `Synced` or `Rejected`.
  - `NotFound` → `Degraded(NO_RECENT_PUBLISH)`, dropping the validator.
  - Errors → `Degraded(cause)`.
  - Repeated `Older` → `Degraded(RELAY_REGRESSED)`, shown to the player.
- **Recovery:** `Degraded` + `Fresh` or `Same` → `Synced`. `CheckNow` (at most every 10 s) → immediate fetch.
- **Consent revoked:** from any state → `Off` + `Cancel`.
- **Startup:** load `RulesStore`, then `Degraded(STARTING, "Using saved rules from …")`, then an immediate fetch with the saved validator.
- **Freshness:** `verifiedAt` comes only from `Same` or `Fresh` in `Synced`, so the Strict Mode policy is unchanged.

Seams for tests:

- Table-driven reducer tests with a fake clock: no sockets, no sleeps, no reflection.
- `RelayContract` fixture tests. JSON fixtures (status, headers, body) live next to the worker. The worker suite asserts it produces them; the plugin vendors them, with a checksum, and asserts each one's `RelayResult`. That makes the contract one artefact instead of three restatements.
- `RulesStore` tests in a temp directory: persist, load, corrupt-file recovery, precedence, and rejection of non-bundles.
- A few `SyncRuntime` integration tests against MockWebServer.
- Panel tests render `SyncView`s.

Manual import verdict:

- **Keep the clipboard path** (button plus hotkey). It is the only path for players who decline network consent, and the recovery path during relay outages. Route it through `RulesStore` so it persists and validates.
- **Drop the paste box**, which duplicates the clipboard button.
- **Drop the 1 s file watcher, Auto-reload and "Reload from file"**, or replace them with a one-shot "Import newest bundle file". The web downloads to the browser's Downloads folder, not `~/.runelite/fate-locked/`, so the file path needs manual moves. It silently competes with the relay (S2). The web still claims a "Downloads auto-detect" (`utils/runeliteExport.ts:145`). Owner decision, since some players may drop files.

Staging:

1. **Quick fixes (S):** S2 stopgap, S3, S4 plugin half, S5, S6, S12, S13, S8 copy.
2. **Persistence and validation (M):** `RulesStore` (S1, S9).
3. **State machine (M–L):** reducer, `SyncView`, account badge, re-pair rollback (S7, S10), contract fixtures.
4. **Web and worker (M):** tombstone and 410 (S11), lenient ETag compare then quoting (S4), optional keep-alive or longer TTL, masked code, remove the web Roll Inbox "Listening" label (`components/RollInbox.tsx:225`; the plugin never sends events, and `services/fateEventRelay.ts` is unused).

### Cross-area notes (brief)

- **Strict Mode:** it fails open after S1 restarts and S2 wipes. The failure backoff cap (15 min) equals the freshness window (S6).
- **Sidebar:** every successful relay import calls `flashStatus(…, true)`, which collapses the Bundle section if it's open (`FateLockedPanel.java:797-800`). The disclosure says "RuneLite retrieves rules…" even when sync is off.
- **Docs drift:** README step 4 and the manual matrix say the panel goes Waiting → Connected. ROADMAP §1 says the Plugin Hub release shipped, while the plugin README says the candidate "has not been submitted or accepted".

## Not covered

- The full Gradle suite. RuneLite's Maven repository is blocked, so I ran the 37 existing connection tests and 13 harness scenarios against the real sources with stand-ins, not the plugin-level tests (`FateLockedRelayImportTest`, the startup contract, the panel).
- No live RuneLite or browser session. S1, S2, S7, S9's thread paths and S10 are traced from code, not executed. Panel rendering was read, not viewed.
- Unverified Cloudflare behaviour: rewriting the ETag or If-None-Match headers at the edge, KV negative-lookup caching and propagation, and free-tier request and write limits. Also unverified: whether RuneLite profile sync or export copies `trackerPairingCode`, and OSRS name equivalence of `_`, `-` and space (wiki blocked).
- The cost of parsing a full-size bundle on the client thread. Only the 2.5 KB fixture was available. **[Corrected]** The
  rules reviewer measured a real 1.3 MiB bundle: about 180 ms cold, 14 ms warm (R13).
- Rules content and parity, detectors, Strict Mode semantics, and the broader sidebar layout, which belong to the other reviewers.
