package com.fatelocked;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carries each player's settings from before Stage 3 over to the merged ones (D2, U11).
 *
 * <p>RuneLite writes every declared setting's default before the plugin starts, and did
 * for the old ones, so a stored value can't tell a choice from a default. The rule: a new
 * setting whose old keys are all still at their old defaults keeps its own default;
 * otherwise the old choice maps across. The old keys are left stored for one release, so
 * an older build on the same profile still reads them. {@code settingsVersion}, written
 * last, marks a profile done; a profile switch runs it again for that profile.
 */
final class SettingsMigration
{
    static final String VERSION_KEY = "settingsVersion";
    static final int VERSION = 2;

    /** Raw access to the plugin's stored settings. */
    interface ConfigStore
    {
        /** The stored value, or null when unset. */
        String get(String key);

        void set(String key, String value);
    }

    /** The settings Stage 3 retired, with the defaults they had. */
    static final Map<String, String> OLD_DEFAULTS;

    static
    {
        Map<String, String> old = new LinkedHashMap<>();
        old.put("chatOnEnter", "true");
        old.put("warnOnLocked", "true");
        old.put("flashOnLocked", "true");
        old.put("warnLockedBank", "true");
        old.put("warnLockedSlayer", "true");
        old.put("warnOverTierGear", "true");
        old.put("warnAccountMismatch", "true");
        old.put("tagLockedMenus", "true");
        old.put("tagLockedTeleports", "true");
        old.put("showHud", "true");
        old.put("showNearest", "true");
        old.put("showChunkContentBox", "false");
        old.put("drawWorldMap", "true");
        old.put("worldMapTooltip", "true");
        old.put("worldMapTooltipContent", "true");
        old.put("drawScene", "true");
        old.put("highlightLockedBorders", "true");
        // Grey (107, 114, 128) at alpha 60, as RuneLite stored it: ARGB in one number.
        old.put("unauthoredColor", String.valueOf(0x3C6B7280));
        OLD_DEFAULTS = Collections.unmodifiableMap(old);
    }

    /** The colours Stage 3 kept, with the defaults they have; a changed one means Custom. */
    private static final Map<String, String> COLOUR_DEFAULTS;

    static
    {
        FateLockedConfig defaults = new FateLockedConfig() { };
        Map<String, String> colours = new LinkedHashMap<>();
        colours.put("unlockedColor", rgb(defaults.unlockedColor()));
        colours.put("frontierColor", rgb(defaults.frontierColor()));
        colours.put("lockedColor", rgb(defaults.lockedColor()));
        COLOUR_DEFAULTS = Collections.unmodifiableMap(colours);
    }

    private SettingsMigration()
    {
    }

    static void migrate(ConfigStore store)
    {
        if (version(store.get(VERSION_KEY)) >= VERSION)
        {
            return;
        }

        // A new player has no old settings stored, so nothing below maps across.
        boolean chat = old(store, "chatOnEnter");
        if (changed(store, "chatOnEnter", "warnOnLocked", "flashOnLocked"))
        {
            store.set("lockedAreaAlert",
                alert(chat, old(store, "warnOnLocked"), old(store, "flashOnLocked")).name());
        }
        if (changed(store, "chatOnEnter"))
        {
            store.set("announceAreaChanges", String.valueOf(chat));
        }
        if (changed(store, "warnLockedBank", "warnLockedSlayer", "warnOverTierGear"))
        {
            store.set("ruleWarnings", String.valueOf(old(store, "warnLockedBank")
                || old(store, "warnLockedSlayer") || old(store, "warnOverTierGear")));
        }
        if (changed(store, "tagLockedMenus", "tagLockedTeleports"))
        {
            store.set("tagLockedOptions",
                String.valueOf(old(store, "tagLockedMenus") || old(store, "tagLockedTeleports")));
        }
        if (changed(store, "showHud", "showNearest", "showChunkContentBox"))
        {
            store.set("hudMode", (old(store, "showChunkContentBox") ? FateLockedConfig.HudMode.DETAILED
                : old(store, "showHud") ? FateLockedConfig.HudMode.COMPACT : FateLockedConfig.HudMode.OFF).name());
        }
        if (changed(store, "drawWorldMap", "worldMapTooltip", "worldMapTooltipContent"))
        {
            store.set("worldMapMode", worldMap(old(store, "drawWorldMap"), old(store, "worldMapTooltip"),
                old(store, "worldMapTooltipContent")).name());
        }
        if (changed(store, "drawScene", "highlightLockedBorders"))
        {
            store.set("chunkBorders", (old(store, "highlightLockedBorders") ? FateLockedConfig.ChunkBorders.LOCKED_EDGES
                : old(store, "drawScene") ? FateLockedConfig.ChunkBorders.ALL_EDGES
                : FateLockedConfig.ChunkBorders.OFF).name());
        }
        if (COLOUR_DEFAULTS.entrySet().stream().anyMatch(colour ->
            store.get(colour.getKey()) != null && !colour.getValue().equals(store.get(colour.getKey()))))
        {
            store.set("colourPreset", FateLockedConfig.ColourPreset.CUSTOM.name());
        }
        store.set(VERSION_KEY, String.valueOf(VERSION));
    }

    static FateLockedConfig.LockedAreaAlert alert(boolean chat, boolean sound, boolean fade)
    {
        if (sound && fade)
        {
            return FateLockedConfig.LockedAreaAlert.CHAT_SOUND_FADE;
        }
        if (sound)
        {
            return FateLockedConfig.LockedAreaAlert.CHAT_SOUND;
        }
        if (fade)
        {
            return FateLockedConfig.LockedAreaAlert.CHAT_FADE;
        }
        return chat ? FateLockedConfig.LockedAreaAlert.CHAT : FateLockedConfig.LockedAreaAlert.OFF;
    }

    private static FateLockedConfig.WorldMapMode worldMap(boolean shading, boolean tooltip, boolean contents)
    {
        if (!shading)
        {
            return FateLockedConfig.WorldMapMode.OFF;
        }
        if (!tooltip)
        {
            return FateLockedConfig.WorldMapMode.SHADING;
        }
        return contents ? FateLockedConfig.WorldMapMode.SHADING_TOOLTIP_CONTENTS
            : FateLockedConfig.WorldMapMode.SHADING_TOOLTIP;
    }

    /** Whether the player changed any of these old settings from its default. */
    private static boolean changed(ConfigStore store, String... keys)
    {
        List<String> names = Arrays.asList(keys);
        return names.stream().anyMatch(key -> store.get(key) != null && !OLD_DEFAULTS.get(key).equals(store.get(key)));
    }

    /** An old switch's value, or its default when it was never stored. */
    private static boolean old(ConfigStore store, String key)
    {
        String stored = store.get(key);
        return Boolean.parseBoolean(stored == null ? OLD_DEFAULTS.get(key) : stored);
    }

    private static int version(String stored)
    {
        try
        {
            return stored == null ? 0 : Integer.parseInt(stored);
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    /** How RuneLite stores a colour. */
    static String rgb(Color colour)
    {
        return String.valueOf(colour.getRGB());
    }
}
