package com.fatelocked.detection;

import com.fatelocked.detectors.DetectedEvent;
import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.fatelocked.events.EventConfidence.EXACT;
import static com.fatelocked.events.EventConfidence.UNCERTAIN;
import static com.fatelocked.events.FateEventType.BOSS_KILL;
import static com.fatelocked.events.FateEventType.CLUE_CASKET;
import static com.fatelocked.events.FateEventType.COLLECTION_LOG;
import static com.fatelocked.events.FateEventType.COMBAT_ACHIEVEMENT;
import static com.fatelocked.events.FateEventType.DIARY_TASK;
import static com.fatelocked.events.FateEventType.QUEST;
import static com.fatelocked.events.FateEventType.RAID_COMPLETION;
import static com.fatelocked.events.FateEventType.SKILL_LEVEL;
import static com.fatelocked.events.FateEventType.SLAYER_TASK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class DetectorsTest
{
    private static final DetectionTables TABLES = DetectionTables.parse(new JsonParser().parse(("{`bosses`:["
        + "{`key`:`Corporeal Beast`,`raid`:false,`killCounts`:[`Corporeal Beast`]},"
        + "{`key`:`The Gauntlet`,`raid`:false,`killCounts`:[`Gauntlet`,`Corrupted Gauntlet`]},"
        + "{`key`:`Chambers of Xeric`,`raid`:true,`killCounts`:[`Chambers of Xeric`,`Chambers of Xeric Challenge Mode`]}],"
        + "`quests`:[{`id`:`Cook's Assistant`,`name`:`Cook's Assistant`},{`id`:`Rune Mysteries`,`name`:`Rune Mysteries`},"
        + "{`id`:`Doric's Quest`,`name`:`Doric's Quest`},{`id`:`RFD: The Cook`,`name`:`RFD: The Cook`}],"
        + "`diaryTiers`:[`Varrock Easy`,`Lumbridge Easy`,`Karamja Easy`,`Karamja Elite`]}").replace('`', '"')));

    private static final int VARROCK_EASY = 4479;
    private static final int LUMBRIDGE_EASY = 4495;
    private static final int KARAMJA_EASY = 3578;
    private static final int ARDOUGNE_EASY = 4458;
    private static final int COLLECTION_LOG_OPTION = 11959;

    // ── Kills, clues, combat tasks and the collection log ─────────────────────

    @Test
    public void aKillCountIsABossKillUnderTheTrackersKey()
    {
        assertEquals(List.of(event(BOSS_KILL, "Corporeal Beast", EXACT, Detectors.KILL_COUNT, 1, "corporeal beast 4",
                "killCountName", "Corporeal Beast", "count", 4L)),
            fresh().on(chat("Your Corporeal Beast kill count is: <col=ff0000>4</col>.")));
        assertEquals(List.of(event(BOSS_KILL, "The Gauntlet", EXACT, Detectors.KILL_COUNT, 1, "corrupted gauntlet 4729",
                "killCountName", "Corrupted Gauntlet", "count", 4729L)),
            fresh().on(chat("Your Corrupted Gauntlet completion count is: <col=ff0000>4729</col>.")));
    }

    @Test
    public void aRaidsCountIsARaidCompletion()
    {
        assertEquals(List.of(event(RAID_COMPLETION, "Chambers of Xeric", EXACT, Detectors.KILL_COUNT, 1,
                "chambers of xeric challenge mode 13", "killCountName", "Chambers of Xeric Challenge Mode", "count", 13L)),
            fresh().on(chat("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>13</col>.")));
    }

    @Test
    public void aBossesCountsUnderEachOfItsNamesNeverShareAnId()
    {
        Detectors detectors = fresh();
        String normal = detectors.on(chat("Your completed Chambers of Xeric count is: <col=ff0000>13</col>.")).get(0).getCount();
        String challenge = detectors.on(chat("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>13</col>."))
            .get(0).getCount();
        String again = detectors.on(chat("Your completed Chambers of Xeric count is: 13.")).get(0).getCount();

        assertNotEquals(normal, challenge);
        // Seeing one line twice records it once.
        assertEquals(normal, again);
    }

    @Test
    public void aCountThatNamesNoBossIsNoEvent()
    {
        Detectors detectors = fresh();

        assertEquals(List.of(), detectors.on(chat("Your Prifddinas Agility Course lap count is: @mes_hl_red@2</col>.")));
        assertEquals(List.of(), detectors.on(chat("Your <col=a53fff>Corrupted Hunllef (Echo)</col> kill count is: 31")));
        assertEquals(List.of(), new Detectors(null, null, null).on(chat("Your Corporeal Beast kill count is: 4.")));
    }

    @Test
    public void aNewerBundleBringsItsTables()
    {
        Detectors detectors = new Detectors(null, null, null);
        Signal line = chat("Your Corporeal Beast kill count is: <col=ff0000>4</col>.");
        assertEquals(List.of(), detectors.on(line));

        detectors.useTables(TABLES);
        assertEquals(List.of("Corporeal Beast"), labels(detectors.on(line)));

        detectors.useTables(null);
        assertEquals(List.of(), detectors.on(line));
    }

    @Test
    public void onlyTheGamesOwnMessagesCount()
    {
        String line = "Your Corporeal Beast kill count is: <col=ff0000>5</col>.";
        for (String type : new String[]{"PUBLICCHAT", "PRIVATECHAT", "FRIENDSCHAT", "CLAN_CHAT", "CLAN_GIM_CHAT",
            "TRADE", "FRIENDSCHATNOTIFICATION", "CONSOLE", null})
        {
            assertEquals(String.valueOf(type), List.of(), fresh().on(new Signal.Chat(type, line)));
        }
        assertEquals(1, fresh().on(new Signal.Chat("SPAM", line)).size());
        assertEquals(1, fresh().on(new Signal.Chat("GAMEMESSAGE", line)).size());
    }

    @Test
    public void aClueCountsUnderItsTier()
    {
        assertEquals(List.of(event(CLUE_CASKET, "Clue scroll (medium)", EXACT, Detectors.CLUE, 1, "2823",
                "tier", "medium", "count", 2823L)),
            fresh().on(chat("<col=3300ff>You have completed 2,823 medium Treasure Trails</col>")));
    }

    @Test
    public void aCombatTaskHasOneIdFromTheChatLineOrThePopup()
    {
        Detectors detectors = fresh();

        assertEquals(List.of(event(COMBAT_ACHIEVEMENT, "Egniol Diet II", EXACT, Detectors.COMBAT_TASK, 2, DetectedEvent.ONCE,
                "tier", "grandmaster", "from", "chat")),
            detectors.on(chat("Congratulations, you've completed a grandmaster combat task: "
                + "@ach_comp@Egniol Diet II</col> (6 points).")));
        assertEquals(List.of(event(COMBAT_ACHIEVEMENT, "Egniol Diet II", EXACT, Detectors.COMBAT_TASK, 2, DetectedEvent.ONCE,
                "from", "popup")),
            detectors.on(new Signal.Popup("Combat Task Completed!", "Task Completed: <col=ffffff>Egniol Diet II</col> (6 points)")));
    }

    @Test
    public void theCollectionLogsChatLineCountsUnlessThePopupShowsTheItem()
    {
        String line = "New item added to your collection log: <col=ef1020>Dragon pickaxe</col>";
        Signal popup = new Signal.Popup("Collection log", "New item:<br><br><col=ffffff>Dragon pickaxe</col>");
        List<DetectedEvent> fromChat = List.of(event(COLLECTION_LOG, "Dragon pickaxe", EXACT, Detectors.COLLECTION_LOG, 2,
            null, "from", "chat"));
        List<DetectedEvent> fromPopup = List.of(event(COLLECTION_LOG, "Dragon pickaxe", EXACT, Detectors.COLLECTION_LOG, 2,
            null, "from", "popup"));

        // Before the setting is read, the line counts.
        assertEquals(fromChat, fresh().on(chat(line)));

        // 1 is chat only, as RuneLite's Screenshot plugin reads it.
        Detectors chatOnly = fresh();
        chatOnly.on(new Signal.Varbits(Map.of(COLLECTION_LOG_OPTION, 1)));
        assertEquals(fromChat, chatOnly.on(chat(line)));

        // Otherwise the popup shows the item, even with the line in chat too.
        Detectors both = fresh();
        both.on(new Signal.Varbits(Map.of(COLLECTION_LOG_OPTION, 3)));
        assertEquals(List.of(), both.on(chat(line)));
        assertEquals(fromPopup, both.on(popup));

        // A change of setting counts at once, even before the diaries are read.
        Detectors changed = fresh();
        changed.on(new Signal.Varbit(COLLECTION_LOG_OPTION, 2));
        assertEquals(List.of(), changed.on(chat(line)));
        changed.on(new Signal.Varbit(COLLECTION_LOG_OPTION, 1));
        assertEquals(fromChat, changed.on(chat(line)));
        changed.on(new Signal.Varbit(COLLECTION_LOG_OPTION, 2));
        // A reading without the setting keeps it.
        changed.on(new Signal.Varbits(Map.of(VARROCK_EASY, 0)));
        changed.on(new Signal.Varbit(VARROCK_EASY, 1));
        assertEquals(List.of(), changed.on(chat(line)));
    }

    @Test
    public void everyChompyBirdHatIsAnEventOfItsOwn()
    {
        // The game names all 18 alike; the count leaves each its own id.
        Detectors detectors = fresh();
        String line = "New item added to your collection log: <col=ef1020>Chompy bird hat</col>";

        assertEquals(1, detectors.on(chat(line)).size());
        assertEquals(1, detectors.on(chat(line)).size());
        assertNull(detectors.on(chat(line)).get(0).getCount());
    }

    @Test
    public void anUnrelatedPopupOrLineIsNoEvent()
    {
        assertEquals(List.of(), fresh().on(new Signal.Popup("Some other title", "Some text")));
        assertEquals(List.of(), fresh().on(chat("Welcome to Old School RuneScape.")));
        assertEquals(List.of(), fresh().on(null));
    }

    // ── Levels ─────────────────────────────────────────────────────────────────

    @Test
    public void oneEventPerLevelGainedOldestFirst()
    {
        Detectors detectors = fresh();
        detectors.on(new Signal.Levels(Map.of("Attack", 70, "Runecraft", 44)));

        assertEquals(List.of(
                event(SKILL_LEVEL, "Attack Level 71", EXACT, Detectors.SKILL, 2, DetectedEvent.ONCE,
                    "skill", "Attack", "previousLevel", 70, "level", 71),
                event(SKILL_LEVEL, "Attack Level 72", EXACT, Detectors.SKILL, 2, DetectedEvent.ONCE,
                    "skill", "Attack", "previousLevel", 71, "level", 72)),
            detectors.on(new Signal.Level("Attack", 72)));
        assertEquals(List.of(event(SKILL_LEVEL, "Runecraft Level 45", EXACT, Detectors.SKILL, 2, DetectedEvent.ONCE,
                "skill", "Runecraft", "previousLevel", 44, "level", 45)),
            detectors.on(new Signal.Level("Runecraft", 45)));
    }

    @Test
    public void aSkillsFirstReadingIsItsBaseline()
    {
        Detectors detectors = fresh();

        assertEquals(List.of(), detectors.on(new Signal.Level("Attack", 71)));
        assertEquals(List.of("Attack Level 72"), labels(detectors.on(new Signal.Level("Attack", 72))));
    }

    @Test
    public void theSameLevelIsNoEvent()
    {
        Detectors detectors = fresh();
        detectors.on(new Signal.Levels(Map.of("Attack", 70)));

        assertEquals(List.of(), detectors.on(new Signal.Level("Attack", 70)));
        assertEquals(List.of(), detectors.on(new Signal.Level("Attack", 69)));
    }

    // ── Quests ─────────────────────────────────────────────────────────────────

    @Test
    public void aQuestTheGameNewlySaysIsFinished()
    {
        Detectors detectors = fresh();

        assertNull(detectors.finishedQuests());
        assertEquals(List.of(), detectors.on(quests("Rune Mysteries")));
        assertEquals(List.of(event(QUEST, "Cook's Assistant", EXACT, Detectors.QUEST, 1, DetectedEvent.ONCE,
                "quest", "Cook's Assistant")),
            detectors.on(quests("Rune Mysteries", "Cook's Assistant")));
        assertEquals(Set.of("Rune Mysteries", "Cook's Assistant"), detectors.finishedQuests());
    }

    @Test
    public void questsFinishedTogetherComeInOrder()
    {
        Detectors detectors = fresh();
        detectors.on(quests());

        assertEquals(List.of("Cook's Assistant", "Doric's Quest", "Rune Mysteries"),
            labels(detectors.on(quests("Rune Mysteries", "Doric's Quest", "Cook's Assistant"))));
    }

    @Test
    public void aRememberedQuestCountsWhatWasFinishedWhileRuneLiteWasClosed()
    {
        Detectors detectors = new Detectors(TABLES, Set.of("Rune Mysteries"), null);

        assertEquals(List.of("Cook's Assistant"), labels(detectors.on(quests("Rune Mysteries", "Cook's Assistant"))));
    }

    @Test
    public void aFinishedQuestIsNeverForgotten()
    {
        Detectors detectors = fresh();
        detectors.on(quests("Rune Mysteries", "Cook's Assistant"));

        // A reading taken before every quest came from the server can't make one look new.
        assertEquals(List.of(), detectors.on(quests("Rune Mysteries")));
        assertEquals(List.of(), detectors.on(quests("Rune Mysteries", "Cook's Assistant")));
        assertEquals(Set.of("Rune Mysteries", "Cook's Assistant"), detectors.finishedQuests());
    }

    @Test
    public void aQuestTheTrackerDoesntListIsRememberedWithoutAnEvent()
    {
        Detectors detectors = fresh();
        detectors.on(quests());

        assertEquals(List.of(), detectors.on(quests("Dragon Slayer II")));
        assertEquals(Set.of("Dragon Slayer II"), detectors.finishedQuests());
    }

    @Test
    public void aRecipeForDisasterPartTakesTheTrackersId()
    {
        Detectors detectors = fresh();
        detectors.on(quests("Cook's Assistant"));

        assertEquals(List.of(event(QUEST, "RFD: The Cook", EXACT, Detectors.QUEST, 1, DetectedEvent.ONCE,
                "quest", "Recipe for Disaster - Another Cook's Quest")),
            detectors.on(quests("Cook's Assistant", "Recipe for Disaster - Another Cook's Quest")));
    }

    // ── Diaries ────────────────────────────────────────────────────────────────

    @Test
    public void aTierDoneAfterTheBaselineIsAnEvent()
    {
        Detectors detectors = fresh();

        assertNull(detectors.finishedTiers());
        assertEquals(List.of(), detectors.on(new Signal.Varbits(Map.of(VARROCK_EASY, 0))));
        assertEquals(List.of(event(DIARY_TASK, "Varrock Easy", UNCERTAIN, Detectors.DIARY, 2, DetectedEvent.ONCE,
                "tierId", "Varrock Easy", "previous", 0, "completed", 1)),
            detectors.on(new Signal.Varbit(VARROCK_EASY, 1)));
        assertEquals(Set.of("Varrock Easy"), detectors.finishedTiers());
        assertEquals(List.of(), detectors.on(new Signal.Varbit(VARROCK_EASY, 1)));
    }

    @Test
    public void karamjasFirstTiersAreStartedAtOneAndDoneAtTwo()
    {
        Detectors detectors = fresh();
        detectors.on(new Signal.Varbits(Map.of(KARAMJA_EASY, 0)));

        assertEquals(List.of(), detectors.on(new Signal.Varbit(KARAMJA_EASY, 1)));
        assertEquals(List.of(event(DIARY_TASK, "Karamja Easy", UNCERTAIN, Detectors.DIARY, 2, DetectedEvent.ONCE,
                "tierId", "Karamja Easy", "previous", 1, "completed", 2)),
            detectors.on(new Signal.Varbit(KARAMJA_EASY, 2)));
    }

    @Test
    public void aChangeBeforeTheFullReadingIsTheLoginsOwn()
    {
        Detectors detectors = fresh();

        assertEquals(List.of(), detectors.on(new Signal.Varbit(VARROCK_EASY, 1)));
        assertNull(detectors.finishedTiers());
        assertEquals(List.of(), detectors.on(new Signal.Varbits(Map.of(VARROCK_EASY, 1, LUMBRIDGE_EASY, 1))));
        assertEquals(Set.of("Varrock Easy", "Lumbridge Easy"), detectors.finishedTiers());
        assertEquals(List.of(), detectors.on(new Signal.Varbit(VARROCK_EASY, 1)));
    }

    @Test
    public void aRememberedTierCountsWhatWasDoneWhileRuneLiteWasClosed()
    {
        Detectors detectors = new Detectors(TABLES, null, Set.of("Lumbridge Easy"));

        assertEquals(List.of(event(DIARY_TASK, "Varrock Easy", UNCERTAIN, Detectors.DIARY, 2, DetectedEvent.ONCE,
                "tierId", "Varrock Easy", "previous", 0, "completed", 1)),
            detectors.on(new Signal.Varbits(Map.of(VARROCK_EASY, 1, LUMBRIDGE_EASY, 1, KARAMJA_EASY, 1))));
    }

    @Test
    public void tiersOfOneReadingComeInTheTrackersOrder()
    {
        Map<Integer, Integer> reading = new LinkedHashMap<>();
        reading.put(VARROCK_EASY, 1);
        reading.put(LUMBRIDGE_EASY, 1);
        reading.put(1234, 1);

        assertEquals(List.of("Lumbridge Easy", "Varrock Easy"),
            labels(new Detectors(TABLES, null, Set.of()).on(new Signal.Varbits(reading))));
    }

    @Test
    public void aTierTheTrackerDoesntListIsRememberedWithoutAnEvent()
    {
        Detectors detectors = fresh();
        detectors.on(new Signal.Varbits(Map.of(ARDOUGNE_EASY, 0)));

        assertEquals(List.of(), detectors.on(new Signal.Varbit(ARDOUGNE_EASY, 1)));
        assertEquals(Set.of("Ardougne Easy"), detectors.finishedTiers());
    }

    // ── Slayer ─────────────────────────────────────────────────────────────────

    @Test
    public void aTaskCompletesWhenNoneAreLeftAndTheStreakRises()
    {
        Detectors detectors = fresh();

        assertEquals(List.of(), detectors.on(slayer("Abyssal demons", 1, 120, 7, 43)));
        assertEquals(List.of(event(SLAYER_TASK, "Abyssal demons", UNCERTAIN, Detectors.SLAYER, 1, null,
                "master", "Krystilia", "assigned", 120, "streak", 44)),
            detectors.on(slayer("Abyssal demons", 0, 120, 7, 44)));
        // Done: the next streak needs a task of its own.
        assertEquals(List.of(), detectors.on(slayer("Abyssal demons", 0, 120, 7, 45)));
    }

    @Test
    public void theAmountAndTheStreakCanChangeApart()
    {
        Detectors detectors = fresh();
        detectors.on(slayer("Abyssal demons", 1, 120, 7, 43));

        assertEquals(List.of(), detectors.on(slayer("Abyssal demons", 0, 120, 7, 43)));
        assertEquals(List.of("Abyssal demons"), labels(detectors.on(slayer("Abyssal demons", 0, 120, 7, 44))));
    }

    @Test
    public void aCancelledTaskIsNoCompletionAndTheNextTaskReplacesIt()
    {
        Detectors detectors = fresh();
        detectors.on(slayer("Abyssal demons", 80, 120, 7, 43));
        assertEquals(List.of(), detectors.on(slayer("Abyssal demons", 0, 120, 7, 43)));

        detectors.on(slayer("Cows", 20, 20, 7, 43));
        assertEquals(List.of("Cows"), labels(detectors.on(slayer("", 0, 0, 7, 44))));
    }

    @Test
    public void aStreakReadForAnotherMasterIsAnotherTasks()
    {
        Detectors detectors = fresh();
        detectors.on(slayer("Abyssal demons", 5, 120, 7, 43));

        assertEquals(List.of(), detectors.on(slayer("Abyssal demons", 0, 120, 9, 500)));
    }

    @Test
    public void aTaskResetRestartsTheStreak()
    {
        Detectors detectors = fresh();
        detectors.on(slayer("Abyssal demons", 50, 120, 9, 100));
        detectors.on(slayer("Cows", 30, 30, 1, 0));

        assertEquals(List.of(event(SLAYER_TASK, "Cows", UNCERTAIN, Detectors.SLAYER, 1, null,
                "assigned", 30, "streak", 1)),
            detectors.on(slayer("Cows", 0, 30, 1, 1)));
    }

    @Test
    public void onlyTheMastersCheckedInGameAreNamed()
    {
        assertEquals("Mortimer", completed(10).getEvidence().get("master"));
        assertEquals("Krystilia", completed(7).getEvidence().get("master"));
        for (int unchecked : new int[]{0, 1, 5, 9, 11})
        {
            assertFalse(String.valueOf(unchecked), completed(unchecked).getEvidence().containsKey("master"));
        }
    }

    @Test
    public void aBossTaskSaysSo()
    {
        Detectors detectors = fresh();
        detectors.on(new Signal.Slayer("Vorkath", 3, 5, 9, 10, true));

        assertEquals(event(SLAYER_TASK, "Vorkath", UNCERTAIN, Detectors.SLAYER, 1, null,
                "assigned", 5, "streak", 11, "bossTask", true),
            detectors.on(new Signal.Slayer(null, 0, 5, 9, 11, false)).get(0));
    }

    @Test
    public void aTaskAlreadyDoneAtLoginIsNotNew()
    {
        Detectors detectors = fresh();

        assertEquals(List.of(), detectors.on(slayer("Abyssal demons", 0, 120, 7, 44)));
        assertEquals(List.of(), detectors.on(slayer("Abyssal demons", 0, 120, 7, 45)));
    }

    @Test
    public void theNameAndSizeFallBackToTheCompletingReading()
    {
        Detectors detectors = fresh();
        detectors.on(slayer(" ", 1, 0, 7, 43));
        assertEquals(event(SLAYER_TASK, "Abyssal demons", UNCERTAIN, Detectors.SLAYER, 1, null,
                "master", "Krystilia", "assigned", 120, "streak", 44),
            detectors.on(slayer(" Abyssal demons ", 0, 120, 7, 44)).get(0));

        Detectors nameless = fresh();
        nameless.on(slayer(null, 1, 120, 7, 43));
        assertEquals(List.of(), nameless.on(slayer("", 0, 120, 7, 44)));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static Detectors fresh()
    {
        return new Detectors(TABLES, null, null);
    }

    private static Signal chat(String message)
    {
        return new Signal.Chat("GAMEMESSAGE", message);
    }

    private static Signal quests(String... finished)
    {
        return new Signal.Quests(new HashSet<>(Arrays.asList(finished)));
    }

    private static Signal slayer(String task, int amount, int assigned, int master, int streak)
    {
        return new Signal.Slayer(task, amount, assigned, master, streak, false);
    }

    private static DetectedEvent completed(int master)
    {
        Detectors detectors = fresh();
        detectors.on(slayer("Abyssal demons", 1, 120, master, 43));
        return detectors.on(slayer("Abyssal demons", 0, 120, master, 44)).get(0);
    }

    private static List<String> labels(List<DetectedEvent> events)
    {
        String[] labels = new String[events.size()];
        for (int i = 0; i < labels.length; i++)
        {
            labels[i] = events.get(i).getCanonicalLabel();
        }
        return Arrays.asList(labels);
    }

    private static DetectedEvent event(FateEventType type, String label, EventConfidence confidence, String detector,
        int version, String count, Object... evidence)
    {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < evidence.length; i += 2)
        {
            values.put((String) evidence[i], evidence[i + 1]);
        }
        return DetectedEvent.builder().type(type).canonicalLabel(label).confidence(confidence).detectorId(detector)
            .detectorVersion(version).evidence(values).count(count).build();
    }
}
