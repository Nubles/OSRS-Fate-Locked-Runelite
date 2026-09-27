package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.MenuFacts;
import lombok.Value;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A click as Strict Mode sees it (F3): a trip the tracker's travel table
 * matched by id, with every place its option can go, or a click that isn't
 * travel at all.
 */
@Value
public class TravelAction
{
    public enum Confidence { EXACT, UNKNOWN }

    /** Options that name no place themselves, so the target alone names the trip. */
    private static final Set<String> ACTIVATIONS = new HashSet<>(Arrays.asList(
        "cast", "break", "teleport", "rub", "read", "travel", "charter", "pay-fare", "minigame teleport"));

    /** The table's id for the method, such as "item:amulet-of-glory"; null when the click isn't travel. */
    String methodId;
    /** The option as the table keys it: "Edgeville", or "code:CKS"; null when the click isn't travel. */
    String option;
    /** The trip as the menu names it: "Falador Teleport", or "Amulet of glory(4) to Edgeville". */
    String label;
    CanonicalChunk origin;
    /** Every chunk the option can go to; empty when the click isn't travel, or the table doesn't say. */
    List<CanonicalChunk> destinations;
    /** Tagged but never blocked: networks and boats in Stage 2. */
    boolean advisory;
    /** EXACT when the table matched the click by id. */
    Confidence confidence;

    /** The trip a table match is. */
    public static TravelAction of(TravelMatch match, MenuFacts facts, CanonicalChunk origin)
    {
        return new TravelAction(match.getMethod().getId(), match.getOption().getText(), label(facts), origin,
            match.getOption().getTo(), match.getMethod().isAdvisory(), Confidence.EXACT);
    }

    /** A click that isn't travel: Strict Mode leaves it alone. */
    public static TravelAction notTravel(MenuFacts facts, CanonicalChunk origin)
    {
        return new TravelAction(null, null, label(facts), origin, Collections.emptyList(), false, Confidence.UNKNOWN);
    }

    /** Whether an option names no place itself, as Cast, Break and Rub don't. */
    static boolean isActivation(String option)
    {
        return option != null && ACTIVATIONS.contains(option.toLowerCase(Locale.ROOT));
    }

    /** Its one destination; null when it can go to several places, or the table doesn't say where. */
    public CanonicalChunk getDestination()
    {
        return destinations != null && destinations.size() == 1 ? destinations.get(0) : null;
    }

    /**
     * The trip as the menu names it, in its own case (B15): "Varrock
     * Teleport" for Cast on the spell, and "Amulet of glory(4) to
     * Edgeville" for an option that names the place.
     */
    static String label(MenuFacts facts)
    {
        String option = facts == null ? "" : facts.getOption();
        String target = facts == null ? "" : facts.getTarget();
        if (target.isEmpty()) return option;
        if (option.isEmpty() || isActivation(option)) return target;
        return target + " to " + option;
    }
}
