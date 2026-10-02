package com.fatelocked.detection;

import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/** The diary tiers' varbits, read through RuneLite's gameval ids, keep the numbers the game uses. */
public class DiaryTiersTest
{
    @Test
    public void eachTierKeepsItsVarbitInTheTrackersOrder()
    {
        int[] varbits = {
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
        };
        String[] regions = {"Ardougne", "Desert", "Falador", "Fremennik", "Kandarin", "Karamja", "Kourend",
            "Lumbridge", "Morytania", "Varrock", "Western", "Wilderness"};
        String[] tiers = {"Easy", "Medium", "Hard", "Elite"};

        List<Integer> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Map.Entry<Integer, String> tier : DiaryTiers.TIER_IDS.entrySet())
        {
            ids.add(tier.getKey());
            names.add(tier.getValue());
        }
        List<Integer> expectedIds = new ArrayList<>();
        List<String> expectedNames = new ArrayList<>();
        for (int i = 0; i < varbits.length; i++)
        {
            expectedIds.add(varbits[i]);
            expectedNames.add(regions[i / 4] + " " + tiers[i % 4]);
        }
        assertEquals(expectedIds, ids);
        assertEquals(expectedNames, names);
    }

    @Test
    public void givesEachTierItsFullNameForChat()
    {
        assertEquals(48, DiaryTiers.FULL_NAMES.size());
        assertEquals("Lumbridge & Draynor Easy", DiaryTiers.FULL_NAMES.get("Lumbridge Easy"));
        assertEquals("Kourend & Kebos Elite", DiaryTiers.FULL_NAMES.get("Kourend Elite"));
        assertEquals("Western Provinces Hard", DiaryTiers.FULL_NAMES.get("Western Hard"));
        assertEquals("Varrock Medium", DiaryTiers.FULL_NAMES.get("Varrock Medium"));
        assertEquals("Karamja Elite", DiaryTiers.FULL_NAMES.get("Karamja Elite"));
    }

    /** The OSRS Wiki: Karamja's easy, medium and hard varbits are 1 once started and 2 once done. */
    @Test
    public void karamjasFirstThreeTiersAreDoneAtTwo()
    {
        assertEquals(3578, VarbitID.ATJUN_EASY_DONE);
        assertEquals(3599, VarbitID.ATJUN_MED_DONE);
        assertEquals(3611, VarbitID.ATJUN_HARD_DONE);
        assertEquals(2, DiaryTiers.doneValue(3578));
        assertEquals(2, DiaryTiers.doneValue(3599));
        assertEquals(2, DiaryTiers.doneValue(3611));
        assertEquals(1, DiaryTiers.doneValue(4566));
        assertEquals(1, DiaryTiers.doneValue(4479));
        assertEquals(1, DiaryTiers.doneValue(4495));
    }
}
