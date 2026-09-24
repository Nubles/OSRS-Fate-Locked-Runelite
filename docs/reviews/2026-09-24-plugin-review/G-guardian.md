# G — Strict Mode (Guardian) and travel blocking

> Area report from the 2026-09-24 plugin review. The consolidated report is [`../2026-09-24-plugin-review.md`](../2026-09-24-plugin-review.md). Harness names refer to a throwaway harness that is not in this repository. Web commit `2f1697c` has the same tree as web `main` at `9617a16`. Corrections made while consolidating are marked **[Corrected]**.

Plugin `bda88c8` (main). Web app: local checkout of `claude/quirky-archimedes-aponk4` at `2f1697c`. The merge commit `9617a16` isn't in the local clone, so web cross-checks use the branch tip.
Harness: `G-harness` (not committed). The real plugin sources (guardian, travel, rules, `FateLockedBundle`, `Teleports`, `TravelGuardianPluginShell`) were compiled with `javac --release 11` against local RuneLite stand-ins in `stubs/`. There are 13 probe tests (`test/.../GuardianProbeTest.java`, `test/com/fatelocked/ShellProbeTest.java`), and all of them pass; `G-harness/run.sh` rebuilds and reruns them. RuneLite reference sources fetched from GitHub are in `ref/`. The Chunk Picker teleport extract is in `chunkpicker-teleports.txt`.

## Summary
- Strict Mode is two enforcement pipelines, not one. The first is the documented Travel Guardian (account-bound, "exact"). The second is an older generic guard that also consumes NPC, object, bank and Wear/Wield clicks under weaker trust. It enforces bundles that have no bound account, whichever character is logged in, or none. The README, Hub review notes, CONTRIBUTING and the web guide describe only the first pipeline.
- Travel recognition is a substring match of menu text against a hand-kept coordinate table. Walking reads mouse pixels as scene tiles. Several destinations and unlock families disagree with the web app's own data, and multi-destination actions are treated as exact. The result is sporadic false blocks and broad misses.
- The fail-safe story has two gaps. "Fresh" means "imported under 15 minutes ago", so an old bundle file is enforced for 15 minutes after every client start. And the panel shows a green "On" whether or not protection is actually active.
- Thread safety and per-menu-entry cost are fine. The audit log is private, but it mislabels paused actions and rewrites its file on the client thread for every blocked click.
- Recommendation: one decision service over one trust snapshot, travel data authored by the web app into the bundle, and Strict Mode limited to travel until classification is ID-based. Show a visible readiness state (whether protection is actually active, and why not).

## Findings

### G1 — "Walk here" destination is computed from mouse pixels, not the clicked tile
- **Severity:** high. **Status:** reproduced (logic, in the harness). The RuneLite parameter semantics are traced from external sources; not confirmed on the live client.
- **Location:** `guardian/travel/TravelActionResolver.java:22-30,116-125`; `guardian/GuardedActionFactory.java:24-29,74-105`. There is also a dead copy at `FateLockedPlugin.java:1240-1275`, whose comment claims WALK params are scene coordinates.
- **Player scenario:** the player stands in unlocked Lumbridge (chunk 50,50) and clicks the ground in the top-left corner of the game view. The params, about (40,30) px, are read as scene tile (40,30). That maps to chunk 50,49, which is locked, so the click is consumed with "Travel blocked — Walk here / East Lumbridge Swamp is locked", even though the clicked tile can be inside Lumbridge. Clicks anywhere else (x or y ≥ 104 px) resolve to Unknown, so the promised "walk into a locked chunk" protection almost never fires. The same bug makes `onMenuEntryAdded` append "(LOCKED)" to "Walk here" in that corner.
- **Evidence:**
  - Fact: several deobfuscated OSRS clients, spanning revisions of roughly 184 to 213, build the entry with `insertMenuItemNoShift("Walk here", "", 23, 0, var0 - var2, var1 - var3)`. The call site is `addSceneMenuOptions(mouseX, mouseY, viewportX, viewportY)`, so the params are viewport-relative mouse pixels. Examples: `MatthewBishop/213-deob` (`src/ScriptFrame.java` and its call site in `src/ViewportMouse.java`), `SanLiteOSRS/SanLite`, `runetopic/openosrs`.
  - Fact: RuneLite core `GroundMarkerPlugin` resolves WALK targets through `client.getWorldView(entry.getWorldViewId()).getSelectedSceneTile()` and never reads the params (`ref/GroundMarkerPlugin.java:261-276`).
  - Harness `walkTreatsParamsAsSceneTiles`: pixel (40,30) resolves to EXACT WALK with destination 50,49, and the click is consumed. Pixel (380,210) resolves to Unknown.
  - The repo's tests pin the wrong assumption: they mock `WorldPoint.fromScene(client, 10, 20, 0)` for WALK (`TravelActionResolverTest.java:29-39` and the coordinator and shell walk fixtures).
  - The docs conflict on whether walking may be blocked at all:
    - The 07-24 design blocks exact walks.
    - The 07-27 unified design says "walking is never blocked" (`docs/superpowers/specs/2026-07-27-unified-plugin-hub-design.md:195`).
    - The panel intro says "uncertain movement is never blocked" (`FateLockedPanel.java:364`).
    - The code and tests block exact walks.
- **Fix:** stop consuming WALK and make it warning-only, as the 07-27 design decided; this also reduces Hub risk. If walk blocking returns later, take the tile from the world view's selected scene tile captured when the menu is built, instance-mapped. Delete `menuTargetWorldPoint`. **Effort:** S.

### G2 — Generic Strict Mode categories enforce unbound rules on any character
- **Severity:** high. **Status:** traced. The plugin's own test pins this behaviour.
- **Location:**
  - `FateLockedPlugin.java:736-745`: `currentAccountMatches` returns true when `rules.account` is empty.
  - `FateLockedPlugin.java:1100-1105`: builds the generic context.
  - `FateLockedPlugin.java:1116-1153`: the generic handler.
  - Test `FateLockedPluginTravelAccountBindingTest.unboundRulesWithAbsentPlayerRetainGenericEquipmentEnforcement` sets no account and no local player, and verifies `consume()`.
- **Player scenario:** the tracker profile has no linked account. That is a normal state: the pairing dialog shows "No bound account" (`components/RunelitePairingDialog.tsx:82`), and the export writes `account: null` (`utils/runeliteRulesManifest.ts:165`). The player turns Strict Mode on and logs into their main account. "Wield" on a Dragon scimitar is consumed ("[Fate Locked] Prevented: dragon scimitar — is T5; Weapon is unlocked to T1."). Bank clicks at locked banks, unrolled farming patches and service NPCs are blocked the same way. This happens on a character the rules don't belong to, and even while no local player exists.
- **Evidence:** the travel path uses `strictTravelAccountMatches` (`FateLockedPlugin.java:747-761`), which fails open in exactly this case, so the two paths disagree. The behaviour contradicts:
  - `CONTRIBUTING.md:88` ("rules belong to the logged-in, correctly bound account")
  - `README.md:100-103`
  - `docs/plugin-hub-review-notes.md:38,48`
  - the web guide (`data/runeliteGuide.ts:593`)
- **Fix:** one trust gate for every category that consumes clicks: bound account, matching logged-in character, fresh rules, v4 bundle. Unbound bundles become advisory only. **Effort:** S.

### G3 — The Hub review notes under-describe what Strict Mode consumes
- **Severity:** high (review risk). **Status:** traced. The reviewer stance is inferred.
- **Location:** `docs/plugin-hub-review-notes.md:34-58`; `README.md:98-110`; `guardian/StrictModeGuard.java:10-32`; `FateLockedPlugin.java:1116-1153`.
- **What goes wrong:** the notes and README present Strict Mode as Travel Guardian, with an exhaustive fail-open list ("exact", "account-bound", "recognition confidence is not exact"). The code consumes more than that:
  - NPC options: any non-Examine option on an NPC whose menu name equals an authored row, such as Talk-to, Trade or Attack.
  - Object options, including harmless Inspect and Guide on unrolled farming patches.
  - Bank options, including "Collect", which GE clerks also offer; whether the web rules treat GE collection as banking is unverified.
  - Wear, Wield and Equip.
  - Exact walks (G1).

  The generic categories go through a path with no confidence concept and with the unbound trust of G2. RuneLite's "Rejected or Rolled-Back Features" page (`ref/rejected.md`) lists "Conditional menu entry removing … hiding attack options on NPCs/players based on some conditions, like … it being specific type of NPC". Generic NPC consumption is the closest match, and it is the one the notes don't disclose.
- **Evidence:** a grep over `src/main` finds `consume()` only in `StrictModeClickHandler.java:24,38`. The only menu mutation is the "(LOCKED)" `setTarget` at `FateLockedPlugin.java:1230`. No automated gate pins this surface: `PluginHubNetworkBoundaryTest` covers only the network, and `UnifiedPluginContractTest` covers only the descriptor.
- **Fix:** decide the scope before submitting. Option (a): Strict Mode is travel-only, and NPC, object, bank and equip checks become warnings or tags. Option (b): disclose every category with its exact conditions. Either way, add a source-boundary test that pins the single consume call site and forbids `setMenuEntries`, `createMenuEntry` and `setDeprioritized`. **Effort:** S for docs and the test; M if the scope is reduced.

### G4 — "Fresh" means recently imported, not recent rules
- **Severity:** medium. **Status:** traced.
- **Location:**
  - `FateLockedPlugin.java:1193-1198`: `rulesAreFresh`.
  - `FateLockedPlugin.java:1331`: `reloadBundle` sets `rulesImportedAt = Instant.now()`.
  - `FateLockedPlugin.java:448`: `startUp` calls `reloadBundle`.
  - `FateLockedPlugin.java:733,1096,1102`: `FateRuleEngine`'s `staleImport` argument is always false.
  - `rules.exportedAt` and `runRevision` are never read.
- **Player scenario:** a file or clipboard user without the relay. The newest `fate-locked-bundle-*.json` in `~/.runelite/fate-locked` was exported two weeks ago. Every client start marks it fresh for 15 minutes; so do re-enabling the plugin, "Reload saved bundle" and toggling Auto-reload. During that window Strict Mode blocks teleports into areas unlocked since the export. After 15 minutes it silently stops blocking until the next import (see G5).
- **Relay users:** fine while polls succeed, because `lastSync` refreshes on 304. Cross-area: a clipboard paste made while paired stays "fresh" for as long as the relay keeps answering 304, even though the active rules are the pasted ones.
- **Fix:**
  - File and clipboard bundles: base freshness on `rules.exportedAt`, with a clock-skew tolerance, and enforce `runRevision` monotonicity.
  - Relay: freshness is the last confirmation of the version that is actually active.
  - Show the rules' age in the panel.
- **Effort:** M. This touches the core and relay areas.

### G5 — Inactive protection is shown as a green "On"
- **Severity:** medium. **Status:** traced.
- **Location:** `FateLockedPanel.java:414-425` (status derived from enabled/paused only); `FateLockedPlugin.java:1188-1192`. No HUD or overlay shows Strict Mode state. The web guide (`data/runeliteGuide.ts:318-323`) says "On means Strict Mode is active".
- **Fail-open or fail-closed, by condition (from the code):**

| Condition | Travel path | Generic path (NPC, object, bank, equip) | What the player sees |
|---|---|---|---|
| No bundle, or legacy v1–v3 | open | open | "On" |
| v4 bundle with empty `rules.account` | open | **enforces**, for any character or none | "On" |
| v4 bundle, another character logged in | open | open | one chat warning per login, only if `warnAccountMismatch` is on and `state.linkedAccount` is set |
| v4 file or clipboard import, under 15 min since load (any export age) | **enforces** | **enforces** | "Offline snapshot" in the chunk panel |
| Over 15 min since load, or relay unconfirmed for over 15 min | open | open | "On" |
| Future-version or invalid import | previous rules kept | same | flash status |
| Paused | open (and audited, see G9) | open | sidebar only |

- **Player impact:** the player believes accidental locked travel is being caught when it isn't: stale rules, an unbound bundle, the wrong character, or a name mismatch. For the last one, `normName` doesn't fold "_" into " "; how OSRS canonicalizes names is unverified. The design required "report that protection is unavailable", "show the last successful sync time" and "show the binding mismatch" (`specs/2026-07-24-…`, Error handling).
- **Fix:** derive a `GuardianReadiness {ACTIVE, INACTIVE(reason)}` from the same trust gate that enforcement uses. Render it in the panel and the HUD, and include it in the block banner. **Effort:** S–M.

### G6 — Travel unlock families drift from the web app's mobility list
- **Severity:** medium. **Status:** reproduced in the harness (resolver, evaluator and coordinator). In-game option text is unverified.
- **Location:** `TravelActionResolver.java:53-94` against the web's `MOBILITY_LIST` (`data/items.ts:18-26`).
- **Cases:**
  - **Separate web unlocks lumped together.** Slayer ring, Drakan's medallion, Xeric's talisman, Digsite pendant and Ring of the elements are separate web unlocks, but the plugin requires "Jewelry Teleports" for all of them.
    - Harness `digsitePendantRubIsBlockedByTheWrongUnlockFamily`: "Digsite Pendant" unlocked, chunk allowed, and the click is still consumed with "Jewelry Teleports is not unlocked".
    - `drakanMedallionIgnoresItsOwnWebUnlock` shows the same thing.
    - The reverse is a miss: a player with only Jewelry Teleports unlocked is allowed.
  - **Necklace of passage.** The "Eagles' Eyrie" option contains the substring "eagle", so it is classified as the EAGLE family and requires "Eagle Transport". A player with Jewelry Teleports unlocked is blocked (`necklaceOfPassageEyrieRequiresEagleTransport`).
  - **Teleport tablets.** Real tablet names (for example "Varrock teleport", which doesn't contain "tablet"; the item name is from memory) resolve as `named-teleport` with no unlock check. The web's "Teleport Tablets" unlock is never enforced (`tabletsNeverCheckTeleportTabletsUnlock`), yet the alternative finder requires it before suggesting a tablet.
  - **Never checked at all:**
    - Spellbook arcana (Ancient Magicks, Lunar, Arceuus).
    - Ectophial, Royal seed pod, Enchanted lyre, Camulet and Pharaoh's sceptre.
- **Fix:** the web app authors an unlock id for each travel method in the bundle; no substring-based family inference. **Effort:** M.

### G7 — Ambiguous and unsourced destinations are treated as exact
- **Severity:** medium. **Status:** matcher behaviour reproduced; destination errors backed by the web repo's own data. Menu text and landing tiles are unverified.
- **Location:** `Teleports.java:25-49,59-197,225-267`; `TravelActionResolver.java:32-39`.
- **Multi-destination actions treated as one destination:**
  - **Digsite pendant.** "Rub" opens a three-destination dialog. The text contains the key "digsite", and "rub" counts as an activation, so it resolves EXACT to 52,53. It is blocked whenever Digsite is locked, even if the player intends unlocked Fossil Island (`digsitePendantRubIsBlockedWhenOnlyTheDigsiteChunkIsLocked`).
  - **Configurable spells.** Camelot→Seers', Varrock→Grand Exchange and Watchtower→Yanille (diary toggles; unverified) each resolve by name to one chunk. The "seers" key maps to 43,54, which the web's `chunkNames.ts:362` labels Camelot; Seers' Village is 42,54 (`chunkNames.ts:344`).
  - **Ape Atoll Teleport.** The standard and Arceuus spells share this name, and the Chunk Picker data gives different destinations: region 11051 on the surface versus "Ape Atoll Dungeon". The plugin uses 43,43 for both.
- **Table rows that disagree with the web app's own data:**
  - `senntisten` maps to 51,52, which the web calls "East Varrock Mine" (`chunkNames.ts:517`). Chunk Picker "Cast senntisten teleport" gives region 13364, which is 52,52, "Exam Centre" (`chunkNames.ts:538`).
  - `battlefield of khazard` maps to 41,49, "Port Khazard". The web has 39,50 as "Khazard Battlefield" (`chunkNames.ts:291`).
  - `hosidius` maps to 24,53, "South Kourend Woodland" (`chunkNames.ts:109`). The web's Hosidius chunks are 26–28,55–56.
  - `fossil island` maps to 58,59, "Museum Camp". The web has "House on the Hill" at 58,60 (`chunkNames.ts:619-620`).
  - The key is spelled `carrallangar`, but the Chunk Picker data and the web use "Carrallanger", so the spell never matches.
  - Any "Teleport" option on an item whose name contains "fremennik" resolves to 43,56, "Golden Apple Tree" (for example, sea boots; the option is unverified).
- **Tests prove the matcher, not coverage:** the fixtures use strings the game doesn't produce, such as "Fairy ring — Zanaris", "Varrock teleport tablet", "Amulet of glory - Edgeville" and "Magic carpet to Nardah" (`TravelActionResolverMatrixTest.java:44-57`).
- **Fix:**
  - Match by ID (item id, spell widget, object id from the `MenuEntry`) against a web-authored travel table that declares single versus multiple destinations. Multi-destination actions are Unknown.
  - Add a web-side parity test generated from the Chunk Picker challenges, which already list destination chunks for about 40 spells.
- **Effort:** M–L.

### G8 — Five divergent answers to "is this locked?"
- **Severity:** medium. **Status:** traced; one case reproduced.
- **The five decision paths:**
  1. Travel guard: v4 chunk `entry` plus mobility (`TravelRuleEvaluator`).
  2. Generic guard: v4 target rows matched by exact name (`StrictModeGuard.decision`).
  3. Menu tags: chunk `entry` only, the `include=false` teleport matcher, no freshness check, unbound treated as trusted, and legacy `lockStateAt` for v1–v3 bundles (`FateLockedPlugin.java:1199-1232`).
  4. Entry warnings, flash and HUD: legacy `lockStateAt`, even for v4 bundles (`FateLockedPlugin.java:1059-1082`).
  5. Bank warning: uses the player's chunk (`FateLockedPlugin.java:764-792`), while the guard uses the chunk of the banker or booth.
- **Visible inconsistencies:**
  - A goblin is tagged "Goblin (level-2) (LOCKED)", but Strict Mode lets you attack it. The menu target keeps "(level-2)", so it never equals the row name "Goblin" (`genericNpcMatchingDependsOnTheLevelSuffix`). Only NPCs without a level suffix, such as the service NPCs Ellis or Tanner, ever match. The repo's tests use "Goblin" without the suffix, so they pin behaviour the game won't produce.
  - The "Zanaris" fairy-ring option is blocked but never tagged.
  - A teleport blocked for its mobility unlock is never tagged.
  - "Inspect" on a farming patch is blocked with no tag.
- **Fix:** see the target design below. **Effort:** L (core of the overhaul).

### G9 — The audit log mislabels paused actions and writes on every click
- **Severity:** low. **Status:** reproduced.
- **Location:** `TravelGuardianCoordinator.java:54-61` (the paused branch fires for ALLOWED decisions too); `TravelGuardianPluginShell.java:126-136,165-181`; `StrictModeAuditLog.java:41-47,176-194`; `FateLockedPlugin.java:1137-1152`.
- **Issues:**
  - (a) While paused, every exact ALLOWED travel is stored as ALLOWED_PAUSED (`pausedAllowedTeleportIsAuditedAsAllowedPaused`). The entry claims the pause let an action through when nothing was locked.
  - (b) Each consumed click appends and rewrites the whole JSON file synchronously on the client thread. That took about 0.65 ms per append on tmpfs in the harness; it was not measured on Windows or with antivirus scanning. There is no dedupe, so spam-clicking a blocked bank fills the 100-entry cap and evicts real history.
  - (c) The two paths store reasons in different formats: "is T5; Weapon is unlocked to T1" versus "Varrock is locked".
  - (d) Entries carry no account key, so "Recent prevented" mixes characters.
- **Privacy:** fine. Entries contain no account names, inventory or chat, and the loader rejects unknown fields.
- **Fix:** log only Locked-while-paused; dedupe by fingerprint; write off the client thread; use one entry builder. **Effort:** S.

### G10 — How to get unstuck depends on which category blocked you
- **Severity:** medium. **Status:** traced. Overlaps with the UX review.
- **Location:** `FateLockedTravelBlockOverlay.java` (travel notices only); `FateLockedPlugin.java:1116-1153` (generic blocks produce one chat line and no banner); `FateLockedPanel.java:172-198`.
- **Issues:**
  - Generic blocks (bank, equip, NPC, object) show "[Fate Locked] Prevented: … — is …". The message doesn't mention Strict Mode or how to pause it.
  - The banner's pause button exists only for the 4 seconds after a travel block.
  - The HUD shows no paused or active state; the design asked for an amber countdown. There is no hotkey.
  - The headline shows lowercased menu text plus the tag: "Travel blocked — Cast varrock teleport (locked)" (harness probe).
  - For a blocked walk, the "nearest legal option" is still a teleport tablet.
  - The "same area" ranking compares per-chunk names, so it rarely applies.
- **Fix:**
  - One presenter for every block: a banner with pause, and a chat line that names Strict Mode and how to pause.
  - Show readiness and pause state in the HUD.
  - Strip tags from labels and keep the original casing.
- **Effort:** S–M.

### G11 — Tile resolution ignores world views and instances, and an object's tile is not its destination
- **Severity:** low. **Status:** traced; the Sailing behaviour is suspected.
- **Location:** `TravelActionResolver.java:41-49,116-125`; `GuardedActionFactory.java:74-83`; `FateLockedPlugin.java:1106-1111`.
- **Facts:**
  - The code uses the deprecated `WorldPoint.fromScene(Client, …)`, which assumes the top-level world view, and ignores `MenuEntry.getWorldViewId()`.
  - The player's origin is not instance-mapped.
  - RuneLite core does both: it uses `client.getWorldView(entry.getWorldViewId())` and `WorldPoint.fromLocalInstance` (`ref/ObjectIndicatorsPlugin.java:272,443-449`).
- **Inferences:**
  - Instances fail open (a miss).
  - Objects in another world view, such as Sailing boats (unverified), would be mapped onto the wrong scene and could produce a false boundary-object block.
  - The boundary-object "destination" is the object's own tile, so ladders and caves in the player's chunk are never checked (a miss). Objects that straddle a chunk edge are suspected false blocks.
- **Fix:** one world-view-aware, instance-aware tile resolver. Treat boundary objects as Unknown unless a web-authored link says where they lead. **Effort:** M.

### G12 — The generic path consumes before explaining and blocks harmless options
- **Severity:** low. **Status:** traced.
- **Location:** `FateLockedPlugin.java:1116-1153`; `TravelGuardianPluginShell.java:107-113`; `GuardedActionFactory.java:23`.
- **Facts:**
  - The click is consumed at line 1121, before the chat and audit entries, while `CONTRIBUTING.md:93` requires staging them first.
  - The generic handler runs outside the shell's try/catch, so an exception after the consume leaves an unexplained block.
  - Every option not starting with "examine" is guarded, including "Inspect".
  - The MOVEMENT → WARN_ONLY outcome is never acted on.
- **Fix:** fold the generic path into the single pipeline, with an allowlist of options per category. **Effort:** S.

### G13 — Most transport families can never be recognised
- **Severity:** low. The design is fail-open, but the design document and README promise this coverage. **Status:** traced; menu flows unverified.
- **Location:** `Teleports.java`; `TravelActionResolver.java`.
- **Never recognised:**
  - Fairy-ring codes ("Last-destination (CKS)").
  - Destinations picked in widgets: spirit trees, gliders, charters, carpets, balloons, canoes and mine carts.
  - Jewellery "Rub" dialogs.
  - POH portals and the portal nexus.
  - Minimap walking, which bypasses the menu (see `checkIfMinimapClicked` in the deob).
- **Recognised:** spell casts, tablet "Break", worn-jewellery destination options, "Zanaris", and cross-chunk object tiles.
- The design's own example, "Fairy ring to Canifis", can't occur.
- **Unmapped spells:** Paddewwa, Carrallanger (see G7), Respawn, Civitas illa Fortis, Teleport to boat.
- **Fix:** state the real coverage in the docs now; the G7 classifier fixes it properly. **Effort:** no separate work.

### G14 — Dead and duplicated guard code
- **Severity:** low. **Status:** traced.
- **Items:**
  - `menuTargetWorldPoint` is unused (`FateLockedPlugin.java:1240-1275`).
  - There are two trust gates: `StrictModeGuard.decideTravel` and `TravelGuardianCoordinator.isTrustedExact`.
  - `FateRuleEngine.staleImport` is always false.
  - `WARN_ONLY` has no consumer.
  - `GuardedAction.Kind.TELEPORT` is reachable only when travel classification misses.
  - `StrictModePause` runs on the wall clock (`Clock.systemUTC`) and isn't reset at `shutDown`.
- **Fix:** delete during the overhaul. **Effort:** S.

## Verified correct
- **Menu-manipulation surface.** A grep over `src/main` finds `consume()` only in `StrictModeClickHandler` and one text-only `setTarget` tag. Nothing removes, reorders or creates menu entries, nothing automates input, and suggested alternatives are display-only.
- **Travel-path trust gate.** It requires a non-empty `rules.account` equal (after sanitize and lowercase) to the local player's name. Missing player, unbound bundle or wrong character all fail open. Unknown and NOT_READY never become Locked, and legacy bundles yield UNKNOWN (`FateRuleEngine.trustDecision`). Checked by reading the code and by running the coordinator with a real `FateRuleEngine` in the harness.
- **Coordinator ordering.** Alternative-lookup failures are caught. The consume is the last fallible step. Coordinator exceptions route to FAIL_OPEN and don't fall through to the generic guard.
- **Thread safety.** Menu events run on the client thread. `StrictModePause`, `TravelBlockNoticeStore` and `StrictModeAuditLog` are synchronized. The overlay's mouse handler (EDT) only touches synchronized state. Panel updates go through `invokeLater`. The bundle, `rulesImportedAt` and the controller snapshot are volatile. No races found.
- **Performance.** `GuardedActionFactory.from` costs about 3.5 µs per entry in the harness (JDK 21, JIT-warm). Per-frame `MenuEntryAdded` work is tens of µs. Acceptable, though the regexes are compiled on every call.
- **Pause.** Lasts 60 seconds, resumes automatically, can be resumed early from the sidebar, and is reset when the config is toggled. The overlay button only acts while the notice is visible.
- **Audit privacy and bounds.** No names, inventory or chat. Atomic replace on write. Capped at 100 entries. Chat dedupe is 10 seconds per method+destination, bounded to 32 fingerprints.
- **Coordinates.** All keys were compared against the web's `data/chunkNames.ts`, and the spell rows against Chunk Picker region ids (region id → cx = id>>8, cy = id&255). The standard, Ancient, Lunar and Arceuus spell destinations match, except Senntisten and the Arceuus Ape Atoll variant. Balloon Taverley, Castle Wars and Crafting Guild also match. The only overlapping keys with different chunks are `ardougne` inside `west ardougne`, which longest-first matching handles (harness).
- **Mobility labels.** Every mobility label the resolver emits exists verbatim in the web's `MOBILITY_LIST`.

## Structural notes for the overhaul
**What makes this area hard to change**
- Two enforcement pipelines exist: the generic `StrictModeGuard.decide` and the travel coordinator. They differ in trust contexts, account semantics, presentation (chat only versus banner plus chat) and audit formats.
- Beyond the guards, at least three more consumers compute lock state their own way (see G8).
- Rules knowledge is split between the web app and a hand-coded Java substring table that has no source or parity test.
- The tests mock RuneLite statics with synthetic strings, so they pin the implementation, not game reality.
- Trust booleans are assembled ad hoc in the 1,822-line `FateLockedPlugin`, and the trust gate is written three times.

**Proposed target design: one decision service**
1. **`RulesSnapshot`** (pure Java, immutable, built once per accepted import). It holds the bundle plus trust facts: source, `exportedAt`, `runRevision`, bound account, relay confirmation time. It derives a `Readiness` value (ACTIVE, or INACTIVE with a reason). It contains no RuneLite types.
2. **`MenuFacts` adapter.** The only class that touches the RuneLite menu API. It captures option, target (tags stripped), type, identifier, item id, npc id/name/level, widget id, and a world-view-aware, instance-mapped tile; for WALK, the selected scene tile. Golden fixtures are `MenuFacts` JSON recorded from a live session.
3. **`IntentClassifier`** (pure). Turns `MenuFacts` into an intent: `TRAVEL(methodId, destinations[], unlockId)`, `ENTER(chunk)`, `INTERACT(kind, id/name, chunk)`, `EQUIP(itemId)` or `BANK(bankId)`. Confidence is EXACT only for single-destination, ID-matched intents. The travel table comes from the bundle (`rules.travel[]`, authored by the web app from Chunk Picker data). An optional small fallback ships with the plugin, pinned by extending the web repo's `utils/runelitePluginParity.test.ts`.
4. **`DecisionService`** (pure). Turns an intent plus the snapshot into `Decision {status, reason, ruleId, confidence}`. It is the only reader of chunk entries, target rows, mobility, equipment and banks. Everything else consumes it:
   - menu tags (memoized per frame);
   - entry warnings, flash, HUD and the panel's current chunk;
   - map overlays;
   - slayer and gear warnings;
   - Strict Mode.
5. **`StrictModePolicy`** (pure). Maps (Decision, intent kind, Readiness, pause) to ALLOW, BLOCK or WARN, with one trust gate and a category allowlist. Start with TRAVEL only; walking and the NPC, object, bank and equip categories become WARN.
6. **`EnforcementPresenter`.** For every category it shows the banner with pause, writes a chat line naming Strict Mode and how to pause, and records an async, deduped audit entry, all before consuming the click.
7. **Readiness everywhere.** The panel row (for example "Active", or "Inactive: rules 3 h old / not bound / other character"), a HUD chip, and the block banner.

**Suggested staging**
- **Stage 0 (hotfix, S):** stop WALK consumption (G1); unify the account gate (G2); correct the paused audit (G9a); show a minimal readiness state (G5); correct the Hub notes and add the source-boundary test (G3).
- **Stage 1 (M):** base freshness on `exportedAt` and revision (G4); map unlocks exactly and mark multi-destination actions Unknown (G6, part of G7).
- **Stage 2 (L):** build the decision service, the web-authored travel table and the ID-based classifier, and move tags, warnings and overlays onto them (G7, G8, G11, G13).

## Not covered
- **Live client.** Nothing was checked on a live client. The WALK param semantics rest on deobfuscated revisions of roughly 184–213 and on RuneLite core usage; the current revision is unverified.
- **In-game strings and destinations.** Menu texts, option names, diary toggles and landing tiles are unverified because the OSRS Wiki is blocked. Examples: jewellery worn options, sea boots, the tablet item name, the grouping and nexus UIs. Coordinates were checked only against the web repo's own data.
- **Sailing.** The WorldView behaviour of Sailing boats is unverified.
- **Gradle suite.** The plugin's own tests weren't run (`repo.runelite.net` is blocked); the harness runs only the RuneLite-free classes against stand-ins.
- **Other areas, noted only where they cross into this one:** relay freshness and pairing, legacy `lockStateAt` parity, and panel/overlay rendering. These belong to the relay, rules and UX reviewers.
- **Hub acceptance.** RuneLite's stance on click consumption is inferred from the rejected-features page. No reviewer precedent was verified.
