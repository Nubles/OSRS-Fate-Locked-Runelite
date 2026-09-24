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
    public GuardResult decideTravel(
        TravelAction action,
        TravelDecision decision,
        GuardContext context)
    {
        if (action == null || decision == null || context == null
            || !context.isEnabled() || context.isPaused()
            || !context.isAccountMatches() || !context.isFreshRules()
            || action.getConfidence() != TravelAction.Confidence.EXACT)
        {
            return allow();
        }

        RuleDecision rule = new RuleDecision(
            decision.getStatus(), decision.getLabel(), decision.getReason());
        return decision.getStatus() == PermissionStatus.LOCKED
            ? new GuardResult(GuardResult.Outcome.BLOCK, rule)
            : allow(rule);
    }

    private static GuardResult allow()
    {
        return new GuardResult(GuardResult.Outcome.ALLOW, null);
    }

    private static GuardResult allow(RuleDecision decision)
    {
        return new GuardResult(GuardResult.Outcome.ALLOW, decision);
    }
}
