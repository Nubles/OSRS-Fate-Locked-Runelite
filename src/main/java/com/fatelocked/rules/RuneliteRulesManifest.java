package com.fatelocked.rules;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Getter
public final class RuneliteRulesManifest
{
    /** The capability that says a bundle sends the Chunked frontier (R4). */
    public static final String FRONTIER = "frontier";

    private String rulesVersion;
    private int contentVersion;
    private int detectorContractVersion;
    private String runId;
    private long runRevision;
    private String account;
    private String gameModeId;
    private String exportedAt;
    private boolean bankLocks;
    @Getter(AccessLevel.NONE)
    @SerializedName("knownMobility")
    private JsonElement knownMobilityDeclaration;
    private transient List<String> knownMobility;
    private Unlocks unlocks;
    private Map<String, ItemRule> itemRules;
    private Map<String, ChunkPermissionSnapshot> chunks;
    /**
     * Stage 2: the sections the bundle has, by capability (R15). A section is
     * read only when this names it, and ids this build doesn't know are
     * ignored. Read leniently: a malformed list names nothing.
     */
    @Getter(AccessLevel.NONE)
    @SerializedName("capabilities")
    private JsonElement capabilitiesDeclaration;
    private transient Set<String> capabilities;
    /** Stage 2, Chunked runs: the chunks the run may roll next ("cx,cy"); null when the bundle doesn't send it. */
    @Getter(AccessLevel.NONE)
    @SerializedName("frontier")
    private JsonElement frontierDeclaration;
    private transient List<String> frontier;

    public RuneliteRulesManifest normalized()
    {
        RuneliteRulesManifest copy = new RuneliteRulesManifest();
        copy.rulesVersion = rulesVersion;
        copy.contentVersion = Math.max(0, contentVersion);
        copy.detectorContractVersion = Math.max(0, detectorContractVersion);
        copy.runId = runId;
        copy.runRevision = Math.max(0, runRevision);
        copy.account = account;
        copy.gameModeId = gameModeId;
        copy.exportedAt = exportedAt;
        copy.bankLocks = bankLocks;
        copy.knownMobility = knownMobility == null
            ? immutableStringList(knownMobilityDeclaration)
            : Unlocks.immutableList(knownMobility);
        copy.unlocks = unlocks == null ? new Unlocks().normalized() : unlocks.normalized();
        Map<String, ItemRule> normalizedItems = new TreeMap<>();
        if (itemRules != null)
        {
            for (Map.Entry<String, ItemRule> entry : itemRules.entrySet())
            {
                if (entry.getValue() != null)
                {
                    normalizedItems.put(entry.getKey(), entry.getValue().normalized());
                }
            }
        }
        copy.itemRules = Collections.unmodifiableMap(normalizedItems);

        Map<String, ChunkPermissionSnapshot> normalizedChunks = new LinkedHashMap<>();
        if (chunks != null)
        {
            for (Map.Entry<String, ChunkPermissionSnapshot> entry :
                new TreeMap<>(chunks).entrySet())
            {
                if (entry.getValue() != null)
                {
                    normalizedChunks.put(entry.getKey(),
                        entry.getValue().normalized(entry.getKey()));
                }
            }
        }
        copy.chunks = Collections.unmodifiableMap(normalizedChunks);
        copy.capabilities = capabilities == null ? strings(capabilitiesDeclaration) : capabilities;
        copy.frontier = !copy.capabilities.contains(FRONTIER) ? null
            : frontier != null ? frontier : stringItems(frontierDeclaration);
        return copy;
    }

    /** A JSON array's strings, skipping anything else; nothing when it isn't an array. */
    private static Set<String> strings(JsonElement declaration)
    {
        List<String> items = stringItems(declaration);
        return items == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(items));
    }

    /** A JSON array's strings, skipping anything else; null when it isn't an array. */
    private static List<String> stringItems(JsonElement declaration)
    {
        if (declaration == null || !declaration.isJsonArray()) return null;
        List<String> values = new ArrayList<>();
        for (JsonElement value : declaration.getAsJsonArray())
        {
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())
            {
                values.add(value.getAsString());
            }
        }
        return Collections.unmodifiableList(values);
    }

    private static List<String> immutableStringList(JsonElement declaration)
    {
        if (declaration == null || !declaration.isJsonArray())
        {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement value : declaration.getAsJsonArray())
        {
            if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString())
            {
                return Collections.emptyList();
            }
            values.add(value.getAsString());
        }
        return Collections.unmodifiableList(values);
    }

    public boolean hasRequiredFields()
    {
        return rulesVersion != null && !rulesVersion.trim().isEmpty()
            && runId != null && !runId.trim().isEmpty()
            && gameModeId != null && !gameModeId.trim().isEmpty()
            && exportedAt != null && !exportedAt.trim().isEmpty()
            && unlocks != null && chunks != null;
    }

    @Getter
    public static final class ItemRule
    {
        private int tier;
        private String slot;

        private ItemRule normalized()
        {
            ItemRule copy = new ItemRule();
            copy.tier = Math.max(0, tier);
            copy.slot = slot;
            return copy;
        }
    }
    @Getter
    public static final class Unlocks
    {
        private List<String> regions;
        private List<String> chunks;
        private Map<String, Integer> skills;
        private Map<String, Integer> levels;
        private Map<String, Integer> equipment;
        private List<String> banks;
        private List<String> merchants;
        private List<String> bosses;
        private List<String> minigames;
        private List<String> mobility;
        private List<String> arcana;
        private List<String> guilds;
        private List<String> farming;
        private List<String> slayer;
        private List<String> quests;

        private Unlocks normalized()
        {
            Unlocks copy = new Unlocks();
            copy.regions = immutableList(regions);
            copy.chunks = immutableList(chunks);
            copy.skills = immutableMap(skills);
            copy.levels = immutableMap(levels);
            copy.equipment = immutableMap(equipment);
            copy.banks = immutableList(banks);
            copy.merchants = immutableList(merchants);
            copy.bosses = immutableList(bosses);
            copy.minigames = immutableList(minigames);
            copy.mobility = immutableList(mobility);
            copy.arcana = immutableList(arcana);
            copy.guilds = immutableList(guilds);
            copy.farming = immutableList(farming);
            copy.slayer = immutableList(slayer);
            copy.quests = immutableList(quests);
            return copy;
        }

        private static List<String> immutableList(List<String> values)
        {
            return values == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
        }

        private static Map<String, Integer> immutableMap(Map<String, Integer> values)
        {
            return values == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new TreeMap<>(values));
        }
    }
}
