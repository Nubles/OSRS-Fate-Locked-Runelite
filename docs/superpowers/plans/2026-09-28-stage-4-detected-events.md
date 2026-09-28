# Stage 4: Detected Events Implementation Plan

> Steps use checkbox (`- [ ]`) syntax for tracking. Each task is one commit
> unless it says otherwise.

**Goal:** Stage 4 of the [overhaul design](../specs/2026-09-24-plugin-overhaul-design.md), as the owner chose on
24 September 2026 (decision 2): a hand-off the player starts.
- RuneLite keeps what it notices for each character: levels, quests, diaries, combat tasks, collection log slots,
  clues, bosses and raids, Slayer tasks and pets.
- **Copy for tracker** in the Roll inbox card puts them on the clipboard.
- **Paste from RuneLite** in the web Roll Inbox brings them in. The web's classifier and Roll path, which already work
  end to end, take over from there.
- The detectors are rebuilt on the web's own ids, and one set of real game messages tests both repositories.

**What players notice:**
- The Roll inbox card lists what RuneLite noticed, and one click copies it for the tracker.
- The web Roll Inbox pastes it in. Each row says it's ready to roll, needs checking (with choices), or why not.
- Every row of a paste can be rolled. Today only the first could be.
- Kills, raids and clues are noticed without RuneLite's Loot Tracker plugin, for every boss on the web's list.
- Slayer tasks are noticed without checking the gem, with the master who gave them.
- A jump of several levels gives one roll per level, as logging each level by hand does.
- Nothing is uploaded. The copy is local, and only happens when the player clicks.

**Tech stack:** Java 11, RuneLite 1.12, Gradle 8, JUnit 4, Mockito 4 for the plugin; React/TypeScript/Vitest for the web
app.

**Findings:** D8, D10, D14, D16, D17 and D18, and the rest of D1, D3, D4, D6, D7, D9 and D11, whose quick fixes shipped
in Stages 0 and 1. Evidence is in [the review](../../reviews/2026-09-24-plugin-review/D-detectors.md). Two briefs
re-read both repositories on 28 September: plugin `9b6b5d3` and web `1e860dc`.

## Where things stand

**Plugin**
- `DetectionGate` records only for a run with a Run ID, a linked character who is logged in, and a standard world.
- `FateEventHistory` keeps 250 events per character, safely across two clients (`LocalFileMerge`). Its gaps:
  - Ids are random, so seeing one thing twice records it twice.
  - There is no New or Copied status, and nothing older than 30 days is cut.
  - Nulls are dropped when saving, and the web rejects an event without its label.
  - The card counts events from every run of the character.
- The Roll inbox card shows only counts and an **Open web Roll Inbox** button. No clipboard write exists anywhere.
- Diary memory and the Slayer "completed" flag are saved before the gate is checked. A completion seen while the gate
  is shut is used up and never recorded.
- Stage 1 changed the output of the combat task, quest, pet and diary detectors without raising their versions.

| Detector | Today | Stage 4 |
|---|---|---|
| Skill | Right signal; baseline lost when the plugin starts mid-session (D18); a jump gives one event | Seed from real levels; one event per level |
| Quest | Scroll text, fixed for the known shapes (D6) | Quest states from the game, mapped to web ids |
| Combat task | Parser fixed; no popup path (D3) | Add the popup path |
| Collection log | Colour tags kept, always uncertain, no popup path (D10) | Strip tags, exact name, popup path |
| Clue | Needs Loot Tracker (D17); its id isn't in the web's policies | The game's completion line, with its count |
| Boss | Nine names, any loot, needs Loot Tracker (D4) | The game's kill-count line, through a web table |
| Raid | Needs Loot Tracker (D17) | The kill-count line, as a raid |
| Slayer | Chat only; misses tasks without a gem check (D8) | The game's Slayer variables, as RuneLite's own plugin does |
| Diary | Fixed ids (D9); tables inside the plugin class | Tables in the detector; memory behind the gate |
| Pet | Unnamed, by design (D11) | Unchanged |

**Web**
- Everything after the input works: parse, store, classify, Review, Roll, history with the event's id, and duplicates.
  Nothing calls the store's `ingest`, so nothing ever arrives.
- **Blocker 1:** an event must carry the run's current revision, and every roll raises it. After the first Roll, every
  other row of a paste reads "The run changed after this event was detected" and can only be dismissed.
- **Blocker 2:** a run that isn't linked to a character blocks every event, saying the account doesn't match. The only
  way to link one is the Wise Old Man fetch. The plugin's gate records nothing for an unlinked run either.
- Uncertain events of five types get no choices, so they can only be dismissed.
- A batch keeps 100 events; the plugin keeps 250.
- The clue key is `Casket (hard)`, which no game signal produces. The boss list has no NPC ids or kill-count names.

## Global constraints

- **Branches.**
  - Plugin: `claude/stage-4-detected-events` from `main` `9b6b5d3`.
  - Web: two branches in the worktree `.worktrees/stage-4` of the local web clone, from web `main` `1e860dc`. Never
    touch the web clone's own working tree.
- **Order of release.**
  1. **Web pull request 1**, which players can't see: the contract and its real messages, the new detector policies,
     the detection tables in the bundle, the two blockers, and the card's words in the wording contract. It merges
     first; older plugins ignore the new bundle fields.
  2. **The plugin** re-pins at that merge. The owner tries it in RuneLite before anything is pushed. The owner merges it,
     and the Plugin Hub pull request follows.
  3. **Web pull request 2**, which players see: Paste from RuneLite, linking a run from a paste, the Roll Inbox copy,
     the guide and What's New. It merges after the Hub serves the plugin.
- **Network boundary.** The plugin's only request stays the consent-gated relay `GET`. The copy is a local clipboard
  write, made only when the player clicks. `PluginHubNetworkBoundaryTest` and `verifyPluginHubJar` stay green, and a
  new test pins the single clipboard write to the card's action.
- **Compatibility.**
  - The bundle stays at version 4. The detection tables are an additive `rules.detection` block, and
    `detectorContractVersion` becomes 2.
  - The event envelope stays at protocol 1, so the web's parser reads old and new events alike.
  - History files keep loading. Events recorded before Stage 4 stay in the file until they age out, but are never
    offered (decision 7).
  - A bundle without the new block turns off only the detectors that need its tables (bosses, raids, quests) until the
    companion next syncs.
- **Hub review.** The Hub notes, README and CONTRIBUTING change from "never transferred" to "copied to your clipboard
  only when you click Copy for tracker; RuneLite never uploads it". The inbound-only design already allows a local
  hand-off the player starts.
- **Threads.** Detectors are pure. RuneLite events become plain signals on the client thread. The store writes on
  `SerialFileWriter`. The card and the clipboard change only on the Swing thread.
- **Verification.**
  - Plugin: `gradle clean check --no-daemon` before every push, and a mutation run for each detector.
  - Web: all five gates, against this PC's known Windows failures.
  - The owner tries the plugin in RuneLite, as in Stage 3, before any push.
- **Publishing.** The owner merges every pull request. Claude asks before pushing a branch, opening a pull request, or
  opening the Plugin Hub pull request.
- **Commits.** Small: one finding or one mechanical step each. The body says why and which test pins it.

## Decisions taken in this plan

Decided here so work can go ahead; the pull requests list every one that changes what players see, and any can be
reversed.

1. **The clipboard is the hand-off** (the owner's decision of 24 September). Nothing is uploaded; the relay is
   unchanged.
2. **The copy is plain JSON the web already reads:** `{"format":"fate-locked-runelite-events","events":[…]}` at
   protocol 1. Players can see what they paste. A paste takes up to 250 events; the relay's limit of 100 is unchanged.
3. **Events carry the web's own ids:**
   - skill names;
   - quest ids, from the game's quest states, not the scroll;
   - diary tier ids;
   - combat task names, which the web accepts because all 655 are unique;
   - collection log item names, without colour tags;
   - clue tiers;
   - boss keys, from kill counts through a table the web writes into the bundle;
   - Slayer tasks, with their master.
4. **One event per level gained, oldest first.** The web's own level-up adds one level at a time, so a jump pasted in
   rolls as often as logging each level by hand.
5. **An event's id comes from what it is:** the character, the run, the type, the key and a count. The count is the
   level, the kill count, the clue count, or the Slayer streak. Seeing the same thing twice records it once, and
   pasting twice is harmless.
6. **RuneLite keeps each character's events for the current run,** marked New, Copied or Dismissed, and forgets them
   after 30 days, as the web does.
7. **Events recorded before this release are never offered.** They have no status, some carry old labels, and a boss
   kill already rolled by hand would roll again. The card starts empty.
8. **A run that isn't linked to a character:**
   - RuneLite records events for whoever is logged in.
   - The first paste asks "Link this run to <name>?", the same one-time link the Wise Old Man fetch makes.
   - A linked run keeps today's rule: another character's events aren't recorded.
9. **Every row of a paste can roll** (blocker 1). An event from an earlier revision of the same run and character is
   checked against the run as it is now. A reviewed row keeps its review.
10. **Kills come from the game's kill-count line** (for example "Your Vorkath kill count is: 12."), so the Loot Tracker
    plugin isn't needed. Raids and clues use their completion lines. Loot events stay as a fallback only where the
    game prints no count.
11. **Slayer comes from the game's Slayer variables** (task, count, master and streak), as RuneLite's own Slayer
    plugin does. A task completes when its count reaches 0 and the streak rises.
12. **A pet pays once.** Its collection log slot is recorded without a second roll when its pet drop is in the run.
13. **The words come from the web first:** Copy for tracker, Paste from RuneLite, New, Copied and Dismiss join the
    wording contract. The plugin then re-pins.
14. **One chat reminder per recorded event:** "Attack level 71: added to your Roll inbox." It follows the existing
    Roll reminders setting, and only an event that was saved gets one.

## Phases

Web pull request 1 comes first (W), then the plugin (A–D), then web pull request 2 (P).

### Phase W: the contract, on the web (pull request 1)

- [ ] **W1. The detected-events contract.** `contracts/detected-events/corpus.json` pairs real game signals with the
  events they must produce. The signals are chat lines, level changes, quest states, varbits, varps, popups and loot
  events. They start from RuneLite's own test fixtures and the review's cases. The goldens manifest lists the file, so
  the plugin's pin picks it up. The web test parses and classifies every expected event against a golden run, and
  expects the verdict the case names.
- [ ] **W2. Detector policies:** the new ids and versions this plan ships, all exact except Slayer, diary and pet;
  `clue-completion-loot-v1` approved; the retired ids refused.
- [ ] **W3. Every row of a paste can roll** (decision 9): the classifier, `detectedEventIdentityMatches` and
  `RollInboxDriver`. `RollInbox.test.tsx` flips its pin.
- [ ] **W4. No dead ends.** Every event in the corpus lands Ready, or Needs checking with choices:
  - clue tiers map to the casket keys;
  - a collection log name on several pages offers its pages;
  - a Slayer task pre-selects its master's tier.
- [ ] **W5. A pet pays once** (decision 12).
- [ ] **W6. `rules.detection`** in the bundle:
  - bosses and raids by kill-count names, with every boss key covered and Brutus included;
  - quest ids with the game's names where they differ;
  - the 48 diary tier ids.

  `detectorContractVersion` becomes 2. The relay's size budget is checked, and the goldens are rewritten.
- [ ] **W7. The card's words** in `data/runeliteWording.ts` and the byte-compared contract.

### Phase A: foundations in the plugin (no visible change)

- [ ] **A1. Re-pin** at web pull request 1's merge. `DetectedEventsContractTest` runs the corpus through the detectors;
  it starts with every case listed as pending, and each Phase B task clears its own.
- [ ] **A2. Signals:** one adapter turns RuneLite events into plain signals. Chat lines have their tags stripped;
  levels are real levels; quest states come as a snapshot; varbits, varps, popups and loot events come as they are.
  All parsing leaves `FateLockedPlugin`.
- [ ] **A3. Event ids from content** (decision 5), with `PairingSupport`'s SHA-256 helper. `FateEventFactoryTest` pins
  the new form.
- [ ] **A4. The store** (decisions 6 and 7):
  - New, Copied and Dismissed, where Copied and Dismissed win a merge;
  - duplicates dropped by id, and the 30-day cut;
  - filtered to the current run;
  - nulls kept on export, and bad entries dropped on load;
  - older events kept but never offered.
- [ ] **A5. The gate** (decision 8): a linked run as today, an unlinked run for whoever is logged in, and other game
  worlds shut. Diary, quest and Slayer memories change only when the gate is open.

### Phase B: the detectors, one commit each

Each detector is pure, reads plain signals, clears its cases in the corpus, and gets its own mutation run.

- [ ] **B1. Skill** (D18): the baseline comes from real levels at login and when the plugin starts; one event per
  level; the count is the level.
- [ ] **B2. Quest** (D6): FINISHED transitions of the game's quest states, remembered per character like diaries. The
  quest scroll triggers a fresh read. Names map to web ids through the bundle's table, and the widget scraping goes.
- [ ] **B3. Combat task** (D3): the popup path (`CA_TASK_POPUP` off, then the notification script's text), and version
  2.
- [ ] **B4. Collection log** (D10): tags stripped, exact names, and the popup path (`OPTION_COLLECTION_NEW_ITEM`).
- [ ] **B5. Clue** (D7, D17): "You have completed 12 hard Treasure Trails.", counted. Loot Tracker's event stays as a
  fallback.
- [ ] **B6. Bosses and raids** (D4, D17): the kill-count line through the bundle's table, counted. Raids are raid
  completions. The nine-name list and the Loot Tracker path go.
- [ ] **B7. Slayer** (D8): `SLAYER_COUNT`, `SLAYER_TARGET`, `SLAYER_MASTER` and `SLAYER_TASKS_COMPLETED`, with the
  task's name from the game's Slayer task table. The chat regexes go. The master's values are checked in game before
  release; until they are, the web asks for the master.
- [ ] **B8. Diary** (D9): the varbit and tier tables move into the detector, and every tier id is checked against the
  bundle's list. Karamja's completed value is checked in game.
- [ ] **B9. Reminders** (decision 14), behind the gate, only after a save.

### Phase C: the card and the copy

- [ ] **C1. `RollInboxPresenter`** (pure):
  - the header counts New events;
  - rows show newest first, five then "+N more", each with its type's OSRS icon, its label, and "Needs checking" where
    the web will ask;
  - **Copy for tracker** is the primary action, then "Copied 5 · Copy again";
  - Dismiss on each row is quiet;
  - **Open web Roll Inbox** and the warnings row stay;
  - the note becomes "Copied only when you click. RuneLite doesn't upload anything."
- [ ] **C2. The copy:** the one `setContents` call, on the Swing thread, from the card's action. Events turn Copied only
  after the write succeeds. The notice reads "Copied 5 events. In the companion's Roll Inbox, choose Paste from
  RuneLite." A boundary test pins the single clipboard write.
- [ ] **C3. An unlinked run's card** says the first paste links the run to the character (decision 8).
- [ ] **C4. Pictures:** previews for an empty card, New rows, Needs checking, and Copied; the guide's `roll-inbox` shot
  anchors on the new parts.
- [ ] **C5. Owner review in RuneLite,** with real messages captured where the game allows.

### Phase D: release (plugin)

- [ ] **D1. Docs:** README, CONTRIBUTING, the Hub review notes and the review checklist.
- [ ] **D2. Checks:** clean check, the mutation runs, and the owner's "all good".
- [ ] **D3. Pull request and Plugin Hub:** push and open the pull request after the owner's OK. After the owner merges,
  open the Hub pull request with the merge commit.

### Phase P: the paste, on the web (pull request 2)

- [ ] **P1. Paste from RuneLite** in the Roll Inbox. It reads the clipboard, with a box to paste into where the browser
  won't allow that. It takes up to 250 events and says what it did: "Added 5. 2 were already here. 1 was too old."
- [ ] **P2. Linking a run from a paste** (decision 8), with a confirmation that names the character.
- [ ] **P3. The Roll Inbox's words:**
  - the empty state: "In RuneLite, open the Roll inbox card and choose Copy for tracker, then paste here.";
  - the coach hint;
  - "Skip any you've already logged by hand".
- [ ] **P4. The guide and What's New:**
  - the Roll inbox chapter and its re-rendered picture;
  - the privacy text, `RuneLiteOnboarding` and `docs/online-relay.md`;
  - a What's New release.

## Owner decisions and pending steps

- The decisions above stand unless the owner changes them. Players will see decisions 4, 7, 8 and 12.
- Real messages for the corpus come first from RuneLite's own fixtures. The owner's review in RuneLite (C5) confirms
  the Slayer master values, Karamja's diary value, and the combat task and collection log popups.
