package com.fatelocked.guardian.travel;

import net.runelite.api.Skill;

import java.util.Set;

public interface TravelAvailability
{
    boolean hasAnyItem(Set<Integer> itemIds);

    int realLevel(Skill skill);

    int spellbook();
}
