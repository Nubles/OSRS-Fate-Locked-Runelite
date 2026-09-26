# Fate Locked Ironman

Fate Locked Ironman is one RuneLite Plugin Hub plugin for the
[Fate Locked tracker](https://github.com/Nubles/OSRS-Fate-Locked). Its single
sidebar shows the app-authored rules for the current run, renders chunk
boundaries and lock state, warns about locked content, and provides the
optional Strict Mode safety layer.

The plugin is on the RuneLite Plugin Hub. The Hub builds the commit pinned in
its [entry](https://github.com/runelite/plugin-hub/blob/master/plugins/fate-locked-ironman),
not `main`, so changes here reach players only when that pin is updated (see
[Releasing to the Plugin Hub](CONTRIBUTING.md#releasing-to-the-plugin-hub)).

## Connect the tracker

The normal same-PC setup is:

1. Install the single **Fate Locked Ironman** plugin from the RuneLite
   Plugin Hub.
2. Open its one sidebar and click **Connect tracker**. Read and accept the
   third-party network warning to enable online sync.
3. In the GitHub Pages tab RuneLite opens, confirm the current tracker
   profile.
4. Return to RuneLite and verify that the Fate Locked panel shows
   **Connected**.
5. Use clipboard or file import only if the relay is unavailable.

RuneLite retrieves a complete v4 rules bundle from the fixed Fate Locked
relay. It does not upload player or gameplay data. The relay sees the IP
address used for the HTTPS request, and the rules it holds name your
character, so it can link the two.

Once connected, RuneLite checks the tracker every minute while you play,
every 5 minutes at the login screen, and at once when you log in. To pick
up a change you have just made in the web tracker, press **Check now**
under the connection status; it works once every 10 seconds. When a check
fails, the line under the status says why and when the next check is,
never more than 5 minutes away unless the relay asks RuneLite to wait
longer.

The same button pairs RuneLite with another tracker profile once it is
connected: it reads **Re-pair tracker…** and asks first. RuneLite keeps
your current pairing until the new one sends your rules; if none arrives
within 10 minutes, or you press **Cancel re-pairing**, nothing changes. With
online sync off, it reads **Turn on online sync** and picks up the pairing
you already have.

The sidebar's **Pairing** row shows only the last four characters of the
code in use, as the web tracker's pairing dialog does, so the code stays
off screen. If you press **Disconnect** in the web tracker, the sidebar
says "Disconnected in the web tracker"; press **Connect tracker** to pair
again.

Online sync is off by default, including for existing pairings after this
update. No relay requests are made until you accept the warning. Canceling
leaves sync disabled. Disable **Enable online sync** in the Bundle section
or RuneLite plugin settings at any time to stop syncing. Clipboard and file
imports remain available offline.

## One unified sidebar

The plugin has one navigation button and seven independently collapsible
sections:

1. Current chunk
2. Guardian
3. Roll inbox
4. Run
5. Bundle
6. Warnings
7. Rendering

Current chunk and Guardian start expanded; the remaining sections start
collapsed. The existing 30 settings plus online-sync consent are available in these sections,
including the single Strict Mode toggle.

## Main features

- World-map, scene, minimap, and current-chunk rendering from app-authored
  rules.
- HUD run state, account binding, unlock progress, pinned goals, and active
  warnings.
- Locked-region, bank, slayer-task, over-tier gear, and account-mismatch
  warnings.
- Menu tagging and a four-second warning banner for recognised locked
  actions.
- Strict Mode, which blocks only exactly matched travel into locked areas,
  with fail-open safeguards, a status that says when it cannot act, a
  60-second pause, and a bounded local audit log.
- Local detection of supported skill, quest, diary, combat achievement,
  collection, clue, boss, raid, pet, and Slayer observations.

## Roll Inbox ownership and privacy

The Roll Inbox section counts the observations saved for the logged-in
account, in its own folder of RuneLite's local Fate Locked data directory,
which keeps the newest 250. Ambiguous observations are counted under
**Needs review**. Only the character your tracker profile is bound to is
tracked, and only on worlds that save to that account, so not Leagues,
Deadman or speedrunning worlds. A profile bound to no character gets no
roll reminders. Detection never rolls and never changes the tracker; the
player still reviews the result and presses Roll in the web app.

**Local only — RuneLite does not upload gameplay data.**

**Open web Roll Inbox** opens a separate browser view. It does not transfer
RuneLite's local history to that view.

Each account's history, Strict Mode log and Slayer task live in
`accounts/<account id>/` in the data directory, so a main account and an
ironman played in one RuneLite keep them apart, and two RuneLites on one
account merge their writes rather than overwrite each other. The first
time an account is used, its history starts from the shared history (or
the older queue) that earlier versions kept, taking only that character's
observations; the Strict Mode log and Slayer task come along only for the
character the rules are bound to. The shared files are left unchanged. A
malformed file is preserved with a corruption suffix and a new one is
started.

## Clipboard and file recovery

- **Import from clipboard:** copy a bundle in the tracker, then use the
  plugin sidebar or its re-import hotkey.
- **Backup file:** place `fate-locked-bundle-*.json` in
  `~/.runelite/fate-locked/` (or
  `%USERPROFILE%\.runelite\fate-locked\` on Windows), then click **Load
  newest backup file** in the Bundle section. It does not watch the folder.

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
the click is travel the plugin matches exactly to one destination (a teleport
spell or tablet, a teleport item's destination option, or a named transport
destination), and fresh rules bound to the logged-in character show that
destination Locked.

Walking, NPCs, objects (including doors, stairs and ladders), banks and
equipment are never blocked. The red (LOCKED) menu tags, the locked-bank
warning and the over-tier gear warning cover them. Fairy-ring codes, spirit
trees, gliders, charters and other destinations picked in an interface,
jewellery "Rub" dialogs and house portals are not recognised, so they are
never blocked either.

Missing, invalid, legacy, future, stale, unbound, wrong-character, ambiguous,
unrecognised, Allowed, or Unknown decisions fail open. The sidebar shows
whether Strict Mode is Active, Paused, Off, or Inactive and why. The pause
turns it off for 60 seconds and resumes automatically.

This behavior is adjacent to RuneLite's restrictions on conditional menu
entry changes, so it is limited to exactly matched travel and does not claim
reviewer approval. See [Plugin Hub review notes](docs/plugin-hub-review-notes.md).

## Building

Developer architecture, compliance commands, and the standard-jar build are
documented in [CONTRIBUTING.md](CONTRIBUTING.md). The planned same-PC evidence
is tracked in the
[manual validation matrix](docs/plugin-hub-manual-matrix.md).
