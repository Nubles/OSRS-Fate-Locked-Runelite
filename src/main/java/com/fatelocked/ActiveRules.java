package com.fatelocked;

import com.fatelocked.rules.RulesSnapshot;
import java.time.Instant;

/**
 * The rules in force and where they came from. Immutable, and swapped as one
 * reference, so nothing ever sees a new bundle with an old source.
 */
final class ActiveRules
{
    static final ActiveRules NONE =
        new ActiveRules(FateLockedBundle.empty(), FateLockedPlugin.RulesSource.NONE);

    private final FateLockedBundle bundle;
    private final RulesSnapshot snapshot;
    private final FateLockedPlugin.RulesSource source;
    private final RulesPrecedence.Arrival arrival;
    private final Instant arrivedAt;

    /** Rules whose snapshot is built here, for callers that parsed on this thread. */
    ActiveRules(FateLockedBundle bundle, FateLockedPlugin.RulesSource source)
    {
        this(bundle, bundle == null ? null : RulesSnapshot.of(bundle), source, null, null);
    }

    /**
     * Rules with the snapshot their parse already built, off the client thread.
     *
     * @param arrival   how they arrived, or null when not known
     * @param arrivedAt when they arrived: now, or when the last start saved them; null when not known
     */
    ActiveRules(FateLockedBundle bundle, RulesSnapshot snapshot, FateLockedPlugin.RulesSource source,
        RulesPrecedence.Arrival arrival, Instant arrivedAt)
    {
        if (bundle == null || snapshot == null || source == null)
        {
            throw new IllegalArgumentException("rules, their snapshot and their source are required");
        }
        this.bundle = bundle;
        this.snapshot = snapshot;
        this.source = source;
        this.arrival = arrival;
        this.arrivedAt = arrivedAt;
    }

    FateLockedBundle getBundle()
    {
        return bundle;
    }

    RulesSnapshot getSnapshot()
    {
        return snapshot;
    }

    FateLockedPlugin.RulesSource getSource()
    {
        return source;
    }

    /** How the rules arrived, so the status card can say "restored from the last start"; null when not known. */
    RulesPrecedence.Arrival getArrival()
    {
        return arrival;
    }

    /** When the rules arrived, or were saved by the last start; null when not known. */
    Instant getArrivedAt()
    {
        return arrivedAt;
    }
}
