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
        new Object[]{"hudMode", FateLockedConfig.displaySection, 0, FateLockedConfig.HudMode.COMPACT},
        new Object[]{"worldMapMode", FateLockedConfig.displaySection, 1,
            FateLockedConfig.WorldMapMode.SHADING_TOOLTIP_CONTENTS},
        new Object[]{"worldMapMarkers", FateLockedConfig.displaySection, 2, false},
        new Object[]{"chunkBorders", FateLockedConfig.displaySection, 3, FateLockedConfig.ChunkBorders.LOCKED_EDGES},
        new Object[]{"shadeNearbyLocked", FateLockedConfig.displaySection, 4, true},
        new Object[]{"drawMinimap", FateLockedConfig.displaySection, 5, true},
        new Object[]{"showInfoBoxes", FateLockedConfig.displaySection, 6, false},
        new Object[]{"colourPreset", FateLockedConfig.displaySection, 7, FateLockedConfig.ColourPreset.DEFAULT},
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
        assertEquals(Arrays.asList("Off", "Locked edges", "All edges"), labels(FateLockedConfig.ChunkBorders.values()));
        assertEquals(Arrays.asList("Default", "Colour-blind safe", "Custom"),
            labels(FateLockedConfig.ColourPreset.values()));

        for (FateLockedConfig.LockedAreaAlert alert : FateLockedConfig.LockedAreaAlert.values())
        {
            String label = alert.toString().toLowerCase(Locale.ROOT);
            assertEquals(alert.name(), label.contains("chat"), alert.chat());
            assertEquals(alert.name(), label.contains("sound"), alert.sound());
            assertEquals(alert.name(), label.contains("fade"), alert.fade());
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
