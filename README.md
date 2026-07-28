# Fate Locked Ironman

Fate Locked Ironman is one RuneLite Plugin Hub plugin for the
[Fate Locked tracker](https://github.com/Nubles/OSRS-Fate-Locked). Its single
sidebar shows the app-authored rules for the current run, renders chunk
boundaries and lock state, warns about locked content, and provides the
optional Strict Mode safety layer.

This branch is a Plugin Hub candidate. It has not been submitted or accepted.

## Connect the tracker

The normal same-PC setup is:

1. Install the single **Fate Locked Ironman** plugin from the RuneLite
   Plugin Hub.
2. Open its one sidebar and click **Connect tracker**.
3. In the GitHub Pages tab RuneLite opens, confirm the current tracker
   profile.
4. Return to RuneLite and verify that the Fate Locked panel shows
   **Connected**.
5. Use clipboard or file import only if the relay is unavailable.

RuneLite retrieves a complete v4 rules bundle from the fixed Fate Locked
relay. It does not upload player or gameplay data. The relay sees the IP
address used for the HTTPS request.

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
collapsed. The existing 30 settings remain available in these sections,
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
- Strict Mode Travel Guardian with exact-destination blocking, fail-open
  safeguards, a shared 60-second pause, and a bounded local audit log.
- Local detection of supported skill, quest, diary, collection, clue,
  boss, raid, pet, minigame, and Slayer observations.

## Roll Inbox ownership and privacy

The Roll Inbox section shows the newest 250 unique observations saved in
RuneLite's local Fate Locked data directory. Ambiguous observations are
counted under **Needs review**. Detection never rolls and never changes the
tracker; the player still reviews the result and presses Roll in the web app.

**Local only — RuneLite does not upload gameplay data.**

**Open web Roll Inbox** opens a separate browser view. It does not transfer
RuneLite's local history to that view.

If the new history file is absent, the plugin can migrate the newest 250
pending entries from the former local queue. The old file is left unchanged.
A malformed history file is preserved with a corruption suffix and the
plugin begins a new local history.

## Clipboard and file recovery

- **Import from clipboard:** copy a bundle in the tracker, then use the
  plugin sidebar or its re-import hotkey.
- **Paste JSON:** paste a complete bundle into the Bundle section.
- **File:** place `fate-locked-bundle-*.json` in
  `~/.runelite/fate-locked/` (or
  `%USERPROFILE%\.runelite\fate-locked\` on Windows). The plugin reads the
  newest matching file and can watch for changes.

Imports replace the active rules only after complete parsing and validation.
Malformed, stale, or unsupported relay responses keep the previous valid
rules.

## Strict Mode

Strict Mode is off by default. It does not remove or reorder menu entries and
does not perform an action. Travel Guardian can consume only a
user-selected click when fresh, exact, account-bound app-authored rules prove
the destination is Locked.

Missing, invalid, legacy, future, stale, wrong-account, ambiguous, same-chunk,
unrecognised, Allowed, or Unknown decisions fail open. The sidebar pause
disables every Strict Mode category for 60 seconds and resumes automatically.

This behavior is adjacent to RuneLite's restrictions on conditional menu
entry changes. The release therefore requests reviewer pre-clearance and does
not claim that Strict Mode is already approved. See
[Plugin Hub review notes](docs/plugin-hub-review-notes.md).

## Building

Developer architecture, compliance commands, and the standard-jar build are
documented in [CONTRIBUTING.md](CONTRIBUTING.md). The planned same-PC evidence
is tracked in the
[manual validation matrix](docs/plugin-hub-manual-matrix.md).
