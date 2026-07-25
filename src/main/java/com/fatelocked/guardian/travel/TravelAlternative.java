package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import lombok.Value;
import net.runelite.api.Skill;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Value
public class TravelAlternative
{
    String id;
    String label;
    CanonicalChunk destination;
    Set<Integer> requiredItemIds;
    Skill requiredSkill;
    int requiredLevel;
    Integer requiredSpellbook;

    public TravelAlternative(
        String id,
        String label,
        CanonicalChunk destination,
        Set<Integer> requiredItemIds,
        Skill requiredSkill,
        int requiredLevel,
        Integer requiredSpellbook)
    {
        this.id = id;
        this.label = label;
        this.destination = destination;
        this.requiredItemIds = requiredItemIds == null
            ? Collections.emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<>(requiredItemIds));
        this.requiredSkill = requiredSkill;
        this.requiredLevel = requiredLevel;
        this.requiredSpellbook = requiredSpellbook;
    }
}
