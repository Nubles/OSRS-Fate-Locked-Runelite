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
        OFF("Off"),
        LOCKED_EDGES("Locked edges"),
        ALL_EDGES("All edges");

        private final String label;

        ChunkBorders(String label)
        {
            this.label = label;
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
        description = "Stops a teleport to a place your rules lock",
        position = 1
    )
    String strictModeSection = "strictModeSection";

    @ConfigItem(
        keyName = "strictMode",
        name = "Strict Mode",
        description = "Stops a teleport only when it can match the trip exactly and fresh rules for this character lock"
            + " where it goes. Walking, NPCs, objects, banks and equipment are never stopped. Off by default.",
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
        description = "When you enter a locked area: a chat line, and a sound and a short screen fade if you choose."
            + " Once per area.",
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
        description = "A chat line whenever you walk into another area the tracker maps, locked or not.",
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
        description = "Chat and HUD warnings for a bank you haven't unlocked, a Slayer task in locked areas, and gear"
            + " above your unlocked tier. Each needs your rules to cover it.",
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
        description = "Adds (Locked) to right-click options for NPCs, objects and teleports your rules lock.",
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
        description = "A chat reminder when a level-up, quest, diary, boss kill or collection log entry may be worth a"
            + " roll in the tracker.",
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
        description = "Also send a RuneLite notification, as your RuneLite settings deliver them, for locked areas and"
            + " rule warnings.",
        section = alertsSection,
        position = 5
    )
    default boolean useNotifier()
    {
        return false;
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
            + " Detailed adds what's in the chunk.",
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
        keyName = "worldMapMarkers",
        name = "Pin locked areas on the world map",
        description = "A pin on each area you haven't unlocked; click one to jump the world map there.",
        section = displaySection,
        position = 2
    )
    default boolean worldMapMarkers()
    {
        return false;
    }

    @ConfigItem(
        keyName = "chunkBorders",
        name = "Chunk borders in the game view",
        description = "Lines on the ground where chunks meet: only where locked land starts, or every chunk edge.",
        section = displaySection,
        position = 3
    )
    default ChunkBorders chunkBorders()
    {
        return ChunkBorders.LOCKED_EDGES;
    }

    @ConfigItem(
        keyName = "shadeNearbyLocked",
        name = "Shade locked land nearby",
        description = "Darkens locked land beside you in the game view and on the minimap.",
        section = displaySection,
        position = 4
    )
    default boolean shadeNearbyLocked()
    {
        return true;
    }

    @ConfigItem(
        keyName = "drawMinimap",
        name = "Minimap chunk borders",
        description = "Chunk lines and locked land on the minimap.",
        section = displaySection,
        position = 5
    )
    default boolean drawMinimap()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showInfoBoxes",
        name = "Infoboxes",
        description = "RuneLite infoboxes for your keys, Fate Points and unlock progress, each movable on its own.",
        section = displaySection,
        position = 6
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
        position = 7
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
        description = "Unlocked land and status, with Colours set to Custom.",
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
        description = "Chunked mode: the chunks next to yours you can roll, with Colours set to Custom.",
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
