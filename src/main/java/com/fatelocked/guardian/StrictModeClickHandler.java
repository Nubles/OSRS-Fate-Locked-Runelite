package com.fatelocked.guardian;

import com.fatelocked.guardian.travel.TravelAction;
import com.fatelocked.guardian.travel.TravelDecision;
import net.runelite.api.events.MenuOptionClicked;

public final class StrictModeClickHandler
{
    private final StrictModeGuard guard;

    public StrictModeClickHandler(StrictModeGuard guard)
    {
        this.guard = guard;
    }

    public GuardResult handle(
        MenuOptionClicked event,
        GuardedAction action,
        GuardContext context)
    {
        GuardResult result = guard.decide(action, context);
        if (result.getOutcome() == GuardResult.Outcome.BLOCK)
        {
            event.consume();
        }
        return result;
    }

    public GuardResult handleTravel(
        MenuOptionClicked event,
        TravelAction action,
        TravelDecision decision,
        GuardContext context)
    {
        GuardResult result = guard.decideTravel(action, decision, context);
        if (result.getOutcome() == GuardResult.Outcome.BLOCK)
        {
            event.consume();
        }
        return result;
    }
}
