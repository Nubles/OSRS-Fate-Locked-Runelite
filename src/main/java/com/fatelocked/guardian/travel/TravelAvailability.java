package com.fatelocked.guardian.travel;

import java.util.Set;

/** What the player has with them, for suggesting another way there (F7). */
public interface TravelAvailability
{
    /** Whether any of these items is carried or worn. */
    boolean hasAnyItem(Set<Integer> itemIds);
}
