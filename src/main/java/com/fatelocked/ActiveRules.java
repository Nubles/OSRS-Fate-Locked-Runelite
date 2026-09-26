package com.fatelocked;

/**
 * The rules in force and where they came from. Immutable, and swapped as one
 * reference, so nothing ever sees a new bundle with an old source.
 */
final class ActiveRules
{
    static final ActiveRules NONE =
        new ActiveRules(FateLockedBundle.empty(), FateLockedPlugin.RulesSource.NONE);

    private final FateLockedBundle bundle;
    private final FateLockedPlugin.RulesSource source;

    ActiveRules(FateLockedBundle bundle, FateLockedPlugin.RulesSource source)
    {
        if (bundle == null || source == null)
        {
            throw new IllegalArgumentException("rules and their source are required");
        }
        this.bundle = bundle;
        this.source = source;
    }

    FateLockedBundle getBundle()
    {
        return bundle;
    }

    FateLockedPlugin.RulesSource getSource()
    {
        return source;
    }
}
