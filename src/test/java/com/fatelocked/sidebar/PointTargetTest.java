package com.fatelocked.sidebar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fatelocked.rules.ChunkPermissionRow;
import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Test;

/** The owner's review, 28 Sept: what a Here row points at in the game when it's clicked. */
public class PointTargetTest
{
    @Test
    public void onlyCategoriesWithOneThingToPointAt()
    {
        for (String category : Arrays.asList("SKILLING", "COMBAT", "BANKS", "SHOPS", "FARMING", "ACTIVITIES"))
        {
            assertTrue(category, PointTarget.pointable(category));
        }
        assertFalse(PointTarget.pointable("QUESTS"));
        assertFalse(PointTarget.pointable("TRAVEL"));
        assertNull(PointTarget.of("QUESTS", "Cook's Assistant"));
        assertNull(PointTarget.of("SKILLING", " "));
    }

    @Test
    public void skillingAndMonstersGoByTheirOwnNames()
    {
        assertEquals(names("oak tree"), PointTarget.of("SKILLING", "Oak tree").getNames());
        assertEquals(names("goblin"), PointTarget.of("COMBAT", "Goblin").getNames());
        assertEquals("a bracket naming a kind is left out", names("guard"),
            PointTarget.of("COMBAT", "Guard (Cave goblin)").getNames());
        assertEquals("Guard (Cave goblin)", PointTarget.of("COMBAT", "Guard (Cave goblin)").getLabel());
        assertTrue(PointTarget.of("SKILLING", "Oak tree").matches("Oak tree", null));
        assertTrue("as the game writes it", PointTarget.of("SKILLING", "Oak tree").matches(" oak TREE ", null));
        assertFalse(PointTarget.of("SKILLING", "Oak tree").matches("Tree", null));
        assertFalse(PointTarget.of("SKILLING", "Oak tree").matches(null, null));
    }

    /** A fishing spot's bracket says what it offers; the spot must offer all of it. */
    @Test
    public void aFishingSpotByWhatItOffers()
    {
        PointTarget lure = PointTarget.of("SKILLING", "Fishing spot (lure, bait)");
        assertEquals("the game calls a fly-fishing spot a rod fishing spot", names("fishing spot", "rod fishing spot"),
            lure.getNames());
        assertTrue(lure.matches("Rod Fishing spot", new String[] {"Lure", "Bait"}));
        assertTrue(PointTarget.of("SKILLING", "Fishing spot (small net, bait)")
            .matches("Fishing spot", new String[] {"Small Net", "Bait"}));
        assertEquals(names("lure", "bait"), lure.getActions());
        assertTrue(lure.matches("Fishing spot", new String[] {"Lure", null, "Bait", null, null}));
        assertFalse(lure.matches("Fishing spot", new String[] {"Net", "Bait"}));
        assertFalse(lure.matches("Fishing spot", null));

        PointTarget frogspawn = PointTarget.of("SKILLING", "Fishing spot (frogspawn)");
        assertTrue("a bracket that isn't an option", frogspawn.getActions().isEmpty());
        assertTrue(frogspawn.matches("Fishing spot", new String[] {"Net"}));
    }

    @Test
    public void aBankByItsBoothsChestsAndBankers()
    {
        PointTarget bank = PointTarget.of("BANKS", "Lumbridge Castle");
        // Deposit boxes since the deep audit (plugin-ui.d3[1]): one Banks unlock opens all of a place.
        assertEquals(names("bank booth", "bank chest", "banker", "bank deposit box", "bank deposit chest",
            "deposit pool"), bank.getNames());
        assertTrue(bank.matches("Bank booth", null));
        assertTrue(bank.matches("Banker", null));
        assertFalse(bank.matches("Lumbridge Castle", null));
    }

    /**
     * plugin-ui.d3[1]: a place whose only facility is a deposit box, chest or NPC is found by it.
     * The Wiki names the box "Bank Deposit Box" or "Bank deposit box" by version; Zul-Andra's is
     * a "Chest" (Chest (Zul-Andra), option Deposit); Peer the Seer and Rionasta are NPCs.
     */
    @Test
    public void aDepositOnlyPlaceByItsBoxChestOrNpc()
    {
        PointTarget sarim = PointTarget.of("BANKS", "Port Sarim deposit boxes");
        assertTrue(sarim.matches("Bank deposit box", new String[] {"Deposit"}));
        assertTrue(sarim.matches("Bank Deposit Box", new String[] {"Deposit"}));
        assertTrue(PointTarget.of("BANKS", "Guardians of the Rift bank and deposit pool").matches("Deposit Pool", null));
        assertTrue(PointTarget.of("BANKS", "Peer the Seer deposit service").matches("Peer the Seer", null));
        assertTrue(PointTarget.of("BANKS", "Rionasta deposit service").matches("Rionasta", null));
        assertTrue(PointTarget.of("BANKS", "Zul-Andra deposit chest").matches("Chest", new String[] {"Deposit"}));
        assertFalse("only Zul-Andra's deposit chest goes by a bare Chest",
            PointTarget.of("BANKS", "Varrock west bank").matches("Chest", null));
        assertFalse(PointTarget.of("BANKS", "Varrock west bank").matches("Peer the Seer", null));
    }

    /**
     * plugin-ui.d1[0]: a shop by the keepers the tracker's table names, where its name doesn't
     * give them, and by its name's owner, including one ending in s'.
     */
    @Test
    public void aShopByTheKeepersTheTrackerNames()
    {
        assertTrue(PointTarget.of("SHOPS", "Fine Fashions").matches("Rometti", new String[] {"Talk-to", "Trade"}));
        assertTrue(PointTarget.of("SHOPS", "Varrock Swordshop").matches("Shop keeper", null));
        assertTrue(PointTarget.of("SHOPS", "Agelus' Farm Shop").matches("Agelus", null));
        assertTrue("a section the card shows in brackets",
            PointTarget.of("SHOPS", "Battle Runes (Pre-miniquest)").matches("Mage of Zamorak", null));
        assertTrue("a shop name with its own brackets",
            PointTarget.of("SHOPS", "Armour Shop (Jatizso)").matches("Raum Urda-Stein", null));
        assertEquals(names("bob's brilliant axes", "bob"), PointTarget.of("SHOPS", "Bob's Brilliant Axes").getNames());
        assertFalse("another shop's keeper", PointTarget.of("SHOPS", "Fine Fashions").matches("Bob", null));

        ShopKeepers none = ShopKeepers.NONE;
        assertEquals("with no table, the s' owner", names("agelus' farm shop", "agelus"),
            PointTarget.of("SHOPS", "Agelus' Farm Shop", none).getNames());
        assertEquals(names("gaius' two handed shop", "gaius"),
            PointTarget.of("SHOPS", "Gaius' Two Handed Shop", none).getNames());
        assertEquals(names("bob's brilliant axes", "bob"), PointTarget.of("SHOPS", "Bob's Brilliant Axes", none).getNames());
        assertFalse(PointTarget.of("SHOPS", "Fine Fashions", none).matches("Rometti", null));
        assertEquals("a shop the table lacks falls back to its name", names("made-up emporium"),
            PointTarget.of("SHOPS", "Made-up Emporium").getNames());
        assertEquals(names("lumbridge general store", "shop keeper", "shop assistant"),
            PointTarget.of("SHOPS", "Lumbridge General Store", none).getNames());
    }

    /** The table as the tracker writes it; one that can't be read is no table, and the names fall back. */
    @Test
    public void theKeeperTableReadsTheTrackersShapeAndFailsOpen()
    {
        ShopKeepers table = ShopKeepers.read(new StringReader("{\"schema\": 1, \"about\": \"Who runs each shop.\","
            + " \"shops\": {\"Fine Fashions\": [\"Rometti\"], \"Culinaromancer's Chest#Food\": [],"
            + " \"Battle Runes#Pre-miniquest\": [\"Mage of Zamorak\"], \"Odd\": 3}}"));
        assertEquals(Arrays.asList("rometti"), table.of("Fine Fashions"));
        assertEquals(Arrays.asList("mage of zamorak"), table.of("Battle Runes (Pre-miniquest)"));
        assertTrue(table.lists("Culinaromancer's Chest (Food)"));
        assertTrue(table.of("Culinaromancer's Chest (Food)").isEmpty());
        assertFalse(table.lists("Odd"));
        assertFalse(table.lists("Varrock Swordshop"));
        assertTrue(PointTarget.of("SHOPS", "Fine Fashions", table).matches("Rometti", null));

        assertTrue(ShopKeepers.load(null).of("Fine Fashions").isEmpty());
        assertTrue(ShopKeepers.load(new ByteArrayInputStream("not json {".getBytes(StandardCharsets.UTF_8)))
            .of("Fine Fashions").isEmpty());
        assertTrue(ShopKeepers.load(new ByteArrayInputStream("[]".getBytes(StandardCharsets.UTF_8)))
            .of("Fine Fashions").isEmpty());
        assertTrue("the plugin's own table loads", ShopKeepers.shared().lists("Fine Fashions"));
    }

    /**
     * plugin-ui.d3[3]: a minigame's row names a place, not a thing, so it has no target; a boss
     * row, and a row from an export without the field, has one as before.
     */
    @Test
    public void aMinigameRowHasNoTarget()
    {
        Gson gson = new Gson();
        ChunkPermissionRow pestControl = gson.fromJson(
            "{\"name\":\"Pest Control\",\"status\":\"LOCKED\",\"targetKind\":\"ACTIVITY\",\"minigame\":true}",
            ChunkPermissionRow.class);
        ChunkPermissionRow vardorvis = gson.fromJson(
            "{\"name\":\"Vardorvis\",\"status\":\"LOCKED\",\"targetKind\":\"ACTIVITY\"}", ChunkPermissionRow.class);
        ChunkPermissionRow older = gson.fromJson(
            "{\"name\":\"Pest Control\",\"status\":\"LOCKED\",\"targetKind\":\"ACTIVITY\"}", ChunkPermissionRow.class);
        assertNull(PointTarget.ofRow("ACTIVITIES", pestControl));
        assertEquals(names("vardorvis"), PointTarget.ofRow("ACTIVITIES", vardorvis).getNames());
        assertEquals(names("pest control"), PointTarget.ofRow("ACTIVITIES", older).getNames());
        assertEquals("a section keeps its brackets", "Battle Runes (Pre-miniquest)", PointTarget.ofRow("SHOPS",
            gson.fromJson("{\"name\":\"Battle Runes#Pre-miniquest\",\"status\":\"LOCKED\"}", ChunkPermissionRow.class))
            .getLabel());
    }

    /** A shop by whoever runs it: the owner its name gives, a general store's shop keeper, an inn's bartender. */
    @Test
    public void aShopByWhoeverRunsIt()
    {
        assertEquals(names("bob's brilliant axes", "bob"), PointTarget.of("SHOPS", "Bob's Brilliant Axes").getNames());
        assertEquals(names("zaff's superior staffs!", "zaff"),
            PointTarget.of("SHOPS", "Zaff's Superior Staffs!").getNames());
        assertEquals(names("lumbridge general store", "shop keeper", "shop assistant"),
            PointTarget.of("SHOPS", "Lumbridge General Store").getNames());
        assertEquals(names("blue moon inn", "bartender", "barmaid"), PointTarget.of("SHOPS", "Blue Moon Inn").getNames());
        assertEquals("a service run by its own NPC", names("sawmill operator"),
            PointTarget.of("SHOPS", "Sawmill operator").getNames());
        assertEquals("a variant's bracket left out", names("culinaromancer's chest", "culinaromancer"),
            PointTarget.of("SHOPS", "Culinaromancer's Chest (Food)").getNames());
        assertTrue(PointTarget.of("SHOPS", "Bob's Brilliant Axes").matches("Bob", new String[] {"Talk-to", "Trade"}));
    }

    @Test
    public void aPatchWithOrWithoutTheWord()
    {
        assertEquals(names("allotment patch", "allotment"), PointTarget.of("FARMING", "Allotment patch").getNames());
        assertEquals(names("herb patch", "herb"), PointTarget.of("FARMING", "Herb patch").getNames());
        assertEquals(names("anvil"), PointTarget.of("ACTIVITIES", "Anvil").getNames());
    }

    private static Set<String> names(String... names)
    {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(names)));
    }
}
