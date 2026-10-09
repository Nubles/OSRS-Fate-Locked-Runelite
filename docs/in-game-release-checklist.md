# In-game release checklist

The unit tests run against mocks, so they cannot show what a player sees in a
real client. Run these checks on the exact commit you are about to pin in the
Plugin Hub, and paste the filled-in table into the release pull request.

## Setup

- `gradle runClient --no-daemon` starts RuneLite with the plugin loaded from
  source (see [CONTRIBUTING.md](../CONTRIBUTING.md#run-it-in-a-real-client)).
  A Jagex account can log in only after RuneLite's "Using Jagex Accounts"
  step (the `--insecure-write-credentials` client argument); delete
  `.runelite/credentials.properties` when you finish.
- Back up `.runelite/fate-locked`, uninstall the Plugin Hub copy of Fate
  Locked Ironman, and move any Fate Locked jar out of
  `.runelite/sideloaded-plugins`. Otherwise they run beside the source build.
- Use a tracker profile bound to the character you log in with: look the
  character up in the tracker's Wise Old Man panel, which binds the run for
  good. Without a bound account Strict Mode stays Inactive, so rows 9 and 10
  cannot pass. Have a second character ready for row 10, or import an export
  whose `rules.account` names another player.
- Start from a RuneLite profile with no Fate Locked rules loaded (move
  `saved-rules.json` out of `.runelite/fate-locked` too), and turn on **Also
  send RuneLite notifications** under Alerts in RuneLite's configuration (it
  is off by default) and RuneLite's own **Send notifications when focused**,
  so rows 4 and 5 can show them while you play.
- For row 26, keep a copy of a RuneLite profile from the Stage 2 build
  (`4c2bf97`) with some Fate Locked settings changed from their defaults.
- `gradle runClient --no-daemon --args="--developer-mode --debug"` also logs
  every chat line and notification, which makes the rows quick to confirm.

## Checks

| # | Do this | Expect this | Result |
|---|---|---|---|
| 1 | Walk across a few chunks before loading any rules. | No chunk chat, no HUD, no borders or shade on the game view or minimap, nothing on the world map. | |
| 2 | Click **Connect tracker**, accept the warning, and wait a minute before confirming in the browser. | The status card says "Waiting for confirmation" (never "No profile arrived") for up to 10 minutes. | |
| 3 | Confirm the profile in the browser. | The status card says **Rules up to date**, synced moments ago for your character; the Run card, the HUD, the borders and the world map shading appear. | |
| 4 | Walk from an unlocked area into a locked one, then through two more chunks of the same locked area, then into a different locked area next to it. | One chat line per area, saying why each is locked. The sound, the single fade around the view and the notification come once, on entering from unlocked land; the second locked area posts its chat line only. | |
| 5 | Set **Locked-area alert** to Chat and turn off **Announce every area change**, step back out, and walk into locked ground again. | One chat line for the locked area, no sound or fade, and no lines for unlocked areas. Stepping out and back in within a minute stays quiet. | |
| 6 | Walk into a dungeon the tracker names (the Karuulm Slayer Dungeon, say), then into an instance. | A chat line names the dungeon with its lock state, and the HUD's Here line names it. In the instance, the lock state is that of the chunk it copies. | |
| 7 | Teleport a few times, or cross a few loading screens, inside an area you have already visited. | The area you are in is not announced again, and gear warnings do not repeat. | |
| 8 | Gain a level just after a loading screen. | The level-up reminder appears. | |
| 9 | With Strict Mode on and rules less than 15 minutes old, teleport to a locked place: **Cast** a spellbook teleport, **Break** a tablet, or pick a place on worn jewellery. Then try **Walk here**, an NPC, a door, a bank and **Wear** on locked ground. | The teleport is stopped, with the banner and a chat line giving the tracker's reason, and it appears under Recently stopped. Nothing else is stopped. | |
| 10 | Log in on the second character and teleport once or twice. | The status card says **Different character**, and a chat line says so once, not after each loading screen. Strict Mode reads Inactive and stops nothing; no borders, shade, world map shading or tags are drawn. | |
| 11 | Remove any backup file, then click **Load newest backup file**. | "No backup file in .runelite/fate-locked, so your rules are unchanged." The borders and HUD keep working. | |
| 12 | Turn off **Online sync**, then import a rules export made more than 15 minutes ago from the clipboard. (While paired, the tracker's copy replaces an import on its next check.) | The status card says **Using a backup** and that Strict Mode doesn't use rules more than 15 minutes old; Strict Mode reads Inactive. | |
| 13 | Log out and back in. | The area you are in is announced once. | |
| 14 | Change something in the web tracker (unlock an area, say), then press **Check now** in Connection & backup. | The change shows within a few seconds, and the status card's sync time updates. | |
| 15 | Press **Re-pair tracker…**, confirm, then close the browser tab without confirming. Press **Cancel re-pairing**. | The status card says "Waiting for confirmation" for the new profile, and the overlays keep working. After Cancel it is **Rules up to date** again on the old pairing. | |
| 16 | Note the last four characters in Connection & backup's **Pairing** row, then press **Disconnect** in the web tracker. | The web tracker's pairing dialog showed the same four characters. Within a minute the status card says "Disconnected in the tracker", and **Connect tracker** pairs afresh. | |
| 17 | Close RuneLite, turn off your network, and start RuneLite again. | The status card says **Checking with the tracker**, with the saved rules' time in use until it answers, and the borders and HUD show your rules. With the network back, **Check now** confirms the same rules without importing them again. | |
| 18 | On a spellbook the run hasn't unlocked (Ancient, Lunar or Arceuus), cast a teleport to an unlocked place. | Stopped, with "Needs" and the book's unlock (e.g. "Needs Ancient Magicks"). | |
| 19 | **Break** a teleport tablet to an unlocked place with Teleport Tablets locked, then with it unlocked. | Stopped with "Needs Teleport Tablets", then let through. | |
| 20 | With Fossil Island locked, **Rub** a Digsite pendant, then pick **Fossil Island** on it. | The Rub is not stopped: its place is picked after the click. Fossil Island is stopped. | |
| 21 | With Eagles' Eyrie locked, pick **Eagles' Eyrie** on a necklace of passage. | Stopped with the tracker's reason; Jewelry Teleports decides it, not Eagle Transport. | |
| 22 | Right-click a fairy ring, a spirit tree, a charter crewmember and a boat's crew (the Port Sarim ship, say), and an NPC and an item on the ground in a locked chunk. | Options to one locked place, and the locked chunk's options, end in (Locked). Nothing is stopped. | |
| 23 | Take a boat or sail out to sea. | The HUD reads the sea's entry, with a Why line where the tracker gives a reason. | |
| 24 | Pause Strict Mode from the banner or the sidebar (or its hotkey, if set). | The HUD's Strict Mode line and the card count down from 60 seconds, and Strict Mode resumes by itself. | |
| 25 | With online sync off, import a rules export from before Stage 2 (an old backup file). | Strict Mode reads Inactive and says the rules have no travel table. | |
| 26 | Start RuneLite on the Stage 2 profile copy from the setup. | In RuneLite's configuration, each changed setting carried over: for example the HUD off or Detailed, the world map off or without contents, borders off, chat without sound. A colour picked in Stage 2 shows as Colours: Custom, still see-through. The old settings are kept, not deleted. | |
| 27 | Stand next to locked land, then walk along its edge. Set **Chunk borders in the game view** to All edges, then Chunk grid, then Off. | Locked edges are dashed over a dark underlay, lined up with the tiles, with a band of shade two tiles deep on the locked side; the minimap shows the same. All edges adds thin lines between unlocked chunks; Chunk grid draws a thin line on every chunk edge with no dashes, on the minimap too; Off draws none on the ground, and the minimap keeps the locked edges. | |
| 28 | Open the world map, pan and zoom, open and close the overview and the surface selector, and hover a locked and an unlocked chunk. | Locked land is shaded like fog and unlocked land is clear, with its edge outlined, lined up with the map's own pins. Nothing is drawn over the overview or the selector. The tooltip gives the chunk's area and status, and with contents what it holds. | |
| 29 | Set **HUD** to Compact, then Detailed, then Off, in an unlocked and a locked chunk. | Compact shows Here, Status, Why when locked, Strict Mode and the nearest bank and shop. Detailed adds progress, Keys, Fate Points and what the place holds. Off hides it. | |
| 30 | Turn on **Infoboxes**, then move one of them. | Three boxes, for Keys, Fate Points and progress, each with its own OSRS icon; each moves on its own. On the second character they disappear. | |
| 31 | Set **Colours** to Colour-blind safe, then back to Default. | The sidebar, HUD, borders, minimap and world map all change colour together, and change back. | |
| 32 | In Lumbridge Castle, open Here's Skilling, then Woodcutting; walk to Varrock and back; restart RuneLite. | Each category starts closed, saying what it holds ("3 can do · 2 locked"). Skilling lists its skills with their icons, level and cap. What you opened stays open as you walk and after the restart. | |
| 33 | In Lumbridge Castle before starting The Lost Tribe, open Here's Combat; then start the quest; then carry a lit lantern. | No row says Needs checking. The caves' monsters read Not ready, naming The Lost Tribe; once it's started the guards read Can do, and with the lantern so do the rest the quests allow. | |
| 34 | Open the world map around Lumbridge and Draynor. | The dashed line runs all the way round your unlocked land, under Wizards' Tower too; Tutorial Island is clear, outside it. | |
| 35 | In Lumbridge, open Here's Skilling and click Oak tree; then click a Fishing spot row; walk to the arrow. Click Bob's Brilliant Axes under Shops, then a row for something in the swamp caves. | The game's yellow arrow points at the nearest oak, then moves to a fishing spot that offers what the row says, and comes down when you reach it. Bob is pointed at. For the caves the card says it can't find it near you. Clicking a row twice takes the arrow down. | |
| 36 | Stand by a locked edge with trees, a fence or NPCs near it. Turn the camera right round, tilt it and zoom in and out; walk along the edge, and stand between the camera and the line. | The dashes stay where they are on the ground, like paint, as the camera moves and you walk. Trees, fences, walls, NPCs and you hide the line and the shade behind them; nothing in front of the line is drawn over. On the minimap the dashes stay put too, three to a chunk's side. | |
| 37 | In Lumbridge, walk along the river east of the castle past the fishing spots, then go into the castle, out of their sight, and click Here's Fishing spot row. Then install and turn on the Shortest Path plugin, click it again and walk to it; then restart RuneLite and click it from the castle. | The card says the minimap's arrow points the way to the nearest fishing spot you've seen, with a pin on the world map, and the minimap's arrow points toward it. With Shortest Path, its route is drawn and the card says so. Near the river the arrow moves onto the fishing spot itself, and the arrow, pin and route come down when you reach it. After the restart the plugin still knows the spot. | |
| 38 | In RuneLite's configuration, set **World map borders** to Off, then Chunk grid, then All edges, then back to Locked edges, looking at the world map each time. | Off: locked land is still shaded and the tooltip still shows, but there are no lines. Chunk grid: a faint line along every chunk edge, with no dashed line. All edges: the faint lines, with the dashed line round your unlocked land on top. Locked edges: the dashed line alone, as before. | |
| 39 | With the run linked to your character, gain a level, finish a quest or a diary tier, or get a boss kill count. | The Roll inbox card lists it as New, with a roll reminder. Nothing rolls. | |
| 40 | Click **Copy for tracker**. In the tracker's Roll Inbox, click **Paste from RuneLite**, then paste again. | The card's events turn Copied. The tracker says how many it added, then that they were already here; nothing rolls until you choose Roll. | |
| 41 | Log in on a character the run isn't linked to, then with no rules loaded. | The Roll inbox card says why RuneLite notices nothing, each time; no reminders. | |
| 42 | In a Vanilla run that has had Brutus's Standard Key, kill Brutus; then kill a boss that still has a Key to give. | Brutus's kill gets no reminder and isn't in the Roll inbox card; the other boss's kill gets both. | |
| 43 | If a pet drops while you play: the game says you have a funny feeling you're being followed, or that something is sneaking into your backpack. | The Roll inbox card lists a New pet, with a roll reminder, and Paste from RuneLite in the tracker asks which pet it was. A pet you already own adds nothing. | |
| 44 | In Lumbridge with no Woodcutting tier, right-click a tree and click **Chop down**; click it again at once. Then right-click a cow. | Chop down ends in (Locked), and the click gives one chat line naming the tier it needs; the second click gives none. The cow's options aren't tagged. | |
| 45 | With online sync on, roll an area in the tracker, then press **Check now**. Open the world map, then walk into the new area. | A chat line names what opened and a "Fate unlocked" banner shows it for a few seconds, then fades. Its chunks glow gold on the world map; each stops glowing once you stand in it. With **Announce new unlocks** off, none of this happens. | |
| 46 | In Lumbridge Castle, look at the bank booths, Bob, the trees outside, the hops patch across the river to the north, and the spiders and cows; walk a few chunks with monsters about; turn **Outline locked things** off and on, then each setting under it, and **Outline open ones too**. | The booths and Bob are outlined in red; the trees you can chop in red if Woodcutting isn't unlocked, or orange if it is but its tier is too low; the hops patch, if Hops patches aren't unlocked, as a steady red square on the ground; the cows and spiders in green. No words show over anything, trees you can't chop aren't outlined, a monster's or person's outline stays on steadily, including while the camera moves and as they walk between chunks, and nobody upstairs is outlined on the ground floor. Off hides every outline; each setting hides its kind; turning open ones off leaves only the red and orange. Locked land across a border is not outlined. | |

Record the commit, the RuneLite version, the date, and a pass or fail (with a
note for any fail) in each row. Any fail blocks the release until it is fixed
or explained in the pull request.
