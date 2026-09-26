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

    /** What the gate says about a trip, without touching the click. */
    public GuardResult decide(
        TravelAction action,
        TravelDecision decision,
        StrictModeReadiness readiness)
    {
        return guard.decideTravel(action, decision, readiness);
    }

    /** Asks the gate again, so nothing but its own BLOCK can consume the click. */
    public GuardResult handleTravel(
        MenuOptionClicked event,
        TravelAction action,
        TravelDecision decision,
        StrictModeReadiness readiness)
    {
        GuardResult result = guard.decideTravel(action, decision, readiness);
        if (result.getOutcome() == GuardResult.Outcome.BLOCK)
        {
            event.consume();
        }
        return result;
    }
}
