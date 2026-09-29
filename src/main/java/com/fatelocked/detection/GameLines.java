package com.fatelocked.detection;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The game's own lines and popups, read the way RuneLite's plugins read them, with the real
 * messages from their tests pinned in contracts/detected-events.json. Tags are removed first, so a
 * line reads the same with its colours or without.
 */
final class GameLines
{
    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    /**
     * "Your Vorkath kill count is: 12.", "Your completed Chambers of Xeric count is: 51.", "Your
     * subdued Wintertodt count is: 4.", "Your Gauntlet completion count is: 123.", "Your completion
     * count for TzHaar-Ket-Rak's First Challenge is: 1." and "Your Barrows chest count is 310", as
     * RuneLite's chat commands read them, with the colon optional. Any name reads; the bundle's
     * table says which are bosses.
     */
    private static final Pattern KILL_COUNT = Pattern.compile(
        "Your (?:completion count for |subdued |completed )?(?<boss>.+?) "
            + "(?:(?:kill|harvest|lap|completion|success|Total Ticket) )?(?:count )?is:? ?(?:@[^@]*@)?(?<count>[0-9,]+)\\.?");

    /** "You have completed 12 hard Treasure Trails.", as RuneLite's loot tracker reads it, thousands and all. */
    private static final Pattern CLUE = Pattern.compile(
        "You have completed (?<count>[0-9,]+) (?<tier>beginner|easy|medium|hard|elite|master) Treasure Trails?\\.?");

    /**
     * "Congratulations, you've completed a grandmaster combat task: {@literal @}ach_comp@Egniol Diet
     * II&lt;/col&gt; (6 points).", as RuneLite's screenshot plugin reads it: the colour marker and the
     * points are not the task's name.
     */
    private static final Pattern COMBAT_TASK = Pattern.compile(
        "Congratulations, you've completed an? (?<tier>\\w+) combat task: (?:@[^@]*@)?(?<task>.+?)(?: \\(\\d+ points?\\))?\\.?");

    /** The popup's "Task Completed: Handyman (6 points)". */
    private static final Pattern COMBAT_TASK_POPUP = Pattern.compile(
        "Task Completed: (?<task>.+?)(?: \\(\\d+ points?\\))?\\.?");

    private static final Pattern COLLECTION_LOG = Pattern.compile("New item added to your collection log: (?<item>.+)");
    private static final Pattern COLLECTION_LOG_POPUP = Pattern.compile("New item:\\s*(?<item>.+)");

    private GameLines()
    {
    }

    static String withoutTags(String text)
    {
        return text == null ? "" : TAG.matcher(text).replaceAll("").trim();
    }

    /** A line's boss name and count, or null for a line that isn't a kill count. */
    static Count killCount(String line)
    {
        Matcher match = KILL_COUNT.matcher(withoutTags(line));
        return match.matches() ? new Count(match.group("boss"), number(match.group("count"))) : null;
    }

    /** A completed clue's tier and count, or null. */
    static Count clue(String line)
    {
        Matcher match = CLUE.matcher(withoutTags(line));
        return match.matches() ? new Count(match.group("tier"), number(match.group("count"))) : null;
    }

    /** A completed combat task's tier and name, from its chat line, or null. */
    static String[] combatTask(String line)
    {
        Matcher match = COMBAT_TASK.matcher(withoutTags(line));
        if (!match.matches() || match.group("task").trim().isEmpty()) return null;
        return new String[]{match.group("tier"), match.group("task").trim()};
    }

    /** A completed combat task's name, from the game's popup, or null. */
    static String combatTaskPopup(String title, String text)
    {
        if (!"Combat Task Completed!".equalsIgnoreCase(withoutTags(title))) return null;
        Matcher match = COMBAT_TASK_POPUP.matcher(withoutTags(text));
        return match.matches() && !match.group("task").trim().isEmpty() ? match.group("task").trim() : null;
    }

    /** A new collection log item's name, from its chat line, or null. */
    static String collectionLog(String line)
    {
        Matcher match = COLLECTION_LOG.matcher(withoutTags(line));
        return match.matches() ? match.group("item") : null;
    }

    /** A new collection log item's name, from the game's popup, or null. */
    static String collectionLogPopup(String title, String text)
    {
        if (!"Collection log".equalsIgnoreCase(withoutTags(title))) return null;
        Matcher match = COLLECTION_LOG_POPUP.matcher(withoutTags(text));
        return match.matches() ? match.group("item") : null;
    }

    private static long number(String digits)
    {
        return Long.parseLong(digits.replace(",", ""));
    }

    /** A name and a count read from a line. */
    static final class Count
    {
        final String name;
        final long count;

        Count(String name, long count)
        {
            this.name = name;
            this.count = count;
        }
    }
}
