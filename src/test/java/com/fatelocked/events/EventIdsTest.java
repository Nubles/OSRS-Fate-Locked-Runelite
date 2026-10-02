package com.fatelocked.events;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class EventIdsTest
{
    private static String id(String account, String run, FateEventType type, String name, String count)
    {
        return EventIds.of(account, run, type, name, count);
    }

    @Test
    public void isShortAndMarkedAsRuneLites()
    {
        assertTrue(id("Nubles", "run-1", FateEventType.BOSS_KILL, "Vorkath", "12").matches("^fl1-[0-9a-f]{32}$"));
    }

    @Test
    public void foldsNamesAsTheTrackerDoes()
    {
        assertEquals(id("Nubles", "run-1", FateEventType.QUEST, "Dragon Slayer I", ""),
            id("  nUbLeS ", "run-1", FateEventType.QUEST, "dragon   slayer i", ""));
    }

    @Test
    public void tellsEveryPartApart()
    {
        String base = id("Nubles", "run-1", FateEventType.BOSS_KILL, "Vorkath", "12");
        assertNotEquals(base, id("Other", "run-1", FateEventType.BOSS_KILL, "Vorkath", "12"));
        assertNotEquals(base, id("Nubles", "run-2", FateEventType.BOSS_KILL, "Vorkath", "12"));
        assertNotEquals(base, id("Nubles", "run-1", FateEventType.RAID_COMPLETION, "Vorkath", "12"));
        assertNotEquals(base, id("Nubles", "run-1", FateEventType.BOSS_KILL, "Zulrah", "12"));
        assertNotEquals(base, id("Nubles", "run-1", FateEventType.BOSS_KILL, "Vorkath", "13"));
        // Parts can't run into each other: "ab" + "c" isn't "a" + "bc".
        assertNotEquals(id("Nubles", "run-1", FateEventType.BOSS_KILL, "ab", "c"),
            id("Nubles", "run-1", FateEventType.BOSS_KILL, "a", "bc"));
    }

    @Test
    public void takesMissingPartsAsEmpty()
    {
        assertEquals(id(null, null, null, null, null), id("", "", null, " ", ""));
    }
}
