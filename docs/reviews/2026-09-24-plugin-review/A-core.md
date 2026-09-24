# Review A: core lifecycle and architecture

> Area report from the 2026-09-24 plugin review. The consolidated report is [`../2026-09-24-plugin-review.md`](../2026-09-24-plugin-review.md). Harness names refer to a throwaway harness that is not in this repository. Web commit `2f1697c` has the same tree as web `main` at `9617a16`. Corrections made while consolidating are marked **[Corrected]**.

Plugin: `osrs-fate-locked-runelite` at `bda88c8` (main). Web app read for cross-checks only
(`OSRS-Fate-Locked`, branch `claude/quirky-archimedes-aponk4`). Nothing was edited in either repository;
`git status` is clean in both.

## Summary

- Registration symmetry is sound. Every listener, overlay, the navigation button, the key and mouse listeners, both scheduled
  futures, the infoboxes and the map markers are released in `shutDown`, including after a failed start. What leaks is
  client-thread work queued before shutdown (A8).
- The biggest reliability problem is that no single component owns the active rules. The relay, the file watcher, the
  "Reload from file" button, the Auto-reload toggle and paste/clipboard imports all write `bundle` and `rulesImportedAt`
  directly. Each finding below was reproduced against the real plugin code:
  - Toggling Auto-reload erases relay rules, and the panel keeps showing Connected (A1).
  - An old bundle file counts as fresh for Strict Mode and blocks travel (A2).
  - An older file silently replaces newer relay rules (A6).
- One malformed local file (`slayer-assignment.json`) stops the whole plugin from starting, on every start, with no in-app
  message (A3).
- `LOGGED_IN` fires after every loading screen, not once per login. The per-login resets spam wrong-account warnings with a
  sound and chunk announcements, and they drop level-ups (A4). Plugin state is mutated from the EDT, the client thread and
  executor threads. Sidebar actions call client APIs on the EDT (A5).
- This matters for the overhaul: the plugin is already live on the Plugin Hub. `runelite/plugin-hub` master
  `plugins/fate-locked-ironman` pins commit `52f45f5` (A17). Config keys and data files therefore form a compatibility
  contract with real users.

Method: I compiled the real production sources (all except the six injected render overlays) with `javac --release 11`
against local RuneLite stand-ins. The stand-ins mirror upstream semantics that I confirmed from RuneLite master source,
fetched from raw.githubusercontent.com on 2026-09-24. The repository's own 56 core-area tests pass on this harness. I then
ran 16 scenario and probe tests. Harness: `A-harness` (`run.sh`, log `run-all.log`; not committed).
RuneLite facts marked "upstream" were read from that source. The plugin builds against `latest.release`, which can differ
slightly from master.

## Findings

### A1: Auto-reload toggle or "Reload from file" without a bundle file erases active relay/clipboard rules; relay 304s never restore them
- **Severity:** high
- **Status:** reproduced
- **Location:** `src/main/java/com/fatelocked/FateLockedPlugin.java:1321-1325` (no file → `bundle = empty`),
  `:497-502` (autoReload `ConfigChanged` → `reloadBundle()`), `:433` (panel "Reload from file"),
  `TrackerConnectionController.java:350-397` (304 path commits freshness, never payload)
- **Player scenario:** A relay-connected player unticks "Auto-reload on change", switches to a RuneLite profile with a
  different value, or clicks "Reload from file". There is no `fate-locked-bundle*.json` in the data folder. All rules
  vanish: overlays, HUD, warnings, menu tags and Strict Mode. The panel still shows "Connected · HH:MM UTC" because the
  next poll sends `If-None-Match` and gets 304. The rules come back only after the web app publishes a new version or
  RuneLite restarts. No status message is shown.
- **Evidence:** Harness `autoReloadToggleWipesRelayRulesAndRelayKeepsReporting304`: after the toggle `runId=null regions=0`.
  After the 304 `state=CONNECTED runId=null rulesAreFresh=true`. Relay requests:
  `GET /r/<code>`, `GET /r/<code> If-None-Match=1`.
- **Fix:**
  - A file source that finds no file must be a no-op with a status message ("no bundle file in .runelite/fate-locked"),
    not "empty rules".
  - The autoReload toggle should only start or stop the watcher.
  - Any non-relay replacement of the active snapshot must force the next relay poll to be unconditional.
  - Longer term, route every source through one `RulesStore` (see the target design).
  - Port the harness scenario to a repository test.
- **Effort:** S

### A2: A local bundle of any age counts as fresh for 15 minutes after it is loaded; Strict Mode can block on stale rules
- **Severity:** high
- **Status:** reproduced
- **Location:** `FateLockedPlugin.java:1331, 1413` (`rulesImportedAt = Instant.now()` on file/paste load),
  `:1193-1198` (`rulesAreFresh`: tracker last sync if paired, else local load time), `:729` (panel freshness uses `null`
  when unpaired); `panel/ChunkPanelViewModelFactory.java:158-164` ("Offline snapshot")
- **Player scenario:** An offline (unpaired) player with Strict Mode on still has a January bundle file in the folder.
  They have unlocked more areas in the tracker since then. Every RuneLite start loads that file and treats it as fresh for
  15 minutes, so the Guardian consumes clicks the tracker now allows. At the same moment the chunk panel says "Offline
  snapshot". After 15 minutes enforcement silently stops until the next import. The bundle carries `exportedAt` (web
  `utils/runeliteBundle.ts:62,71,110`), but the plugin ignores it.
- **Evidence:** Harness `oldBundleFileIsFreshForStrictModeAndBlocksTravel`: file with
  `exportedAt=2026-01-01` and a LOCKED destination → `rulesAreFresh=true teleport consumed=true`.
- **Fix:**
  - Compute freshness from the snapshot, not from the import moment. Relay: last 200/304 confirmation (as today).
    File/clipboard/paste: `rules.exportedAt`, with a clock-skew guard.
  - Use one `FreshnessPolicy` for the guard, the panel and the HUD.
  - Whether offline sources may enable blocking at all is a product decision for the Strict Mode reviewer.
- **Effort:** S–M

### A3: A malformed or schema-changed `slayer-assignment.json` prevents the plugin from starting
- **Severity:** high (low likelihood, total impact)
- **Status:** reproduced
- **Location:** `detectors/SlayerTaskDetector.java:22-29` (`gson.fromJson` in the constructor), `FateLockedPlugin.java:346-355`
  (catches only `IOException`)
- **Player scenario:** Any of the following makes every start throw `JsonSyntaxException`:
  - the file is truncated or hand-edited;
  - a sync tool or a disk problem damages it;
  - a later plugin version (or a sideloaded build) changes a field type.

  RuneLite then stops the plugin (upstream `PluginManager.startPlugin` catches, calls `stopPlugin`, logs). No sidebar
  appears and nothing tells the player why. It stays broken until they delete the file by hand.
- **Evidence:** Harness `corruptSlayerStateAbortsStartup` produced
  `JsonSyntaxException: MalformedJsonException: Unterminated string ... path $.name`, with no nav button, overlays or tasks.
  `schemaDriftInSlayerStateAlsoAbortsStartup` produced `NumberFormatException: For input string: "many"`. The other two
  stores tolerate bad files: a probe of 7 malformed inputs against `FateEventHistory` and `StrictModeAuditLog` under
  RuneLite's Gson 2.8.5 raised no exceptions.
- **Fix:**
  - Catch `RuntimeException` in every local-state loader and quarantine the file (the `.corrupt-<ts>` pattern that
    `FateEventHistory` already uses).
  - Treat each optional subsystem's init failure as a visible degraded mode, never a failed start.
  - Add one startup test per corrupt file.
- **Effort:** S

### A4: Per-login resets run after every loading screen
- **Severity:** medium
- **Status:** reproduced (account warning, level-up); traced (diary, gear)
- **Location:** `FateLockedPlugin.java:545-558`, consumers `:1015-1043` (`checkBoundAccount` every tick), `:571-587`,
  `:853-882`, `:952-1002`, `:1066-1082`
- **Upstream fact:** `XpTrackerPlugin.java:187`: "LOGGED_IN is triggered between region changes too."
- **Player scenario:** Each loading screen (teleport, region change, world hop) has the following effects:
  - **Wrong account:** a player logged into a non-bound account gets the "This run is bound to…" chat line again, plus
    sound 2277 and a notification if enabled.
  - **Chunk announcement:** the same chunk is announced again, with sound and notification when it is locked.
  - **Level-ups:** a level-up is dropped if it is that skill's first `StatChanged` after the load.
  - **Diaries:** the diary baseline is re-read, so a completion that arrives as the first varbit change after a load is
    missed.
  - **Over-tier gear:** gear warnings re-fire on the next equipment change.

  The code comments say "once per login".
- **Evidence:** Harness `wrongAccountWarningRepeatsOnEveryLoadingScreen`: 1 warning after login, 6 after 5 loading
  screens, sounds `[2277 ×6]`, and 6 same-chunk announcements without moving. `levelUpIsMissedWhenItIsTheFirstStatAfterALoadingScreen`:
  the control level-up is recorded, the post-load level-up is not.
- **Fix:**
  - Reset only on a real login or account change: record `client.getAccountHash()` (and world) on `LOGGED_IN` and compare
    (the XpTracker pattern), or key the reset off `LOGGING_IN`/`HOPPING`.
  - Warn about the wrong account once per (account, run).
- **Effort:** S

### A5: Plugin state is mutated from three threads; sidebar and config actions call client APIs on the EDT
- **Severity:** medium
- **Status:** reproduced (EDT client calls); traced (races)
- **Location:** `FateLockedPlugin.java:432` (paste/clipboard import wired straight to `applyPastedBundle` on the EDT;
  `FateLockedPanel.java:450-453, 613-632`), `:492-542` (`ConfigChanged` handler runs on the posting thread), `:448`
  (`startUp` parse and refresh on the EDT), `:952-1002` (client and `ItemManager` access), `:1512-1542` (`mapMarkers`
  `ArrayList`)
- **Threads (upstream semantics):**
  - `startPlugin`/`stopPlugin` assert the EDT.
  - `ConfigManager.setConfiguration` posts `ConfigChanged` synchronously on the caller's thread, which is the EDT for the
    sidebar and the native config panel.
  - The executor runs the watcher and the poll.
  - OkHttp threads run the callbacks.
  - Hub plugins are instantiated outside `invokeAndWait` (`ExternalPluginManager.java:328`), so the field-injected
    `FateLockedPanel` builds its Swing tree off the EDT.
- **Player scenario:** Pasting a bundle, toggling "Warn on over-tier gear" or "Pin locked areas" in the sidebar, or
  enabling the plugin mid-session runs the following on the EDT while the client thread may be using the same objects:
  `client.getLocalPlayer`, `client.getItemContainer`, `ItemManager.getItemComposition`, world-map marker rebuilds and
  warning state.
  - **Plausible outcomes:** duplicated or leaked map markers, a `ConcurrentModificationException` escaping a handler, or a
    reader seeing the new `bundle` with the old `rulesImportedAt`. The last happens because `bundle` is written before
    `rulesImportedAt` at `:1409-1413`.
  - **Unverified:** whether the injected client asserts the client thread in `getItemDefinition` (it would under `-ea`).
- **Evidence:** Harness `sidebarPasteImportTouchesClientApiOnTheEdt`:
  `[getLocalPlayer@AWT-EventQueue-0 ×2, getItemContainer@AWT-EventQueue-0, getItemDefinition@AWT-EventQueue-0]`.
- **Fix:**
  - Confine all plugin state to the client thread. Every entry point from the EDT, the executor or OkHttp hops through
    `clientThread.invoke`.
  - Swing sees only immutable view models, through one presenter.
  - Create the panel in `startUp` on the EDT instead of by field injection.
- **Effort:** M

### A6: Import sources have no precedence; an older file silently replaces newer relay rules
- **Severity:** medium
- **Status:** reproduced
- **Location:** `FateLockedPlugin.java:1313-1341` (file), `:1391-1433` (paste/clipboard), `:1446-1486` (relay); watcher
  `:1716-1733`
- **Player scenario:**
  - A connected player has an old `fate-locked-bundle-*.json` written or restored into the folder (watcher on by
    default). The older run revision replaces the relay's rules. The relay keeps answering 304, so the newer rules never
    return.
  - Strict Mode then enforces the file's rules while freshness comes from the relay timestamp (see A2).
  - At every start, a stale file is also the active rule set until the first relay sync.
- **Evidence:** Harness `olderFileSilentlyReplacesNewerRelayRules`: relay revision 41, then an old file makes it 12, and
  after the next poll (304) it is still 12 with `state=CONNECTED`.
- **Fix:**
  - A `RulesStore` accepts an automatic source (watcher, startup file) only when it has the same `runId` and a newer
    `runRevision`/`exportedAt`. Explicit user imports always win and are labelled.
  - When paired, the relay is authoritative (demote or disable the watcher).
  - A non-relay replacement clears the relay ETag.
- **Effort:** M

### A7: Local state files are global across accounts, runs and RuneLite instances
- **Severity:** medium
- **Status:** reproduced (multi-instance lost update); traced (cross-account)
- **Location:** `FateLockedPlugin.java:277, 344-380` (fixed paths under `~/.runelite/fate-locked/`),
  `events/FateEventHistory.java:58-77, 191-213` (each instance rewrites the whole file from its own memory; fixed `.tmp`
  name), `:1786-1799` (Roll Inbox counts all events), `:325-331` (`slayerTask`, `slayerTaskWarn`, `slayerWarnedFor` never
  reset on logout)
- **Player scenario:**
  - **Two clients:** a player runs two RuneLite clients (main plus ironman). Each client's history write drops the other
    client's events.
  - **Account switch:** after switching accounts, the HUD keeps the previous account's "Slayer ⚠", and a task completion
    on account B is recorded against account A's saved assignment.
  - **Counts:** "Local events" and "Needs review" count every account and every past run.
- **Evidence:** Harness `twoClientsSharingTheDataDirectoryLoseEachOthersEvents`: A records "Dragon Slayer I", B records
  "Cook's Assistant", and only B's event is on disk.
- **Fix:**
  - Scope state per account hash (and run) in the directory or file name.
  - Show counts for the current run and account.
  - Reset per-account fields on account change.
  - Make writes merge-safe: re-read and merge under a `FileLock`, or use an append-only log with compaction.
  - Include a one-time migration of today's global files.
- **Effort:** M

### A8: Client-thread work queued before shutdown still runs after it
- **Severity:** low
- **Status:** reproduced (reload); traced (Connect)
- **Location:** `FateLockedPlugin.java:1730` (watcher queues `reloadBundle`), `:1386-1387` (hotkey), `:1620-1638`
  (Connect); `TrackerConnectionController.java:84` (`beginPairing` sets `stopped = false`)
- **Player scenario:**
  - The player disables the plugin just after a bundle file changed, or just after pressing the hotkey. The queued reload
    runs after `shutDown`: rules and a locked-area world-map pin come back while the plugin is off, and they stay until
    the plugin is re-enabled.
  - A Connect queued just before disabling revives the stopped controller and opens the browser.
- **Evidence:** Harness `queuedReloadAfterShutdownRepopulatesRulesAndWorldMapMarkers`: after `shutDown`,
  `runId=run-1 worldMapMarkers=1`.
- **Fix:**
  - Use a per-start session token that every deferred runnable checks.
  - `beginPairing` must not clear `stopped`.
- **Effort:** S

### A9: Per-frame and per-tick work that could be cached
- **Severity:** low
- **Status:** reproduced (cost measured); per-tick call rate suspected
- **Location:**
  - `FateLockedPlugin.java:1200-1232`, `guardian/GuardedActionFactory.java:113-121`, `Teleports.java:269-275`: several
    `String.replaceAll` regex compiles and a new `FateRuleEngine` plus account normalisation per menu entry.
  - `:1051`: panel post every game tick.
  - `TrackerConnectionController.java:191-196`: with consent off, publishes to the panel every 4 s.
  - `:1723`: the watcher lists the data folder every second, on by default.
  - `FateLockedContentOverlay.java:52-54`: builds a full chunk view model every frame when enabled.
- **Evidence:** Harness `menuEntryAddedHotPathCost`: 3.6–4.0 µs and ~7.8 KB allocated per entry. A 6-entry menu at 50
  client ticks/s costs about 1.1 ms CPU and 2.3 MB of garbage per second while hovering the game with a bundle loaded.
  The per-tick `MenuEntryAdded` rate is inferred from the upstream `ClientTick` javadoc (menu sorted every 20 ms) and not
  verified. `idleCostsWithSyncOffAndNoMovement`: 15 panel connection updates per minute with sync off, and 100
  strict-mode panel posts per minute while standing still.
- **Fix:**
  - Precompile patterns and normalise once.
  - Cache a per-(snapshot, account) `RulesView` with O(1) chunk status.
  - Publish panel state only on change.
  - Stop publishing when consent is off and unchanged.
  - Slow the watcher down or scope it to explicit use.
- **Effort:** S–M

### A10: Disk I/O and bundle parsing run synchronously on the client thread and the EDT
- **Severity:** low
- **Status:** reproduced (timings measured)
- **Location:** `FateLockedPlugin.java:884-911` (`record` → whole-history rewrite), `:1137-1152` and `:1169-1174` (audit
  append inside the click handler), `:602-651` (slayer writes on chat), `:1313-1341` / `:1446-1486` (parse)
- **Evidence:**
  - A full bundle (121,892 bytes, generated with the web app's `buildBundlePayload`) takes 16–33 ms to parse the first
    time and 2.4–2.9 ms warm; the FLGZ form takes 3.8–4.2 ms. **[Corrected]** That bundle was built without the chunk
    content loaded. A real bundle is about 1.3 MiB and takes about 180 ms to parse cold and 14 ms warm (R13).
  - The history file at its cap is 106,913 bytes. Each `record()` takes a median of 1.4–2.0 ms (max 4.7 ms) on this
    Linux filesystem. Windows with antivirus is unverified.
- **Fix:** Use one background I/O worker for parse and write, and commit results on the client thread. Memory stays
  authoritative.
- **Effort:** M

### A11: `RepeatedValueLimiter` hides a failed re-import under a green "imported" status
- **Severity:** low
- **Status:** reproduced
- **Location:** `FateLockedPlugin.java:165-166, 1396-1402, 1424-1428`; `RepeatedValueLimiter.java:16-29`
- **Player scenario:** Paste bad JSON (red), then a good bundle (green), then the same bad JSON within 30 s. The import
  fails silently and the panel still says "imported N regions".
- **Evidence:** Harness `repeatedBadPasteAfterAGoodOneFailsSilentlyUnderGreenStatus`:
  `bad='import failed…', good='imported 1 regions', same bad again (returned false)='imported 1 regions'`.
- **Fix:** Reset the limiter on success, or rate-limit only the log line and always update the status.
- **Effort:** S

### A12: Import rollback is not transactional
- **Severity:** low
- **Status:** traced
- **Location:** `FateLockedPlugin.java:1404-1432`, `:1463-1485`, `:1489-1509`
- **Scenario:** When `refreshPanel` throws, `bundle` and `rulesImportedAt` are restored. The side effects already
  produced from the rejected bundle stay:
  - map markers;
  - over-tier and slayer chat lines and notifications;
  - the HUD summary fields;
  - the queued panel update.

  An `Error`, which `catch (RuntimeException)` does not catch, leaves the new bundle with the old timestamp. The existing
  test `relayImporterRollsBackWhenPanelRefreshFails` asserts only the two fields.
- **Fix:** Derive everything from the candidate snapshot first, commit atomically, then announce.
- **Effort:** S, as part of `RulesStore`

### A13: Boss, raid and clue detection silently depends on the Loot Tracker plugin
- **Severity:** low
- **Status:** traced (upstream)
- **Location:** `FateLockedPlugin.java:821-850`
- **Fact:** `LootReceived` is posted only by `LootTrackerPlugin.addLoot` (upstream `LootTrackerPlugin.java:715`). Core
  `LootManager` posts `NpcLootReceived`/`PlayerLootReceived`/`ServerNpcLoot`. `@PluginDependency(LootTrackerPlugin.class)`
  would fail, because upstream `instantiate` requires the dependency to expose a public module and I found none in
  `LootTrackerPlugin`.
- **Player scenario:** A player who disables Loot Tracker gets no boss, raid or clue observations and no reminders, and
  sees no hint why.
- **Fix:** Use the core events for NPC kills, and show a sidebar hint when event-type loot is unavailable.
- **Effort:** S
- **Note:** overlaps the detectors reviewer.

### A14: Deprecated RuneLite APIs in core paths
- **Severity:** low
- **Status:** traced (upstream)
- **Location:** `FateLockedPlugin.java:225-238, 946, 969` (`Varbits.DIARY_*`, `InventoryID.EQUIPMENT`),
  `guardian/travel/RuneLiteTravelAvailability.java:29-43`
- **Fact:** `net.runelite.api.Varbits` and `InventoryID` are `@Deprecated` upstream in favour of `api.gameval.*`. They are
  not in the Hub packager's `disallowed-apis.txt` today, so the build still passes. The packager turns disallowed APIs into
  build failures for PRs, so this is future Hub risk.
- **Fix:** Migrate to `gameval` ids and `client.getItemContainer(int)`.
- **Effort:** S

### A15: The three infoboxes share one InfoBox name
- **Severity:** low
- **Status:** traced (upstream)
- **Location:** `FateLockedInfoBox.java` (no `getName()` override), `FateLockedPlugin.java:1566-1597`
- **Fact:** Upstream `InfoBox.getName()` is `plugin class + "_" + infobox class`, and `InfoBoxManager.splitInfobox` moves
  every box with the same name. Keys, fate and progress therefore detach and move as one unit and share one saved layer key.
- **Fix:** Give each box its own name (this resets saved layers once).
- **Effort:** S

### A16: Dead and misleading code in the plugin class
- **Severity:** low
- **Status:** traced
- **Location:**
  - `FateLockedPlugin.java:1240-1275`: `menuTargetWorldPoint`, never called.
  - `:222, 553`: `lastLevels`, write-only.
  - `:267-275`: `BOSS_LOOT_COMBAT_LEVEL`, unused, although the Javadoc at `:814-820` describes a threshold this method no
    longer applies.
  - `TrackerConnectionSettings.java:62`: `clearPairing`, unused; there is no disconnect UI.
  - `FateLockedPanel.java:471-474`: unused `setCallbacks` overload.
  - `SlayerTaskDetector.cancel`: unused.
  - `FateLockedPlugin.java:89, 123`: unused imports.
- **Fix:** Delete these, or wire `clearPairing` to a Disconnect action, and correct the Javadoc.
- **Effort:** S

### A17: Plugin Hub status in the docs is wrong; the Hub already ships this plugin at `52f45f5`
- **Severity:** low
- **Status:** traced (Hub manifest fetched)
- **Location:** `README.md:9` ("has not been submitted or accepted"); web `ROADMAP.md:9-16` ("resolves to … `5cc1ffc`")
- **Fact:** `runelite/plugin-hub` master `plugins/fate-locked-ironman` contains
  `repository=https://github.com/Nubles/RS3-Fate-Locked-Runelite.git` and `commit=52f45f5b709e…`. That is this repository's
  `HEAD~1`, "restore explicit consent (#14)". `bda88c8` (8 MiB cap) is not yet on the Hub. I did not see the review
  thread, so I cannot say whether Strict Mode was explicitly pre-cleared.
- **Fix:**
  - Correct the README, CONTRIBUTING, the review notes and ROADMAP §1.
  - Treat the Hub pin, the config keys and the data-file formats as release constraints in the overhaul plan.
- **Effort:** S

## Verified correct

- **Start/stop symmetry:** each registration in `startUp` has a matching release in `shutDown`. Harness
  `repeatedStartStopLeavesNoDuplicates`: three start/stop cycles then a start leave exactly 7 overlays, 1 mouse listener,
  1 nav button, 1 key listener, 2 live tasks and 3 infoboxes; the final `shutDown` empties all of them.

  | Registration (start) | Release (stop) |
  |---|---|
  | `overlayManager.add` ×6 (`:416-421`) | remove ×6 (`:472-477`) |
  | Travel banner overlay + mouse listener via `TravelGuardianOverlayLifecycle` (`:423-428`) | `stop()` (`:460-471`) |
  | Nav button (`:445-446`) | `removeNavigation` (`:478-482`) |
  | Hotkey (`:451`) | unregister (`:483`) |
  | Watcher and poll futures (`:449`, `:452`) | cancelled (`:458-459`), controller stopped (`:1760-1763`) |
  | Infoboxes (`:450`) | `removeIf` (`:486`) |
  | Map markers | removed (`:484-485`) |

- **Failed-start cleanup:** upstream `PluginManager.startPlugin` calls `stopPlugin` → `shutDown` after any `Throwable`,
  and `shutDown` is null-safe for a partial start. Harness `failureAfterRegistrationsIsCleanedUpByShutdown`, with a failure
  injected at the key listener, left 0 overlays, mouse listeners, nav buttons, tasks and infoboxes. No file handles are held
  between calls.
- **TravelGuardianOverlayLifecycle:** rollback on partial start or stop is correct and retryable. Its 7 tests pass on the
  harness.
- **Exceptions in handlers:** upstream `EventBus.post` catches `Exception` per subscriber, and `ClientThread` catches
  `Throwable` per task, so handler exceptions are logged, not fatal. The travel shell additionally fails open on
  coordinator errors (9 tests pass).
- **Network and compliance boundary** (by grep and review):
  - one `Request.Builder` (GET `/r/<code>`), consent default-off and re-checked before every poll;
  - `LinkBrowser` only on clicks;
  - no reflection, JNI, subprocess, socket, `URLConnection`, `new Gson`, `new OkHttpClient`, `WidgetInfo` or
    `client.getVar` in production code;
  - file writes confined to `~/.runelite/fate-locked/`;
  - Hub packager checks satisfied: `icon.png` 48×72, required properties, `build=standard`, Java 11 bytecode, one plugin
    class, nothing from `disallowed-apis.txt`.
- **Local stores:** temp file plus `ATOMIC_MOVE` with fallback in all three stores. `FateEventHistory` quarantines corrupt
  files and changes its in-memory list only after persisting. `StrictModeAuditLog` tolerates malformed input (probe with
  7 inputs under Gson 2.8.5).
- **Config binder:** consent prompt only on enable; a failed write rolls the control back; refresh does not echo writes.
  RuneLite "Reset" writes explicit default values (upstream `setDefaultConfiguration`), so the sidebar refreshers receive
  them.
- **Relay imports:** commits happen on the client thread and are gated by the controller's generation and `stopped` flag.
  A pasted pairing code is caught before parsing.
- **Hotkey path:** reads the clipboard on the EDT and parses on the client thread, which is the correct split.
- **Repository tests:** the 56 core-area tests pass unchanged on the harness (JDK 21, main at `--release 11`, Mockito
  4.11): StartupContract, LocalHistory, TravelAccountBinding, RelayImport, ConfigBinder, Config, RepeatedValueLimiter,
  OverlayLifecycle, PluginShell, UnifiedPluginContract and NetworkBoundary.

## Structural notes for the overhaul

### What makes this area hard to change

- **`FateLockedPlugin.java` (1,822 lines) holds twelve responsibilities:**
  - lifecycle and wiring;
  - rules import from four sources, plus freshness;
  - relay glue and pairing UI flow;
  - detection and local history (5 detector hooks);
  - roll reminders;
  - five kinds of warnings (chunk entry, account, bank, slayer, gear);
  - menu tagging;
  - Strict Mode context building and generic-guard chat/audit;
  - world-map pins;
  - infoboxes;
  - panel/navigation wiring;
  - account identity.

  It also has 24 `@Inject` fields, 11 `@Subscribe` handlers, about 35 mutable fields plus ~10 stateful helpers, and 8
  copies of the chat-message boilerplate.
- **Four account-match implementations disagree on the source field:** `currentAccountMatches`,
  `strictTravelAccountMatches`, `checkBoundAccount` (which uses `state.linkedAccount`) and the HUD overlay's own.
- **No owner for "the active rules":** `bundle`, `rulesImportedAt`, the controller's `lastSync`/`acceptedVersion`,
  watcher bookkeeping and derived state (markers, over-tier, slayer) are updated separately by five code paths
  (A1, A2, A6, A12).
- **No threading model:** the same methods run on the EDT, the client thread and executor threads (A5).
  `TrackerConnectionController` also writes config (which fans `ConfigChanged` out synchronously to every plugin) while
  holding `pollLock` (`:74-87`).
- **Settings sprawl:** each of the 31 settings (30 plus consent) is declared four times:
  1. the `@ConfigItem`;
  2. a hand-written sidebar control whose label string is duplicated;
  3. an optional branch in an 8-way `onConfigChanged` if/else;
  4. the counts in tests and docs ("30 retained settings", `assertEquals(31, …)`).

  Two hidden keys (`trackerPairingCode`, `strictModeIntroSeen`) live outside the interface. The "Warnings" section holds
  15 items, including the HUD, infobox and reminder toggles.
- **Test seams are reflection:** five test classes set private fields and call private methods by name (`record`,
  `acceptRelayPayload`, `applyPastedBundle`, `reloadBundle`, `rulesAreFresh`), so renames compile and then fail at run time.
- **Coverage gaps:**
  - no test asserts what `shutDown` releases;
  - no tests for `GameStateChanged`/`GameTick` semantics, `MenuEntryAdded` tagging, `ConfigChanged` side effects,
    `VarbitChanged` baselining, `LootReceived`, the watcher, or threading.
- **No dev launcher:** there is no RuneLite dev-launcher test class (the Hub template's `ExamplePluginTest`), which is one
  reason the manual matrix is mostly "Blocked".
- **Hub CI mismatch:** the Hub replaces `build.gradle` (`build=standard`, verified in the upstream packager), so
  `verifyPluginHubJar` and the tests never run in the Hub pipeline. A CI job that compiles main with the Hub's
  `standard-build.gradle` would catch divergence.

### Proposed target design

```
FateLockedPlugin (thin adapter: Guice fields, @Subscribe → forward, startUp/shutDown → SessionFactory)
 └─ PluginSession (one per start; token checked by every deferred task)
     ├─ Registrations      LIFO closer for overlays, listeners, nav button, futures, infoboxes, markers (replaces
     │                     TravelGuardianOverlayLifecycle and the hand-written shutDown)
     ├─ ClientThreadGate   the only way in from EDT/executor/OkHttp; all state below is client-thread-confined
     ├─ LoginTracker       GameStateChanged + accountHash/world → LoggedIn(once), AccountChanged, SceneLoaded
     ├─ RulesStore         owns RulesSnapshot{bundle, source, runId, runRevision, exportedAt, acceptedAt, verifiedAt};
     │   │                 offer(candidate, source) applies precedence; publishes an immutable RulesView per snapshot
     │   ├─ RelaySource    (TrackerConnectionController; unchanged contract, told to drop its ETag on local replace)
     │   ├─ FileSource     (watcher; "no file" = no-op; only newer same-run revisions when automatic)
     │   └─ ImportSource   (paste / clipboard / hotkey; explicit, always labelled)
     ├─ FreshnessPolicy    one function shared by guard, panel, HUD (with the Strict Mode reviewer)
     ├─ SettingsModel      typed snapshot; key → affected-module registry instead of the if/else chain;
     │                     also drives the sidebar binder, so labels and sections exist once
     ├─ LocalStateStore    per-account dir, schema-versioned files, never-throwing load (quarantine), background
     │                     writer, merge-safe across instances, one-time migration of today's global files
     ├─ Feature modules    ChunkEntryWarnings, AccountWarnings, GearWarnings, SlayerWarnings, BankWarnings,
     │   (start/stop +     MenuTagger, Detection→EventHistory + RollReminders, StrictModeService, WorldMapPins,
     │    event handlers)  InfoBoxes; each reads RulesView + SettingsModel + ClientFacade
     └─ PanelPresenter     change-only, coalesced immutable view states → EDT
```

**Seams for tests:**
- constructor injection everywhere;
- `Clock` and a `Scheduler` interface (a manual scheduler in tests);
- a `ClientThreadGate` fake (run now, or queue);
- a small `ClientFacade`: local player name and location, account hash, equipment, varbits, widget text;
- `LocalStateStore` on a temp directory;
- the OkHttp interceptor transport already used by the controller tests.

Every harness scenario in `A-harness/test` maps directly to a repository test once these seams exist, and the gameplay
modules become testable without mocking `Client`.

**Suggested order:**
1. The small independent fixes (A1, A3, A4, A11), each with its regression test.
2. Session, registrations and client-thread confinement (A5, A8).
3. `RulesStore` with freshness and precedence (A2, A6, A12).
4. Per-account persistence with migration (A7).
5. Module split and the settings registry.

Keep config key names and the file formats readable throughout, because Hub users exist (A17).

### Cross-area notes (brief)

- **Strict Mode:** freshness policy (A2, A6); click consumption is the main Hub-policy exposure.
- **Detectors and events:** `FateEventFactory` assigns a random UUID per event, so "dedupe by stable event ID" never
  deduplicates repeated detections (`events/FateEventFactory.java:262`).
- **Diary baseline at real login:** whether varps are synced before the first `VarbitChanged` after `LOGGED_IN`
  (`FateLockedPlugin.java:855-862`) is unverified.
- **Instances (unverified):** chunk resolution uses `Player.getWorldLocation()`, which in instances is believed to return
  instance coordinates.
- **Relay:** polling continues at the login screen; the network gates do not look for other HTTP mechanisms
  (`URLConnection`, `java.net.http`, `newWebSocket`). There are none today.
- **UX:**
  - successful syncs collapse the Bundle section (`FateLockedPanel.java:797-800`);
  - "Synced Nm ago" is frozen until the next chunk change;
  - the native config panel shows the IP warning on disable too (upstream `ConfigPanel` warns on any change).
- **Self-feedback:** the plugin's own chat lines pass back through `onChatMessage`. Today no nudge text contains a trigger
  phrase, so this is harmless but fragile. That added messages raise `ChatMessage` is unverified (it happens inside the
  injected client).

## Not covered

- Live RuneLite verification. There is no client or login here, so all runtime claims come from the stand-in harness plus
  upstream source. The injected-client internals are unverified: client-thread assertions and exactly when varps and
  `ChatMessage` events fire.
- The real Gradle build. `repo.runelite.net` is blocked, so the full suite was not run. Only the 56 core-area repository
  tests ran, on stand-ins.
- The six render overlays (replaced by stand-ins in the harness), the detectors' parsing accuracy, rules parity,
  `TrackerConnectionController` internals, sidebar layout and Strict Mode decision logic, except where they touch lifecycle
  or threading. These belong to the other five reviewers.
- Windows and antivirus I/O timings, and behaviour on filesystems with coarse modification times.
- Whether RuneLite reviewers explicitly pre-cleared Strict Mode. The Hub manifest shows acceptance at `52f45f5` but not the
  review discussion.
- The web repository was read on its feature branch (reported as merged to main as `9617a16`); I did not check the merged
  commit.
