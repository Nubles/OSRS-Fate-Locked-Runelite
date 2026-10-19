package com.fatelocked.badges;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fatelocked.rules.Progress;
import org.junit.Test;

/** CONTRACT section 2: 0 under 25%, then Wayfarer, Pathfinder, Trailblazer and Fate Unbound. */
public class BadgeTierTest
{
    @Test
    public void eachMilestoneStartsAtItsPercent()
    {
        assertEquals(BadgeTier.NONE, BadgeTier.ofPercent(-1));
        assertEquals(BadgeTier.UNDER_25, BadgeTier.ofPercent(0));
        assertEquals(BadgeTier.UNDER_25, BadgeTier.ofPercent(24));
        assertEquals(BadgeTier.WAYFARER, BadgeTier.ofPercent(25));
        assertEquals(BadgeTier.WAYFARER, BadgeTier.ofPercent(49));
        assertEquals(BadgeTier.PATHFINDER, BadgeTier.ofPercent(50));
        assertEquals(BadgeTier.PATHFINDER, BadgeTier.ofPercent(74));
        assertEquals(BadgeTier.TRAILBLAZER, BadgeTier.ofPercent(75));
        assertEquals(BadgeTier.TRAILBLAZER, BadgeTier.ofPercent(99));
        assertEquals(BadgeTier.FATE_UNBOUND, BadgeTier.ofPercent(100));
        assertEquals(0, BadgeTier.UNDER_25);
        assertEquals(4, BadgeTier.FATE_UNBOUND);
    }

    @Test
    public void theTierIsTheRunsProgressPercent()
    {
        // 15 of 187 areas is 8%, as the run card says.
        assertEquals(BadgeTier.UNDER_25, BadgeTier.of(new Progress(Progress.AREAS, 15, 187, 0, 0)));
        assertEquals(BadgeTier.WAYFARER, BadgeTier.of(new Progress(Progress.AREAS, 47, 187, 0, 0)));
        assertEquals(BadgeTier.PATHFINDER, BadgeTier.of(new Progress(Progress.CHUNKS, 50, 100, 50, 100)));
        assertEquals(BadgeTier.FATE_UNBOUND, BadgeTier.of(new Progress(Progress.AREAS, 187, 187, 0, 0)));
        assertEquals(BadgeTier.NONE, BadgeTier.of(new Progress(Progress.AREAS, 0, 0, 0, 0)));
        assertEquals(BadgeTier.NONE, BadgeTier.of(null));
    }

    /** percent() rounds 374 of 375 up to 100; Fate Unbound waits for the last one. */
    @Test
    public void fateUnboundNeedsEverything()
    {
        Progress almost = new Progress(Progress.CHUNKS, 374, 375, 374, 375);
        assertEquals(100, almost.percent());
        assertEquals(BadgeTier.TRAILBLAZER, BadgeTier.of(almost));
    }

    @Test
    public void onlyTheRelaysTiersAreValid()
    {
        assertFalse(BadgeTier.valid(-1));
        for (int tier = 0; tier < BadgeTier.COUNT; tier++)
        {
            assertTrue(BadgeTier.valid(tier));
        }
        assertFalse(BadgeTier.valid(BadgeTier.COUNT));
    }
}
