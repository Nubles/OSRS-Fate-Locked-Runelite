# Plugin overhaul design

Date: 2026-09-24. Status: **proposed, waiting for the owner's approval and four decisions.** Evidence: [`docs/reviews/2026-09-24-plugin-review.md`](../../reviews/2026-09-24-plugin-review.md).

## Why

The review found 84 distinct problems. Most come from five structural causes, so fixing them one at a time would keep producing new ones:

1. **No owner for the active rules.** Relay, file watcher, reload button, toggle and paste all write the same two fields, with no source, age or precedence (S1, S2, A2, A6, R7, A12).
2. **Several answers to "is this locked?".** A legacy engine drives overlays, HUD and alerts; the manifest engine drives the sidebar, tags and Strict Mode; Strict Mode has two trust gates of its own (R2, G2, G8, U4).
3. **Rules re-derived in Java** instead of looked up from what the web app authors, with a drift test that no longer tests the real plugin (R1, R3–R6, G6, G7, D4, D9).
4. **No thread model or session.** State changes on three threads and after shutdown (A5, A8, A10).
5. **One 1,822-line plugin class and one 931-line panel** that own a dozen responsibilities each, tested through reflection and invented game text (A, U, D14).

## Constraints

- **Live Hub users.** The Plugin Hub runs `52f45f5`. Each stage below ends in one pull request to `runelite/plugin-hub` that bumps the pinned commit: the owner opens it, RuneLite's reviewers read its diff and merge it, and players' clients update on their next start. Keep each release small enough to review.
- **The bundle stays version 4.** New data is additive and optional; older plugins ignore unknown fields (verified). Never repurpose a field, and never drop a root field a released plugin still reads.
- **Config keys and local files stay readable.** Settings move behind a `settingsVersion` migration; the per-account file layout migrates today's global files once.
- **Inbound-only network boundary.** One GET to the relay, consent first, nothing uploaded. Any hand-off to the web app is player-started and local (clipboard).
- **Verification.** In cloud sessions `repo.runelite.net` is blocked, so the repository CI is the compile and test gate unless that host is allowed. Nothing replaces a short in-game check by the owner before each Hub release.

## Target architecture

A thin RuneLite adapter layer around a RuneLite-free core. The core compiles with plain `javac` and Gson, so it can be tested without a client and its contract tests can also run in the web app's CI.

```
FateLockedPlugin            thin adapter: Guice fields; @Subscribe forwards; startUp/shutDown open/close a session
 └─ PluginSession           one per start; token checked by every queued task
     ├─ Registrations       LIFO release of overlays, listeners, button, timers, infoboxes, map pins
     ├─ ClientThreadGate    the only way in from Swing, timers and HTTP callbacks; core state is game-thread only
     ├─ LoginTracker        real login, account change and scene load (account hash, not LOGGED_IN)
     ├─ LocalStateStore     per-account, versioned files; loads never throw; background writer; safe with two clients
     ├─ RulesStore          the one owner of the active rules
     │    sources           RelaySource · ImportSource (clipboard, hotkey) · BackupFileSource (one-shot)
     │    BundleCodec       bytes → RulesSnapshot or a typed rejection; never "empty rules"
     │    FreshnessPolicy   relay: last confirmation; backups: export time; one answer for guard, panel, HUD
     ├─ Sync                RelayContract (pure) · RelayClient (the Hub's one GET) · SyncMachine (pure reducer)
     ├─ DecisionService     the only reader of the rules: chunk, interior, ocean, bank, item, slayer, travel
     │    ChunkLocator      world point → chunk, with instances and world views
     ├─ StrictMode          MenuFacts adapter → IntentClassifier → StrictModePolicy → EnforcementPresenter
     ├─ Detection           signal adapter → pure detectors → DetectionGate → ObservationStore
     └─ Presentation        PluginState → pure presenters → thin Swing views; overlays read cached models;
                            one wording module (Copy/Terms) and one Palette
```

Contracts between the repositories, each pinned by tests on both sides:

- **Golden bundles.** A web script writes bundles for a fixed matrix of runs plus the expected answers from the web app's own functions (per chunk, bank, Slayer task, item and account case, and accept/reject for malformed input). The plugin copies them at a pinned web commit and a JUnit test runs them through the real codec and decision service. This replaces the web app's `pluginSim`.
- **Relay fixtures.** Status, headers and body cases the worker test asserts it produces, and the plugin test asserts it classifies correctly.
- **Detector fixtures.** Real game messages and signals with the expected event JSON, used by plugin tests and by a web parity test through the web app's own classifier.

## Stages

Each stage leaves the plugin releasable. Finding ids refer to the review.

### Stage 0: Safety release

*Size:* Small: independent fixes, no redesign.

**Goal.** Stop the harm live Hub players can hit today: the crash on start, rules that vanish, false blocks, blocking on the wrong character, and a Strict Mode status that says On when it isn't.

**What players notice.** Strict Mode blocks only what it should and says when it is inactive. Rules no longer vanish. Connecting says "Waiting for confirmation" instead of red "expired". Fewer repeated warnings.

**Web app work.** Roll Inbox copy ("Listening", "will queue here") and the ROADMAP Hub pin.

**Ships as.** One Plugin Hub release (also carries the 8 MiB cap already on main).

**Findings addressed (19):** S2, A2, R7, R14, A11, G1, G2, G3, G5, G12, G13, D1, A3, A4, S3, U7, U10, A17, C2.

**Done when:** a test pins that local files can't stop startup; a test pins the single place a click can be consumed and that walking is never consumed; one trust gate covers every consume path, tested for unbound, wrong-character and logged-out cases; the Hub notes, README and web ROADMAP match the code; the in-game checklist passes.

### Stage 1: Reliable rules and connection

*Size:* Medium to large: the core rewiring.

**Goal.** One owner for the active rules that saves them, knows their source and age, and sits behind one freshness rule; one thread model; per-account local files; a connection state machine with clear states; golden bundles that pin today's correct land rules before anything else moves.

**What players notice.** Rules survive restarts and offline starts. Check now. Clear connection states. Two clients and several accounts stop interfering. Quick detector fixes stop the worst mislabels and junk nudges.

**Web app work.** Golden bundle generator and the Java contract test; lenient version-tag compare on the relay; an optional "gone" marker on Disconnect.

**Ships as.** One Plugin Hub release, one web release, a relay deploy.

**Findings addressed (29):** S1, A6, A12, G9, R11, R3, D3, D4, D5, D6, D7, D9, D11, D15, D19, A5, A8, A10, A14, A16, S4, S5, S6, S7, S8, S11, S12, S13, C1.

**Done when:** rules survive a restart and an offline start in tests; every relay outcome in the fixtures maps to a visible state; golden bundles pass in the plugin CI; two simulated clients keep each other's history; a CI job also builds with the Hub's standard build.

### Stage 2: One source of truth for "is this locked?"

*Size:* Large: both repositories.

**Goal.** The web app writes every decision into the bundle (interiors, ocean, frontier, banks, free areas, progress, Slayer per master, the travel table with unlock ids). The plugin looks decisions up through one decision service that every surface uses. Strict Mode becomes a small policy on top, matching travel by id.

**What players notice.** Interiors, dungeons and instances show real lock states. The sidebar, HUD, overlays, alerts and Strict Mode always agree. Teleports are checked against the right unlock and place.

**Web app work.** Additive bundle fields (bundle version stays 4), extended golden bundles, a travel table built from its transport data.

**Ships as.** Web release first (older plugins ignore new fields), then one Plugin Hub release.

**Findings addressed (15):** G6, G7, G10, G11, G14, R2, R10, R1, R4, R5, R6, R9, R13, R15, R16.

**Done when:** every surface reads `DecisionService` (no other caller of the legacy engine remains); golden bundles cover interiors, ocean, frontier, banks, Slayer masters and travel; Strict Mode matches travel by id and treats anything with several destinations as Unknown.

### Stage 3: Sidebar and in-game display

*Size:* Large: mostly the plugin, plus the web guide.

**Goal.** A status-first sidebar driven by pure presenters; settings in RuneLite's config panel with a migration; one wording module and palette; chunk borders drawn tile by tile; alerts once per locked area; caching instead of per-frame work.

**What players notice.** A status card that answers "are my rules current and for this character?", reasons on every locked row, borders that actually draw, colour-blind-safe colours, far fewer settings.

**Web app work.** The RuneLite guide's settings list, wording and screenshots ship in the same release.

**Ships as.** One Plugin Hub release and one web release together.

**Findings addressed (15):** U13, R12, A9, A15, U3, U8, U11, U12, U15, U16, U17, U18, U20, U21, U22.

**Done when:** a presenter test covers every row of the status table in the UX report; settings migrate from the old keys; the web guide's settings list and screenshots match the release.

### Stage 4: Detected events

*Size:* Medium to large, depending on decision 2.

**Goal.** Either a player-started hand-off (Copy for tracker in RuneLite, Paste from RuneLite in the web Roll Inbox) with detectors rebuilt on web ids and real-message fixtures, or retiring the history and the web pipeline and keeping chat reminders.

**What players notice.** Either detected levels, quests, diaries, bosses and clues reach the Roll Inbox in two clicks, or the dead inbox is gone from both sides.

**Web app work.** Paste from RuneLite and candidates for uncertain events, or removal of the dormant pipeline.

**Ships as.** One Plugin Hub release and one web release.

**Findings addressed (6):** D8, D10, D14, D16, D17, D18.

It also finishes D1, D3, D4, D6, D7, D9 and D11, whose copy and quick fixes ship in Stages 0 and 1.

**Done when:** real-message fixtures drive both the plugin detectors and a web parity test; per the decision, either a copied batch appears in the web Roll Inbox, or the history, counters and web pipeline are gone.

## Decisions for the owner

1. **What should Strict Mode be allowed to block?**
   - Travel only (recommended): It blocks only teleports and transport it can match exactly. NPC, object, bank and equip checks become warnings and menu tags. Walking is never blocked. This is the smallest Plugin Hub exposure.
   - Everything it blocks today, fixed and disclosed: Keep NPC, object, bank and equip blocking behind the same trust gate, with an allowlist of options, and describe each category in the Hub notes.
2. **What should happen to the Roll inbox?**
   - Build a clipboard hand-off (recommended): "Copy for tracker" in RuneLite, "Paste from RuneLite" in the web Roll Inbox. Nothing is uploaded, so the Hub boundary stays inbound-only. Detectors are rebuilt on web ids in Stage 4.
   - Retire it: Remove the local history, the counters and the web app's dormant pipeline; keep the chat reminders. Smaller, but detection never feeds the tracker.
3. **Which backup import methods should stay?**
   - Clipboard plus one "Load newest backup file" button (recommended): Keep clipboard import and its hotkey for players without online sync. Drop the paste box, the 1-second folder watcher, Auto-reload and "Reload from file", which cause S2 and A6.
   - Keep every method, fixed: All four methods stay, each routed through the new rules owner with precedence rules.
4. **Where should the settings live?**
   - RuneLite's config panel only (recommended): About 15 settings in RuneLite's own panel, the RuneLite convention. The sidebar keeps the online-sync consent and the Strict Mode toggle, and shows status and actions.
   - Keep them in the sidebar too: Both places stay, merged to the same 15 settings and kept in step.

## Owner actions outside the code

- Open the Plugin Hub pull request that bumps the pin after each stage, and run the in-game checklist first.
- Update the Hub entry's repository URL from `RS3-Fate-Locked-Runelite` to this repository's current name.
- Deploy the relay worker (`wrangler deploy` from `workers/fate-relay`) when a stage changes it.
- Optionally allow `repo.runelite.net` in the cloud environment's network settings, so sessions can build and test the plugin locally instead of waiting on CI.

## Out of scope

- New game-mode features. The overhaul changes how the plugin applies the web app's rules, not the rules.
- Uploading anything from RuneLite. The plugin stays inbound-only.

