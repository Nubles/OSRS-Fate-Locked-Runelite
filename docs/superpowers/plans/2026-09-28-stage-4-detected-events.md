# Stage 4: Detected Events Implementation Plan

> Steps use checkbox (`- [ ]`) syntax for tracking. Each task is one commit
> unless it says otherwise.

**Goal:** Stage 4 of the [overhaul design](../specs/2026-09-24-plugin-overhaul-design.md), as the owner chose on
24 September 2026 (decision 2): a hand-off the player starts.
- RuneLite keeps what it notices for each character: levels, quests, diaries, combat tasks, collection log slots,
  clues, bosses and raids, and Slayer tasks. Pets are left as they are for now (decision 13).
- **Copy for tracker** in the Roll inbox card puts them on the clipboard.
- **Paste from RuneLite** in the web Roll Inbox brings them in. The web's classifier and Roll path, which already work
  end to end, take over from there.
- The detectors are rebuilt on the web's own ids, and one set of real game messages tests both repositories.

**What players notice:**
- Nothing is taken away. Logging by hand, rolling and spending Keys stay exactly as they are (decision 1).
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
| Pet | Unnamed, by design (D11) | Unchanged, and not copied yet (decision 13) |

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
     the detection tables in the bundle, and the two blockers. It merges first; older plugins ignore the new bundle
     fields.
  2. **The plugin** pins web pull request 2's head commit, which holds the card's words with the guide that uses them
     (Stage 3 did the same). The owner tries it in RuneLite before anything is pushed. The owner merges it, and the
     Plugin Hub pull request follows.
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
    offered (decision 8).
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
reversed. The owner approved the plan on 28 September with two changes, decisions 1 and 13.

1. **Manual play stays exactly as it is** (the owner, 28 September: no "massive locks and restrictions"). Players can
   still log anything by hand, and spend and roll Keys freely. RuneLite and the Roll Inbox are an optional helper:
   - a paste only lists what RuneLite noticed;
   - nothing rolls, spends or unlocks on its own, and every roll is the player's click;
   - any row can be skipped;
   - nothing manual is blocked by, or waits on, anything RuneLite noticed.

   A web test pins this (W3).
2. **The clipboard is the hand-off** (the owner's decision of 24 September). Nothing is uploaded; the relay is
   unchanged.
3. **The copy is plain JSON the web already reads:** `{"format":"fate-locked-runelite-events","events":[…]}` at
   protocol 1. Players can see what they paste. A paste takes up to 250 events; the relay's limit of 100 is unchanged.
4. **Events carry the web's own ids:**
   - skill names;
   - quest ids, from the game's quest states, not the scroll;
   - diary tier ids;
   - combat task names, which the web accepts because all 655 are unique;
   - collection log item names, without colour tags;
   - clue tiers;
   - boss keys, from kill counts through a table the web writes into the bundle;
   - Slayer tasks, with their master.
5. **One event per level gained, oldest first.** The web's own level-up adds one level at a time, so a jump pasted in
   rolls as often as logging each level by hand.
6. **An event's id comes from what it is:** the character, the run, the type, the key and a count. The count is the
   level, the kill count under the game's name for it, or the clue count. Seeing the same thing twice records it once,
   and pasting twice is harmless. A collection log item and a Slayer task get an id of their own each time: the game
   names 18 chompy bird hats alike, and a streak reset repeats a task at the same streak.
7. **RuneLite keeps each character's events for the current run,** marked New, Copied or Dismissed, and forgets them
   after 30 days, as the web does.
8. **Events recorded before this release are never offered.** They have no status, some carry old labels, and a boss
   kill already rolled by hand would roll again. The card starts empty.
9. **A run that isn't linked to a character takes a paste too,** with no link asked for:
   - RuneLite records events for whoever is logged in;
   - the paste shows whose events they are, and the player decides row by row;
   - a linked run keeps today's rule: another character's events aren't recorded or accepted.
10. **Every row of a paste can roll** (blocker 1). An event from an earlier revision of the same run and character is
    checked against the run as it is now. A reviewed row keeps its review.
11. **Kills come from the game's kill-count line** (for example "Your Vorkath kill count is: 12."), so the Loot Tracker
    plugin isn't needed. Raids and clues use their completion lines too, so no loot event is read at all: a loot
    fallback beside a count line would record one clue twice.
12. **Slayer comes from the game's Slayer variables** (task, count, master and streak), as RuneLite's own Slayer
    plugin does. A task completes when its count reaches 0 and the streak rises.
13. **Pets are left as they are for now** (the owner, 28 September). A poll is deciding whether a pet rewards an
    Omni-Key instead of a Key. Until it has, RuneLite doesn't copy pet drops, and nothing about pets changes on the
    web; players log pets by hand as today.
14. **The words come from the web first:** Copy for tracker, Paste from RuneLite, New and Copied join the wording
    contract with the guide's glossary, in web pull request 2, whose head the plugin pins. Dismiss stays an ordinary
    button label.
15. **One chat reminder per recorded event:** "Attack level 71: added to your Roll inbox." It follows the existing
    Roll reminders setting, and only an event that was saved gets one.

## Phases

Web pull request 1 comes first (W), then the plugin (A–D), then web pull request 2 (P).

### Phase W: the contract, on the web (pull request 1)

- [x] **W1. The detected-events contract.** `contracts/detected-events/corpus.json` pairs real game signals with the
  events they must produce. The signals are chat lines, level changes, quest states, varbits, varps, popups and loot
  events. They start from RuneLite's own test fixtures and the review's cases. The goldens manifest lists the file, so
  the plugin's pin picks it up. The web test parses and classifies every expected event against a golden run, and
  expects the verdict the case names.
- [x] **W2. Detector policies:** the new ids and versions this plan ships, all exact except Slayer and diary;
  `clue-completion-loot-v1` approved; the retired ids refused.
- [x] **W3. Every row of a paste can roll, and manual play is untouched** (decisions 1 and 10): the classifier,
  `detectedEventIdentityMatches` and `RollInboxDriver`. `RollInbox.test.tsx` flips its pin. A new test pins decision
  1: with rows waiting in the inbox, a level-up logged by hand, a manual roll and spending a Key all work as before,
  and a paste changes nothing in the run until the player clicks Roll.
- [x] **W4. No dead ends.** Every event in the corpus lands Ready, or Needs checking with choices:
  - clue tiers map to the casket keys;
  - a collection log name on several pages offers its pages;
  - a Slayer task pre-selects its master's tier.
- [x] **W5. `rules.detection`** in the bundle:
  - bosses and raids by kill-count names, with every boss key covered and Brutus included;
  - quest ids with the game's names where they differ;
  - the 48 diary tier ids.

  `detectorContractVersion` becomes 2. The relay's size budget is checked, and the goldens are rewritten.
- [x] **W6. The card's words** moved to Phase P (P0): the wording test wants every shared word in the guide's
  glossary, so they ship with the guide.

*Done* on web branch `claude/stage-4-contract`, local, 29 September:
- `fdf0dfc` W3, with a `preparedRevision` guard so a stale prepared roll still can't land;
- `6944c94` W2;
- `ec44d84` W4;
- `f0c1b0c` W5;
- `3813f94` W1.

Also:
- `9812bc5` What's New, "Groundwork for RuneLite's Roll Inbox";
- `1172a03` loads the detection names only at export time, keeping the startup file in its budget.

Every commit's mutants were caught. Writing W1 found a dead end the plan hadn't: the game calls all 18 chompy bird
hats "Chompy bird hat", so a plain name with no item of its own now offers every item it can be.

### Phase A: foundations in the plugin (no visible change)

- [x] **A1. Re-pin** at web pull request 1's merge. `DetectedEventsContractTest` runs the corpus through the detectors;
  it starts with every case listed as pending, and each Phase B task clears its own. Done through web pull request 2,
  which holds pull request 1's merge (`17bdc1c`): the plugin pinned its head, then its merge commit.
- [x] **A2. Signals:** the plugin turns RuneLite's events into plain signals (`detection.Signal`), and all parsing
  leaves `FateLockedPlugin`:
  - chat lines, popups, level changes and varbit changes as they come;
  - a session's first reading once its files are open and detection is on: real levels, diary tiers, the quests'
    states and the Slayer task;
  - the quests again after the quest scroll and every minute, and Slayer after its variables changed.

  No loot event is read (decision 11).
- [x] **A3. Event ids from content** (decision 6), with `PairingSupport`'s SHA-256 helper. `FateEventFactoryTest` pins
  the new form.
- [x] **A4. The store** (decisions 7 and 8):
  - New, Copied and Dismissed, where Copied and Dismissed win a merge;
  - duplicates dropped by id, and the 30-day cut;
  - filtered to the current run;
  - nulls kept on export, and bad entries dropped on load;
  - older events kept but never offered;
  - pet drops kept but not offered yet (decision 13).
- [x] **A5. The gate** (decision 9): a linked run as today, an unlinked run for whoever is logged in, and other game
  worlds shut. The quest and diary memories change only while the gate is open. Slayer needs no memory: the game's
  variables hold the task.

*Done* on plugin branch `claude/stage-4-detected-events`, local, 29 September: `fd3e4e8` A3, `c6408cf` A4, `ef779b5`
A4 and A5 in the plugin, and `6b4b07f` A2. A1 waits for web pull request 1.

### Phase B: the detectors, one commit each

The detectors are one pure suite, `detection.Detectors`, which reads plain signals and clears the corpus's cases. The
suite, the bundle's tables and the wiring each got a mutation run.

- [x] **B1. Skill** (D18): the baseline comes from real levels at the session's first reading; one event per level.
  The level is in the label.
- [x] **B2. Quest** (D6): FINISHED transitions of the game's quest states, remembered per character in `quests.json`
  beside `diary-tiers.json`. The quest scroll, and a minute, trigger a fresh read. Names map to web ids through the
  bundle's table; 212 of RuneLite's 213 quests do, all but the whole of Recipe for Disaster, whose last part is RFD:
  Finale. The widget scraping went.
- [x] **B3. Combat task** (D3): the popup path (the notification script's title and text, as RuneLite's Screenshot
  plugin reads them), and version 2. The chat line and the popup share one id, so no setting needs reading.
- [x] **B4. Collection log** (D10): tags stripped, exact names, and the popup path. `OPTION_COLLECTION_NEW_ITEM` says
  which notification stands for the item: the chat line only at 1, as RuneLite's Screenshot plugin reads it.
- [x] **B5. Clue** (D7, D17): "You have completed 12 hard Treasure Trails.", counted. No loot event (decision 11).
- [x] **B6. Bosses and raids** (D4, D17): the kill-count line through the bundle's table, counted, with "Your
  completion count for TzHaar-Ket-Rak's First Challenge is: 3." too. Raids are raid completions. The nine-name list
  and the Loot Tracker path went.
- [x] **B7. Slayer** (D8): `SLAYER_COUNT`, `SLAYER_TARGET`, `SLAYER_COUNT_ORIGINAL`, `SLAYER_MASTER` and the streak,
  as RuneLite's Slayer plugin reads them: Krystilia's and Mortimer's streaks are their own, a boss task is task 98, and
  the task's name comes from the game's Slayer task table. The amount and the streak may change apart, and a streak
  read for another master is another task's. The chat regexes went. RuneLite names masters 7 (Krystilia) and 10
  (Mortimer); the rest are checked in game before release, and until then the web asks for the master.
- [x] **B8. Diary** (D9): the varbit and tier tables moved into `detection.DiaryTiers`, and every tier id is checked
  against the bundle's list. Karamja's easy, medium and hard varbits are 2 once done and 1 once started (the OSRS
  Wiki's varbits 3578, 3599 and 3611). The released plugin reminds "Diary complete" when one is started; this fixes
  it. The value is still checked in game (C5).
- [x] **B9. Reminders** (decision 15): "Attack level 71: added to your Roll inbox.", behind the gate, only after a
  save, with the Roll reminders setting. A pet gets none (decision 13).

*Done* on the plugin branch, local, 29 September: `390e3db` the suite (84 mutants caught), `1c08292` the bundle's
`rules.detection`, and `6b4b07f` the wiring. The corpus test (A1) runs once the plugin pins web pull request 1. Writing
the suite added eight cases to the corpus, on the web branch as `a1b2656`: Karamja's value, a change before the
reading, a boss task, the amount and streak apart, another master's streak, the popup setting, two chompy bird hats,
and the TzHaar-Ket-Rak line.

### Phase C: the card and the copy

- [x] **C1. `RollInboxPresenter`** (pure), with C2 and C3 in `98346e8`:
  - the header counts New events;
  - rows show newest first, five then "+N more", each with its type's OSRS icon, its label, and "Needs checking" where
    the web will ask;
  - **Copy for tracker** is the primary action, then "Copied 5 · Copy again";
  - Dismiss on each row is quiet;
  - **Open web Roll Inbox** and the warnings row stay;
  - the note becomes "Copied only when you click. RuneLite doesn't upload anything."
- [x] **C2. The copy:** the one `setContents` call, on the Swing thread, from the card's action. Events turn Copied only
  after the write succeeds. The notice reads "Copied 5 events. In the tracker's Roll Inbox, choose Paste from
  RuneLite." A boundary test pins the single clipboard write.
- [x] **C3. An unlinked run's card** says whose events it holds (decision 9).
- [x] **C4. Pictures:** previews for an empty card, New rows, Needs checking, and Copied; the guide's `roll-inbox` shot
  anchors on the new parts. The previews add an unlinked run and a copy the clipboard refused.
- [x] **C5. Owner review in RuneLite,** with real messages captured where the game allows. On 2 October the owner
  pasted a real copy into the tracker (it read in, a second paste added nothing, and another run's event was held),
  and asked for a chunk grid without the dashed line: Chunk grid in both border settings (`be4105a`), which the owner
  then confirmed.

### Phase D: release (plugin)

- [x] **D1. Docs:** README, CONTRIBUTING, the Hub review notes and the review checklist (rows 39 to 41 for Stage 4,
  and Chunk grid in rows 27 and 38).
- [x] **D2. Checks:** clean check, the mutation runs, and the owner's "all good" ("the chunk grid works, start the
  release", 2 October).
- [x] **D3. Pull request and Plugin Hub:** push and open the pull request after the owner's OK. After the owner merges,
  open the Hub pull request with the merge commit.

  *Done* on 2 October 2026: the plugin merged as `4c53198` (#26), with its build and the Hub packager green;
  runelite/plugin-hub#17653 merged at 22:53 UTC, so the Hub builds `4c53198`; web pull request 2, held as a draft
  until then, merged as `46b009c` (web #61); and the contracts are pinned at that merge commit (the same files as
  the branch's head, `fba7072`).

### Phase P: the paste, on the web (pull request 2)

- [x] **P0. The card's words** (from W6) in `data/runeliteWording.ts`, the byte-compared contract, and the guide's
  glossary. The plugin pins this pull request's head commit. `962479b` on web branch `claude/stage-4-paste`, from
  pull request 1's head; the branch is web #61, a draft, and pull request 1 is web #60.
- [x] **P1. Paste from RuneLite** in the Roll Inbox. It reads the clipboard, with a box to paste into where the browser
  won't allow that. It takes up to 250 events and says what it did: "Added 5. 2 were already here. 1 was too old."
- [x] **P2. A paste into an unlinked run** (decision 9): it names the character the events came from, and each
  row can be rolled or skipped as usual. Nothing asks to link the run.
- [x] **P3. The Roll Inbox's words** (P1 to P3 in `fc96ef9` on the same branch):
  - the empty state: "In RuneLite, open the Roll inbox card and choose Copy for tracker, then paste here.";
  - the coach hint;
  - "Skip any you've already logged by hand", since logging by hand stays open (decision 1).
- [x] **P4. The guide and What's New:**
  - the Roll inbox chapter and its re-rendered picture;
  - the privacy text, `RuneLiteOnboarding` and `docs/online-relay.md`;
  - a What's New release.

## Owner decisions and pending steps

- The owner approved the plan on 28 September, with decisions 1 and 13 as their changes. Players will see
  decisions 1, 5, 8, 9, 13 and 15.
- Pets wait for the poll on rewarding an Omni-Key instead of a Key. Once it's decided, pets join the hand-off with
  whichever reward wins, as a change of their own.
- Real messages for the corpus come first from RuneLite's own fixtures. The owner's review in RuneLite (C5) confirms
  the Slayer master values, Karamja's diary value (2, by the OSRS Wiki), and the combat task and collection log
  popups.
