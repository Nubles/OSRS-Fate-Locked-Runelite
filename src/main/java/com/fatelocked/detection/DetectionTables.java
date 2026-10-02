package com.fatelocked.detection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The tracker's names for what RuneLite notices (Stage 4), from the bundle's rules.detection: each
 * boss by the names the game prints in its kill-count line, every quest, and every diary tier. A
 * detector that needs a table the bundle didn't send notices nothing rather than guess.
 */
public final class DetectionTables
{
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Recipe for Disaster's parts, by RuneLite's quest names, as the tracker names them. The last
     * part is the whole quest's finale.
     */
    private static final Map<String, String> RFD_PARTS = rfdParts();

    @Value
    public static class Boss
    {
        String key;
        boolean raid;
    }

    private final Map<String, Boss> bossesByKillCount;
    private final Map<String, String> questIds;
    private final Set<String> diaryTiers;

    private DetectionTables(Map<String, Boss> bossesByKillCount, Map<String, String> questIds, Set<String> diaryTiers)
    {
        this.bossesByKillCount = Collections.unmodifiableMap(bossesByKillCount);
        this.questIds = Collections.unmodifiableMap(questIds);
        this.diaryTiers = Collections.unmodifiableSet(diaryTiers);
    }

    /** No tables: bosses and quests aren't noticed, and every diary tier is. */
    public static DetectionTables none()
    {
        return new DetectionTables(new HashMap<>(), new HashMap<>(), new HashSet<>());
    }

    /** The tables in rules.detection, leaving out anything malformed; null when it isn't an object. */
    public static DetectionTables parse(JsonElement declaration)
    {
        if (declaration == null || !declaration.isJsonObject())
        {
            return null;
        }
        JsonObject detection = declaration.getAsJsonObject();
        Map<String, Boss> bosses = new HashMap<>();
        for (JsonObject boss : objects(detection.get("bosses")))
        {
            String key = string(boss.get("key"));
            JsonElement raid = boss.get("raid");
            if (key == null) continue;
            Boss value = new Boss(key, raid != null && raid.isJsonPrimitive() && raid.getAsJsonPrimitive().isBoolean()
                && raid.getAsBoolean());
            for (JsonElement name : array(boss.get("killCounts")))
            {
                String killCount = string(name);
                if (killCount != null) bosses.putIfAbsent(fold(killCount), value);
            }
        }
        // Every id first, so no quest's name can take another's id.
        Map<String, String> quests = new HashMap<>();
        List<JsonObject> questEntries = objects(detection.get("quests"));
        for (JsonObject quest : questEntries)
        {
            String id = string(quest.get("id"));
            if (id != null) quests.putIfAbsent(fold(id), id);
        }
        for (JsonObject quest : questEntries)
        {
            String id = string(quest.get("id"));
            String name = string(quest.get("name"));
            if (id != null && name != null) quests.putIfAbsent(fold(name), id);
        }
        Set<String> tiers = new HashSet<>();
        for (JsonElement tier : array(detection.get("diaryTiers")))
        {
            String id = string(tier);
            if (id != null) tiers.add(id);
        }
        return new DetectionTables(bosses, quests, tiers);
    }

    /** The boss a kill-count line names, or null for a count that is no boss's, such as an agility lap. */
    public Boss bossForKillCount(String name)
    {
        return bossesByKillCount.get(fold(name));
    }

    /** The tracker's id for a quest by RuneLite's name, or null for one the tracker doesn't list. */
    public String questId(String runeliteName)
    {
        String part = RFD_PARTS.get(fold(runeliteName));
        String id = part != null ? part : fold(runeliteName);
        return questIds.get(fold(id));
    }

    /** Whether the tracker lists this diary tier; any tier while the bundle sends no list. */
    public boolean isDiaryTier(String tierId)
    {
        return diaryTiers.isEmpty() || diaryTiers.contains(tierId);
    }

    static String fold(String value)
    {
        return value == null ? "" : WHITESPACE.matcher(value.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    private static String string(JsonElement element)
    {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return null;
        String value = element.getAsString().trim();
        return value.isEmpty() ? null : value;
    }

    private static JsonArray array(JsonElement element)
    {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static List<JsonObject> objects(JsonElement element)
    {
        List<JsonObject> objects = new ArrayList<>();
        for (JsonElement item : array(element))
        {
            if (item.isJsonObject()) objects.add(item.getAsJsonObject());
        }
        return objects;
    }

    private static Map<String, String> rfdParts()
    {
        Map<String, String> parts = new HashMap<>();
        parts.put(fold("Recipe for Disaster - Another Cook's Quest"), "RFD: The Cook");
        parts.put(fold("Recipe for Disaster - Mountain Dwarf"), "RFD: Dwarf");
        parts.put(fold("Recipe for Disaster - Wartface & Bentnoze"), "RFD: Goblins");
        parts.put(fold("Recipe for Disaster - Pirate Pete"), "RFD: Pirate Pete");
        parts.put(fold("Recipe for Disaster - Lumbridge Guide"), "RFD: Lumbridge Guide");
        parts.put(fold("Recipe for Disaster - Evil Dave"), "RFD: Evil Dave");
        parts.put(fold("Recipe for Disaster - Skrach Uglogwee"), "RFD: Skrach Uglogwee");
        parts.put(fold("Recipe for Disaster - Sir Amik Varze"), "RFD: Sir Amik Varze");
        parts.put(fold("Recipe for Disaster - King Awowogei"), "RFD: King Awowogei");
        parts.put(fold("Recipe for Disaster - Culinaromancer"), "RFD: Finale");
        return Collections.unmodifiableMap(parts);
    }
}
