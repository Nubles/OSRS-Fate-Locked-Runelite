package com.fatelocked.guardian;

import com.fatelocked.guardian.travel.TravelAction;
import com.fatelocked.guardian.travel.TravelDecision;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RuleDecision;

/**
 * Strict Mode decides only exactly matched travel. Walking, NPCs, objects,
 * banks and equipment are never blocked; the menu tags and the locked-bank
 * and over-tier gear warnings cover them.
 */
public final class StrictModeGuard
{
    /**
     * A trip is stopped only when the readiness the sidebar shows is ACTIVE,
     * the travel is exactly matched to a destination, and the rules prove it
     * LOCKED. The same trip while Strict Mode is paused is let through as
     * {@link GuardResult.Outcome#ALLOW_PAUSED}, so it can be recorded.
     */
    public GuardResult decideTravel(
        TravelAction action,
        TravelDecision decision,
        StrictModeReadiness readiness)
    {
        RuleDecision rule = decision == null ? null
            : new RuleDecision(decision.getStatus(), decision.getLabel(), decision.getReason());
        if (readiness == null || !provesLocked(action, decision))
        {
            return new GuardResult(GuardResult.Outcome.ALLOW, rule);
        }
        switch (readiness.getState())
        {
            case ACTIVE:
                return new GuardResult(GuardResult.Outcome.BLOCK, rule);
            case PAUSED:
                return new GuardResult(GuardResult.Outcome.ALLOW_PAUSED, rule);
            default:
                return new GuardResult(GuardResult.Outcome.ALLOW, rule);
        }
    }

    /** Exactly matched travel to a known destination that the rules prove locked. */
    private static boolean provesLocked(TravelAction action, TravelDecision decision)
    {
        return action != null && decision != null
            && action.getConfidence() == TravelAction.Confidence.EXACT
            && action.getDestination() != null
            && decision.getStatus() == PermissionStatus.LOCKED;
    }
}
