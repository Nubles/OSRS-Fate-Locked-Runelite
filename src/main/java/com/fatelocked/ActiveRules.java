package com.fatelocked;

import com.fatelocked.rules.RulesSnapshot;

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

    /** Rules whose snapshot is built here, for callers that parsed on this thread. */
    ActiveRules(FateLockedBundle bundle, FateLockedPlugin.RulesSource source)
    {
        this(bundle, bundle == null ? null : RulesSnapshot.of(bundle), source);
    }

    /** Rules with the snapshot their parse already built, off the client thread. */
    ActiveRules(FateLockedBundle bundle, RulesSnapshot snapshot, FateLockedPlugin.RulesSource source)
    {
        if (bundle == null || snapshot == null || source == null)
        {
            throw new IllegalArgumentException("rules, their snapshot and their source are required");
        }
        this.bundle = bundle;
        this.snapshot = snapshot;
        this.source = source;
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
}
