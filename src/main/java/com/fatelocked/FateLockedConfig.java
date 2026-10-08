package com.fatelocked;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

/**
 * Fate Locked's settings, in RuneLite's configuration only (Decision 4, U11). The sidebar
 * keeps just Strict Mode's switch and online sync's, which carries the player's consent.
 *
 * <p>Stage 3 merged 31 settings into these. A changed setting has a new key, never an old
 * key with a new type: RuneLite overwrites a value it can't read with the default before
 * the plugin starts. {@link SettingsMigration} carries each player's old choices over.
 */
@ConfigGroup(FateLockedConfig.GROUP)
public interface FateLockedConfig extends Config
{
    String GROUP = "fatelocked";
    String NETWORK_ACCESS_KEY = "trackerNetworkAccess";
    String NETWORK_WARNING = "This feature submits your IP address to a 3rd-party server "
        + "not controlled or verified by RuneLite developers.";

    /** When a locked area is entered: which of chat, a sound and a screen fade say so. */
    enum LockedAreaAlert
    {
        OFF("Off", false, false, false),
        CHAT("Chat", true, false, false),
        CHAT_FADE("Chat and fade", true, false, true),
        CHAT_SOUND("Chat and sound", true, true, false),
        CHAT_SOUND_FADE("Chat, sound and fade", true, true, true);

        private final String label;
        private final boolean chat;
        private final boolean sound;
        private final boolean fade;

        LockedAreaAlert(String label, boolean chat, boolean sound, boolean fade)
        {
            this.label = label;
            this.chat = chat;
            this.sound = sound;
            this.fade = fade;
        }

        public boolean chat()
        {
            return chat;
        }

        public boolean sound()
        {
            return sound;
        }

        public boolean fade()
        {
            return fade;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    enum HudMode
    {
        OFF("Off"),
        /** Here, its status and why, Strict Mode, and the nearest bank and shop. */
        COMPACT("Compact"),
        /** Compact, and what the chunk holds, with progress. */
        DETAILED("Detailed");

        private final String label;

        HudMode(String label)
        {
            this.label = label;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    enum WorldMapMode
    {
        OFF("Off", false, false, false),
        SHADING("Shading", true, false, false),
        SHADING_TOOLTIP("Shading and tooltip", true, true, false),
        SHADING_TOOLTIP_CONTENTS("Shading, tooltip and contents", true, true, true);

        private final String label;
        private final boolean shading;
        private final boolean tooltip;
        private final boolean contents;

        WorldMapMode(String label, boolean shading, boolean tooltip, boolean contents)
        {
            this.label = label;
            this.shading = shading;
            this.tooltip = tooltip;
            this.contents = contents;
        }

        public boolean shading()
        {
            return shading;
        }

        public boolean tooltip()
        {
            return tooltip;
        }

        public boolean contents()
        {
            return contents;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    enum ChunkBorders
    {
        OFF("Off", false, false),
        /** The dashed line where unlocked land meets locked land. */
        LOCKED_EDGES("Locked edges", true, false),
        /** A plain line on every chunk edge, without the dashed one. */
        CHUNK_GRID("Chunk grid", false, true),
        /** The chunk grid, with the dashed line over it. */
        ALL_EDGES("All edges", true, true);

        private final String label;
        private final boolean locked;
        private final boolean grid;

        ChunkBorders(String label, boolean locked, boolean grid)
        {
            this.label = label;
            this.locked = locked;
            this.grid = grid;
        }

        /** Whether the dashed locked edges show. */
        public boolean locked()
        {
            return locked;
        }

        /** Whether a plain line shows on every chunk edge. */
        public boolean grid()
        {
            return grid;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    enum ColourPreset
    {
        DEFAULT("Default"),
        COLOUR_BLIND_SAFE("Colour-blind safe"),
        CUSTOM("Custom");

        private final String label;

        ColourPreset(String label)
        {
            this.label = label;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    @ConfigSection(
        name = "Tracker",
        description = "Getting your run's rules from the Fate Locked web tracker",
        position = 0
    )
    String trackerSection = "trackerSection";

    @ConfigItem(
        keyName = NETWORK_ACCESS_KEY,
        name = "Online sync",
        description = "Get your rules from the Fate Locked relay. Off by default; imports from the clipboard and backup"
            + " files work without it.",
        section = trackerSection,
        position = 0,
        warning = NETWORK_WARNING
    )
    default boolean trackerNetworkAccess()
    {
        return false;
    }

    @ConfigSection(
        name = "Strict Mode",
        description = "Stops teleports your rules don't allow",
        position = 1
    )
    String strictModeSection = "strictModeSection";

    @ConfigItem(
        keyName = "strictMode",
        name = "Strict Mode",
        description = "Stops a teleport it recognises exactly, with one place it can go, when fresh rules for this"
            + " character lock that place. It also stops one to an unlocked place when you haven't unlocked that kind"
            + " of teleport, such as Teleport Tablets, Jewelry Teleports or a spellbook. A worn item's teleport, such"
            + " as a glory's Edgeville, counts. Walking, NPCs, objects, banks and putting on gear are never stopped."
            + " Off by default.",
        section = strictModeSection,
        position = 0
    )
    default boolean strictMode()
    {
        return false;
    }

    @ConfigItem(
        keyName = "pauseStrictModeHotkey",
        name = "Pause hotkey",
        description = "Pauses Strict Mode for 60 seconds. Not set by default.",
        section = strictModeSection,
        position = 1
    )
    default Keybind pauseStrictModeHotkey()
    {
        return Keybind.NOT_SET;
    }

    @ConfigSection(
        name = "Alerts",
        description = "What tells you about locked areas and your rules in game",
        position = 2
    )
    String alertsSection = "alertsSection";

    @ConfigItem(
        keyName = "lockedAreaAlert",
        name = "Locked-area alert",
        description = "When you walk into a locked area: a chat line, plus a sound and a short screen fade if you pick"
            + " them, which come only when you arrive from unlocked land. The same area stays quiet for a minute"
            + " after it alerts.",
        section = alertsSection,
        position = 0
    )
    default LockedAreaAlert lockedAreaAlert()
    {
        return LockedAreaAlert.CHAT_SOUND_FADE;
    }

    @ConfigItem(
        keyName = "announceAreaChanges",
        name = "Announce every area change",
        description = "A chat line when you walk into another area the tracker maps. Locked areas follow the"
            + " Locked-area alert instead.",
        section = alertsSection,
        position = 1
    )
    default boolean announceAreaChanges()
    {
        return true;
    }

    @ConfigItem(
        keyName = "ruleWarnings",
        name = "Rule warnings",
        description = "Chat warnings for a bank you haven't unlocked, a Slayer task in locked areas, and gear above"
            + " your unlocked tier; the Slayer and gear ones also stay on the HUD. Each needs your rules to cover it.",
        section = alertsSection,
        position = 2
    )
    default boolean ruleWarnings()
    {
        return true;
    }

    @ConfigItem(
        keyName = "tagLockedOptions",
        name = "Tag locked right-click options",
        description = "Adds (Locked) to right-click options for NPCs, objects, items on the ground and teleports your"
            + " rules lock, and to skilling spots your skill tier doesn't open yet. Clicking one of those says in chat"
            + " which tier it needs.",
        section = alertsSection,
        position = 3
    )
    default boolean tagLockedOptions()
    {
        return true;
    }

    @ConfigItem(
        keyName = "rollNudges",
        name = "Roll reminders",
        description = "A chat line when RuneLite adds something to your Roll inbox: a level, a quest, a finished diary"
            + " tier (not each task), a combat task, a clue scroll, a boss or raid kill, a collection log item, a"
            + " Slayer task or a new pet. Collection log items need the game's own collection log notification, in"
            + " chat or as a popup. Only on the character your run is linked to.",
        section = alertsSection,
        position = 4
    )
    default boolean rollNudges()
    {
        return true;
    }

    @ConfigItem(
        keyName = "useNotifier",
        name = "Also send RuneLite notifications",
        description = "Also send a RuneLite notification, as your RuneLite settings deliver them, with each locked-area"
            + " alert's chat line, each rule warning, and the warning that you're on a character your run isn't"
            + " linked to.",
        section = alertsSection,
        position = 5
    )
    default boolean useNotifier()
    {
        return false;
    }

    @ConfigItem(
        keyName = "announceUnlocks",
        name = "Announce new unlocks",
        description = "When a sync brings something new, such as an area, a bank or a skill tier: a chat line naming"
            + " it, a banner for a few seconds, and new land glowing on the world map until you stand in it.",
        section = alertsSection,
        position = 6
    )
    default boolean announceUnlocks()
    {
        return true;
    }

    @ConfigSection(
        name = "Display",
        description = "What Fate Locked draws in game and on the maps",
        position = 3
    )
    String displaySection = "displaySection";

    @ConfigItem(
        keyName = "hudMode",
        name = "HUD",
        description = "Compact: where you are, its status and why, Strict Mode, and the nearest bank and shop."
            + " Detailed adds your progress, Keys and Fate Points, and what's in the chunk.",
        section = displaySection,
        position = 0
    )
    default HudMode hudMode()
    {
        return HudMode.COMPACT;
    }

    @ConfigItem(
        keyName = "worldMapMode",
        name = "World map",
        description = "Shades locked land on the world map, with a tooltip for the chunk under the mouse and, if you"
            + " choose, what it holds.",
        section = displaySection,
        position = 1
    )
    default WorldMapMode worldMapMode()
    {
        return WorldMapMode.SHADING_TOOLTIP_CONTENTS;
    }

    @ConfigItem(
        keyName = "worldMapBorders",
        name = "World map borders",
        description = "Lines on the world map: a dashed line where your unlocked land meets locked land, a plain"
            + " line on every chunk edge (Chunk grid), or both. Off keeps the shading and the tooltip.",
        section = displaySection,
        position = 2
    )
    default ChunkBorders worldMapBorders()
    {
        return ChunkBorders.LOCKED_EDGES;
    }

    @ConfigItem(
        keyName = "worldMapMarkers",
        name = "Pin locked areas on the world map",
        description = "A pin on each area you haven't unlocked; click one to jump the world map there.",
        section = displaySection,
        position = 3
    )
    default boolean worldMapMarkers()
    {
        return false;
    }

    @ConfigItem(
        keyName = "chunkBorders",
        name = "Chunk borders in the game view",
        description = "Lines on the ground where chunks meet: dashed where locked land starts, a plain line on"
            + " every chunk edge (Chunk grid), or both.",
        section = displaySection,
        position = 4
    )
    default ChunkBorders chunkBorders()
    {
        return ChunkBorders.LOCKED_EDGES;
    }

    @ConfigItem(
        keyName = "shadeNearbyLocked",
        name = "Shade locked land nearby",
        description = "Darkens locked land near you: a band two tiles deep along each locked edge in the game view,"
            + " and all locked land nearby on the minimap while Minimap chunk borders is on.",
        section = displaySection,
        position = 5
    )
    default boolean shadeNearbyLocked()
    {
        return true;
    }

    @ConfigItem(
        keyName = "drawMinimap",
        name = "Minimap chunk borders",
        description = "The game view's chunk lines on the minimap, or the locked edges while Chunk borders in the"
            + " game view is Off. With Shade locked land nearby on, locked land is darkened too.",
        section = displaySection,
        position = 6
    )
    default boolean drawMinimap()
    {
        return true;
    }

    @ConfigItem(
        keyName = "outlineLocked",
        name = "Outline locked things",
        description = "Outlines locked banks, shops, skilling spots and monsters near you in the game view, in the"
            + " locked colour, or in the not-ready colour when your skill tier doesn't open them yet, with a few words"
            + " saying why. Off hides every outline; the settings below pick which kinds. Land locked as a whole is"
            + " left to its borders.",
        section = displaySection,
        position = 7
    )
    default boolean outlineLocked()
    {
        return true;
    }

    @ConfigItem(
        keyName = "outlineBanksAndShops",
        name = "Outline banks and shops",
        description = "With Outline locked things on: bank booths, bank chests, bankers and shop keepers.",
        section = displaySection,
        position = 8
    )
    default boolean outlineBanksAndShops()
    {
        return true;
    }

    @ConfigItem(
        keyName = "outlineSkilling",
        name = "Outline skilling spots",
        description = "With Outline locked things on: trees, rocks, fishing spots, stalls and farming patches.",
        section = displaySection,
        position = 9
    )
    default boolean outlineSkilling()
    {
        return true;
    }

    @ConfigItem(
        keyName = "outlineMonsters",
        name = "Outline monsters and bosses",
        description = "With Outline locked things on: monsters and bosses the rules lock.",
        section = displaySection,
        position = 10
    )
    default boolean outlineMonsters()
    {
        return true;
    }

    @ConfigItem(
        keyName = "outlineOpen",
        name = "Outline open ones too",
        description = "With Outline locked things on: also outlines the ones you can use, in the unlocked colour.",
        section = displaySection,
        position = 11
    )
    default boolean outlineOpen()
    {
        return false;
    }

    @ConfigItem(
        keyName = "showInfoBoxes",
        name = "Infoboxes",
        description = "RuneLite infoboxes for your Keys, Fate Points and unlock progress, each movable on its own.",
        section = displaySection,
        position = 12
    )
    default boolean showInfoBoxes()
    {
        return false;
    }

    @ConfigItem(
        keyName = "colourPreset",
        name = "Colours",
        description = "Default, a set safe for colour-blind players, or your own colours below.",
        section = displaySection,
        position = 13
    )
    default ColourPreset colourPreset()
    {
        return ColourPreset.DEFAULT;
    }

    @ConfigSection(
        name = "Custom colours",
        description = "Used when Colours is set to Custom",
        position = 4,
        closedByDefault = true
    )
    String customColoursSection = "customColoursSection";

    @Alpha
    @ConfigItem(
        keyName = "unlockedColor",
        name = "Unlocked",
        description = "Text and labels for Unlocked, Can do and other good states, such as Active and Rules up to"
            + " date, with Colours set to Custom. Unlocked land isn't coloured, and its transparency isn't used.",
        section = customColoursSection,
        position = 0
    )
    default Color unlockedColor()
    {
        return new Color(16, 185, 129, 110);
    }

    @Alpha
    @ConfigItem(
        keyName = "frontierColor",
        name = "Frontier",
        description = "Chunked mode: the chunks next to yours you can unlock next, with Colours set to Custom.",
        section = customColoursSection,
        position = 1
    )
    default Color frontierColor()
    {
        return new Color(245, 158, 11, 100);
    }

    @Alpha
    @ConfigItem(
        keyName = "lockedColor",
        name = "Locked",
        description = "Locked land, edges and status, with Colours set to Custom.",
        section = customColoursSection,
        position = 2
    )
    default Color lockedColor()
    {
        return new Color(239, 68, 68, 110);
    }

    @ConfigSection(
        name = "Backup",
        description = "For runs without online sync",
        position = 5,
        closedByDefault = true
    )
    String backupSection = "backupSection";

    @ConfigItem(
        keyName = "reimportHotkey",
        name = "Import from clipboard hotkey",
        description = "Imports your rules from the clipboard, as the sidebar's Import from clipboard does: press it"
            + " after copying them from the web tracker.",
        section = backupSection,
        position = 0
    )
    default Keybind reimportHotkey()
    {
        return Keybind.NOT_SET;
    }
}
