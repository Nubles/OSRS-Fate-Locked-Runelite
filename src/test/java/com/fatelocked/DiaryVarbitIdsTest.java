package com.fatelocked;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * The diary tiers are read through RuneLite's gameval ids. Their numbers must
 * stay the ones the deprecated Varbits constants had, in the order the tier
 * names use.
 */
public class DiaryVarbitIdsTest
{
    @Test
    public void diaryTiersKeepTheirVarbitIds() throws Exception
    {
        assertArrayEquals(new int[] {
            4458, 4459, 4460, 4461, // Ardougne
            4483, 4484, 4485, 4486, // Desert
            4462, 4463, 4464, 4465, // Falador
            4491, 4492, 4493, 4494, // Fremennik
            4475, 4476, 4477, 4478, // Kandarin
            3578, 3599, 3611, 4566, // Karamja
            7925, 7926, 7927, 7928, // Kourend & Kebos
            4495, 4496, 4497, 4498, // Lumbridge & Draynor
            4487, 4488, 4489, 4490, // Morytania
            4479, 4480, 4481, 4482, // Varrock
            4471, 4472, 4473, 4474, // Western Provinces
            4466, 4467, 4468, 4469, // Wilderness
        }, (int[]) staticField("DIARY_VARBITS"));
    }

    @Test
    public void eachTierIdNamesItsOwnTier() throws Exception
    {
        @SuppressWarnings("unchecked")
        Map<Integer, String> names = (Map<Integer, String>) staticField("DIARY_VARBIT_NAMES");
        assertEquals(48, names.size());
        assertEquals("Ardougne Easy", names.get(4458));
        assertEquals("Karamja Easy", names.get(3578));
        assertEquals("Karamja Elite", names.get(4566));
        assertEquals("Lumbridge & Draynor Easy", names.get(4495));
        assertEquals("Wilderness Elite", names.get(4469));
    }

    /**
     * Events name each tier by the tracker's id, the keys of DIARIES in the
     * web app's data/diaryData.ts; three regions there are shorter than
     * their full names. Tiers named otherwise matched no tracker task.
     */
    @Test
    public void eventsUseTheTrackersTierIds() throws Exception
    {
        @SuppressWarnings("unchecked")
        Map<Integer, String> ids = (Map<Integer, String>) staticField("DIARY_TIER_IDS");
        Set<String> tracker = new HashSet<>();
        for (String region : new String[] {"Ardougne", "Desert", "Falador", "Fremennik",
            "Kandarin", "Karamja", "Kourend", "Lumbridge", "Morytania", "Varrock", "Western",
            "Wilderness"})
        {
            for (String tier : new String[] {"Easy", "Medium", "Hard", "Elite"})
            {
                tracker.add(region + " " + tier);
            }
        }

        assertEquals(tracker, new HashSet<>(ids.values()));
        assertEquals("Lumbridge Easy", ids.get(4495));
        assertEquals("Kourend Elite", ids.get(7928));
        assertEquals("Western Hard", ids.get(4473));
    }

    private static Object staticField(String name) throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
