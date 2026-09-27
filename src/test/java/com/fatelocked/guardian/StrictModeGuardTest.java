package com.fatelocked.guardian;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.guardian.travel.TravelAction;
import com.fatelocked.guardian.travel.TravelDecision;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class StrictModeGuardTest
{
    private final StrictModeGuard guard = new StrictModeGuard();

    @Test
    public void travelBlocksOnlyExactLockedTripsWhileActive()
    {
        TravelAction exact = exactTravel();
        TravelDecision locked = travelDecision(PermissionStatus.LOCKED);

        assertEquals(GuardResult.Outcome.BLOCK,
            guard.decideTravel(exact, locked, active()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, travelDecision(PermissionStatus.UNKNOWN),
                active()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, locked, off()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, locked, stale()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, locked, wrongAccount()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(unknownTravel(), locked, active()).getOutcome());
        assertEquals("an exact match needs somewhere to go", GuardResult.Outcome.ALLOW,
            guard.decideTravel(noDestination(), locked, active()).getOutcome());
        assertEquals("one place, not one of several", GuardResult.Outcome.ALLOW,
            guard.decideTravel(severalPlaces(), locked, active()).getOutcome());
        assertEquals("networks and boats are tagged, never blocked", GuardResult.Outcome.ALLOW,
            guard.decideTravel(advisory(), locked, active()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(advisory(), locked, paused()).getOutcome());
    }

    @Test
    public void aPauseLetsOnlyAProvenLockThroughAsPaused()
    {
        TravelAction exact = exactTravel();
        TravelDecision locked = travelDecision(PermissionStatus.LOCKED);

        assertEquals(GuardResult.Outcome.ALLOW_PAUSED,
            guard.decideTravel(exact, locked, paused()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, travelDecision(PermissionStatus.ALLOWED),
                paused()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(unknownTravel(), locked, paused()).getOutcome());
        // Paused on stale rules: it couldn't have blocked, so it isn't a pause.
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, locked, readiness(true, true, true, false)).getOutcome());
    }

    @Test
    public void travelAllowsOtherStatusesAndNullInputs()
    {
        TravelAction exact = exactTravel();
        TravelDecision locked = travelDecision(PermissionStatus.LOCKED);

        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, travelDecision(PermissionStatus.ALLOWED),
                active()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, travelDecision(PermissionStatus.NOT_READY),
                active()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(null, locked, active()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decideTravel(exact, null, active()).getOutcome());
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
                action, travelDecision(status), active());

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
        return new TravelAction("spell:standard:falador-teleport", "Cast", "Falador Teleport",
            Collections.singletonList(new CanonicalChunk(46, 52)), false, TravelAction.Confidence.EXACT);
    }

    private static TravelAction noDestination()
    {
        return new TravelAction("item:somewhere", "Teleport", "Teleport",
            Collections.emptyList(), false, TravelAction.Confidence.EXACT);
    }

    private static TravelAction severalPlaces()
    {
        return new TravelAction("item:digsite-pendant", "Rub", "Digsite pendant",
            Arrays.asList(new CanonicalChunk(52, 53), new CanonicalChunk(58, 59)), false, TravelAction.Confidence.EXACT);
    }

    private static TravelAction advisory()
    {
        return new TravelAction("network:fairy-ring", "Zanaris", "Fairy ring to Zanaris",
            Collections.singletonList(new CanonicalChunk(37, 69)), true, TravelAction.Confidence.EXACT);
    }

    private static TravelAction unknownTravel()
    {
        return new TravelAction(null, null, "Unknown",
            Collections.emptyList(), false, TravelAction.Confidence.UNKNOWN);
    }

    private static TravelDecision travelDecision(PermissionStatus status)
    {
        return new TravelDecision(status, "Destination", "not unlocked");
    }

    private static StrictModeReadiness active()
    {
        return readiness(true, false, true, true);
    }

    private static StrictModeReadiness off()
    {
        return readiness(false, false, true, true);
    }

    private static StrictModeReadiness paused()
    {
        return readiness(true, true, true, true);
    }

    private static StrictModeReadiness stale()
    {
        return readiness(true, false, true, false);
    }

    private static StrictModeReadiness wrongAccount()
    {
        return readiness(true, false, false, true);
    }

    /** Rules bound to Nubles, played by Nubles or by someone else. */
    static StrictModeReadiness readiness(
        boolean enabled, boolean paused, boolean accountMatches, boolean fresh)
    {
        return StrictModeReadiness.evaluate(enabled, paused, true, true, "Nubles",
            accountMatches ? "Nubles" : "Zezima", accountMatches, fresh);
    }
}
