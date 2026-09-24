package com.fatelocked.guardian;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StrictModeReadinessTest
{
    @Test
    public void activeOnlyWhenEveryTrustFactHolds()
    {
        StrictModeReadiness ready = evaluate(true, false, true, "Nubles", "Nubles", true, true);
        assertEquals(StrictModeReadiness.State.ACTIVE, ready.getState());
        assertNull(ready.getReason());
    }

    @Test
    public void offAndPausedNeedNoReason()
    {
        assertEquals(StrictModeReadiness.State.OFF,
            evaluate(false, false, false, null, null, false, false).getState());
        StrictModeReadiness paused = evaluate(true, true, false, null, null, false, false);
        assertEquals(StrictModeReadiness.State.PAUSED, paused.getState());
        assertNull(paused.getReason());
    }

    @Test
    public void inactiveNamesTheFirstMissingFact()
    {
        assertInactive("no tracker rules are loaded",
            evaluate(true, false, false, "Nubles", "Nubles", true, true));
        assertInactive("the tracker profile has no linked account",
            evaluate(true, false, true, "  ", "Nubles", false, true));
        assertInactive("you are not logged in",
            evaluate(true, false, true, "Nubles", null, false, true));
        assertInactive("these rules belong to Nubles",
            evaluate(true, false, true, " Nubles ", "Zezima", false, true));
        assertInactive("the rules are more than 15 minutes old",
            evaluate(true, false, true, "Nubles", "Nubles", true, false));
    }

    private static void assertInactive(String reason, StrictModeReadiness readiness)
    {
        assertEquals(StrictModeReadiness.State.INACTIVE, readiness.getState());
        assertEquals(reason, readiness.getReason());
    }

    private static StrictModeReadiness evaluate(
        boolean enabled, boolean paused, boolean rules, String bound, String player,
        boolean matches, boolean fresh)
    {
        return StrictModeReadiness.evaluate(enabled, paused, rules, bound, player, matches, fresh);
    }
}
