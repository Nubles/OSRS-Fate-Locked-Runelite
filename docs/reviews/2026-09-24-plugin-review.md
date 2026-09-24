# RuneLite plugin review, 2026-09-24

Plugin `main` at `bda88c8`; the Plugin Hub runs `52f45f5`. Web app `main` at `9617a16`.
The staged plan that follows from this review is [`docs/superpowers/specs/2026-09-24-plugin-overhaul-design.md`](../superpowers/specs/2026-09-24-plugin-overhaul-design.md).

## Summary

Six parallel reviews covered the whole plugin: tracker connection (S), rules parity with the web app (R), Strict Mode (G), detectors (D), sidebar and in-game display (U), and core lifecycle and architecture (A). They found 101 issues; merging the ones two reviewers found separately, and adding two from consolidation, leaves **84 distinct findings: 16 high, 32 medium, 36 low**.

What matters most:

- **The plugin is live on the Plugin Hub** at `52f45f5`, one commit behind `main`. The README says it was never submitted and the web ROADMAP names a different commit (A17). Real players run it, so config keys, local files and the bundle format are a compatibility contract, and each stage below is a Hub release.
- **Nothing owns the active rules.** Five code paths write them. Rules can vanish while the panel says Connected (S2), are lost on restart (S1), can be silently replaced by an older file (A6), and an old file counts as fresh for Strict Mode (A2).
- **Strict Mode can block the wrong clicks.** "Walk here" reads the mouse position as a tile (G1); an older guard blocks NPC, object, bank and equip clicks on any character when the profile has no bound account (G2); the Hub notes disclose only travel blocking (G3); and the sidebar says On when it cannot act (G5).
- **Two rule engines disagree** about what is locked (R2), and the plugin has no lock state at all for 699 interior regions (R1). The web app's parity test no longer tests the real plugin (R3).
- **Detected events go nowhere** (D1), and several detectors produce labels the web app cannot match (D3, D4, D6, D9).
- **One damaged local file stops the plugin from starting** (A3).

## Counts

| Theme | High | Medium | Low | Total |
|---|---:|---:|---:|---:|
| Rules vanish, go stale, or get replaced | 3 | 2 | 3 | 8 |
| Strict Mode blocks the wrong things and hides when it is off | 4 | 3 | 5 | 12 |
| Different parts of the plugin disagree about what is locked | 1 | 1 | 2 | 4 |
| The plugin works out rules the web app should hand it | 2 | 3 | 5 | 10 |
| Detected events go nowhere, and several detectors mislabel | 3 | 8 | 5 | 16 |
| Startup, threads and local files | 1 | 2 | 6 | 9 |
| Tracker connection states mislead players | 1 | 5 | 3 | 9 |
| Sidebar and in-game display | 1 | 7 | 5 | 13 |
| Release, docs and verification | 0 | 1 | 2 | 3 |
| **All** | **16** | **32** | **36** | **84** |

## What is already right

- **Land-chunk rules match the web app exactly.** 0 mismatches in 86,736 checks: 9 real bundles built by the web app and 130 random runs, across Vanilla, legacy Xtreme, Chunked and Custom modes, banks and free areas.
- **The network boundary and consent hold.** One GET to one host with no body; online sync is off until the player agrees to the IP-address warning; nothing is uploaded; tokens never reach the plugin.
- **Start and stop are clean.** Every overlay, listener, button, timer, infobox and map pin registered at start is released at stop, including after a failed start and over repeated on/off cycles.
- **The relay protocol core is sound.** Pairing codes are validated, stale callbacks are discarded, only one request is in flight, and the new relay version numbers from web PR #45 import correctly.
- **Two of three local stores recover from damage.** The event history and the Strict Mode audit log move a damaged file aside and start fresh. Only the Slayer file crashes startup (A3).
- **The existing tests pass.** Run on the RuneLite-free classes with stand-ins: 37 connection, 56 core, 19 detector and history, and 31 web parity and bundle tests. The full Gradle suite couldn't run here.

## How it was checked

- Each reviewer compiled the plugin's RuneLite-free classes with `javac --release 11` against local stand-ins for RuneLite types, whose behaviour was copied from RuneLite's current source. They ran the repository's own tests on that harness, then wrote scenario tests that reproduce each finding marked "reproduced".
- Rules parity used real bundles built by the web app's `buildBundlePayload` with the real chunk content and equipment catalogue, compared against the web app's own lock functions.
- Game message formats came from RuneLite's own test fixtures and source, because the OSRS Wiki is blocked from this environment.
- Findings marked "re-checked" were confirmed again in the code while this report was consolidated.

Not covered:

- **A live RuneLite client.** Nothing was run in game. Overlay rendering, menu text, landing tiles and game messages rest on code, RuneLite source and RuneLite's fixtures.
- **The plugin's full Gradle suite.** `repo.runelite.net` is blocked here, so only the RuneLite-free classes and their tests ran; the repository CI remains the compile check.
- **Windows file timing, antivirus, and Plugin Hub review history.** The Hub entry shows acceptance, not whether Strict Mode was discussed.

## Findings

Each finding keeps its reviewer's id; the area reports in [`2026-09-24-plugin-review/`](2026-09-24-plugin-review/) have the full evidence, locations and harness output. "Also" lists the ids another reviewer gave the same problem. Stage refers to the overhaul design.

### Rules vanish, go stale, or get replaced

Five code paths write the active rules, and nothing records which source is active or how old it is. That one gap causes most of the reliability bugs players would notice.

#### S2 · high · Stage 0: "Reload from file" or the Auto-reload toggle can erase the tracker's rules

With no bundle file in the folder, both load empty rules: overlays, warnings, menu tags and Strict Mode stop. The relay keeps answering "unchanged", so the panel still says Connected and the rules only come back when the web app publishes again. An older file replaces the rules instead.

*Fix:* A missing file does nothing but show a message; the toggle only starts or stops the watcher; any local replacement makes the next relay check fetch in full. *Status:* reproduced in a harness; re-checked. Also A1, U1. Details: [S2](2026-09-24-plugin-review/S-sync.md).

#### A2 · high · Stage 0: An old bundle file counts as fresh, so Strict Mode can block on stale rules

Freshness counts from when rules were loaded, not when they were exported. A January file blocked a teleport the tracker now allows, for 15 minutes after every start; then enforcement stopped without a word.

*Fix:* Take freshness from the bundle's export time for files and clipboard, and from the last relay confirmation for the relay, through one policy the guard, panel and HUD share. *Status:* reproduced in a harness. Also G4. Details: [A2](2026-09-24-plugin-review/A-core.md).

#### S1 · high · Stage 1: Rules from the tracker are never saved, so a restart can leave the plugin with none

The relay keeps a profile for 24 hours after the last web publish. Start RuneLite the next evening without opening the tracker, or without internet, and the plugin has no rules, shows red "Pairing request expired", and Strict Mode silently stops.

*Fix:* Save the last accepted rules and load them at startup as "saved rules from <time>"; treat a 404 as "no recent publish", not an expired pairing. *Status:* reproduced in a harness; re-checked. Details: [S1](2026-09-24-plugin-review/S-sync.md).

#### R7 · medium · Stage 0: Empty or non-bundle JSON wipes the rules and reports success

Pasting {} or null, or the watcher catching a half-written file, replaces every rule with nothing and shows "imported 0 regions".

*Fix:* Reject anything without a version and chunk data, on every import path. *Status:* reproduced in a harness. Also S9. Details: [R7](2026-09-24-plugin-review/R-rules.md).

#### A6 · medium · Stage 1: An older bundle file silently replaces newer tracker rules

With the folder watcher on (the default), restoring an old file dropped the rules from run revision 41 to 12, and the relay never restored them.

*Fix:* Accept automatic sources only for the same run with a newer revision; the relay wins while paired. *Status:* reproduced in a harness. Details: [A6](2026-09-24-plugin-review/A-core.md).

#### R14 · low · Stage 0: File imports use the system text encoding

Real bundles contain 1,592 "·" characters. On a system whose default encoding isn't UTF-8, file imports would show "Â·". Clipboard and relay imports are unaffected.

*Fix:* Read files as UTF-8. *Status:* suspected from code. Details: [R14](2026-09-24-plugin-review/R-rules.md).

#### A11 · low · Stage 0: Re-pasting the same bad bundle fails silently under a green status

A bad paste, then a good one, then the same bad one within 30 seconds leaves "imported N regions" on screen.

*Fix:* Always update the status; rate-limit only the log line. *Status:* reproduced in a harness. Details: [A11](2026-09-24-plugin-review/A-core.md).

#### A12 · low · Stage 1: A failed import only partly rolls back

Map pins, chat warnings and HUD fields produced from the rejected bundle stay behind.

*Fix:* Build everything from the candidate first, commit in one step, then announce. *Status:* traced in code. Details: [A12](2026-09-24-plugin-review/A-core.md).

### Strict Mode blocks the wrong things and hides when it is off

Strict Mode runs two pipelines with different trust rules. The older one also blocks NPC, object, bank and equip clicks, which the Plugin Hub notes never mention. The plugin is live on the Hub, so that gap is a live risk.

#### G1 · high · Stage 0: "Walk here" reads the mouse position as a map tile

The walk option carries viewport pixel coordinates, which the plugin treats as a scene tile. Clicks in the top-left 104×104 pixels of the game view are checked against an unrelated chunk and can be blocked; everywhere else walk protection never fires. The same bug tags "Walk here" as locked. RuneLite's own Ground Markers plugin uses the selected scene tile instead.

*Fix:* Never block or tag walking. The July 27 design already says walking is never blocked. *Status:* reproduced in a harness; re-checked. Details: [G1](2026-09-24-plugin-review/G-guardian.md).

#### G2 · high · Stage 0: The older guard blocks clicks on any character when the profile has no bound account

NPC, object, bank and Wear/Wield clicks are blocked on whichever character is logged in, even with nobody logged in. Unbound profiles are normal: the pairing dialog shows "No bound account". The travel guard correctly does nothing in the same case, and the plugin's own test pins the difference.

*Fix:* One trust gate for every click Strict Mode can block: bound account, matching character, fresh rules. *Status:* traced in code; re-checked. Details: [G2](2026-09-24-plugin-review/G-guardian.md).

#### G3 · high · Stage 0: The Plugin Hub notes don't disclose everything Strict Mode blocks

The notes, README, CONTRIBUTING and web guide describe only travel blocking, "exact" and "account-bound". The code also blocks NPC options (Talk-to, Trade, Attack), object options including Inspect, bank options and equipping. Blocking NPC options is the closest match to RuneLite's rejected "conditional menu entry removing". No test pins which clicks can be blocked.

*Fix:* Decide the scope (decision 1), make the docs match exactly, and add a test that pins the one place a click can be consumed. *Status:* traced in code. Details: [G3](2026-09-24-plugin-review/G-guardian.md).

#### G5 · high · Stage 0: Strict Mode shows a green "On" while it cannot act

With stale rules, an unbound profile, another character, a legacy bundle or nobody logged in, every guard correctly lets clicks through, and the sidebar still says On. A player relying on it thinks it is working.

*Fix:* Show Active, Paused, or Inactive with the reason, computed by the same gate enforcement uses. *Status:* traced in code. Also U5. Details: [G5](2026-09-24-plugin-review/G-guardian.md).

#### G6 · medium · Stage 2: Travel unlocks don't match the web app's list

Slayer ring, Drakan's medallion, Xeric's talisman, Digsite pendant and Ring of the elements are separate web unlocks, but the plugin requires "Jewelry Teleports" for all of them. The Necklace of passage "Eagles' Eyrie" option requires "Eagle Transport". Teleport tablets and spellbooks are never checked.

*Fix:* The web app names the unlock for each travel method in the bundle; no guessing from menu text. *Status:* reproduced in a harness. Details: [G6](2026-09-24-plugin-review/G-guardian.md).

#### G7 · medium · Stage 2: Several teleports are matched to the wrong place, or to one place when they have many

Rubbing a Digsite pendant (three destinations) is treated as a trip to the Digsite. Senntisten, Seers', Hosidius, Battlefield of Khazard and Fossil Island map to chunks the web app names differently; Prifddinas, Zanaris and Lithkren have no web rules. "Carrallanger" is misspelled, so it never matches. The tests use menu text the game doesn't produce.

*Fix:* Match by item, spell or object id against a travel table the web app writes into the bundle; anything with several destinations is Unknown. *Status:* reproduced in a harness. Also R8. Details: [G7](2026-09-24-plugin-review/G-guardian.md).

#### G10 · medium · Stage 2: How to get unstuck depends on what was blocked

Only travel blocks get the banner with a Pause button. Bank, equip, NPC and object blocks get one chat line that doesn't mention Strict Mode or pausing. The HUD shows no Strict Mode state and there is no hotkey.

*Fix:* One presenter for every block: banner with Pause, a chat line that names Strict Mode, an audit entry. *Status:* traced in code. Details: [G10](2026-09-24-plugin-review/G-guardian.md).

#### G12 · low · Stage 0: The older guard blocks before it explains, including harmless options

The click is consumed before the chat line and audit entry are written, outside the error handler, and every option except Examine is guarded, including Inspect.

*Fix:* Fold it into one pipeline with an allowlist of options per category, or remove it (decision 1). *Status:* traced in code. Details: [G12](2026-09-24-plugin-review/G-guardian.md).

#### G13 · low · Stage 0: Most transport can never be recognised

Fairy-ring codes, spirit trees, gliders, charters, carpets, balloons, jewellery Rub dialogs, house portals and minimap walking all pass unchecked. The docs promise more; their own example "Fairy ring to Canifis" can't occur.

*Fix:* State the real coverage now; the id-based classifier in Stage 2 extends it. *Status:* traced in code. Details: [G13](2026-09-24-plugin-review/G-guardian.md).

#### G9 · low · Stage 1: The audit log mislabels paused actions and writes on every blocked click

While paused, every allowed trip is logged as "allowed because paused". Each blocked click rewrites the file on the game thread, with no de-duplication, and entries from different characters mix.

*Fix:* Log only locked actions let through by a pause; de-duplicate; write in the background. *Status:* reproduced in a harness. Details: [G9](2026-09-24-plugin-review/G-guardian.md).

#### G11 · low · Stage 2: Tile checks ignore instances and other world views

Instances aren't mapped to their template, and an object's own tile is used as its destination, so ladders and caves in the player's chunk are never checked.

*Fix:* One tile resolver that handles world views and instances; boundary objects are Unknown unless the web app says where they lead. *Status:* traced in code. Details: [G11](2026-09-24-plugin-review/G-guardian.md).

#### G14 · low · Stage 2: Dead and duplicated guard code

Two trust gates, an unused warning-only outcome, a stale-import flag that is always false, and a pause timer on the wall clock.

*Fix:* Delete during the rebuild. *Status:* traced in code. Details: [G14](2026-09-24-plugin-review/G-guardian.md).

### Different parts of the plugin disagree about what is locked

Overlays, HUD, chat, sound and flash read one rule engine; the sidebar, menu tags, bank warning and Strict Mode read another. They agree on land chunks today and disagree everywhere else.

#### R2 · high · Stage 2: Two rule engines answer "is this locked?" differently

On another character the sidebar says Unknown and menu tags stop, but the HUD says LOCKED and the locked sound and red flash still fire. A Not-ready chunk reads Unlocked on the HUD. 314 ocean chunks read Locked in the sidebar but "unauthored" in the tint. A goblin is tagged "(LOCKED)" and Strict Mode still lets you attack it.

*Fix:* One decision service, used by the sidebar, HUD, overlays, alerts, tags, warnings and Strict Mode. *Status:* reproduced in a harness. Also U4, G8. Details: [R2](2026-09-24-plugin-review/R-rules.md).

#### U13 · medium · Stage 3: The sidebar drops the reason something is Not ready or Locked

"Cook's Assistant" shows in amber with no word of why; the bundle says "Needs Cooking 10". Only Skilling and Travel keep their reasons, and Not ready and Locked share the same ✕ mark.

*Fix:* Always carry the reason and show it; one mark per status. *Status:* traced in code. Details: [U13](2026-09-24-plugin-review/U-ux.md).

#### R11 · low · Stage 1: Account binding reads two different fields

Rule checks use the manifest's account; the HUD and the mismatch chat line use an older field with different trimming. A mismatch shows only as one chat line.

*Fix:* One account source, the web app's normalisation, and "Profile is for X; you're logged in as Y" in the status. *Status:* traced in code. Also S10. Details: [R11](2026-09-24-plugin-review/R-rules.md).

#### R10 · low · Stage 2: A stray field could switch a non-Chunked profile to Chunked rules

If a non-Chunked bundle ever carried the root unlockedChunks field, free Varrock would read Locked in the overlays. The web app never sends it today.

*Fix:* Decide Chunked mode only from the manifest's game mode. *Status:* reproduced in a harness. Details: [R10](2026-09-24-plugin-review/R-rules.md).

### The plugin works out rules the web app should hand it

Land chunks match the web app exactly. Interiors, ocean, banks, travel and progress are either missing or re-derived in Java, and the only drift test no longer tests the real plugin.

#### R3 · high · Stage 1: The web app's plugin-parity test no longer tests the real plugin

It simulates a plugin that reads the old root fields, while the Java reads the manifest, and it checks named areas only. On one of the web app's own test bundles the simulation says Prifddinas and Lletya are unlocked; the real Java says Locked. No Java test uses a bundle the web app built.

*Fix:* Web-built golden bundles with expected answers, checked by JUnit against the real classes; delete the simulation. *Status:* reproduced in a harness; re-checked. Plugin and web app. Details: [R3](2026-09-24-plugin-review/R-rules.md).

#### R1 · high · Stage 2: Interiors, dungeons and instances have no lock state in game

The web app gates 699 interior regions (Keldagrim, Prifddinas, Zanaris, God Wars, every dungeon). In game, walking into locked Keldagrim shows "Unknown chunk" and "unauthored", with no warning, no menu tags, no bank warning, and Strict Mode lets everything through. Instances aren't mapped to their template area either.

*Fix:* The web app exports an entry for every interior chunk; the plugin maps instances to their template. *Status:* reproduced in a harness; re-checked. Also U14. Details: [R1](2026-09-24-plugin-review/R-rules.md).

#### R4 · medium · Stage 2: Ocean chunks and the Chunked Sailing frontier are missing

The web app locks 548 ocean chunks until Sailing is unlocked; the plugin gives no cue. The Chunked "rollable next" tint is wrong along the coast, and Mogre tasks warn "locked" when the web app says reachable.

*Fix:* The web app exports ocean entries and the frontier it already computes. *Status:* reproduced in a harness. Plugin and web app. Details: [R4](2026-09-24-plugin-review/R-rules.md).

#### R5 · medium · Stage 2: Bank warnings miss interior banks, and the nearest-bank hint misses 19 banks

Opening the bank in Keldagrim, Prifddinas, Zanaris, Guardians of the Rift or the Mage Arena never warns, even when the web app says Locked. The HUD's nearest usable bank never points to 19 of the web app's 127 bank chunks.

*Fix:* The web app exports its bank list with each bank's real chunks. *Status:* reproduced in a harness. Plugin and web app. Details: [R5](2026-09-24-plugin-review/R-rules.md).

#### R6 · medium · Stage 2: New bundles still depend on the old root fields

If the web app dropped the root chunks field, every current plugin would reject tracker bundles and keep stale rules. The free-area baseline comes from a global in the web app rather than from the run.

*Fix:* Put the free-area baseline in the manifest and write down which root fields are frozen. *Status:* reproduced in a harness. Plugin and web app. Details: [R6](2026-09-24-plugin-review/R-rules.md).

#### R9 · low · Stage 2: Unlock progress differs from the web app

The same run shows 24/187 areas on the web and 21/177 in the HUD and infobox.

*Fix:* The web app exports the numbers; the plugin shows them. *Status:* reproduced in a harness. Plugin and web app. Details: [R9](2026-09-24-plugin-review/R-rules.md).

#### R13 · low · Stage 2: Bundles are ten times bigger than the docs said, and parse on the game thread

Real bundles are about 1.3 MiB (about 205 KB compressed); the docs said 120 KiB. A cold parse takes about 180 ms on the game thread. The docs are corrected in plugin PR #16 and web PR #46.

*Fix:* Parse in the background (Stage 1); a compact encoding behind a capability flag (Stage 2). *Status:* reproduced in a harness. Plugin and web app. Details: [R13](2026-09-24-plugin-review/R-rules.md).

#### R15 · low · Stage 2: No versioning plan beyond "reject version 5"

Any bump of the bundle version would break every installed plugin, and the rules and content versions are never checked.

*Fix:* Keep version 4; add optional capabilities; a web test fails on a version bump without a plugin release. *Status:* traced in code. Plugin and web app. Details: [R15](2026-09-24-plugin-review/R-rules.md).

#### R16 · low · Stage 2: Slayer checks are coarser than the web app's

All masters' locations are merged, so Krystilia's Wilderness tasks and Konar's locations never warn.

*Fix:* The web app exports task entries per master. *Status:* traced in code. Plugin and web app. Details: [R16](2026-09-24-plugin-review/R-rules.md).

#### R12 · low · Stage 3: Fields read but never sent, and sent but never read

The Run section's Profile row is always "—" because the web app never sends a profile name. "imported 13 regions" counts continents, not areas.

*Fix:* Send the profile name or drop the row; fix the status text. *Status:* traced in code. Plugin and web app. Details: [R12](2026-09-24-plugin-review/R-rules.md).

### Detected events go nowhere, and several detectors mislabel

Detections land in one local file and show only as counters. Nothing reviews or sends them, and the web inbox the button opens is fed by nothing. Several detectors produce labels the web app cannot match.

#### D1 · high · Stage 0: The Roll inbox is a dead end on both sides

Detections go to one local file and show as "Local events" and "Needs review" counters that only grow. Nothing can be listed, reviewed, cleared or sent. "Open web Roll Inbox" opens an inbox nothing feeds, which says "Listening" and "RuneLite events will queue here". The Warnings count goes stale.

*Fix:* Decision 2: a clipboard hand-off to the web inbox, or retire both. Fix the copy now either way. *Status:* traced in code; re-checked. Also U9, D13. Plugin and web app. Details: [D1](2026-09-24-plugin-review/D-detectors.md).

#### D3 · high · Stage 1: Combat achievement labels keep the game's markup

Real messages carry an @ach_comp@ marker and "(N points)", which end up in the label, marked exact. None of five real messages from RuneLite's own tests resolve on the web app.

*Fix:* Parse the way RuneLite does; map task names to web ids later. *Status:* reproduced in a harness. Details: [D3](2026-09-24-plugin-review/D-detectors.md).

#### D4 · high · Stage 1: The boss detector nudges non-bosses and misses a third of the bosses

"Combat level 250 or more" posts "Boss kill — may be worth a roll" for Mithril dragons, superiors and gorillas, and can never reach 23 of the web app's 69 bosses, including Brutus, Obor, Giant Mole, Barrows, the Gauntlet and the Inferno.

*Fix:* Retire the combat-level rule now; later, one boss detector by NPC id and kill-count messages. *Status:* reproduced in a harness. Details: [D4](2026-09-24-plugin-review/D-detectors.md).

#### D5 · medium · Stage 1: Detections and local files are shared by every account and client

Your main account's kills and levels are recorded under your Fate run, on any world type, Leagues included, even with no rules loaded. With two RuneLite clients open, each one's history write erases the other's events.

*Fix:* One detection gate (rules loaded, bound account, normal world); per-account files that merge safely. *Status:* reproduced in a harness. Also A7, D12. Details: [D5](2026-09-24-plugin-review/D-detectors.md).

#### D6 · medium · Stage 1: Quest names ending in "Quest" are cut short

"Doric's Quest" is stored as "Doric's"; four scroll title shapes give no name; Recipe for Disaster never matches.

*Fix:* Keep the suffix and add the missing title shapes now; detect completions from quest state later. *Status:* reproduced in a harness. Details: [D6](2026-09-24-plugin-review/D-detectors.md).

#### D7 · medium · Stage 1: The clue detector never sees a clue completion

It scans loot item names for "casket". Real clue rewards arrive as a "Clue Scroll (Hard)" loot event, and unrelated caskets create junk events.

*Fix:* Retire the item scan; detect "Clue Scroll (<tier>)" completions later. *Status:* traced in code. Details: [D7](2026-09-24-plugin-review/D-detectors.md).

#### D9 · medium · Stage 1: 12 of 48 diary tiers use names the web app doesn't have

Kourend & Kebos, Lumbridge & Draynor and Western Provinces tiers can't be matched on the web app.

*Fix:* Use the web app's tier ids; persist the baseline per account. *Status:* reproduced in a harness. Details: [D9](2026-09-24-plugin-review/D-detectors.md).

#### D11 · medium · Stage 1: The pet detector has the wrong ids

Two of its three NPC ids belong to other followers; the "sneaking into your backpack" message is missed and the duplicate-pet message is counted.

*Fix:* Turn the id map off now; use a web-authored table later. *Status:* reproduced in a harness. Details: [D11](2026-09-24-plugin-review/D-detectors.md).

#### D8 · medium · Stage 4: The Slayer detector rarely knows the task

It learns the task only from a gem check, can attach an old task, and takes any message containing "to kill". RuneLite's own Slayer plugin reads game variables instead.

*Fix:* Rewrite on game variables, per account. *Status:* reproduced in a harness. Details: [D8](2026-09-24-plugin-review/D-detectors.md).

#### D10 · medium · Stage 4: Collection log events can never be reviewed on the web app

Every event is marked uncertain and the web app offers no candidates. Players who use the popup setting send no chat line at all.

*Fix:* Resolve item names through a bundle table; handle the popup; offer candidates on the web app. *Status:* traced in code. Plugin and web app. Details: [D10](2026-09-24-plugin-review/D-detectors.md).

#### D14 · medium · Stage 4: Neither repository tests the other side's real output

The web app added handling for Brutus kills the plugin can't emit, and nothing failed. The same blind spot let the combat achievement, quest and diary drift ship.

*Fix:* One shared set of real-message fixtures drives a parity test in the web app. *Status:* traced in code. Plugin and web app. Details: [D14](2026-09-24-plugin-review/D-detectors.md).

#### D15 · low · Stage 1: The Pest Control detector can't fire

Its 5-second window opens when the game starts, not when it ends.

*Fix:* Retire it. *Status:* traced in code. Details: [D15](2026-09-24-plugin-review/D-detectors.md).

#### D19 · low · Stage 1: Dead detector code and unused resource files

Three unused JSON tables disagree with the Java lists, and an unused threshold has a misleading comment.

*Fix:* Delete them. *Status:* traced in code. Details: [D19](2026-09-24-plugin-review/D-detectors.md).

#### D16 · low · Stage 4: Event ids and saved events don't support de-duplication or a hand-off

Each detection gets a random id, null labels are dropped when saved (so the web app would reject them), and the history keeps events past the web app's 30-day limit.

*Fix:* Content-based ids; keep nulls on export; validate on load. *Status:* reproduced in a harness. Details: [D16](2026-09-24-plugin-review/D-detectors.md).

#### D17 · low · Stage 4: Boss, raid and clue detection quietly need the Loot Tracker plugin

If a player turns off RuneLite's Loot Tracker, these detectors stop without any hint.

*Fix:* Use RuneLite's core loot events, or show a hint. *Status:* traced in code. Also A13. Details: [D17](2026-09-24-plugin-review/D-detectors.md).

#### D18 · low · Stage 4: Skill detector edge cases

A jump from 70 to 72 gives one event, and the first level-up after enabling the plugin mid-session is lost.

*Fix:* Take the baseline from the real skill levels. *Status:* reproduced in a harness. Details: [D18](2026-09-24-plugin-review/D-detectors.md).

### Startup, threads and local files

Every start/stop registration is released correctly. The problems are one crash-on-start file, per-login logic that runs after every loading screen, and state touched from three threads.

#### A3 · high · Stage 0: A damaged Slayer file stops the whole plugin from starting

A truncated or changed slayer-assignment.json throws an error startup doesn't catch. RuneLite stops the plugin: no sidebar, rules or Strict Mode, every start, with no message, until the player deletes the file by hand.

*Fix:* Local files never stop startup: a bad file is moved aside and the feature starts empty. *Status:* reproduced in a harness; re-checked. Also D2. Details: [A3](2026-09-24-plugin-review/A-core.md).

#### A4 · medium · Stage 0: "Once per login" code runs after every loading screen

RuneLite reports a login after each loading screen. The wrong-account warning and its sound repeated six times over one login and five teleports; the same chunk is re-announced; a level-up right after a teleport is dropped.

*Fix:* Reset only on a real login or account change, using the account hash. *Status:* reproduced in a harness. Details: [A4](2026-09-24-plugin-review/A-core.md).

#### A5 · medium · Stage 1: Plugin state is changed from three threads

Pasting a bundle calls game-client methods from the Swing thread, and several shared collections have no locking.

*Fix:* Keep plugin state on the game thread; Swing only sees finished view models. *Status:* reproduced in a harness. Details: [A5](2026-09-24-plugin-review/A-core.md).

#### A8 · low · Stage 1: Work queued before shutdown still runs after it

Disabling the plugin right after a file change brings the rules and a map pin back while it is off; a queued Connect revives the connection.

*Fix:* Each start gets a session token that queued work checks. *Status:* reproduced in a harness. Details: [A8](2026-09-24-plugin-review/A-core.md).

#### A10 · low · Stage 1: Parsing and file writes run on the game thread

A cold parse of a real bundle takes about 180 ms, and each detection rewrites the whole history file (about 105 KB when full).

*Fix:* One background worker for parsing and writing; results are committed on the game thread. *Status:* reproduced in a harness. Details: [A10](2026-09-24-plugin-review/A-core.md).

#### A14 · low · Stage 1: Deprecated RuneLite APIs in core paths

Varbits and InventoryID are deprecated in RuneLite; if the Hub ever disallows them, the build fails.

*Fix:* Move to RuneLite's gameval ids. *Status:* traced in code. Details: [A14](2026-09-24-plugin-review/A-core.md).

#### A16 · low · Stage 1: Dead and misleading code in the plugin class

An unused walk-tile method, write-only fields, an unused Disconnect helper and stale comments.

*Fix:* Delete, or wire the Disconnect helper to a button. *Status:* traced in code. Details: [A16](2026-09-24-plugin-review/A-core.md).

#### A9 · low · Stage 3: Work repeated every frame and every menu entry

Menu tagging allocates about 7.8 KB per entry (about 2.3 MB of garbage a second while hovering), and the world map recomputes about 624 chunks per frame.

*Fix:* Cache per rules snapshot; publish panel state only when it changes. *Status:* reproduced in a harness. Also U19. Details: [A9](2026-09-24-plugin-review/A-core.md).

#### A15 · low · Stage 3: The three infoboxes share one name

Keys, Fate Points and progress detach and move as one unit.

*Fix:* Give each its own name. *Status:* traced in code. Details: [A15](2026-09-24-plugin-review/A-core.md).

### Tracker connection states mislead players

The relay protocol core is sound. The states around it are not: a new pairing shows red "expired", failures are silent, and there is no way to check now.

#### S3 · high · Stage 0: Connecting shows red "Pairing request expired" before the player has confirmed

The first check runs within 4 seconds, before the browser page is even read, and turns the panel red. After confirming, Connected can take a minute. The guide's advice (press Connect again) swaps the code the open page is about to publish to.

*Fix:* Show "Waiting for confirmation in your browser" until the first import, check every 5 seconds, and time out after 10 minutes. *Status:* reproduced in a harness. Also U2. Details: [S3](2026-09-24-plugin-review/S-sync.md).

#### S4 · medium · Stage 1: The version check is fragile

A reply carrying the same version counts as a failure: back-off climbs to 15 minutes while the panel stays Connected. That is what would happen if anything ever quotes the relay's version tag. An older version is dropped without a word.

*Fix:* Treat the same version as unchanged; forget the version on 404; the relay compares tags leniently. *Status:* reproduced in a harness. Plugin and web app. Details: [S4](2026-09-24-plugin-review/S-sync.md).

#### S5 · medium · Stage 1: Unusable replies change nothing on screen

A captive-portal page or a reply without a payload leaves "Waiting for tracker" or an old "Connected" while checks slow to every 15 minutes.

*Fix:* Every outcome gets a visible state with its reason. *Status:* reproduced in a harness. Details: [S5](2026-09-24-plugin-review/S-sync.md).

#### S6 · medium · Stage 1: No "Check now", slow recovery, and polling at the login screen

After a 10-minute relay blip the plugin can wait 15 more minutes to notice, which is also when Strict Mode treats rules as stale. A roll takes about 35 seconds to arrive and there is no way to pull it. Polling continues every minute at the login screen.

*Fix:* A Check now button; back-off capped at 5 minutes; slower polling when logged out. *Status:* reproduced in a harness. Details: [S6](2026-09-24-plugin-review/S-sync.md).

#### S7 · medium · Stage 1: Pressing Connect on a working pairing throws it away

The old code is replaced at once, before the browser confirms; there is no Disconnect, and the button never changes its label.

*Fix:* Label the button by state, confirm before re-pairing, and keep the old code until the new one works. *Status:* traced in code. Details: [S7](2026-09-24-plugin-review/S-sync.md).

#### S8 · medium · Stage 1: Connection states are lumped together

"Pairing request expired" covers three different situations. A corrupt payload and a newer bundle format give the same message. Connected stays green on the wrong character or when file rules have taken over.

*Fix:* States with reasons and one clear action each (the status card in Stage 3 shows them). *Status:* reproduced in a harness. Also U6. Details: [S8](2026-09-24-plugin-review/S-sync.md).

#### S11 · low · Stage 1: Disconnecting in the web app doesn't revoke the profile

The relay keeps serving the last profile, including the character name, for up to 24 hours. The pairing dialog shows the full read code, which matters for streamers.

*Fix:* A token-protected "gone" marker on disconnect; show only the code's last 4 characters. *Status:* traced in code. Plugin and web app. Details: [S11](2026-09-24-plugin-review/S-sync.md).

#### S12 · low · Stage 1: Requests have no overall timeout or size cap

A slowly dripping reply holds the only request slot indefinitely, calls aren't cancelled on stop, and a late update can briefly show Connected after sync is turned off.

*Fix:* Call timeout, body cap, cancel on stop, one ordered path for panel updates. *Status:* reproduced in a harness. Details: [S12](2026-09-24-plugin-review/S-sync.md).

#### S13 · low · Stage 1: Dead or noisy connection code

An unused state, an unreachable error path, a status re-sent every 4 seconds while sync is off, and rejected payloads downloaded again on every retry.

*Fix:* Remove them; publish only on change. *Status:* reproduced in a harness. Details: [S13](2026-09-24-plugin-review/S-sync.md).

### Sidebar and in-game display

The sidebar is mostly settings that repeat RuneLite's config panel, while the status a player needs is truncated or missing. The in-game chunk tint often fails to draw.

#### U3 · high · Stage 3: The in-game chunk tint and borders often don't draw

They are one polygon built from the chunk's four corners, so they vanish whenever a corner is outside the loaded scene or behind the camera: right after entering a chunk and at normal camera angles. When they do draw, the fill covers NPCs and the player and sits half a tile inside the real edge.

*Fix:* Draw each chunk edge tile by tile and skip tiles that can't be projected; no full-chunk fill. *Status:* suspected from code; re-checked. Details: [U3](2026-09-24-plugin-review/U-ux.md).

#### U7 · medium · Stage 0: Noise when there are no rules and underground

A fresh install posts "Chunk (x, y) — unauthored" at every chunk crossing and tints the chunk grey; so do dungeons, basements and instances.

*Fix:* Stay quiet until rules are loaded; never announce uncharted chunks. *Status:* traced in code. Details: [U7](2026-09-24-plugin-review/U-ux.md).

#### U10 · medium · Stage 0: Locked-area alerts depend on the chat toggle and repeat at every chunk

Turning off "Chat on chunk entry" also silences the locked warning. With it on, the warning sound plays at every chunk boundary inside a locked area.

*Fix:* Sound and notification fire once when entering a locked area, whatever the chat setting. *Status:* traced in code. Details: [U10](2026-09-24-plugin-review/U-ux.md).

#### U8 · medium · Stage 3: The main status line is cut off

The web guide's own screenshots show "Connected · 13:…" and "Waiting for trac…". Times are UTC, and "Last sync" stays green however old it is.

*Fix:* A full-width status line with local, relative times that turn amber and red with age. *Status:* traced in code. Details: [U8](2026-09-24-plugin-review/U-ux.md).

#### U11 · medium · Stage 3: Settings are duplicated and sprawl

27 of the sidebar's roughly 40 controls repeat RuneLite's config panel, and 31 settings overlap in five groups.

*Fix:* Decision 4: settings live in RuneLite's config panel, merged to about 15, with a one-time migration. *Status:* traced in code. Details: [U11](2026-09-24-plugin-review/U-ux.md).

#### U12 · medium · Stage 3: Wording drifts from the web app and within the plugin

Strict Mode has four names (Guardian, Fate Guardian, Pause Guardian, Strict Mode). "Fate" vs "Fate Points", raw "LUCK/GREED", a broken "Prevented: X — is T3…" message, and instructions that name web buttons that no longer exist.

*Fix:* One wording module and glossary, shared with the web guide and checked by a test on each side. *Status:* traced in code. Plugin and web app. Details: [U12](2026-09-24-plugin-review/U-ux.md).

#### U15 · medium · Stage 3: Lock colours are hard to tell apart for colour-blind players

The unlocked and locked tints differ by about 61 for normal vision and only 11 to 14 under simulated protanopia or deuteranopia. Red sidebar text is 3.9:1 against its background, below the 4.5:1 guideline.

*Fix:* One palette with a colour-blind preset and a non-colour cue for locked edges. *Status:* computed. Details: [U15](2026-09-24-plugin-review/U-ux.md).

#### U16 · medium · Stage 3: Long lists push the Strict Mode controls down the page

A busy chunk lists 20 to 40 rows above the Strict Mode section, and every tracker import collapses the section under the player's cursor.

*Fix:* Cap lists at 5 rows per category with "+N more"; never auto-collapse. *Status:* traced in code. Details: [U16](2026-09-24-plugin-review/U-ux.md).

#### U17 · low · Stage 3: Visual conventions differ from RuneLite's

A blue-grey palette beside RuneLite's neutral greys, two collapsible-section styles, and colour buttons whose white label is 2.1:1 on amber.

*Fix:* RuneLite's colour scheme and fonts, one section component, swatch previews. *Status:* traced in code. Details: [U17](2026-09-24-plugin-review/U-ux.md).

#### U18 · low · Stage 3: The world map tint covers the map's own controls

It paints over the overview and surface selector and tints every chunk, unlocked ones included.

*Fix:* Clip like RuneLite's own map overlay; tint only locked and frontier chunks. *Status:* traced in code. Details: [U18](2026-09-24-plugin-review/U-ux.md).

#### U20 · low · Stage 3: The locked flash pulses about 2.5 times a second

That is close to the three-flashes-per-second guideline, with no reduced-motion option.

*Fix:* A single fade. *Status:* computed. Details: [U20](2026-09-24-plugin-review/U-ux.md).

#### U21 · low · Stage 3: "Synced now" freezes, and valid imports say "Offline snapshot"

The label is computed when you enter a chunk and never refreshed.

*Fix:* Show freshness only in the status card, refreshed every minute. *Status:* traced in code. Details: [U21](2026-09-24-plugin-review/U-ux.md).

#### U22 · low · Stage 3: Small hygiene items

The full Run ID is shown although the guide asks players to keep it out of screenshots, and the paste box keeps the whole bundle text after an import.

*Fix:* Shorten the Run ID; clear the paste box. *Status:* traced in code. Details: [U22](2026-09-24-plugin-review/U-ux.md).

### Release, docs and verification

The docs disagree about whether the plugin is on the Hub, and nothing in the repository checks the plugin in a real client.

#### C2 · medium · Stage 0: Nothing checks the plugin in a real client

The manual matrix is mostly "Blocked", there is no RuneLite dev launcher, and many tests pin game text the game doesn't produce. This review could not run a client either.

*Fix:* A dev-launcher test class, and a short in-game checklist the owner runs before each Hub release; test fixtures from real captures. *Status:* traced in code. Found while consolidating this report.

#### A17 · low · Stage 0: The docs disagree about the Plugin Hub; the Hub runs 52f45f5

The Plugin Hub entry pins 52f45f5 (16 September, "restore explicit consent"), one commit behind main. The README says the plugin was never submitted, the web ROADMAP names 5cc1ffc, and the Hub entry still points at the old repository name RS3-Fate-Locked-Runelite (it works through GitHub's rename redirect).

*Fix:* Correct the README, review notes and ROADMAP; update the Hub entry's repository URL with the next pin. *Status:* traced in code; re-checked. Plugin and web app. Details: [A17](2026-09-24-plugin-review/A-core.md).

#### C1 · low · Stage 1: The Hub builds the plugin differently from this repository's CI

The Hub replaces build.gradle with its standard build, so the repository's jar check and tests never run in the Hub pipeline.

*Fix:* A CI job that also builds with the Hub's standard build file. *Status:* traced in code. Found while consolidating this report.

## Area reports

- [S-sync.md](2026-09-24-plugin-review/S-sync.md): Tracker connection, relay sync and imports
- [R-rules.md](2026-09-24-plugin-review/R-rules.md): Rules, bundle parsing and parity
- [G-guardian.md](2026-09-24-plugin-review/G-guardian.md): Strict Mode and travel blocking
- [D-detectors.md](2026-09-24-plugin-review/D-detectors.md): Detectors, events and the Roll inbox
- [U-ux.md](2026-09-24-plugin-review/U-ux.md): Sidebar and in-game display
- [A-core.md](2026-09-24-plugin-review/A-core.md): Core lifecycle and architecture

The area reports were written against plugin `bda88c8` and web `2f1697c` (the same tree as web `main` `9617a16`). Statements corrected while consolidating are marked **[Corrected]** in place.

