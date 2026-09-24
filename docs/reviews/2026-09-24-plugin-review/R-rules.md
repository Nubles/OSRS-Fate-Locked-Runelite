# R: Rules, bundle parsing and parity with the web app

> Area report from the 2026-09-24 plugin review. The consolidated report is [`../2026-09-24-plugin-review.md`](../2026-09-24-plugin-review.md). Harness names refer to a throwaway harness that is not in this repository. Web commit `2f1697c` has the same tree as web `main` at `9617a16`. Corrections made while consolidating are marked **[Corrected]**.

Baselines: plugin `main` @ `bda88c8`. Web: branch tip `2f1697c` (`claude/quirky-archimedes-aponk4`). The task says this branch was merged to `main` as `9617a16`, but that commit is not in the local clone, so I reviewed the branch tip. Nothing in either repository was modified.

Harness (throwaway, not committed): `R-harness/`.
- `web/parity.harness.test.ts` builds real bundles with `buildBundlePayload` (real `chunk-content.json` and equipment catalogue) for 9 runs and records the web's own oracles:
  - the RegionMap tint logic
  - `chunkUnlocked`
  - `rules.chunks[*].entry`
  - `isAreaReachable`
  - `isBankReachable`
  - slayer, using `chunkUnlocked` over `slayerChunks`
- `web/fuzz.harness.test.ts` adds 130 random runs.
- `src/harness/*.java` load those bundles into the real `FateLockedBundle`, `rules/*`, `FateRuleEngine` and `Teleports`, compiled with `javac --release 11`, Gson, Lombok and the `WorldPoint` stub.
- `compare.mjs` diffs the two sides.

To rerun:

```
cd R-harness
npx vitest run --config vitest.config.mjs web/parity.harness.test.ts
java -cp java-out:../../plugin-harness/lib/gson-2.10.1.jar harness.DumpPlugin out <scenario…>
node compare.mjs out
```

## Summary

- **Land chunks match exactly.** Across 9 real runs and 130 fuzzed runs (5,616 plus 81,120 chunk checks), the plugin's lock state for the 624 authored land chunks matches the web map. This covers parent/continent rules, free areas per mode (Vanilla, legacy Xtreme, Custom none/Lumbridge), Chunked start and non-Sailing frontier, and bank ids.
- **The drift sits outside that grid.** The plugin has no rules for the 699 interior/instance regions (Keldagrim, Prifddinas, Zanaris, Mor Ul Rek, God Wars, every dungeon) or the 548 ocean chunks. It also misses the Sailing frontier in Chunked mode, and bank warnings never fire inside interiors.
- **The plugin runs two rule engines.** Overlays, HUD, chat, slayer, gear and nearest-bank read legacy root fields. The panel, menu tags, bank warning and Strict Mode read the v4 manifest. They agree on land today but disagree on ocean chunks, a wrong account and labels. The v4 path still hard-requires legacy root fields.
- **The only drift guard is weaker than it claims.** The TS parity simulation no longer mirrors the Java: it reads root fields where Java reads `rules.*`, and it tests named areas only. On a bundle from the web's own test suite it says Prifddinas and Lletya are unlocked, while the Java says they are locked. No Java test uses a web-generated bundle.
- **Proposed fix.**
  - Every decision is authored by the web, for every chunk a player can stand in.
  - One RuneLite-free rules core and one query API in the plugin.
  - Web-generated golden bundles with expected tables, asserted by JUnit.

## Findings

### R1: Interiors, dungeons, instances and off-map cities have no lock state in-game
- **Severity:** high
- **Status:** reproduced
- **Location:**
  - Plugin: `FateLockedBundle.java:563-574` (`lockStateAt` returns UNAUTHORED), `:421-427` (`permissionsAt`), `CanonicalChunk.java:18-21`, `FateLockedPlugin.java:1045-1083`.
  - Web: `services/ChunkContentService.ts:298-329` (interior content is keyed to entrance chunks), `utils/runeliteRulesManifest.ts:141-155` (snapshots only for `allChunkCoords()`), `utils/entityAccess.ts:105` (entrance-chunk gate) and `:148-155` (interior area ownership enforced).
- **Player impact:** In a Vanilla run with Keldagrim locked, the player walks into Keldagrim at chunk 44,159. The plugin shows:
  - panel: "Unknown chunk", entry Unknown
  - scene tint: the "unauthored" colour
  - chat: "Chunk (44, 159) — unauthored"
  - no locked flash and no menu tags
  - no warning when the bank opens
  - Strict Mode fails open

  Meanwhile, the web marks Keldagrim's bank, shops, quests and monsters LOCKED ("Unlock Keldagrim"). The same applies in Prifddinas (50–51,94–95), Zanaris (37–38,68–69), Mor Ul Rek (37–39,79–80), God Wars (44–45,82–83), Taverley Dungeon (43–45,151–153), Motherlode Mine (58,88), Cerberus (19,19) and similar places.
- **Evidence:**
  - `chunk-content.json` has 699 numeric interior region ids, 275 of them with entrance requirements. None appear in `rules.chunks` or the root `chunks`/`subAreaChunks`.
  - `EdgeCases.java`: every probe above returns `lock=UNAUTHORED label=null v4=false engine=UNKNOWN bankTarget=UNKNOWN`.
  - 8 of the 10 unlockable areas that have no map chunks are interiors: Dwarven Mine, Asgarnian Ice Dungeon, Motherlode Mine, Mor Ul Rek, Keldagrim, Wilderness God Wars Dungeon, Catacombs of Kourend, Zanaris.
  - `Teleports.java:179-180` already knows Zanaris is at 37,69 and Prifddinas at 51,95, but no rules exist for those chunks.
  - Instanced bosses: RuneLite `getWorldLocation()` returns instance coordinates, not template coordinates (from memory; unverified), so even template ids like Cerberus 19,19 would not match without `WorldPoint.fromLocalInstance`.
- **Fix:**
  - Web: export an `interiors` table keyed by interior chunk key, giving `{entrances, area?, requirements?}`, plus a `rules.chunks` snapshot per interior chunk, with entry = best entrance entry combined with interior-area ownership.
  - Plugin: resolve the physical chunk (through the instance template) to that snapshot.
- **Effort:** M (web), M (plugin).

### R2: Two rule engines in the plugin for one question
- **Severity:** high
- **Status:** reproduced for ocean and labels; traced for the rest
- **Location (plugin):**
  - Root-field engine (`lockStateAt`/`isUnlocked`/`isFrontierChunk`/`monsterReach`/`nearest*`/`itemTiers`):
    - `FateLockedWorldMapOverlay.java:84-86`
    - `FateLockedSceneOverlay.java:67,106,128`
    - `FateLockedMinimapOverlay.java:73,129`
    - `FateLockedHudOverlay.java:101-159`
    - `FateLockedPlugin.java:668` (slayer), `:960-962` (over-tier), `:1061-1082` (chat and flash), `:1519-1522` (pins), `:1589-1595` (infobox)
  - v4 engine (`rules.chunks` through `FateRuleEngine`):
    - `ChunkPanelViewModelFactory.java:47-50` (labels at `:116-118`)
    - `FateLockedPlugin.java:1213-1223` (menu tags), `:779` (bank warning)
    - `StrictModeGuard.java:62-76`
  - Account source: `rules.account` at `FateLockedPlugin.java:738-741`, but `state.linkedAccount` at `:1019` and `FateLockedHudOverlay.java:101`.
- **Player impact:**
  - **Ocean:** on 314 ocean chunks the panel says "Locked" and NPCs and objects get "(LOCKED)" tags, while the tint, HUD and chat say unauthored.
  - **Wrong account:** on an alt, the panel shows "Wrong account" and menu tags stop, but the map, scene and minimap keep painting the bound run's locks, and chat keeps announcing LOCKED.
  - **Labels at 43,58:** the HUD says "Mountain Camp · Fremennik" while the panel header says "Mountain Slope South".
  - **Bank semantics:** the legacy path checks only the bank roll; the v4 path checks roll, chunk entry and access.
  - **Gear:** the over-tier warning reads `itemTiers` plus `state.equipment`; Strict Mode reads `itemRules` plus `unlocks.equipment`.
- **Evidence:**
  - Harness tally for vanilla-fresh: `LOCKED/UNAUTHORED/ocean: 314`.
  - On land, v4 entry LOCKED matched overlay LOCKED for all 624 chunks in all 9 runs. Equal today only because the web writes both from the same inputs.
  - The approved plan (`docs/superpowers/plans/2026-07-24-shared-rules-compact-chunk-panel.md:394`) deliberately left overlays on `FateLockedBundle` "for v1–v3 compatibility". They now run on v4 too.
- **Fix:** plugin: one `RuleEngine` fed by web-authored decisions, used by every surface (see Structural notes).
- **Effort:** L.

### R3: The parity simulation no longer matches the Java
- **Severity:** high
- **Status:** reproduced
- **Location:**
  - Web: `utils/runelitePluginParity.test.ts:103-147` (`pluginSim`), `:165/185/195` (oracle), `:264-370` (`effectiveV4`).
  - Plugin: `FateLockedBundle.java:159-199, 268-274, 516-531`, `FateLockedPlugin.java:736-745`.
- **Differences, sim vs Java:**

| Semantic | Java | `pluginSim` |
|---|---|---|
| Unlocked regions | `rules.unlocks.regions` (v4) | root `unlockedRegions` |
| Chunked detection and set | `rules.gameModeId=='chunked'` → `rules.unlocks.chunks`, offset applied | root `unlockedChunks` presence |
| Bank lock and ids | `rules.bankLocks`, `rules.unlocks.banks`, trimmed | root `bankLocks`/`unlockedBanks` |
| Parent continent | first writer (`putIfAbsent`) | last writer |
| Chunked name → chunks | falls back to region when sub list is null or empty | `??` (null only) |
| Account and bank fallback | no fallback from `rules.account` to `state.linkedAccount` | `effectiveV4` falls back (`??`) |
| Not simulated at all | `lockStateAt`, frontier, ocean, interiors, `monsterReach`, nearest, progress counts, v4 entries, version gates | — |

- **More gaps:**
  - The oracle is `isRegionUnlocked(name, rawUnlocks)`, not the map's per-chunk logic (`RegionMap.tsx:1231-1252` with `visibleAreaUnlocks`) or `chunkUnlocked`.
  - The "v3/v4 parity" tests compare hand-written literals with each other and never touch built bundles or Java behaviour.
  - The bank test decodes the virtual id `woodcutting-leprechaun` to NaN (`:219`).
  - "Otto's Grotto" is asserted only through `'Baxtorian Falls'`. The Java returns false for the alias itself; this is harmless because no chunk carries the alias.
- **Counterexample (reproduced):** the web's own test `utils/runeliteBundle.test.ts:327-355` builds a bundle whose root `unlockedRegions` is `[Prifddinas, Iorwerth Camp, Lletya]` but whose `rules.unlocks.regions` is `[Iorwerth Camp]`.
  - The sim's field choice: Prifddinas and Lletya unlocked.
  - Real Java (`Divergent.java`): `isUnlocked(Prifddinas)=false`, `lock(34,52)=LOCKED`, `lock(36,49)=LOCKED`.
  - Production passes the same `unlocks` to both fields, so players are not affected today. The builder API allows the split, and the guard cannot see it.
- **Fix:** replace the simulation with golden bundles and expected tables asserted by the real Java (Structural notes). Web: build the root legacy fields *from* the manifest instead of separate parameters.
- **Effort:** M.

### R4: Ocean chunks and the Sailing frontier are missing
- **Severity:** medium
- **Status:** reproduced
- **Location:**
  - Plugin: `FateLockedBundle.java:563-574, 652-660`.
  - Web: `utils/chunkLocations.ts:89-90`, `utils/chunkAdjacency.ts:64-87`, `utils/oceanAccess.ts:6-9`.
- **Player impact:**
  - **Ocean (548 chunks, disjoint from land data):** the web locks them until Sailing is unlocked and Pandemonium is done (Chunked: BFS from owned coast). The plugin tints them "unauthored" and never warns. Someone sailing on an account whose run has not unlocked Sailing gets no lock cue, while menu tags from `rules.chunks` still say "(LOCKED)" (see R2).
  - **Chunked + Sailing:** the web's frontier adds offshore land reached over water and boat landings. The plugin world map shows those chunks as plain locked, so the "rollable next" amber tint is wrong. In the example: 46,45 46,47 47,44 48,44 49,45 49,46 49,47 47,46.
  - **Slayer:** with Sailing unlocked, `mogre` is web REACHABLE (via ocean chunks 42,48, 43,44, 44,44) but plugin LOCKED, so the plugin warns "Your slayer task (mogres) is in a locked area".
- **Evidence:** compare output for `chunked-sailing` and `vanilla-mid`. `rules.chunks` already carries 314 ocean snapshots, and `chunkContent` has 0 ocean keys.
- **Fix:**
  - Web: export an entry for all 548 ocean chunks, and export the Chunked frontier set, already computed with Sailing and boat landings.
  - Plugin: read both instead of computing adjacency.
- **Effort:** S–M.

### R5: Bank enforcement gaps (interiors, entrance-keyed banks, nearest-bank index)
- **Severity:** medium
- **Status:** reproduced
- **Location:**
  - Plugin: `FateLockedPlugin.java:693-702, 764-792`; `FateLockedBundle.java:224-249, 679-726`.
  - Web: `data/banks.ts`, `data/sources/bank-locations.json` (`referenceKind: "entrance"`), `utils/chunkPermissionSnapshot.ts:126-144`.
- **Player impact:** bank locks are on in every built-in mode (`config/gameModes.ts:58,84`).
  - Opening the bank in Keldagrim, Prifddinas, Zanaris, Guardians of the Rift, Mor Ul Rek (probably the bank listed as "Karamja Volcano", 44,49), the Mage Arena or similar never warns with v4 rules, even when the web says LOCKED.
    - The warning checks the player's current chunk: an interior id with no snapshot, so UNKNOWN.
    - The web keys these banks to entrance chunks, for example `11066` → 43,58 for Keldagrim and `12849` → 50,49 for Zanaris. `bank-locations.json` flags 6 as `entrance`. The interior data confirms more, for example "Mage Arena bank" at 39,73 with entrance 48,61.
  - With legacy bundles the same case falsely warns "This bank is LOCKED" even when the bank was rolled.
  - The HUD "nearest usable bank" is built from lite `poi` strings containing "bank". It finds 108 of the web's 127 bank chunks and never points to 19 of them: 53,50 22,46 45,58 47,53 43,54 23,51 25,48 43,43 58,59 52,42 21,51 38,50 41,57 42,58 43,48 55,52 59,47 40,35 34,47.
- **Evidence:** `EdgeCases.java` output (`bankTarget=UNKNOWN` at 44,159, 51,95, 37,69, 38,80); Node set difference over the bundle's `chunkContent`.
- **Fix:**
  - Web: export the bank universe `{id → name, physical chunk keys including interior ids}`.
  - Plugin: resolve bank widgets by physical chunk to bank id, and drive "nearest" from that list.
- **Effort:** M.

### R6: v4 still depends on legacy root fields, and the free-area baseline is outside the manifest
- **Severity:** medium
- **Status:** reproduced
- **Location:**
  - Plugin: `FateLockedBundle.java:376-380` (v4 requires root `chunks`), `:251-265` (fallback when `freeAreas` is absent).
  - Web: `utils/runeliteBundle.ts:112-117`, `utils/freeAreas.ts:27-32, 62` (module global), `utils/runeliteRulesManifest.ts:158-196` (no free baseline).
- **Player impact:**
  - If the web ever drops root `chunks` (the plan's "keep v3 root fields during one compatibility release", `plans/2026-07-24-shared-rules-compact-chunk-panel.md:246,274`), every v4 plugin rejects the relay bundle and keeps stale rules. Reproduced: "Bundle v4 is missing required rules fields".
  - If `freeAreas` goes missing, a legacy-Xtreme run shows Wizards' Tower UNLOCKED in the overlays while the v4 entry says LOCKED. Reproduced with the field removed.
  - `freeAreas` comes from the global set by `GameContext`'s render, not from `run.gameModeId/customMode`. A publish racing a profile switch could ship the wrong baseline. Suspected only; the relay's `isCurrent()` code check probably guards it (`OnlineSyncDriver.tsx:50, 74-77`).
  - The web's own `StreamOverlay.tsx:83-84` also reads root `unlockedChunks`/`unlockedRegions`.
- **Fix:**
  - Web: put `freeAreas` (or `startArea`) into `rules` and compute it purely from the run; list which root fields are frozen and required in a contract doc and test.
  - Plugin: prefer the manifest; fall back to deriving the baseline from `rules.gameModeId`, and only last to Misthalin.
- **Effort:** S–M.

### R7: Empty or non-bundle JSON silently replaces valid rules
- **Severity:** medium
- **Status:** reproduced at class level; plugin path traced
- **Location (plugin):** `FateLockedBundle.java:363-384`; `FateLockedPlugin.java:1313-1341` (file), `:1391-1433` (paste and clipboard).
- **Player impact:** an empty or whitespace-only file named `fate-locked-bundle*.json`, or pasting `{}`, `null` or `{"version":3}`, parses as an "empty" bundle. All locks, warnings and panel data disappear, and the UI reports success ("imported 0 regions"). This contradicts the README's "Imports replace the active rules only after complete parsing and validation." The relay path is safe because it requires v4.
- **Evidence:** `EdgeCases.java`: `""`, `"   "`, `{}`, `null`, `{"version":3}` all return `accepted … lock(50,50)=UNAUTHORED`. `FateLockedBundleTest.java:123` even documents "Whitespace parses as no bundle".
- **Fix (plugin):** reject any bundle without `version ≥ 1` and a non-empty `chunks`; never swap in `empty()` from an import.
- **Effort:** S.

### R8: Teleport destinations are a plugin-only table with no web counterpart
- **Severity:** low
- **Status:** traced; coordinates unverified (wiki blocked)
- **Location:** plugin `Teleports.java:59-197`, used by menu tags (`GuardedActionFactory.java:31`) and Travel Guardian.
- **Player impact:** the lock decision for a teleport is whatever web chunk the table names.
  - 3 of 103 destinations have no web rules at all: `prifddinas` 51,95 and `zanaris` 37,69 are interiors, and `lithkren` 55,61 has no data (web Lithkren is 55,62 and 56,62). These are always UNKNOWN.
  - Several destinations land in chunks owned by a different area in web data:
    - `rellekka` → 41,56 (Kandarin terrain; web Rellekka is 40–42,57–59)
    - `trollheim` → Burthorpe
    - `senntisten` → Varrock
    - `ice plateau` → Mage Arena
    - `fremennik` → Kandarin terrain

    A run that unlocked Rellekka but not all of Kandarin gets "Rellekka … (LOCKED)". I could not verify whether that is the true landing chunk.
- **Fix:**
  - Web: export teleport and transport destinations with their owner chunk, from its transport data.
  - Plugin: consume that list; keep keyword matching only as a classifier.
- **Effort:** M.

### R9: Unlock-progress numbers differ from the web
- **Severity:** low
- **Status:** reproduced
- **Location:**
  - Plugin: `FateLockedBundle.java:289-305`, `FateLockedHudOverlay.java:153-159`, `FateLockedPlugin.java:1585-1596`.
  - Web: `components/RunCard.tsx:432-441`.
- **Player impact:** the same run shows "24/187 regions" on the web and "21/177" in the HUD and infobox (vanilla, all of Asgarnia plus Catherby); Custom-none shows 15/187 vs 12/177. The plugin counts only sub-areas that have chunks (177) and omits the 10 chunkless areas.
- **Fix:** web exports the progress numbers; plugin displays them.
- **Effort:** S.

### R10: A v4 non-Chunked bundle carrying root `unlockedChunks` flips to Chunked resolution
- **Severity:** low (latent)
- **Status:** reproduced
- **Location:** plugin `FateLockedBundle.java:169-172`.
- **Player impact:** none today, because the web only sends the field for Chunked runs. If it ever did, free Varrock would read LOCKED in the overlays while the v4 entry says ALLOWED (reproduced by adding the field).
- **Fix (plugin):** for v4, decide Chunked-ness only from `rules.gameModeId`.
- **Effort:** S.

### R11: Account binding uses two sources and different normalisation
- **Severity:** low
- **Status:** traced
- **Location:**
  - Plugin: `FateLockedPlugin.java:736-761, 1006-1043`, `FateLockedHudOverlay.java:101-111`.
  - Web: `utils/runeliteRulesManifest.ts:165`, `services/fateEventProtocol.ts:55-57`.
- **Detail:**
  - Rule gating uses `rules.account`, which the web trims. The HUD and the mismatch chat use `state.linkedAccount`, which is untrimmed.
  - The web normalises by trimming, collapsing whitespace and lower-casing. The plugin uses `Text.sanitize` plus lower-casing (whether that trims is unverified).
  - Overlays ignore the binding entirely (see R2).
  - Low risk in practice, since `linkedAccount` comes from the Wise Old Man `displayName`.
- **Fix:** one account source (`rules.account`) and the web's normalisation.
- **Effort:** S.

### R12: Fields read but never sent, and sent but never read
- **Severity:** low
- **Status:** traced
- **Detail:**
  - `profileName` is read (`FateLockedPanel.java:640`) but has never been sent (`git log -S` finds nothing), so the Run section's Profile row always shows "—".
  - The lite `chunkContent.inside` list is ignored (`FateLockedBundle.java:105-107`).
  - `chunkOffset` is applied to `chunks`, `subAreaChunks` and `unlockedChunks`, but not to `slayerChunks`, `chunkContent`, bank ids or `rules.chunks`. The web always sends `{0,0}`.
  - `rules.detectorPolicies` is ignored (detector reviewer).
  - "imported N regions" counts the 13 continents in static data, whatever the run's state (`FateLockedPlugin.java:1411-1412`).
- **Fix:** send `profileName` or drop the row; drop `chunkOffset` (assert 0); fix the status text.
- **Effort:** S.

### R13: Size assumptions in the parser are off by about 10x
- **Severity:** low
- **Status:** reproduced
- **Location:** plugin `FateLockedBundle.java:327-332`, `CONTRIBUTING.md:42`.
- **Detail:**
  - The docs say a full bundle "inflates to about 120 KiB".
  - Real bundles are 1.28–1.32 MiB plain, about 149 KiB gzip and about 198 KiB FLGZ. `rules.chunks` is 76% of that.
  - The 8 MiB cap still leaves about 6x headroom.
  - The relay body limit (256 KiB) leaves about 22% headroom (relay reviewer).
  - A cold parse takes 180 ms on the client thread (14 ms warm).
- **Fix:** correct the docs; compact the `rules.chunks` encoding (Structural notes).
- **Effort:** S (docs), M (encoding).

### R14: File import decodes with the platform default charset
- **Severity:** low
- **Status:** suspected
- **Location:** plugin `FateLockedBundle.java:322`.
- **Detail:** real bundles contain 1,592 "·" characters in SKILLING details. If RuneLite's JVM default charset is not UTF-8 (unverified; Windows with Java ≤17 is likely), file imports show "Â·". Clipboard and relay imports are unaffected.
- **Fix:** `new String(bytes, UTF_8)`.
- **Effort:** S.

### R15: No version or capability policy beyond "reject version > 4"
- **Severity:** low
- **Status:** traced
- **Location:** plugin `FateLockedBundle.java:372-375`; web `utils/runeliteBundle.ts:107`.
- **Detail:**
  - Any v5 bump breaks every installed plugin: the relay keeps stale rules and clipboard imports fail.
  - `rulesVersion` and `contentVersion` are never checked, so a semantic change under the same `version` would be misread silently.
  - The Hub-pinned commit named in the web ROADMAP is not in this repository's history, so which plugin builds are in the wild cannot be checked here. **[Corrected]** The review clone was shallow. The Plugin Hub pins `52f45f5`, which is in this repository's history (A17).
- **Fix:** freeze `version: 4` for additive changes; add `rules.schema` or `capabilities` that the plugin can ignore; add a web test that fails on a version bump without a recorded plugin release.
- **Effort:** S.

### R16: Slayer gating is coarser than the web's
- **Severity:** low
- **Status:** traced
- **Location:** plugin `FateLockedBundle.java:581-626`, `FateLockedPlugin.java:640-691`; web `utils/slayerReach.ts:127-160`, `services/ChunkContentService.ts:631-643`.
- **Detail:** the plugin unions all masters' locations, so the following cases fail open (no warning where the web says area-locked):
  - Krystilia's Wilderness-only tasks
  - Konar's location tasks
  - interior-area and entity-access requirements
  - Separately, the current task is not restored after a client restart (detectors area).
- **Fix:** web exports task entries per master (it already emits `master:task` keys); plugin passes the master when it knows it.
- **Effort:** S–M.

## Verified correct

- **Land lock parity.**
  - 9 real `buildBundlePayload` runs: vanilla fresh, vanilla mid (legacy parent, alias, chunkless area, island, Sailing), legacy Xtreme, Xtreme with all Misthalin, all of Asgarnia, Chunked fresh, Chunked with Sailing, Custom none with banks off, Custom Lumbridge.
  - 130 fuzzed runs: random region sets in vanilla, xtreme and none modes, including continents, Misthalin, aliases and Tutorial Island, plus random Chunked walks up to 80 chunks.
  - Result: 0 mismatches across 81,120 fuzzed and 5,616 real-run chunk checks between the web map tint and `chunkUnlocked` on one side and `lockStateAt` on the other. The Chunked frontier matches whenever Sailing is locked.
  - In the 9 real runs, v4 `entry == LOCKED` held exactly when the overlay showed LOCKED for all 624 land chunks. NOT_READY entries show as unlocked on the web map too.
- **Free areas.** `freeAreas` matched the web global in every mode: Vanilla gets Tutorial Island plus full Misthalin; legacy Xtreme and Custom Lumbridge get `[Tutorial Island, Lumbridge]`; Chunked and Custom none get `[Tutorial Island]`. Legacy Xtreme's 8 rollable Misthalin areas, and Misthalin terrain once all 9 are unlocked, match. The Chunked start is 50,50 on both sides.
- **Banks.** For all 127 physical bank ids in all 9 runs, `isBankReachable` equals `isBankUnlocked`. Bank locks are on in built-in modes and off in Custom when set.
- **Parsing.** `ALLOWED`, `NOT_READY`, `LOCKED` and `UNKNOWN` parse, with unknown values falling back to UNKNOWN. v4 with missing required fields is rejected. v5 is rejected.
- **New fields.** Unknown fields are ignored: `unlocks.housing`, `unlocks.storage`, `equipmentCatalogue`, `detectorPolicies`, root `gameModeId` and `exportedAt`. The "housing and storage" parity test pins this.
- **Equipment.**
  - Slot names match (`EQUIPMENT_SLOTS` vs `SLOT_NAMES`).
  - Legacy `itemTiers` and v4 `itemRules` carry the same 2,405 reviewed ids.
  - Estimated tiers fail open in the plugin by design, pinned by the web parity test. The web picker still disables "Est." items, which is intentional and should be explained to players.
- **Slayer.** 205 plain task keys matched in every run except the ocean `mogre` case (R4).
- **Web tests.** `npx vitest run utils/runelitePluginParity.test.ts utils/runeliteBundle.test.ts utils/runeliteRulesManifest.test.ts`: 31 of 31 passed.
- **Data invariants hold.** No chunk sits in two regions or two sub-areas. Every sub-area chunk is on the map. No sub-area key is an alias. Ocean and land chunk sets are disjoint. Tutorial Island is unmapped on both sides.

## Structural notes for the overhaul

### Contract rules (write these into both READMEs and pin them with tests)
1. The web authors every decision. The plugin looks decisions up and never re-derives unlock rules for v4. Legacy resolution lives only in a frozen v1–v3 adapter.
2. `version` stays 4. Additive features go behind `rules.schema` or `capabilities`. Never repurpose a field. Dropping any root field needs a recorded plugin release that no longer reads it.
3. The manifest is self-sufficient: free baseline, Chunked frontier, ocean and interior entries, the bank universe, teleport destinations and progress numbers all live in `rules`. The root legacy fields are *derived from* the manifest by one function, so root and rules can never diverge (R3 counterexample).

### Drift prevention: golden bundles
- **Web.** Add `scripts/runelite-goldens.ts`, run in `--check` mode like `chunks:verify`. For a fixed matrix, it writes `goldens/<scenario>.bundle.json` plus `.expect.json` computed from the web's own functions, not a simulation.
  - Matrix: the modes above, Sailing on and off, wrong account, degraded export, malformed/legacy/future inputs.
  - Expected tables:
    - per chunk key (land, ocean, interior): status, frontier, label
    - per bank id: usable
    - per slayer task (and master): result
    - sampled item ids: ALLOWED/LOCKED
    - account cases
    - progress numbers
    - accept or reject for each input variant
- **Plugin.** A sync script copies the goldens pinned to a web commit, recorded in `SOURCE`. A parameterised JUnit `GoldenBundleContractTest` runs them through the real codec and `RuleEngine`. Add "inject random extra fields" and "drop each optional field" variants to prove forward compatibility and fallbacks.
- **Remove `pluginSim`.** It is a third implementation that has already drifted.
- **Make the rules core RuneLite-free.** Move `CanonicalChunk.of`/`southWestTile` into an adapter. The core then compiles with plain `javac` plus Gson, as this harness did, so web CI can also run the Java golden test.

### Proposed target design for rules resolution in the plugin
- `BundleCodec` turns bytes into either `RulesSnapshot` or a typed rejection. It never returns `empty()` for garbage (R7). It has version adapters (v1–v3 legacy, v4). All-or-nothing apply.
- `RulesSnapshot` is immutable and holds web-authored data only:
  - chunk entries (land, ocean, interiors via their owner) with label and reason
  - frontier set
  - bank universe
  - item rules
  - slayer index by master and task
  - mobility
  - teleport destinations
  - progress
  - account
  - free baseline (legacy adapter only)
- `RuleEngine` is the single query API with trust gates (account, freshness, legacy). The same four statuses feed overlays, HUD, chat and flash, pins, panel, menu tags, bank/slayer/gear warnings and Strict Mode.
- `WorldResolver` maps RuneLite positions to canonical chunk keys, including instance templates and (unverified) boats. It is the only RuneLite-aware piece in rules resolution.
- **Size.** Encode `rules.chunks` compactly (status codes per chunk; interned row-name and detail tables) to cut the 1.3 MiB plain / 198 KiB FLGZ payload. This keeps relay headroom and lowers client-thread parse cost.
- **Staging.**
  1. Goldens and the Java contract test, plus R7 and R14 (S).
  2. Web adds interiors, ocean, frontier, banks, `freeAreas` and progress to `rules` additively (M).
  3. Plugin moves every surface to `RuleEngine` and deletes the second engine (L).
  4. Compact encoding behind a capability flag (M).

## Not covered

- The full plugin Gradle suite and any in-client behaviour: `repo.runelite.net` is blocked. Only `FateLockedBundle`, `CanonicalChunk`, `rules/*`, `panel/*` and `Teleports` were run, with a `WorldPoint` stub.
- Anything needing the OSRS Wiki or a live client is unverified:
  - teleport landing tiles
  - where the in-game world map draws Prifddinas
  - player coordinates while sailing (world entities)
  - `getWorldLocation` in instances
  - `Text.sanitize` trimming
  - RuneLite's JVM default charset
- Other reviewers' areas: relay transport and ETag handling, detectors, sidebar and overlay UX details, Strict Mode decision logic, and core lifecycle. Cross-area items are noted above.
- Whether the web's own rules are right; I checked parity only. One web-side observation to pass on: interior content is gated by the entrance chunk's lock even when the interior's own area is unlocked. With Keldagrim unlocked but 43,58 (Mountain Camp) locked, all of Keldagrim reads "Location locked", which may or may not be intended.
- Map-authoring drafts in localStorage (`RegionMap` `draftChunks`) can make an author's web map differ from shipped data. Not relevant to players.
- The web `main` merge commit `9617a16`, and older installed plugin builds. **[Corrected]** The Hub-pinned commit `52f45f5` is in this repository (A17).
