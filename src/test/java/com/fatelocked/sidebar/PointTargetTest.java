package com.fatelocked.sidebar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
        assertEquals(names("bank booth", "bank chest", "banker"), bank.getNames());
        assertTrue(bank.matches("Bank booth", null));
        assertTrue(bank.matches("Banker", null));
        assertFalse(bank.matches("Lumbridge Castle", null));
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
