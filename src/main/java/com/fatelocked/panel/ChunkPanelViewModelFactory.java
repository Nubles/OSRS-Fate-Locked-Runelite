package com.fatelocked.panel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.rules.ChunkPermissionRow;
import com.fatelocked.rules.ChunkPermissionSnapshot;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The sidebar's chunk card and its in-game twin, from the decision service
 * (B1), so they say what every other surface says. On another character, or
 * with no rules, the card is Unknown and says why.
 */
public final class ChunkPanelViewModelFactory
{
    private static final List<String> ORDER = Arrays.asList(
        "SKILLING", "BANKS", "SHOPS", "QUESTS", "COMBAT",
        "TRAVEL", "FARMING", "ACTIVITIES");

    private static final Map<String, String> TITLES;
    static
    {
        Map<String, String> titles = new LinkedHashMap<>();
        titles.put("SKILLING", "Skilling");
        titles.put("BANKS", "Banks");
        titles.put("SHOPS", "Shops");
        titles.put("QUESTS", "Quests");
        titles.put("COMBAT", "Combat");
        titles.put("TRAVEL", "Travel");
        titles.put("FARMING", "Farming");
        titles.put("ACTIVITIES", "Activities");
        TITLES = Collections.unmodifiableMap(titles);
    }

    public ChunkPanelViewModel create(
        DecisionService decisions,
        CanonicalChunk chunk,
        Instant importedAt)
    {
        PermissionStatus entry = decisions.chunk(chunk).getStatus();
        ChunkPermissionSnapshot snapshot = decisions.details(chunk).orElse(null);
        List<ChunkPanelViewModel.CategoryView> categories = new ArrayList<>();
        int allowed = 0;
        int notReady = 0;
        int locked = 0;
        int unknown = 0;

        if (snapshot != null)
        {
            for (String id : ORDER)
            {
                List<ChunkPermissionRow> source = snapshot.getCategories().get(id);
                if (source == null || source.isEmpty()) continue;
                List<ChunkPanelViewModel.RowView> rows = new ArrayList<>();
                for (ChunkPermissionRow row : source)
                {
                    PermissionStatus status = row.getStatus();
                    if (status == PermissionStatus.ALLOWED) allowed++;
                    else if (status == PermissionStatus.NOT_READY) notReady++;
                    else if (status == PermissionStatus.LOCKED) locked++;
                    else unknown++;
                    rows.add(new ChunkPanelViewModel.RowView(
                        row.getName(),
                        status,
                        glyph(id, status),
                        statusText(id, status),
                        keepsDetail(id) ? row.getDetail() : null));
                }
                categories.add(new ChunkPanelViewModel.CategoryView(
                    id, TITLES.get(id), rows));
            }
        }
        else
        {
            Map<String, List<String>> legacy = decisions.legacyContent(chunk);
            Map<String, String> legacyCategories = new LinkedHashMap<>();
            legacyCategories.put("mon", "COMBAT");
            legacyCategories.put("shop", "SHOPS");
            legacyCategories.put("farm", "FARMING");
            legacyCategories.put("poi", "ACTIVITIES");
            for (String id : ORDER)
            {
                List<ChunkPanelViewModel.RowView> rows = new ArrayList<>();
                for (Map.Entry<String, String> mapping : legacyCategories.entrySet())
                {
                    if (!id.equals(mapping.getValue())) continue;
                    for (String name : legacy.getOrDefault(
                        mapping.getKey(), Collections.emptyList()))
                    {
                        rows.add(new ChunkPanelViewModel.RowView(
                            name,
                            PermissionStatus.UNKNOWN,
                            "?",
                            statusText(id, PermissionStatus.UNKNOWN),
                            null));
                        unknown++;
                    }
                }
                if (!rows.isEmpty())
                {
                    categories.add(new ChunkPanelViewModel.CategoryView(
                        id, TITLES.get(id), rows));
                }
            }
        }

        String name = decisions.chunkName(chunk);
        if (name == null || name.trim().isEmpty()) name = "Unknown chunk";
        return new ChunkPanelViewModel(
            name,
            decisions.regionName(chunk),
            chunk.getCx() + ", " + chunk.getCy(),
            entry,
            freshness(importedAt),
            decisions.trustReason(),
            allowed,
            notReady,
            locked,
            unknown,
            categories);
    }

    private static boolean keepsDetail(String category)
    {
        return "SKILLING".equals(category) || "TRAVEL".equals(category);
    }

    private static String glyph(String category, PermissionStatus status)
    {
        if (status == PermissionStatus.UNKNOWN) return "?";
        if ("COMBAT".equals(category))
        {
            return status == PermissionStatus.ALLOWED ? "✓" : "✕";
        }
        if (status == PermissionStatus.ALLOWED) return "✓";
        if (status == PermissionStatus.NOT_READY) return "○";
        return "✕";
    }

    private static String statusText(String category, PermissionStatus status)
    {
        if ("QUESTS".equals(category) || "COMBAT".equals(category)) return null;
        if (status == PermissionStatus.ALLOWED) return "Available";
        if (status == PermissionStatus.NOT_READY) return "Not ready";
        if (status == PermissionStatus.LOCKED) return "Locked";
        return "Unknown";
    }

    /**
     * When the tracker last confirmed the rules, on the player's clock. The
     * card is rebuilt only when the player changes chunk, so "5m ago" went
     * stale where it stood; a time of day stays true.
     */
    private static String freshness(Instant importedAt)
    {
        return importedAt == null ? "Offline snapshot" : "Synced " + LocalTimeText.of(importedAt);
    }
}
