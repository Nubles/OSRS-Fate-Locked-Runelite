package com.fatelocked.guardian.travel;

import com.fatelocked.rules.FateRuleEngine;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RuleDecision;

public class TravelRuleEvaluator
{
    public TravelDecision evaluate(TravelAction action, FateRuleEngine rules)
    {
        if (action == null
            || action.getConfidence() != TravelAction.Confidence.EXACT
            || action.getDestination() == null
            || rules == null)
        {
            return unknown(action);
        }

        RuleDecision destination = rules.entry(action.getDestination());
        if (destination.getStatus() == PermissionStatus.LOCKED)
        {
            return new TravelDecision(
                PermissionStatus.LOCKED,
                label(action),
                destination.getLabel() + " is locked");
        }
        if (destination.getStatus() == PermissionStatus.UNKNOWN
            || destination.getStatus() == PermissionStatus.NOT_READY)
        {
            return unknown(action);
        }

        String requiredUnlock = action.getRequiredUnlock();
        if (requiredUnlock != null && !requiredUnlock.trim().isEmpty())
        {
            RuleDecision mobility = rules.mobility(requiredUnlock);
            if (mobility.getStatus() == PermissionStatus.LOCKED)
            {
                return new TravelDecision(
                    PermissionStatus.LOCKED,
                    label(action),
                    mobility.getReason());
            }
            if (mobility.getStatus() == PermissionStatus.UNKNOWN
                || mobility.getStatus() == PermissionStatus.NOT_READY)
            {
                return unknown(action);
            }
        }

        return new TravelDecision(PermissionStatus.ALLOWED, label(action), null);
    }

    private static TravelDecision unknown(TravelAction action)
    {
        return new TravelDecision(PermissionStatus.UNKNOWN, label(action), null);
    }

    private static String label(TravelAction action)
    {
        if (action == null || action.getLabel() == null
            || action.getLabel().trim().isEmpty())
        {
            return "Unknown travel";
        }
        return action.getLabel();
    }
}