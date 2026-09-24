# D — Detectors, detected events and the Roll inbox

> Area report from the 2026-09-24 plugin review. The consolidated report is [`../2026-09-24-plugin-review.md`](../2026-09-24-plugin-review.md). Harness names refer to a throwaway harness that is not in this repository. Web commit `2f1697c` has the same tree as web `main` at `9617a16`. Corrections made while consolidating are marked **[Corrected]**.

Scope: plugin `osrs-fate-locked-runelite` @ `bda88c8` (`detectors/*`, `events/*`, Roll inbox wiring in `FateLockedPlugin.java` / `FateLockedPanel.java`, their tests), cross-checked against web `OSRS-Fate-Locked` (branch tree identical to `main` `9617a16`, verified by tree hash `f8c2bbf`). Read-only; neither repo was modified (`git status` clean in both).

Evidence sources used where the OSRS Wiki was unavailable: public RuneLite `master` sources and **RuneLite's own test fixtures** fetched from raw.githubusercontent.com on 2026-09-24 (`LootTrackerPlugin`, `ScreenshotPlugin(Test)`, `ChatCommandsPlugin(Test)`, `SlayerPlugin`, `PestControlOverlay`, `PluginManager`, `GameEventManager`, `NpcID`/`gameval` tables, `StatChanged`, `Text`), plus the web repo's wiki-derived data (`public/monster-catalogue.*.json`, `data/resourceEnrichment.ts`). Message formats taken from RuneLite fixtures are not live captures; I say so where it matters.

Harness (not committed): `D-harness/` — plugin's RuneLite-free classes compiled with `javac --release 11` + Gson/Lombok (one `WorldPoint` stand-in), `DetectorReview.java` and `TwoClients.java` scenario runners, and `webcheck/classify.check.ts`, which feeds the plugin-produced events through the **web repo's own** `parseFateEvent` / `classifyFateEvent` / `classifyFateEventCandidate`. Outputs: `D-harness/work/detector-review.txt`, `two-clients.txt`, `web-classification.txt`. See `D-harness/README.txt` to rerun.

## Summary

- Today a detection is written to one global local file and surfaces only as two counters ("Local events", "Needs review") plus an optional chat line. Nothing can be listed, reviewed, cleared or handed to the web, and the web Roll Inbox that "Open web Roll Inbox" opens is always empty while its copy says RuneLite events "will queue here" (D1). The plugin and web still maintain a full event contract (envelope v1, 12 detector policies, classifier) that has no transport and no shared test.
- Of 12 detection paths, 4 work end to end for most cases: skill levels, raids, the 9-name boss list and most diary tiers. 4 produce wrong labels that the web's own classifier cannot resolve: combat achievements (all real messages, D3), some quests (D6), 12/48 diary tiers (D9) and bosses named by NPC (D4). Collection-log events can never be reviewed on the web (D10). 3 effectively never fire: clue caskets (D7), Pest Control (D15) and Slayer for players who don't check their gem (D8). The pet identity map is wrong for 2 of its 3 NPC ids (D11).
- Reliability: a malformed `slayer-assignment.json` stops the whole plugin from starting (D2, reproduced). The history and Slayer files are global across accounts and clients, so events from another account are recorded under the run, and a second RuneLite client silently erases the first's events (D5, D12, reproduced).
- Recommendation: fix D2 and the user-facing copy now. Retire the combat-level boss heuristic, the clue item scan and Pest Control. Rebuild the detectors as pure, fixture-tested classes that emit canonical ids from bundle-provided tables. The owner must also decide whether local events reach the web through a user-initiated clipboard hand-off, or the Roll inbox is retired.

## Today's event flow (the answer)

1. **Detect.** `@Subscribe` handlers in `FateLockedPlugin` (`onStatChanged` 572, `onChatMessage` 590, `onWidgetLoaded` 694, `onLootReceived` 822, `onVarbitChanged` 853) parse the signal, often in the plugin itself, and call a detector.
2. **Gate + wrap.** `record()` (884–911) requires a non-null detection, a bundle with a non-blank `runId`, and *any* logged-in player name. It does **not** check the bound account, the world type or duplicates. `FateEventFactory` wraps the detection in the v1 envelope with a random UUID (`FateEventFactory.java:38`).
3. **Store.** `FateEventHistory` appends to `~/.runelite/fate-locked/event-history.json`, one file for every account, run and client. It keeps the newest 250, copy-on-write, writes a temporary file then moves it into place, and moves a corrupt file aside.
4. **Show.** `updatePanelRollInbox()` (1786–1799) counts *all* retained events: "Local events" is the total and "Needs review" counts UNCERTAIN events. It also shows a third "Warnings" value, which is stale (D13). There is no list, detail, status, clear or export (`FateLockedPanel.java:202–226`). The separate chat "roll reminders" (default on) fire from 6 call sites, whether or not anything was recorded.
5. **Send.** Nothing. "Open web Roll Inbox" opens `…/?open=roll-inbox` (no code, no data), which the web's Dashboard turns into the AUTOROLL tab.

**Coherence with the web: not coherent.**
- The web keeps a complete dormant pipeline: `fateEventProtocol.ts`, the `detectorPolicies` registry (also exported in every v4 bundle, and ignored by the plugin), `fateEventEligibility.ts`, the `RollInbox` UI, `RollInboxDriver`, the playtest export and the `ACCEPT_DETECTED_EVENT` reducer. Nothing in production feeds it: `services/fateEventRelay.ts` has no production importer, and `RollInboxDriver.tsx:160–163` says it only reclassifies rows migrated from older browser sessions.
- The copy on both sides implies a flow that does not exist:
  - web `RollInbox.tsx:232`: "RuneLite events will queue here for you to decide";
  - web `RollInbox.tsx:225`: shows "Listening" whenever the tracker is paired;
  - plugin `FateLockedPlugin.java:879`: "Diary complete: X — review its tasks in the Roll Inbox."
- The envelope itself matches the web protocol: 11 event types, 2 confidences, `protocolVersion` 1, and all 12 detector ids and versions present in `DETECTOR_POLICIES` with matching types. Three details differ: null labels are dropped on write so the web rejects the event, integers come back as Doubles after a reload, and the web rejects events older than 30 days while the plugin history keeps them indefinitely (D16).

## Per-detector analysis

| Detector (id) | Trigger | Robustness | False positives / negatives | Dedupe / identity | Web match |
|---|---|---|---|---|---|
| Skill (`skill-level-v1`) | `StatChanged.level` rises vs per-login baseline | Real level; `boostedLevel` is a separate field (RuneLite `StatChanged`), so boosts are ignored. Language-independent. No world or account gate. A multi-level jump produces 1 event (70→72 gives "Attack Level 72", evidence 70/72). | FN: first level-up after the plugin starts mid-session (RuneLite simulates no `StatChanged`) and, inferred, after each loading screen (D18) | In-memory baseline reset on every `LOGGED_IN`; offline levels are never seen | Names = web `SKILLS_LIST` (incl. Runecraft, Sailing); label and evidence match `classifySkill` ✓ |
| Quest (`quest-widget-v1`) | `WidgetLoaded` 153 (= `InterfaceID.QUESTSCROLL`), regex over child texts | English text only. Two regexes, suffixed form first. | Mislabel/FN: "… Quest" names truncated; 4 title shapes yield null; RFD subquests never match web `RFD: …` ids (D6) | One scroll per completion; random UUID | Matches only when the text is not truncated (The Corsair Curse, Hazeel Cult, Dragon Slayer I ✓) |
| Combat achievement (`combat-achievement-chat-v1`) | Chat containing "combat task:" | Real text contains `@ach_comp@` marker and "(N points)"; tier discarded; popup-only setting not handled | Every real completion is mislabelled and marked EXACT (D3) | Random UUID | 0/5 RuneLite fixtures resolve ✗ |
| Collection log (`collection-log-chat-v1`) | Chat "New item added to your collection log:" matched on the **raw** message | Colour tags stay in the label if present. RuneLite fixtures disagree on whether the game sends them (D10). Popup-only setting → no chat line. | FN with popup setting; every event UNCERTAIN | Random UUID | Web gives UNCERTAIN CL events no candidates → dead end even for clean names ✗ |
| Clue casket (`clue-casket-loot-v1`) | Any `LootReceived` *item* whose name contains "casket" | Clue completions arrive as `LootReceived(EVENT, "Clue Scroll (Hard)", rewards)` (RuneLite `LootTrackerPlugin` 1138–1172), and those items contain no casket | FN ≈100% for real clues; FP: Tempoross/other loot containing a "Casket" (D7) | Random UUID | Allowlist `casket (hard)` = web `CLUE_SOURCES` keys, a label no game signal produces ✗ |
| Boss V2 (`boss-kill-v2`) | `LootReceived` name ∈ 9 hard-coded bosses (any loot type) | Needs RuneLite's Loot Tracker plugin (D17). A PvP kill of a player named "Vorkath" counts. | FN for every boss outside the 9 | Only same-game-cycle duplicates | 9/9 are web canonical names; web review → READY ✓ |
| Boss heuristic (`boss-loot-v1`) | NPC loot with combat level ≥ 250 | Name = NPC name, not the canonical boss | FP for superiors, metal dragons, gorillas…; FN for 8 bosses < 250 incl. Brutus; 15 web keys need a name mapping (D4) | Per kill | Only where NPC name = web key ✗/✓ |
| Raid (`raid-loot-v1`) | `LootReceived(EVENT)` named CoX / ToB / ToA | Names verified against RuneLite `LootTrackerPlugin` 338–340; one post per raid (`chestLooted`). Needs the Loot Tracker plugin. | FN if Loot Tracker is disabled | Per chest | EXACT → web READY ✓ |
| Slayer (`slayer-task-v1`) | Assignment from any chat containing "to kill"; completion from "completed your task" / "return to a slayer master" | Global JSON across accounts; master always null; `cancel()` never called | FN without a gem check; wrong label after an unseen task change; garbage assignment from other "to kill" text; cross-account leakage (D8) | `completed` flag persisted globally | Web always asks for the master |
| Diary (`diary-task-v1`) | Varbit 0→1 on 48 `Varbits.DIARY_*`, baseline taken on first `VarbitChanged` after each `LOGGED_IN` | Language-independent; baseline timing suspected; Karamja varbit semantics disputed (unverified) | 12/48 tier ids don't exist on the web (D9) | Per-login baseline | 36/48 ✓, 12 dead ends ✗ |
| Pet (`pet-drop-v1`) | Chat containing "funny feeling" (excluding insured/reclaim) + current follower id | 2 of 3 ids wrong (RuneLite NPC tables); "backpack" message missed; duplicate-pet message counted | FP/mislabel (D11) | 5 s in-memory window | Generic "Pet drop" review works; labels untrustworthy |
| Pest Control (`minigame-completion-v1`) | Widget 408 then "You have won the game!" within 5 s | 408 = `PEST_STATUS_OVERLAY`, present for the whole game (RuneLite `PestControlOverlay`) | Cannot fire in practice (D15) | `emitted` flag | n/a |

## Findings

Ordered by severity. "Reproduced" means shown by running code in the harness. "Traced" means established by reading code. Game-text claims marked unverified could not be checked against the live game or the Wiki.

### D1 — The Roll inbox is a dead end, and copy on both sides describes a flow that does not exist
- **Severity:** high · **Status:** traced
- **Location:** `FateLockedPanel.java:202-226, 497-509`; `FateLockedPlugin.java:879, 884-911, 1786-1799`; web `components/RollInbox.tsx:223-233`, `components/RollInboxDriver.tsx:159-174`, `services/fateEventRelay.ts:8-29`
- **Scenario:** a player finishes Doric's Quest and two diary tiers, so the sidebar shows "Local events 3 · Needs review 2" and chat says "Diary complete: Varrock Easy — review its tasks in the Roll Inbox." They click **Open web Roll Inbox**. The web shows "No detected rolls waiting. RuneLite events will queue here for you to decide." and, if paired, "Listening". Nothing can be reviewed anywhere. The counters only grow (until the 250 cap), mix every account and run, and "Needs review" excludes wrong EXACT events such as D3's.
- **Evidence:**
  - The panel has three `JLabel`s and one button.
  - `updatePanelRollInbox` iterates every retained event with no account or run filter.
  - No code reads history except that count.
  - `fateEventRelay` is imported only by its own test.
  - In `RollInbox.tsx`, `connected = relaySync.enabled` means paired, not listening (`relaySync.ts:82-86`).
- **Fix:** Stage 0 owner decision.
  - (a) Make it real: a sidebar list of recent observations per account and run with Handled/Dismiss, and a user-initiated **Copy for tracker** that puts a protocol-v1 `{events:[…]}` batch (nulls serialised) on the clipboard. The web gains **Paste from RuneLite**, which feeds the existing `parseEventBatch` → `store.ingest` → classifier. A clipboard copy mirrors today's clipboard import and is not HTTP, but it still needs the owner's Plugin Hub compliance read.
  - (b) Retire the history, counters and button, plus the web pipeline, and keep reminders only.
  - Fix the copy now either way.
- **Effort:** (a) M–L across both repos; (b) S–M; copy S.

### D2 — A malformed `slayer-assignment.json` stops the entire plugin from starting
- **Severity:** high · **Status:** reproduced
- **Location:** `SlayerTaskDetector.java:22-29`; `FateLockedPlugin.java:346-355`
- **Scenario:**
  1. The file is truncated or edited (sync conflict, manual edit, disk error, or a later plugin version writing a different shape).
  2. On the next launch `new SlayerTaskDetector` throws `com.google.gson.JsonSyntaxException`. `startUp()` catches only `IOException`, so the exception propagates.
  3. RuneLite's `PluginManager.startPlugin` (master, lines 428–461) catches the `Throwable`, calls `stopPlugin` and throws `PluginInstantiationException`.
  4. No rules, overlays, sidebar or Guardian run until the player deletes the file by hand.
- **Evidence:** harness section H (`{"name":"Abyssal demons",` → `JsonSyntaxException`, "NOT an IOException"). `FateEventHistory` and `StrictModeAuditLog` both recover from corrupt JSON; this store does not.
- **Fix:** In `SlayerTaskDetector`'s load, catch `JsonParseException`/`RuntimeException`, move the file aside as `.corrupt-<ms>` and start empty (share one helper with the history). In `startUp`, construct every optional local store under `catch (Exception)` so no detector can block rules or the Guardian. Add a test.
- **Effort:** S

### D3 — Combat achievement labels carry the game's `@ach_comp@` marker and "(N points)" suffix, and are marked EXACT
- **Severity:** high · **Status:** reproduced against RuneLite's own fixtures and the web classifier
- **Location:** `CombatAchievementDetector.java:10-25`; `FateLockedPlugin.java:612-624`; `CombatAchievementDetectorTest.java:14-19`
- **Scenario:** Real format (RuneLite `ScreenshotPluginTest` 389–394, 527): `Congratulations, you've completed a grandmaster combat task: @ach_comp@Egniol Diet II</col> (6 points).`
  - The plugin stores `@ach_comp@Egniol Diet II (6 points)` as EXACT, so it is not even counted under Needs review.
  - The reminder reads "Combat achievement: @ach_comp@Egniol Diet II (6 points) — may be worth a roll." (how the marker renders in chat is unverified).
  - The web classifier returns "Combat task is not in the current rules." with no candidates, for 5/5 real-format messages. Only the plugin's own synthetic test string ("Congratulations, Combat task: Noxious Foe") resolves.
- **Also:**
  - The tier (`easy`…`grandmaster`) is discarded.
  - Players with the popup notification setting are not covered: RuneLite's ScreenshotPlugin switches to the `NOTIFICATION_TITLE` "Combat Task Completed!" popup when `VarbitID.CA_TASK_POPUP == 0`. Whether the chat line also appears then is unverified.
- **Fix:**
  - Parse like RuneLite: `an? (?<tier>\w+) combat task: (?:@[^@]+@)?(?<task>.+?)(?:</col>)?(?: \(\d+ points?\))?\.?$`.
  - Resolve the task to a CA task id through a bundle-provided name→id table.
  - Add the popup path (`ScriptPreFired` `NOTIFICATION_START`/`DELAY`).
  - Replace the synthetic test with RuneLite's fixture strings.
- **Effort:** S (parser and fixtures) / M (popup, table)

### D4 — Boss detection nudges non-bosses, misses a third of the web's boss list (including Brutus), and names don't match web keys
- **Severity:** high · **Status:** reproduced (harness F + web classifier). Combat levels come from the web's monster catalogue.
- **Location:** `BossRaidDetector.java:13-29`; `BossKillDetectorV2.java:17-31`; `FateLockedPlugin.java:821-850` (stale `BOSS_LOOT_COMBAT_LEVEL = 200` and javadoc at 267–275; the detector uses 250)
- **Scenario — false positives:** an ironman on a Mithril dragon task gets "[Fate Locked] Boss kill (Mithril dragon) — may be worth a roll." on every kill (level 304). The same happens for Demonic gorilla 275, Lava dragon 252, Brutal black dragon 318, Adamant/Rune dragons 338/380, and superiors (Nechryarch 300, Greater abyssal demon 342, Night beast 374, Marble gargoyle 349, Basilisk Sentinel 358, Repugnant spectre 335).
- **Scenario — the web's 69 boss keys (68 + Brutus):**
  - 8 can never fire because the NPC is below 250: Brutus 30, Obor 106, Gemstone Crab 160, Chaos Fanatic 202, Crazy Archaeologist 204, Scorpia 225, Giant Mole 230, Shellbane Gryphon 235.
  - 15 have no NPC or event of that name and would need a mapping: Dagannoth Kings, Grotesque Guardians (loot NPC "Dusk"), The Royal Titans (Branda/Eldric), Tormented Demons (NPC is singular), Barrows Brothers (loot event "Barrows"), Moons of Peril ("Lunar Chest"), Fortis Colosseum (the event name matches but only CoX/ToB/ToA events are accepted), The Gauntlet, Inferno, TzHaar Fight Cave, TzHaar-Ket-Rak's Challenges, The Mad Angel, Mimic, Wintertodt, Tempoross.
  - Mismatched labels dead-end in web review ("Boss or raid is not in the current rules.").
- **Evidence:** `D-harness/work/detector-review.txt` §F and `web-classification.txt` F:*. The reachability split was computed from `data/bossKeyTiers.ts` and `public/monster-catalogue.*.json`. The web test `fateEventEligibility.test.ts:237-260` expects EXACT `boss-loot-v1` "Brutus" events that the plugin cannot produce.
- **Fix:**
  - Retire the heuristic.
  - Build one boss detector keyed by NPC id, using core `ServerNpcLoot`, which carries `NPCComposition` and is posted by RuneLite's core `LootManager` without the Loot Tracker plugin.
  - Add kill-count chat (format `Your Corporeal Beast kill count is: <col=ff0000>4</col>.`, RuneLite `ChatCommandsPluginTest`). The count also gives a natural idempotency key.
  - Add loot-event names (Barrows, Lunar Chest, Fortis Colosseum, raids).
  - The web authors the map `{npcIds, kcNames, lootEvents} → boss key` into the bundle, Brutus included. Gate reminders on a resolved key.
- **Effort:** M (both repos)

### D5 — Detections (and reminders) are recorded for any account and any world type, into state shared by every account
- **Severity:** medium · **Status:** traced; Slayer leakage reproduced
- **Location:** `FateLockedPlugin.java:884-892` (gate), `:277` (single `DATA_DIR`), `:348-362`; reminder call sites `:581-585, 617-623, 633-638, 710-716, 843-848, 877-880`; `SlayerTaskDetector.java:26-29`
- **Scenario:**
  - The same RuneLite runs the player's main account and a Fate ironman, with a bundle loaded. The main's GWD kills, levels and CAs are stored under the ironman's `runId`, inflate both counters and push real ironman observations out of the 250 cap. The main also receives "may be worth a roll" reminders.
  - On a seasonal (Leagues) world with the same display name, league level-ups are stored under the right account name, so any future hand-off would pass the web's account check.
  - Reminders fire even with no bundle loaded.
  - Slayer (harness H): account A checks their gem; account B later completes a task, which produces SLAYER_TASK "Abyssal demons" for B. A's own completion is then suppressed as "already completed".
- **Evidence:** `record()` checks only `runId` and a non-blank name. `currentAccountMatches()` exists (`:736`) but is unused there. `grep` finds no `getWorldType`, `WorldType` or `getAccountHash` anywhere in `src/main`.
- **Fix:**
  - One `DetectionGate`: bundle present; bound account matches (switch to `client.getAccountHash()` if the bundle can carry it); standard world types only. The exact exclusion list is unverified: seasonal, deadman, tournament, beta, fresh-start, …
  - Reminders go through the same gate.
  - Key all detector state and history by account (RuneLite RS-profile config, as `ChatCommandsPlugin` uses for pets, or per-account files), and filter counts by the current run.
- **Effort:** S (gate) / M (per-account state)

### D6 — Quest names ending in "Quest" are truncated, several scroll titles yield no name, and RFD never matches
- **Severity:** medium · **Status:** reproduced (RuneLite `ScreenshotPluginTest` quest fixtures → plugin regexes → web classifier)
- **Location:** `FateLockedPlugin.java:320-323, 795-812`; `QuestDetector.java:10-21`
- **Scenario:**
  - Truncated, stored as EXACT: "You have completed Doric's Quest!" gives `Doric's`. The same happens to Heroes' Quest, Waterfall Quest and Another Cook's Quest, and by the same rule Legends', Olaf's and Observatory (RuneLite keeps a list of these, `WORD_QUEST_IN_NAME_TAGS`).
  - No name at all: "'One Small Favour' completed!", "You have completely completed Rag and Bone Man!" (Rag and Bone Man II), "Congratulations! You have defeated the Culinaromancer!", "Sins of the Father forgiven!".
  - The web holds the correct names ("Doric's Quest", "One Small Favour", "Rag and Bone Man II", "Sins of the Father"), but every one of these events dead-ends ("Quest is not in the current rules." or no candidates).
  - The web's Recipe for Disaster entries are ids like `RFD: The Cook` and `RFD: Finale`, which no scroll title can match.
- **Fix:**
  - Preferred: detect quest completion as a per-account diff of RuneLite `Quest` states (FINISHED transitions). This is language-independent and exact. It needs a RuneLite-quest→web-id map in the bundle; the RuneLite `Quest` enum's coverage is unverified.
  - Minimal: try the bare pattern first, keep a trailing " Quest" when the stripped name isn't in a bundle-provided quest list, and add the four extra title shapes as fixtures.
- **Effort:** S (minimal) / M (quest state)

### D7 — The clue detector never sees clue completions
- **Severity:** medium · **Status:** traced (signal naming verified in RuneLite source); logic reproduced (harness E)
- **Location:** `ClueCasketDetector.java:13-31`; `FateLockedPlugin.java:825-833`
- **Scenario:**
  - Opening a hard reward casket makes RuneLite's loot tracker post `LootReceived(EVENT, "Clue Scroll (Hard)", rewards)`, triggered by "You have completed N hard Treasure Trails." (`LootTrackerPlugin` 141, 1138–1172). The detector only scans item names for "casket", so nothing is recorded.
  - Conversely, any loot containing an item named "Casket" (Tempoross reward-pool caskets; opened caskets are their own "Casket" event) creates UNCERTAIN CLUE_CASKET "Casket", which dead-ends on the web.
  - The EXACT allowlist `casket (hard)`, … likely never matches a real item: the web's wiki-derived data calls the reward item "Reward Casket (hard)" (`data/resourceEnrichment.ts`; unverified, Wiki blocked). The web's `CLUE_SOURCES` uses the same invented keys, so both sides agree on a label nothing produces.
- **Fix:**
  - Replace it with a clue-completion detector: `LootReceived` EVENT `Clue Scroll (<Tier>)`, or the chat line above. Accept a thousands separator; RuneLite's own `[0-9]+` pattern would not.
  - The canonical key is the tier and the completion count is the natural key.
  - Change web `CLUE_SOURCES` in the same release.
- **Effort:** S–M

### D8 — The Slayer detector rarely knows the task and can attach the wrong one
- **Severity:** medium · **Status:** reproduced (harness H) / traced
- **Location:** `FateLockedPlugin.java:602-607, 640-657`; `SlayerTaskDetector.java:31-69`
- **Scenario:**
  - The assignment is captured only from a chat line containing "to kill", such as a gem check ("You're assigned to kill X; only N more to go."). A master's assignment is dialogue, not chat (unverified), so a player who never checks the gem gets no Slayer events.
  - After one gem check followed by a new task from dialogue or a skip, completion carries the *old* task name. `cancel()` is never called.
  - Any other game message with "to kill" overwrites the task. Harness: "You need to kill 5 more goblins to unlock this." → task "5 more goblins to unlock this".
  - The master is always null (the web must ask), `joinedMidAssignment` is always false, and state is global (D5).
- **Evidence:** RuneLite's own `SlayerPlugin` (master) no longer parses assignment chat. It reads `VarPlayerID.SLAYER_COUNT`, `SLAYER_TARGET`, `SLAYER_AREA`, `SLAYER_COUNT_ORIGINAL` and `VarbitID.SLAYER_MASTER`, `SLAYER_TASKS_COMPLETED` (plus wilderness/Mortimer streaks), with the task name from the Slayer task DB table.
- **Fix:**
  - Rewrite on those varps: completion when the count reaches 0 and the streak increments.
  - Key the task by id, with the master mapped to the web's `DropSource` through the bundle.
  - Use per-account state, with the streak as the natural key.
  - Delete the chat regex.
- **Effort:** M

### D9 — Diary tier ids drift from the web for 12 of 48 tiers; baseline logic lives in the plugin
- **Severity:** medium · **Status:** reproduced (drift); suspected (baseline timing)
- **Location:** `FateLockedPlugin.java:242-256, 547-556, 852-882`; `DiaryTierReviewDetector.java:12-28`
- **Scenario:** completing Lumbridge & Draynor Easy stores `tierId "Lumbridge & Draynor Easy"`, but the web's ids are `Lumbridge Easy`, `Kourend Easy` and `Western Easy`. The web finds no candidate tasks, so Review stays disabled. The 12 tiers affected are all four tiers of Kourend & Kebos, Lumbridge & Draynor and Western Provinces (`web-classification.txt`, "DIARY labels without any web task: 12/48").
- **Suspected (unverified in game):**
  1. The baseline is re-taken on the first `VarbitChanged` after *every* `GameStateChanged(LOGGED_IN)`. RuneLite's own plugins treat `LOADING` as a mid-session scene load and detect logins via `LOGGING_IN`/`HOPPING`, which implies `LOGGED_IN` recurs after loading screens. If the tier varbit change is the first varbit event after such a screen, the completion is absorbed into the baseline.
  2. At a real login, if the first `VarbitChanged` arrives before the diary varps sync, completed tiers read 0 and then flip to 1, which would produce spurious "Diary complete" events every login. ROADMAP says the design avoids this; I could not verify it.
  3. Karamja tier varbits may use a third value: one Hub plugin requires 2, quest-helper uses 1. Unverified.
- **Fix:**
  - Emit web tier ids from a bundle-provided varbit→tierId map, or at minimum rename the three regions.
  - Move the baseline into the detector with a per-account persisted last-known bitmap. This also catches tiers finished while RuneLite was closed.
  - Reset on `LOGGING_IN`/`HOPPING`, not on `LOGGED_IN`.
- **Effort:** S (names) / M (baseline)

### D10 — Collection log events can never be reviewed on the web; the label may keep colour tags
- **Severity:** medium · **Status:** reproduced (dead end); tag inclusion depends on the wire format (unverified)
- **Location:** `FateLockedPlugin.java:626-639`; `CollectionLogDetector.java:10-21`; web `utils/fateEventEligibility.ts:125-152`
- **Scenario:**
  - The plugin matches the raw message, so RuneLite's `ScreenshotPluginTest` fixture `New item added to your collection log: <col=ef1020>Chompy bird hat</col>` becomes the label `<col=ef1020>Chompy bird hat</col>`. `ChatCommandsPluginTest` uses an untagged variant, so which form the game sends is unverified.
  - Either way the plugin hard-codes `uniqueMapping=false`, which makes every event UNCERTAIN. The web's `confirmationCandidates` has no COLLECTION_LOG branch, so even a clean "Chompy chick" gives "Detector version is not approved…" with no candidates, a dead end.
  - Players using the popup-only new-item notification (RuneLite reads `VarbitID.OPTION_COLLECTION_NEW_ITEM` and the "Collection log" notification popup) produce no chat line at all.
- **Fix:**
  - Strip tags before matching.
  - Emit EXACT when the name resolves uniquely through a bundle item table.
  - Handle the popup.
  - On the web, offer name candidates for UNCERTAIN collection-log events.
- **Effort:** S–M

### D11 — The pet detector's identity map is wrong and its message coverage is inverted
- **Severity:** medium · **Status:** reproduced (RuneLite NPC tables + harness D)
- **Location:** `PetDropDetector.java:16-31`; `FateLockedPlugin.java:598-601`; unused `src/main/resources/pet-identities.json`
- **Scenario:**
  - In RuneLite's tables `7334` is the POH Giant squirrel (`POH_SKILLPET_AGILITY`), not Olmlet. The Olmlet follower is `7520`.
  - `6637` is the Kalphite Princess follower (`KQ_PET_FLYING`), not Jal-Nib-Rek, whose follower is `7675`.
  - A player walking a Kalphite Princess who gets the duplicate-pet line ("…you would have been followed…") is recorded as PET_DROP "Jal-Nib-Rek".
  - The new-pet-to-backpack line ("You feel something weird sneaking into your backpack.", one of RuneLite's three `PET_MESSAGES`) is not detected.
  - A real new pet is labelled from whatever `getFollower()` returns at that instant; timing is unverified, and an unmapped follower gives a null label.
- **Fix:** detect the two new-pet lines, treat "would have been followed" as a duplicate, and take identity from the pet item id or collection-log line via a bundle table. Otherwise retire the detector until that table exists.
- **Effort:** S / M

### D12 — History and Slayer files are shared by every RuneLite client and rewritten whole
- **Severity:** medium · **Status:** reproduced (`TwoClients.java`)
- **Location:** `FateEventHistory.java:58-77, 182-213`; `SlayerTaskDetector.java:71-85`
- **Scenario:** two clients are open (main + ironman). Each loads the history once and rewrites the entire file from its own memory, so the ironman's observation disappears as soon as the main records anything. Harness: on disk only `[from-main-client]`. The same applies to `slayer-assignment.json`.
- **Also:**
  - Every `record()` re-serialises and rewrites the whole file on the client thread: about 105 KB at the cap and about 1.6 ms per record here. Windows or antivirus timing was not measured.
  - There is no fsync. A torn write is recovered only by the corrupt-file path, which drops the history.
- **Fix:**
  - Per-account files (accountHash).
  - Merge-on-write under a `FileLock`, or an append-only log compacted on start.
  - A single-writer executor off the client thread.
- **Effort:** M

### D13 — The Roll inbox "Warnings" value is stale
- **Severity:** medium · **Status:** traced · **Area:** also the sidebar reviewer's
- **Location:** `FateLockedPlugin.java:1786-1808` (counts); updates only at `:443, 540, 903, 909`; `lastLockState` changes at `:1081`, Slayer at `:661-690`, over-tier gear at `:952-1001`
- **Scenario:** entering a locked chunk warns on the HUD, but the sidebar keeps "Warnings: None" until the next detection. After leaving, it keeps "1 active".
- **Fix:** refresh on those transitions, or move live warnings out of the Roll inbox.
- **Effort:** S

### D14 — No shared contract between plugin output and web classifier; each side tests invented strings; the bundle's detector fields are unused
- **Severity:** medium · **Status:** traced
- **Location:** plugin detector tests (e.g. `CombatAchievementDetectorTest` "Congratulations, Combat task: Noxious Foe", `ClueCasketDetectorTest` "Casket (hard)"); web `utils/fateEventEligibility.test.ts:189, 237-260, 255`; web `utils/runeliteRulesManifest.ts:192-194` exports `detectorPolicies`; the plugin parses `detectorContractVersion` (`FateLockedBundle.java:76, 152-154`) and never reads it.
- **Scenario:** web commit `e32a1a6` ("Recognise detected Brutus kills") added handling for a detection the plugin cannot emit, and nothing failed. The same blind spot let D3, D6, D9 and D10 ship.
- **Fix:**
  - A golden fixture corpus in the plugin repo (real message or varbit signal → expected canonical event JSON), consumed by a web parity test. This follows the pattern `utils/runelitePluginParity.test.ts` already uses for bundles.
  - The web authors canonical tables in the bundle (D4, D6–D11).
- **Effort:** M

### D15 — The Pest Control detector cannot fire
- **Severity:** low · **Status:** traced (the win message text is unverified)
- **Location:** `MinigameCompletionDetector.java:14-25`; `FateLockedPlugin.java:596-597, 696`; unused `minigame-completions.json`
- **Scenario:** the 5-second window opens when group 408 loads. 408 is `PEST_STATUS_OVERLAY` (RuneLite `gameval`), and RuneLite's `PestControlOverlay` treats its presence as "in a game" (appears at game start, disappears at game end). A win minutes later falls outside the window (harness I).
- **Fix:** retire it. If minigame completions matter, add a verified per-minigame signal from a bundle table.
- **Effort:** S

### D16 — Event identity and serialisation don't support dedupe or a hand-off
- **Severity:** low (latent) · **Status:** reproduced
- **Location:** `FateEventFactory.java:38`; `FateEventHistory.java:58-66, 159-189`; web `services/fateEventProtocol.ts:111-114`
- **Details:**
  - A random UUID per detection means the history's "unique event id" check never catches a duplicate observation. Harness: the same quest scroll seen twice gives 2 events.
  - `new Gson()` omits nulls, so every null-label event lacks `canonicalLabel` and is rejected by the web's `parseFateEvent`. That covers all diary events and unnamed quests and pets ("REJECTED as persisted" in `web-classification.txt`). RuneLite's injected Gson null policy is unverified.
  - Evidence integers reload as `Double` (71 → 71.0).
  - The history keeps events indefinitely, while the web rejects `occurredAt` older than 30 days.
  - An entry with an unknown enum loads with `eventType=null` and still counts.
- **Fix:** a content-derived id (hash of account, run, type, canonical key and natural key); `serializeNulls` on export; validate entries on load.
- **Effort:** S

### D17 — Boss, raid and clue detection silently depend on RuneLite's Loot Tracker plugin
- **Severity:** low · **Status:** traced (RuneLite source)
- **Location:** `FateLockedPlugin.java:68, 821-850`
- **Details:** `LootReceived` is posted only from `LootTrackerPlugin.addLoot` (master line 715). If the player disables Loot Tracker, these detectors stop with no indication. The core `LootManager` posts `ServerNpcLoot` and `NpcLootReceived` regardless.
- **Fix:** use the core events for NPC kills and chat or kill-count signals for raids and clues, or declare the dependency.
- **Effort:** S

### D18 — Skill detector edge cases
- **Severity:** low · **Status:** reproduced (harness J)
- **Location:** `SkillLevelDetector.java:14-17`; `FateLockedPlugin.java:547-555`
- **Details:**
  - A 70→72 jump yields one "Attack Level 72" event. The web awards one roll plus chaos milestones across the jump; whether that matches the manual flow is a rules-parity question.
  - When the plugin starts mid-session (enable or Hub update), RuneLite's `GameEventManager` simulates item containers and spawns but not `StatChanged`, so each skill's first level-up becomes the baseline.
  - The baseline is also cleared on every `LOGGED_IN`, which likely follows loading screens (inferred, see D9), so a level-up on the first XP drop after a teleport is lost.
- **Fix:** baseline from `client.getRealSkillLevel` (or persisted per account); reset on `LOGGING_IN`/`HOPPING`.
- **Effort:** S

### D19 — Dead or misleading code in this area
- **Severity:** low · **Status:** traced
- **Location and details:**
  - Unused resources: `boss-encounters.json` (5 names vs V2's 9), `minigame-completions.json`, `pet-identities.json` (no references in source or build).
  - In `FateLockedPlugin.java`: unused `COMBAT_TASK` regex (`:312`); `BOSS_LOOT_COMBAT_LEVEL = 200` plus a javadoc claiming superiors sit "well under 200" (`:267-275`), unused, while the detector uses 250; `lastLevels` is only ever cleared (`:222, 553`).
  - `SlayerTaskDetector.cancel()` is never called; the default `FateEventFactory.create(...)` overload (`"plugin-v1"`) is used only by tests.
  - The README says the section "shows the newest 250 unique observations" (it shows counts) and omits combat achievements from the detection list.
  - `net.runelite.api.Varbits` is `@Deprecated` in RuneLite master (replaced by `gameval.VarbitID`).
- **Fix:** delete or align in Stage 1.
- **Effort:** S

## Verified correct

- **History mechanics:** 250 cap keeps the newest; dedupe by id; a failed write leaves memory and disk unchanged (copy-on-write); temp file then atomic move with a non-atomic fallback; corrupt file moved aside as `.corrupt-<ms>`; migration keeps the newest 250 legacy `pending` entries and leaves `event-outbox.json` byte-identical.
  - How: ran the plugin's own RuneLite-free tests in the harness, all green: `FateEventHistoryTest` 7, `FateEventFactoryTest` 1, and 11 detector tests across 7 classes, 19 in total. Added corruption cases (`{"events":{}}`, `[]`, empty file), each moved aside with a fresh start.
  - The legacy format was confirmed from git history (`FateEventOutbox` `{pending, acknowledged}`).
- **No transport for events:** no event, ack or state client exists in `src/main` (grep plus `verifyPluginHubJar`'s symbol list); history is never read by network code; the Roll Inbox URL carries no code or data (existing `FateLockedPanelStatusTest`).
- **Envelope parity:** `FateEventType` has the same 11 names as web `FATE_EVENT_TYPES`; confidences are identical; all 12 detector ids and versions emitted by the plugin exist in `DETECTOR_POLICIES` with matching event types; `protocolVersion` 1. Checked by comparing sources and by running plugin output through `parseFateEvent`: every event with a non-null label parsed.
- **Skill:** `StatChanged` carries `level` and `boostedLevel` separately (RuneLite source), so boosts can't fire. Names equal web `SKILLS_LIST` including Runecraft and Sailing. Label and evidence shape is what `classifySkill` reads.
- **Raids:** event names equal RuneLite's `LootTrackerPlugin` constants (`:338-340`) and the web's `BOSS_TIERS` keys. Web cross-check returns READY for "Chambers of Xeric".
- **V2 names:** all 9 are web canonical names; web review returns READY (Vorkath, and Kalphite Queen via the heuristic).
- **Diary:** the plugin uses RuneLite's `Varbits.DIARY_*` constants; 36/48 labels equal web tier ids.
- **Widget ids:** 153 = `InterfaceID.QUESTSCROLL`; 408 = `PEST_STATUS_OVERLAY` (the id is right; the timing is wrong).
- **Chat filter:** GAMEMESSAGE/SPAM only, so clan broadcasts about other players are excluded. RuneLite's CA and collection-log fixtures are GAMEMESSAGE.
- **Gating and failure states:** detection needs a bundle `runId` and a logged-in name; a failed history write keeps prior counts and shows "Local history save failed", which clears after the next success (existing `FateLockedPluginLocalHistoryTest`, read not run; plus code). Panel updates are marshalled to the EDT (`runOnEdt`).
- **Slayer completion dedupe:** survives a restart via the persisted `completed` flag (existing test and harness).

## Structural notes for the overhaul

**What makes this area hard to change**
1. **The risky code lives in the untestable class.** Message regexes, widget scraping, the diary tables and baseline, the Slayer regex, the loot loops and the reminders are all in `FateLockedPlugin` (1,822 lines), which needs the RuneLite client to test. The detector classes are thin wrappers, and their tests use invented strings that match no real message.
2. **Canonical names are hard-coded in Java** (9 bosses, 3 pets, 12 diary regions). Three unused JSON copies disagree with them. The web's canonical tables (`BOSS_TIERS`, `QUEST_DATA`, CA tasks, diary tier ids, CL items) evolve independently; Brutus was added only on the web.
3. **Events carry display labels, not ids**, so every naming difference is a silent drift bug, and there is no cross-repo fixture to catch it.
4. **State is scattered.** Per-login in-memory baselines sit in the plugin; there is one global file per concern; nothing is keyed by account, world type or run.
5. **Reminders are wired ad hoc** at 6 call sites. They are not gated consistently and exist for only some detectors.
6. **The envelope carries network-era fields** (`runRevision`, `bundleVersion`, detector policy) with no consumer. The web maintains a full dormant pipeline that must stay in sync with nothing feeding it.
7. **Cross-area pointer:** `onGameStateChanged(LOGGED_IN)` also resets `lastAccountWarned` and `warnedOverTier` (`:547-558`). If `LOGGED_IN` recurs after loading screens (inferred), wrong-account and over-tier warnings repeat after each teleport. This is for the core-lifecycle reviewer.

**Keep / fix / retire**

| Detector | Verdict | Why |
|---|---|---|
| Skill level | **Keep** (fix gate and baseline) | Right signal: real level, language-independent, labels match the web. Needs account/world gate and baseline reset on `LOGGING_IN`/`HOPPING` (D5, D18). |
| Quest | **Fix** (rewrite on quest state) | Scroll parsing mislabels or misses about a dozen quests and all RFD (D6); quest-state diffing is exact and language-independent. |
| Combat achievement | **Fix** | Signal is fine; parser is wrong for every real message (D3). Add popup path and task-id table. |
| Collection log | **Fix** | Strip tags, resolve via item table, add popup path; web must offer candidates (D10). |
| Clue casket | **Retire → replace** | Never sees clue completions (D7). Replace with a `Clue Scroll (<Tier>)` / "Treasure Trails" completion detector keyed by tier. |
| Boss V2 | **Merge** into a new boss detector | Correct but only 9 bosses; hard-coded (D4). |
| Boss heuristic (≥250) | **Retire** | Floods non-boss "may be worth a roll" reminders and can never reach 23 of 69 web bosses (D4). |
| Raid (EVENT) | **Keep** (move into the boss detector) | Only fully correct EXACT → READY path; remove the Loot Tracker dependency (D17). |
| Slayer | **Fix** (rewrite on varps) | Chat-only assignment is unreliable, stale and cross-account (D8); RuneLite's own Slayer plugin moved to varps. Also crash-proof its store (D2). |
| Diary tier | **Keep** (fix names and baseline) | Right signal; 12/48 ids drift and the baseline is fragile (D9). |
| Pet | **Fix or retire** | Id map 2/3 wrong, message coverage inverted (D11). Keep only with a web-authored identity table. |
| Pest Control | **Retire** | Cannot fire (D15); single minigame; add minigames later from verified signals. |
| History + Roll inbox panel | **Rebuild or retire** (Stage 0 decision) | Invisible, unreviewable, account-mixed, multi-client unsafe (D1, D5, D12, D13, D16). |

**Proposed target design**
- **Stage 0 decision.** Either (a) a user-initiated local hand-off, where RuneLite's **Copy for tracker** feeds the web's **Paste from RuneLite** into the existing Roll Inbox, or (b) reminders only, with the history, counters and web Roll Inbox pipeline retired.
- **Signals → pure detectors.** A thin RuneLite adapter turns events into plain signals: tag-stripped chat lines, real stat levels, varbit/varp changes, notification popups, loot events with NPC id, kill counts, and context (accountHash, world types). Detectors are pure state machines over those signals. Each one ships with a fixture corpus of real messages (starting from RuneLite's own test fixtures, then in-game captures). No parsing stays in `FateLockedPlugin`.
- **Observation shape.** Detectors emit `Observation{type, canonicalKey, naturalKey, confidence, evidence}` with web ids. The mapping tables come from an optional, versioned `rules.detection` block in the bundle, generated by the web: boss NPC ids, kill-count names and loot events; quest ids; CA task ids; CL item ids; diary varbit→tierId; pet ids; clue tiers; Slayer master→source. The block uses `detectorContractVersion`. A detector whose table is absent turns itself off rather than guess.
- **One gate for everything.** A single `DetectionGate` (bundle present, bound account, standard world) sits in front of both storage and reminders.
- **Store.** An `ObservationStore` per account and run. The stable id is a hash of (accountHash, runId, type, canonicalKey, naturalKey). Entries have states NEW/HANDLED/DISMISSED and a cap. Writes are atomic and off the client thread, with a multi-client-safe merge.
- **Presentation.** The sidebar shows a short NEW list with counts that reflect NEW only; reminders come from one policy.
- **Contract test.** The same golden corpus drives a web parity test, so plugin labels and the web classifier cannot drift apart silently.
- **Staging.**
  - Stage 1 (S): D2; the gate; copy fixes; the CA, quest and diary-name fixes; CL tag stripping; retire Pest Control, the boss heuristic and the clue item scan; disable the pet id map; dead code; D13.
  - Stage 2 (M): bundle tables; the rewritten boss/raid, clue, Slayer, quest-state and diary detectors; per-account store; fixture corpus plus web parity test.
  - Stage 3 (M–L): the hand-off (web paste, list UI) or retirement of the web pipeline.

## Not covered

- **Live game checks.** No in-game capture of any message, widget or varbit behaviour; RuneLite's test fixtures stood in. Specifically unverified:
  - whether CA and collection-log lines appear in chat when the popup settings are on;
  - whether the collection-log item is colour-tagged;
  - the Pest Control win text;
  - the reward casket item names;
  - Slayer master dialogue never reaching chat;
  - Karamja diary varbit values;
  - `LOGGED_IN` re-firing after loading screens and the diary login-sync timing;
  - `getFollower()` timing for new pets;
  - loot attribution in group boss kills;
  - whether any OSRS client language changes these strings.
- **RuneLite-dependent plugin tests** (`FateLockedPluginLocalHistoryTest` and others) were not run: the client jar is unavailable.
- **RuneLite's injected Gson** null-serialisation policy.
- **Web in a browser:** the Roll Inbox UI and Dashboard `?open=roll-inbox` were not driven. Web detected-event economics (e.g. multi-level jumps, diary per-task rolls) are left to the rules-parity reviewer.
- **Other reviewers' areas:** the relay Worker's legacy `/events` route, the wider sidebar layout, Strict Mode and core lifecycle, beyond the cross-area pointers above.
- **Windows specifics:** I/O timing, file-locking semantics and antivirus interference with the atomic move.
