package com.fatelocked.rules;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The loaded rules, fixed at load time. {@link DecisionService} answers from
 * it; nothing else reads the bundle's decisions directly.
 */
public final class RulesSnapshot
{
    private static final RulesSnapshot EMPTY = new RulesSnapshot(FateLockedBundle.empty());

    private final FateLockedBundle bundle;
    private final LegacyRules legacy;
    private final Set<CanonicalChunk> mapped;
    private final Progress progress;

    private RulesSnapshot(FateLockedBundle bundle)
    {
        this.bundle = bundle;
        this.legacy = bundle.isLegacyRules() ? new LegacyRules(bundle) : null;
        this.mapped = Collections.unmodifiableSet(mapped(bundle));
        this.progress = new Progress(bundle.getUnlockedAreas(), bundle.getTotalAreas(),
            bundle.getUnlockedChunks(), bundle.getTotalChunks());
    }

    /** The tracker's chunk keys ("cx,cy"), or an older export's area chunks. */
    private static Set<CanonicalChunk> mapped(FateLockedBundle bundle)
    {
        Set<CanonicalChunk> chunks = new LinkedHashSet<>();
        if (bundle.isLegacyRules())
        {
            for (Set<CanonicalChunk> region : bundle.getRegionChunks().values()) chunks.addAll(region);
            return chunks;
        }
        for (String key : bundle.getRules().getChunks().keySet())
        {
            String[] xy = key.split(",");
            if (xy.length != 2) continue;
            try
            {
                chunks.add(new CanonicalChunk(Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim())));
            }
            catch (NumberFormatException ignored)
            {
                // Not a chunk key: nothing to draw for it.
            }
        }
        return chunks;
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

    /** Today's unlock counts from the rules' area lists, fixed when they load. */
    Progress progress()
    {
        return progress;
    }

    /** Every chunk the rules decide, fixed when they load. */
    Set<CanonicalChunk> mappedChunks()
    {
        return mapped;
    }

    /** The named areas the rules list (Falador, Draynor Village…), with their chunks. */
    Map<String, Set<CanonicalChunk>> areas()
    {
        return bundle.getSubAreaChunks();
    }

    /** Whether a named area is unlocked; the golden bundles pin it to the tracker's answers. */
    boolean isAreaUnlocked(String name)
    {
        return bundle.isUnlocked(name);
    }

    /** In a Chunked run, a locked chunk next to an owned one. */
    boolean isFrontier(CanonicalChunk chunk)
    {
        return bundle.isFrontierChunk(chunk);
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
