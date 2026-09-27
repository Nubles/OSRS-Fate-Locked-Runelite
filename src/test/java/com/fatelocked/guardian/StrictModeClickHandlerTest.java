package com.fatelocked.guardian;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.guardian.travel.TravelAction;
import com.fatelocked.guardian.travel.TravelDecision;
import net.runelite.api.events.MenuOptionClicked;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class StrictModeClickHandlerTest
{
    private final StrictModeClickHandler handler =
        new StrictModeClickHandler(new StrictModeGuard());

    @Test
    public void travelConsumesExactlyOnceOnlyForExactLockedTripsWhileActive()
    {
        TravelAction exact = exactTravel();
        TravelDecision locked = travelDecision(PermissionStatus.LOCKED);

        MenuOptionClicked lockedEvent = mock(MenuOptionClicked.class);
        handler.handleTravel(lockedEvent, exact, locked, active());
        verify(lockedEvent, times(1)).consume();

        assertNotConsumed(exact, travelDecision(PermissionStatus.ALLOWED), active());
        assertNotConsumed(exact, travelDecision(PermissionStatus.NOT_READY), active());
        assertNotConsumed(exact, travelDecision(PermissionStatus.UNKNOWN), active());
        assertNotConsumed(exact, locked, readiness(false, false, true, true));
        assertNotConsumed(exact, locked, paused());
        assertNotConsumed(exact, locked, readiness(true, false, true, false));
        assertNotConsumed(exact, locked, readiness(true, false, false, true));
        assertNotConsumed(unknownTravel(), locked, active());
        assertNotConsumed(null, locked, active());
        assertNotConsumed(exact, null, active());
        assertNotConsumed(exact, locked, null);
    }

    @Test
    public void decidingNeverTouchesTheClick()
    {
        MenuOptionClicked event = mock(MenuOptionClicked.class);

        assertEquals(GuardResult.Outcome.BLOCK, handler.decide(
            exactTravel(), travelDecision(PermissionStatus.LOCKED), active()).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW_PAUSED, handler.handleTravel(
            event, exactTravel(), travelDecision(PermissionStatus.LOCKED), paused()).getOutcome());
        verify(event, never()).consume();
    }

    private void assertNotConsumed(
        TravelAction action,
        TravelDecision decision,
        StrictModeReadiness readiness)
    {
        MenuOptionClicked event = mock(MenuOptionClicked.class);
        GuardResult result = handler.handleTravel(
            event, action, decision, readiness);
        assertNotEquals(GuardResult.Outcome.BLOCK, result.getOutcome());
        verify(event, never()).consume();
    }

    private static TravelAction exactTravel()
    {
        return new TravelAction("spell:standard:falador-teleport", "Cast", "Falador Teleport",
            Collections.singletonList(new CanonicalChunk(46, 52)), false, TravelAction.Confidence.EXACT);
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

    private static StrictModeReadiness paused()
    {
        return readiness(true, true, true, true);
    }

    private static StrictModeReadiness readiness(
        boolean enabled, boolean paused, boolean accountMatches, boolean fresh)
    {
        return StrictModeGuardTest.readiness(enabled, paused, accountMatches, fresh);
    }
}
