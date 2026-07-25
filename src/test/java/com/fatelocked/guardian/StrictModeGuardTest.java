package com.fatelocked.guardian;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.rules.FateRuleEngine;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RuleDecision;
import com.fatelocked.guardian.travel.TravelAction;
import com.fatelocked.guardian.travel.TravelDecision;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StrictModeGuardTest
{
    private final CanonicalChunk chunk = new CanonicalChunk(50, 50);
    private final StrictModeGuard guard = new StrictModeGuard();

    @Test
    public void onlyFreshCertainLockedActionsBlock()
    {
        FateRuleEngine engine = mock(FateRuleEngine.class);
        when(engine.target(any(), anyString(), anyString()))
            .thenReturn(decision(PermissionStatus.LOCKED));
        when(engine.entry(any())).thenReturn(decision(PermissionStatus.LOCKED));
        when(engine.equipment(anyInt())).thenReturn(decision(PermissionStatus.LOCKED));
        GuardContext enabled = new GuardContext(true, false, true, true, engine);
        GuardedAction npc = action(GuardedAction.Kind.NPC);

        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decide(npc, new GuardContext(false, false, true, true, engine)).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decide(npc, new GuardContext(true, true, true, true, engine)).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decide(npc, new GuardContext(true, false, false, true, engine)).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decide(npc, new GuardContext(true, false, true, false, engine)).getOutcome());
        assertEquals(GuardResult.Outcome.WARN_ONLY,
            guard.decide(action(GuardedAction.Kind.MOVEMENT), enabled).getOutcome());
        assertEquals(GuardResult.Outcome.BLOCK,
            guard.decide(npc, enabled).getOutcome());
        assertEquals(GuardResult.Outcome.BLOCK,
            guard.decide(action(GuardedAction.Kind.BANK), enabled).getOutcome());
        assertEquals(GuardResult.Outcome.BLOCK,
            guard.decide(action(GuardedAction.Kind.TELEPORT), enabled).getOutcome());
        assertEquals(GuardResult.Outcome.BLOCK,
            guard.decide(new GuardedAction(
                GuardedAction.Kind.EQUIPMENT, "wield", "whip", null, 4151),
                enabled).getOutcome());
    }

    @Test
    public void blockAlwaysImpliesLocked()
    {
        for (PermissionStatus status : PermissionStatus.values())
        {
            FateRuleEngine engine = mock(FateRuleEngine.class);
            when(engine.target(any(), anyString(), anyString()))
                .thenReturn(decision(status));
            GuardResult result = guard.decide(
                action(GuardedAction.Kind.NPC),
                new GuardContext(true, false, true, true, engine));
            if (result.getOutcome() == GuardResult.Outcome.BLOCK)
            {
                assertEquals(PermissionStatus.LOCKED,
                    result.getDecision().getStatus());
            }
            else if (status != PermissionStatus.LOCKED)
            {
                assertEquals(GuardResult.Outcome.ALLOW, result.getOutcome());
            }
        }
    }

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

    private GuardedAction action(GuardedAction.Kind kind)
    {
        return new GuardedAction(kind, "use", "goblin", chunk, null);
    }

    private static RuleDecision decision(PermissionStatus status)
    {
        return new RuleDecision(status, "Target", null);
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
