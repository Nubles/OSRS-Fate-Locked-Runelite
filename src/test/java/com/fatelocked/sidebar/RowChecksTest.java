package com.fatelocked.sidebar;

import static com.fatelocked.rules.PermissionStatus.ALLOWED;
import static com.fatelocked.rules.PermissionStatus.LOCKED;
import static com.fatelocked.rules.PermissionStatus.NOT_READY;
import static com.fatelocked.rules.PermissionStatus.UNKNOWN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Test;

/** The owner's review, 28 Sept: a row the tracker leaves undecided is decided by what the game says. */
public class RowChecksTest
{
    private static final String CAVES = "The Lost Tribe started; Death to the Dorgeshuun; Light source";

    @Test
    public void aRowTheTrackerDecidedStaysAsItIs()
    {
        assertEquals(new RowChecks.Decided(LOCKED, "Unlock Axe Shops"),
            RowChecks.decide(LOCKED, "Unlock Axe Shops", everything()));
        assertEquals(new RowChecks.Decided(NOT_READY, "Woodcutting 15/60 · cap 20"),
            RowChecks.decide(NOT_READY, "Woodcutting 15/60 · cap 20", everything()));
        assertEquals(new RowChecks.Decided(ALLOWED, null), RowChecks.decide(ALLOWED, null, GameFacts.NONE));
    }

    /** The Lumbridge caves' frogs: the quest started, the next one done and a lantern carried. */
    @Test
    public void everyRequirementMetCanBeDone()
    {
        GameFacts facts = facts(true, 0, Collections.emptyMap(),
            quests(Quest.THE_LOST_TRIBE, QuestState.IN_PROGRESS, Quest.DEATH_TO_THE_DORGESHUUN, QuestState.FINISHED),
            Collections.emptySet(), true);
        assertEquals(new RowChecks.Decided(ALLOWED, null), RowChecks.decide(UNKNOWN, CAVES, facts));
    }

    @Test
    public void onlyWhatIsLeftIsNamed()
    {
        GameFacts started = facts(true, 0, Collections.emptyMap(),
            quests(Quest.THE_LOST_TRIBE, QuestState.IN_PROGRESS, Quest.DEATH_TO_THE_DORGESHUUN, QuestState.NOT_STARTED),
            Collections.emptySet(), false);
        assertEquals(new RowChecks.Decided(NOT_READY, "Death to the Dorgeshuun; Light source"),
            RowChecks.decide(UNKNOWN, CAVES, started));
    }

    /** Logged out, or where the game can't say, nothing counts as met: the row isn't ready, and says why. */
    @Test
    public void whatTheGameCantSayIsNotMet()
    {
        assertEquals(new RowChecks.Decided(NOT_READY, CAVES), RowChecks.decide(UNKNOWN, CAVES, GameFacts.NONE));
        assertEquals(new RowChecks.Decided(NOT_READY, "A trustworthy partner in the opposite gang"),
            RowChecks.decide(UNKNOWN, "A trustworthy partner in the opposite gang", everything()));
    }

    /** A skilling row keeps the level and cap the tracker gives, and needs them met too. */
    @Test
    public void skillingKeepsItsLevelAndCap()
    {
        GameFacts started = facts(true, 0, Collections.emptyMap(), quests(Quest.THE_LOST_TRIBE, QuestState.FINISHED),
            Collections.emptySet(), null);
        assertEquals(new RowChecks.Decided(ALLOWED, "Fishing 1/1 · cap 10"),
            RowChecks.decide(UNKNOWN, "Fishing 1/1 · cap 10; The Lost Tribe started", started));
        assertEquals(new RowChecks.Decided(NOT_READY, "Mining 1/20 · cap 0"),
            RowChecks.decide(UNKNOWN, "Mining 1/20 · cap 0; The Lost Tribe started", started));
        assertEquals(new RowChecks.Decided(NOT_READY, "Mining 20/20 · cap 10"),
            RowChecks.decide(UNKNOWN, "Mining 20/20 · cap 10", started));
    }

    @Test
    public void eachKindOfRequirement()
    {
        assertTrue(RowChecks.met("F2P Only", facts(false, 0, Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptySet(), null)));
        assertFalse("not on a members world", RowChecks.met("F2P Only", everything()));
        assertFalse("nor when the world isn't known", RowChecks.met("F2P Only", GameFacts.NONE));

        assertTrue(RowChecks.met("Lost City", quest(Quest.LOST_CITY, QuestState.FINISHED)));
        assertFalse(RowChecks.met("Lost City", quest(Quest.LOST_CITY, QuestState.IN_PROGRESS)));
        assertTrue(RowChecks.met("The Lost Tribe started", quest(Quest.THE_LOST_TRIBE, QuestState.IN_PROGRESS)));
        assertTrue(RowChecks.met("The Lost Tribe started", quest(Quest.THE_LOST_TRIBE, QuestState.FINISHED)));
        assertFalse(RowChecks.met("The Lost Tribe started", quest(Quest.THE_LOST_TRIBE, QuestState.NOT_STARTED)));
        assertTrue(RowChecks.met("Started The Restless Ghost",
            quest(Quest.THE_RESTLESS_GHOST, QuestState.IN_PROGRESS)));
        assertFalse(RowChecks.met("Started The Restless Ghost",
            quest(Quest.THE_RESTLESS_GHOST, QuestState.NOT_STARTED)));

        String temple = "While Guthix Sleeps: reached the Ancient Guthixian Temple";
        assertTrue("done passes every stage", RowChecks.met(temple,
            quest(Quest.WHILE_GUTHIX_SLEEPS, QuestState.FINISHED)));
        assertFalse("a stage part way through can't be seen", RowChecks.met(temple,
            quest(Quest.WHILE_GUTHIX_SLEEPS, QuestState.IN_PROGRESS)));
        assertTrue("the tracker's name for a Recipe for Disaster part", RowChecks.met("RFD: Dwarf",
            quest(Quest.RECIPE_FOR_DISASTER__MOUNTAIN_DWARF, QuestState.FINISHED)));

        assertTrue(RowChecks.met("Quest Points 32", facts(true, 32, Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptySet(), null)));
        assertFalse(RowChecks.met("Quest Points 32", facts(true, 31, Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptySet(), null)));
        assertTrue(RowChecks.met("Attack 15", level(Skill.ATTACK, 15)));
        assertFalse(RowChecks.met("Attack 15", level(Skill.ATTACK, 14)));
        assertFalse("a level the game didn't give", RowChecks.met("Attack 15", GameFacts.NONE));

        assertTrue(RowChecks.met("Brass key", carrying("brass key")));
        assertFalse(RowChecks.met("Brass key", carrying("steel key ring")));
        assertTrue("either of several", RowChecks.met("Dramen staff or lunar staff", carrying("lunar staff")));
        assertTrue(RowChecks.met("Light source", facts(true, 0, Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptySet(), true)));
        assertFalse(RowChecks.met("Light source", facts(true, 0, Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptySet(), false)));
    }

    /** Audit: the Wiki's "Light source" table lists the Max cape and the Jar of Light too. */
    @Test
    public void theMaxCapeAndAJarOfLightAreLightSources()
    {
        for (int item : new int[] { net.runelite.api.gameval.ItemID.SKILLCAPE_MAX,
            net.runelite.api.gameval.ItemID.SKILLCAPE_MAX_WORN, net.runelite.api.gameval.ItemID.JAR_OF_LIGHT })
        {
            assertTrue(String.valueOf(item), RowChecks.LIGHT_SOURCES.contains(item));
        }
    }

    @Test
    public void theQuestsNamedAreTheOnesToAskTheGameAbout()
    {
        assertEquals(new LinkedHashSet<>(Arrays.asList(Quest.THE_LOST_TRIBE, Quest.DEATH_TO_THE_DORGESHUUN,
                Quest.WHILE_GUTHIX_SLEEPS, Quest.RECIPE_FOR_DISASTER__MOUNTAIN_DWARF, Quest.THE_RESTLESS_GHOST)),
            RowChecks.questsNamed(Arrays.asList(CAVES, "While Guthix Sleeps: reached the Ancient Guthixian Temple",
                "RFD: Dwarf; Brass key", null, "Started The Restless Ghost; Attack 15")));
    }

    /**
     * A quest step as the tracker codes it counts as met once the game says the quest is
     * finished; before then the game can't say, so the row isn't ready and names the step.
     */
    @Test
    public void aQuestStepIsMetWhenTheGameSaysTheQuestIsFinished()
    {
        assertEquals(new RowChecks.Decided(ALLOWED, null),
            RowChecks.decide(UNKNOWN, "Plague City 5", quest(Quest.PLAGUE_CITY, QuestState.FINISHED)));
        assertEquals(new RowChecks.Decided(NOT_READY, "Plague City 5"),
            RowChecks.decide(UNKNOWN, "Plague City 5", quest(Quest.PLAGUE_CITY, QuestState.IN_PROGRESS)));
        assertEquals(new RowChecks.Decided(ALLOWED, null),
            RowChecks.decide(UNKNOWN, "Dragon Slayer I 5b2", quest(Quest.DRAGON_SLAYER_I, QuestState.FINISHED)));
        assertEquals(new RowChecks.Decided(NOT_READY, "Dragon Slayer I 5b2"),
            RowChecks.decide(UNKNOWN, "Dragon Slayer I 5b2", quest(Quest.DRAGON_SLAYER_I, QuestState.NOT_STARTED)));
        assertEquals(new RowChecks.Decided(ALLOWED, null),
            RowChecks.decide(UNKNOWN, "Shield of Arrav 2a2", quest(Quest.SHIELD_OF_ARRAV, QuestState.FINISHED)));
        assertEquals(new RowChecks.Decided(NOT_READY, "Shield of Arrav 2a2"),
            RowChecks.decide(UNKNOWN, "Shield of Arrav 2a2", GameFacts.NONE));
        assertEquals(new LinkedHashSet<>(Arrays.asList(Quest.PLAGUE_CITY, Quest.SHIELD_OF_ARRAV)),
            RowChecks.questsNamed(Arrays.asList("Plague City 5; Mining 20; Quest Points 43", "Shield of Arrav 2a2")));
    }

    /** A level, quest points and an item whose name ends in a number are no quest steps. */
    @Test
    public void onlyAQuestsNameMakesAStepCode()
    {
        assertTrue(RowChecks.met("Mining 20", level(Skill.MINING, 20)));
        assertFalse(RowChecks.met("Mining 20", everything()));
        assertFalse(RowChecks.met("Quest Points 43", facts(true, 42, Collections.emptyMap(),
            quests(Quest.PLAGUE_CITY, QuestState.FINISHED), Collections.emptySet(), null)));
        assertFalse("every quest finished passes no item", RowChecks.met("Ring of wealth 5", everything()));
        assertTrue(RowChecks.met("Ring of wealth 5", carrying("ring of wealth 5")));
        for (String clause : new String[] {"Mining 20", "Quest Points 43", "Ring of wealth 5"})
        {
            assertFalse(clause, RowChecks.gameOnly(clause));
        }
        assertEquals(Collections.emptySet(),
            RowChecks.questsNamed(Arrays.asList("Mining 20; Quest Points 43; Ring of wealth 5")));
    }

    /**
     * Cave crawler, which the tracker says isn't ready: the quest started and the lantern carried
     * are checks only the game can make, so they go once met. Death to the Dorgeshuun is a quest
     * the tracker has as not done, so it stays even where the game says it is finished.
     */
    @Test
    public void aRowThatIsNotReadyDropsTheGameChecksThatAreMet()
    {
        GameFacts startedAndLit = facts(true, 0, Collections.emptyMap(),
            quests(Quest.THE_LOST_TRIBE, QuestState.IN_PROGRESS, Quest.DEATH_TO_THE_DORGESHUUN, QuestState.NOT_STARTED),
            Collections.emptySet(), true);
        assertEquals(new RowChecks.Decided(NOT_READY, "Death to the Dorgeshuun"),
            RowChecks.decide(NOT_READY, CAVES, startedAndLit));
        assertEquals(new RowChecks.Decided(NOT_READY, "Death to the Dorgeshuun"),
            RowChecks.decide(NOT_READY, CAVES, everything()));
        GameFacts unlit = facts(true, 0, Collections.emptyMap(), quests(Quest.THE_LOST_TRIBE, QuestState.IN_PROGRESS),
            Collections.emptySet(), false);
        assertEquals(new RowChecks.Decided(NOT_READY, "Death to the Dorgeshuun; Light source"),
            RowChecks.decide(NOT_READY, CAVES, unlit));
        assertEquals("a skilling row keeps its level and cap",
            new RowChecks.Decided(NOT_READY, "Mining 1/20 · cap 0"),
            RowChecks.decide(NOT_READY, "Mining 1/20 · cap 0; The Lost Tribe started", startedAndLit));
        assertEquals(new RowChecks.Decided(NOT_READY, "Dragon Slayer I 3; Attack 40"),
            RowChecks.decide(NOT_READY, "Plague City 5; Dragon Slayer I 3; Attack 40", facts(true, 0,
                Collections.emptyMap(), quests(Quest.PLAGUE_CITY, QuestState.FINISHED), Collections.emptySet(), null)));
    }

    /**
     * Tears of Guthix: the tracker counts quest points from the quests it has as done and judges
     * levels against its caps, so none of these is a check it leaves to the game; all stay.
     */
    @Test
    public void whatTheTrackerDecidedStaysOnARowThatIsNotReady()
    {
        String tears = "Firemaking 49; Crafting 20; Mining 20; Quest Points 43";
        Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values())
        {
            levels.put(skill, 99);
        }
        GameFacts allMet = facts(true, 300, levels, quests(Quest.TEARS_OF_GUTHIX, QuestState.FINISHED),
            Collections.emptySet(), true);
        assertEquals(new RowChecks.Decided(NOT_READY, tears), RowChecks.decide(NOT_READY, tears, allMet));
        assertEquals("nothing met: as the tracker sent it", new RowChecks.Decided(NOT_READY, CAVES),
            RowChecks.decide(NOT_READY, CAVES, GameFacts.NONE));
        assertEquals("every check met: the tracker's own text stays",
            new RowChecks.Decided(NOT_READY, "The Lost Tribe started; Light source; F2P only"),
            RowChecks.decide(NOT_READY, "The Lost Tribe started; Light source; F2P only", facts(false, 0,
                Collections.emptyMap(), quests(Quest.THE_LOST_TRIBE, QuestState.IN_PROGRESS), Collections.emptySet(),
                true)));
        assertEquals(new RowChecks.Decided(NOT_READY, null), RowChecks.decide(NOT_READY, null, allMet));
    }

    /** Whatever the game says, a row the tracker calls not ready stays not ready. */
    @Test
    public void aRowThatIsNotReadyStaysNotReady()
    {
        GameFacts[] all = {GameFacts.NONE, everything(), carrying("lantern"),
            facts(false, 300, Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet(), true)};
        for (String detail : new String[] {null, "", CAVES, "Light source", "F2P only", "The Lost Tribe started",
            "Plague City 5", "Woodcutting 15/60 · cap 20", "Quest Points 43", "Unlock Axe Shops"})
        {
            for (GameFacts facts : all)
            {
                assertEquals(detail, NOT_READY, RowChecks.decide(NOT_READY, detail, facts).getStatus());
            }
        }
    }

    /** For a row that isn't ready, the game is asked only about the quests its game checks name. */
    @Test
    public void aRowThatIsNotReadyAsksOnlyAboutItsGameChecks()
    {
        assertEquals(new LinkedHashSet<>(Arrays.asList(Quest.THE_RESTLESS_GHOST, Quest.THE_LOST_TRIBE,
                Quest.PLAGUE_CITY)),
            RowChecks.questsNamed(Arrays.asList("Started The Restless Ghost"),
                Arrays.asList(CAVES, "Thieving 23; Biohazard; Plague City 5", "RFD: Dwarf; Quest Points 175")));
        assertTrue(RowChecks.hasGameOnlyCheck(CAVES));
        assertTrue(RowChecks.hasGameOnlyCheck("Woodcutting 1/1 · cap 0; F2P only"));
        assertFalse(RowChecks.hasGameOnlyCheck("Thieving 23; Agility 23; Head T1; The Lost Tribe"));
        assertFalse(RowChecks.hasGameOnlyCheck("Woodcutting 1/15 · cap 0"));
        assertFalse(RowChecks.hasGameOnlyCheck(null));
    }

    @Test
    public void aSkillLineIsReadAsTheTrackerWritesIt()
    {
        assertEquals(new SkillLine("Woodcutting", 15, 60, 20), SkillLine.parse("Woodcutting 15/60 · cap 20"));
        assertEquals(new SkillLine("Woodcutting", 15, 60, 20), SkillLine.parse(" Woodcutting 15/60 · cap 20 "));
        assertEquals(null, SkillLine.parse("Unlock Axe Shops"));
        assertFalse(new SkillLine("Woodcutting", 15, 60, 20).met());
        assertFalse("the cap too", new SkillLine("Woodcutting", 70, 60, 50).met());
        assertTrue(new SkillLine("Woodcutting", 60, 60, 60).met());
    }

    /** Every requirement of the kinds the game can check, met: a members world, all quests done, 300 points. */
    private static GameFacts everything()
    {
        Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
        for (Quest quest : Quest.values())
        {
            quests.put(quest, QuestState.FINISHED);
        }
        return facts(true, 300, Collections.emptyMap(), quests, Collections.emptySet(), true);
    }

    private static GameFacts quest(Quest quest, QuestState state)
    {
        return facts(true, 0, Collections.emptyMap(), quests(quest, state), Collections.emptySet(), null);
    }

    private static GameFacts level(Skill skill, int level)
    {
        Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
        levels.put(skill, level);
        return facts(true, 0, levels, Collections.emptyMap(), Collections.emptySet(), null);
    }

    private static GameFacts carrying(String... names)
    {
        return facts(true, 0, Collections.emptyMap(), Collections.emptyMap(), new HashSet<>(Arrays.asList(names)),
            null);
    }

    private static Map<Quest, QuestState> quests(Object... pairs)
    {
        Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
        for (int i = 0; i < pairs.length; i += 2)
        {
            quests.put((Quest) pairs[i], (QuestState) pairs[i + 1]);
        }
        return quests;
    }

    private static GameFacts facts(Boolean members, int questPoints, Map<Skill, Integer> levels,
        Map<Quest, QuestState> quests, Set<String> carried, Boolean light)
    {
        return new GameFacts(members, questPoints, levels, quests, carried, light);
    }
}
