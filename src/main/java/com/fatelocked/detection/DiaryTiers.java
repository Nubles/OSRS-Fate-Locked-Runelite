package com.fatelocked.detection;

import net.runelite.api.gameval.VarbitID;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The 48 achievement diary tiers' completion varbits, by the tracker's tier ids. Each is 1 once
 * the tier's tasks are done, except Karamja's first three: those are 1 once started and 2 once
 * done (the OSRS Wiki's varbits 3578, 3599 and 3611).
 */
public final class DiaryTiers
{
    private static final int[] DONE_AT_TWO = {
        VarbitID.ATJUN_EASY_DONE, VarbitID.ATJUN_MED_DONE, VarbitID.ATJUN_HARD_DONE,
    };

    private static final int[] VARBITS = {
        VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE, VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE, VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE,
        VarbitID.DESERT_DIARY_EASY_COMPLETE, VarbitID.DESERT_DIARY_MEDIUM_COMPLETE, VarbitID.DESERT_DIARY_HARD_COMPLETE, VarbitID.DESERT_DIARY_ELITE_COMPLETE,
        VarbitID.FALADOR_DIARY_EASY_COMPLETE, VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE, VarbitID.FALADOR_DIARY_HARD_COMPLETE, VarbitID.FALADOR_DIARY_ELITE_COMPLETE,
        VarbitID.FREMENNIK_DIARY_EASY_COMPLETE, VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE, VarbitID.FREMENNIK_DIARY_HARD_COMPLETE, VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE,
        VarbitID.KANDARIN_DIARY_EASY_COMPLETE, VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE, VarbitID.KANDARIN_DIARY_HARD_COMPLETE, VarbitID.KANDARIN_DIARY_ELITE_COMPLETE,
        VarbitID.ATJUN_EASY_DONE, VarbitID.ATJUN_MED_DONE, VarbitID.ATJUN_HARD_DONE, VarbitID.KARAMJA_DIARY_ELITE_COMPLETE,
        VarbitID.KOUREND_DIARY_EASY_COMPLETE, VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE, VarbitID.KOUREND_DIARY_HARD_COMPLETE, VarbitID.KOUREND_DIARY_ELITE_COMPLETE,
        VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE, VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE, VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE, VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE,
        VarbitID.MORYTANIA_DIARY_EASY_COMPLETE, VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE, VarbitID.MORYTANIA_DIARY_HARD_COMPLETE, VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE,
        VarbitID.VARROCK_DIARY_EASY_COMPLETE, VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE, VarbitID.VARROCK_DIARY_HARD_COMPLETE, VarbitID.VARROCK_DIARY_ELITE_COMPLETE,
        VarbitID.WESTERN_DIARY_EASY_COMPLETE, VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE, VarbitID.WESTERN_DIARY_HARD_COMPLETE, VarbitID.WESTERN_DIARY_ELITE_COMPLETE,
        VarbitID.WILDERNESS_DIARY_EASY_COMPLETE, VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE, VarbitID.WILDERNESS_DIARY_HARD_COMPLETE, VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE,
    };

    /** The tracker's region names, in VARBITS order, then the region's full name for chat. */
    private static final String[][] REGIONS = {
        {"Ardougne", "Ardougne"}, {"Desert", "Desert"}, {"Falador", "Falador"},
        {"Fremennik", "Fremennik"}, {"Kandarin", "Kandarin"}, {"Karamja", "Karamja"},
        {"Kourend", "Kourend & Kebos"}, {"Lumbridge", "Lumbridge & Draynor"},
        {"Morytania", "Morytania"}, {"Varrock", "Varrock"},
        {"Western", "Western Provinces"}, {"Wilderness", "Wilderness"},
    };
    private static final String[] TIERS = {"Easy", "Medium", "Hard", "Elite"};

    /** Varbit id → the tracker's tier id, such as "Lumbridge Easy". */
    public static final Map<Integer, String> TIER_IDS;
    /** The tracker's tier id → the tier's full name, such as "Lumbridge & Draynor Easy". */
    public static final Map<String, String> FULL_NAMES;

    static
    {
        Map<Integer, String> ids = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (int i = 0; i < VARBITS.length; i++)
        {
            String tier = " " + TIERS[i % 4];
            ids.put(VARBITS[i], REGIONS[i / 4][0] + tier);
            names.put(REGIONS[i / 4][0] + tier, REGIONS[i / 4][1] + tier);
        }
        TIER_IDS = Collections.unmodifiableMap(ids);
        FULL_NAMES = Collections.unmodifiableMap(names);
    }

    /** The value a tier's varbit takes once its tasks are done. */
    public static int doneValue(int varbit)
    {
        for (int twice : DONE_AT_TWO)
        {
            if (twice == varbit) return 2;
        }
        return 1;
    }

    private DiaryTiers()
    {
    }
}
