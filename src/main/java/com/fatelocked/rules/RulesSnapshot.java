package com.fatelocked.rules;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;

import java.util.Collections;
import java.util.HashMap;
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
    /** The tracker's chunks with a BANK row, and with SHOPS rows. */
    private final Set<CanonicalChunk> bankChunks;
    private final Set<CanonicalChunk> shopChunks;
    /** The tracker's frontier for a Chunked run, when the rules send it; null otherwise. */
    private final Set<CanonicalChunk> frontier;
    /** Every land, ocean and interior chunk's entry, when the rules send them; empty otherwise. */
    private final Map<CanonicalChunk, PermissionStatus> entries;
    /** What each chunk that isn't land is, when the rules send it; empty otherwise. */
    private final Map<CanonicalChunk, RuneliteRulesManifest.Place> places;
    /** The tracker's bank table, when the rules send it: each bank by its chunk, and by the chunks its facilities are in. */
    private final Map<CanonicalChunk, RuneliteRulesManifest.Bank> banksAt;
    private final Map<CanonicalChunk, RuneliteRulesManifest.Bank> banksIn;

    private RulesSnapshot(FateLockedBundle bundle)
    {
        this.bundle = bundle;
        this.legacy = bundle.isLegacyRules() ? new LegacyRules(bundle) : null;
        this.mapped = Collections.unmodifiableSet(mapped(bundle));
        Progress tracked = bundle.isLegacyRules() ? null : bundle.getRules().getProgress();
        this.progress = tracked != null ? tracked : new Progress(
            bundle.isChunkedBundle() ? Progress.CHUNKS : Progress.AREAS,
            bundle.getUnlockedAreas(), bundle.getTotalAreas(), bundle.getUnlockedChunks(), bundle.getTotalChunks());
        this.bankChunks = Collections.unmodifiableSet(withRows(bundle, mapped, "BANKS", "BANK"));
        this.shopChunks = Collections.unmodifiableSet(withRows(bundle, mapped, "SHOPS", null));
        List<String> frontierKeys = bundle.isLegacyRules() ? null : bundle.getRules().getFrontier();
        this.frontier = frontierKeys == null ? null : Collections.unmodifiableSet(chunks(frontierKeys));
        RuneliteRulesManifest manifest = bundle.isLegacyRules() ? null : bundle.getRules();
        this.entries = Collections.unmodifiableMap(byChunk(manifest == null ? null : manifest.getChunkEntries()));
        this.places = Collections.unmodifiableMap(byChunk(manifest == null ? null : manifest.getPlaces()));
        Map<CanonicalChunk, RuneliteRulesManifest.Bank> at = new HashMap<>();
        Map<CanonicalChunk, RuneliteRulesManifest.Bank> in = new HashMap<>();
        if (manifest != null && manifest.getBanks() != null)
        {
            for (RuneliteRulesManifest.Bank bank : manifest.getBanks().values())
            {
                for (CanonicalChunk chunk : chunks(Collections.singletonList(bank.getAt()))) at.putIfAbsent(chunk, bank);
                for (CanonicalChunk chunk : chunks(bank.getPhysical())) in.putIfAbsent(chunk, bank);
            }
        }
        this.banksAt = Collections.unmodifiableMap(at);
        this.banksIn = Collections.unmodifiableMap(in);
    }

    /** A map by "cx,cy" key, keyed by chunk instead, skipping keys that aren't one. */
    private static <T> Map<CanonicalChunk, T> byChunk(Map<String, T> byKey)
    {
        Map<CanonicalChunk, T> byChunk = new HashMap<>();
        if (byKey == null) return byChunk;
        for (Map.Entry<String, T> entry : byKey.entrySet())
        {
            for (CanonicalChunk chunk : chunks(Collections.singletonList(entry.getKey())))
            {
                byChunk.put(chunk, entry.getValue());
            }
        }
        return byChunk;
    }

    /** The chunks of "cx,cy" keys, skipping any that aren't one. */
    private static Set<CanonicalChunk> chunks(Iterable<String> keys)
    {
        Set<CanonicalChunk> chunks = new LinkedHashSet<>();
        for (String key : keys)
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

    /** The mapped chunks with a row in a category (of one target kind, when given). */
    private static Set<CanonicalChunk> withRows(
        FateLockedBundle bundle, Set<CanonicalChunk> mapped, String category, String targetKind)
    {
        Set<CanonicalChunk> chunks = new LinkedHashSet<>();
        if (bundle.isLegacyRules()) return chunks;
        for (CanonicalChunk chunk : mapped)
        {
            ChunkPermissionSnapshot snapshot = bundle.permissionsAt(chunk).orElse(null);
            List<ChunkPermissionRow> rows = snapshot == null ? null : snapshot.getCategories().get(category);
            if (rows == null) continue;
            for (ChunkPermissionRow row : rows)
            {
                if (targetKind == null || targetKind.equalsIgnoreCase(row.getTargetKind()))
                {
                    chunks.add(chunk);
                    break;
                }
            }
        }
        return chunks;
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
        chunks.addAll(chunks(bundle.getRules().getChunks().keySet()));
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

    /** The tracker's chunks with a bank, fixed when the rules load (all its banks, R5). */
    Set<CanonicalChunk> bankChunks()
    {
        return bankChunks;
    }

    /** The tracker's chunks with a shop, fixed when the rules load. */
    Set<CanonicalChunk> shopChunks()
    {
        return shopChunks;
    }

    /** The run's progress, as the tracker counts it or else from the rules' area lists, fixed when they load. */
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

    /** The tracker's travel table; null without rules, or for older rules, which have none (F1). */
    TravelTable travelTable()
    {
        return isEmpty() || isLegacy() || bundle.getRules() == null ? null : bundle.getRules().getTravel();
    }

    /** Whether the rules send each Slayer task's decision. */
    boolean hasSlayerTasks()
    {
        return !isLegacy() && bundle.getRules().getSlayerTasks() != null;
    }

    /** The tracker's decision for a Slayer task key ("master:task" or "task"); null when it has none. */
    RuneliteRulesManifest.SlayerTask slayerTaskAt(String key)
    {
        return hasSlayerTasks() ? bundle.getRules().getSlayerTasks().get(FateLockedBundle.slayerKey(key)) : null;
    }

    /** The tracker's Slayer index entry for a key; null when it has none. */
    Set<CanonicalChunk> slayerChunks(String key)
    {
        return bundle.slayerChunks(key);
    }

    /** The chunks whose monster lists name a monster, for tasks the Slayer index misses. */
    Set<CanonicalChunk> monsterChunks(String name)
    {
        return bundle.monsterChunks(name);
    }

    /**
     * In a Chunked run, a chunk the run may roll next: the tracker's own
     * frontier, which with Sailing reaches land across the sea (R4), or for
     * older rules a locked chunk next to an owned one.
     */
    boolean isFrontier(CanonicalChunk chunk)
    {
        return frontier != null ? frontier.contains(chunk) : bundle.isFrontierChunk(chunk);
    }

    /** A chunk's entry as the tracker's chunkEntries give it, the ocean and interiors included; null when they don't. */
    PermissionStatus entryAt(CanonicalChunk chunk)
    {
        return entries.get(chunk);
    }

    /** Whether the rules send the tracker's bank table. */
    boolean hasBankTable()
    {
        return !isLegacy() && bundle.getRules().getBanks() != null;
    }

    /** The bank whose facilities are in a chunk, as the tracker's bank table has it; null when none is. */
    RuneliteRulesManifest.Bank bankIn(CanonicalChunk chunk)
    {
        return banksIn.get(chunk);
    }

    /** Each bank in the tracker's table by its chunk: where it is, or the entrance to it. */
    Map<CanonicalChunk, RuneliteRulesManifest.Bank> banksAt()
    {
        return banksAt;
    }

    /** What a chunk that isn't land is, as the rules' places say; null for land or when they don't. */
    RuneliteRulesManifest.Place placeAt(CanonicalChunk chunk)
    {
        return places.get(chunk);
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
