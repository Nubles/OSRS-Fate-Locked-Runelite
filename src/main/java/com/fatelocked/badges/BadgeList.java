package com.fatelocked.badges;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** The players who share a badge, by normalised name, with their tier. Immutable. */
public final class BadgeList
{
    /** No badges: none downloaded yet, or a relay that has none. */
    public static final BadgeList NONE = new BadgeList(Collections.emptyMap());

    private final Map<String, Integer> tiers;

    private BadgeList(Map<String, Integer> tiers)
    {
        this.tiers = tiers;
    }

    /** A list of these names, normalised here, and tiers; invalid tiers and empty names are left out. */
    static BadgeList of(Map<String, Integer> byName)
    {
        Map<String, Integer> tiers = new HashMap<>();
        for (Map.Entry<String, Integer> entry : byName.entrySet())
        {
            String name = BadgeNames.normalise(entry.getKey());
            Integer tier = entry.getValue();
            if (!name.isEmpty() && tier != null && BadgeTier.valid(tier))
            {
                tiers.put(name, tier);
            }
        }
        return tiers.isEmpty() ? NONE : new BadgeList(Collections.unmodifiableMap(tiers));
    }

    /** The tier of the player of this name, as the game writes it; {@link BadgeTier#NONE} if none. */
    public int tierOf(String name)
    {
        if (tiers.isEmpty() || name == null)
        {
            return BadgeTier.NONE;
        }
        Integer tier = tiers.get(BadgeNames.normalise(name));
        return tier == null ? BadgeTier.NONE : tier;
    }

    public int size()
    {
        return tiers.size();
    }

    public boolean isEmpty()
    {
        return tiers.isEmpty();
    }
}
