# U — Sidebar and in-game UX review

> Area report from the 2026-09-24 plugin review. The consolidated report is [`../2026-09-24-plugin-review.md`](../2026-09-24-plugin-review.md). Harness names refer to a throwaway harness that is not in this repository. Web commit `2f1697c` has the same tree as web `main` at `9617a16`. Corrections made while consolidating are marked **[Corrected]**.

Plugin `osrs-fate-locked-runelite` at `bda88c8` (main). Web app cross-checked at `OSRS-Fate-Locked` (main, `2f1697c`). Read-only review; nothing in either repository was changed.

Evidence used:

- **Code reading.** Every file in scope, plus the controller, rule engine, guard and bundle classes the UI depends on.
- **Harness probe.** `U-harness/probe/UxProbe.java` compiled the pure bundle, rules and panel classes with `javac --release 11` and ran them against a crafted v4 bundle. Output is in `U-harness/probe-output.txt`.
- **Colour maths.** `U-harness/contrast.py` computes WCAG contrast and simulates colour blindness with the Machado 2009 matrices.
- **RuneLite source.** Contracts were checked through GitHub code search fragments of `runelite/runelite` master. Full files were not readable, so claims that rest on memory are marked **unverified**.
- **Screenshots.** The web guide's 14 real RuneLite captures (`public/guides/runelite/*.png`, manifest `pluginCommit 1e118ec`, the last UI commit before `bda88c8`).

Nothing was rendered in a RuneLite client. CI for `bda88c8` passed against RuneLite `latest.release` (run 36065264678), so the deprecated APIs the overlays use still compile.

## Summary

The sidebar mostly holds settings: 27 of its roughly 40 controls repeat RuneLite's config panel. Meanwhile, what a player needs at a glance is truncated, hidden, or shown as a bare "On" or "Unknown": whether the rules are current, whether they belong to this character, and whether Strict Mode can actually act.

Five high-severity problems:

- **U1.** "Reload from file", the auto-reload toggle and backup imports can silently replace relay rules while the panel still says Connected.
- **U2.** The normal connect flow shows red "Pairing request expired" before the player has confirmed.
- **U3.** The scene overlays are drawn from four chunk corners, so they vanish whenever a corner is off-scene or behind the camera.
- **U4.** The panel and every other surface use different lock decisions, so a wrong-character session still gets LOCKED sounds and flashes.
- **U5.** Strict Mode reads "On" while it is silently inactive.

Copy and terminology drift from the web app in more than a dozen places (U12).

Target: a status-first sidebar driven by one presenter layer and one lock-decision choke point, quiet-by-default alerts, and about 15 settings that live in RuneLite's config panel.

## Findings

Severity: critical > high > medium > low. Effort: S is under a day, M is 1–3 days, L is more than 3 days.

### U1 — Backup loads silently replace the relay's rules
**Titles in full:** "Reload from file", the "Auto-reload on change" toggle and clipboard or paste imports silently replace relay rules; with no file present, "Reload from file" loads an empty bundle.
- **Severity:** high
- **Status:** traced (read `reloadBundle`, `onConfigChanged`, the paste path and the controller's 304 path).
- **Location:** `FateLockedPlugin.java:497-502` (toggling autoReload calls `reloadBundle()`), `1313-1326` (`file == null` → `bundle = FateLockedBundle.empty()` with no message), `1391-1418`; `FateLockedPanel.java:457-460`; `TrackerConnectionController.java:155, 271, 350-400`.
- **Scenario:** A connected player clicks "Reload from file" in Bundle, or toggles "Auto-reload on change" in either settings UI.
  - With no `fate-locked-bundle-*.json` in `~/.runelite/fate-locked`, the active rules become empty and no message appears.
  - The relay keeps answering 304, because the controller still holds the accepted version. The rules therefore stay empty until the web app publishes a new revision.
  - The header keeps saying "Connected · <time>".
  - If an older file exists, or the player pastes an older bundle, those rules replace the relay's instead.
- **Cross-area (Strict Mode, core):** while paired, `rulesAreFresh()` (`FateLockedPlugin.java:1193-1198`) takes freshness from the relay's `lastSync`, which every 304 refreshes. The file's older rules are therefore treated as fresh. Strict Mode could block travel to an area unlocked since that file was made, which contradicts the "fresh rules only" invariant.
- **Fix:**
  - Use one rules-source model. The relay wins while paired and consented.
  - A backup import while paired asks for confirmation and marks the source as a backup. Freshness comes from the active source's own timestamp.
  - `reloadBundle()` never clears rules when there is no file; it shows "No backup file in …" instead.
  - Toggling auto-reload only starts or stops the watcher.
  - Show the active source in the status card.
- **Effort:** S for the guard and message; M for the source model.

### U2 — The connect flow shows "Pairing request expired" while the player is still confirming
- **Severity:** high
- **Status:** traced:
  - the controller maps 404 to EXPIRED;
  - the relay returns 404 for an unpublished code (`workers/fate-relay/worker.js:149`);
  - the web publishes only in `onConfirm → relaySync.adoptCode` (`App.tsx:1077-1078`);
  - the controller tests pin EXPIRED on 404 (`TrackerConnectionControllerTest.java:280-306, 792-812`).
- **Location:** `TrackerConnectionController.java:276-290`; `FateLockedPanel.java:539-543` (EXPIRED is shown in red).
- **Scenario:**
  - The player clicks Connect tracker and the browser opens. Within about 4 s, RuneLite polls `/r/<code>`, gets 404, and the panel turns red: "Pairing request expired". The player is still reading the confirmation page.
  - Retries back off to 5, 10, 20, 40, then 60 s, so after confirming the player can wait up to a minute while the panel says "expired".
  - The web guide's troubleshooting for this symptom says "select Connect tracker again". That replaces the code, and the open confirmation page then publishes to a code RuneLite no longer polls.
  - The same red copy appears when a working pairing's relay record lapses (the web notes "published data is ephemeral (24h)"). There the fix is to open the web tracker, not to re-pair.
- **Fix:**
  - Before the first import, treat 404 as WAITING ("Waiting for confirmation in your browser"), polling at a fixed 5 s, with a 10-minute timeout state ("Request timed out — connect again").
  - After a successful import, treat 404 as "Tracker copy expired — open the web tracker to refresh it" with an [Open web tracker] action.
  - Update the guide's troubleshooting.
  - Cross-area: relay reviewer (state machine).
- **Effort:** S

### U3 — The scene overlays draw only when all four chunk corners are in the loaded scene and in front of the camera
**Affects:** the scene tint, the locked-border lines and the nearby shading.
- **Severity:** high
- **Status:** suspected, high confidence. Traced in code.
  - Confirmed from RuneLite master source: `LocalPoint.fromWorld(WorldView,int,int)` is documented "@return coordinate if the tile is in the current scene, otherwise null" (`WorldPoint.isInScene` check), and `localToCanvasCpu/Gpu` return null outside the extended scene.
  - The null return for points behind the camera (`z1 >= 50` test) is from RuneLite's implementation as I recall it — **unverified**.
  - Not observed in a client. No recorded evidence exists of any overlay rendering: the manual matrix has no overlay rows, and the web guide deliberately shows only settings captures.
- **Location:** `FateLockedSceneOverlay.java:176-208` (one quad from four corners), `165-174` (edge endpoints), `136-157` (clamped quads still need four projected corners).
- **Scenario:**
  - **Walking into a new chunk.** The far half of the new chunk lies outside the 104-tile scene until the next rebuild (8 or more tiles later). The tint and the side border lines are missing at the moment of entry, when they matter most.
  - **Normal camera angles.** The camera-side corners of a 64-tile chunk are usually behind the camera unless the player is near that edge. My estimate at 45° pitch and mid zoom is within about 20–25 tiles. The quad is then rejected entirely, so the tint flickers on and off as the camera turns.
  - **When the quad does draw,** one flat polygon covers most of the game view at 43% alpha (default alpha 110), including NPCs and the player, and it ignores terrain height.
  - **Half-tile inset.** Corners are tile centres, so the fill and the "danger line" sit half a tile inside the real boundary.
- **Fix:**
  - Draw per-tile boundary segments along each chunk edge (project each tile edge; skip tiles off-scene or behind the camera). Draw locked edges strongly and others faintly.
  - Drop the full-chunk fill, or keep only a band 1–2 tiles wide along locked edges.
  - Cache the edge-tile list per (chunk, scene base).
- **Effort:** M

### U4 — Surfaces disagree because they use two different lock decisions
- **Severity:** high
- **Status:** traced. Confirmed by the harness probe.
- **Location:**
  - The panel uses `FateRuleEngine.entry`, which knows about the account and NOT_READY (`panel/ChunkPanelViewModelFactory.java:47-48`).
  - The HUD (`FateLockedHudOverlay.java:122-135`), scene (`FateLockedSceneOverlay.java:67-72`), minimap (`FateLockedMinimapOverlay.java:73-77`), world map (`FateLockedWorldMapOverlay.java:84-86, 127-130`) and chat, sound and flash (`FateLockedPlugin.java:1061-1081, 1277-1310`) all use `FateLockedBundle.lockStateAt` (`FateLockedBundle.java:563-574`), which has no account or NOT_READY input.
  - Menu tags and bank warnings use the v4 engine. Slayer and gear warnings check neither.
- **Scenario A (wrong character).** The player logs into their main with the plugin on.
  - The panel shows the area as "Unknown" with no categories and no reason. Menu tags and bank warnings go silent.
  - Meanwhile the HUD shows "Status LOCKED", chat posts "⚠ LOCKED", the locked-entry sound plays, and the red flash pulses as the main walks around.
  - Slayer and over-tier gear warnings fire against the other character's run.
- **Scenario B (Not-ready entry).** A chunk inside an unlocked area that the web marks NOT_READY (unreachable) shows "Not ready" in the panel but "Unlocked" in the HUD, "✓ unlocked" in chat and green in the scene.
- **Evidence** (`U-harness/probe-output.txt`):
  - Same chunk: panel `entry=NOT_READY`, `lockStateAt=UNLOCKED`.
  - Wrong account: panel `entry=UNKNOWN`, `categories=0`, and the engine's reason "Wrong account" is not carried into the view model, while `lockStateAt` still returns LOCKED for Falador.
- **Fix:** one `LockDecisions` choke point (v4 entry + trust state + legacy fallback) that the panel, HUD, overlays, alerts and tags all consume, plus a `RulesTrust` gate that puts every warning surface into a quiet "different character" mode. Cross-area: rules parity (legacy `lockStateAt` vs v4 entry).
- **Effort:** M

### U5 — Strict Mode shows green "On" while it cannot act
- **Severity:** high
- **Status:** traced
- **Location:** `FateLockedPanel.java:414-425` (only enabled, paused and seconds are inputs); `FateLockedPlugin.java:1093-1105, 1188-1198`; `guardian/StrictModeGuard.java:12-17`.
- **Scenario:** Strict Mode is on, and one of these holds:
  - rules came from a clipboard or file more than 15 minutes ago;
  - the relay has been unreachable for 15 minutes;
  - the character is not the bound account;
  - the run has no bound account (travel requires a non-empty `rules.account`);
  - only a legacy bundle is loaded;
  - the player is logged out.

  Every guard correctly fails open, but the Guardian row still says green "On" and nothing in game says otherwise. A player who relies on the safety net thinks it is working.
- **Fix:** `StrictStatus` = OFF | ACTIVE | PAUSED(n) | INACTIVE(reason), computed from the same inputs as `GuardContext`. Show "Inactive — rules older than 15 minutes", "— different character", "— run has no bound account" or "— no current rules". Pin it with presenter tests.
- **Effort:** S

### U6 — The sidebar has no state for "different character", "no rules loaded" or "which rules are active"
- **Severity:** medium
- **Status:** traced
- **Location:** `FateLockedPanel.java:124-143, 487-495, 634-669`; `panel/ChunkPanelViewModel.java` (no reason field); `FateLockedPlugin.java:1497-1502`.
- **Scenario:**
  - **Wrong character.** The only signals are one chat line per login and a red ⚠ on the HUD Account line. The sidebar's "Tracker account" and Run "Account" rows both show the bound name, the same value twice, and never the logged-in name. The web guide says "Check the Account row in the Run section", which cannot reveal a mismatch.
  - **No rules loaded.** Logging in shows "Unknown chunk / Unknown region · 50, 50 / Unknown · Offline snapshot / Can do 0 · Not ready 0 · Locked 0" (probe).
  - **Clipboard or file users** always see "Connection: Not connected" and "Offline snapshot", although their rules are active. A successful file load produces no message at all.
- **Fix:** a status card with explicit states (see the target UX), the logged-in name beside the bound account, a `reason` field in the view model, and the source and time of the active rules.
- **Effort:** S–M

### U7 — Noise when there are no rules, and in unauthored chunks
- **Severity:** medium
- **Status:** traced
- **Location:** `FateLockedPlugin.java:1045-1083, 1277-1287`; `FateLockedHudOverlay.java:116-135`; `FateLockedSceneOverlay.java:62-73`; `FateLockedMinimapOverlay.java:79-87`.
- **Scenario:** A fresh install with default settings and no rules:
  - posts "[Fate Locked] Chunk (x, y) — unauthored" on every chunk crossing and every login;
  - tints the current chunk grey in the scene and on the minimap;
  - shows a HUD reading "Here (50, 50) / Status Unknown".

  The same happens in dungeons, basements and instances (U14).
- **Fix:** no chat, HUD content or tint until rules are loaded; never announce unauthored chunks.
- **Effort:** S

### U8 — The main status is truncated and hard to read
- **Severity:** medium
- **Status:** traced, with visual evidence from the web guide's captures of plugin `1e118ec`: "Waiting for trac…" (`02-panel-disconnected.png`), "Connected · 13:…" (`04`, `05`), "Pause Strict Mode for 60 sec…" (`05`, `07`).
- **Location:** `FateLockedPanel.java:126-128, 910-925` (two equal GridLayout columns, about 100 px each at the 225 px panel width), `516-556`, `421-423`.
- **Detail:**
  - The status message shares a half-width cell and has no tooltip.
  - "Connected · HH:mm:ss UTC" repeats the time already shown in "Last sync".
  - Times are UTC wall-clock, whereas the web app says "Profile sent 2m ago".
  - "Last sync" stays green however old it is.
  - Failures to reach the relay show grey, like "Not connected".
  - "Tracker relay is busy; retrying later" cannot fit.
- **Fix:** a full-width, wrapping status line; relative local time; amber after 5 minutes and red after 15; the exact time in a tooltip; a shorter button label ("Pause 60 s").
- **Effort:** S

### U9 — "Roll inbox" is a dead end, and its Warnings count goes stale
- **Severity:** medium
- **Status:** traced. The web's `RollInboxDriver.tsx:33-36` confirms the web inbox no longer receives RuneLite observations.
- **Location:** `FateLockedPanel.java:202-227, 497-510`; `FateLockedPlugin.java:1786-1808`, called only from `startUp`, a pairing-code change and `record()`; `FateLockedPlugin.java:879`.
- **Scenario:**
  - The section shows "Local events 42 / Needs review 5 / Warnings None" and a button to the web Roll Inbox.
  - The 42 events cannot be viewed anywhere in RuneLite.
  - "Needs review" counts every UNCERTAIN event in the 250-entry history, so it never goes down.
  - The web inbox the button opens does not contain them; the tooltip admits this.
  - "Warnings" is recomputed only when a new event is recorded, so it reads "None" (green) while the player stands in a locked chunk, and "1 active" (red) long after they leave.
  - The diary nudge says "review its tasks in the Roll Inbox", which cannot show them.
- **Fix:** replace the section with an "Activity" list of recent local observations (time, label, a "Needs checking" badge). Move active warnings to the status card and HUD. Fix the nudge copy ("log it in the tracker"). Alternatively, drop the section.
- **Effort:** M for the list; S for dropping it and fixing the copy.

### U10 — Locked-entry alerts are tied to chat and repeat for every chunk
- **Severity:** medium
- **Status:** traced
- **Location:** `FateLockedPlugin.java:1066-1081, 1277-1311`.
- **Scenario:**
  - A player turns off "Chat on chunk entry" (default on; it posts a line on every crossing). That silently disables "Warn entering locked chunk" too, because the sound and notification run only inside `announceEntry`.
  - With both on, crossing a three-chunk locked area plays the locked-entry sound (effect 2277) and sends a notification at every boundary; `announceEntry` has no transition check. The flash correctly fires only on the transition.
  - Walking back and forth across one boundary repeats the chat line each time, and walking within one area posts one identical line per chunk.
  - The config text promises a "Loud red warning"; the red part is actually the separate flash setting.
- **Fix:** one "Locked-area alert" level (Off / Chat / Chat + sound / Chat + sound + flash), fired on area transitions into Locked, with a 60 s debounce per area. Routine announcements off by default.
- **Effort:** S

### U11 — Settings sprawl, duplication and divergence
- **Severity:** medium
- **Status:** traced. RuneLite's config-reset and item-ordering behaviour is **unverified**.
- **Location:** `FateLockedPanel.java:242-315` (27 setting controls in the sidebar); `FateLockedConfig.java:63-233`; `FateLockedConfigBinder.java:65-73, 85-93, 112-120` (refreshers skip null).
- **Detail:**
  - Every setting exists twice, in the sidebar and in RuneLite's config panel.
  - The config "Warnings" section also holds the HUD, content-box, infobox, reminder and notifier toggles.
  - Five groups of settings control one idea each:
    - chat, warn and flash on locked entry;
    - the two menu-tag toggles;
    - the HUD, nearest bank/shop and content box;
    - the world map tint, tooltip and tooltip contents;
    - the scene tint and locked borders.
  - `unauthoredColor` only affects the current chunk in the scene and minimap. The world map never draws unauthored chunks, although the config text and the web guide say it does.
  - After "Reset" in RuneLite's config, keys are unset (as I recall), so `ConfigChanged` carries null and the sidebar refreshers ignore it. The sidebar checkboxes keep their pre-reset values.
  - Items without `position` are ordered differently in the two UIs (**unverified**).
- **Fix:** settings live only in RuneLite's config panel; the sidebar keeps just the consent and Strict Mode toggles. Adopt the merged set in the target table. Refreshers fall back to the interface default when a key is unset.
- **Effort:** M (includes migrating old keys and updating the web guide).

### U12 — Copy and terminology drift, both inside the plugin and against the web app
- **Severity:** medium
- **Status:** traced.
  - Web sources: `data/runeliteGuide.ts`, `config/economy.ts:401-402`, `utils/chunkAdjacency.ts:122-128`, `components/StreamOverlay.tsx:102`, `utils/runeliteExport.ts:106-137`.
  - The ROADMAP's "Needs checking" label.
  - The harness probe.
- **Drift:**
  - **Status words.** The panel says "Available", the chips say "Can do", and the HUD, chat and tooltip say "Unlocked".
  - **Unauthored chunks.** The plugin says "Unknown" or "unauthored"; the guide says "Unauthored"; the web map says "Uncharted Chunk (x, y)".
  - **Strict Mode has four names.** Section "Guardian", toggle "Strict Mode", chat "[Fate Guardian]", banner "Pause Guardian for 60s". The web uses only "Strict Mode" (26 mentions).
  - **Currency and rituals.** "Fate" (panel, HUD) vs "Fate points" (infobox) vs the web's "Fate Points". "Buff LUCK/GREED/NONE" is a raw enum; the web says "Ritual of Clarity" and "Ritual of Greed".
  - **Key abbreviations.** The HUD's "Keys 3 · O1 · C2" keeps the abbreviation the legibility fix removed from the panel.
  - **Mixed units.** The HUD's "Unlocked 1/2 · 33%" puts a chunk percentage next to an area count (probe).
  - **Wrong unit.** "imported/synced 13 regions" counts continents, where the web counts areas.
  - **Broken grammar.** Strict Mode's generic block writes "Prevented: Rune platebody — is T3; Body is unlocked to T2." because it prefixes "is " (`FateLockedPlugin.java:1124-1132`), and that text also reaches "Recent prevented actions".
  - **Stale instructions.** The bank warning says "roll it under Banks in the tracker", but the web says "roll it in Spend Keys". The clipboard tooltip says "Click RuneLite in the tracker", but the web button is labelled "RL" (`RegionMap.tsx:1804-1812`).
  - **Dead row.** Run "Profile" is always "—", because the web bundle carries no `profileName` (probe: null).
  - **Style.** Status messages are lowercase fragments ("clipboard empty", "couldn't save setting").
  - **Glyphs.** ✓ and ⚠ in chat and the HUD may not exist in the game fonts (**unverified**).
  - **Guide claims the plugin doesn't match:**
    - messages for "stale, not found, rejected, unsupported" that the plugin never shows;
    - warnOnLocked "A strong red warning appears";
    - the world map "unauthored areas use their configured colors";
    - Current chunk shows an "entry source".
- **Fix:** one `Copy`/`Terms` module for every player-facing string, with a glossary shared with the web guide and checked by a test in each repository. Export `profileName` from the web, or drop the row.
- **Effort:** M

### U13 — Current chunk hides why something is Not ready or Locked
- **Severity:** medium
- **Status:** traced (probe)
- **Location:** `panel/ChunkPanelViewModelFactory.java:76, 132-135` (detail kept only for Skilling and Travel), `137-156`; `FateLockedPanel.java:736-760`.
- **Scenario:**
  - "○ Cook's Assistant" appears in amber with no status word. The bundle's detail ("Needs Cooking 10") is dropped, so neither the row nor its tooltip explains it.
  - A Locked bank loses its detail too.
  - A Not-ready Combat row shows ✕, the Locked glyph, in amber. Shape alone cannot tell Locked from Not ready, which matters for colour-blind players (U15).
  - Tests pin the omission (`ChunkPanelViewModelFactoryTest` asserts null quest detail; `ChunkPanelRenderingTest` forbids "Unlock from" and "Spend Keys").
- **Fix:** always carry the reason. Render it as a second line for Not ready and Locked, and in the tooltip. If some web phrasing does not suit RuneLite, have the web export a short plugin-specific reason instead of dropping it. Use one glyph per status: ✓ ○ ✕ ?.
- **Effort:** S

### U14 — Instances and underground or interior areas resolve to the wrong chunk
- **Severity:** medium
- **Status:** traced in code — there is no `fromLocalInstance` or instance check anywhere. The player impact is suspected, not observed.
- **Location:** `CanonicalChunk.java:14-17`, and every `CanonicalChunk.of(local.getWorldLocation())` call:
  - `FateLockedPlugin.java:769, 1059, 1110, 1495`;
  - `FateLockedHudOverlay.java:120`;
  - `FateLockedSceneOverlay.java:52`;
  - `FateLockedMinimapOverlay.java:78`;
  - `FateLockedContentOverlay.java:227`.
- **Scenario:**
  - In a player-owned house, a raid, a boss instance, or any dungeon or basement (surface y + 6400), the computed chunk is outside the authored surface grid.
  - The panel says "Unknown chunk", the HUD says "Status Unknown", chat posts "unauthored" at each crossing, and the chunk is tinted grey.
  - Entering a locked boss instance gives no warning.
  - The web assigns interiors to a surface chunk (`inside` lists such as "Kurask Lair" and "Interior 13137"), but the bundle carries no map from region id to surface chunk that the plugin could use.
- **Fix:**
  - Add a `ChunkLocator` choke point, using `WorldPoint.fromLocalInstance` for instances.
  - Have the web export an interior map (region id → owning surface chunk).
  - Add explicit Instance and Underground states in the UI.
  - Cross-area: rules parity and the bundle contract.
- **Effort:** M

### U15 — Lock colours rely on hue alone, and red text is below AA contrast
- **Severity:** medium
- **Status:** computed (`U-harness/contrast.py`). `ColorScheme` values confirmed from RuneLite source: `DARK_GRAY_COLOR` is (40,40,40) and `DARKER_GRAY_COLOR` is (30,30,30).
- **Location:** `FateLockedConfig.java:354-390`; `FateLockedPanel.java:45-50`; `FateLockedHudOverlay.java:24-27`; `FateLockedContentOverlay.java:195-199`.
- **Evidence:**
  - Default unlocked and locked tints composited over grass or map tan differ by ΔE ≈ 61 for normal vision, but only ΔE ≈ 11–14 under simulated protanopia or deuteranopia; both read as olive or khaki.
  - The scene and minimap show a single colour with no shape cue, so the player must identify it absolutely.
  - Panel red (239,68,68) is 3.9:1 on the panel backgrounds, used for 9–10 pt text; WCAG AA needs 4.5:1.
  - The HUD uses a different, lighter red (248,113,113; 5.3:1) and a different green, so one status has two colours.
- **Fix:** one Palette; a lighter red for text; a colour-blind-safe preset (for example blue for unlocked and orange for locked); a non-colour cue for locked (dashed or thicker border, hatching on the world map); leave unlocked chunks untinted by default.
- **Effort:** S–M

### U16 — The layout puts long, variable content above critical controls, and the connect button misleads
- **Severity:** medium
- **Status:** traced. Also checked against the approved spec, `docs/superpowers/specs/2026-07-27-unified-plugin-hub-design.md:54-57`, which required the button to read "Connecting…", "Connected" or "Reconnect tracker".
- **Location:** `FateLockedPanel.java:109-156, 676-725, 791-802`; `FateLockedPlugin.java:1613-1639`; `TrackerConnectionController.java:66-89`.
- **Detail:**
  - **Long lists push controls down.** Current chunk renders every row; a busy chunk is 20–40 rows at 28–40 px each. These sit above the expanded Guardian section, so the Strict Mode pause control moves down the page as content changes. The content overlay already caps at 5 rows per category.
  - **Heavy header.** Two full-width buttons, three status rows, a sticky status line and a four-line disclosure take about 200 px before any section.
  - **Connect button re-pairs.** It never changes label. One click while connected immediately creates a new code and clears the accepted version and last-sync time. There is no confirmation and no Disconnect action (`TrackerConnectionSettings.clearPairing` is never called).
  - **Status line never clears.** For example, "couldn't save setting" stays red indefinitely.
  - **Bundle auto-collapses.** Each successful relay import collapses an open Bundle section under the player's cursor.
  - **Guardian over-exposed.** Guardian, a default-off opt-in, starts expanded and shows "RECENT PREVENTED ACTIONS" even if Strict Mode was never used.
- **Fix:**
  - Use the target layout.
  - Cap each category at 5 rows with "+N more".
  - Move connection actions into their own section, with a confirmation for Reconnect and a Disconnect button.
  - Clear status messages after about 8 s, and never auto-collapse a section.
- **Effort:** M (part of the redesign).

### U17 — Visual conventions: palette, fonts and controls
- **Severity:** low
- **Status:** traced, plus the guide's captures. Font behaviour is **unverified**.
- **Location:** `FateLockedPanel.java:45-50` (Tailwind-style palette; blue-grey SURFACE (35,39,46) beside RuneLite's neutral greys); `689-786` (`deriveFont` at 9–13 pt); `871-894` vs `CollapsiblePanelSection.java:67` (two collapsible implementations); `FateLockedConfigBinder.java:97-122`.
- **Detail:**
  - The captures show light checkbox rows and light buttons inside a dark panel.
  - Section headers are 10 pt grey text that does not look clickable.
  - The two collapsible implementations use different arrows (▾▸ vs ▼▶) and casing ("RECENT PREVENTED ACTIONS", "…OR PASTE JSON" vs sentence case).
  - Colour buttons paint the whole button: the white label on amber is about 2.1:1. RuneLite's own Screen Markers panel uses a swatch underline instead.
  - Deriving 9–10 pt sizes from RuneLite's UI font may blur it (**unverified**).
- **Fix:** use only `ColorScheme` and `FontManager` sizes; one collapsible section component with a hover state; swatch indicators; one Palette class.
- **Effort:** S

### U18 — World map layer covers the map's own widgets and tints everything
- **Severity:** low
- **Status:** traced. RuneLite's own `WorldMapOverlay.getWorldMapClipArea` clips out the overview and surface-selector widgets (confirmed via code search); this plugin does not.
- **Location:** `FateLockedWorldMapOverlay.java:68-96` (clip is the map bounds only; `new Area(bounds)` is never subtracted from), `28-37`, `84-94`.
- **Detail:**
  - The tint paints over the map's overview and surface selector.
  - Every authored chunk is filled at 43% alpha, unlocked green included, with a darker 1 px border. A new run's map is mostly red, and labels and icons are harder to read.
  - The class javadoc promises grey unauthored chunks, but they are never drawn.
- **Fix:** reuse RuneLite's clip logic, possibly by injecting its `WorldMapOverlay` and calling `mapWorldPointToGraphicsPoint` (injectability **unverified**). Tint locked and frontier chunks only by default.
- **Effort:** S

### U19 — Work recomputed every frame and for every menu entry
- **Severity:** low
- **Status:** traced. Cost estimated by reading, not measured.
- **Location:**
  - `FateLockedContentOverlay.java:228` rebuilds the full view model every frame: rule engine, two config reads, name sanitising and a `Duration`.
  - `FateLockedWorldMapOverlay.java:76-95` processes about 624 authored chunks per frame. Each gets `lockStateAt`, `isFrontierChunk`, two `RenderOverview` calls, `Area.intersects` and `Color.darker()`.
  - `FateLockedPlugin.java:1199-1232` with `GuardedActionFactory.java:128-136` and `Teleports.java:269-275` compiles several `String.replaceAll` regexes and builds a `FateRuleEngine` for each `MenuEntryAdded`.
  - `FateLockedPanel.java:625` copies the whole clipboard bundle (**[Corrected]** about 205 KB of FLGZ text) into a line-wrapped `JTextArea` on the Swing thread. This is a likely visible hitch (**unverified**).
- **Fix:** cache models keyed by (bundle revision, chunk, account match, settings version, minute); precompute a per-chunk world-map colour for each bundle; use precompiled patterns and a per-tick memo for tags; do not echo imports into the paste box.
- **Effort:** S

### U20 — The locked-entry flash pulses at about 2.5 Hz
- **Severity:** low
- **Status:** computed: `|sin(t/125 ms)|` has a period of about 393 ms.
- **Location:** `FateLockedFlashOverlay.java:51-60`.
- **Detail:** 1.6 s of a saturated red frame pulsing roughly 2.5 times a second, close to the three-flashes-per-second guideline, with no reduced-motion option.
- **Fix:** a single fade.
- **Effort:** S

### U21 — The freshness label freezes, and valid imports read "Offline snapshot"
- **Severity:** low
- **Status:** traced
- **Location:** `panel/ChunkPanelViewModelFactory.java:158-164`; `FateLockedPlugin.java:722-730, 1066-1069`.
- **Detail:**
  - "Synced now" is computed when the player enters a chunk and never refreshed. Skilling in one chunk shows "Synced now" for an hour, contradicting the Last sync row.
  - Every clipboard or file import shows "Offline snapshot".
- **Fix:** show freshness only in the status card, refreshed every minute.
- **Effort:** S

### U22 — Hygiene
- **Severity:** low
- **Status:** traced
- **Items:**
  - The Run ID is shown in full (`FateLockedPanel.java:233`), though the web guide asks players to keep Run IDs out of support screenshots.
  - Section open or closed state resets on every restart (by design in the spec; worth revisiting).
  - `FateLockedPlugin.menuTargetWorldPoint` (`1240-1275`) is dead code, and `TrackerConnectionSettings.clearPairing` is unused.
  - The hotkey button gives no "Esc clears" hint.
  - The paste box keeps the full bundle text after an import.
- **Effort:** S

## Verified correct

These behaviours work and should survive the overhaul:

- **Consent flow.** The sidebar toggle and Connect tracker show the same third-party IP warning; declining leaves sync off; the config item carries RuneLite's `warning`. Checked in `FateLockedPanel.java:244-268`, `FateLockedPlugin.java:1613-1639`, `FateLockedConfigTest` and the startup contract test.
- **Transactional imports.** Paste, clipboard and relay imports restore the previous bundle if parsing or refresh fails (`FateLockedPlugin.java:1404-1433, 1463-1486`).
- **Config binder rollback.** It reverts a control when a save fails and reports it. Colour previews are opaque while the RGBA value is saved (`FateLockedConfigBinderTest`).
- **Legibility fixes.** The header-sizing fix and the three separate key rows are pinned by tests, and the captures show clean headers and three key rows.
- **Swing threading.** Every panel mutator moves its work onto the Swing thread (`invokeLater` / `runOnEdt`).
- **Minimap projection.** Correct for current RuneLite. RuneLite master's `localToMinimap(client, point, distance)` compares `dx² + dy²` against `distance²` in local units from the camera focus point, and its default passes `(20 << LOCAL_COORD_BITS) * 4 / zoom` (source fragment via code search). So `PROJECTION_DISTANCE = 30000`, about 234 tiles, reaches every corner. The ellipse clip prevents spill.
- **World map mapping.** The pixel mapping and the hover inversion agree with each other and, by reasoning, with RuneLite's tile-edge convention to within about 1 px (from memory; **unverified**).
- **Throttles that work as designed:**
  - the flash fires only on a transition into Locked;
  - the slayer warning fires once per assignment;
  - the over-tier warning fires once per item per session;
  - the account mismatch fires once per login;
  - the travel banner lasts 4 s with a 10 s chat de-dup;
  - tooltip contents are capped per category;
  - the HUD's nearest bank and shop are cached per chunk and bundle;
  - the content box is capped at 5 rows per category.
- **Travel banner.** Hidden when Strict Mode is off or paused. Its pause button consumes only clicks inside its own bounds (`FateLockedTravelBlockOverlay.java:257-279`, tests).
- **View-model seam.** `ChunkPanelViewModel` and its factory are already a pure, tested view-model layer worth growing into the presenter layer.

## Structural notes for the overhaul

### What makes the UI hard to change

- **One class owns the sidebar.** `FateLockedPanel` (931 lines) owns layout, 27 setting controls, the connection display, chunk rendering, Strict Mode, import tools and about 20 `*ForTest` hooks.
- **State arrives through a dozen setters,** called from different plugin paths at different times: `update`, `updateConnection`, `updateTrackerAccount`, `updateRollInboxStatus`, `updateStrictMode`, `updateRecentPrevented`, `flashStatus`, `showStrictModeIntro`, `refreshConfig`, `setCallbacks`, and others. There is no single panel state, so values go stale (U9, U21) and new states have nowhere to live (U5, U6).
- **UI decisions live in `FateLockedPlugin` (1822 lines):** chat copy, sounds, flash timing, menu tagging, HUD getters (`slayerTaskWarn`, `overTierSummary`) and freshness.
- **Each overlay re-derives everything.** Each one works out "where am I" and "what is the status" with its own palette, copy and decision source (U4, U12, U15).
- **Tests pin the current tree.** They assert the Swing component tree and exact strings: section order, 31 keys, button labels. The web guide pins the 7 sections, 30 settings and 14 screenshots (`data/runeliteGuide.test.ts`). An overhaul must replace these with presenter tests and ship the web guide text and captures in the same release.

### Proposed target UX

**Principles:**

1. One status card answers "can I trust what I see?": are rules loaded, current, and for this character?
2. One decision source for every surface: panel, HUD, overlays, chat, sound, flash and tags.
3. Quiet by default. Alert on entering locked territory; stay silent with no rules or on another character.
4. Explain instead of only colouring. Every Locked or Not ready item gets a reason and a next step in the tracker.
5. Settings live in RuneLite's config panel, which is the RuneLite convention; the sidebar is for status and actions. Only the online-sync consent and the Strict Mode toggle stay in the sidebar.
6. Use the web app's words.

**Sidebar, top to bottom:**

1. **Header (not collapsible):** "Fate Locked" and a "?" link to the web guide (`?open=runelite-guide`, which the web already handles). Below it:
   - a full-width status card: coloured border, icon, two lines, at most one primary button;
   - a compact strip: "Keys 3 · Omni 1 · Chaos 0 · Fate Points 12", plus the active ritual.
2. **Here** (expanded):
   - area name; region and chunk in small text;
   - a status pill (Unlocked / Locked / Not ready / Uncharted) with one reason line ("Unlock Falador in the tracker");
   - "3 can do · 1 not ready · 1 locked";
   - category lists capped at 5 rows with "+N more", each non-"can do" row showing its reason.
3. **Strict Mode** (collapsed unless on):
   - the toggle with one line of description;
   - the effective status (Active / Paused · 42 s [Resume] / Inactive — reason / Off);
   - [Pause 60 s];
   - "Recently prevented" (last 5, with relative times).
4. **Run** (collapsed):
   - profile and account: "Nubles (you)" or "Nubles — you're Zezima";
   - Keys, Omni Keys, Chaos Keys, Fate Points, ritual, pinned goal;
   - "12 of 40 areas unlocked"; run revision;
   - [Open web tracker].
5. **Activity** (collapsed; replaces Roll inbox):
   - the last 10 local observations with times and a "Needs checking" badge;
   - "Stored on this computer only. Log rolls in the tracker.";
   - a save-failure line when needed.
6. **Connection & backup** (collapsed):
   - online sync toggle (consent) and privacy note;
   - pairing details, [Reconnect…] with confirmation, [Disconnect];
   - backup tools: [Import from clipboard], [Load newest backup file] showing file name and time, a "Paste JSON…" disclosure, the folder path, and a hotkey hint.

Footer: "More settings: RuneLite configuration ▸ Fate Locked Ironman".

**States — today (`bda88c8`) vs target:**

| State | What the player sees today | Target status card and behaviour |
|---|---|---|
| First run, logged out | Two web buttons; "Connection: Not connected"; four-line disclosure; "Enter the game to see this chunk"; Guardian expanded | "Not connected — Connect the tracker to load your run's rules." [Connect tracker]; link "Use a backup instead"; nothing else expanded |
| First run, logged in | Chat "Chunk (x, y) — unauthored" per crossing; HUD "Here (x, y) / Status Unknown"; grey tint; card "Unknown chunk … Can do 0" | Same card; no chat, HUD content or tint |
| Connecting | Amber "Waiting for trac…", then red "Pairing request expired" within about 4 s; Connected up to about 60 s after confirming | "Waiting for confirmation — confirm this profile in the browser tab RuneLite opened." [Open page again] [Cancel]; 5 s polls; times out at 10 min |
| Connected | Green "Connected · 13:…" (truncated); "Last sync 13:41:03 UTC"; sticky "synced 13 regions" | "Rules up to date · synced 2 min ago · revision 41" |
| Stale (15 min or more) | Grey "Could not reach tracker"; Last sync still green; Strict Mode "On" | Amber "Rules may be out of date — last synced 32 min ago. Strict Mode is inactive until they refresh." [Retry now] |
| Relay copy lapsed | Red "Pairing request expired" | Amber "Tracker copy expired — open the web tracker to publish your run again." [Open web tracker] |
| Backup source | "Not connected"; "Offline snapshot" | "Using a backup from 14:02 (clipboard). Strict Mode works for 15 min after an import." |
| Different character | One chat line per login; HUD ⚠; panel "Unknown" with no categories; sounds, flashes and slayer/gear warnings still fire | Red "Different character — this run belongs to Nubles; you're logged in as Zezima. Warnings and Strict Mode are off." All in-game warnings quiet |
| In a locked area | Panel "Locked" with every row red; HUD "LOCKED"; chat "⚠ LOCKED" plus sound on every chunk, plus flash; Roll inbox Warnings may say "None" | Here: "Locked — Unlock Falador in the tracker"; one chat line, with optional sound and flash, on entering the area; HUD "Falador — Locked" |
| Strict Mode paused | "Paused"; "Resume Strict Mode · 42s" | "Paused · 42 s" [Resume]; optional HUD line |
| Instance or underground | "Unknown chunk"; chat "unauthored" per crossing | "Instance — <template area>" or "Underground — counted as <surface area>", or "Uncharted" with no chat |

**Canonical terms** (proposed; the web guide should adopt the same):

| Concept | Term |
|---|---|
| An area you may enter | Unlocked |
| Row statuses | Can do / Not ready / Locked / Needs checking |
| Chunk outside the map | Uncharted (not Unknown or unauthored) |
| The guard feature | Strict Mode (drop Guardian and Fate Guardian) |
| Currency | Fate Points |
| Rituals | Ritual of Clarity / Ritual of Greed |
| Unlock unit | areas (chunks in Chunked mode) |
| Keys | Keys / Omni Keys / Chaos Keys, never abbreviated |
| Local observations | Activity |
| Imported data | "rules" in player copy; "backup" for files and clipboard |

**In game:**

- **HUD, compact by default:** "Lumbridge — Unlocked" and "Keys 3 · Fate Points 12". Warning lines appear only while active ("Slayer task locked: Cave crawlers", "Gear above tier: Body", "Different character"). A detailed mode adds the goal, nearest bank and shop, and chunk contents, replacing the separate content box.
- **Chat:** "[Fate Locked] Entered Falador — Locked. Unlock it in the tracker before training here." Sent only on entering a locked area, with a 60 s debounce.
- **Flash:** a single fade, no pulse.
- **Menu tag:** " (Locked)" in red.
- **Strict Mode banner:** "Strict Mode blocked: Varrock Teleport" / reason / "Try: <alternative>" / [Pause 60 s].
- **World map:** tint locked and frontier chunks only, with the RuneLite clip area. Tooltip "Falador — Locked", with contents as an option.
- **Scene:** per-tile border lines, locked edges red and others subtle; no full-chunk fill.
- **Minimap:** border lines and locked shading only.

**Settings: 31 today → 15 visible plus two closed groups:**

| Target setting (default) | Replaces |
|---|---|
| Online sync (off, with consent) | `trackerNetworkAccess` |
| Strict Mode (off) | `strictMode` |
| Locked-area alert: Off / Chat / Chat + sound / Chat + sound + flash (Chat + flash) | `warnOnLocked`, `flashOnLocked`, and the locked half of `chatOnEnter` |
| Announce every area change in chat (off) | the routine half of `chatOnEnter` |
| Rule warnings: bank, slayer task, gear tier (on; each dormant when the bundle lacks its data) | `warnLockedBank`, `warnLockedSlayer`, `warnOverTierGear` |
| Tag locked right-click options (on) | `tagLockedMenus`, `tagLockedTeleports` |
| Roll reminders in chat (on) | `rollNudges` |
| Also send RuneLite notifications (off) | `useNotifier` |
| In-game HUD: Off / Compact / Detailed (Compact) | `showHud`, `showNearest`, `showChunkContentBox` |
| World map: Off / Tint / Tint + tooltip / Tint + tooltip with contents (Tint + tooltip) | `drawWorldMap`, `worldMapTooltip`, `worldMapTooltipContent` |
| Pin locked areas on world map (off) | `worldMapMarkers` |
| Chunk borders in game view: Off / Locked edges / All edges (Locked edges) | `drawScene`, `highlightLockedBorders` |
| Minimap chunk borders (on) | `drawMinimap` |
| Shade nearby locked chunks (on) | `shadeNearbyLocked` |
| Colours: Default / Colour-blind safe / Custom | presets |
| *Custom colours* (closed section): Unlocked, Frontier, Locked | `unlockedColor`, `frontierColor`, `lockedColor` |
| *Backup* (closed section): Watch backup folder (off), Re-import hotkey | `autoReload`, `reimportHotkey` |

Dropped settings:

- `warnAccountMismatch` — a different character is always shown.
- `showInfoBoxes` — the compact HUD covers it; restore it if players ask.
- `unauthoredColor` — uncharted chunks are not tinted.

Migration: on first start, map the old booleans to the new values behind a `settingsVersion` key, and keep the old keys readable for one release. Ship the updated `RUNELITE_GUIDE_SETTINGS`, presets and captures with it.

### Proposed component structure

```
state/     PluginState (immutable; rebuilt on the client thread when an input changes:
             chunk, bundle revision, connection snapshot, config, strict tick, minute tick)
           RulesTrust     NONE | CURRENT | STALE(age) | BACKUP(source, age) | WRONG_ACCOUNT(bound, current) | LEGACY
           ChunkLocator   WorldPoint -> ChunkRef{chunk, kind SURFACE|INSTANCE|INTERIOR|UNKNOWN}   (choke point)
           LockDecisions  ChunkRef -> Decision{status, reason, label}   (the only lock source for every surface)
present/   pure presenters, JUnit only:
             StatusCard, Here (grown from ChunkPanelViewModelFactory), Strict, Run, Activity,
             Connection, Hud, AlertPolicy (transitions + debounce), MapModel (per bundle revision)
           Copy/Terms (every player-facing string + glossary), Palette (default + colour-blind safe)
ui/        thin Swing views: FateLockedPanel.apply(PanelModel), StatusCardView, HereView, StrictView,
             RunView, ActivityView, ConnectionView, one CollapsibleSection
overlay/   read cached HudModel / MapModel from volatile fields; no rule evaluation per frame
plugin     AlertController (chat/sound/flash/notify), MenuTagger (per-tick memo), PanelController
             (builds PluginState, posts one invokeLater per change)
```

Tests:

- a presenter test for every row of the states table;
- `AlertPolicy` tests: transition, debounce, quiet when the character differs;
- `LockDecisions` pinned by the web's `runelitePluginParity.test.ts` simulation;
- one smoke test per view, replacing the current tree-walking tests.

### Suggested staging for the overhaul plan

- **Stage A — trust and safety fixes; each is S and needs no redesign:** U1, U2, U5, U7, U10, the stale count and nudge copy in U9, and U8.
- **Stage B — one decision source:** `ChunkLocator` and `LockDecisions` (U4, U14), the scene-border rewrite (U3), the world-map clip and defaults (U18).
- **Stage C — sidebar rebuild:** the presenter layer, status card and new sections, Copy/Terms and reasons (U6, U12, U13, U16, U17, U21, U22).
- **Stage D — settings and polish:** settings consolidation with migration, the colour-blind palette, the web guide and new captures (U11, U15, U20), and render caches (U19).

## Not covered

- **Live rendering.** Nothing was rendered in RuneLite. All overlay behaviour is inferred from code and RuneLite API contracts, confirmed by source fragments where marked and from memory where marked unverified. No profiling was done.
- **Glyphs.** Coverage in the Swing font, overlay fonts and the game chatbox for ✓ ⚠ ▼ ▶ ○ ✕ — … · was not checked.
- **Config panel behaviour.** RuneLite's ordering and reset semantics, and whether a plugin can open its own config page.
- **Scaling and newer world views.** Stretched mode and UI scaling for the banner's click target; Sailing and boat world views.
- **Accessibility and localisation.** Screen-reader names and keyboard focus order; localisation.
- **Other reviewers' areas:** detector logic, travel resolution and relay internals, beyond how they surface in the UI.

## Cross-area notes (brief)

- **Relay:**
  - 404 during pairing and the 24 h expiry copy (U2).
  - While consent is off, `pollIfDue` calls `networkAccessChanged()` every 4 s, re-sending DISCONNECTED to the panel (`TrackerConnectionController.java:97-102, 191-196`).
- **Strict Mode / core:**
  - A backup loaded while paired is treated as fresh through relay 304s (U1), so a click could be blocked on stale rules. This may be critical in their area.
  - The generic-block reason grammar and the "[Fate Guardian]" prefix (U12).
- **Rules parity:**
  - The legacy `lockStateAt` still drives the HUD, overlays and alerts (U4).
  - There is no interior or instance map in the bundle (U14).
  - The web does not export `profileName`.
- **Docs:**
  - The README says the plugin is a candidate "not submitted or accepted". ROADMAP §1 and the web guide (Plugin Hub PR #14395) say the unified plugin is merged.
  - The panel-legibility plan's Task 4, the live narrow-panel check, has no recorded result.
