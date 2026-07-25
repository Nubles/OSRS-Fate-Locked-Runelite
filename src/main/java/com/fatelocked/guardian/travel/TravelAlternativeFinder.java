package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.rules.FateRuleEngine;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RuleDecision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class TravelAlternativeFinder
{
    private final List<TravelAlternative> alternatives;

    public TravelAlternativeFinder()
    {
        this(TravelAlternativeCatalog.alternatives());
    }

    TravelAlternativeFinder(List<TravelAlternative> alternatives)
    {
        this.alternatives = alternatives == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(alternatives));
    }

    public Optional<TravelAlternative> find(
        TravelAction action,
        FateRuleEngine rules,
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

        String intendedArea = normalizeArea(
            rules.areaLabel(action.getDestination()));
        TravelAlternative best = null;
        int bestAreaRank = Integer.MAX_VALUE;
        long bestDistance = Long.MAX_VALUE;

        for (TravelAlternative candidate : alternatives)
        {
            if (!isVerified(candidate, rules, availability))
            {
                continue;
            }

            int areaRank = sameArea(
                intendedArea, normalizeArea(rules.areaLabel(
                    candidate.getDestination()))) ? 0 : 1;
            long distance = distance(
                action.getDestination(), candidate.getDestination());
            if (best == null
                || areaRank < bestAreaRank
                || (areaRank == bestAreaRank && distance < bestDistance))
            {
                best = candidate;
                bestAreaRank = areaRank;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean isVerified(
        TravelAlternative candidate,
        FateRuleEngine rules,
        TravelAvailability availability)
    {
        if (candidate == null
            || candidate.getDestination() == null
            || candidate.getRequiredItemIds() == null
            || candidate.getRequiredItemIds().isEmpty())
        {
            return false;
        }

        RuleDecision destination = rules.entry(candidate.getDestination());
        if (destination == null
            || destination.getStatus() != PermissionStatus.ALLOWED)
        {
            return false;
        }

        if (!availability.hasAnyItem(candidate.getRequiredItemIds()))
        {
            return false;
        }

        if (candidate.getRequiredSkill() == null)
        {
            if (candidate.getRequiredLevel() > 0)
            {
                return false;
            }
        }
        else if (availability.realLevel(candidate.getRequiredSkill())
            < candidate.getRequiredLevel())
        {
            return false;
        }

        return candidate.getRequiredSpellbook() == null
            || availability.spellbook() == candidate.getRequiredSpellbook();
    }

    private static boolean sameArea(String intended, String candidate)
    {
        return intended != null && intended.equals(candidate);
    }

    private static String normalizeArea(String area)
    {
        if (area == null)
        {
            return null;
        }
        String normalized = area.trim().replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static long distance(CanonicalChunk left, CanonicalChunk right)
    {
        return Math.abs((long) left.getCx() - right.getCx())
            + Math.abs((long) left.getCy() - right.getCy());
    }
}
