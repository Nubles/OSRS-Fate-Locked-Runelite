package com.fatelocked.rules;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;

import java.util.List;
import java.util.Map;

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

    /** The root-field area name ("Falador · Asgarnia") older exports and unmapped chunks fall back to. */
    String areaLabel(CanonicalChunk chunk)
    {
        return bundle.labelAt(chunk);
    }

    /** The root-field region (continent), as {@link #areaLabel} falls back. */
    String regionAt(CanonicalChunk chunk)
    {
        return bundle.regionAt(chunk);
    }

    /**
     * Whether the bank at a chunk is rolled, or the run doesn't lock banks.
     * Every export carries this in its root fields, and the golden bundles
     * pin it to the tracker's own bank answers.
     */
    boolean bankRolled(CanonicalChunk chunk)
    {
        return bundle.isBankUnlocked(chunk);
    }

    /** An older export's content lists for a chunk ("mon", "shop", "farm", "poi"). */
    Map<String, List<String>> legacyContent(CanonicalChunk chunk)
    {
        return bundle.legacyContentAt(chunk);
    }
}
