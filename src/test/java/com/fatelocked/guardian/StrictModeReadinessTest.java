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
        assertEquals(StrictModeReadiness.State.OFF,
            evaluate(false, true, true, "Nubles", "Nubles", true, true).getState());
        StrictModeReadiness paused = evaluate(true, true, true, "Nubles", "Nubles", true, true);
        assertEquals(StrictModeReadiness.State.PAUSED, paused.getState());
        assertNull(paused.getReason());
    }

    /** Paused means it would block otherwise, so a pause never hides why it couldn't. */
    @Test
    public void aPauseDoesNotHideWhyStrictModeCannotAct()
    {
        assertInactive("no tracker rules are loaded",
            evaluate(true, true, false, null, null, false, false));
        assertInactive("the rules are more than 15 minutes old",
            evaluate(true, true, true, "Nubles", "Nubles", true, false));
        assertInactive("the rules have no travel table; sync them from the tracker again",
            StrictModeReadiness.evaluate(true, true, true, false, "Nubles", "Nubles", true, true));
    }

    @Test
    public void inactiveNamesTheFirstMissingFact()
    {
        assertInactive("no tracker rules are loaded",
            evaluate(true, false, false, "Nubles", "Nubles", true, true));
        assertInactive("the rules have no travel table; sync them from the tracker again",
            StrictModeReadiness.evaluate(true, false, true, false, "Nubles", "Nubles", true, true));
        assertInactive("the tracker profile has no linked account",
            evaluate(true, false, true, "  ", "Nubles", false, true));
        assertInactive("you are not logged in",
            evaluate(true, false, true, "Nubles", null, false, true));
        assertInactive("the profile is for Nubles; you're logged in as Zezima",
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
        return StrictModeReadiness.evaluate(enabled, paused, rules, true, bound, player, matches, fresh);
    }
}
