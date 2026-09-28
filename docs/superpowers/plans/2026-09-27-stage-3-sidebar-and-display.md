# Stage 3: Sidebar and In-Game Display Implementation Plan

> Steps use checkbox (`- [ ]`) syntax for tracking. Each task is one commit
> unless it says otherwise.

**Goal:** Stage 3 of the [overhaul design](../specs/2026-09-24-plugin-overhaul-design.md):
- **The sidebar** leads with a status card and is driven by pure presenters.
- **Settings** live in RuneLite's config panel, with a migration from the old keys.
- **One wording module and one palette** serve every surface.
- **In game:** chunk borders are drawn tile by tile, alerts fire once per locked area, and caching replaces per-frame work.

The owner set the bar on 27 September 2026: "make the plugin look fantastic, really nice, refined and premium". So the
plan adds a design language (below), and the owner sees renders of the real code before the views are wired in.

**What players notice:**
- A status card that answers "are my rules current, and for this character?".
- Reasons on every locked row.
- Borders that actually draw, and colour-blind-safe colours.
- Far fewer settings.
- OSRS artwork instead of text symbols.

**Tech stack:** Java 11, RuneLite 1.12, Gradle 8, JUnit 4, Mockito 4 for the plugin; React/TypeScript/Vitest for the web
app.

**Findings (15):** U13, R12, A9, A15, U3, U8, U11, U12, U15, U16, U17, U18, U20, U21, U22. Evidence is in
[the review](../../reviews/2026-09-24-plugin-review.md). Four briefs re-checked every finding against plugin `b7d77b7`
(the Hub build `4c2bf97` plus docs) and web `6369585`: sidebar and settings, in-game display and performance, the web
guide, and RuneLite's UI kit with OSRS icon candidates. Where a brief measured something, this plan quotes it.

## Global constraints

- **Branches.**
  - Plugin: `claude/stage-3-sidebar-and-display` from `main` `b7d77b7`.
  - Web: `claude/runelite-guide-stage-3` in the worktree `.worktrees/stage-2` of the local web clone, from web `main`
    `6369585`. Never touch the web clone's own working tree.
- **Order of release.** Stage 3 needs no new bundle data, so the plugin can go first:
  1. The plugin pins the web pull request's head commit, which carries the wording contract (G2).
  2. The owner merges the plugin, and the Plugin Hub pull request follows.
  3. The web pull request merges only after the Hub does, because the guide describes the new sidebar.
  4. A docs commit re-pins the plugin at the web merge commit, as #22 did.
- **Network boundary.** The plugin's only request stays the consent-gated
  `GET https://fate-relay.fatelocked.workers.dev/r/<32-hex>`. OSRS artwork comes from the game cache through
  `SpriteManager` and `ItemManager`, never from the network. `PluginHubNetworkBoundaryTest` and `verifyPluginHubJar` stay
  green.
- **Compatibility.**
  - The bundle is unchanged.
  - Config keys stay readable. A changed setting gets a new key, never an old key with a new type, because RuneLite
    overwrites an unparseable value with the default before `startUp` runs.
  - Retired keys are kept, not unset, for one release, so a Hub rollback or an older build on a synced profile still
    reads them.
- **Hub review.** Borders and tints show only the player's own rules, inside the loaded scene; the menu tag only appends
  text. The per-frame work the review would flag goes (the world map loop, the scene fill). Deprecated APIs in touched
  overlays are migrated.
- **Threads.** Presenters are pure and run where their inputs are read. Swing views change only on the Swing thread.
  Overlays read models cached on the client thread.
- **Verification.**
  - Plugin: `gradle clean check --no-daemon` before every push.
  - Web: `npx vitest run`, `npx tsc --noEmit` and `npm run build`.
  - `gradle previews` renders every sidebar state; the owner reviews them (B4).
  - Before release, a login-screen client test with an empty RuneLite home, and one with a Stage 2 profile to prove the
    settings migration (H1).
- **Publishing.** The owner merges every pull request. Claude asks before pushing a new branch, opening a pull request,
  or opening the Plugin Hub pull request.
- **Commits.** Small: one finding or one mechanical step each. The body says why and which test pins it.

## Decisions taken in this plan

The briefs raised a dozen choices. The owner asked for work to go ahead without option menus, so each is decided here;
the pull request lists every one that changes what players see, and any can be reversed.

1. **The look is RuneLite's own, refined.** RuneLite's dark theme and RuneScape fonts, cards on the panel background,
   sentence case, even spacing, and one accent: the web app's gold (`#fbbf24`), used sparingly. The plugin's mark is the
   crystal key, the web app's own icon.
2. **OSRS artwork, never text symbols.** RuneLite's fonts have no ✓ ⚠ ○ ✕ ▸ ▾ glyphs (the display brief checked
   `Font.canDisplay`), so today's HUD and content-box marks already draw as missing glyphs. Every mark becomes a game
   sprite or item image, or a shape drawn in code. In-game text says the status in words.
3. **Colours.** One `Palette` with Default, Colour-blind safe and Custom presets. Every text colour is at least 4.5:1 on
   RuneLite's greys. "Locked" is never carried by hue alone: rows say it in words, scene edges are dashed over a dark
   underlay, and the world map outlines the unlocked area.
4. **The world map shades locked land dark, like fog of war, and leaves unlocked land clear.** 88–97% of land is locked
   for most of a run, so a red tint would cover the map. The frontier keeps a light fill in Chunked mode.
5. **Defaults keep what players see today.** Merged settings default to today's behaviour:
   - the locked-area alert is chat, sound and a screen fade;
   - area announcements are on, but per area instead of per chunk;
   - the compact HUD keeps the nearest bank and shop;
   - the world map shows the tint and a tooltip with contents.

   A player who changed any old setting keeps that choice through the migration. What Stage 3's findings change for
   everyone is the drawing itself: fog instead of a red map, borders instead of fills, one fade instead of a 2.5 Hz
   pulse.
6. **Five alert levels**, so the migration loses nothing: Off, Chat, Chat and fade, Chat and sound, Chat, sound and fade.
   A new locked area next to the last one posts chat only; the same area alerts again only after 60 seconds.
7. **Words come from the web app:** Omni-Keys (the web says "Omni-Key" 46 times), Chaos Keys, Fate Points, Ritual of
   Clarity, Ritual of Greed, Uncharted, Needs checking, Different character, and the tag " (Locked)".
8. **The Profile row goes.** Run shows "Nubles (you)", or "Nubles, and you're on Zezima". The relay is unchanged.
9. **Disconnect clears the pairing and keeps the saved rules** as a backup, labelled as such.
10. **A different character at login** still gets its chat line once per login, without the sound. The
    `warnAccountMismatch` setting retires.
11. **Infoboxes stay** (off by default), each with its own name (A15) and an OSRS icon.
12. **Scene borders:** locked edges are dashed, over a dark underlay. `shadeNearbyLocked` keeps its key, but now means a
    short fog band on the locked side of an edge instead of whole-chunk fills.
13. **Freshness:** amber after two missed polls; "may be out of date" at 15 minutes, which is Strict Mode's window.
    Revisions are not shown; the card says "synced 2 min ago".
14. **Guide screenshots are rendered** by `gradle previews` from the release's code, in RuneLite's own theme, and
    captioned "Rendered from the plugin's code". The guide's screenshot policy changes to match. The same renders
    replace the web guide's 12 sidebar crops, which the web brief found were probably not taken in RuneLite's theme.
15. **One Hub release.** If the diff grows too large to review comfortably, it splits after Phase D: the sidebar and
    settings first, the in-game display and performance second.

## Design language

The rules every view and overlay follows. `UiBoundaryTest` (A6) enforces the mechanical ones.

- **Surfaces.** The panel is `ColorScheme.DARK_GRAY_COLOR`. Cards are `DARKER_GRAY_COLOR`, with 8 px inside and 6 px
  between. Hover is `DARK_GRAY_HOVER_COLOR`. Hairlines separate groups inside a card.
- **Type.** RuneScape fonts at their own size, never derived: bold for titles, regular for body, small for secondary
  text. Primary text is white, secondary is `LIGHT_GRAY_COLOR`, and status text uses the palette.
- **Status.** A pill says the status in a word, in its colour on a faint tint of it. A row pairs an icon with the pill.
- **Hierarchy.** One card leads (status). Everything else is a section with one header component: title, count,
  chevron and hover. Only Here and an active Strict Mode start open.
- **Lists** show five rows per category, then "+N more". A reason wraps to two lines at most; the tooltip has the rest.
- **Buttons.** One primary action per card, in the accent. Secondary actions are quiet. Labels fit: "Pause 60 s", not
  "Pause Strict Mode for 60 seconds".
- **Icons** are OSRS sprites and item images at 16 px, loaded through one `IconSource`. Previews load the same art from
  a local folder.
- **In game.** Words carry every status. Lines are thin and exact; nothing fills a whole chunk. A locked area fades in
  once. Overlays use RuneLite's standard panel background.

## Phases

Phase A is invisible. Phase B builds the views over sample models and stops for the owner's review of the renders.
Phases C–F wire them in and rebuild the in-game display. Phase G is the shared wording and the web guide; Phase H
releases.

### Phase A: foundations (no visible change)

- [x] **A1. Preview tool.** `SwingSnapshot` paints a component with no display: it lays out by hand and repeats until
  HTML labels have wrapped. `Previews` is the main class, and `gradle previews` runs it headless, in UTC, with
  `RuneLiteLAF.setup()`, writing PNGs to `build/previews`. It isn't part of `check`: the theme is global to the JVM.
  `SwingSnapshotTest` paints a plain panel headless.
- [x] **A2. `Palette`** (pure, only `java.awt.Color`): surface, text, accent, status, tint and edge tokens, with the
  Default, Colour-blind safe and Custom presets from the display brief §5.
  `PaletteTest`:
  - every text token is at least 4.5:1 on both RuneLite greys;
  - the colour-blind preset's statuses are at least 10 apart (CIEDE2000) under protanopia and deuteranopia, using the
    Machado 2009 matrices;
  - the locked shade stands out from terrain in both presets;
  - locked edges are dashed in every preset.
- [x] **A3. `Type` and `Space`:** the three RuneScape fonts from `FontManager`, and the spacing constants.
- [x] **A4. `Terms` and `Copy`:** the canonical words (decision 7) and the message templates. Every status word, row
  mark, section name and chat line comes from here. `CopyTest` covers the templates.
- [x] **A5. `IconSource`:** OSRS art by sprite or item id, with a placeholder until it loads. The runtime source wraps
  `SpriteManager` and `ItemManager`; the preview source reads a folder. `Icons` names the art for each concept (the
  design brief's candidates).
- [x] **A6. `UiBoundaryTest`,** in the style of `DecisionBoundaryTest`:
  - no `new Color(` outside `Palette`;
  - no `deriveFont(` in views;
  - no ✓ ✕ ○ ⚠ ▸ ▾ ▼ ▶ in any string literal.

  It lists today's offenders as known exceptions, and each later task removes its own.

### Phase B: the component kit and views, over sample models

- [x] **B1. Components:** `Card`, `Section` (the one collapsible header), `StatusPill`, `StatTiles`, `ItemRow` (icon,
  name, pill, a reason clamped to two lines, a tooltip), `MoreRow`, `Buttons` (primary, secondary, quiet), `ToggleRow`,
  `ProgressBar` and `Hairline`. Smoke-tested at 225 px.
- [x] **B2. Views over plain models:** `StatusCardView`, `HereView`, `StrictModeView`, `RunView`, `ConnectionView`, and
  the header and footer. The footer reads "More settings: RuneLite configuration › Fate Locked Ironman". RuneLite has
  no public way to open a plugin's config page, so it's text, not a link.
- [x] **B3. Preview shots:** every row of the UX status table, and Here in Lumbridge, Falador (locked), an interior and
  the sea, from the golden bundles' fictional "Iron Example" run.
- [x] **B4. Owner review.** Send the renders beside today's sidebar, and adjust before wiring. This is the one
  checkpoint where the owner's taste decides. (Renders sent 27 Sept; the wiring went ahead while the owner's
  reaction is awaited, and any changes land on top.)

  *Done* 28 Sept in RuneLite itself rather than on the renders: the owner reviewed the whole stage in a client of
  its own, asked for changes in four rounds (listed under H1), and then said "all looks good".

### Phase C: presenters and wiring

- [x] **C1. `FreshnessPolicy`** (pure), taken out of `FateLockedPlugin.rulesAreFresh()`, so Strict Mode, the card and
  the HUD share one answer. `ActiveRules` gains `arrivedAt`. A minute tick refreshes ages and the pause countdown, even
  at the login screen.
- [x] **C2. `StatusFacts` and `StatusCardPresenter`** (U8, U21).
  - `StatusTableTest` runs the 11 rows of the UX table. The Different character row also checks the Here, HUD and alert
    outputs, which stay quiet.
  - `everySyncReasonHasACard` covers the 17 `SyncReason`s, saved rules at startup, legacy backups, the logged-out state
    and an unbound profile.
  - The primary button comes from `SyncView.action`, which is computed today but never shown.
- [x] **C3. `HerePresenter`,** grown from `ChunkPanelViewModelFactory` (U13, U16, U21):
  - interiors and the sea are named from `places`, fixing "Unknown chunk" at 18,143;
  - every row keeps its reason, with one mark per status and a status word from `Terms`;
  - the chunk's own reason shows;
  - five rows per category, then "+N more";
  - no freshness line.

  Tests: `ChunkPanelViewModelFactoryTest` (the reason assertions flip), and `ChunkPanelGoldenTest` (every place chunk
  is named, and every row's detail matches).
- [x] **C4. `StrictModeSectionPresenter`:** open only while Strict Mode is on; "Pause 60 s"; "Recently prevented"
  hidden until it has an entry.
- [x] **C5. `RunPresenter`** (R12, U22): the character line (decision 8), the Run ID as "…a1b2", Fate Points, ritual
  names, keys, and progress as "15 of 187 areas unlocked". A test pins that the full Run ID never appears.
- [x] **C6. `ConnectionPresenter`:** the online-sync consent toggle, Re-pair, Disconnect (wires the unused
  `clearPairing`), Check now, Import from clipboard, Load newest backup file, and the privacy note.
- [x] **C7. Wire the views into `FateLockedPanel`.**
  - Delete the sidebar's copies of settings, `KeybindCaptureButton`, and the binder's colour and keybind code. The
    binder keeps the two consent-bearing toggles.
  - Post to the panel only on a change; today Strict Mode posts every tick (A9).
  - `FateLockedPanelStatusTest` gives way to one smoke test per view.
- [x] **C8. Messages** (R12): "imported 13 regions" and its siblings count continents. They become `Copy` lines naming
  the source and time. Status messages clear after about 8 seconds, and Bundle no longer collapses itself.

### Phase D: settings in RuneLite's config panel (U11)

- [x] **D1. The new `FateLockedConfig`** (the sidebar brief §3.4).
  - Sections: Tracker, Strict Mode, Alerts, Display, and two closed ones: Custom colours and Backup.
  - Every item gets an explicit `position`; today they sort alphabetically.
  - `@Alpha` goes on the three colours; without it, RuneLite's picker drops the tint's alpha.
  - New enum keys: `lockedAreaAlert`, `hudMode`, `worldMapMode`, `chunkBorders` and `colourPreset`.
  - Merged booleans: `announceAreaChanges`, `ruleWarnings` and `tagLockedOptions`.
  - `FateLockedConfigTest` pins the exact list: key, type, default, section and unique positions.
- [x] **D2. `SettingsMigration`** behind a `ConfigStore` seam.
  - It runs first in `startUp` and again on `ProfileChanged`, since RuneLite rewrites defaults for each profile.
  - `settingsVersion` is a raw key, and it is written last.
  - The rule: if all of a new setting's old keys are still at their old defaults, the new default stands. Otherwise
    the old value maps as the brief's table says.
  - `SettingsMigrationTest` covers:
    - a fresh install;
    - all old defaults;
    - each non-default mapping;
    - idempotence;
    - a profile switch;
    - retired keys kept.

  `FateLockedPluginStartupContractTest` proves the migration runs before the first config read.
- [x] **D3. Every read site moves to the new keys.** `ruleWarnings` recomputes both gear and Slayer.

### Phase E: in-game display

- [x] **E1. The palette in game:**
  - the HUD, tooltip colours, the menu tag, the pin, the infoboxes and the banner, which takes RuneLite's standard
    background;
  - the tag " (Locked)" is changed where it is written, and in the three places that strip it;
  - no glyphs in game text.

  `HudStatusTest`, `WorldMapTooltipTest`, `MenuTagTest` and `TagConsistencyTest` follow.

  *Done as* `6cacd80`, `f6392a3`, `490ecf8`, `efd3719` and `5ea34f5`, with `32d770e` repairing colours the old pickers
  saved solid. The HUD's colours came with E6 (`HudStatusTest` became `HudPresenterTest`), the infoboxes' with E7.
- [x] **E2. Scene borders (U3).** *Done as* `2d55726`; the path tests are in `ChunkBorderRendererTest`.
  - `SceneEdges` (pure) is built once per scene key from a 13×13 grid of 8-tile zones, so instances break at their
    zones.
  - `ChunkBorderRenderer` walks tile corners on the exact chunk line within 32 tiles, and breaks the path where a corner
    can't be projected.
  - There is no chunk fill, and nothing is drawn on another character.
  - `SceneEdgesTest` and `ChunkBorderPathTest` use a fake projector.
  - Measured: about 0.3 ms a frame, against 2.4 ms for today's fill.
- [x] **E3. Minimap:** *Done as* `a1b8926`.
  - edges come from `SceneEdges` through `localToMinimap`;
  - locked shading is drawn as runs;
  - the current chunk is not filled;
  - the gameval minimap ids replace the deprecated `ComponentID`.
- [x] **E4. World map (U18).** *Done as* `9272fe5`; the clip is tested in `WorldMapModelTest` and
  `FateLockedWorldMapOverlayTest`.
  - `WorldMapProjection` ports RuneLite's integer maths, so the tint lines up with the pins; today it can be a tile off.
  - `WorldMapClip` cuts out the overview and surface selector, for drawing and for the tooltip.
  - `WorldMapModel` holds runs per row and is cached per decision service and palette. Locked land is shaded, with a
    boundary line; the frontier is filled; unlocked land is clear.
  - The tooltip is cached, and the deprecated APIs go.
  - Tests: `WorldMapProjectionTest` (0 px against RuneLite's formula), `WorldMapClipTest`, and `WorldMapModelTest` over
    every golden.
- [x] **E5. `LockedAreaAlerts` and `FlashFade`** (U10's rest, U20). *Done as* `769e217`.
  - Chat is posted per area.
  - The locked alert fires when entering a locked area from an unlocked one, or when entering a different locked area.
  - The same area waits 60 seconds before alerting again.
  - The sea groups under its reason.
  - State is forgotten at login and shutdown, which today skips the sound after a re-login.
  - The flash is a single fade, on a monotonic clock.
  - `LockedAreaAlertsTest` and `FlashFadeTest`; the golden walk in `FateLockedChunkEntryTest` becomes one line per area.
- [x] **E6. HUD modes.**
  - Compact: Here, Status, Why when locked or not ready, Strict Mode, and the nearest bank and shop.
  - Detailed adds the chunk's contents, replacing the content box, and progress.
  - The model is published on change, not rebuilt each frame.

  *Done as* `66708b9`. Detailed also shows the run's Keys, Fate Points, ritual and goal, which left Compact.
- [x] **E7. Infoboxes (A15):** distinct pinned names, OSRS icons and the web's words. `FateLockedInfoBoxTest`.
  *Done as* `64d91d8`. A box with nothing to count, or on another character, isn't drawn.

### Phase F: performance (A9)

- [x] **F1. Menu tags filter by `MenuAction` first.**
  - Walk here, Cancel, player options and Examine allocate nothing.
  - Text is normalised once, with precompiled patterns.
  - A characterisation test covers every `MenuAction`, and a seam counts reader calls.

  *Done as* `ea6ba66`: `MenuTagFilter` lets through 18 types; `MenuTagFilterTest` is the characterisation, and a spy
  reader in `MenuTagTest` the seam.
- [x] **F2. What remains of the per-frame work,** after E2–E6. `A9PerformanceTest` pins zero allocation in the world
  map loop and the tag fast path, using `ThreadMXBean`.
  *Done as* `07c11fb`: the world map keeps its projection, clip and outline until the view or the rules change.
  `A9PerformanceTest` runs in a JVM of its own (`performanceTest`, part of `check`), away from Mockito's inline mocks.

### Phase G: one wording, shared with the web guide (U12)

- [x] **G1. Web: `data/runeliteWording.ts`.**
  - It holds terms, words to avoid, the settings list with options, and the sidebar section names.
  - `goldens:write` writes it as `contracts/golden-bundles/runelite-wording.json`.
  - `data/runeliteWording.test.ts` checks the guide against it, and that no avoided word appears in the guide.

  *Done as* web `c6f90da` on `claude/runelite-guide-stage-3`; the guide checks came with G3. The wording file is
  compared byte for byte, since its lists are in order.
- [x] **G2. Plugin:** pin the web pull request's head. `WordingContractTest` checks that:
  - each `Terms` constant equals the contract's term;
  - no avoided word appears in a main-code string literal;
  - the visible `@ConfigItem`s equal the contract's settings.

  That makes "the guide's settings list matches the release" a plugin CI failure.

  *Done as* `cddd963`, pinned at web `c6f90da`, and re-pinned at the pushed branch's head, `65418f3`, before the
  pull request (its contract files are the same). The sidebar's cards are checked too.
- [x] **G3. The web guide:**
  - settings, sections, glossary, chapters, troubleshooting, presets, command-palette keywords, and the two untested
    counts;
  - screenshots from `gradle previews`, with a `source` per image and manifest version 2 with hashes;
  - the screenshot policy and its caption;
  - a What's New release.

  *Done as* web `8f70619` (the words), `8e12200` (the pictures) and `1a5c14c` (What's New). The pictures come from a
  task of their own, `gradle guideScreenshots` (`10a34bd`), which also measures where each marker points.
- [x] **G4. Plugin docs:** README, CONTRIBUTING, the Hub review notes, and checklist rows for borders, fog, the fade,
  alerts per area and the migration. *Done as* `e6b50fb`.

### Phase H: release

- [x] **H1. Client tests.** A login-screen run with an empty RuneLite home. Then a run on a profile seeded with Stage 2's
  keys at non-default values, checking each migrated setting in RuneLite's config panel.

  *Done* 28 Sept on RuneLite 1.12.39 at `0efc835`, read from the profile each run saved. The empty home started clean,
  with no warnings, and all 21 settings at their defaults. The Stage 2 profile, with chat on entry, the fade, the rule
  warnings and the tags off, the content box on, the tooltip without contents, locked borders off and a solid red
  picked, came out as Chat and sound, announcements off, rule warnings off, tags off, Detailed, Shading and tooltip,
  All edges, and Custom with the red see-through again; every old key was kept. The owner's own look in the client
  follows.

  *The owner's look*, 28 Sept, logged in to their own account through a review client (Try Stage 3). Four rounds of
  changes, each committed on its own and mutation-checked, then approved:
  1. `e726da0`: the world map's line goes all the way round the unlocked land, past Tutorial Island.
  2. `7b46f7a`: Here opens and closes, Skilling by skill with the game's icons, and every row decided in game.
  3. `b7d090b`: a clicked Here row puts the game's arrow on the nearest one.
  4. `f436b84`: the border's dashes are fixed on the ground and hidden behind what stands in front; `37b9417`: with
     none loaded, a row shows the way to the nearest one seen, with the Shortest Path plugin's route when it runs.
- [ ] **H2. Pull requests.**
  1. The plugin and web pull requests.
  2. The Plugin Hub pull request once the plugin is merged (ask first).
  3. The web merge once the Hub has merged.
  4. The re-pin.

## Owner decisions and pending steps

- **B4:** approved in RuneLite on 28 Sept. Nothing else waits on the owner before the merge clicks.
- **Reversible calls:** any decision above can be reversed in review. The ones players will notice are 4, 5, 6, 7, 10
  and 14.
