package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.sidebar.LockedThings;
import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * What the (Locked) tag and the outlines read (the owner's call, 8 Oct), from the golden
 * vanilla-mid run: Lumbridge Castle is unlocked, its bank and shops are locked, Woodcutting,
 * Fishing and Mining have no tier yet, and Seers' Village is locked as a whole.
 */
public class LockedThingsGoldenTest
{
    private static final Gson GSON = new Gson();
    private static final CanonicalChunk LUMBRIDGE = new CanonicalChunk(50, 50);
    private static final CanonicalChunk SEERS = new CanonicalChunk(42, 54);

    @Test
    public void aSpotTheSkillTierDoesntOpenSaysWhichTierItNeeds() throws Exception
    {
        LockedThings.Thing yew = lumbridge().find("Yew tree", new String[]{"Chop down", null});
        assertEquals(LockedThings.Look.TIER, yew.getLook());
        assertEquals(LockedThings.Kind.SKILLING, yew.getKind());
        assertEquals("Woodcutting tier 6", yew.getLabel());
        assertEquals("Woodcutting tier 6 (level 60) needed; Woodcutting isn't unlocked yet.", yew.getWhy());
        assertEquals("the game's capitals don't matter", LockedThings.Look.TIER,
            lumbridge().find("YEW TREE", null).getLook());
    }

    @Test
    public void aFishingSpotIsReadByWhatItOffers() throws Exception
    {
        LockedThings.Thing rod = lumbridge().find("Rod Fishing spot", new String[]{"Lure", "Bait"});
        assertEquals("Fishing tier 2", rod.getLabel());
    }

    @Test
    public void aLockedBankOrShopSaysWhatOpensIt() throws Exception
    {
        LockedThings.Thing booth = lumbridge().find("Bank booth", new String[]{"Bank", "Collect"});
        assertEquals(LockedThings.Look.LOCKED, booth.getLook());
        assertEquals(LockedThings.Kind.BANKS_AND_SHOPS, booth.getKind());
        assertEquals("Locked", booth.getLabel());

        LockedThings.Thing bob = lumbridge().find("Bob", new String[]{"Talk-to", "Trade"});
        assertEquals(LockedThings.Look.LOCKED, bob.getLook());
        assertEquals("Unlock Axe Shops", bob.getLabel());
    }

    @Test
    public void anOpenThingIsOpenAndAnUndecidedOrUnnamedOneHasNoAnswer() throws Exception
    {
        LockedThings.Thing cow = lumbridge().find("Cow", new String[]{null, "Attack"});
        assertEquals(LockedThings.Look.OPEN, cow.getLook());
        assertEquals(LockedThings.Kind.MONSTERS, cow.getKind());
        assertNull(cow.getLabel());
        // Not ready for want of a quest, not a tier: neither tagged nor outlined.
        assertNull(lumbridge().find("Cave goblin guard", null));
        assertNull(lumbridge().find("Hans", new String[]{"Talk-to"}));
        assertNull(lumbridge().find(null, null));
    }

    @Test
    public void landLockedAsAWholeIsLeftToItsBorders() throws Exception
    {
        assertFalse(lumbridge().placeLocked());
        assertTrue(LockedThings.of(decisions().details(SEERS).orElse(null)).placeLocked());
        assertNull(LockedThings.of(null).find("Yew tree", null));
    }

    private static LockedThings lumbridge() throws Exception
    {
        return LockedThings.of(decisions().details(LUMBRIDGE).orElse(null));
    }

    private static DecisionService decisions() throws Exception
    {
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz"));
        return DecisionService.create(RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json)), "iron example",
            "iron example");
    }
}
