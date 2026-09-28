package com.fatelocked.sidebar;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.Value;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * What the game itself says about the player, for the requirements the tracker can't see:
 * quest progress, quest points, levels, the world, and what they carry. Read on the client
 * thread; anything not read is unknown, and a requirement that needs it isn't met.
 */
@Value
public class GameFacts
{
    /** Nothing read: logged out, or no row here needs the game. */
    public static final GameFacts NONE = new GameFacts(null, -1, Collections.emptyMap(), Collections.emptyMap(),
        Collections.emptySet(), null);

    /** A members world, or null when unknown. */
    Boolean members;
    /** Quest points, or -1 when unknown. */
    int questPoints;
    /** Real levels, by skill; missing when unknown. */
    Map<Skill, Integer> levels;
    /** The state of each quest a row here names; missing when unknown. */
    Map<Quest, QuestState> quests;
    /** The names of the items in the inventory and worn, lower case. */
    Set<String> carried;
    /** Carrying a light source, or null when unknown. */
    Boolean light;
}
