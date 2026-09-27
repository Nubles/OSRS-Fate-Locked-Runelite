package com.fatelocked.sidebar;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.rules.ChunkPermissionRow;
import com.fatelocked.rules.ChunkPermissionSnapshot;
import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.Trust;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Terms;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Here card from the rules, for the place the player stands in (U13, U16, U21):
 * <ul>
 *   <li>its name as the tracker gives it, with interiors and the sea named from the
 *       rules' places;</li>
 *   <li>its status in a word, and the tracker's reason when it isn't unlocked;</li>
 *   <li>counts, and every row in the tracker's categories with its reason.</li>
 * </ul>
 * Freshness is the status card's job, so the card never says when the rules synced.
 */
public final class HerePresenter
{
    static final String LOGGED_OUT = "Log in to see the place you're standing in.";
    static final String NO_RULES = "No rules are loaded, so nothing here is checked.";
    static final String OTHER_CHARACTER = "These rules are for another character, so nothing here is checked.";
    static final String UNMAPPED = "The tracker doesn't map this place.";
    /** The tracker's reason for a row whose place is locked: said once, by the card, in a locked place. */
    static final String LOCATION_LOCKED = "Location locked";

    private static final List<String> ORDER = Arrays.asList(
        "SKILLING", "BANKS", "SHOPS", "QUESTS", "COMBAT", "TRAVEL", "FARMING", "ACTIVITIES");
    private static final Map<String, String> TITLES = new LinkedHashMap<>();
    /** An older export lists content without statuses, by these keys. */
    private static final Map<String, String> LEGACY = new LinkedHashMap<>();

    static
    {
        TITLES.put("SKILLING", "Skilling");
        TITLES.put("BANKS", "Banks");
        TITLES.put("SHOPS", "Shops");
        TITLES.put("QUESTS", "Quests");
        TITLES.put("COMBAT", "Combat");
        TITLES.put("TRAVEL", "Travel");
        TITLES.put("FARMING", "Farming");
        TITLES.put("ACTIVITIES", "Activities");
        LEGACY.put("mon", "COMBAT");
        LEGACY.put("shop", "SHOPS");
        LEGACY.put("farm", "FARMING");
        LEGACY.put("poi", "ACTIVITIES");
    }

    public HereModel present(DecisionService decisions, CanonicalChunk chunk)
    {
        if (chunk == null)
        {
            return HereModel.message(LOGGED_OUT);
        }
        Decision decision = decisions.chunk(chunk);
        String place = place(decisions, chunk, decision);
        String where = where(decisions, chunk, place);
        if (decisions.trust() != Trust.TRUSTED)
        {
            String why = decisions.trust() == Trust.WRONG_CHARACTER ? OTHER_CHARACTER : NO_RULES;
            return new HereModel(place, null, Palette.Tone.NEUTRAL, where, why, Collections.emptyList(),
                Collections.emptyList(), null);
        }

        PermissionStatus status = decision.getStatus();
        String reason = decision.getSource() == Decision.Source.UNMAPPED ? UNMAPPED : decision.getReason();
        Map<PermissionStatus, Integer> counts = new EnumMap<>(PermissionStatus.class);
        List<HereModel.Group> groups = groups(decisions, chunk, counts, status == PermissionStatus.LOCKED);
        List<HereModel.Count> tiles = groups.isEmpty() ? Collections.emptyList() : Arrays.asList(
            new HereModel.Count(counts.getOrDefault(PermissionStatus.ALLOWED, 0), Terms.CAN_DO, Palette.Tone.GOOD),
            new HereModel.Count(counts.getOrDefault(PermissionStatus.NOT_READY, 0), Terms.NOT_READY,
                Palette.Tone.PENDING),
            new HereModel.Count(counts.getOrDefault(PermissionStatus.LOCKED, 0), Terms.LOCKED, Palette.Tone.BAD));
        return new HereModel(place, Terms.place(status), Palette.tone(status), where, reason, tiles, groups, null);
    }

    private static List<HereModel.Group> groups(DecisionService decisions, CanonicalChunk chunk,
        Map<PermissionStatus, Integer> counts, boolean placeLocked)
    {
        List<HereModel.Group> groups = new ArrayList<>();
        ChunkPermissionSnapshot snapshot = decisions.details(chunk).orElse(null);
        if (snapshot != null)
        {
            for (String id : ORDER)
            {
                List<ChunkPermissionRow> source = snapshot.getCategories().get(id);
                if (source == null || source.isEmpty())
                {
                    continue;
                }
                List<HereModel.Row> rows = new ArrayList<>();
                for (ChunkPermissionRow row : source)
                {
                    PermissionStatus status = row.getStatus();
                    counts.merge(status, 1, Integer::sum);
                    rows.add(new HereModel.Row(rowName(row.getName()), Terms.row(status), Palette.tone(status),
                        rowReason(row.getDetail(), placeLocked)));
                }
                groups.add(new HereModel.Group(id, TITLES.get(id), rows));
            }
            return groups;
        }
        Map<String, List<String>> legacy = decisions.legacyContent(chunk);
        for (String id : ORDER)
        {
            List<HereModel.Row> rows = new ArrayList<>();
            for (Map.Entry<String, String> key : LEGACY.entrySet())
            {
                if (!id.equals(key.getValue()))
                {
                    continue;
                }
                for (String name : legacy.getOrDefault(key.getKey(), Collections.emptyList()))
                {
                    counts.merge(PermissionStatus.UNKNOWN, 1, Integer::sum);
                    rows.add(new HereModel.Row(name, Terms.NEEDS_CHECKING, Palette.Tone.NEUTRAL, null));
                }
            }
            if (!rows.isEmpty())
            {
                groups.add(new HereModel.Group(id, TITLES.get(id), rows));
            }
        }
        return groups;
    }

    /**
     * A row's name as players read it. The tracker keeps variants of one thing apart
     * with a suffix ("Culinaromancer's Chest#Food"), shown here in brackets.
     */
    static String rowName(String name)
    {
        int hash = name.indexOf('#');
        if (hash <= 0 || hash == name.length() - 1)
        {
            return name;
        }
        return name.substring(0, hash).trim() + " (" + name.substring(hash + 1).trim() + ")";
    }

    /**
     * A row's reason. In a locked place every row is locked for that reason, which
     * the card already gives, so the rows don't repeat it.
     */
    static String rowReason(String detail, boolean placeLocked)
    {
        String reason = blankToNull(detail);
        if (reason == null || !placeLocked)
        {
            return reason;
        }
        StringBuilder kept = new StringBuilder();
        for (String part : reason.split(";"))
        {
            String clause = part.trim();
            if (clause.isEmpty() || clause.equalsIgnoreCase(LOCATION_LOCKED))
            {
                continue;
            }
            if (kept.length() > 0)
            {
                kept.append("; ");
            }
            kept.append(clause);
        }
        return kept.length() == 0 ? null : kept.toString();
    }

    /** The tracker's name for the chunk, else the place the rules' places name, else Uncharted. */
    private static String place(DecisionService decisions, CanonicalChunk chunk, Decision decision)
    {
        String name = blankToNull(decisions.chunkName(chunk));
        if (name == null && decision.getSource() == Decision.Source.CHUNK)
        {
            name = blankToNull(decision.getLabel());
        }
        if (name == null)
        {
            name = blankToNull(decisions.areaName(chunk));
        }
        return name == null ? Terms.UNCHARTED : name;
    }

    /** The area and region, unless the title already says them, and the chunk's coordinates. */
    private static String where(DecisionService decisions, CanonicalChunk chunk, String place)
    {
        String area = blankToNull(decisions.areaName(chunk));
        String coordinates = chunk.getCx() + ", " + chunk.getCy();
        if (area == null || area.equals(place))
        {
            String region = blankToNull(decisions.regionName(chunk));
            return region == null || region.equals(place) ? coordinates : region + " · " + coordinates;
        }
        return area + " · " + coordinates;
    }

    private static String blankToNull(String value)
    {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
