# Fate Locked Ironman

Fate Locked Ironman is one RuneLite Plugin Hub plugin for the
[Fate Locked tracker](https://github.com/Nubles/OSRS-Fate-Locked). Its sidebar
shows the app-authored rules for the current run; in game it draws chunk
borders and locked land, warns about locked content, and provides the optional
Strict Mode safety layer.

The plugin is on the RuneLite Plugin Hub. The Hub builds the commit pinned in
its [entry](https://github.com/runelite/plugin-hub/blob/master/plugins/fate-locked-ironman),
not `main`, so changes here reach players only when that pin is updated (see
[Releasing to the Plugin Hub](CONTRIBUTING.md#releasing-to-the-plugin-hub)).

## Connect the tracker

The normal same-PC setup is:

1. Install the single **Fate Locked Ironman** plugin from the RuneLite
   Plugin Hub.
2. Open the Fate Locked sidebar (the crystal key) and click **Connect
   tracker** on the status card. Read and accept the third-party network
   warning to turn on online sync.
3. In the GitHub Pages tab RuneLite opens, confirm the current tracker
   profile.
4. Return to RuneLite: the status card says **Rules up to date** and when
   it last synced.
5. Use clipboard or file import only if the relay is unavailable.

RuneLite retrieves a complete v4 rules bundle from the fixed Fate Locked
relay. It does not upload player or gameplay data. The relay sees the IP
address used for the HTTPS request, and the rules it holds name your
character, so it can link the two.

Once connected, RuneLite checks the tracker every minute while you play,
every 5 minutes at the login screen, and at once when you log in. To pick
up a change you have just made in the web tracker, press **Check now** in
Connection & backup (the status card offers it too when the rules may be
out of date); it works once every 10 seconds. When a check fails, the
status card says why and when the next check is, never more than 5 minutes
away unless the relay asks RuneLite to wait longer.

**Re-pair tracker…** in Connection & backup pairs RuneLite with another
tracker profile, and asks first. RuneLite keeps your current pairing until
the new one sends your rules; if none arrives within 10 minutes, or you
press **Cancel re-pairing**, nothing changes. With online sync off, the
status card offers **Turn on online sync**, which picks up the pairing you
already have. **Disconnect** forgets the pairing and keeps the rules you
have.

The **Pairing** row shows only the last four characters of the code in
use, as the web tracker's pairing dialog does, so the code stays off
screen. If you press **Disconnect** in the web tracker, the status card
says "Disconnected in the tracker"; press **Connect tracker** to pair
again.

Online sync is off by default, including for existing pairings. No relay
requests are made until you accept the warning. Canceling leaves sync
disabled. Turn off **Online sync** in Connection & backup, or under Tracker
in RuneLite's configuration, at any time to stop syncing. Clipboard and
file imports remain available offline.

## The sidebar

The plugin has one navigation button, a crystal key. Its sidebar leads with
a status card: whether your rules are current and for the character you're
on, and the one thing to do about it. Below it, five cards open and close on
their own:

1. **Here**: the place you're standing in, its status and why, and what it
   holds, category by category. Each category opens and closes and, closed,
   says what it holds; Skilling opens skill by skill, with the game's skill
   icons and your level and cap. What you leave open stays open. Click a
   skilling spot, monster, bank or shop, and the game's own arrow points at
   the nearest one around you.
2. **Strict Mode**: its switch, its status, the pause, and what it recently
   stopped.
3. **Run**: whose run it is, progress, Keys, Omni-Keys, Chaos Keys, Fate
   Points, the ritual and the next goal.
4. **Roll inbox**: local events that may be worth a roll.
5. **Connection & backup**: online sync, the pairing, and the clipboard and
   file backups.

Here starts open. Every setting is in RuneLite's configuration, under Fate
Locked Ironman: 21 settings in six sections (Tracker, Strict Mode, Alerts,
Display, Custom colours and Backup). Settings from before Stage 3 carry over
once, the first time the plugin starts.

## Main features

- Every answer is the tracker's own, for land, the sea, and dungeons and
  other interiors; an instance is judged as the chunk it copies. The world
  map, game view, minimap, HUD and sidebar show them.
- Chunk borders on the ground where locked land starts, dashed over a dark
  underlay with a band of shade on the locked side, and the same on the
  minimap.
- The world map shades locked land like fog and outlines your unlocked land
  all the way round, with a tooltip for the chunk under the mouse.
- Every row in Here has a status. Where the tracker can't see a requirement,
  such as a quest started, quest points, a free-to-play world or a light
  source you carry, the plugin checks it in game, and a row that isn't ready
  names only what's left.
- A Compact or Detailed HUD: where you are and why it's locked, Strict Mode,
  and the nearest bank and shop; Detailed adds your progress, Keys, Fate
  Points and what the place holds.
- A chat line per area, and on entering locked land a sound and one short
  fade. Infoboxes for Keys, Fate Points and progress, each movable on its
  own.
- Bank, Slayer-task and over-tier gear warnings, and a different-character
  line at login.
- (Locked) tags on right-click options the rules lock, and a four-second
  warning banner for recognised locked travel.
- Default, colour-blind safe or custom colours for everything the plugin
  draws.
- Strict Mode, which blocks only travel the tracker's travel table matches
  by id, to one place it locks, with fail-open safeguards, a status that
  says when it cannot act, a 60-second pause, and a bounded local audit log.
- Local detection of supported skill, quest, diary, combat achievement,
  collection, clue, boss, raid, pet, and Slayer observations.

## Roll Inbox ownership and privacy

The Roll inbox card counts the observations saved for the logged-in
account, in its own folder of RuneLite's local Fate Locked data directory,
which keeps the newest 250. Ambiguous observations are counted under
**Needs checking**. Only the character your tracker profile is bound to is
tracked, and only on worlds that save to that account, so not Leagues,
Deadman or speedrunning worlds. A profile bound to no character gets no
roll reminders. Detection never rolls and never changes the tracker; the
player still reviews the result and presses Roll in the web app.

**Local only — RuneLite does not upload gameplay data.**

**Open web Roll Inbox** opens a separate browser view. It does not transfer
RuneLite's local history to that view.

Each account's history, Strict Mode log, Slayer task and finished diary
tiers live in `accounts/<account id>/` in the data directory, so a main
account and an ironman played in one RuneLite keep them apart, and two
RuneLites on one account merge their writes rather than overwrite each
other. A diary tier finished while RuneLite was closed counts at the next
login. The first time an account is used, its history starts from the
shared history (or the older queue) that earlier versions kept, taking
only that character's observations; the Strict Mode log and Slayer task
come along only for the character the rules are bound to. The shared files
are left unchanged. A malformed file is preserved with a corruption suffix
and a new one is started.

## Clipboard and file recovery

- **Import from clipboard:** copy your rules in the tracker, then press
  **Import from clipboard** in Connection & backup, or its hotkey.
- **Backup file:** place `fate-locked-bundle-*.json` in
  `~/.runelite/fate-locked/` (or
  `%USERPROFILE%\.runelite\fate-locked\` on Windows), then click **Load
  newest backup file** in Connection & backup. It does not watch the folder.

The plugin keeps the last rules it accepted in `saved-rules.json` and
brings them back when it starts, even offline. It reads the newest backup
file at startup only when nothing is saved, as on the first start after
updating. Saved tracker rules are never fresh enough for Strict Mode until
the tracker confirms them, and rules that arrive later always replace
them.

Imports replace the active rules only after complete parsing and validation.
Malformed, stale, or unsupported relay responses keep the previous valid
rules.

## Strict Mode

Strict Mode is off by default. It never removes, reorders or creates menu
entries and never performs an action. It consumes a click in one case only:
the tracker's travel table matches the click by id (a spell by its spellbook
and name; a tablet, scroll or teleport item by its item id), the option goes
to one place, and fresh rules bound to the logged-in character lock it.
The tracker locks a trip when the place is locked, or when the run hasn't
unlocked what the trip needs, such as a spellbook, Teleport Tablets or
Jewelry Teleports.

An option that picks its place after the click, such as a jewellery Rub, is
never blocked. Fairy rings, spirit trees, gliders, charters, boats and the
other networks are matched too, and an option to one locked place is tagged
(Locked), but they are never blocked in this release. Walking, NPCs, objects
(including doors, stairs and ladders), banks and equipment are never
blocked; the (Locked) menu tags, the locked-bank warning and the over-tier
gear warning cover them.

Missing, invalid, legacy, future, stale, unbound, wrong-character,
unmatched, several-place, Allowed, Not ready and Unknown decisions fail
open. Rules saved before the tracker sent a travel table can't match any
trip, so Strict Mode reads Inactive until the tracker syncs them again. The
sidebar shows whether Strict Mode is Active, Paused, Off, or Inactive and
why, and the HUD shows it while it is on. The pause turns it off for 60
seconds and resumes automatically. A block's notice may suggest a trip the
player carries that the tracker allows; the plugin never uses it for them.

This behavior is adjacent to RuneLite's restrictions on conditional menu
entry changes, so it is limited to travel matched by id and does not claim
reviewer approval. See [Plugin Hub review notes](docs/plugin-hub-review-notes.md).

## Building

Developer architecture, compliance commands, and the standard-jar build are
documented in [CONTRIBUTING.md](CONTRIBUTING.md). The planned same-PC evidence
is tracked in the
[manual validation matrix](docs/plugin-hub-manual-matrix.md).
