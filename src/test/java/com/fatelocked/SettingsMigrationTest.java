package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

/** D2, U11: each player's settings from before Stage 3 carry over to the merged ones. */
public class SettingsMigrationTest
{
    /** Every setting the release before Stage 3 declared. */
    private static final List<String> BEFORE_STAGE_3 = Arrays.asList(
        FateLockedConfig.NETWORK_ACCESS_KEY, "reimportHotkey", "chatOnEnter", "warnOnLocked", "warnLockedBank",
        "flashOnLocked", "warnAccountMismatch", "tagLockedMenus", "tagLockedTeleports", "showHud", "showNearest",
        "showChunkContentBox", "useNotifier", "warnLockedSlayer", "warnOverTierGear", "showInfoBoxes", "rollNudges",
        "strictMode", "pauseStrictModeHotkey", "drawWorldMap", "drawScene", "drawMinimap", "highlightLockedBorders",
        "shadeNearbyLocked", "worldMapMarkers", "worldMapTooltip", "worldMapTooltipContent", "unlockedColor",
        "frontierColor", "lockedColor", "unauthoredColor");

    /** A profile's stored settings, recording each write in order. */
    private static final class Profile implements SettingsMigration.ConfigStore
    {
        final Map<String, String> stored = new HashMap<>();
        final List<String> writes = new ArrayList<>();

        /** A new player: RuneLite has stored every declared setting at its default. */
        static Profile fresh()
        {
            Profile profile = new Profile();
            profile.stored.putAll(newDefaults());
            return profile;
        }

        /** A player from before Stage 3, who also had every old setting stored, with these changes. */
        static Profile existing(String... changes)
        {
            Profile profile = fresh();
            profile.stored.putAll(SettingsMigration.OLD_DEFAULTS);
            for (int i = 0; i < changes.length; i += 2)
            {
                profile.stored.put(changes[i], changes[i + 1]);
            }
            return profile;
        }

        Profile migrated()
        {
            SettingsMigration.migrate(this);
            return this;
        }

        @Override
        public String get(String key)
        {
            return stored.get(key);
        }

        @Override
        public void set(String key, String value)
        {
            writes.add(key);
            stored.put(key, value);
        }
    }

    @Test
    public void everyOldSettingIsKeptCarriedOverOrRetired()
    {
        Set<String> kept = new HashSet<>(BEFORE_STAGE_3);
        kept.removeAll(SettingsMigration.OLD_DEFAULTS.keySet());
        assertTrue(BEFORE_STAGE_3.containsAll(SettingsMigration.OLD_DEFAULTS.keySet()));
        assertEquals(13, kept.size());
        assertTrue("a kept setting is still declared", newDefaults().keySet().containsAll(kept));
    }

    @Test
    public void aNewPlayerStartsWithEveryNewDefault()
    {
        Profile player = Profile.fresh();
        Map<String, String> before = new HashMap<>(player.stored);

        player.migrated();

        assertEquals(Collections.singletonList(SettingsMigration.VERSION_KEY), player.writes);
        assertEquals("2", player.stored.remove(SettingsMigration.VERSION_KEY));
        assertEquals(before, player.stored);
    }

    /** Old defaults can't be told from choices, so a player who kept them gets the new ones. */
    @Test
    public void aPlayerWhoKeptTheOldDefaultsGetsTheNewOnes()
    {
        Profile player = Profile.existing().migrated();

        assertEquals(Collections.singletonList(SettingsMigration.VERSION_KEY), player.writes);
        assertEquals("LOCKED_EDGES", player.stored.get("chunkBorders"));
        assertEquals("COMPACT", player.stored.get("hudMode"));
    }

    @Test
    public void theLockedAreaAlertKeepsWhatThePlayerHad()
    {
        assertEquals("CHAT_FADE", alert("warnOnLocked", "false"));
        assertEquals("CHAT_SOUND", alert("flashOnLocked", "false"));
        assertEquals("CHAT", alert("warnOnLocked", "false", "flashOnLocked", "false"));
        assertEquals("OFF", alert("chatOnEnter", "false", "warnOnLocked", "false", "flashOnLocked", "false"));
        // Every alert level has its chat line; the sound and fade the player kept decide the level.
        assertEquals("CHAT_SOUND_FADE", alert("chatOnEnter", "false"));
        assertEquals("CHAT_FADE", alert("chatOnEnter", "false", "warnOnLocked", "false"));
    }

    @Test
    public void routineAnnouncementsFollowTheOldChatSetting()
    {
        assertEquals("false", setting("announceAreaChanges", "chatOnEnter", "false"));
        assertFalse(Profile.existing("warnOnLocked", "false").migrated().writes.contains("announceAreaChanges"));
    }

    @Test
    public void ruleWarningsStayOnUnlessEveryOneWasOff()
    {
        assertEquals("true", setting("ruleWarnings", "warnLockedBank", "false"));
        assertEquals("true", setting("ruleWarnings", "warnLockedSlayer", "false", "warnOverTierGear", "false"));
        assertEquals("true", setting("ruleWarnings", "warnLockedBank", "false", "warnLockedSlayer", "false"));
        assertEquals("false", setting("ruleWarnings",
            "warnLockedBank", "false", "warnLockedSlayer", "false", "warnOverTierGear", "false"));
    }

    @Test
    public void taggingStaysOnUnlessBothKindsWereOff()
    {
        assertEquals("true", setting("tagLockedOptions", "tagLockedMenus", "false"));
        assertEquals("true", setting("tagLockedOptions", "tagLockedTeleports", "false"));
        assertEquals("false", setting("tagLockedOptions", "tagLockedMenus", "false", "tagLockedTeleports", "false"));
    }

    @Test
    public void theHudKeepsItsDetail()
    {
        assertEquals("DETAILED", setting("hudMode", "showChunkContentBox", "true"));
        assertEquals("DETAILED", setting("hudMode", "showHud", "false", "showChunkContentBox", "true"));
        assertEquals("OFF", setting("hudMode", "showHud", "false"));
        assertEquals("COMPACT", setting("hudMode", "showNearest", "false"));
    }

    @Test
    public void theWorldMapKeepsItsLayers()
    {
        assertEquals("OFF", setting("worldMapMode", "drawWorldMap", "false"));
        assertEquals("SHADING", setting("worldMapMode", "worldMapTooltip", "false"));
        assertEquals("SHADING_TOOLTIP", setting("worldMapMode", "worldMapTooltipContent", "false"));
    }

    @Test
    public void chunkBordersKeepWhatWasDrawn()
    {
        assertEquals("ALL_EDGES", setting("chunkBorders", "highlightLockedBorders", "false"));
        assertEquals("LOCKED_EDGES", setting("chunkBorders", "drawScene", "false"));
        assertEquals("OFF", setting("chunkBorders", "drawScene", "false", "highlightLockedBorders", "false"));
    }

    @Test
    public void aChangedColourSwitchesToCustomColours()
    {
        String purple = SettingsMigration.rgb(new Color(128, 0, 128, 110));
        assertEquals("CUSTOM", setting("colourPreset", "unlockedColor", purple));
        assertEquals("CUSTOM", setting("colourPreset", "frontierColor", purple));
        assertEquals("CUSTOM", setting("colourPreset", "lockedColor", purple));
        // Places the rules don't decide aren't tinted any more, so their colour has no successor.
        assertEquals("DEFAULT", setting("colourPreset", "unauthoredColor", purple));
    }

    /**
     * On a profile switch RuneLite may store that profile's defaults after the plugin has run,
     * so a colour that isn't stored yet is still the default one.
     */
    @Test
    public void aColourNotStoredYetIsTheDefault()
    {
        Profile player = Profile.existing("showHud", "false");
        player.stored.keySet().removeAll(Arrays.asList("unlockedColor", "frontierColor", "lockedColor", "colourPreset"));

        player.migrated();

        assertFalse(player.writes.contains("colourPreset"));
        assertEquals("OFF", player.stored.get("hudMode"));
    }

    /** Decision 10: another character's line always shows, so its old switch has no successor. */
    @Test
    public void theAccountMismatchSwitchIsRetired()
    {
        assertEquals(Collections.singletonList(SettingsMigration.VERSION_KEY),
            Profile.existing("warnAccountMismatch", "false").migrated().writes);
    }

    /** A player who started on an older release never had some settings; each counts as its default. */
    @Test
    public void aSettingThePlayerNeverHadCountsAsItsOldDefault()
    {
        Profile player = Profile.fresh();
        player.stored.put("chatOnEnter", "false");

        player.migrated();

        assertEquals("CHAT_SOUND_FADE", player.stored.get("lockedAreaAlert"));
        assertEquals("false", player.stored.get("announceAreaChanges"));
        assertEquals(Arrays.asList("lockedAreaAlert", "announceAreaChanges", SettingsMigration.VERSION_KEY),
            player.writes);
    }

    /** Written last, so a start that stops part-way carries the settings over again next time. */
    @Test
    public void theVersionIsWrittenLastAndTheOldSettingsStay()
    {
        Profile player = Profile.existing("warnOnLocked", "false", "showHud", "false", "drawWorldMap", "false",
            "drawScene", "false", "tagLockedMenus", "false", "warnLockedBank", "false");
        Map<String, String> old = new HashMap<>();
        for (String key : SettingsMigration.OLD_DEFAULTS.keySet())
        {
            old.put(key, player.stored.get(key));
        }

        player.migrated();

        assertEquals(7, player.writes.size());
        assertEquals(SettingsMigration.VERSION_KEY, player.writes.get(6));
        for (Map.Entry<String, String> setting : old.entrySet())
        {
            assertEquals("kept for one release: " + setting.getKey(), setting.getValue(),
                player.stored.get(setting.getKey()));
        }
    }

    @Test
    public void eachProfileIsCarriedOverOnce()
    {
        Profile player = Profile.existing("showHud", "false").migrated();
        player.writes.clear();
        player.stored.put("hudMode", "DETAILED");

        player.migrated();

        assertTrue(player.writes.isEmpty());
        assertEquals("the player's own choice since stays", "DETAILED", player.stored.get("hudMode"));

        Profile later = Profile.existing("showHud", "false");
        later.stored.put(SettingsMigration.VERSION_KEY, "3");
        assertTrue("a later release's profile is left alone", later.migrated().writes.isEmpty());
    }

    @Test
    public void anUnreadableVersionIsCarriedOverAgain()
    {
        Profile player = Profile.existing("showHud", "false");
        player.stored.put(SettingsMigration.VERSION_KEY, "two");

        player.migrated();

        assertEquals("OFF", player.stored.get("hudMode"));
        assertEquals("2", player.stored.get(SettingsMigration.VERSION_KEY));
    }

    /** One merged setting after carrying over a player with these old changes. */
    private static String setting(String key, String... changes)
    {
        return Profile.existing(changes).migrated().stored.get(key);
    }

    /** The alert level, which RuneLite must be able to read back as the setting's type. */
    private static String alert(String... changes)
    {
        return FateLockedConfig.LockedAreaAlert.valueOf(setting("lockedAreaAlert", changes)).name();
    }

    /** What RuneLite stores for each declared setting before the plugin starts. */
    private static Map<String, String> newDefaults()
    {
        FateLockedConfig defaults = new FateLockedConfig() { };
        Map<String, String> stored = new HashMap<>();
        for (Method method : FateLockedConfig.class.getDeclaredMethods())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item == null)
            {
                continue;
            }
            try
            {
                Object value = method.invoke(defaults);
                stored.put(item.keyName(), value instanceof Color ? SettingsMigration.rgb((Color) value)
                    : value instanceof Enum ? ((Enum<?>) value).name() : String.valueOf(value));
            }
            catch (ReflectiveOperationException e)
            {
                throw new AssertionError(e);
            }
        }
        return stored;
    }
}
