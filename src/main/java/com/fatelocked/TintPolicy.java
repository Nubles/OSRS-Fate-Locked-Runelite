package com.fatelocked;

import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;

import java.awt.Color;

/**
 * Which tint a chunk takes on the scene and the minimap (B7), from the
 * decision service, so the tints say what the sidebar and the HUD say.
 * NOT_READY is owned, so it tints as unlocked until Stage 3's palette.
 * Another character's rules and chunks the rules don't map tint as unknown.
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

    static boolean isLocked(DecisionService decisions, CanonicalChunk chunk)
    {
        return at(decisions, chunk) == Tint.LOCKED;
    }

    /** The player's colour for a tint, or null for a chunk the rules don't decide, which isn't tinted. */
    static Color color(Tint tint, FateLockedConfig config)
    {
        switch (tint)
        {
            case UNLOCKED: return config.unlockedColor();
            case LOCKED: return config.lockedColor();
            default: return null;
        }
    }
}
