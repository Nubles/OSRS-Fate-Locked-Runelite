package com.fatelocked.detection;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DetectionTablesTest
{
    @Test
    public void findsABossByAnyNameTheGamePrintsForIt()
    {
        DetectionTables tables = parse("{`bosses`:["
            + "{`key`:`TzHaar Fight Cave`,`raid`:false,`killCounts`:[`TzTok-Jad`]},"
            + "{`key`:`Chambers of Xeric`,`raid`:true,`killCounts`:[`Chambers of Xeric`,`Chambers of Xeric Challenge Mode`]}]}");

        assertEquals(new DetectionTables.Boss("TzHaar Fight Cave", false), tables.bossForKillCount("TzTok-Jad"));
        assertEquals(new DetectionTables.Boss("Chambers of Xeric", true),
            tables.bossForKillCount("  chambers of   XERIC challenge mode "));
        assertNull(tables.bossForKillCount("Prifddinas Agility Course"));
        assertNull(tables.bossForKillCount(null));
    }

    @Test
    public void leavesOutWhatIsMalformedAndKeepsTheRest()
    {
        DetectionTables tables = parse("{`bosses`:["
            + "{`raid`:true,`killCounts`:[`Keyless`]},"
            + "{`key`:7,`killCounts`:[`Numbered`]},"
            + "{`key`:` `,`killCounts`:[`Blank`]},"
            + "{`key`:`Vorkath`,`raid`:`yes`,`killCounts`:[`Vorkath`,3,null,` `]},"
            + "{`key`:`Zulrah`,`killCounts`:`Zulrah`},"
            + "`Obor`,"
            + "{`key`:`Giant Mole`,`raid`:1,`killCounts`:[`Giant Mole`]},"
            + "{`key`:`Sarachnis`,`raid`:`true`,`killCounts`:[`Sarachnis`]}],"
            + "`quests`:[{`name`:`Idless`},{`id`:5},`Cook's Assistant`,{`id`:`Rune Mysteries`,`name`:3}],"
            + "`diaryTiers`:[`Varrock Easy`,4,null,{}]}");

        assertNull(tables.bossForKillCount("Keyless"));
        assertNull(tables.bossForKillCount("Numbered"));
        assertNull(tables.bossForKillCount("Blank"));
        // Only a true raid is one.
        assertEquals(new DetectionTables.Boss("Vorkath", false), tables.bossForKillCount("Vorkath"));
        assertEquals(new DetectionTables.Boss("Giant Mole", false), tables.bossForKillCount("Giant Mole"));
        assertEquals(new DetectionTables.Boss("Sarachnis", false), tables.bossForKillCount("Sarachnis"));
        assertNull(tables.bossForKillCount("Zulrah"));
        assertNull(tables.bossForKillCount("Obor"));
        assertNull(tables.questId("Idless"));
        assertNull(tables.questId("Cook's Assistant"));
        assertEquals("Rune Mysteries", tables.questId("rune mysteries"));
        assertTrue(tables.isDiaryTier("Varrock Easy"));
        assertFalse(tables.isDiaryTier("Varrock Medium"));
    }

    @Test
    public void theFirstBossToClaimANameKeepsIt()
    {
        DetectionTables tables = parse("{`bosses`:["
            + "{`key`:`The Gauntlet`,`killCounts`:[`Gauntlet`]},"
            + "{`key`:`The Corrupted Gauntlet`,`killCounts`:[`Gauntlet`,`Corrupted Gauntlet`]}]}");

        assertEquals("The Gauntlet", tables.bossForKillCount("Gauntlet").getKey());
        assertEquals("The Corrupted Gauntlet", tables.bossForKillCount("Corrupted Gauntlet").getKey());
    }

    @Test
    public void findsAQuestByItsIdOrItsName()
    {
        DetectionTables tables = parse("{`quests`:["
            + "{`id`:`Mourning's End Part II`,`name`:`Mourning's End Part I`},"
            + "{`id`:`Cook's Assistant`,`name`:`Cook's Assistant`},"
            + "{`id`:`Mourning's End Part I`,`name`:`Mourning's Ends Part I`},"
            + "{`name`:`Nameless`},"
            + "{`id`:`Mourning's Ends Part I`,`name`:`Another`}]}");

        assertEquals("Cook's Assistant", tables.questId("COOK'S  ASSISTANT"));
        assertEquals("Mourning's End Part II", tables.questId("Mourning's End Part II"));
        // An id is never taken by another quest's name, even one listed first.
        assertEquals("Mourning's End Part I", tables.questId("Mourning's End Part I"));
        assertEquals("Mourning's Ends Part I", tables.questId("Mourning's Ends Part I"));
        assertEquals("Mourning's Ends Part I", tables.questId("another"));
        assertNull(tables.questId("Nameless"));
        assertNull(tables.questId("Dragon Slayer II"));
    }

    @Test
    public void namesRecipeForDisastersPartsAsTheTrackerDoes()
    {
        StringBuilder quests = new StringBuilder();
        String[] ids = {"RFD: The Cook", "RFD: Dwarf", "RFD: Goblins", "RFD: Pirate Pete", "RFD: Lumbridge Guide",
            "RFD: Evil Dave", "RFD: Skrach Uglogwee", "RFD: Sir Amik Varze", "RFD: King Awowogei", "RFD: Finale"};
        for (String id : ids)
        {
            quests.append(quests.length() == 0 ? "" : ",").append("{`id`:`").append(id).append("`}");
        }
        DetectionTables tables = parse("{`quests`:[" + quests + "]}");

        String[] parts = {"Another Cook's Quest", "Mountain Dwarf", "Wartface & Bentnoze", "Pirate Pete",
            "Lumbridge Guide", "Evil Dave", "Skrach Uglogwee", "Sir Amik Varze", "King Awowogei", "Culinaromancer"};
        for (int i = 0; i < parts.length; i++)
        {
            assertEquals(parts[i], ids[i], tables.questId("Recipe for Disaster - " + parts[i]));
        }
        assertEquals("RFD: Finale", tables.questId("recipe for disaster - CULINAROMANCER"));
        // A part the tracker doesn't list is no event.
        assertNull(parse("{`quests`:[]}").questId("Recipe for Disaster - Another Cook's Quest"));
    }

    @Test
    public void anyDiaryTierCountsWhileTheBundleListsNone()
    {
        assertTrue(parse("{}").isDiaryTier("Varrock Easy"));
        assertTrue(parse("{`diaryTiers`:[]}").isDiaryTier("Anything"));
        assertTrue(DetectionTables.none().isDiaryTier("Varrock Easy"));
    }

    @Test
    public void noTablesNoticeNoBossOrQuest()
    {
        DetectionTables none = DetectionTables.none();

        assertNull(none.bossForKillCount("Vorkath"));
        assertNull(none.questId("Cook's Assistant"));
        assertNull(DetectionTables.parse(null));
        assertNull(DetectionTables.parse(JsonNull.INSTANCE));
        assertNull(DetectionTables.parse(json("[]")));
        assertNull(DetectionTables.parse(json("`detection`")));
    }

    @Test
    public void foldsCaseAndSpaces()
    {
        assertEquals("tztok-jad", DetectionTables.fold("  TzTok-Jad "));
        assertEquals("theatre of blood: hard mode", DetectionTables.fold("Theatre of\tBlood:  Hard Mode"));
        assertEquals("", DetectionTables.fold(null));
    }

    private static DetectionTables parse(String json)
    {
        return DetectionTables.parse(json(json));
    }

    /** JSON written with backticks for quotes, since names have apostrophes. */
    private static JsonElement json(String text)
    {
        return new JsonParser().parse(text.replace('`', '"'));
    }
}
