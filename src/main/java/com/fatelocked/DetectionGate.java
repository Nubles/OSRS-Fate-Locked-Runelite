package com.fatelocked;

import net.runelite.api.WorldType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Whether a detection counts, and may be recorded or earn a roll reminder:
 * rules for a run are loaded, they are bound to the logged-in character,
 * and the world's progress is that account's own. An unbound tracker
 * profile gets no reminders (owner decision, 25 September).
 */
final class DetectionGate
{
    /**
     * Worlds whose progress isn't the account's own: Leagues, Deadman,
     * tournament, beta and no-save worlds, Fresh Start worlds, quest
     * speedrunning, Last Man Standing and the PvP Arena.
     */
    private static final Set<WorldType> OTHER_GAMES = EnumSet.of(
        WorldType.SEASONAL, WorldType.DEADMAN, WorldType.TOURNAMENT_WORLD,
        WorldType.BETA_WORLD, WorldType.NOSAVE_MODE, WorldType.FRESH_START_WORLD,
        WorldType.QUEST_SPEEDRUNNING, WorldType.LAST_MAN_STANDING, WorldType.PVP_ARENA);

    private DetectionGate()
    {
    }

    static boolean allows(FateLockedBundle rules, String loggedInName, Set<WorldType> worldTypes)
    {
        if (rules == null || rules.getRunId() == null || rules.getRunId().trim().isEmpty())
        {
            return false;
        }
        String bound = AccountBinding.boundAccount(rules);
        return bound != null
            && AccountBinding.sameAccount(bound, loggedInName)
            && (worldTypes == null || Collections.disjoint(worldTypes, OTHER_GAMES));
    }
}
