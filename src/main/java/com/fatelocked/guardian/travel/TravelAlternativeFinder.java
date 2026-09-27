package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.TravelTable;

import java.util.Locale;
import java.util.Optional;

/**
 * Another way there, from the tracker's travel table (F7): a trip on an
 * item the player carries or wears, to one place, that the tracker allows
 * for the run. One landing in the blocked trip's area ranks first, then
 * the nearest; ties go to the lower id. It only picks words for the
 * notice: nothing here clicks or moves.
 */
public class TravelAlternativeFinder
{
    public Optional<TravelAlternative> find(
        TravelAction action,
        DecisionService rules,
        TravelAvailability availability)
    {
        if (action == null
            || action.getConfidence() != TravelAction.Confidence.EXACT
            || action.getDestination() == null
            || rules == null
            || availability == null)
        {
            return Optional.empty();
        }
        TravelTable table = rules.travelTable();
        if (table == null) return Optional.empty();

        CanonicalChunk intended = action.getDestination();
        String intendedArea = normalize(rules.areaName(intended));
        TravelAlternative best = null;
        int bestRank = Integer.MAX_VALUE;
        long bestDistance = Long.MAX_VALUE;
        String bestId = null;

        for (TravelTable.Method method : table.methods())
        {
            if (method.getMatch() != TravelTable.Match.ITEMS) continue;
            Boolean carried = null;
            for (TravelTable.Option option : method.getOptions().values())
            {
                CanonicalChunk destination = option.destination();
                if (destination == null) continue;
                Decision decision = rules.travel(method, option);
                if (decision == null || decision.getStatus() != PermissionStatus.ALLOWED) continue;
                if (carried == null) carried = availability.hasAnyItem(method.getIds());
                if (!carried) break;

                String id = method.getId() + "|" + option.getText();
                long distance = distance(intended, destination);
                int rank = sameArea(intendedArea, normalize(rules.areaName(destination))) ? 0 : 1;
                String stableId = normalize(id);
                if (best == null
                    || rank < bestRank
                    || rank == bestRank && distance < bestDistance
                    || rank == bestRank && distance == bestDistance && stableId.compareTo(bestId) < 0)
                {
                    best = new TravelAlternative(id, label(method, option), destination);
                    bestRank = rank;
                    bestDistance = distance;
                    bestId = stableId;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** "Lumbridge teleport" for an option that names no place; "Amulet of glory to Edgeville" for one that does. */
    static String label(TravelTable.Method method, TravelTable.Option option)
    {
        return TravelAction.isActivation(option.getText())
            ? method.getLabel() : method.getLabel() + " to " + option.getText();
    }

    private static boolean sameArea(String intended, String candidate)
    {
        return intended != null && intended.equals(candidate);
    }

    private static String normalize(String text)
    {
        if (text == null) return null;
        String normalized = text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static long distance(CanonicalChunk left, CanonicalChunk right)
    {
        return Math.abs((long) left.getCx() - right.getCx())
            + Math.abs((long) left.getCy() - right.getCy());
    }
}
