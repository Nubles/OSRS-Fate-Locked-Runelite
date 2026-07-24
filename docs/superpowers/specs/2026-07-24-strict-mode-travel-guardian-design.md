# Strict Mode Travel Guardian Design

**Date:** 2026-07-24  
**Status:** Approved design  
**Repository:** `Nubles/RS3-Fate-Locked-Runelite`

## Goal

Make travel the first comprehensive Strict Mode protection category. When the
current app-authored rules prove a travel action is locked, RuneLite stops the
click before it reaches the game, explains the rule in a compact banner and
chat record, and suggests a verified legal alternative.

The feature reduces accidental rule breaks without automating travel or making
uncertain decisions for the player.

## Product decisions

- Travel Guardian is part of the existing **Strict Mode** checkbox.
- Strict Mode remains off by default.
- No separate Travel Guardian toggle is added.
- Proven locked travel is blocked and explained.
- Allowed travel remains silent.
- Unknown, stale, legacy, malformed, or wrong-account decisions never block.
- The blocked banner offers **Pause Guardian for 60 seconds**.
- Pausing affects all Strict Mode prevention categories and resumes
  automatically.
- Guardian never activates an alternative, selects a destination, moves the
  player, or performs gameplay.

## User experience

### Strict Mode off

Existing overlays, menu tags, chat warnings, and other independently configured
advice continue to work. RuneLite does not cancel travel actions.

### Strict Mode on

A proven locked action is consumed before it reaches the game. A compact banner
appears for approximately four seconds:

> **Travel blocked — Fairy ring to Canifis**  
> Morytania is locked. Nearest legal option: Varrock teleport.  
> **Pause Guardian for 60s**

Chat retains a slightly fuller record:

> `[Fate Guardian] Blocked Fairy ring → Canifis: destination chunk is locked by Morytania access. Suggested: Varrock teleport.`

Repeated identical attempts refresh the banner but do not flood chat. While
paused, the panel and HUD show an amber countdown. Guardian resumes
automatically when the countdown reaches zero.

There is no “continue this locked action” control. A deliberate temporary pause
is the only inline override.

## Travel coverage

The first release recognises:

- teleport spells and tablets;
- jewellery and wearable teleports;
- fairy rings and spirit trees;
- minecarts, boats, charter ships, magic carpets, balloons and eagles;
- minigame, grouping and home teleports;
- doors, gates, ladders, stairs, trapdoors and shortcuts that cross areas; and
- `Walk here` clicks aimed into an adjacent authored locked chunk.

Walking is cancelled only when the clicked destination tile and its locked
chunk are both known from a fresh rules snapshot. Ambiguous movement remains
warning-only.

Cutscenes, knockbacks, server-forced movement, and movement already submitted
cannot always be cancelled. If one crosses into locked territory, the existing
entry warning activates immediately and highlights the safe direction.

## Architecture

### `TravelActionResolver`

Normalises RuneLite menu clicks into a small `TravelAction` contract:

- action family;
- source object, item, spell or transport identity;
- known origin;
- resolved destination;
- recognition confidence; and
- the original menu action for diagnostics.

It contains identification only and does not decide permission.

### `TravelRuleEvaluator`

Evaluates the normalised action against the latest app-authored v4 rules. It
returns:

- `ALLOWED`;
- `LOCKED`; or
- `UNKNOWN`.

A result also contains a concise reason and the rule identity used. This
component preserves the existing invariant that Unknown can never become
Locked.

### `TravelAlternativeFinder`

Ranks legal alternatives without activating them. A candidate is eligible only
when RuneLite can verify locally that:

- its destination is Allowed;
- the travel method is unlocked;
- quest, spellbook and level requirements are satisfied;
- a required item is equipped or carried when RuneLite can inspect that fact;
  and
- the rules snapshot is fresh and bound to the logged-in character.

Candidates are ranked in this order:

1. a legal method reaching the intended area;
2. a legal method reaching an adjacent unlocked chunk;
3. the nearest safe transport hub or bank; then
4. no suggestion rather than an unverified guess.

### `TravelBlockPresenter`

Owns the transient banner, deduplicated chat record, and concise wording. It
does not evaluate rules or pause Strict Mode.

### Existing Strict Mode controller

The existing Strict Mode guard remains the single enforcement owner. It calls
the travel components, consumes only a proven `LOCKED` action, and delegates
the existing 60-second pause state to the shared pause controller.

## Data flow

1. RuneLite emits a menu click.
2. `TravelActionResolver` attempts to normalise it.
3. Unrecognised actions are allowed and may receive an Unknown warning.
4. `TravelRuleEvaluator` checks account, freshness, bundle version and
   permissions.
5. Allowed and Unknown actions continue without cancellation.
6. A proven Locked action is consumed.
7. `TravelAlternativeFinder` searches only verified local candidates.
8. `TravelBlockPresenter` displays the banner and writes one deduplicated chat
   record.
9. If the player pauses Guardian, all Strict Mode blocking is disabled for 60
   seconds and then resumes automatically.

## Error handling and safe degradation

- Missing bundle: allow and report that protection is unavailable.
- Legacy bundle without authoritative travel rules: allow and warn.
- Stale rules: allow and show the last successful sync time.
- Wrong account: allow and show the binding mismatch.
- Unresolved destination: allow and classify as Unknown.
- Alternative lookup failure: preserve the block but omit the suggestion.
- Unexpected plugin exception: allow the action and write a bounded local
  diagnostic.

The last valid rules snapshot remains active after a malformed import, subject
to the normal freshness limit.

## Privacy and audit behaviour

Inventory, equipment and spellbook checks are local only. They are never
uploaded and never written to the Strict Mode audit log.

The bounded audit entry may contain:

- action family;
- normalised public target label;
- permission result;
- concise rule reason;
- whether Guardian was paused; and
- whether a verified alternative was available.

It must not contain account names, inventory contents, chat text, relay
credentials, exact routes, or unrelated gameplay data.

## Testing strategy

### Unit tests

- table-driven recognition fixtures for every supported travel family;
- Allowed, Locked and Unknown evaluation;
- the invariant that Unknown is never blocked;
- stale, malformed, legacy and wrong-account contexts;
- alternative eligibility and ranking;
- no verified alternative fallback;
- repeated-click banner refresh and chat suppression;
- shared 60-second pause and automatic resume; and
- exceptions fail open.

### Integration tests

- proven locked clicks are consumed exactly once;
- Allowed and Unknown clicks are never consumed;
- resolving and presenting an action cannot activate travel;
- walk clicks are blocked only for a known locked destination tile;
- forced movement remains warning-only; and
- Strict Mode off never invokes prevention.

### Manual RuneLite matrix

Test at least one example of each travel family with Strict Mode off, on, and
paused. Repeat with a fresh correct-account bundle, stale bundle, wrong
account, and an unauthored destination. Confirm:

- blocked actions do not reach the game;
- the banner and chat reason agree;
- chat does not flood;
- alternatives are actually usable;
- pause applies immediately and resumes automatically; and
- no action is blocked under Unknown conditions.

## Success criteria

- A proven locked travel click is stopped before reaching the game.
- The player can understand the reason without opening the side panel.
- When a verified alternative exists, the player receives one useful
  suggestion.
- Allowed and uncertain travel remains responsive.
- No new checkbox is added.
- Strict Mode and Online sync remain off by default.
- RuneLite performs no movement, travel, rolling, or other gameplay action.

## Non-goals

- Automatic pathfinding or movement
- Activating a suggested teleport or transport
- Blocking uncertain or stale decisions
- Replacing the app-owned rules engine
- Expanding equipment, banking, combat, or activity protection in this project
- Promoting detector confidence

Those Strict Mode categories can receive separate designs after travel
protection has passed its live-client safety matrix.
