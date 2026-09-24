package com.fatelocked.guardian;

import com.fatelocked.guardian.travel.TravelAction;
import com.fatelocked.guardian.travel.TravelDecision;
import net.runelite.api.events.MenuOptionClicked;

/**
 * The only place the plugin consumes a click, and only for exactly matched
 * travel that fresh, account-bound rules prove locked. PluginHubClickBoundaryTest
 * pins that no other code consumes or rewrites menu clicks.
 */
public final class StrictModeClickHandler
{
    private final StrictModeGuard guard;

    public StrictModeClickHandler(StrictModeGuard guard)
    {
        this.guard = guard;
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
