package com.fatelocked;

import com.fatelocked.guardian.StrictModeReadiness;

/**
 * The one chat line when Strict Mode stops blocking because the tracker's rules are old
 * (ECO-3), and one more when fresh rules bring it back. Pure apart from remembering
 * whether it has spoken; called each tick.
 *
 * <p>It speaks only when the rules' age is the one thing keeping Strict Mode from acting:
 * the readiness gives the stale reason only after the setting, the rules, the travel table
 * and the character have all checked out. Then it stays quiet until fresh rules arrive,
 * however long they take, so a long session hears it once.
 */
final class StaleRulesNotice
{
    static final String RESUMED = "Fresh rules arrived from the tracker: Strict Mode is blocking again.";

    private boolean told;

    /**
     * @param readiness what Strict Mode can do this tick
     * @param freshness FreshnessPolicy's answer for the rules in force
     * @return the line to say in chat now, or null
     */
    String next(StrictModeReadiness readiness, FreshnessPolicy.Verdict freshness)
    {
        if (freshness.isFresh())
        {
            boolean resumed = told && readiness.getState() == StrictModeReadiness.State.ACTIVE;
            told = false;
            return resumed ? RESUMED : null;
        }
        if (told || !freshness.isTrackerRulesOld()
            || readiness.getState() != StrictModeReadiness.State.INACTIVE
            || !StrictModeReadiness.STALE_RULES.equals(readiness.getReason()))
        {
            return null;
        }
        told = true;
        return StatusCardPresenter.oldRules(freshness, true) + " " + StatusCardPresenter.REFRESH;
    }
}
