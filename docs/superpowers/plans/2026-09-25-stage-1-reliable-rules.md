# Stage 1: Reliable Rules and Connection Implementation Plan

> Steps use checkbox (`- [ ]`) syntax for tracking. Each task is one commit
> unless it says otherwise.

**Goal:** Stage 1 of the [overhaul design](../specs/2026-09-24-plugin-overhaul-design.md):
one owner for the active rules that saves them and knows their source and
age; one thread model; per-account local files; a connection state machine
with clear states; golden bundles that pin today's land rules before
anything else moves; and the quick detector fixes.

**What players notice:** rules survive restarts and offline starts; Check
now; clear connection states; two clients and several accounts stop
interfering; fewer mislabelled events and junk nudges.

**Tech stack:** Java 11, RuneLite 1.12.39, Gradle 8, JUnit 4, Mockito 4,
OkHttp MockWebServer; React/TypeScript/Vitest for the web app; the
Cloudflare Worker relay.

**Findings (29):** S1, A6, A12, G9, R11, R3, D3, D4, D5, D6, D7, D9, D11,
D15, D19, A5, A8, A10, A14, A16, S4, S5, S6, S7, S8, S11, S12, S13, C1.
Evidence for each is in [the review](../../reviews/2026-09-24-plugin-review.md)
on the review branch; the briefs behind this plan re-checked every finding
against `874b9d1` (Stage 0 merged).

## Global constraints

- **Branches.** Plugin: `claude/stage-1-reliable-rules` from `main`
  `874b9d1`. Web and relay: `claude/stage-1-golden-bundles` in the
  isolated worktree `.worktrees/stage-1` of the local web clone, from web
  `main` `9617a16`. Never touch the web clone's own working tree.
- **Network boundary.** The plugin's only request stays `GET
  https://fate-relay.fatelocked.workers.dev/r/<32-hex>` with optional
  `If-None-Match`, consent-gated. The single `new Request.Builder()` stays
  in `TrackerConnectionController`; `PluginHubNetworkBoundaryTest` and
  `verifyPluginHubJar` must stay green, and if a class is ever extracted
  both gates move with it in the same commit.
- **Compatibility with live Hub users.** Bundle stays version 4; never
  repurpose a field; the web keeps sending `state.linkedAccount` (52f45f5
  reads it). Config keys stay readable; a removed setting's stored value is
  ignored. Old global files under `.runelite/fate-locked` are read, never
  rewritten or deleted, so a downgrade to the Hub build still works. No new
  file may match `fate-locked-bundle*.json`.
- **Threads.** Anything that reads game state runs on the client thread
  through one gate that takes only a `Runnable` (the `BooleanSupplier`
  overload re-runs a task that returns false). Parsing and file writes run
  off the game thread.
- **Verification.** Plugin: `gradle clean check --no-daemon` before every
  push. Web: `npx vitest run`, `npx tsc --noEmit`, `npm run build`. The web
  deploys on every push to its `main`, so web and relay work land only
  through pull requests the owner merges.
- **No publishing without the owner.** No merge, relay deploy, web deploy
  or Plugin Hub pull request without asking.
- **Commits.** Small, one finding or one mechanical step each, with a body
  that says why and which test pins it, in the style of Stage 0.

## Decisions taken in this plan

- **The contract test runs only in plugin CI.** The design suggested the
  web app's CI could also run it, but the web repository's
  `scripts/runeliteRepositoryBoundary.test.ts` forbids Java and Gradle
  there. The web side checks that its golden files are current; the plugin
  checks that it agrees with them.
- **"Gone" is `404 {"gone":true}`, not 410.** On a 404 the Hub build
  52f45f5 shows "Pairing request expired" (the right prompt: reconnect)
  and 874b9d1 shows "No recent update". On a 410 both would show "Tracker
  is unavailable" and back off, and the stream overlay would silently
  freeze.
- **Saved rules belong to the pairing, not the OSRS account.** They must
  load before login, so `saved-rules.json` sits at the data-folder root,
  tagged with a hash of the pairing code (never the code).
- **The detection gate follows the design:** rules loaded, bound account
  matching the character, a normal world. An unbound tracker profile gets
  no roll reminders. (Owner confirmed, 25 September.)
- **Retiring the level-250 boss rule keeps only the exact boss detector.**
  Bosses outside it lose their nudge until Stage 4 rebuilds boss
  detection on NPC ids. (Owner confirmed, 25 September.)
- **Contract files are copied, never fetched at test time.** One script
  copies the golden bundles and the relay fixture from a web commit and
  records the commit and SHA-256 of each file; a CI step re-checks the copy
  against that commit.

## Phases

The order puts safety nets before refactors, and the rules owner before the
connection work that reports on it. Each phase leaves `main` releasable.

### Phase A: safety nets (no behaviour change)

- [x] **A1. Plugin: build each commit the way the Plugin Hub does (C1).**
  A CI job runs the Hub's own packager (plugin-hub-tooling `v4`, SHA-256
  pinned from the Hub's build log) on this commit, then runs
  `verifyPluginHubJar` against the jar it produced. `verifyPluginHubJar`
  gains `-PpluginHubJar=<path>`. Subject: `ci: also build each commit with
  the Plugin Hub's standard build`.
- [x] **A2. Web: write golden bundles with the app's own answers (R3).**
  `scripts/runelite-goldens.test.ts` (vitest; runs in check mode inside
  `npm test`) builds bundles through `buildBundlePayload(...,
  {requireRulesData:true})` for 12 runs: vanilla fresh, mid-run and a
  complete continent; Xtreme; Chunked fresh, walked and with Sailing;
  Custom with no free areas and banks off; Custom Lumbridge-only; an
  unbound profile; and a degraded export. Expected answers come from
  `chunkUnlocked`, `isFrontierChunk`, `isAreaReachable`,
  `isBankReachable`, the Slayer reach index, the item rules and
  `normalizeAccountName`. Randomness only through `drawFloat`; fixed
  system time; check mode compares parsed JSON so locale sort order and
  zlib bytes don't matter. Output `contracts/runelite/` (gzipped bundles,
  expect files, a manifest with SHA-256s; under 3.5 MiB).
  *Done as* `scripts/goldenBundles.test.ts` writing `contracts/golden-bundles/`
  for 10 runs, with chunk, area, bank, frontier and account answers (web
  PR #47). The Slayer reach index, item rules, Sailing and a degraded
  export are not among the runs yet.
- [x] **A3. Web: malformed and forward-compatible cases.** `cases.json`:
  inputs that must be rejected (empty, `{}`, `null`, v3 stub, v5, v4
  without chunks or rules, truncated JSON, bad FLGZ, a 9 MiB gzip bomb) and
  inputs that must keep the same answers (unknown fields, dropped optional
  fields).
  *Done as* 11 inputs to refuse and 5 changes to shrug off, written as
  recipes against a golden run so the file stays small (web PR #47). The
  reader applies them and compresses too: stored gzip bytes differ between
  zlib builds. The plugin checks them in `GoldenBundleCasesTest`.
- [x] **A4. Web: pin the relay's GET replies (relay fixtures).**
  `workers/fate-relay/contract/relay-get.json`, replayed through the worker
  by `contract.test.ts`: fresh, newer and older versions, 304 for bare
  validators, 404, KV failure, plus intermediary cases (HTML 200, missing
  payload, bad versions, mismatched ETag, 429 with and without
  Retry-After, 502) and transport cases (reset, timeout, oversized body).
  *Done as* `contracts/relay/relay-get.json`, replayed through the worker
  by `workers/fate-relay/relayContract.test.ts` (web PR #47). C1 turned the
  quoted and weak validator cases into 304s, C2 added "gone", and C5 added
  the rules the plugin holds, sent in full.
- [x] **A5. Plugin: copy the web contracts at a pinned commit.**
  `scripts/pin-web-contracts.sh <web-sha>` copies A2–A4's files into
  `src/test/resources/contracts/` and writes `PINNED` (repository, commit,
  SHA-256 per file). `.gitattributes` marks them binary so line endings
  never change them.
- [x] **A6. Plugin: check the golden bundles through the real codec and
  rule engine.** `GoldenBundleContractTest` (parameterized) loads each
  bundle as JSON and as `FLGZ:`, and asserts `lockStateAt`,
  `FateRuleEngine.entry`, `isUnlocked`, frontier, `isBankUnlocked` and the
  BANK row, `monsterReach`, item tiers, account pairs and the accept/reject
  cases. Known divergences (interiors R1, ocean and Sailing R4, banks R5,
  progress R9, per-master Slayer R16, root-field mutations R6/R10, account
  whitespace R11) assert today's plugin answer and are tagged with their
  finding, so each Stage 2 fix has to change them on purpose.
  *Done for* land chunks, areas, banks, frontier, v4 entries and `FLGZ:`,
  with R1 and R4 pinned; the accept/reject cases are in
  `GoldenBundleCasesTest`. `monsterReach` and item tiers wait for the runs
  A2 left out. B11 removed the R11 exceptions.
- [x] **A7. Plugin: classify relay replies in one pure `RelayContract`**
  (no behaviour change), and **check it against the relay fixtures**
  (`RelayContractFixtureTest`; transport cases through MockWebServer).
  The one reply still read otherwise than the fixture says is "gone",
  pinned as a known divergence until C15.
- [x] **A8. Plugin CI: fail when the contract copy differs from its pinned
  web commit.**

### Phase B: one owner for the rules

- [x] **B1. Remove dead code from the plugin class (A16):**
  `BOSS_LOOT_COMBAT_LEVEL`, unused imports, `rulesImportedAt`, the
  two-argument `setCallbacks`, `SlayerTaskDetector.cancel`.
- [x] **B2. Read diaries, spellbook and worn items through gameval ids
  (A14).** Same numeric ids; a test pins all 48 diary ids.
- [x] **B3. Log only locked travel that a pause lets through (G9a).**
- [x] **B4. Record a repeated blocked trip once (G9b)**, reusing the
  once-per-10-seconds chat decision.
- [x] **B5. Replace the paste box and folder watcher with "Load newest
  backup file" (owner decision 3).** Remove the `autoReload` setting, the
  paste box, the watcher and "Reload from file"; keep clipboard import and
  its hotkey (the button reuses the hotkey's path). The new button loads
  the newest `fate-locked-bundle*.json` once, off the game thread, as an
  explicit import. The startup read of the newest file stays until B10.
- [x] **B6. Drop work queued before the plugin was turned off (A8):** a
  `PluginSession` token checked by every queued task; `beginPairing`
  refuses once the controller is stopped.
- [x] **B7. Change plugin state only on the client thread (A5):** one
  `ClientThreadGate`; config changes, sidebar and overlay actions and
  startup go through it; world-map markers removed with `removeIf`. Pins
  the startup case (a bundle with gear tiers) the way 6279da0 pinned paste.
- [x] **B8. Build imported rules completely before switching to them
  (A12):** one immutable `ActiveRules` reference; markers, gear, Slayer and
  panel model are worked out from the candidate; one swap; announcements
  after, each isolated.
- [x] **B9. Parse rules and write local files off the game thread (A10):**
  relay payloads parse on the OkHttp thread before dispatch; one serial
  worker for writes.
- [x] **B10. Keep the last accepted rules across restarts (S1, A6).**
  `saved-rules.json` (FLGZ payload, source, export time, relay version,
  pairing tag); loads at startup only when nothing is active, as "Saved
  rules from <time>", never fresh for Strict Mode until the tracker
  confirms them; a matching pairing tag seeds `If-None-Match`, so the first
  check's 304 confirms them. A pure `RulesPrecedence` pins the order:
  saved never replaces active, the tracker wins while paired, explicit
  imports always apply and hand back to the tracker. Saved rules replace
  the startup read of the newest backup file, which otherwise shadows them
  (A6); that read stays only when there are no saved rules, so the first
  start after updating still finds an offline player's file.
  *Done:* saved rules keep their source, so saved tracker rules stay
  unfresh until the relay confirms them and saved imports keep counting
  from their export time.
- [x] **B11. Read the bound account from one place with the tracker's
  normalisation (R11)**, and **say which account the profile is for when
  another is logged in.** Removes the R11 divergence tags from A6.

### Phase C: the connection

Relay and web first; every relay change is compatible with 52f45f5 and
874b9d1. The relay deploy after C3 needs the owner's `wrangler login` and
go-ahead.

- [x] **C1. Relay: answer 304 to quoted and weak validators (S4).**
- [x] **C2. Relay: let the owner mark a code gone (S11):** `POST /r/<code>
  {token, gone:true}` with the owner token; GET answers `404
  {"gone":true}`.
- [x] **C3. Web: mark the code gone on Disconnect**, after any in-flight
  publish; **show only the code's last four characters**; document both.
  *Done in* web PR #47. The relay deploy waits for the owner's `wrangler
  login`; merging #47 before it would ship a What's New line that isn't
  true yet.
- [x] **C4. Plugin: move connection states and timing into a pure
  `SyncMachine`** (no behaviour change; the controller's concurrency tests
  guard the move).
- [x] **C5. Count a same-version reply as a check and forget the version
  after a 404 (S4).**
- [x] **C6. Say when the relay sends an unreadable reply (S5).**
- [x] **C7. Remember a rejected version instead of downloading it again
  (S13).** Must land before C12, or a v5 bundle is re-fetched every five
  minutes.
- [x] **C8. Schedule after every reply and stop republishing "Not
  connected"; remove the unused Preparing state and browser-failure path
  (S13).**
- [x] **C9. Time out, cap and cancel the tracker request (S12):** 20 s call
  timeout, 1 MiB body cap, cancel on stop, revoke and re-pair; the tick
  never dies on an exception.
- [x] **C10. Show connection changes in the order they happen (S12).**
- [x] **C11. Give each connection state its reason, local time and one
  action (S8):** a pure `SyncView`; the importer reports OK, FUTURE_FORMAT
  or INVALID; every fixture outcome maps to a visible state.
  *Done as* a typed `SyncReason` on each snapshot, a line under the
  connection rows saying why and what to do, and times on the player's
  clock (the chunk card too, which no longer goes stale).
  `RelayFixtureStatesTest` replays every fixture reply through the
  controller; "gone" shows as "No recent update" until C15. The action
  is named in the line; Stage 3's status card turns it into a button.
- [x] **C12. Add Check now; retry within 5 minutes after a failure (S6);
  honour Retry-After exactly (30 s–1 h).**
  Check now works once every 10 seconds and shows only with a pairing
  and online sync on. No jitter on the back-off: each client's failures
  already start at its own point in the minute.
- [x] **C13. Check every 5 minutes while logged out and at once on login
  (S6).** Only the login screen counts as logged out; a hop, a lost
  connection or a loading screen keeps the minute's checks. A login still
  waits out a Retry-After.
- [x] **C14. Ask before re-pairing and keep the working pairing until the
  new one delivers (S7);** turning online sync back on resumes the saved
  pairing instead of replacing it.
- [ ] **C15. Say "Disconnected in the web tracker" and show only the
  code's last four characters (S11).**

### Phase D: detectors and per-account files

- [ ] **D1. Read combat task names the way RuneLite does (D3).**
- [ ] **D2. Keep "Quest" in quest names and read the other scroll titles
  (D6).**
- [ ] **D3. Stop calling every monster over level 250 a boss (D4).**
- [ ] **D4. Stop treating caskets in loot as clue completions (D7).**
- [ ] **D5. Stop guessing which pet dropped (D11).**
- [ ] **D6. Remove the Pest Control detector, which could never fire
  (D15).**
- [ ] **D7. Name diary tiers with the tracker's ids (D9).**
- [ ] **D8. Delete dead detector code and unused resource files (D19).**
- [ ] **D9. Record and nudge only for the bound character on a normal
  world (D5).**
- [ ] **D10. Merge local files on write so two clients keep each other's
  history:** a lock sidecar, re-read, merge, unique temp file, atomic move;
  the audit log moves a damaged file aside instead of overwriting it.
- [ ] **D11. Keep history, audit log and Slayer state per account**
  (`accounts/<hash>/`; read-only migration from the global files).
- [ ] **D12. Remember completed diary tiers per account (D9 baseline).**

### Phase E: finish

- [ ] **E1. Web: move the web-only export checks out of the plugin parity
  test, then remove the simulation** once plugin CI is green at the pinned
  commit.
- [ ] **E2. Docs:** README, CONTRIBUTING, review notes, the in-game
  checklist (new rows for saved rules, Check now, re-pairing and Load
  newest backup file), bundle-size wording, and the web ROADMAP contract
  section.
- [ ] **E3. Release:** in-game checklist on the release commit; then, with
  the owner, the web release, the relay deploy and the Plugin Hub pull
  request.

## Owner decisions and pending steps

1. **Roll reminders for an unbound tracker profile:** none, as the design
   says (confirmed 25 September).
2. **Boss reminders:** only the exact boss detector's bosses until Stage 4
   (confirmed 25 September).
3. **Relay deploy (pending):** Phase C's relay changes, and the undeployed
   changes from web PR #45, need the owner's `wrangler login` and
   go-ahead.

## Test and file map (new files)

| File | Purpose |
|---|---|
| `scripts/pin-web-contracts.sh` | Copy web contract files at a commit |
| `src/test/resources/contracts/` | Golden bundles, cases, relay fixture, `PINNED` |
| `GoldenBundleContractTest`, `RelayContractFixtureTest` | Contract checks |
| `rules/RulesStore`, `rules/RulesPrecedence`, `rules/ActiveRules` | Rules owner |
| `ClientThreadGate`, `PluginSession`, `SerialWorker` | Thread model |
| `sync/RelayContract`, `sync/SyncMachine`, `sync/SyncView` | Connection |
| `AccountBinding`, `detectors/DetectionGate` | Account and detection gates |
| `LocalFileMerge` | Two-client-safe local files |
