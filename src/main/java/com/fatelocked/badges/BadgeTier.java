package com.fatelocked.badges;

import com.fatelocked.rules.Progress;

/**
 * A run's milestone, as the relay's badge list numbers it (CONTRACT section 2): 0 under 25%,
 * 1 Wayfarer at 25%, 2 Pathfinder at 50%, 3 Trailblazer at 75% and 4 Fate Unbound at 100%.
 */
public final class BadgeTier
{
    /** No milestone: the rules count nothing, or the tier isn't one the relay knows. */
    public static final int NONE = -1;
    public static final int UNDER_25 = 0;
    public static final int WAYFARER = 1;
    public static final int PATHFINDER = 2;
    public static final int TRAILBLAZER = 3;
    public static final int FATE_UNBOUND = 4;
    /** How many tiers there are, and so how many icons. */
    public static final int COUNT = 5;

    private BadgeTier()
    {
    }

    /** The tier for a progress percent, as {@link Progress#percent()} rounds it; NONE below 0. */
    public static int ofPercent(int percent)
    {
        if (percent < 0)
        {
            return NONE;
        }
        if (percent >= 100)
        {
            return FATE_UNBOUND;
        }
        if (percent >= 75)
        {
            return TRAILBLAZER;
        }
        if (percent >= 50)
        {
            return PATHFINDER;
        }
        return percent >= 25 ? WAYFARER : UNDER_25;
    }

    /**
     * The tier of a run's progress; NONE when there is none or it counts nothing. Fate Unbound
     * needs everything unlocked: percent() rounds 374 of 375 up to 100.
     */
    public static int of(Progress progress)
    {
        if (progress == null)
        {
            return NONE;
        }
        int tier = ofPercent(progress.percent());
        return tier == FATE_UNBOUND && progress.getUnlocked() < progress.getTotal() ? TRAILBLAZER : tier;
    }

    /** Whether the relay knows this tier. */
    public static boolean valid(int tier)
    {
        return tier >= UNDER_25 && tier <= FATE_UNBOUND;
    }
}
