package com.fatelocked.guardian.travel;

import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;

/**
 * A trip's decision (F3, G6): the tracker's own decision for the option
 * clicked, which counts the unlocks the method needs and where it lands.
 * One destination is the invariant: an option that can go to several
 * places stays Unknown even when the tracker locks it, since the place is
 * picked after the click.
 */
public class TravelRuleEvaluator
{
    public TravelDecision evaluate(TravelMatch match, TravelAction action, DecisionService rules)
    {
        String label = label(action);
        if (match == null || rules == null || action == null || action.getDestination() == null)
        {
            return new TravelDecision(PermissionStatus.UNKNOWN, label, null);
        }
        Decision decision = rules.travel(match.getMethod(), match.getOption());
        return new TravelDecision(decision.getStatus(), label, decision.getReason());
    }

    private static String label(TravelAction action)
    {
        if (action == null || action.getLabel() == null || action.getLabel().trim().isEmpty())
        {
            return "Unknown travel";
        }
        return action.getLabel();
    }
}
