# Plugin Hub review notes

The plugin is on the Plugin Hub, which builds commit `0ae842d` (new pets in
the Roll inbox, 5 October 2026, runelite/plugin-hub#17794). These notes describe
`main` for reviewers of the next update and do not claim approval of any
change made since that commit.

Official references checked while preparing this candidate:

- [Plugin Hub submission and review process](https://github.com/runelite/plugin-hub#reviewing)
- [Rejected or rolled-back features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
- [Jagex third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)

## What Stage 4 changes

Stage 4 changes what the plugin notices and lets the player hand to the
tracker, not what it can block. Strict Mode stops the same travel as before;
only its descriptions say more exactly what that is. For review:

- **No new network.** The one request and its consent are as below.
- **One clipboard write, on the player's click.** The Roll inbox card lists
  what RuneLite noticed, with **Copy for tracker**. On that click, on the
  Swing thread, the plugin writes those events as text to the system
  clipboard (`setContents`), its only clipboard write
  (`ClipboardBoundaryTest`); an event turns Copied only after the write
  succeeds. The player pastes them into the tracker's Roll Inbox; nothing is
  sent, and nothing rolls until the player chooses Roll there.
- **It notices from what the client already shows.** Levels (`StatChanged`),
  quests (RuneLite's quest states, read when the quest-complete scroll opens
  and every 100 ticks), finished diary tiers (varbits), combat tasks and
  collection log items (their chat lines and popups), clue, boss and raid
  counts and Slayer tasks (chat lines and vars), and a new pet (the game's
  two chat lines for one; it doesn't say which pet, so the tracker asks). It
  reads; it never clicks, moves or changes anything.
- **Who it notices for.** Only with a run's rules loaded, on worlds that
  save to the account: the character the rules are bound to, with a roll
  reminder; any character while the rules are bound to no one, recorded for
  the card without reminders; nobody else. Kills of a boss the tracker says
  can't roll again (a Vanilla boss with no Standard Keys left) aren't
  recorded.
- **Local files.** Per account: `detected-events.json` (New, Copied or
  Dismissed; 30 days; the newest 250), and `quests.json` and
  `diary-tiers.json`, so a finish isn't noticed twice. They're written only
  when something changes. Events from before Stage 4 are never offered.
- **Display.** World map borders and Chunk borders in the game view each
  choose Off, Locked edges, Chunk grid or All edges. Chunk grid draws the
  faint line on every chunk edge without the dashed one; the drawing is as
  Stage 3 describes.

## What Stage 3 changes

Stage 3 changes what the plugin shows, not what it can block. Strict Mode is
unchanged. For review:

- **No new network.** The one request and its consent are as below. One
  local file is new, `spots.json`, under Local data.
- **Drawing stays inside what the game shows.** Chunk borders are drawn tile
  by tile within 32 tiles of the player, inside the loaded scene, with no
  chunk fills; a short band of shade marks the locked side. The world map is
  drawn only inside the map, never over its overview or surface selector, and
  its projection, clip and outline are kept until the map moves or the rules
  change. Nothing is drawn for another character's rules.
- **What stands in front of a border isn't drawn over.** RuneLite draws
  overlays over the finished scene, so each frame the game view reads the
  outline (`getConvexHull`) of the players, NPCs, objects, walls and wall
  decorations that stand between the camera and the locked edges or their
  shade, and leaves out the pieces of line and shade behind them. Only what
  stands in that corridor is read, never the whole scene, and nothing at all
  while the borders and shade are off or no locked edge is within 32 tiles.
- **Less per-frame work.** The overlays draw models worked out when something
  changes, not each frame, but for those outlines, which move with the
  camera. The menu tag skips Walk here, Cancel, Examine and
  player options before reading them, and `A9PerformanceTest` pins that the
  world map loop and that path allocate nothing.
- **The menu tag only appends text:** " (Locked)", in the palette's colour.
- **Here reads the game to decide its rows, nothing more.** Where the tracker
  can't see a requirement, the plugin reads, on the client thread, once a
  tick and only while standing where such a row is: the state of the quests
  those rows name (RuneLite's `Quest.getState`), quest points, real levels,
  whether the world is members, and the names of the items carried or worn.
  It only decides which word the sidebar shows; nothing is stored or sent.
- **A clicked Here row puts up the game's own hint arrow, nothing more.** On the
  player's click, the plugin looks through the loaded scene, inside the chunk
  they stand in and placed through `ChunkLocator`, for the nearest object or NPC
  by that row's name, and calls `Client.setHintArrow`. The arrow comes down on
  arrival, on a second click, on leaving the chunk or on shutdown, and an arrow
  the game or another plugin put up is never taken down. Nothing moves the
  player or clicks for them.
- **With none loaded, the way to one seen before.** The plugin notes where
  the NPCs and objects a Here row names load (`NpcSpawned` and the object
  spawn events), in the real world only, never in an instance or on a boat,
  and keeps the spots in `spots.json`. A click with none loaded puts the hint
  arrow on the nearest spot seen in that chunk (`setHintArrow(WorldPoint)`),
  whose minimap arrow shows the way, and adds a world map pin with the game's
  own destination flag. When the Shortest Path plugin is running
  (`PluginManager.isPluginActive`), it is sent the `PluginMessage` Quest
  Helper sends: "shortestpath", "path" with the player's tile and the spot,
  and "clear" when the arrow comes down. The way stays up from chunk to
  chunk until it is reached, cleared, replaced or the player logs out; the
  arrow moves onto the thing when it loads, and a spot found empty is
  forgotten.
- **Alerts are calmer.** A locked area posts one chat line and, on arrival
  from unlocked land, a sound and a single 1.1-second fade. The old red border
  pulsed at 2.5 Hz; it is gone.
- **Settings moved to RuneLite's configuration.** A one-time migration maps
  the retired settings to the new ones; the retired keys stay readable for a
  release, so a rollback finds them.

## Network boundary

The plugin constructs one request:

```text
GET https://fate-relay.fatelocked.workers.dev/r/<32-lowercase-hex-code>
```

It may attach `If-None-Match`. It has no other HTTP method or host, no local
server, and no gameplay-data upload. The response must parse as a complete
non-legacy v4 bundle before the active rules are replaced. The random code is
generated by RuneLite and handed to the fixed GitHub Pages companion.

The request has a 20-second limit on the whole call and reads at most 1 MiB
of reply; stopping the plugin, withdrawing consent or re-pairing cancels it.
It is sent every minute while logged in, every 5 minutes at the login screen,
at once on login, and when the player presses **Check now** (at most every
10 seconds). Failures retry within 5 minutes, and the relay's `Retry-After`
is honoured, up to an hour. A 404 reply's body is read only for the relay's
`{"gone":true}` marker, which says the owner disconnected the code in the web
tracker.

Online sync is default-off behind a separate `trackerNetworkAccess` setting.
The Connect action, sidebar setting, and RuneLite config warning disclose that
the third-party server receives the user's IP address and is not controlled or
verified by RuneLite developers. Declining leaves sync disabled; existing
pairings also require consent. Disabling the setting blocks subsequent polls
and invalidates pending imports. Clipboard and file imports remain local.

Detected events stay in a bounded local history. They leave it only when the
player clicks Copy for tracker, by the clipboard. The web Roll Inbox link does
not include the pairing code and does not transfer that history.

## Local data

All files stay in RuneLite's own `fate-locked` data directory: the last
accepted rules (`saved-rules.json`, tagged with a hash of the pairing, never
the code), and per OSRS account, in `accounts/<account hash>/`, the detected
events, the Strict Mode audit log and the finished quests and diary tiers. `spots.json` holds where the things the Here card can point at
were seen, by chunk and row, at most eight tiles a row, and is written at most
every half minute. Two RuneLites sharing the folder merge their writes under a lock
file rather than overwrite each other. Detections are recorded only on
worlds that save to the account (not Leagues, Deadman, speedrunning and
similar worlds): with roll reminders for the character the rules are bound
to, and without, for any character, while they're bound to no one.

## Strict Mode pre-clearance request

Strict Mode is off by default. It does not remove, reorder or create menu
entries and never performs an action. It consumes a user-selected click only
when the travel table the tracker sends matches it by id (the active
spellbook and a spell's name, or a tablet, scroll or teleport item's item
id), the option goes to one destination, and fresh rules bound to the
logged-in character lock that trip: the destination, or the unlock the trip
needs, is locked. It never consumes walking, NPC, object (including doors,
stairs and ladders), bank or equipment clicks; those get only the passive
(Locked) menu tag and chat warnings. Fairy rings, spirit trees, gliders,
charters, boats and the other networks are matched too, and an option to one
locked place is tagged, but they are never blocked. Because blocking travel
is behaviorally adjacent to conditional menu-entry restrictions, we request
reviewer pre-clearance and do not claim that this behavior is already
approved.

What Stage 2 (`4c2bf97`) changed: the build before it, `4e37895`, recognised
travel from menu text, with a table of places inside the plugin, and checked
an unlock it guessed from the text. Stage 2 matches by id against the
tracker's table, which names each trip's unlock and where each option can
go, and blocks nothing that can go to several places. The code keeps limits
the table can't widen: options that are never travel are dropped, and the
table has caps. The tags and Strict Mode read the same decision, and both
now cover dungeons and other interiors the tracker names, and instances,
judged as the chunk they copy.

Builds up to and including `52f45f5` also consumed NPC, object, bank and
Wear/Wield clicks through an older generic guard, and read "Walk here" menu
parameters as a tile. Both are removed.

Strict Mode fails open when:

- Strict Mode is disabled or its 60-second pause is active;
- the bundle is missing, invalid, legacy, future, or stale, or has no travel
  table;
- the rules have no bound account, or it does not match the logged-in
  character;
- the click is walking, or an NPC, object, bank or equipment option;
- the tracker's table doesn't match the click by id, or two of its methods
  do;
- the option can go to several places, or its method is advisory;
- the tracker's decision is Allowed, Not ready or Unknown; or
- evaluation throws or required client state is unavailable.

The sidebar shows whether Strict Mode is Active, Paused, Off, or Inactive and
why. The pause resumes automatically. Blocked clicks produce only a local
explanation and bounded local audit record. Suggested alternatives are
display-only and are never activated. `PluginHubClickBoundaryTest` pins the
single place a click can be consumed.

## Reviewability

- Java 11 and standard Plugin Hub build.
- No shaded dependencies.
- No reflection, JNI, subprocesses, dynamic class loading, or embedded server.
- No arbitrary downloads or runtime code.
- One plugin descriptor, one main plugin class, one panel, and one navigation
  button.
- Source and jar gates run from `gradle clean check --no-daemon`.
