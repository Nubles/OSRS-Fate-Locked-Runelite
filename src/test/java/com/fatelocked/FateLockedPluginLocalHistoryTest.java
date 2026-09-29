package com.fatelocked;

import com.fatelocked.detection.Detectors;
import com.fatelocked.sidebar.RollInboxModel;
import com.fatelocked.detectors.DetectedEvent;
import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.DetectedEventStore;
import com.fatelocked.events.FateEvent;
import com.fatelocked.events.FateEventType;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FateLockedPluginLocalHistoryTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void validBundleAndAccountRecordLocallyWithoutPairing()
        throws Exception
    {
        Harness harness = harness("unpaired");

        assertFalse(harness.connectionSettings.isPaired());
        invokeRecord(harness.plugin, detected("Dragon Slayer"));

        assertEquals(1, events(harness).size());
        assertEquals("Dragon Slayer",
            events(harness).get(0).getCanonicalLabel());
        verify(harness.panel).showRollInbox(new RollInboxModel(1, 0, 0, false));
    }

    @Test
    public void aCompletedClueIsRecordedFromTheGamesLine() throws Exception
    {
        Harness harness = harness("clues");
        readForDetectors(harness);

        harness.plugin.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, "You have completed 12 hard Treasure Trails."));
        // A player saying it is not the game.
        harness.plugin.onChatMessage(chat(ChatMessageType.PUBLICCHAT, "You have completed 13 hard Treasure Trails."));

        assertEquals(List.of("Clue scroll (hard)"), labels(harness));
        assertEquals(FateEventType.CLUE_CASKET, events(harness).get(0).getEventType());
    }

    @Test
    public void aKillCountLineIsRecordedUnderTheBossesKey() throws Exception
    {
        Harness harness = harness("kills");
        readForDetectors(harness);
        String line = "Your Vorkath kill count is: <col=ff0000>12</col>.";
        // Rules without the tracker's names notice no boss...
        harness.plugin.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, line));
        assertEquals(0, events(harness).size());

        // ...and newer rules bring them mid-session.
        useDetectionTables(harness);
        harness.plugin.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, line));
        harness.plugin.onChatMessage(chat(ChatMessageType.GAMEMESSAGE,
            "Your Prifddinas Agility Course lap count is: @mes_hl_red@2</col>."));

        assertEquals(List.of("Vorkath"), labels(harness));
        assertEquals(FateEventType.BOSS_KILL, events(harness).get(0).getEventType());
    }

    @Test
    public void aLevelUpAfterTheSessionsReadingIsRecordedOncePerLevel() throws Exception
    {
        Harness harness = harness("levels");
        when(harness.client.getRealSkillLevel(Skill.ATTACK)).thenReturn(70);
        // The login's own levels, before the session's reading.
        harness.plugin.onStatChanged(new StatChanged(Skill.ATTACK, 0, 70, 70));
        readForDetectors(harness);
        readForDetectors(harness);
        verify(harness.client, times(1)).getRealSkillLevel(Skill.ATTACK);

        harness.plugin.onStatChanged(new StatChanged(Skill.ATTACK, 0, 72, 72));

        assertEquals(List.of("Attack Level 71", "Attack Level 72"), labels(harness));
    }

    @Test
    public void aQuestFinishedWithoutTheScrollIsReadWithinAMinute() throws Exception
    {
        Harness harness = harness("quests-minute");
        useDetectionTables(harness);
        readForDetectors(harness);
        harness.finishedQuests.add(Quest.COOKS_ASSISTANT);

        readAtTick(harness, FateLockedPlugin.QUEST_READING_TICKS - 1);
        assertEquals(0, events(harness).size());
        readAtTick(harness, FateLockedPlugin.QUEST_READING_TICKS);
        assertEquals(List.of("Cook's Assistant"), labels(harness));

        // The next reading is a minute after that one.
        harness.finishedQuests.add(Quest.RUNE_MYSTERIES);
        readAtTick(harness, 2 * FateLockedPlugin.QUEST_READING_TICKS - 1);
        assertEquals(1, events(harness).size());
        readAtTick(harness, 2 * FateLockedPlugin.QUEST_READING_TICKS);
        assertEquals(List.of("Cook's Assistant", "Rune Mysteries"), labels(harness));
    }

    @Test
    public void aQuestIsReadAfterTheQuestScrollAndRemembered() throws Exception
    {
        Harness harness = harness("quests");
        useDetectionTables(harness);
        useMemories(harness);
        readForDetectors(harness);
        harness.finishedQuests.add(Quest.COOKS_ASSISTANT);

        WidgetLoaded scroll = new WidgetLoaded();
        scroll.setGroupId(153);
        harness.plugin.onWidgetLoaded(scroll);
        assertEquals(0, events(harness).size());
        readForDetectors(harness);

        assertEquals(List.of("Cook's Assistant"), labels(harness));
        assertEquals(Set.of("Cook's Assistant"),
            FinishedMemory.open(harness.gson, harness.dataDirectory.resolve(FinishedMemory.QUESTS)).finished());
    }

    @Test
    public void aSlayerTaskCompletesFromTheGamesVariables() throws Exception
    {
        Harness harness = harness("slayer");
        Client client = harness.client;
        slayerTask(client, "Abyssal demons", 110);
        when(client.getVarbitValue(VarbitID.SLAYER_MASTER)).thenReturn(Detectors.KRYSTILIA);
        // The task's size, with its modifier.
        when(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID)).thenReturn(2);
        when(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_VALUE)).thenReturn(10);
        // Krystilia's streak is her own.
        when(client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED)).thenReturn(10);
        when(client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED)).thenReturn(300);
        readForDetectors(harness);

        // The last kill: none left, and Krystilia's streak rises.
        when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(0);
        when(client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED)).thenReturn(11);
        harness.plugin.onVarbitChanged(varp(VarPlayerID.SLAYER_COUNT, 0));
        assertEquals("read once the tick's changes are in", 0, events(harness).size());
        readForDetectors(harness);
        // Read again only when a Slayer variable changes.
        readForDetectors(harness);
        verify(client, times(2)).getVarpValue(VarPlayerID.SLAYER_COUNT);

        assertEquals(List.of("Abyssal demons"), labels(harness));
        FateEvent task = events(harness).get(0);
        assertEquals("Krystilia", task.getEvidence().get("master"));
        assertEquals(11, ((Number) task.getEvidence().get("streak")).intValue());
        assertEquals(120, ((Number) task.getEvidence().get("assigned")).intValue());
    }

    @Test
    public void mortimersStreakIsHisOwn() throws Exception
    {
        Harness harness = harness("slayer-mortimer");
        Client client = harness.client;
        slayerTask(client, "Kurask", 130);
        when(client.getVarbitValue(VarbitID.SLAYER_MASTER)).thenReturn(Detectors.MORTIMER);
        // A modifier can take some off.
        when(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID)).thenReturn(2);
        when(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_VALUE)).thenReturn(10);
        when(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_NEGATIVE)).thenReturn(1);
        when(client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED)).thenReturn(4);
        when(client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED)).thenReturn(300);
        readForDetectors(harness);

        when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(0);
        when(client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED)).thenReturn(5);
        harness.plugin.onVarbitChanged(varp(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED, 5));
        readForDetectors(harness);

        assertEquals(List.of("Kurask"), labels(harness));
        assertEquals("Mortimer", events(harness).get(0).getEvidence().get("master"));
        assertEquals(120, ((Number) events(harness).get(0).getEvidence().get("assigned")).intValue());
    }

    @Test
    public void everyOtherMasterSharesTheStreak() throws Exception
    {
        Harness harness = harness("slayer-shared");
        Client client = harness.client;
        slayerTask(client, "Kurask", 120);
        when(client.getVarbitValue(VarbitID.SLAYER_MASTER)).thenReturn(9);
        when(client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED)).thenReturn(300);
        when(client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED)).thenReturn(7);
        readForDetectors(harness);

        when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(0);
        when(client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED)).thenReturn(301);
        // Only the streak's varbit says so this time.
        harness.plugin.onVarbitChanged(varbit(VarbitID.SLAYER_TASKS_COMPLETED, 301));
        readForDetectors(harness);

        assertEquals(List.of("Kurask"), labels(harness));
        assertFalse(events(harness).get(0).getEvidence().containsKey("master"));
        assertFalse(events(harness).get(0).getEvidence().containsKey("bossTask"));
    }

    @Test
    public void aBossTaskIsNamedFromTheGamesBossTable() throws Exception
    {
        Harness harness = harness("slayer-boss");
        Client client = harness.client;
        when(client.getVarpValue(VarPlayerID.SLAYER_TARGET)).thenReturn(98);
        when(client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID)).thenReturn(3);
        when(client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID, DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, 3))
            .thenReturn(List.of(20));
        when(client.getDBTableField(20, DBTableID.SlayerTaskSublist.COL_TASK, 0)).thenReturn(new Object[]{21});
        when(client.getDBTableField(21, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)).thenReturn(new Object[]{"Vorkath"});
        when(client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL)).thenReturn(5);
        when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(3);
        when(client.getVarbitValue(VarbitID.SLAYER_MASTER)).thenReturn(9);
        when(client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED)).thenReturn(10);
        readForDetectors(harness);

        when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(0);
        when(client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED)).thenReturn(11);
        harness.plugin.onVarbitChanged(varp(VarPlayerID.SLAYER_COUNT, 0));
        readForDetectors(harness);

        assertEquals(List.of("Vorkath"), labels(harness));
        assertEquals(true, events(harness).get(0).getEvidence().get("bossTask"));
    }

    @Test
    public void theCollectionLogsSettingSaysWhichNotificationCounts() throws Exception
    {
        Harness harness = harness("collection-log");
        when(harness.client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM)).thenReturn(3);
        popup(harness, "Collection log", "New item:<br><br><col=ffffff>Dragon pickaxe</col>");
        readForDetectors(harness);

        // With the popup on, the popup counts and the line doesn't.
        harness.plugin.onChatMessage(chat(ChatMessageType.GAMEMESSAGE,
            "New item added to your collection log: <col=ef1020>Dragon pickaxe</col>"));
        harness.plugin.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_START));
        harness.plugin.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_DELAY));
        assertEquals(List.of("Dragon pickaxe"), labels(harness));
        assertEquals("popup", events(harness).get(0).getEvidence().get("from"));

        // Chat only: the line counts.
        harness.plugin.onVarbitChanged(varbit(VarbitID.OPTION_COLLECTION_NEW_ITEM, 1));
        harness.plugin.onChatMessage(chat(ChatMessageType.GAMEMESSAGE,
            "New item added to your collection log: <col=ef1020>Chompy bird hat</col>"));
        assertEquals(List.of("Dragon pickaxe", "Chompy bird hat"), labels(harness));
    }

    @Test
    public void aCombatTaskPopupIsRecordedOnceShown() throws Exception
    {
        Harness harness = harness("popup");
        popup(harness, "Combat Task Completed!", "Task Completed: <col=ffffff>Handyman</col> (6 points)");
        readForDetectors(harness);

        // The delay alone is some other notification's.
        harness.plugin.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_DELAY));
        assertEquals(0, events(harness).size());

        harness.plugin.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_START));
        harness.plugin.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_DELAY));

        assertEquals(List.of("Handyman"), labels(harness));
        assertEquals(FateEventType.COMBAT_ACHIEVEMENT, events(harness).get(0).getEventType());
    }

    @Test
    public void aPopupIsReadOncePerStart() throws Exception
    {
        Harness harness = harness("popup-once");
        popup(harness, "Collection log", "New item:<br><br><col=ffffff>Chompy bird hat</col>");
        readForDetectors(harness);

        harness.plugin.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_START));
        harness.plugin.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_DELAY));
        harness.plugin.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_DELAY));

        // Each chompy bird hat is an item of its own, so a second reading would be a second event.
        assertEquals(List.of("Chompy bird hat"), labels(harness));
    }

    @Test
    public void aSavedEventGetsOneReminderWhileTheSettingIsOn() throws Exception
    {
        Harness harness = harness("reminders");
        FateLockedConfig config = (FateLockedConfig) PluginTestSupport.get(harness.plugin, "config");
        when(config.rollNudges()).thenReturn(true);
        ChatMessageManager chat = mock(ChatMessageManager.class);
        setField(harness.plugin, "chatMessageManager", chat);
        readForDetectors(harness);

        harness.plugin.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, "You have completed 12 hard Treasure Trails."));
        // The same line again is the same event: nothing saved, so no second reminder.
        harness.plugin.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, "You have completed 12 hard Treasure Trails."));

        ArgumentCaptor<QueuedMessage> line = ArgumentCaptor.forClass(QueuedMessage.class);
        verify(chat).queue(line.capture());
        assertTrue(line.getValue().getRuneLiteFormattedMessage(),
            line.getValue().getRuneLiteFormattedMessage().endsWith("Clue scroll (hard): added to your Roll inbox."));

        when(config.rollNudges()).thenReturn(false);
        harness.plugin.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, "You have completed 13 hard Treasure Trails."));
        assertEquals(2, events(harness).size());
        verify(chat).queue(any(QueuedMessage.class));
    }

    @Test
    public void theLoggedInAccountsOwnFilesAreOpenedAndUsed() throws Exception
    {
        Harness harness = harness("per-account");
        when(harness.client.getAccountHash()).thenReturn(7L);
        FinishedMemory.open(harness.gson, AccountFiles.folder(harness.dataDirectory, 7L).resolve(FinishedMemory.QUESTS))
            .remember(List.of("Cook's Assistant"));
        FinishedMemory.open(harness.gson, AccountFiles.folder(harness.dataDirectory, 7L).resolve(FinishedMemory.DIARY_TIERS))
            .remember(List.of("Lumbridge Easy"));

        invokeNoArg(harness.plugin, "openAccountFiles");
        invokeRecord(harness.plugin, detected("Dragon Slayer"));

        assertTrue(Files.exists(harness.dataDirectory.resolve("accounts/7/detected-events.json")));
        assertEquals(0, events(harness).size());
        // The session's detectors start from what the account remembers.
        assertEquals(Set.of("Cook's Assistant"), PluginTestSupport.get(harness.plugin, "questsRemembered"));
        assertEquals(Set.of("Lumbridge Easy"), PluginTestSupport.get(harness.plugin, "tiersRemembered"));
    }

    @Test
    public void aDetectionBeforeTheAccountsOwnFilesOpenIsDropped() throws Exception
    {
        // Another account's files are open, and this account's aren't yet.
        Harness harness = harness("before-files");
        when(harness.client.getAccountHash()).thenReturn(7L);

        invokeRecord(harness.plugin, detected("Dragon Slayer"));
        readForDetectors(harness);

        assertEquals(0, events(harness).size());
        assertNull(PluginTestSupport.get(harness.plugin, "detectors"));
    }

    @Test
    public void nothingIsRecordedOnALeaguesWorld() throws Exception
    {
        Harness harness = harness("leagues");
        when(harness.client.getWorldType()).thenReturn(EnumSet.of(WorldType.SEASONAL));

        invokeRecord(harness.plugin, detected("Dragon Slayer"));

        assertEquals(0, events(harness).size());
    }

    @Test
    public void nothingIsReadWithoutTheAccountsEventStore() throws Exception
    {
        Harness harness = harness("no-store");
        setField(harness.plugin, "detectedEvents", null);

        readForDetectors(harness);

        assertNull(PluginTestSupport.get(harness.plugin, "detectors"));
    }

    @Test
    public void aMemoryChangesOnlyWhileDetectionIsOn() throws Exception
    {
        Harness harness = harness("gate-memory");
        useMemories(harness);
        readForDetectors(harness);
        Path tiers = harness.dataDirectory.resolve(FinishedMemory.DIARY_TIERS);
        assertEquals(Set.of(), FinishedMemory.open(harness.gson, tiers).finished());

        // Another character, on this session's detectors.
        Player main = mock(Player.class);
        when(main.getName()).thenReturn("Zezima");
        when(harness.client.getLocalPlayer()).thenReturn(main);
        harness.plugin.onVarbitChanged(varbit(LUMBRIDGE_EASY, 1));

        assertEquals(Set.of(), FinishedMemory.open(harness.gson, tiers).finished());
        assertEquals(0, events(harness).size());
    }

    @Test
    public void nothingIsReadOrRememberedWhileDetectionIsOff() throws Exception
    {
        Harness harness = harness("leagues-reading");
        useMemories(harness);
        when(harness.client.getWorldType()).thenReturn(EnumSet.of(WorldType.SEASONAL));
        when(harness.client.getVarbitValue(LUMBRIDGE_EASY)).thenReturn(1);

        readForDetectors(harness);
        harness.plugin.onVarbitChanged(varbit(LUMBRIDGE_EASY + 1, 1));

        assertNull(PluginTestSupport.get(harness.plugin, "detectors"));
        assertFalse(Files.exists(harness.dataDirectory.resolve(FinishedMemory.DIARY_TIERS)));
        assertEquals(0, events(harness).size());
    }

    @Test
    public void anotherCharacterGetsNeitherRecordsNorReminders() throws Exception
    {
        Harness harness = harness("another-character");
        FateLockedConfig config = (FateLockedConfig) PluginTestSupport.get(harness.plugin, "config");
        when(config.rollNudges()).thenReturn(true);
        ChatMessageManager chat = mock(ChatMessageManager.class);
        setField(harness.plugin, "chatMessageManager", chat);
        Player main = mock(Player.class);
        when(main.getName()).thenReturn("Zezima");
        when(harness.client.getLocalPlayer()).thenReturn(main);
        useMemories(harness);
        readForDetectors(harness);

        harness.plugin.onVarbitChanged(varbit(LUMBRIDGE_EASY, 1));

        assertEquals(0, events(harness).size());
        verify(chat, never()).queue(any(QueuedMessage.class));

        // The rules' own character gets both.
        Player bound = mock(Player.class);
        when(bound.getName()).thenReturn("Nubles");
        when(harness.client.getLocalPlayer()).thenReturn(bound);
        readForDetectors(harness);
        harness.plugin.onVarbitChanged(varbit(LUMBRIDGE_EASY + 1, 1));
        assertEquals(1, events(harness).size());
        verify(chat).queue(any(QueuedMessage.class));
    }

    @Test
    public void aRunLinkedToNoOneRecordsForWhoeverIsLoggedInButNeverReminds() throws Exception
    {
        Harness harness = harness("unlinked");
        JsonObject unlinked = harness.gson.fromJson(fixture("bundles/v4-rules.json"), JsonObject.class);
        unlinked.getAsJsonObject("rules").add("account", JsonNull.INSTANCE);
        setField(harness.plugin, "active", new ActiveRules(
            FateLockedBundle.loadFromJson(harness.gson, unlinked.toString()), FateLockedPlugin.RulesSource.NONE));
        assertEquals(null, AccountBinding.boundAccount(harness.plugin.getBundle()));
        FateLockedConfig config = (FateLockedConfig) PluginTestSupport.get(harness.plugin, "config");
        when(config.rollNudges()).thenReturn(true);
        ChatMessageManager chat = mock(ChatMessageManager.class);
        setField(harness.plugin, "chatMessageManager", chat);
        Player anyone = mock(Player.class);
        when(anyone.getName()).thenReturn("Zezima");
        when(harness.client.getLocalPlayer()).thenReturn(anyone);
        useMemories(harness);
        readForDetectors(harness);

        harness.plugin.onVarbitChanged(varbit(LUMBRIDGE_EASY, 1));

        // Stage 4: recorded for the Roll inbox to copy, and the paste says whose it is.
        assertEquals(1, events(harness).size());
        assertEquals("Zezima", events(harness).get(0).getAccount());
        // An unbound profile gets no reminders (owner decision, 25 September).
        verify(chat, never()).queue(any(QueuedMessage.class));
    }

    @Test
    public void aFinishedDiaryTierIsRecordedUnderTheTrackersId() throws Exception
    {
        Harness harness = harness("diary");
        useMemories(harness);
        // The session's full reading: every tier unfinished.
        readForDetectors(harness);

        harness.plugin.onVarbitChanged(varbit(LUMBRIDGE_EASY, 1));

        assertEquals(1, events(harness).size());
        // A diary event names its tier in the evidence; the tracker picks the task.
        assertEquals("Lumbridge Easy", events(harness).get(0).getEvidence().get("tierId"));
        assertEquals(Set.of("Lumbridge Easy"),
            FinishedMemory.open(harness.gson, harness.dataDirectory.resolve(FinishedMemory.DIARY_TIERS)).finished());
    }

    @Test
    public void aTierFinishedWhileRuneLiteWasClosedCountsAtTheNextLogin() throws Exception
    {
        Harness harness = harness("diary-away");
        FinishedMemory.open(harness.gson, harness.dataDirectory.resolve(FinishedMemory.DIARY_TIERS))
            .remember(Collections.<String>emptyList());
        useMemories(harness);
        when(harness.client.getVarbitValue(LUMBRIDGE_EASY)).thenReturn(1);

        readForDetectors(harness);

        assertEquals(1, events(harness).size());
        assertEquals("Lumbridge Easy", events(harness).get(0).getEvidence().get("tierId"));
    }

    @Test
    public void tiersArrivingAtLoginAreNotNewCompletions() throws Exception
    {
        Harness harness = harness("diary-login");
        useMemories(harness);
        when(harness.client.getVarbitValue(LUMBRIDGE_EASY)).thenReturn(1);

        // The login's own varbits, before this session's full reading...
        harness.plugin.onVarbitChanged(varbit(LUMBRIDGE_EASY, 1));
        // ...and the reading itself, this account's first.
        readForDetectors(harness);
        harness.plugin.onVarbitChanged(varbit(LUMBRIDGE_EASY, 1));

        assertEquals(0, events(harness).size());
    }

    @Test
    public void theFullReadingComesWithTheSessionsFirstTick() throws Exception
    {
        Harness harness = harness("diary-tick");
        FinishedMemory.open(harness.gson, harness.dataDirectory.resolve(FinishedMemory.DIARY_TIERS))
            .remember(Collections.<String>emptyList());
        useMemories(harness);
        when(harness.client.getVarbitValue(LUMBRIDGE_EASY)).thenReturn(1);

        harness.plugin.onGameTick(new GameTick());

        assertEquals(1, events(harness).size());
    }

    @Test
    public void nullDetectionAndMissingAccountAddNothing() throws Exception
    {
        Harness harness = harness("gates");

        invokeRecord(harness.plugin, null);
        when(harness.client.getLocalPlayer()).thenReturn(null);
        invokeRecord(harness.plugin, detected("Dragon Slayer"));

        assertEquals(0, events(harness).size());
    }

    @Test
    public void relayClipboardAndFileImportsShareTheLocalHistoryPath()
        throws Exception
    {
        String rules = fixture("bundles/v4-rules.json");

        Harness relay = harness("relay-source");
        assertTrue(PluginTestSupport.importFromRelay(relay.plugin, rules));
        invokeRecord(relay.plugin, detected("Dragon Slayer"));
        assertEquals(1, events(relay).size());

        Harness clipboard = harness("clipboard-source");
        PluginTestSupport.importFromClipboard(clipboard.plugin, rules);
        invokeRecord(clipboard.plugin, detected("Dragon Slayer"));
        assertEquals(1, events(clipboard).size());

        Harness file = harness("file-source");
        Files.write(
            file.dataDirectory.resolve("fate-locked-bundle-test.json"),
            rules.getBytes(StandardCharsets.UTF_8));
        invokeNoArg(file.plugin, "loadNewestBackupFile");
        invokeRecord(file.plugin, detected("Dragon Slayer"));
        assertEquals(1, events(file).size());

        assertEquals(
            events(relay).get(0).getCanonicalLabel(),
            events(clipboard).get(0).getCanonicalLabel());
        assertEquals(
            events(relay).get(0).getCanonicalLabel(),
            events(file).get(0).getCanonicalLabel());
    }

    @Test
    public void failedWriteKeepsRulesCountsAndDurableHistory()
        throws Exception
    {
        Harness harness = harness("write-failure");
        invokeRecord(harness.plugin, detected("Dragon Slayer"));
        FateLockedBundle bundleBefore = harness.plugin.getBundle();
        // A directory where the write's lock file goes: the write fails, and
        // the file already there stays readable.
        Path temporary = harness.historyPath.resolveSibling(
            harness.historyPath.getFileName() + ".lock");
        Files.deleteIfExists(temporary);
        Files.createDirectory(temporary);

        invokeRecord(harness.plugin, detected("Cook's Assistant"));

        assertSame(bundleBefore, harness.plugin.getBundle());
        assertEquals(1, events(harness).size());
        assertEquals(1, new DetectedEventStore(harness.gson, harness.historyPath).entries().size());
        verify(harness.panel).showRollInbox(new RollInboxModel(1, 0, 0, true));

        Files.delete(temporary);
        invokeRecord(harness.plugin, detected("Demon Slayer"));
        assertEquals(2, events(harness).size());
        verify(harness.panel).showRollInbox(new RollInboxModel(2, 0, 0, false));
    }

    /** Lumbridge & Draynor Easy's varbit. */
    private static final int LUMBRIDGE_EASY = 4495;

    /** The account's quest and diary memories in the harness's folder, as they are there. */
    private static void useMemories(Harness harness) throws Exception
    {
        FinishedMemory quests = FinishedMemory.open(harness.gson, harness.dataDirectory.resolve(FinishedMemory.QUESTS));
        FinishedMemory tiers = FinishedMemory.open(harness.gson, harness.dataDirectory.resolve(FinishedMemory.DIARY_TIERS));
        setField(harness.plugin, "questMemory", quests);
        setField(harness.plugin, "tierMemory", tiers);
        setField(harness.plugin, "questsRemembered", quests.finished());
        setField(harness.plugin, "tiersRemembered", tiers.finished());
    }

    /** The v4 rules with the tracker's names for Vorkath and Cook's Assistant. */
    private static void useDetectionTables(Harness harness) throws Exception
    {
        JsonObject root = harness.gson.fromJson(fixture("bundles/v4-rules.json"), JsonObject.class);
        JsonObject rules = root.getAsJsonObject("rules");
        JsonArray capabilities = new JsonArray();
        capabilities.add("detection");
        rules.add("capabilities", capabilities);
        rules.add("detection", harness.gson.fromJson("{\"bosses\":[{\"key\":\"Vorkath\",\"raid\":false,"
            + "\"killCounts\":[\"Vorkath\"]}],\"quests\":[{\"id\":\"Cook's Assistant\",\"name\":\"Cook's Assistant\"},"
            + "{\"id\":\"Rune Mysteries\",\"name\":\"Rune Mysteries\"}],\"diaryTiers\":[]}", JsonObject.class));
        setField(harness.plugin, "active", new ActiveRules(
            FateLockedBundle.loadFromJson(harness.gson, root.toString()), FateLockedPlugin.RulesSource.NONE));
    }

    private static void readForDetectors(Harness harness) throws Exception
    {
        invokeNoArg(harness.plugin, "readForDetectors");
    }

    private static void readAtTick(Harness harness, int tick) throws Exception
    {
        when(harness.client.getTickCount()).thenReturn(tick);
        readForDetectors(harness);
    }

    /** A task, target 42, with five to go of this size, named by the game's task table. */
    private static void slayerTask(Client client, String name, int assigned)
    {
        when(client.getVarpValue(VarPlayerID.SLAYER_TARGET)).thenReturn(42);
        when(client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL)).thenReturn(assigned);
        when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(5);
        when(client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, 42)).thenReturn(List.of(9));
        when(client.getDBTableField(9, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)).thenReturn(new Object[]{name});
    }

    private static void popup(Harness harness, String title, String text)
    {
        when(harness.client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE)).thenReturn(title);
        when(harness.client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN)).thenReturn(text);
    }

    private static ChatMessage chat(ChatMessageType type, String message)
    {
        ChatMessage chat = new ChatMessage();
        chat.setType(type);
        chat.setMessage(message);
        return chat;
    }

    private static VarbitChanged varbit(int id, int value)
    {
        VarbitChanged event = new VarbitChanged();
        event.setVarbitId(id);
        event.setValue(value);
        return event;
    }

    private static VarbitChanged varp(int id, int value)
    {
        VarbitChanged event = new VarbitChanged();
        event.setVarbitId(-1);
        event.setVarpId(id);
        event.setValue(value);
        return event;
    }

    private Harness harness(String name) throws Exception
    {
        File dataDirectory = folder.newFolder(name);
        Gson gson = new Gson();
        TestPlugin plugin = new TestPlugin(dataDirectory);
        FateLockedPanel panel = mock(FateLockedPanel.class);
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Nubles");
        when(client.getLocalPlayer()).thenReturn(player);
        ConfigManager configManager = mock(ConfigManager.class);
        TrackerConnectionSettings connectionSettings =
            new TrackerConnectionSettings(configManager);
        Path historyPath = dataDirectory.toPath().resolve(DetectedEventStore.FILE);
        DetectedEventStore history = new DetectedEventStore(gson, historyPath);

        PluginTestSupport.runQueuedWorkInline(plugin);
        setField(plugin, "client", client);
        setField(plugin, "config", mock(FateLockedConfig.class));
        setField(plugin, "panel", panel);
        setField(plugin, "gson", gson);
        setField(plugin, "worldMapPointManager",
            mock(WorldMapPointManager.class));
        setField(plugin, "connectionSettings", connectionSettings);
        setField(plugin, "detectedEvents", history);
        setField(plugin, "active", new ActiveRules(
            FateLockedBundle.loadFromJson(gson, fixture("bundles/v4-rules.json")),
            FateLockedPlugin.RulesSource.NONE));

        return new Harness(
            plugin, panel, client, connectionSettings, history,
            gson, dataDirectory.toPath(), historyPath, plugin.finishedQuests);
    }

    /** The events the store holds, oldest first. */
    private static List<FateEvent> events(Harness harness)
    {
        return harness.history.entries().stream().map(DetectedEventStore.Entry::getEvent).collect(Collectors.toList());
    }

    private static List<String> labels(Harness harness)
    {
        return events(harness).stream().map(FateEvent::getCanonicalLabel).collect(Collectors.toList());
    }

    private static DetectedEvent detected(String label)
    {
        return DetectedEvent.builder()
            .type(FateEventType.QUEST)
            .canonicalLabel(label)
            .confidence(EventConfidence.EXACT)
            .detectorId("quest-widget-v1")
            .detectorVersion(1)
            .evidence(Collections.<String, Object>emptyMap())
            .build();
    }

    private static void invokeRecord(
        FateLockedPlugin plugin, DetectedEvent event) throws Exception
    {
        Method method = FateLockedPlugin.class.getDeclaredMethod(
            "record", DetectedEvent.class);
        method.setAccessible(true);
        method.invoke(plugin, event);
    }

    private static void invokeNoArg(
        FateLockedPlugin plugin, String methodName) throws Exception
    {
        Method method = FateLockedPlugin.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(plugin);
    }

    private static void setField(
        Object target, String name, Object value) throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static String fixture(String name) throws Exception
    {
        try (InputStream input =
            FateLockedPluginLocalHistoryTest.class.getClassLoader()
                .getResourceAsStream(name))
        {
            assertNotNull("missing fixture " + name, input);
            return new String(
                input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class Harness
    {
        private final FateLockedPlugin plugin;
        private final FateLockedPanel panel;
        private final Client client;
        private final TrackerConnectionSettings connectionSettings;
        private final DetectedEventStore history;
        private final Gson gson;
        private final Path dataDirectory;
        private final Path historyPath;
        /** The quests the game says are finished. */
        private final Set<Quest> finishedQuests;

        private Harness(
            FateLockedPlugin plugin,
            FateLockedPanel panel,
            Client client,
            TrackerConnectionSettings connectionSettings,
            DetectedEventStore history,
            Gson gson,
            Path dataDirectory,
            Path historyPath,
            Set<Quest> finishedQuests)
        {
            this.plugin = plugin;
            this.panel = panel;
            this.client = client;
            this.connectionSettings = connectionSettings;
            this.history = history;
            this.gson = gson;
            this.dataDirectory = dataDirectory;
            this.historyPath = historyPath;
            this.finishedQuests = finishedQuests;
        }
    }

    private static final class TestPlugin extends FateLockedPlugin
    {
        private final File dataDirectory;
        private final Set<Quest> finishedQuests = new HashSet<>();

        private TestPlugin(File dataDirectory)
        {
            this.dataDirectory = dataDirectory;
        }

        @Override
        File dataDirectory()
        {
            return dataDirectory;
        }

        /** The game's script needs a real client: the harness says which quests are finished. */
        @Override
        QuestState questState(Quest quest)
        {
            return finishedQuests.contains(quest) ? QuestState.FINISHED : QuestState.NOT_STARTED;
        }
    }
}
