# Stage 2: One Source of Truth Implementation Plan

> Steps use checkbox (`- [ ]`) syntax for tracking. Each task is one commit
> unless it says otherwise.

**Goal:** Stage 2 of the [overhaul design](../specs/2026-09-24-plugin-overhaul-design.md).
- **The web app:** it writes every decision into the bundle: interiors,
  ocean, frontier, banks, free areas, progress, Slayer per master, and a
  travel table with unlock ids.
- **The plugin:** it looks decisions up through one `DecisionService` that
  every surface uses.
- **Strict Mode:** it becomes a small policy on top, matching travel by id.

**What players notice:**
- Interiors, dungeons and instances show real lock states.
- The sidebar, HUD, overlays, alerts and Strict Mode always agree.
- Teleports are checked against the right unlock and place.

**Tech stack:** Java 11, RuneLite 1.12, Gradle 8, JUnit 4, Mockito 4 for
the plugin; React/TypeScript/Vitest for the web app.

**Findings (15):** G6, G7, G10, G11, G14, R2, R10, R1, R4, R5, R6, R9,
R13, R15, R16. Evidence for each is in
[the review](../../reviews/2026-09-24-plugin-review.md). The briefs behind
this plan re-checked every finding against plugin `4e37895` (Stage 1
merged) and web `0977d52`.

## Global constraints

- **Branches.**
  - Plugin: `claude/stage-2-one-source-of-truth` from `main` `4e37895`.
  - Web: `claude/stage-2-one-source-of-truth` in the isolated worktree
    `.worktrees/stage-2` of the local web clone, from web `main` `0977d52`.
  - Never touch the web clone's own working tree.
- **Order of release.** The web release comes first: older plugins ignore
  the new fields. Then one Plugin Hub release.
- **Network boundary.** The plugin's only request stays the consent-gated
  `GET https://fate-relay.fatelocked.workers.dev/r/<32-hex>`.
  `PluginHubNetworkBoundaryTest` and `verifyPluginHubJar` stay green.
- **Compatibility with live Hub users.**
  - The bundle stays version 4. New fields are additive and optional; never
    repurpose a field.
  - Never drop a root field that an installed build reads: `874b9d1`, and
    `4e37895` once the Hub merges it.
  - The web keeps sending `state.linkedAccount`.
- **Size.** The relay refuses a published body over 256 KiB, and the web
  asserts under 240 KiB. A compressed bundle is about 200 KiB today, so
  every new field needs a measured size. Anything large goes behind a
  compact encoding or a capability flag (R13).
- **Threads.** Game state is read on the client thread through
  `ClientThreadGate`; parsing and file writes run off it (Stage 1).
- **Verification.**
  - Plugin: `gradle clean check --no-daemon` before every push.
  - Web: `npx vitest run`, `npx tsc --noEmit` and `npm run build`.
  - The owner can't run in-game checks, so golden bundles carry the proof.
    Before release, a login-screen client test runs with an empty RuneLite
    home (CONTRIBUTING, "Run it in a real client").
- **Publishing.** The owner merges every pull request. Claude may deploy the
  relay after the owner's `wrangler login`, and opens the Plugin Hub pull
  request once the plugin is merged.
- **Commits.** Small: one finding or one mechanical step each. The body says
  why and which test pins it.

## Decisions taken in this plan

- **New fields, not new keys in `rules.chunks`.** Installed builds (874b9d1
  and 4e37895) drive their sidebar, tags, bank warning and Strict Mode from
  every key in `rules.chunks`. An interior key there would make Strict Mode
  on the live Hub build start blocking the "Zanaris" fairy-ring option with
  no Hub review. Interior and ocean states therefore go in new maps that
  only the Stage 2 plugin reads.
- **Field names.** `rules.chunkEntries` is one status per chunk key for
  land, ocean and interiors (1,871 keys, about 6 KiB gz). `rules.places`
  describes the keys that aren't land (`kind`, `name`, `area`,
  `entrances`). The other sections are `frontier`, `banks`, `freeAreas`,
  `progress`, `slayerTasks`, `travel` and `capabilities`. Statuses use only
  the four existing names: an unknown value would read as UNKNOWN in
  installed builds, so a fifth is never added.
- **Slim the export first.** `rules.chunks` carries three fields no build
  reads (snapshot `chunkKey` and `counts`, row `key`). Dropping them saves
  about 33 KiB gz (44 KiB of relay body), which the new fields need: without
  it the relay body would sit about 10 KiB under the 240 KiB check. The
  proof runs before the web release merges: 4e37895's own golden tests
  pass on the slimmed bundles (C2, and again at E1).
- **The web decides; the plugin looks up.** Every per-run answer (chunk,
  place, bank, Slayer task, travel option) is computed by the web app's own
  functions and written into the bundle. The plugin keeps no rules of its
  own for v4 bundles; the legacy engine survives only as `LegacyRules` for
  v1–3 file and clipboard imports.
- **Strict Mode's decisions are data, inside limits set in code.** The
  bundle says whether a travel option is LOCKED, so a stricter ruleset can
  later be added from the web app alone. The plugin's code sets the outer
  limit, which only a Hub release can widen: travel only, matched by id,
  one destination chunk, never a denylisted option (Walk here, Attack,
  Talk-to, Trade, Bank, Wear and the rest), and one consume site.
- **Several destinations stay Unknown** (the design), even when every one
  is locked. A single-destination option whose required unlock is locked
  is blocked even if the destination itself is unknown: the unlock alone
  decides, so the match is still exact.
- **Spells are matched by spellbook and name**, not by widget id, because
  the spellbook's child ids shift when spells are added. The spellbook comes
  from the varbit the plugin already reads.
- **NOT_READY never alerts or tags.** It means "owned, not usable yet": the
  HUD says "Not ready", and tints and chat treat it as unlocked until
  Stage 3's palette.
- **A wrong character quiets every surface.** Today only the sidebar knows
  the bound account; after Stage 2 the HUD, tints, alerts, tags and warnings
  all read Unknown on another character.
- **Slayer masters come from `VarbitID.SLAYER_MASTER` only for values
  RuneLite's own Slayer plugin relies on** (7 Krystilia, 10 Mortimer).
  Otherwise the merged task key is used, which is LOCKED only when every
  master's answer is LOCKED.
- **No in-game recorder.** The owner can't test and Claude can't log in, so
  the research's `runClient` menu recorder is dropped. Fixtures cite the
  OSRS Wiki or RuneLite core's own option strings, and anything that doesn't
  match fails open.
- **The relay cap stays at 256 KiB.** With slimming, the projected body is
  about 181–190 KiB including the travel table. Every golden run gets the
  240 KiB check, not just vanilla fresh.
- **One Hub release**, as the design says. If the diff grows too large to
  review comfortably, it splits after Phase E: surfaces first, travel
  second.

## Phases

Phases A and B are plugin-only and start now. Phases C and D are the web
release and can run alongside them. Phases E and F wait until the plugin
re-pins to a web `main` commit that has C and D (E1). Each phase leaves
`main` releasable.

### Phase A: plugin core (no visible change)

- [x] **A1. Keep the rules core free of RuneLite.** Move
  `CanonicalChunk.of(WorldPoint)` to the adapter; delete the unused
  `southWestTile` and `northEastTile`. Pinned by a new
  `RulesCoreBoundaryTest`: no `net.runelite` import in `rules/`,
  `FateLockedBundle` or `CanonicalChunk`.
  *Done as* `03af23a`: a `WorldChunks` adapter and `CanonicalChunk.ofTile`.
- [x] **A2. `Decision`, `Trust`, `RulesSnapshot` and `DecisionService`**
  over `rules.chunks`, `itemRules`, `knownMobility` and the BANKS rows;
  `LegacyRules` wraps v1–3. Pinned by `DecisionServiceGoldenTest`
  (every `rules.chunks` entry for all 938 keys; land equals the golden
  expectation; a wrong character gives TRUST; an unbound profile is trusted
  for display) and `LegacyRulesTest` (v1 and v3 fixtures keep today's
  answers).
  *Done as* `e2aadb0`, with `chunk`, `details`, `target`, `bankAt`,
  `mobility` and `item`; nothing reads it yet.
- [x] **A3. Fix R10:** with `rules` present, a bundle is Chunked only when
  `rules.gameModeId` is `chunked`. Pinned by
  `GoldenBundleMutationTest.strayRootUnlockedChunksChangeNothing` over every
  non-Chunked golden.
  *Done as* `1e9e8fb`.
- [x] **A4. Build the snapshot on the parse thread (R13)**, publish the
  service through a volatile field, and rebuild it when the bound or
  logged-in account changes. Pinned by the relay and startup import tests
  (the snapshot exists before the client-thread switch) and
  `FateLockedLoginSessionTest`.
  *Done as* `144e0c7`: every source builds a `ParsedRules` with its
  snapshot where it parses; the service is refreshed at each switch, login,
  logout and tick.
- [x] **A5. `ChunkLocator`, not wired yet (G11).** Player, actor and scene
  tile to a rules chunk: top-level views as today, instances through
  `WorldPoint.fromLocalInstance`, boats through
  `WorldEntity.transformToMainWorld`, and Unknown for a missing entity, an
  instance zone with template -1, WALK, minimap and `WORLD_ENTITY_*`
  options. Pinned by `ChunkLocatorTest` with a real template array.
  *Done as* `fc09b14`, with `player`, `actor`, `menuTarget` and `sceneTile`.
- [~] **A6. Remove dead travel code (G14):** `GuardedAction.Kind.MOVEMENT`,
  `TravelAction.Family.WALK`, `BOUNDARY_OBJECT`, `OTHER_TRANSPORT`,
  `TravelAction.origin`, `TravelGuardianResult.guardResult`,
  `Teleports.destinationChunk(String,String)` and `destinations()`,
  `GuardContext.rules`, the resolver's `client` parameter and
  `FateRuleEngine.staleImport`. Pinned by the existing suites.
  *Folded into B4 and F2–F5:* every one of these lives in a class those
  tasks delete or rewrite (`FateRuleEngine`, `TravelActionResolver`,
  `TravelAction`, `Teleports`), so trimming them now would edit about fifty
  call sites twice.
- [x] **A7. One Strict Mode gate (G14).** `StrictModeReadiness` becomes the
  only gate; `isTrustedExact` goes, and the not-proven path no longer calls
  the click handler. Pinned by a parameterised `StrictModeGateTest` (a
  consume happens only when readiness is ACTIVE, the match is exact and the
  decision is LOCKED) and `PluginHubClickBoundaryTest` (still one consume).
  *Done as* `0b8485f`: `GuardContext` is gone and the guard takes the
  readiness the sidebar shows. The pause is checked last, so PAUSED means
  "would block, but paused" (`ALLOW_PAUSED`). `StrictModeGateTest` runs 96
  mixes of facts through the plugin.
- [x] **A8. Pause on a monotonic clock, cleared at shutdown (G14).** Pinned
  by `StrictModePauseTest` with a fake `nanoTime` and a lifecycle test.
  *Done as* `e1b7ec4`.
- [x] **A9. `MenuFacts` adapter:** option and target with tags and
  "(LOCKED)" stripped and case kept; item id with the worn-slot fallback;
  spellbook varbit; interface group; NPC and object ids; world view. Pinned
  by `MenuFactsTest`.
  *Done as* `a01dd95`: `MenuFacts` holds plain values (kept RuneLite-free by
  `RulesCoreBoundaryTest`) and `MenuFactsReader` reads them. A worn slot's
  item comes from its second child, as RuneLite's menu swapper reads it.

### Phase B: every surface reads `DecisionService` (plugin only)

Surfaces that already use the v4 engine move first, with no change in
their answers; then the legacy surfaces, each commit naming the change
players will see.

- [x] **B1. Sidebar and content overlay**, carrying the trust reason (U4).
  `ChunkPanelViewModelFactoryTest` plus the goldens.
  *Done as* `82fafff`: the card says "Unknown · Wrong account" (or "No
  tracker rules are loaded"), names the place on any character, and shows
  an older export's lock state as the HUD does. `ChunkPanelGoldenTest`.
- [x] **B2. Menu tags.** `MenuTagTest`: a golden NPC in a locked chunk, an
  ocean chunk, none on a wrong character, and a v3 fixture as today.
  *Done as* `beb5de0`; an older export now tags only for its own
  character.
- [x] **B3. Bank warning.** `BankWarningTest`: custom-none-banks-on warns at
  a locked bank and never at rolled 13105; banks off never warns.
  *Done as* `dda744c`: the warning needs a locked bank row and
  `DecisionService.bankRoll` (the roll alone, from the root fields the
  goldens pin) saying it isn't rolled. No row, no warning, until E4.
- [x] **B4. Strict Mode on `DecisionService`; delete `FateRuleEngine`.**
  Readiness comes from `Trust`. Existing TravelGuardian*, StrictMode* and
  `FateLockedPluginTravelAccountBindingTest`, plus a golden check: every
  LOCKED destination blocks when fresh and bound, and none on a wrong
  character.
  *Done as* `d30b902`, with `StrictModeGoldenTest` and
  `rules/DecisionServiceTest` (FateRuleEngineTest's cases). The evaluator
  counts only the tracker's chunk decisions; a click refreshes the service.
- [x] **B5. HUD "Here" and "Status"** (visible: NOT_READY, wrong character,
  ocean). `HudStatusTest`.
  *Done as* `9f014d5`: a pure `HudStatus`; "Not ready", "Wrong account" and
  the tracker's locks show, and "Here" keeps the area name.
- [x] **B6. Chunk chat, locked alerts and the warnings count.**
  `FateLockedChunkEntryTest` and a golden walk from Lumbridge to Falador; a
  wrong character is silent and NOT_READY never alerts.
  *Done as* `90050ed`: another character is silent; chunks only the tracker
  maps are announced by coordinates.
- [x] **B7. Scene and minimap tints** through a pure `TintPolicy`.
  `TintPolicyTest` over the golden entries.
  *Done as* `be1b8ce`: a pure `TintPolicy`; the overlays tint through it.
- [x] **B8. World map tint, tooltip and frontier** (land only, as the web
  map). Golden frontier and `WorldMapTooltipTest`.
  *Done as* `41dda69`: a pure `WorldMapChunks` over `mappedChunks` and
  `isFrontier`; the golden frontier matches exactly.
- [x] **B9. World map pins.** `LockedAreaPinsTest`: the pins are the areas
  the tracker says are locked.
  *Done as* `6da5839`: the pins are placed again whenever the decision
  service changes (a login, another character).
- [x] **B10. Progress** in the HUD and the infobox, with today's counts as
  the fallback. `ProgressTest` (vanilla-mid stays 13/177 until E5).
  *Done as* `2fc13d7`: `Progress` and a shared `ProgressText`; hidden on
  another character.
- [x] **B11. Slayer** through `DecisionService.slayerTask`: master keys when
  the varbit value is known, Konar's " in <place>" suffix kept.
  `SlayerDecisionTest` and `SlayerChatTest`.
  *Done as* `66e80d1`: `SlayerAssignment` keeps Konar's place; a known
  master's own list, or a named place, decides alone. A new account forgets
  the task.
- [x] **B12. Over-tier gear** from `itemRules` and `unlocks.equipment`.
  `GearDecisionTest`: every `itemRules` id in vanilla-mid agrees with
  today's `itemTiers` answer.
  *Done as* `57f7848`: `ItemTier`, with older exports rated through
  `LegacyRules`; cb6301a fixed the startup test it broke.
- [x] **B13. Nearest bank and shop** from the BANKS and SHOPS rows (R5, part
  one). `NearestBankTest`: all 127 banks are candidates, the 19 the poi list
  misses are found, and an unusable bank is never chosen.
  *Done as* `9eeaa7a`: every BANKS and SHOPS row is a candidate, usable only
  when ALLOWED; shops now need their type rolled, as the tracker says.
- [x] **B14. `ChunkLocator` everywhere:** player, menu targets, bank
  warning, the Strict Mode origin and the overlays. Retires the deprecated
  `WorldPoint.fromScene(Client…)`, `client.getPlane()` and
  `getBaseX/Y` calls. A plugin test walks into a mocked instance whose
  template is LOCKED and alerts once; a source test forbids those calls and
  raw `getWorldLocation()` outside `ChunkLocator`.
  *Done as* `a6ca51a`: `Located` for the overlays' geometry,
  `LocationBoundaryTest`, and `TestWorld` for tests.
- [x] **B15. Strict Mode presentation (G10).** A pure `BlockNotice` and
  `EnforcementPresenter`: the label from the rules in its own case with no
  "(locked)"; the banner reads "Strict Mode blocked Varrock Teleport" with
  a "Pause Strict Mode for 60s" button; the chat line names Strict Mode and
  how to pause. The notice is staged before the consume, and a presenter
  failure means no consume. `EnforcementPresenterTest` and coordinator
  tests.
  *Done as* `ac34985`: labels keep the game's case; the notice is staged
  before the consume.
- [x] **B16. Strict Mode status everywhere (G10).** A pure
  `StrictModeStatusView` feeds the sidebar row and a new HUD line ("Strict:
  Active", "Paused · 42s", "Inactive"; hidden when off). An optional pause
  hotkey, unset by default. `StrictModeStatusViewTest`, a HUD render test
  and a hotkey test.
  *Done as* `b2eb078`: the view is worked out once per tick for both; the
  hotkey is `pauseStrictModeHotkey`.
- [x] **B17. No legacy-engine callers remain.** `DecisionBoundaryTest`: no
  production class outside `LegacyRules` and `RulesSnapshot` calls
  `lockStateAt`, `isUnlocked`, `isFrontierChunk`, `monsterReach`,
  `nearestUsable*`, `isBankUnlocked`, `getItemTiers`, `labelAt` or the
  progress getters.
  *Done as* `3013d53`: `Progress` fields renamed so the scan can tell them
  from the bundle's getters.

### Phase C: web, additive bundle fields (ships before the plugin)

Web `main` deploys on every push and only the owner merges. Each field
commit carries its builder, the field and its golden answer together.

- [x] **C1. `contracts/golden-bundles/bundle-contract.json`** and its test:
  bundle version 4, the installed builds, every frozen root, `rules` and
  overlay field with its JSON type, the required fields, and the fields that
  are parsed but unread. Mutation checks: bumping the version and dropping
  `freeAreas` both fail.
  *Done as* web `aef22e0`: the contract is listed in the golden manifest, so the pin script copies it.
- [x] **C2. Slim the exported `rules.chunks`:** drop snapshot `chunkKey`
  and `counts` and row `key` at the wire step only; the app's own snapshot
  is unchanged. Regenerated goldens keep every `.expect.json` answer, which
  shows nothing a plugin reads changed. Before the web PR merges, a plugin
  worktree at 4e37895 pins the web branch's goldens and its `GoldenBundle*`
  tests pass.
  *Done as* web `bc90ff0`: `wireChunks`; relay bodies 199 to 155 KiB. With the slimmed goldens, 4e37895 passed its whole suite (590) and the Stage 2 branch its (932).
- [x] **C3. The size check covers every golden run**, each relay body under
  240 KiB.
  *Done as* web `65543c6`: against the relay's own `MAX_REQUEST_BYTES` less 16 KiB.
- [x] **C4. Pure functions, no wire change:** `freeAreasFor(gameModeId,
  customMode)` with an export guard that refuses (and retries) when the
  global disagrees with the run; `chunkEntry` extracted from the snapshot
  and manifest code; `runProgress`, shared with RunCard; Slayer
  `slayerDecisions` with the status mapping, shared with the panel;
  `interiorEntry` with a reviewed interior-to-area map (including Mor Ul
  Rek); `bankDecisions` with physical chunks from the facility model. Each
  with unit tests (Keldagrim; an interior with no entrance; an area lock
  beats an open entrance; bank 11066's physical chunks; all 127 bank
  statuses equal their rows).
  *Done as* web `f17605f`: `freeAreasFor` with `RunChangedError`; 30e2d92 `chunkEntry`; 9ef4880 `runProgress`; 3252f0a and 7e8bb29 `slayerDecisions` (merged keys rank UNKNOWN above NOT_READY); 52bda1c `interiorEntry` with `data/interiorAreas.ts`, and 3fceeba makes the app's own content checks use the same owners (Mor Ul Rek and eight more areas' interiors now need their area); 08fd0e9 `bankDecisions` with the shared `bankAccess`.
- [x] **C5. Golden generator, schema 2:** the frontier with the account;
  new runs `chunked-sailing`, `vanilla-sailing` and `vanilla-interiors`
  (with Slayer levels, so masters differ); a self-consistency test that the
  bundle's fields equal the pinned answers. Existing answers stay; new
  answers are additive keys.
  *Done as* web `8399abb`: with these goldens 4e37895 and the Stage 2 branch fail only the chunked-sailing frontier.
- [x] **C6. `rules.chunkEntries` and `rules.places`** (R1, R4), with a test
  that every `chunkEntries` value equals the `rules.chunks` entry where both
  exist.
  *Done as* web `b75e632`: `places.json` pins the places once. With Sailing only 7 of 548 ocean chunks are reached (the route walk crosses the sea only between neighbouring ocean chunks), so the rest read NOT_READY: an owner question.
- [x] **C7. `rules.frontier`**, Chunked runs only (R4).
  *Done as* web `7ff5b54`: sent whenever the run is Chunked; it needs no chunk data.
- [x] **C8. `rules.banks`**: `{name, status, reason, at, physical}` per
  bank id, with `bankStatus` and `bankAt` answers (R5).
  *Done as* web `d857989`: `banks.json` pins `bankAt` (138 chunks, none shared).
- [x] **C9. `rules.freeAreas`**, and the root `freeAreas` from the same pure
  function (R6).
  *Done as* web `e7f933b`: the root falls back to the global only without rules.
- [x] **C10. `rules.progress`** `{unit, unlocked, total, chunks}` (R9).
  *Done as* web `ccd409c`: `progress.chunks` gives the land chunks owned, for the plugin's percentage.
- [x] **C11. `rules.slayerTasks`**, keyed like `slayerChunks`, `{status,
  reason}` (R16).
  *Done as* web `33008a5`: reasons follow the Slayer panel's badge.
- [x] **C12. Snapshot `kind`, `area` and `entryReason`** inside
  `rules.chunks` (Gson ignores them in installed builds).
  *Done as* web `6d92976`: `chunkEntryReason` beside `chunkEntry`.
- [x] **C13. `rules.capabilities`** listing only the sections present, and
  new `cases.json` entries: without the Stage 2 sections, an unknown
  capability, a stray root `unlockedChunks`, no root `freeAreas`.
  *Done as* web `705a07b`: the two cases installed builds can't pass (stray `unlockedChunks`, no root `freeAreas`) are in `stage2SameAnswers`, which they don't read.
- [x] **C14. Docs:** `docs/online-relay.md` (the v4 section) and ROADMAP
  3b: new answers, frozen fields, capabilities and real sizes.
  *Done as* web `4f807d3`: relay requests are 187-191 KiB.

### Phase D: web, the travel table (ships before the plugin)

- [ ] **D1. `data/travelMethods.ts` and `utils/travelDecisions.ts`**
  with sample rows. Methods keyed by id: `label`, `unlocks` (ids from
  `MOBILITY_LIST`, `ARCANA_LIST` or `POH_LIST`), `match` (exactly one of
  `spell {book, name}`, `items`, `objects`, `npcs`), `options` keyed by exact
  option text with `to` (chunk keys), `status` and `reason`, an optional
  `advisory` flag and fairy-ring `codes`. The per-run `status` combines the
  method's unlocks with the destination's entry. Tests: unique ids; unlock
  ids exist; every `to` is a land, ocean or interior key; no denylisted
  option.
- [ ] **D2. Spells**, checked against the Chunk Picker Magic records,
  with regression rows for Senntisten, Carrallanger and both Ape Atoll
  spells (G7).
- [ ] **D3. Tablets and scrolls**, item ids cited from the OSRS Wiki.
- [ ] **D4. Jewellery and equipment**, ids checked against the
  pinned equipment catalogue; Digsite pendant, Slayer ring, Xeric's
  talisman, Drakan's medallion and the Necklace of passage's Eyrie option
  get their own unlocks (G6).
- [ ] **D5. Networks and their stops** (fairy rings, spirit trees,
  gliders, charters and the rest), advisory in Stage 2, each stop checked
  against the network's nodes in chunk content.
- [ ] **D6. Export `rules.travel`** with golden `travel` answers
  (`"<method>|<option>": status`) and the size check.

### Phase E: the plugin reads the new sections

- [ ] **E1. Re-pin and prove compatibility.** Run
  `scripts/pin-web-contracts.sh` at the web `main` commit with C and D.
  Then, in a worktree at 4e37895, pin the same commit and run its
  `GoldenBundle*` tests: they must pass, and the only allowed failures are
  the intended ones, such as the Sailing-aware frontier.
- [ ] **E2. Places into `DecisionService` (R1, R4).**
  `unmappedChunksReadUnauthoredUntilStage2` becomes
  `everyPlaceMatchesTheTracker`.
- [ ] **E3. Frontier from `rules.frontier` (R4).** The chunked-sailing
  golden.
- [ ] **E4. Bank table:** interior warnings and the nearest bank (R5).
  `expect.bankAt`.
- [ ] **E5. Progress (R9).** `expect.progress`.
- [ ] **E6. Slayer per master (R16).** `expect.slayer`.
- [ ] **E7. The capabilities gate (R15):** a section is read only under a
  known capability id; unknown ids are ignored; `rulesVersion` stays
  informational. Cases.
- [ ] **E8. Labels and reasons** from `area` and `entryReason`.
  `HudStatusTest` and the chat test.
- [ ] **E9. Optional: accept a v4 bundle without root `chunks`** (R6), since
  "has rules" now means the manifest is present. A case.

### Phase F: Strict Mode matches travel by id

- [ ] **F1. Read `rules.travel` into a validated `TravelTable`.** Lenient
  like `knownMobility`: a bad row is dropped, never the bundle. Each match
  kind counts only for its own menu types; denylisted options are dropped;
  caps of about 1,000 methods, 64 options and 64 ids per method, and
  120-character reasons. `TravelTableTest` and new `GoldenBundleCasesTest`
  cases.
- [ ] **F2. Classify travel by id (G7, G13).** `IntentClassifier` replaces
  `TravelActionResolver`. `IntentClassifierTest` and
  `GoldenBundleContractTest.travelMatchesTheTracker`.
- [ ] **F3. Use the tracker's per-option decision (G6)**, with the
  one-destination invariant. Golden decisions for each G6 item; a rewritten
  `TravelRuleEvaluatorTest`.
- [ ] **F4. Tags use the same classifier and decision (G14).** Delete
  `GuardedActionFactory`'s travel path; `TagConsistencyTest` (a tag only for
  an exact LOCKED decision) replaces the isolation test.
- [ ] **F5. Delete `Teleports.java` and the menu-text matrix tests (G7).**
- [ ] **F6. Readiness says when the rules have no travel table** (older
  saved rules and backups): Strict Mode shows Inactive with the reason.
- [ ] **F7. Alternatives from the table:** single-destination rows the
  player carries or wears whose option is ALLOWED, ranked by the
  destination's area. `TravelAlternativeFinderTest`.

### Phase G: finish

- [ ] **G1. Docs.** Plugin: README, CONTRIBUTING (the contract, the frozen
  fields and the Strict Mode invariant: an id match, one destination, "the
  destination or the unlock it needs is locked"), the Hub review notes
  (tags and Strict Mode now cover interiors and instances), and checklist
  rows for interiors, instances, ocean, boats, tablets, spellbooks, the
  passage Eyrie, a Digsite Rub that is not blocked, and the HUD countdown.
  Web: the RuneLite guide text in `data/runeliteGuide.ts`.
- [ ] **G2. Release.** A login-screen client test with an empty RuneLite
  home, as in Stage 1; the web release; then the Plugin Hub pull request
  once the owner has merged the plugin.

## Owner decisions and pending steps

Each has a default the plan follows unless the owner says otherwise.

1. **An owned interior behind a locked entrance** (Keldagrim rolled, its
   entrance 43,58 locked) reads **Locked**, as the web decides today. Adding
   Keldagrim's other entrances to the data is the better fix and can follow.
2. **What Strict Mode may block in Stage 2:** spells, tablets, scrolls and
   teleport items. Fairy rings, spirit trees, house portals and boats are
   tag-only until a later release.
3. **Which unlock a spellbook's tablet needs:** the web's existing rule
   (`areaAccess.ts` gates an Arceuus spell or tablet on the Arceuus
   Spellbook); standard tablets need Teleport Tablets.
4. **Slayer status mapping:** ready → ALLOWED; area-locked → LOCKED;
   Slayer-, quest- or combat-locked and access-blocked → NOT_READY;
   access-unknown and no-location → UNKNOWN.
5. **The 24 banks with no located facility, including 10810,** are
   mirrored as the web has them and listed for review later. (With the
   reviewed facilities and interiors counted, 20 remain; C4f.)
6. **The ocean with Sailing.** The route walk crosses the sea only between
   neighbouring ocean chunks, and the ocean data near Port Sarim is a small
   patch of its own, so a Sailing run reaches 7 of the 548 ocean chunks and
   the rest read NOT_READY (no alerts, but "Not ready" at sea). The export
   mirrors the app; changing the sea model is a web decision for later.
7. **Interior owners in the app's own checks** (web 3fceeba): content in
   interiors of Mor Ul Rek and eight more areas' basements and dungeons now
   needs its area, as their interior entries do. Default: keep.

## Test and file map (new files)

Classes are in `com.fatelocked` unless a folder is given.

| File | Purpose |
|---|---|
| `rules/Decision`, `rules/Trust`, `rules/RulesSnapshot`, `rules/DecisionService`, `rules/LegacyRules` | The one reader of the rules |
| `ChunkLocator`, `Located` | Player, actor and scene tile to a rules chunk |
| `MenuFacts`, `IntentClassifier`, `TravelTable` | Travel matched by id |
| `BlockNotice`, `EnforcementPresenter`, `StrictModeStatusView` | Strict Mode wording and status |
| `TintPolicy`, `NearestBank` | Pure helpers for overlays and hints |
| `RulesCoreBoundaryTest`, `DecisionBoundaryTest` | Source scans |
| `DecisionServiceGoldenTest`, `LegacyRulesTest`, `GoldenBundleMutationTest` | Decisions against the goldens |
| `ChunkLocatorTest`, `MenuFactsTest`, `StrictModeGateTest`, `IntentClassifierTest`, `TravelTableTest`, `TagConsistencyTest` | Strict Mode and tiles |
| Web `contracts/golden-bundles/bundle-contract.json` | Frozen fields |
| Web `utils/chunkEntry.ts`, `utils/runProgress.ts`, `utils/slayerDecisions.ts`, `utils/interiorEntry.ts`, `utils/bankDecisions.ts` | Pure answers the export and the app share |
| Web `data/travelMethods.ts`, `utils/travelDecisions.ts` | The travel table and its per-run decisions |
