package com.fatelocked.rules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
    /** The capabilities for every chunk's entry and what each non-land chunk is (R1). */
    public static final String CHUNK_ENTRIES = "chunkEntries";
    public static final String PLACES = "places";

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
    /** Stage 2: every land, ocean and interior chunk's entry, by "cx,cy"; null when the bundle doesn't send them. */
    @Getter(AccessLevel.NONE)
    @SerializedName("chunkEntries")
    private JsonElement chunkEntriesDeclaration;
    private transient Map<String, PermissionStatus> chunkEntries;
    /** Stage 2: what each chunk that isn't land is, by "cx,cy"; null when the bundle doesn't send it. */
    @Getter(AccessLevel.NONE)
    @SerializedName("places")
    private JsonElement placesDeclaration;
    private transient Map<String, Place> places;

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
        copy.chunkEntries = !copy.capabilities.contains(CHUNK_ENTRIES) ? null
            : chunkEntries != null ? chunkEntries : statuses(chunkEntriesDeclaration);
        copy.places = !copy.capabilities.contains(PLACES) ? null
            : places != null ? places : places(placesDeclaration);
        return copy;
    }

    /** A JSON string's value; null for anything else. */
    private static String string(JsonElement value)
    {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
            ? value.getAsString() : null;
    }

    /**
     * A JSON object's statuses by key: a status this build doesn't know reads
     * UNKNOWN, and a value that isn't a string is skipped. Null when it isn't an object.
     */
    private static Map<String, PermissionStatus> statuses(JsonElement declaration)
    {
        if (declaration == null || !declaration.isJsonObject()) return null;
        Map<String, PermissionStatus> statuses = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : declaration.getAsJsonObject().entrySet())
        {
            String status = string(entry.getValue());
            if (status == null) continue;
            try
            {
                statuses.put(entry.getKey(), PermissionStatus.valueOf(status));
            }
            catch (IllegalArgumentException unknown)
            {
                statuses.put(entry.getKey(), PermissionStatus.UNKNOWN);
            }
        }
        return Collections.unmodifiableMap(statuses);
    }

    /** Each place by key, skipping a value that isn't an object with a kind. Null when it isn't an object. */
    private static Map<String, Place> places(JsonElement declaration)
    {
        if (declaration == null || !declaration.isJsonObject()) return null;
        Map<String, Place> places = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : declaration.getAsJsonObject().entrySet())
        {
            if (entry.getValue() == null || !entry.getValue().isJsonObject()) continue;
            JsonObject value = entry.getValue().getAsJsonObject();
            String kind = string(value.get("kind"));
            if (kind == null) continue;
            List<String> entrances = stringItems(value.get("entrances"));
            places.put(entry.getKey(), new Place(kind, string(value.get("name")), string(value.get("area")),
                entrances == null ? Collections.emptyList() : entrances));
        }
        return Collections.unmodifiableMap(places);
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

    /** A chunk that isn't land: the ocean, or an interior with its name, area and the chunks it is entered from. */
    @Getter
    public static final class Place
    {
        private final String kind;
        /** The interior's name, such as "Mor Ul Rek · Outer Area"; null when the tracker has none. */
        private final String name;
        /** The named area the interior belongs to; null when it has none. */
        private final String area;
        private final List<String> entrances;

        public Place(String kind, String name, String area, List<String> entrances)
        {
            this.kind = kind;
            this.name = name;
            this.area = area;
            this.entrances = entrances;
        }

        public boolean isOcean()
        {
            return "ocean".equals(kind);
        }
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
