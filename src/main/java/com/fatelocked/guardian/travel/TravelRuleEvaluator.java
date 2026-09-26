package com.fatelocked.guardian.travel;

import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;

public class TravelRuleEvaluator
{
    /**
     * Whether an exactly matched trip is allowed: its destination's entry,
     * then the unlock it needs. Only the tracker's own chunk decisions count,
     * so another character, an unmapped place and an older export's areas
     * all stay Unknown and Strict Mode never acts on them.
     */
    public TravelDecision evaluate(TravelAction action, DecisionService rules)
    {
        if (action == null
            || action.getConfidence() != TravelAction.Confidence.EXACT
            || action.getDestination() == null
            || rules == null)
        {
            return unknown(action);
        }

        Decision destination = rules.chunk(action.getDestination());
        if (destination.getSource() != Decision.Source.CHUNK)
        {
            return unknown(action);
        }
        if (destination.getStatus() == PermissionStatus.LOCKED)
        {
            return new TravelDecision(
                PermissionStatus.LOCKED,
                label(action),
                destination.getLabel() + " is locked");
        }
        if (destination.getStatus() != PermissionStatus.ALLOWED)
        {
            return unknown(action);
        }

        String requiredUnlock = action.getRequiredUnlock();
        if (requiredUnlock != null && !requiredUnlock.trim().isEmpty())
        {
            Decision mobility = rules.mobility(requiredUnlock);
            if (mobility.getStatus() == PermissionStatus.LOCKED)
            {
                return new TravelDecision(
                    PermissionStatus.LOCKED,
                    label(action),
                    mobility.getReason());
            }
            if (mobility.getStatus() != PermissionStatus.ALLOWED)
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
