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
  `saved-rules.json` out of `.runelite/fate-locked` too), and turn
  on **Send RuneLite notifications** in the plugin settings (it is off by
  default) and RuneLite's own **Send notifications when focused**, so rows
  4 and 5 can show them while you play.
- `gradle runClient --no-daemon --args="--developer-mode --debug"` also logs
  every chat line and notification, which makes the rows quick to confirm.

## Checks

| # | Do this | Expect this | Result |
|---|---|---|---|
| 1 | Walk across a few chunks before loading any rules. | No chunk chat, no HUD, no chunk tint on the game view or minimap. | |
| 2 | Click **Connect tracker**, accept the warning, and wait a minute before confirming in the browser. | The status says "Confirm in browser" (never "expired") for up to 10 minutes. | |
| 3 | Confirm the profile in the browser. | **Connected**, with Last sync in your own time zone; the run, the HUD and the chunk overlays appear. | |
| 4 | Walk from an unlocked chunk into a locked one, then through two more locked chunks. | A chat line per chunk. The warning sound, red flash and notification come once, on the first locked chunk. | |
| 5 | Turn off **Chat on chunk entry**, step back out, and walk into locked ground again. | No chat, but the sound and notification still come once. | |
| 6 | Walk into a dungeon the tracker names (the Karuulm Slayer Dungeon, say), then into an instance. | A chat line names the dungeon with its lock state, and the HUD's Here line names it. In the instance, the lock state is that of the chunk it copies. | |
| 7 | Teleport a few times, or cross a few loading screens, inside an area you have already visited. | The chunk you are in is not announced again, and gear warnings do not repeat. | |
| 8 | Gain a level just after a loading screen. | The level-up nudge appears. | |
| 9 | With Strict Mode on and rules less than 15 minutes old, teleport to a locked place: **Cast** a spellbook teleport, **Break** a tablet, or pick a place on worn jewellery. Then try **Walk here**, an NPC, a door, a bank and **Wear** on locked ground. | The teleport is blocked, with a chat line giving the tracker's reason and an entry under Recent prevented actions. Nothing else is blocked. | |
| 10 | Log in on the second character and teleport once or twice. | The bound-account warning shows once, not after each loading screen. Strict Mode reads Inactive ("the profile is for …; you're logged in as …") and blocks nothing. | |
| 11 | Remove any bundle file, then click **Load newest backup file**. | "no backup file in .runelite/fate-locked — rules unchanged", and the overlays keep working. | |
| 12 | Turn off **Enable online sync**, then import a rules export made more than 15 minutes ago from the clipboard. (While paired, the tracker's copy replaces an import on its next check.) | The connection reads "Online sync is off" and says your pairing is kept. The import works; Strict Mode reads Inactive ("the rules are more than 15 minutes old"). | |
| 13 | Log out and back in. | The chunk you are in is announced once. | |
| 14 | Change something in the web tracker (unlock an area, say), then press **Check now** in the sidebar. | The change shows within a few seconds and Last sync updates. | |
| 15 | Press **Re-pair tracker…**, confirm, then close the browser tab without confirming. Press **Cancel re-pairing**. | The sidebar says "Confirm the new pairing" and the overlays keep working. After Cancel it is **Connected** again on the old pairing. | |
| 16 | Note the last four characters in the sidebar's **Pairing** row, then press **Disconnect** in the web tracker. (Needs the relay deployed with the gone marker.) | The web tracker's pairing dialog showed the same four characters. Within a minute the sidebar says "Disconnected in the web tracker", and **Connect tracker** pairs afresh. | |
| 17 | Close RuneLite, turn off your network, and start RuneLite again. | "saved rules from" and a time appear, and the overlays and HUD show your rules; the connection says it couldn't reach the tracker. With the network back, **Check now** confirms the same rules without importing them again. | |
| 18 | On a spellbook the run hasn't unlocked (Ancient, Lunar or Arceuus), cast a teleport to an unlocked place. | Blocked, with "Needs" and the book's unlock (e.g. "Needs Ancient Magicks"). | |
| 19 | **Break** a teleport tablet to an unlocked place with Teleport Tablets locked, then with it unlocked. | Blocked with "Needs Teleport Tablets", then let through. | |
| 20 | With Fossil Island locked, **Rub** a Digsite pendant, then pick **Fossil Island** on it. | The Rub is not blocked: its place is picked after the click. Fossil Island is blocked. | |
| 21 | With Eagles' Eyrie locked, pick **Eagles' Eyrie** on a necklace of passage. | Blocked with the tracker's reason; Jewelry Teleports decides it, not Eagle Transport. | |
| 22 | Right-click a fairy ring, a spirit tree, a charter crewmember and a boat's crew (the Port Sarim ship, say). | Options to one locked place show (LOCKED). None is blocked. | |
| 23 | Take a boat or sail out to sea. | The HUD reads the sea's entry, with a Why line where the tracker gives a reason. | |
| 24 | Pause Strict Mode from the banner or the sidebar (or its hotkey, if set). | The HUD's Strict line counts down from 60 seconds, and Strict Mode resumes by itself. | |
| 25 | With online sync off, import a rules export from before this release (an old backup file). | Strict Mode reads Inactive: "the rules have no travel table; sync them from the tracker again". | |

Record the commit, the RuneLite version, the date, and a pass or fail (with a
note for any fail) in each row. Any fail blocks the release until it is fixed
or explained in the pull request.
