package com.fatelocked.sidebar;

import com.fatelocked.rules.PermissionStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;

/**
 * Decides the rows the tracker leaves undecided, from what the game itself says. The tracker
 * can't see quest progress, quest points, the world or what the player carries, so a row
 * whose requirements include one of those comes without a status (the owner's review,
 * 28 Sept: "Needs checking" is no status to show). The game can see them, so each
 * requirement is checked here: the row can be done when every one is met, else it isn't
 * ready yet, and its reason names only what is left. A requirement the game can't check
 * either, such as a partner in the other gang, counts as not met and stays named.
 */
public final class RowChecks
{
    /** The lit light sources the game accepts in dark places, carried or worn. */
    public static final Set<Integer> LIGHT_SOURCES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        ItemID.LIT_CANDLE, ItemID.LIT_BLACK_CANDLE, ItemID.TORCH_LIT, ItemID.OIL_LAMP_LIT,
        ItemID.CANDLE_LANTERN_LIT, ItemID.CANDLE_LANTERN_BLACK_LIT, ItemID.OIL_LANTERN_LIT,
        ItemID.BULLSEYE_LANTERN_LIT, ItemID.BULLSEYE_LANTERN_LIT_LUNAR_QUEST, ItemID.TOG_SAPPHIRE_LANTERN_LIT,
        ItemID.CAVE_GOBLIN_MINING_HELMET_LIT, ItemID.SEERS_HEADBAND_EASY, ItemID.SEERS_HEADBAND_MEDIUM,
        ItemID.SEERS_HEADBAND_HARD, ItemID.SEERS_HEADBAND_ELITE, ItemID.SKILLCAPE_FIREMAKING,
        ItemID.SKILLCAPE_FIREMAKING_TRIMMED, ItemID.WINT_TORCH, ItemID.WINT_TORCH_OFFHAND, ItemID.ABYSSAL_LANTERN,
        ItemID.ABYSSAL_LANTERN_NORMAL, ItemID.ABYSSAL_LANTERN_NORMAL_BLUE, ItemID.ABYSSAL_LANTERN_NORMAL_RED,
        ItemID.ABYSSAL_LANTERN_NORMAL_WHITE, ItemID.ABYSSAL_LANTERN_NORMAL_PURPLE,
        ItemID.ABYSSAL_LANTERN_NORMAL_GREEN, ItemID.ABYSSAL_LANTERN_OAK, ItemID.ABYSSAL_LANTERN_WILLOW,
        ItemID.ABYSSAL_LANTERN_MAPLE, ItemID.ABYSSAL_LANTERN_YEW, ItemID.ABYSSAL_LANTERN_BLISTERWOOD,
        ItemID.ABYSSAL_LANTERN_MAGIC, ItemID.ABYSSAL_LANTERN_REDWOOD)));

    private static final Pattern STARTED = Pattern.compile("^(.+) started$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STARTED_BEFORE = Pattern.compile("^Started (.+)$", Pattern.CASE_INSENSITIVE);
    /** A stage of a quest ("While Guthix Sleeps: reached the Ancient Guthixian Temple"). */
    private static final Pattern MILESTONE = Pattern.compile("^([^:]+): .+$");
    private static final Pattern LEVEL = Pattern.compile("^(.+) (\\d+)$");
    private static final String QUEST_POINTS = "quest points";
    private static final String LIGHT_SOURCE = "light source";
    private static final String F2P_ONLY = "f2p only";
    private static final Pattern OR = Pattern.compile(" or ", Pattern.CASE_INSENSITIVE);

    private static final Map<String, Quest> QUESTS = new HashMap<>();
    private static final Map<String, Skill> SKILLS = new HashMap<>();

    static
    {
        for (Quest quest : Quest.values())
        {
            QUESTS.put(key(quest.getName()), quest);
        }
        // The tracker names Recipe for Disaster's parts as the game's quest list does not.
        alias("RFD: The Cook", "Recipe for Disaster - Another Cook's Quest");
        alias("RFD: Dwarf", "Recipe for Disaster - Mountain Dwarf");
        alias("RFD: Goblins", "Recipe for Disaster - Wartface & Bentnoze");
        alias("RFD: Pirate Pete", "Recipe for Disaster - Pirate Pete");
        alias("RFD: Lumbridge Guide", "Recipe for Disaster - Lumbridge Guide");
        alias("RFD: Evil Dave", "Recipe for Disaster - Evil Dave");
        alias("RFD: Skrach Uglogwee", "Recipe for Disaster - Skrach Uglogwee");
        alias("RFD: Sir Amik Varze", "Recipe for Disaster - Sir Amik Varze");
        alias("RFD: King Awowogei", "Recipe for Disaster - King Awowogei");
        alias("RFD: Finale", "Recipe for Disaster - Culinaromancer");
        for (Skill skill : Skill.values())
        {
            SKILLS.put(key(skill.getName()), skill);
        }
    }

    private RowChecks()
    {
    }

    /** A row as shown: its status in the tracker's terms, and the requirements still to meet. */
    @Value
    public static class Decided
    {
        PermissionStatus status;
        /** The requirements left, or null. */
        String detail;
    }

    /** The row as shown: an undecided row decided by the game, any other as the tracker says. */
    public static Decided decide(PermissionStatus status, String detail, GameFacts facts)
    {
        if (status != PermissionStatus.UNKNOWN)
        {
            return new Decided(status, detail);
        }
        List<String> left = new ArrayList<>();
        boolean met = true;
        for (String clause : clauses(detail))
        {
            SkillLine skill = SkillLine.parse(clause);
            if (skill != null)
            {
                // The tracker's level and cap: kept, as every skilling row shows them.
                left.add(clause);
                met &= skill.met();
            }
            else if (!met(clause, facts))
            {
                left.add(clause);
                met = false;
            }
        }
        return new Decided(met ? PermissionStatus.ALLOWED : PermissionStatus.NOT_READY,
            left.isEmpty() ? null : String.join("; ", left));
    }

    /** The quests the requirements name, whose state the game is asked for. */
    public static Set<Quest> questsNamed(Iterable<String> details)
    {
        Set<Quest> named = new LinkedHashSet<>();
        for (String detail : details)
        {
            for (String clause : clauses(detail))
            {
                Quest quest = questOf(clause);
                if (quest != null)
                {
                    named.add(quest);
                }
            }
        }
        return named;
    }

    /** Whether the game says a requirement is met. */
    static boolean met(String clause, GameFacts facts)
    {
        String text = clause.trim();
        String lower = key(text);
        if (lower.equals(F2P_ONLY))
        {
            return Boolean.FALSE.equals(facts.getMembers());
        }
        if (lower.equals(LIGHT_SOURCE))
        {
            return Boolean.TRUE.equals(facts.getLight());
        }
        Matcher started = STARTED.matcher(text);
        Matcher before = STARTED_BEFORE.matcher(text);
        Quest progress = started.matches() ? quest(started.group(1)) : before.matches() ? quest(before.group(1)) : null;
        if (progress != null)
        {
            QuestState state = facts.getQuests().get(progress);
            return state == QuestState.IN_PROGRESS || state == QuestState.FINISHED;
        }
        Quest quest = quest(text);
        Matcher milestone = MILESTONE.matcher(text);
        if (quest == null && milestone.matches())
        {
            // Finishing a quest passes every stage of it.
            quest = quest(milestone.group(1));
        }
        if (quest != null)
        {
            return facts.getQuests().get(quest) == QuestState.FINISHED;
        }
        Matcher level = LEVEL.matcher(text);
        if (level.matches())
        {
            int needed = Integer.parseInt(level.group(2));
            String what = key(level.group(1));
            if (what.equals(QUEST_POINTS))
            {
                return facts.getQuestPoints() >= needed;
            }
            Skill skill = SKILLS.get(what);
            if (skill != null)
            {
                Integer have = facts.getLevels().get(skill);
                return have != null && have >= needed;
            }
        }
        // An item, or one of several: carried or worn.
        for (String item : OR.split(text))
        {
            if (facts.getCarried().contains(key(item)))
            {
                return true;
            }
        }
        return false;
    }

    /** The requirements in a row's detail, as the tracker writes them, "; " between each. */
    static List<String> clauses(String detail)
    {
        List<String> clauses = new ArrayList<>();
        if (detail == null)
        {
            return clauses;
        }
        for (String part : detail.split(";"))
        {
            String clause = part.trim();
            if (!clause.isEmpty())
            {
                clauses.add(clause);
            }
        }
        return clauses;
    }

    /** The quest a requirement is about, if it is about one. */
    private static Quest questOf(String clause)
    {
        String text = clause.trim();
        Matcher started = STARTED.matcher(text);
        if (started.matches() && quest(started.group(1)) != null)
        {
            return quest(started.group(1));
        }
        Matcher before = STARTED_BEFORE.matcher(text);
        if (before.matches() && quest(before.group(1)) != null)
        {
            return quest(before.group(1));
        }
        Quest quest = quest(text);
        Matcher milestone = MILESTONE.matcher(text);
        return quest == null && milestone.matches() ? quest(milestone.group(1)) : quest;
    }

    private static Quest quest(String name)
    {
        return QUESTS.get(key(name));
    }

    private static void alias(String tracker, String game)
    {
        Quest quest = QUESTS.get(key(game));
        if (quest != null)
        {
            QUESTS.put(key(tracker), quest);
        }
    }

    private static String key(String text)
    {
        return text.trim().toLowerCase(Locale.ROOT);
    }
}
