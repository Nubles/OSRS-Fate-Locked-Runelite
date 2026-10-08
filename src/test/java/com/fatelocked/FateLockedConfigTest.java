package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import org.junit.Test;

/** U11, D1: the settings, all in RuneLite's configuration, in the order players meet them. */
public class FateLockedConfigTest
{
    private static final FateLockedConfig DEFAULTS = new FateLockedConfig() { };

    /** key, section, position, default: the whole list, so any change is deliberate. */
    private static final List<Object[]> EXPECTED = Arrays.asList(
        new Object[]{FateLockedConfig.NETWORK_ACCESS_KEY, FateLockedConfig.trackerSection, 0, false},
        new Object[]{"strictMode", FateLockedConfig.strictModeSection, 0, false},
        new Object[]{"pauseStrictModeHotkey", FateLockedConfig.strictModeSection, 1, Keybind.NOT_SET},
        new Object[]{"lockedAreaAlert", FateLockedConfig.alertsSection, 0,
            FateLockedConfig.LockedAreaAlert.CHAT_SOUND_FADE},
        new Object[]{"announceAreaChanges", FateLockedConfig.alertsSection, 1, true},
        new Object[]{"ruleWarnings", FateLockedConfig.alertsSection, 2, true},
        new Object[]{"tagLockedOptions", FateLockedConfig.alertsSection, 3, true},
        new Object[]{"rollNudges", FateLockedConfig.alertsSection, 4, true},
        new Object[]{"useNotifier", FateLockedConfig.alertsSection, 5, false},
        new Object[]{"announceUnlocks", FateLockedConfig.alertsSection, 6, true},
        new Object[]{"hudMode", FateLockedConfig.displaySection, 0, FateLockedConfig.HudMode.COMPACT},
        new Object[]{"worldMapMode", FateLockedConfig.displaySection, 1,
            FateLockedConfig.WorldMapMode.SHADING_TOOLTIP_CONTENTS},
        new Object[]{"worldMapBorders", FateLockedConfig.displaySection, 2, FateLockedConfig.ChunkBorders.LOCKED_EDGES},
        new Object[]{"worldMapMarkers", FateLockedConfig.displaySection, 3, false},
        new Object[]{"chunkBorders", FateLockedConfig.displaySection, 4, FateLockedConfig.ChunkBorders.LOCKED_EDGES},
        new Object[]{"shadeNearbyLocked", FateLockedConfig.displaySection, 5, true},
        new Object[]{"drawMinimap", FateLockedConfig.displaySection, 6, true},
        new Object[]{"outlineLocked", FateLockedConfig.displaySection, 7, true},
        new Object[]{"outlineBanksAndShops", FateLockedConfig.displaySection, 8, true},
        new Object[]{"outlineSkilling", FateLockedConfig.displaySection, 9, true},
        new Object[]{"outlineMonsters", FateLockedConfig.displaySection, 10, true},
        new Object[]{"outlineOpen", FateLockedConfig.displaySection, 11, true},
        new Object[]{"showInfoBoxes", FateLockedConfig.displaySection, 12, false},
        new Object[]{"colourPreset", FateLockedConfig.displaySection, 13, FateLockedConfig.ColourPreset.DEFAULT},
        new Object[]{"unlockedColor", FateLockedConfig.customColoursSection, 0, new Color(16, 185, 129, 110)},
        new Object[]{"frontierColor", FateLockedConfig.customColoursSection, 1, new Color(245, 158, 11, 100)},
        new Object[]{"lockedColor", FateLockedConfig.customColoursSection, 2, new Color(239, 68, 68, 110)},
        new Object[]{"reimportHotkey", FateLockedConfig.backupSection, 0, Keybind.NOT_SET});

    @Test
    public void theSettingsAreTheseInThisOrder() throws Exception
    {
        Map<String, Method> declared = configItemsByKey();
        assertEquals(EXPECTED.size(), declared.size());
        for (Object[] expected : EXPECTED)
        {
            Method method = declared.get((String) expected[0]);
            assertNotNull((String) expected[0], method);
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            assertEquals(item.keyName(), expected[1], item.section());
            assertEquals(item.keyName(), expected[2], item.position());
            assertEquals(item.keyName(), expected[3], method.invoke(DEFAULTS));
        }
    }

    @Test
    public void everySectionListsItsItemsInOrderAndTheRarelyUsedStartClosed() throws Exception
    {
        Map<String, ConfigSection> sections = new LinkedHashMap<>();
        for (Field field : FateLockedConfig.class.getDeclaredFields())
        {
            ConfigSection section = field.getAnnotation(ConfigSection.class);
            if (section != null)
            {
                sections.put((String) field.get(null), section);
            }
        }
        assertEquals(Arrays.asList(FateLockedConfig.trackerSection, FateLockedConfig.strictModeSection,
            FateLockedConfig.alertsSection, FateLockedConfig.displaySection, FateLockedConfig.customColoursSection,
            FateLockedConfig.backupSection), sections.values().stream()
            .sorted((a, b) -> Integer.compare(a.position(), b.position()))
            .map(section -> sections.entrySet().stream().filter(e -> e.getValue() == section).findFirst().get().getKey())
            .collect(java.util.stream.Collectors.toList()));
        for (Map.Entry<String, ConfigSection> section : sections.entrySet())
        {
            boolean rare = section.getKey().equals(FateLockedConfig.customColoursSection)
                || section.getKey().equals(FateLockedConfig.backupSection);
            assertEquals(section.getKey(), rare, section.getValue().closedByDefault());
        }
        Set<String> places = new HashSet<>();
        for (Method method : configItemsByKey().values())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            assertTrue("one item per place: " + item.keyName(), places.add(item.section() + "#" + item.position()));
        }
    }

    /** Each dropdown shows these labels, and a choice switches on what its label names. */
    @Test
    public void eachChoiceSwitchesOnWhatItsLabelNames()
    {
        assertEquals(Arrays.asList("Off", "Chat", "Chat and fade", "Chat and sound", "Chat, sound and fade"),
            labels(FateLockedConfig.LockedAreaAlert.values()));
        assertEquals(Arrays.asList("Off", "Compact", "Detailed"), labels(FateLockedConfig.HudMode.values()));
        assertEquals(Arrays.asList("Off", "Shading", "Shading and tooltip", "Shading, tooltip and contents"),
            labels(FateLockedConfig.WorldMapMode.values()));
        assertEquals(Arrays.asList("Off", "Locked edges", "Chunk grid", "All edges"),
            labels(FateLockedConfig.ChunkBorders.values()));
        assertEquals(Arrays.asList("Default", "Colour-blind safe", "Custom"),
            labels(FateLockedConfig.ColourPreset.values()));

        for (FateLockedConfig.LockedAreaAlert alert : FateLockedConfig.LockedAreaAlert.values())
        {
            String label = alert.toString().toLowerCase(Locale.ROOT);
            assertEquals(alert.name(), label.contains("chat"), alert.chat());
            assertEquals(alert.name(), label.contains("sound"), alert.sound());
            assertEquals(alert.name(), label.contains("fade"), alert.fade());
        }
        for (FateLockedConfig.ChunkBorders borders : FateLockedConfig.ChunkBorders.values())
        {
            String label = borders.toString().toLowerCase(Locale.ROOT);
            boolean all = label.equals("all edges");
            assertEquals(borders.name(), all || label.contains("locked"), borders.locked());
            assertEquals(borders.name(), all || label.contains("grid"), borders.grid());
        }
        for (FateLockedConfig.WorldMapMode mode : FateLockedConfig.WorldMapMode.values())
        {
            String label = mode.toString().toLowerCase(Locale.ROOT);
            assertEquals(mode.name(), label.contains("shading"), mode.shading());
            assertEquals(mode.name(), label.contains("tooltip"), mode.tooltip());
            assertEquals(mode.name(), label.contains("contents"), mode.contents());
        }
    }

    private static List<String> labels(Enum<?>[] choices)
    {
        return Arrays.stream(choices).map(Object::toString).collect(java.util.stream.Collectors.toList());
    }

    /** RuneLite's picker keeps a colour's transparency only when the setting is marked @Alpha. */
    @Test
    public void colourSettingsKeepTheirTransparency()
    {
        for (Method method : configItemsByKey().values())
        {
            if (method.getReturnType() == Color.class)
            {
                assertNotNull(method.getName(), method.getAnnotation(Alpha.class));
            }
        }
    }

    /**
     * A changed setting takes a new key: RuneLite overwrites a stored value it can't read as
     * the setting's type with the default, before the plugin could carry it over (D2).
     */
    @Test
    public void noRetiredKeyIsDeclaredAgain()
    {
        for (String key : configItemsByKey().keySet())
        {
            assertFalse(key, SettingsMigration.OLD_DEFAULTS.containsKey(key));
        }
        assertFalse(configItemsByKey().containsKey("autoReload"));
        assertFalse(configItemsByKey().containsKey("onlineSync"));
        assertFalse(configItemsByKey().containsKey("syncCode"));
        assertFalse(configItemsByKey().containsKey("relayUrl"));
    }

    @Test
    public void onlineSyncWarnsAboutTheRelayAndStrictModeHasOneSwitch()
    {
        Map<String, Method> items = configItemsByKey();
        assertEquals(FateLockedConfig.NETWORK_WARNING,
            items.get(FateLockedConfig.NETWORK_ACCESS_KEY).getAnnotation(ConfigItem.class).warning());
        int strict = 0;
        for (Method method : items.values())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            String surface = (item.keyName() + " " + item.name() + " " + item.description()).toLowerCase(Locale.ROOT);
            assertFalse(item.keyName(), surface.contains("guardian"));
            if (surface.contains("strict mode") && method.getReturnType() == boolean.class)
            {
                strict++;
            }
        }
        assertEquals(1, strict);
    }

    /**
     * The accuracy review: each description says what its setting does, as the code does it. The
     * claims a finding corrected are pinned here; the code that makes each true has its own test.
     */
    @Test
    public void eachDescriptionSaysWhatItsSettingDoes()
    {
        // T8: the notification goes with the locked-area alert's line (FateLockedChunkEntryTest).
        assertSays("useNotifier", "each locked-area alert's chat line", "each rule warning", "isn't linked to");
        // P-4: a minute's quiet, not once per area; the sound and fade come only from unlocked land.
        assertSays("lockedAreaAlert", "only when you arrive from unlocked land", "quiet for a minute");
        assertEquals("a minute of game ticks", 100, LockedAreaAlerts.QUIET_TICKS);
        // P-11: a locked area's line is the alert's (LockedAreaAlertsTest).
        assertSays("announceAreaChanges", "Locked areas follow the Locked-area alert instead");
        // P-12 to P-14, T9: what reaches the Roll inbox, and when a reminder comes.
        assertSays("rollNudges", "a finished diary tier (not each task)", "a combat task", "a clue scroll",
            "a Slayer task", "a new pet", "game's own collection log notification",
            "the character your run is linked to");
        // P-15, P-16: the minimap's lines and shade (FateLockedMinimapOverlayTest).
        assertSays("shadeNearbyLocked", "a band two tiles deep", "while Minimap chunk borders is on");
        assertEquals(2, ChunkBorderRenderer.FOG_TILES);
        assertSays("drawMinimap", "The game view's chunk lines on the minimap",
            "or the locked edges while Chunk borders in the game view is Off", "With Shade locked land nearby on");
        // P-5: the colour is for words and labels only, made opaque (PaletteSettingsTest).
        assertSays("unlockedColor", "Unlocked land isn't coloured", "its transparency isn't used");
        // P-22, P-23, P-49: the bank warning is chat only; ground items are tagged; Keys unlock.
        assertSays("ruleWarnings", "Chat warnings", "the Slayer and gear ones also stay on the HUD");
        assertSays("tagLockedOptions", "items on the ground", "your skill tier doesn't open yet");
        // 8 Oct: one switch turns every outline off; the new unlock line, banner and glow share one.
        assertSays("outlineLocked", "Off hides every outline");
        assertSays("announceUnlocks", "a chat line", "a banner", "glowing on the world map");
        assertSays("frontierColor", "you can unlock next");
    }

    private static void assertSays(String key, String... claims)
    {
        String description = configItemsByKey().get(key).getAnnotation(ConfigItem.class).description();
        for (String claim : claims)
        {
            assertTrue(key + " says \"" + claim + "\": " + description, description.contains(claim));
        }
    }

    private static Map<String, Method> configItemsByKey()
    {
        Map<String, Method> items = new LinkedHashMap<>();
        for (Method method : FateLockedConfig.class.getDeclaredMethods())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item != null)
            {
                items.put(item.keyName(), method);
            }
        }
        return items;
    }
}
