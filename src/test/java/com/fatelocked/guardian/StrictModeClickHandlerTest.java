package com.fatelocked.guardian;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.guardian.travel.TravelAction;
import com.fatelocked.guardian.travel.TravelDecision;
import net.runelite.api.events.MenuOptionClicked;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class StrictModeClickHandlerTest
{
    @Test
    public void travelConsumesExactlyOnceOnlyForFreshExactLockedDecisions()
    {
        StrictModeClickHandler handler =
            new StrictModeClickHandler(new StrictModeGuard());
        TravelAction exact = exactTravel();
        TravelDecision locked = travelDecision(PermissionStatus.LOCKED);

        MenuOptionClicked lockedEvent = mock(MenuOptionClicked.class);
        handler.handleTravel(lockedEvent, exact, locked, enabled());
        verify(lockedEvent, times(1)).consume();

        assertNotConsumed(handler, exact, travelDecision(PermissionStatus.ALLOWED),
            enabled());
        assertNotConsumed(handler, exact, travelDecision(PermissionStatus.NOT_READY),
            enabled());
        assertNotConsumed(handler, exact, travelDecision(PermissionStatus.UNKNOWN),
            enabled());
        assertNotConsumed(handler, exact, locked, disabled());
        assertNotConsumed(handler, exact, locked, paused());
        assertNotConsumed(handler, exact, locked, stale());
        assertNotConsumed(handler, exact, locked, wrongAccount());
        assertNotConsumed(handler, unknownTravel(), locked, enabled());
        assertNotConsumed(handler, null, locked, enabled());
        assertNotConsumed(handler, exact, null, enabled());
        assertNotConsumed(handler, exact, locked, null);
    }

    private static void assertNotConsumed(
        StrictModeClickHandler handler,
        TravelAction action,
        TravelDecision decision,
        GuardContext context)
    {
        MenuOptionClicked event = mock(MenuOptionClicked.class);
        GuardResult result = handler.handleTravel(
            event, action, decision, context);
        assertEquals(GuardResult.Outcome.ALLOW,
            result.getOutcome());
        verify(event, never()).consume();
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
