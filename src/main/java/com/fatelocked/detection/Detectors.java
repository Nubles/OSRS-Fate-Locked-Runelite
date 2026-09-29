package com.fatelocked.detection;

import com.fatelocked.detectors.DetectedEvent;
import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;
import net.runelite.api.gameval.VarbitID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * RuneLite's detectors for one character's session (Stage 4): fed plain signals in the order they
 * came, they give the events those make, named with the tracker's ids. Pure, so
 * contracts/golden-bundles/detected-events.json drives them as the game does.
 *
 * <p>What is already true when a session starts is its baseline: a level, a quest or a diary tier
 * counts once it changes. A quest or tier remembered from an earlier session (the character's
 * files) counts at the first reading when it was finished while RuneLite was closed.
 *
 * <p>An event's count makes its id (EventIds). Something that happens once has none, so the chat
 * line and the popup for one combat task record it once. A kill has its kill count under the
 * game's name for it, since one boss has several (Chambers of Xeric and its Challenge Mode). A
 * collection log item or a Slayer task gets an id of its own each time: the game names several
 * items alike (18 chompy bird hats), and a streak reset repeats a task at the same streak.
 */
public final class Detectors
{
    static final String SKILL = "skill-level-v1";
    static final String QUEST = "quest-state-v1";
    static final String COMBAT_TASK = "combat-achievement-chat-v1";
    static final String COLLECTION_LOG = "collection-log-chat-v1";
    static final String CLUE = "clue-completion-v1";
    static final String KILL_COUNT = "boss-kill-count-v1";
    static final String SLAYER = "slayer-task-varp-v1";
    static final String DIARY = "diary-task-v1";

    /** Only the game's own messages: a player's chat can't be a kill. */
    private static final Set<String> GAME_MESSAGES = new HashSet<>(Arrays.asList("GAMEMESSAGE", "SPAM"));

    /**
     * The game's collection log notification setting. As RuneLite's Screenshot plugin reads it,
     * the chat line stands for the item only at 1; otherwise the popup does.
     */
    static final int COLLECTION_LOG_OPTION = VarbitID.OPTION_COLLECTION_NEW_ITEM;

    /**
     * Slayer masters by the game's value, as RuneLite's own Slayer plugin names them. The rest are
     * left out until they are checked in game, and the tracker asks for the master instead.
     */
    private static final Map<Integer, String> SLAYER_MASTERS = slayerMasters();

    private final DetectionTables tables;
    private final Map<String, Integer> levels = new HashMap<>();
    /** Finished quests by RuneLite's name; null until the first reading, without a memory. */
    private Set<String> finishedQuests;
    /** Finished diary tiers by the tracker's id; null until the first reading, without a memory. */
    private Set<String> finishedTiers;
    private final Map<Integer, Integer> diaryValues = new HashMap<>();
    /** Whether the diary varbits were read in full: a change before that is the login's own. */
    private boolean diariesRead;
    /** The collection log notification setting; null until read. */
    private Integer collectionLogOption;
    /** The Slayer task in progress, as last read with some left to kill. */
    private Signal.Slayer slayerTask;

    /**
     * @param rememberedQuests the character's finished quests from an earlier session, or null
     * @param rememberedTiers the character's finished diary tiers from an earlier session, or null
     */
    public Detectors(DetectionTables tables, Set<String> rememberedQuests, Set<String> rememberedTiers)
    {
        this.tables = tables == null ? DetectionTables.none() : tables;
        this.finishedQuests = rememberedQuests == null ? null : new HashSet<>(rememberedQuests);
        this.finishedTiers = rememberedTiers == null ? null : new HashSet<>(rememberedTiers);
    }

    /** The events this signal makes, in order; none for most. */
    public List<DetectedEvent> on(Signal signal)
    {
        if (signal instanceof Signal.Chat) return chat((Signal.Chat) signal);
        if (signal instanceof Signal.Popup) return popup((Signal.Popup) signal);
        if (signal instanceof Signal.Levels)
        {
            levels.putAll(((Signal.Levels) signal).getLevels());
            return Collections.emptyList();
        }
        if (signal instanceof Signal.Level) return level((Signal.Level) signal);
        if (signal instanceof Signal.Quests) return quests((Signal.Quests) signal);
        if (signal instanceof Signal.Varbits) return varbits(((Signal.Varbits) signal).getValues(), true);
        if (signal instanceof Signal.Varbit)
        {
            Signal.Varbit varbit = (Signal.Varbit) signal;
            return varbits(Collections.singletonMap(varbit.getId(), varbit.getValue()), false);
        }
        if (signal instanceof Signal.Slayer) return slayer((Signal.Slayer) signal);
        return Collections.emptyList();
    }

    /** The finished quests to remember for the character; null before any reading. */
    public Set<String> finishedQuests()
    {
        return finishedQuests == null ? null : Collections.unmodifiableSet(new TreeSet<>(finishedQuests));
    }

    /** The finished diary tiers to remember for the character; null before any reading. */
    public Set<String> finishedTiers()
    {
        return finishedTiers == null ? null : Collections.unmodifiableSet(new TreeSet<>(finishedTiers));
    }

    private List<DetectedEvent> chat(Signal.Chat chat)
    {
        if (!GAME_MESSAGES.contains(chat.getType())) return Collections.emptyList();
        String line = chat.getMessage();
        GameLines.Count kill = GameLines.killCount(line);
        if (kill != null)
        {
            DetectionTables.Boss boss = tables.bossForKillCount(kill.name);
            if (boss == null) return Collections.emptyList();
            return one(event(boss.isRaid() ? FateEventType.RAID_COMPLETION : FateEventType.BOSS_KILL, boss.getKey(),
                KILL_COUNT, 1, EventConfidence.EXACT, DetectionTables.fold(kill.name) + " " + kill.count,
                "killCountName", kill.name, "count", kill.count));
        }
        GameLines.Count clue = GameLines.clue(line);
        if (clue != null)
        {
            return one(event(FateEventType.CLUE_CASKET, "Clue scroll (" + clue.name + ")", CLUE, 1,
                EventConfidence.EXACT, String.valueOf(clue.count), "tier", clue.name, "count", clue.count));
        }
        String[] task = GameLines.combatTask(line);
        if (task != null)
        {
            return one(event(FateEventType.COMBAT_ACHIEVEMENT, task[1], COMBAT_TASK, 2, EventConfidence.EXACT,
                DetectedEvent.ONCE, "tier", task[0], "from", "chat"));
        }
        String item = GameLines.collectionLog(line);
        if (item != null && (collectionLogOption == null || collectionLogOption == 1))
        {
            return one(event(FateEventType.COLLECTION_LOG, item, COLLECTION_LOG, 2, EventConfidence.EXACT,
                null, "from", "chat"));
        }
        return Collections.emptyList();
    }

    private List<DetectedEvent> popup(Signal.Popup popup)
    {
        String task = GameLines.combatTaskPopup(popup.getTitle(), popup.getText());
        if (task != null)
        {
            return one(event(FateEventType.COMBAT_ACHIEVEMENT, task, COMBAT_TASK, 2, EventConfidence.EXACT,
                DetectedEvent.ONCE, "from", "popup"));
        }
        String item = GameLines.collectionLogPopup(popup.getTitle(), popup.getText());
        if (item != null)
        {
            return one(event(FateEventType.COLLECTION_LOG, item, COLLECTION_LOG, 2, EventConfidence.EXACT,
                null, "from", "popup"));
        }
        return Collections.emptyList();
    }

    /** One event per level gained, oldest first, as logging each by hand does (plan decision 5). */
    private List<DetectedEvent> level(Signal.Level change)
    {
        Integer previous = levels.put(change.getSkill(), change.getLevel());
        if (previous == null) return Collections.emptyList();
        List<DetectedEvent> events = new ArrayList<>();
        for (int level = previous + 1; level <= change.getLevel(); level++)
        {
            events.add(event(FateEventType.SKILL_LEVEL, change.getSkill() + " Level " + level, SKILL, 2,
                EventConfidence.EXACT, DetectedEvent.ONCE,
                "skill", change.getSkill(), "previousLevel", level - 1, "level", level));
        }
        return events;
    }

    /** Quests the game newly says are finished. A quest is never forgotten, so one odd reading can't repeat them. */
    private List<DetectedEvent> quests(Signal.Quests reading)
    {
        if (finishedQuests == null)
        {
            finishedQuests = new HashSet<>(reading.getFinished());
            return Collections.emptyList();
        }
        List<DetectedEvent> events = new ArrayList<>();
        for (String quest : new TreeSet<>(reading.getFinished()))
        {
            if (!finishedQuests.add(quest)) continue;
            String id = tables.questId(quest);
            if (id != null)
            {
                events.add(event(FateEventType.QUEST, id, QUEST, 1, EventConfidence.EXACT, DetectedEvent.ONCE,
                    "quest", quest));
            }
        }
        return events;
    }

    /** Diary tiers newly done, in a full reading with something to compare it with or in a change. */
    private List<DetectedEvent> varbits(Map<Integer, Integer> values, boolean fullReading)
    {
        Integer option = values.get(COLLECTION_LOG_OPTION);
        if (option != null) collectionLogOption = option;
        if (!fullReading && !diariesRead) return Collections.emptyList();
        boolean baseline = fullReading && finishedTiers == null;
        if (finishedTiers == null) finishedTiers = new HashSet<>();
        if (fullReading) diariesRead = true;
        List<DetectedEvent> events = new ArrayList<>();
        for (Map.Entry<Integer, String> tier : DiaryTiers.TIER_IDS.entrySet())
        {
            Integer value = values.get(tier.getKey());
            if (value == null) continue;
            Integer previous = diaryValues.put(tier.getKey(), value);
            if (value < DiaryTiers.doneValue(tier.getKey()) || !finishedTiers.add(tier.getValue()) || baseline
                || !tables.isDiaryTier(tier.getValue()))
            {
                continue;
            }
            events.add(event(FateEventType.DIARY_TASK, tier.getValue(), DIARY, 2, EventConfidence.UNCERTAIN,
                DetectedEvent.ONCE, "tierId", tier.getValue(), "previous", previous == null ? 0 : previous,
                "completed", value));
        }
        return events;
    }

    /**
     * A task completes when none are left and its master's streak rises. The two can change in
     * separate readings. A task cancelled with points ends without the streak rising, and the next
     * task replaces it; a streak read for another master is another task's.
     */
    private List<DetectedEvent> slayer(Signal.Slayer reading)
    {
        if (reading.getAmount() > 0)
        {
            slayerTask = reading;
            return Collections.emptyList();
        }
        Signal.Slayer task = slayerTask;
        if (task == null || reading.getMaster() != task.getMaster() || reading.getStreak() <= task.getStreak())
        {
            return Collections.emptyList();
        }
        slayerTask = null;
        String name = named(task.getTask()) != null ? named(task.getTask()) : named(reading.getTask());
        if (name == null) return Collections.emptyList();
        Map<String, Object> evidence = new LinkedHashMap<>();
        String master = SLAYER_MASTERS.get(task.getMaster());
        if (master != null) evidence.put("master", master);
        evidence.put("assigned", task.getAssigned() > 0 ? task.getAssigned() : reading.getAssigned());
        evidence.put("streak", reading.getStreak());
        if (task.isBossTask()) evidence.put("bossTask", true);
        return one(DetectedEvent.builder()
            .type(FateEventType.SLAYER_TASK).canonicalLabel(name).confidence(EventConfidence.UNCERTAIN)
            .detectorId(SLAYER).detectorVersion(1).evidence(evidence).count(null)
            .build());
    }

    private static String named(String task)
    {
        return task == null || task.trim().isEmpty() ? null : task.trim();
    }

    private static DetectedEvent event(FateEventType type, String label, String detector, int version,
        EventConfidence confidence, String count, Object... evidence)
    {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < evidence.length; i += 2)
        {
            values.put((String) evidence[i], evidence[i + 1]);
        }
        return DetectedEvent.builder()
            .type(type).canonicalLabel(label).confidence(confidence).detectorId(detector).detectorVersion(version)
            .evidence(values).count(count)
            .build();
    }

    private static List<DetectedEvent> one(DetectedEvent event)
    {
        return Collections.singletonList(event);
    }

    private static Map<Integer, String> slayerMasters()
    {
        Map<Integer, String> masters = new HashMap<>();
        // RuneLite SlayerPlugin: KRYSTILIA_SLAYER_MASTER = 7, MORTIMER_SLAYER_MASTER = 10.
        masters.put(7, "Krystilia");
        masters.put(10, "Mortimer");
        return Collections.unmodifiableMap(masters);
    }
}
