package com.fatelocked.detection;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** The game's lines as RuneLite's own tests give them (ChatCommands, Screenshot and Loot Tracker). */
public class GameLinesTest
{
    @Test
    public void readsEveryFormOfKillCountLine()
    {
        assertKillCount("Your Corporeal Beast kill count is: <col=ff0000>4</col>.", "Corporeal Beast", 4);
        assertKillCount("Your Nightmare kill count is: <col=ff0000>1,130</col>", "Nightmare", 1130);
        assertKillCount("Your Kree'arra kill count is: <col=ff0000>4</col>.", "Kree'arra", 4);
        assertKillCount("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>13</col>.",
            "Chambers of Xeric Challenge Mode", 13);
        assertKillCount("Your completed Theatre of Blood: Entry Mode count is: <col=ff0000>73</col>.",
            "Theatre of Blood: Entry Mode", 73);
        assertKillCount("Your subdued Wintertodt count is: <col=ff0000>4</col>.", "Wintertodt", 4);
        assertKillCount("Your Gauntlet completion count is: <col=ff0000>123</col>.", "Gauntlet", 123);
        assertKillCount("Your completion count for TzHaar-Ket-Rak's First Challenge is: <col=ff0000>3</col>.",
            "TzHaar-Ket-Rak's First Challenge", 3);
        assertKillCount("Your Yama success count is: <col=ff0000>227</col>", "Yama", 227);
        assertKillCount("Your Barrows chest count is: <col=ff0000>277</col>.", "Barrows chest", 277);
        assertKillCount("Your Barrows chest count is <col=ff0000>310</col>", "Barrows chest", 310);
        assertKillCount("Your Prifddinas Agility Course lap count is: @mes_hl_red@2</col>.",
            "Prifddinas Agility Course", 2);
        assertKillCount("Your herbiboar harvest count is: <col=ff0000>4091</col>.", "herbiboar", 4091);
        assertKillCount("Your <col=6800bf>Kalphite Queen (Echo)</col> kill count is:<col=e00a19>1</col>",
            "Kalphite Queen (Echo)", 1);
    }

    @Test
    public void aLineThatIsNoKillCountHasNone()
    {
        assertNull(GameLines.killCount("You have completed 1 master Treasure Trail."));
        assertNull(GameLines.killCount("Your Vorkath kill count is: many."));
        assertNull(GameLines.killCount("Someone said: Your Vorkath kill count is: 12."));
        assertNull(GameLines.killCount(null));
    }

    @Test
    public void readsTheClueLineThousandsAndAll()
    {
        assertCount(GameLines.clue("You have completed 1 master Treasure Trail."), "master", 1);
        assertCount(GameLines.clue("<col=3300ff>You have completed 2,823 medium Treasure Trails</col>"), "medium", 2823);
        assertNull(GameLines.clue("You have completed 3 unknown Treasure Trails."));
        assertNull(GameLines.clue("Your Vorkath kill count is: 12."));
    }

    @Test
    public void readsACombatTaskWithoutItsColourOrPoints()
    {
        assertArrayEquals(new String[]{"grandmaster", "Egniol Diet II"}, GameLines.combatTask(
            "Congratulations, you've completed a grandmaster combat task: @ach_comp@Egniol Diet II</col> (6 points)."));
        assertArrayEquals(new String[]{"easy", "Into the Den of Giants"}, GameLines.combatTask(
            "Congratulations, you've completed an easy combat task: @ach_comp@Into the Den of Giants</col>."));
        assertArrayEquals(new String[]{"hard", "Why Cook?"}, GameLines.combatTask(
            "Congratulations, you've completed a hard combat task: @ach_comp@Why Cook?</col>."));
        assertArrayEquals(new String[]{"elite", "From Dusk..."}, GameLines.combatTask(
            "Congratulations, you've completed an elite combat task: @ach_comp@From Dusk...</col>."));
        assertArrayEquals(new String[]{"master", "Perfect Olm (Trio)"}, GameLines.combatTask(
            "Congratulations, you've completed a master combat task: @ach_comp@Perfect Olm (Trio)</col>."));
        assertArrayEquals(new String[]{"grandmaster", "Chambers of Xeric: CM (5-Scale) Speed-Runner"},
            GameLines.combatTask("Congratulations, you've completed a grandmaster combat task: "
                + "@ach_comp@Chambers of Xeric: CM (5-Scale) Speed-Runner</col>."));
        // Written, not the game's: one point, no colour, and space around the name.
        assertArrayEquals(new String[]{"medium", "I'd Rather Not Learn"}, GameLines.combatTask(
            "Congratulations, you've completed a medium combat task: @ach_comp@ I'd Rather Not Learn </col> (1 point)."));
        assertNull(GameLines.combatTask("Congratulations, you've completed an easy combat task: @ach_comp@ </col>."));
        assertNull(GameLines.combatTask("You have completed 1 master Treasure Trail."));
    }

    @Test
    public void readsTheCombatTaskPopupOnlyByItsTitle()
    {
        assertEquals("Handyman", GameLines.combatTaskPopup("Combat Task Completed!",
            "Task Completed: <col=ffffff>Handyman</col> (6 points)"));
        assertEquals("Why Cook?", GameLines.combatTaskPopup("<col=ffffff>combat task completed!</col>",
            "Task Completed: <col=ffffff> Why Cook? </col> (1 point)"));
        assertNull(GameLines.combatTaskPopup("Collection log", "Task Completed: Handyman (6 points)"));
        assertNull(GameLines.combatTaskPopup("Combat Task Completed!", "New item: Handyman"));
        assertNull(GameLines.combatTaskPopup("Combat Task Completed!", "Task Completed: <col=ffffff> </col> (6 points)"));
    }

    @Test
    public void readsACollectionLogItemWithoutItsColour()
    {
        assertEquals("Chompy bird hat",
            GameLines.collectionLog("New item added to your collection log: <col=ef1020>Chompy bird hat</col>"));
        assertEquals("Dragonbone necklace",
            GameLines.collectionLog("New item added to your collection log: Dragonbone necklace"));
        assertNull(GameLines.collectionLog("New item added to your collection log: <col=ef1020> </col>"));
        assertNull(GameLines.collectionLog("Your Vorkath kill count is: 12."));
    }

    @Test
    public void readsTheCollectionLogPopupOnlyByItsTitle()
    {
        assertEquals("Chompy bird hat", GameLines.collectionLogPopup("Collection log",
            "New item:<br><br><col=ffffff>Chompy bird hat</col>"));
        assertEquals("Dragon pickaxe", GameLines.collectionLogPopup("collection LOG", "New item: Dragon pickaxe"));
        assertNull(GameLines.collectionLogPopup("Combat Task Completed!", "New item: Dragon pickaxe"));
        assertNull(GameLines.collectionLogPopup("Collection log", "Task Completed: Handyman"));
        assertNull(GameLines.collectionLogPopup("Collection log", "New item: <col=ffffff> </col>"));
    }

    @Test
    public void removesTagsAndTheSpaceAroundThem()
    {
        assertEquals("Chompy bird hat", GameLines.withoutTags("  <col=ef1020>Chompy bird hat</col> "));
        assertEquals("", GameLines.withoutTags(null));
    }

    private static void assertKillCount(String line, String name, long count)
    {
        assertCount(GameLines.killCount(line), name, count);
    }

    private static void assertCount(GameLines.Count read, String name, long count)
    {
        assertEquals(name, read.name);
        assertEquals(count, read.count);
    }
}
