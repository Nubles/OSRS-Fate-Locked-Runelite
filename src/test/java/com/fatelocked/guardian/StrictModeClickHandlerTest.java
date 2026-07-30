package com.fatelocked.guardian;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.rules.FateRuleEngine;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RuleDecision;
import com.fatelocked.guardian.travel.TravelAction;
import com.fatelocked.guardian.travel.TravelDecision;
import net.runelite.api.events.MenuOptionClicked;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StrictModeClickHandlerTest
{
    @Test
    public void consumesOnlyCertainLockedActions()
    {
        FateRuleEngine engine = mock(FateRuleEngine.class);
        when(engine.target(any(), anyString(), anyString()))
            .thenReturn(new RuleDecision(PermissionStatus.LOCKED, "Goblin", null));
        GuardedAction action = new GuardedAction(
            GuardedAction.Kind.NPC, "attack", "goblin",
            new CanonicalChunk(50, 50), null);
        StrictModeClickHandler handler =
            new StrictModeClickHandler(new StrictModeGuard());

        MenuOptionClicked locked = mock(MenuOptionClicked.class);
        handler.handle(locked, action,
            new GuardContext(true, false, true, true, engine));
        verify(locked).consume();

        MenuOptionClicked disabled = mock(MenuOptionClicked.class);
        handler.handle(disabled, action,
            new GuardContext(false, false, true, true, engine));
        verify(disabled, never()).consume();

        when(engine.target(any(), anyString(), anyString()))
            .thenReturn(new RuleDecision(PermissionStatus.UNKNOWN, "Goblin", null));
        MenuOptionClicked unknown = mock(MenuOptionClicked.class);
        handler.handle(unknown, action,
            new GuardContext(true, false, true, true, engine));
        verify(unknown, never()).consume();
    }

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
            TravelAction.Family.WALK, "walk", "Walk here", null,
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
