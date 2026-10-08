package com.fatelocked;

import com.fatelocked.sidebar.LockedThings;
import com.fatelocked.sidebar.PointTarget;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * What gets an outline (the owner's review, 8 Oct): only things the player can click, so a tree
 * that is scenery isn't outlined, and a monster that keeps one colour as it walks.
 */
public class FateLockedOutlineOverlayTest
{
    private static final LockedThings.Thing LOCKED = thing(LockedThings.Look.LOCKED);
    private static final LockedThings.Thing OPEN = thing(LockedThings.Look.OPEN);

    @Test
    public void onlyWhatCanBeClickedIsOutlined()
    {
        assertTrue(FateLockedOutlineOverlay.clickable(LockedThings.Kind.SKILLING, false,
            new String[]{"Chop down", null, null, null, null}));
        assertFalse("scenery that shares a tree's name", FateLockedOutlineOverlay.clickable(
            LockedThings.Kind.SKILLING, false, new String[]{null, null, null, null, null}));
        assertFalse(FateLockedOutlineOverlay.clickable(LockedThings.Kind.SKILLING, false, null));
        assertTrue(FateLockedOutlineOverlay.clickable(LockedThings.Kind.MONSTERS, true,
            new String[]{null, "Attack", null, null, null}));
        assertFalse("a monster you can only talk to", FateLockedOutlineOverlay.clickable(
            LockedThings.Kind.MONSTERS, true, new String[]{"Talk-to", null, null, null, null}));
        assertTrue("a banker is a bank, not a monster", FateLockedOutlineOverlay.clickable(
            LockedThings.Kind.BANKS_AND_SHOPS, true, new String[]{"Talk-to", "Bank", null, null, null}));
    }

    @Test
    public void aMonsterKeepsTheMostOpenLookItHasHad()
    {
        assertSame(OPEN, FateLockedOutlineOverlay.steadier(OPEN, LOCKED));
        assertSame(OPEN, FateLockedOutlineOverlay.steadier(LOCKED, OPEN));
        assertSame("a chunk with no row for it keeps its look", LOCKED,
            FateLockedOutlineOverlay.steadier(LOCKED, null));
        assertSame(LOCKED, FateLockedOutlineOverlay.steadier(null, LOCKED));
        assertNull(FateLockedOutlineOverlay.steadier(null, null));
    }

    private static LockedThings.Thing thing(LockedThings.Look look)
    {
        return new LockedThings.Thing(PointTarget.of("COMBAT", "Spider"), look, LockedThings.Kind.MONSTERS,
            null, null);
    }
}
