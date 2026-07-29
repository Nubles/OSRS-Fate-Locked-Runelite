# Unified Fate Locked Plugin Hub Design

**Date:** 2026-07-27  
**Status:** Approved visual design  
**Repository:** `Nubles/RS3-Fate-Locked-Runelite`

## Goal

Ship one Fate Locked Plugin Hub plugin that combines the current Travel Guardian
with the seamless tracker-pairing work. The installed plugin must expose its
complete everyday interface in one scrollable RuneLite sidebar. Users must not
switch between an old plugin and a new plugin, nor visit a separate settings
surface to reach a feature.

The update keeps all relevant existing controls, removes the obsolete manual
online-sync controls, and continues to follow RuneLite and Jagex requirements:
no automation, no uncertain blocking, no credentials, and no runtime execution
of unreviewed code.

## Approved product decisions

- There is one plugin descriptor, toolbar entry, sidebar panel, and Plugin Hub
  listing.
- The current Hub Travel Guardian remains intact.
- The one-click `Connect tracker` flow is added to that same plugin.
- The Fate Locked sidebar is the complete primary interface.
- Connection, current chunk, Guardian, Roll Inbox, run information, bundle
  tools, warnings, and rendering controls are available in that sidebar.
- Each major group is expandable and collapsible.
- The three legacy settings `Enable online sync`, `Online sync code`, and
  `Relay URL` are removed from the visible and supported configuration.
- `Connect tracker` is the only supported tracker-connection method.
- The fixed production tracker and relay endpoints are owned by the plugin;
  users do not enter URLs or pairing codes.
- The remaining 30 settings retain their current keys and meanings so existing
  preferences carry forward.
- The plugin does not roll, travel, move, choose an alternative, or perform any
  other gameplay action.

## Single-panel user experience

The sidebar is a vertical, scrollable panel with the following order.

### Persistent header

The top of the panel contains:

- `Open web tracker`;
- `Connect tracker`;
- tracker connection status;
- connected account label when available; and
- last successful sync time.

The connection action changes its label and enabled state to communicate
`Connect tracker`, `Connecting…`, `Connected`, or `Reconnect tracker`. Starting
a new connection always requires a user click. A successful new pairing
replaces any previous internal pairing material.

### Expandable sections

1. **Current chunk**
   - current area and entry status;
   - allowed, not-ready, and locked counts; and
   - the existing detailed permission rows.
2. **Guardian**
   - Strict Mode toggle;
   - active or paused state;
   - `Pause Guardian — 60 seconds` / resume action; and
   - expandable recent prevented actions.
3. **Roll Inbox**
   - queued, needs-review, warning, and last-sync values; and
   - `Open Roll Inbox`.
4. **Run**
   - profile, account, run ID, keys, fate, active buff, and current goal.
5. **Bundle**
   - import from clipboard;
   - reload saved bundle;
   - auto-reload on change; and
   - re-import hotkey.
6. **Warnings**
   - all 15 existing warning controls.
7. **Rendering**
   - all eight existing rendering toggles; and
   - all four existing colour controls.

Current chunk and Guardian start expanded. The remaining sections start
collapsed to keep the initial view compact. The open/closed state lasts for the
life of the panel, matching the existing collapsible-panel behaviour; it does
not add new persisted preferences.

The existing RuneLite configuration keys remain the persistence contract
underneath the controls. No capability is available only through a separate
configuration view. If RuneLite exposes a secondary native configuration view,
it must mirror the same values and must never diverge from the sidebar.

## Visible configuration inventory

The unified plugin has 30 retained settings.

### Bundle — 2

- Auto-reload on change
- Re-import hotkey

### Warnings — 15

- Chat on chunk entry
- Warn entering locked chunk
- Warn opening a locked bank
- Screen flash on locked entry
- Warn on wrong account
- Tag locked right-click targets
- Tag teleports to locked chunks
- Show in-game HUD
- HUD: nearest bank & shop
- Show “in this chunk” box
- Send RuneLite notifications
- Warn on locked slayer task
- Warn on over-tier gear
- Show key/fate/progress infoboxes
- Roll reminders

### Guardian — 1

- Strict Mode

### Rendering — 12

- Draw on world map
- Draw around player
- Draw on minimap
- Highlight locked borders
- Shade nearby locked chunks
- Pin locked areas on world map
- World map hover tooltip
- Tooltip: what’s in the chunk
- Unlocked colour
- Frontier colour (Chunked)
- Locked colour
- Unauthored colour

## Component design

### `FateLockedPanel`

Remains the composition root for the single sidebar. It owns layout and
presentation only. The panel delegates tracker connection, configuration
updates, Guardian state, and bundle import to focused collaborators.

### Collapsible section component

A small reusable Swing component provides a header, open/closed indicator, and
body container. It replaces one-off collapse code so every section behaves
consistently without growing `FateLockedPanel` into an unmaintainable class.

### Panel configuration binder

A focused binder:

- renders the 30 retained settings with RuneLite-compatible controls;
- reads their current values through `FateLockedConfig`;
- writes changes through `ConfigManager`;
- listens for `ConfigChanged` so changes from profiles or a secondary native
  configuration view update the panel; and
- prevents feedback loops when reflecting a change.

Existing key names remain unchanged. The three retired sync keys are neither
rendered nor consulted by normal operation.

### Tracker pairing controller

A dedicated controller owns the connection state machine and isolates relay
logic from Swing. It exposes immutable display states to the panel:

- disconnected;
- preparing pairing;
- waiting for browser;
- connected;
- expired;
- relay unavailable; and
- retry required.

The controller integrates the one-click pairing work while preserving the
current Hub branch’s stronger relay validation and trust boundaries.

### Existing Guardian components

Travel resolution, permission evaluation, legal-alternative selection, pause
state, audit logging, and the blocked-travel presenter remain separate from the
panel and pairing controller. The merge must preserve the current safe
invariant:

- only actions proven locked by fresh, account-bound rules may be blocked;
- allowed and unknown actions are never blocked;
- walking is never blocked;
- the suggested alternative is informational only; and
- any unexpected evaluation error fails open.

## Pairing and sync flow

### First connection

1. The user clicks `Connect tracker`.
2. RuneLite creates a short-lived pairing request.
3. RuneLite opens the production web tracker in the user’s browser with that
   request.
4. The web app confirms the request through the production relay.
5. RuneLite accepts only the response matching the active request.
6. The returned bundle is validated and imported transactionally.
7. Only a successful import is acknowledged.
8. Internal pairing material is stored under a non-visible configuration key.
9. The panel changes to `Connected` and displays the last successful sync.

The web app and RuneLite may run on the same PC, but they communicate through
the reviewed HTTPS pairing protocol. The plugin does not start a local web
server, execute a helper program, or require browser extensions.

### Later updates

With a valid internal pairing identity, the plugin checks the relay using the
existing bounded polling schedule. Responses are serialized. Stale callbacks
from an older request are ignored. A bundle becomes active only after complete
validation; a failed import leaves the last valid bundle unchanged and receives
no success acknowledgement.

### Legacy settings

The old `onlineSync`, `syncCode`, and `relayUrl` values do not initiate network
activity and are not used as a fallback. This avoids silently preserving a
custom endpoint or connection consent from the retired workflow.

After a successful new pairing, the plugin may clear those obsolete keys as
one-way housekeeping. Until then they are harmless ignored data. There is no
automatic migration that could contact a relay without a fresh user action.

## Guardian flow

1. RuneLite emits a user action.
2. Existing Guardian resolvers attempt to identify it.
3. The evaluator checks freshness, account binding, bundle version, and the
   exact rule.
4. Allowed and unknown actions continue unchanged.
5. A proven locked action is consumed and recorded.
6. The in-game presenter shows the reason and, when verified, the nearest legal
   alternative.
7. The sidebar Guardian section shows the same pause state and recent history.

The in-game alert is the only element outside the sidebar because it must appear
at the point of a blocked action. All controls and history remain in the one
sidebar panel.

## Error handling

- Browser launch failure: keep the pairing request visible as retryable and
  show a concise panel error.
- Pairing expiry: return to `Connect tracker` without altering the active
  bundle.
- Relay timeout or unavailable service: keep the last valid bundle and offer
  `Reconnect tracker`.
- Mismatched or stale relay response: ignore it without acknowledging it.
- Invalid or wrong-account bundle: reject it transactionally and retain the
  previous bundle.
- Panel update after shutdown: discard it rather than touching disposed Swing
  state.
- Config write failure: restore the displayed control to the last confirmed
  value and show a bounded status message.
- Guardian exception or uncertain decision: allow the gameplay action and emit
  a bounded local diagnostic.

Network errors and validation failures must not disable local warnings,
rendering, or Guardian behaviour backed by the last valid fresh bundle.

## RuneLite and Jagex compliance

- The plugin remains Java-only.
- It uses RuneLite APIs, Swing, and reviewed HTTPS requests only.
- It does not use reflection, JNI, subprocesses, browser automation, or
  runtime-downloaded code.
- External links open only from explicit user actions.
- The relay address is fixed and HTTPS-only.
- No Jagex, RuneLite, or tracker account credentials are requested or stored.
- No inventory, equipment, chat, or unrelated gameplay data is uploaded.
- The plugin performs no clicks, movement, travel, rolling, menu selection, or
  other gameplay automation.
- Guardian prevents only a proven rule-breaking action and fails open on
  uncertainty.
- The Plugin Hub submission remains auditable as one source repository and one
  plugin artifact.

Relevant official references:

- [RuneLite Developer Guide](https://github.com/runelite/runelite/wiki/Developer-Guide)
- [RuneLite configuration panels](https://github.com/runelite/runelite/wiki/Creating-plugin-config-panels)
- [Rejected or rolled-back features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)

## Merge strategy

The current Hub/Travel Guardian branch is the base. Pairing is integrated at
the feature level instead of replacing whole files from the pairing branch.

The merge must resolve the overlapping files deliberately:

- `FateLockedPlugin.java`
- `FateLockedPanel.java`
- `FateLockedPanelStatusTest.java`

The unified version preserves:

- the current branch’s Travel Guardian, strict trust checks, presenter, legal
  alternatives, audit behaviour, and pause controls;
- the pairing branch’s one-click browser flow, detailed connection states,
  safe import rollback, serialized response effects, stale-callback rejection,
  success-only acknowledgement, and blank-panel sizing fix; and
- one shared panel/status model rather than choosing either branch’s file
  wholesale.

## Testing strategy

### Configuration and panel tests

- exactly 30 retained settings are represented in the sidebar;
- no legacy manual-sync control is rendered;
- every section expands and collapses independently;
- initial expanded/collapsed states match the design;
- each toggle, keybind, and colour control writes the correct existing key;
- `ConfigChanged` updates the matching panel control;
- panel and secondary native configuration values cannot diverge;
- all current chunk, Guardian, Roll Inbox, run, and bundle content remains
  reachable in the same panel; and
- the panel is non-blank and usable at RuneLite’s normal sidebar width.

### Pairing tests

- user click creates one pairing request and opens the expected production URL;
- connection-state transitions are deterministic;
- expired, mismatched, and stale responses are ignored;
- relay effects are serialized;
- only a successfully validated and imported bundle is acknowledged;
- a failed import preserves the previous bundle and visible state;
- repeated Connect replaces the previous in-flight request safely; and
- shutdown prevents late callbacks from updating the panel.

### Guardian regression tests

- all current known travel families retain coverage;
- only proven locked actions are consumed;
- Allowed, Unknown, stale, malformed, wrong-account, and walking actions remain
  unblocked;
- verified alternatives are informational and never activated;
- pause and automatic resume remain shared across Guardian;
- the in-game reason and panel audit entry agree; and
- Strict Mode off never invokes prevention.

### End-to-end checks

- build and tests pass using the Plugin Hub-supported Java and Gradle versions;
- the plugin installs as one artifact with the existing identity;
- existing non-sync preferences survive the update;
- RuneLite and the deployed GitHub web app pair on the same PC;
- reconnect and browser-launch failure are understandable without editing
  settings; and
- the Plugin Hub packaging and review checks pass.

## Success criteria

- A Plugin Hub user installs or updates one Fate Locked plugin.
- The complete plugin is accessible through one scrollable sidebar.
- Every major group can be expanded or collapsed.
- The 30 retained preferences keep their values.
- `Connect tracker` completes pairing without copying a code or editing a URL.
- The current Travel Guardian behaviour and safety invariants are unchanged.
- A bad or stale network response cannot replace a valid bundle.
- No gameplay action is automated.
- The implementation passes the local test suite and Plugin Hub checks.

## Non-goals

- A second “new” Fate Locked plugin
- A companion desktop executable or local web server
- Manual relay configuration
- Manual pairing-code entry
- Automatic travel, movement, rolling, or menu actions
- Expanding Guardian beyond the behaviour already implemented on the current
  Hub branch
- Reworking the web tracker beyond the minimum pairing-protocol compatibility
  needed for this unified plugin
