package com.fatelocked;

import com.fatelocked.sidebar.RollInboxModel;
import com.fatelocked.detectors.DetectedEvent;
import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.DetectedEventStore;
import com.fatelocked.events.FateEvent;
import com.fatelocked.events.FateEventType;
import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    public void aClueRewardIsRecordedButACasketInOtherLootIsNot() throws Exception
    {
        Harness harness = harness("clues");

        // Tempoross's reward pool can hold an item called "Casket".
        harness.plugin.onLootReceived(new LootReceived("Tempoross", 0, LootRecordType.EVENT,
            java.util.List.of(new ItemStack(CASKET, 1)), 1, null));
        harness.plugin.onLootReceived(new LootReceived("Clue Scroll (Hard)", 0, LootRecordType.EVENT,
            java.util.List.of(new ItemStack(COINS, 5000)), 1, null));

        assertEquals(1, events(harness).size());
        assertEquals("Clue Scroll (Hard)", events(harness).get(0).getCanonicalLabel());
    }

    @Test
    public void theLoggedInAccountsOwnFilesAreOpenedAndUsed() throws Exception
    {
        Harness harness = harness("per-account");
        when(harness.client.getAccountHash()).thenReturn(7L);

        invokeNoArg(harness.plugin, "openAccountFiles");
        invokeRecord(harness.plugin, detected("Dragon Slayer"));

        assertTrue(Files.exists(harness.dataDirectory.resolve("accounts/7/detected-events.json")));
        assertEquals(0, events(harness).size());
    }

    @Test
    public void aDetectionBeforeTheAccountsOwnFilesOpenIsDropped() throws Exception
    {
        // Another account's files are open, and this account's aren't yet.
        Harness harness = harness("before-files");
        when(harness.client.getAccountHash()).thenReturn(7L);

        invokeRecord(harness.plugin, detected("Dragon Slayer"));

        assertEquals(0, events(harness).size());
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
        useDiaryMemory(harness);
        readDiaryTiers(harness);

        harness.plugin.onVarbitChanged(varbit(LUMBRIDGE_EASY, 1));

        assertEquals(0, events(harness).size());
        verify(chat, never()).queue(any(QueuedMessage.class));

        // The rules' own character gets both.
        Player bound = mock(Player.class);
        when(bound.getName()).thenReturn("Nubles");
        when(harness.client.getLocalPlayer()).thenReturn(bound);
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
        useDiaryMemory(harness);
        readDiaryTiers(harness);

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
        useDiaryMemory(harness);
        // The session's full reading: every tier unfinished.
        readDiaryTiers(harness);

        harness.plugin.onVarbitChanged(varbit(LUMBRIDGE_EASY, 1));

        assertEquals(1, events(harness).size());
        // A diary event names its tier in the evidence; the tracker picks the task.
        assertEquals("Lumbridge Easy", events(harness).get(0).getEvidence().get("tierId"));
    }

    @Test
    public void aTierFinishedWhileRuneLiteWasClosedCountsAtTheNextLogin() throws Exception
    {
        Harness harness = harness("diary-away");
        new DiaryTierMemory(harness.gson, harness.dataDirectory.resolve(DiaryTierMemory.FILE))
            .reading(Collections.<String>emptyList());
        useDiaryMemory(harness);
        when(harness.client.getVarbitValue(LUMBRIDGE_EASY)).thenReturn(1);

        readDiaryTiers(harness);

        assertEquals(1, events(harness).size());
        assertEquals("Lumbridge Easy", events(harness).get(0).getEvidence().get("tierId"));
    }

    @Test
    public void tiersArrivingAtLoginAreNotNewCompletions() throws Exception
    {
        Harness harness = harness("diary-login");
        useDiaryMemory(harness);
        when(harness.client.getVarbitValue(LUMBRIDGE_EASY)).thenReturn(1);

        // The login's own varbits, before this session's full reading...
        harness.plugin.onVarbitChanged(varbit(LUMBRIDGE_EASY, 1));
        // ...and the reading itself, this account's first.
        readDiaryTiers(harness);
        harness.plugin.onVarbitChanged(varbit(LUMBRIDGE_EASY, 1));

        assertEquals(0, events(harness).size());
    }

    @Test
    public void theFullReadingComesWithTheSessionsFirstTick() throws Exception
    {
        Harness harness = harness("diary-tick");
        new DiaryTierMemory(harness.gson, harness.dataDirectory.resolve(DiaryTierMemory.FILE))
            .reading(Collections.<String>emptyList());
        useDiaryMemory(harness);
        when(harness.client.getVarbitValue(LUMBRIDGE_EASY)).thenReturn(1);

        harness.plugin.onGameTick(new GameTick());

        assertEquals(1, events(harness).size());
    }

    /** The account's diary memory in the harness's folder. */
    private static void useDiaryMemory(Harness harness) throws Exception
    {
        setField(harness.plugin, "diaryTiers", new DiaryTierMemory(harness.gson,
            harness.dataDirectory.resolve(DiaryTierMemory.FILE)));
    }

    private static void readDiaryTiers(Harness harness) throws Exception
    {
        invokeNoArg(harness.plugin, "readDiaryTiersIfDue");
    }

    /** Lumbridge & Draynor Easy's varbit. */
    private static final int LUMBRIDGE_EASY = 4495;

    private static VarbitChanged varbit(int id, int value)
    {
        VarbitChanged event = new VarbitChanged();
        event.setVarbitId(id);
        event.setValue(value);
        return event;
    }

    /** The item ids of a casket and of coins. */
    private static final int CASKET = 405;
    private static final int COINS = 995;

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

    private Harness harness(String name) throws Exception
    {
        File dataDirectory = folder.newFolder(name);
        Gson gson = new Gson();
        FateLockedPlugin plugin = new TestPlugin(dataDirectory);
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
            gson, dataDirectory.toPath(), historyPath);
    }

    /** The events the store holds, oldest first. */
    private static List<FateEvent> events(Harness harness)
    {
        return harness.history.entries().stream().map(DetectedEventStore.Entry::getEvent).collect(Collectors.toList());
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

        private Harness(
            FateLockedPlugin plugin,
            FateLockedPanel panel,
            Client client,
            TrackerConnectionSettings connectionSettings,
            DetectedEventStore history,
            Gson gson,
            Path dataDirectory,
            Path historyPath)
        {
            this.plugin = plugin;
            this.panel = panel;
            this.client = client;
            this.connectionSettings = connectionSettings;
            this.history = history;
            this.gson = gson;
            this.dataDirectory = dataDirectory;
            this.historyPath = historyPath;
        }
    }

    private static final class TestPlugin extends FateLockedPlugin
    {
        private final File dataDirectory;

        private TestPlugin(File dataDirectory)
        {
            this.dataDirectory = dataDirectory;
        }

        @Override
        File dataDirectory()
        {
            return dataDirectory;
        }
    }
}
