package com.fatelocked;

import net.runelite.api.WorldType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * What a detection may do here. It counts only with rules for a run loaded, on a world whose
 * progress is the account's own, for a logged-in character:
 * <ul>
 *   <li>the character the rules are bound to: it is recorded and earns a roll reminder;</li>
 *   <li>any character, while the rules are bound to none: it is recorded, for the Roll inbox to
 *   copy (Stage 4, plan decision 9), but earns no reminder (owner decision, 25 September);</li>
 *   <li>another character than the bound one: nothing.</li>
 * </ul>
 */
final class DetectionGate
{
    /** What the Roll inbox says while no run's rules are loaded. */
    static final String NO_RUN = "RuneLite notices nothing until your run's rules are loaded.";
    /** What it says on a world whose progress isn't the account's own. */
    static final String OTHER_WORLD = "RuneLite notices nothing on Leagues, Deadman, beta and other worlds with"
        + " their own progress.";

    enum Detection
    {
        OFF, RECORD, RECORD_AND_REMIND;

        boolean records()
        {
            return this != OFF;
        }

        boolean reminds()
        {
            return this == RECORD_AND_REMIND;
        }
    }

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

    static Detection decide(FateLockedBundle rules, String loggedInName, Set<WorldType> worldTypes)
    {
        if (!hasRun(rules) || AccountBinding.normalize(loggedInName).isEmpty() || otherGame(worldTypes))
        {
            return Detection.OFF;
        }
        String bound = AccountBinding.boundAccount(rules);
        if (bound == null)
        {
            return Detection.RECORD;
        }
        return AccountBinding.sameAccount(bound, loggedInName) ? Detection.RECORD_AND_REMIND : Detection.OFF;
    }

    /**
     * Why nothing is noticed for the character logged in, as the Roll inbox says it (accuracy
     * review, P-10: its empty line promised events that would never come). Null while something
     * is noticed, and while no one is logged in, when the card's own empty line holds.
     */
    static String quiet(FateLockedBundle rules, String loggedInName, Set<WorldType> worldTypes)
    {
        if (AccountBinding.normalize(loggedInName).isEmpty() || decide(rules, loggedInName, worldTypes).records())
        {
            return null;
        }
        if (!hasRun(rules))
        {
            return NO_RUN;
        }
        if (otherGame(worldTypes))
        {
            return OTHER_WORLD;
        }
        return "RuneLite notices nothing on this character: your run is linked to "
            + AccountBinding.boundAccount(rules) + ".";
    }

    private static boolean hasRun(FateLockedBundle rules)
    {
        return rules != null && rules.getRunId() != null && !rules.getRunId().trim().isEmpty();
    }

    private static boolean otherGame(Set<WorldType> worldTypes)
    {
        return worldTypes != null && !Collections.disjoint(worldTypes, OTHER_GAMES);
    }
}
