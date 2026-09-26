package com.fatelocked.rules;

import com.fatelocked.FateLockedBundle;

/**
 * The loaded rules, fixed at load time. {@link DecisionService} answers from
 * it; nothing else reads the bundle's decisions directly.
 */
public final class RulesSnapshot
{
    private static final RulesSnapshot EMPTY = new RulesSnapshot(FateLockedBundle.empty());

    private final FateLockedBundle bundle;
    private final LegacyRules legacy;

    private RulesSnapshot(FateLockedBundle bundle)
    {
        this.bundle = bundle;
        this.legacy = bundle.isLegacyRules() ? new LegacyRules(bundle) : null;
    }

    public static RulesSnapshot of(FateLockedBundle bundle)
    {
        return bundle == null || bundle.isEmpty() ? EMPTY : new RulesSnapshot(bundle);
    }

    public static RulesSnapshot empty()
    {
        return EMPTY;
    }

    /** True until rules are loaded: nothing is mapped. */
    public boolean isEmpty()
    {
        return bundle.isEmpty();
    }

    /** True for a v1–3 export, which carries no tracker decisions. */
    public boolean isLegacy()
    {
        return !isEmpty() && bundle.isLegacyRules();
    }

    FateLockedBundle bundle()
    {
        return bundle;
    }

    LegacyRules legacy()
    {
        return legacy;
    }
}
