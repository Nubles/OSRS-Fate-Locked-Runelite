package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TravelAlternativeCatalog
{
    private static final List<TravelAlternative> ALTERNATIVES =
        Collections.unmodifiableList(Arrays.asList(
            tablet("varrock-tablet", "Varrock teleport tablet",
                new CanonicalChunk(50, 53), 8007),
            tablet("lumbridge-tablet", "Lumbridge teleport tablet",
                new CanonicalChunk(50, 50), 8008),
            tablet("falador-tablet", "Falador teleport tablet",
                new CanonicalChunk(46, 52), 8009),
            tablet("camelot-tablet", "Camelot teleport tablet",
                new CanonicalChunk(43, 54), 8010),
            tablet("ardougne-tablet", "Ardougne teleport tablet",
                new CanonicalChunk(41, 51), 8011),
            tablet("watchtower-tablet", "Watchtower teleport tablet",
                new CanonicalChunk(39, 48), 8012)));

    private TravelAlternativeCatalog()
    {
    }

    public static List<TravelAlternative> alternatives()
    {
        return ALTERNATIVES;
    }

    private static TravelAlternative tablet(
        String id, String label, CanonicalChunk destination, int itemId)
    {
        Set<Integer> itemIds = new LinkedHashSet<>();
        itemIds.add(itemId);
        return new TravelAlternative(
            id, label, destination, "Teleport Tablets", itemIds,
            null, 0, null);
    }
}
