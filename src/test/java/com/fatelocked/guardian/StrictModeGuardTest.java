package com.fatelocked.guardian;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.guardian.travel.TravelAction;
import com.fatelocked.guardian.travel.TravelDecision;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StrictModeGuardTest
{
    private final StrictModeGuard guard = new StrictModeGuard();

    @Test
    public void travelBlocksOnlyFreshExactLockedDecisions()
    {
        TravelAction exact = exactTravel();
        TravelDecision locked = travelDecision(PermissionStatus.LOCKED);

        assertEquals(GuardResult.Outcome.BLOCK,
            guard.decideTravel(exact, locked, enabled()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, travelDecision(PermissionStatus.UNKNOWN),
                enabled()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, locked, disabled()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, locked, paused()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, locked, stale()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, locked, wrongAccount()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(unknownTravel(), locked, enabled()).getOutcome());
    }

    @Test
    public void travelAllowsOtherStatusesAndNullInputs()
    {
        TravelAction exact = exactTravel();
        TravelDecision locked = travelDecision(PermissionStatus.LOCKED);

        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, travelDecision(PermissionStatus.ALLOWED),
                enabled()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, travelDecision(PermissionStatus.NOT_READY),
                enabled()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(null, locked, enabled()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, null, enabled()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, locked, null).getOutcome());
    }

    @Test
    public void travelBlockAlwaysImpliesExactLockedDecision()
    {
        for (PermissionStatus status : PermissionStatus.values())
        {
            TravelAction action = exactTravel();
            GuardResult result = guard.decideTravel(
                action, travelDecision(status), enabled());

            if (result.getOutcome() == GuardResult.Outcome.BLOCK)
            {
                assertEquals(PermissionStatus.LOCKED,
                    result.getDecision().getStatus());
                assertEquals(TravelAction.Confidence.EXACT,
                    action.getConfidence());
            }
            else
            {
                assertEquals(GuardResult.Outcome.ALLOW, result.getOutcome());
            }
        }
    }

    private static TravelAction exactTravel()
    {
        return new TravelAction(
            TravelAction.Family.SPELL_OR_ITEM, "named-teleport", "Teleport falador", null,
            new CanonicalChunk(51, 51), null,
            TravelAction.Confidence.EXACT);
    }

    private static TravelAction unknownTravel()
    {
        return new TravelAction(
            TravelAction.Family.UNKNOWN, "unknown", "Unknown", null,
            null, null, TravelAction.Confidence.UNKNOWN);
    }

    private static TravelDecision travelDecision(PermissionStatus status)
    {
        return new TravelDecision(status, "Destination", "not unlocked");
    }

    private static GuardContext enabled()
    {
        return new GuardContext(true, false, true, true, null);
    }

    private static GuardContext disabled()
    {
        return new GuardContext(false, false, true, true, null);
    }

    private static GuardContext paused()
    {
        return new GuardContext(true, true, true, true, null);
    }

    private static GuardContext stale()
    {
        return new GuardContext(true, false, true, false, null);
    }

    private static GuardContext wrongAccount()
    {
        return new GuardContext(true, false, false, true, null);
    }
}
