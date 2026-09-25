package com.fatelocked.guardian;

import lombok.Value;

/**
 * What Strict Mode can do right now: off, paused, active, or inactive with
 * the reason it cannot block anything. Built from the same facts as the
 * travel trust gate, so the sidebar never says it is on while every click is
 * let through.
 */
@Value
public class StrictModeReadiness
{
    public enum State { OFF, PAUSED, ACTIVE, INACTIVE }

    State state;
    /** Why Strict Mode cannot act; null unless {@link State#INACTIVE}. */
    String reason;

    public static StrictModeReadiness evaluate(
        boolean enabled,
        boolean paused,
        boolean hasCurrentRules,
        String boundAccount,
        String loggedInAs,
        boolean accountMatches,
        boolean freshRules)
    {
        if (!enabled) return new StrictModeReadiness(State.OFF, null);
        if (paused) return new StrictModeReadiness(State.PAUSED, null);
        if (!hasCurrentRules) return inactive("no tracker rules are loaded");
        if (boundAccount == null || boundAccount.trim().isEmpty())
        {
            return inactive("the tracker profile has no linked account");
        }
        if (loggedInAs == null || loggedInAs.trim().isEmpty())
        {
            return inactive("you are not logged in");
        }
        if (!accountMatches)
        {
            return inactive("the profile is for " + boundAccount.trim()
                + "; you're logged in as " + loggedInAs.trim());
        }
        if (!freshRules)
        {
            return inactive("the rules are more than 15 minutes old");
        }
        return new StrictModeReadiness(State.ACTIVE, null);
    }

    private static StrictModeReadiness inactive(String reason)
    {
        return new StrictModeReadiness(State.INACTIVE, reason);
    }
}
