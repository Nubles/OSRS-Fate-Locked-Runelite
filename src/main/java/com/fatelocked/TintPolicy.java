package com.fatelocked;

import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;

/**
 * How the rules tint a chunk for the edges and the locked land drawn in game (B7, U3), from
 * the decision service, so they say what the sidebar and the HUD say. NOT_READY is owned, so
 * it tints as unlocked. Another character's rules and chunks the rules don't map tint as
 * unknown, which draws nothing.
 */
final class TintPolicy
{
    enum Tint { UNLOCKED, LOCKED, UNKNOWN }

    private TintPolicy()
    {
    }

    static Tint of(Decision decision)
    {
        switch (decision.getStatus())
        {
            case ALLOWED:
            case NOT_READY:
                return Tint.UNLOCKED;
            case LOCKED:
                return Tint.LOCKED;
            default:
                return Tint.UNKNOWN;
        }
    }

    static Tint at(DecisionService decisions, CanonicalChunk chunk)
    {
        return of(decisions.chunk(chunk));
    }
}
