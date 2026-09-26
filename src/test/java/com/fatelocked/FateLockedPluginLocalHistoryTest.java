package com.fatelocked;

import com.fatelocked.detectors.DetectedEvent;
import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventHistory;
import com.fatelocked.events.FateEventType;
import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
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

        assertEquals(1, harness.history.events().size());
        assertEquals("Dragon Slayer",
            harness.history.events().get(0).getCanonicalLabel());
        verify(harness.panel).updateRollInboxStatus(1, 0, 0, false);
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

        assertEquals(1, harness.history.events().size());
        assertEquals("Clue Scroll (Hard)", harness.history.events().get(0).getCanonicalLabel());
    }

    @Test
    public void theLoggedInAccountsOwnFilesAreOpenedAndUsed() throws Exception
    {
        Harness harness = harness("per-account");
        when(harness.client.getAccountHash()).thenReturn(7L);

        invokeNoArg(harness.plugin, "openAccountFiles");
        invokeRecord(harness.plugin, detected("Dragon Slayer"));

        assertTrue(Files.exists(harness.dataDirectory.resolve("accounts/7/event-history.json")));
        assertEquals(0, harness.history.events().size());
    }

    @Test
    public void aDetectionBeforeTheAccountsOwnFilesOpenIsDropped() throws Exception
    {
        // Another account's files are open, and this account's aren't yet.
        Harness harness = harness("before-files");
        when(harness.client.getAccountHash()).thenReturn(7L);

        invokeRecord(harness.plugin, detected("Dragon Slayer"));

        assertEquals(0, harness.history.events().size());
    }

    @Test
    public void nothingIsRecordedOnALeaguesWorld() throws Exception
    {
        Harness harness = harness("leagues");
        when(harness.client.getWorldType()).thenReturn(EnumSet.of(WorldType.SEASONAL));

        invokeRecord(harness.plugin, detected("Dragon Slayer"));

        assertEquals(0, harness.history.events().size());
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

        harness.plugin.onVarbitChanged(varbit(LUMBRIDGE_EASY, 0));
        harness.plugin.onVarbitChanged(varbit(LUMBRIDGE_EASY, 1));

        assertEquals(0, harness.history.events().size());
        verify(chat, never()).queue(any(QueuedMessage.class));

        // The rules' own character gets both.
        Player bound = mock(Player.class);
        when(bound.getName()).thenReturn("Nubles");
        when(harness.client.getLocalPlayer()).thenReturn(bound);
        harness.plugin.onVarbitChanged(varbit(LUMBRIDGE_EASY + 1, 1));
        assertEquals(1, harness.history.events().size());
        verify(chat).queue(any(QueuedMessage.class));
    }

    @Test
    public void aFinishedDiaryTierIsRecordedUnderTheTrackersId() throws Exception
    {
        Harness harness = harness("diary");

        // The first change sets the baseline: every tier unfinished.
        harness.plugin.onVarbitChanged(varbit(LUMBRIDGE_EASY, 0));
        harness.plugin.onVarbitChanged(varbit(LUMBRIDGE_EASY, 1));

        assertEquals(1, harness.history.events().size());
        // A diary event names its tier in the evidence; the tracker picks the task.
        assertEquals("Lumbridge Easy", harness.history.events().get(0).getEvidence().get("tierId"));
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

        assertEquals(0, harness.history.events().size());
    }

    @Test
    public void relayClipboardAndFileImportsShareTheLocalHistoryPath()
        throws Exception
    {
        String rules = fixture("bundles/v4-rules.json");

        Harness relay = harness("relay-source");
        assertTrue(PluginTestSupport.importFromRelay(relay.plugin, rules));
        invokeRecord(relay.plugin, detected("Dragon Slayer"));
        assertEquals(1, relay.history.events().size());

        Harness clipboard = harness("clipboard-source");
        PluginTestSupport.importFromClipboard(clipboard.plugin, rules);
        invokeRecord(clipboard.plugin, detected("Dragon Slayer"));
        assertEquals(1, clipboard.history.events().size());

        Harness file = harness("file-source");
        Files.write(
            file.dataDirectory.resolve("fate-locked-bundle-test.json"),
            rules.getBytes(StandardCharsets.UTF_8));
        invokeNoArg(file.plugin, "loadNewestBackupFile");
        invokeRecord(file.plugin, detected("Dragon Slayer"));
        assertEquals(1, file.history.events().size());

        assertEquals(
            relay.history.events().get(0).getCanonicalLabel(),
            clipboard.history.events().get(0).getCanonicalLabel());
        assertEquals(
            relay.history.events().get(0).getCanonicalLabel(),
            file.history.events().get(0).getCanonicalLabel());
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
        assertEquals(1, harness.history.events().size());
        assertEquals(1, new FateEventHistory(
            harness.gson, harness.historyPath, harness.legacyPath)
            .events().size());
        verify(harness.panel).updateRollInboxStatus(1, 0, 0, true);

        Files.delete(temporary);
        invokeRecord(harness.plugin, detected("Demon Slayer"));
        assertEquals(2, harness.history.events().size());
        verify(harness.panel).updateRollInboxStatus(2, 0, 0, false);
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
        Path historyPath = dataDirectory.toPath()
            .resolve("event-history.json");
        Path legacyPath = dataDirectory.toPath()
            .resolve("event-outbox.json");
        FateEventHistory history =
            new FateEventHistory(gson, historyPath, legacyPath);

        PluginTestSupport.runQueuedWorkInline(plugin);
        setField(plugin, "client", client);
        setField(plugin, "config", mock(FateLockedConfig.class));
        setField(plugin, "panel", panel);
        setField(plugin, "gson", gson);
        setField(plugin, "worldMapPointManager",
            mock(WorldMapPointManager.class));
        setField(plugin, "connectionSettings", connectionSettings);
        setField(plugin, "eventHistory", history);
        setField(plugin, "active", new ActiveRules(
            FateLockedBundle.loadFromJson(gson, fixture("bundles/v4-rules.json")),
            FateLockedPlugin.RulesSource.NONE));

        return new Harness(
            plugin, panel, client, connectionSettings, history,
            gson, dataDirectory.toPath(), historyPath, legacyPath);
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
        private final FateEventHistory history;
        private final Gson gson;
        private final Path dataDirectory;
        private final Path historyPath;
        private final Path legacyPath;

        private Harness(
            FateLockedPlugin plugin,
            FateLockedPanel panel,
            Client client,
            TrackerConnectionSettings connectionSettings,
            FateEventHistory history,
            Gson gson,
            Path dataDirectory,
            Path historyPath,
            Path legacyPath)
        {
            this.plugin = plugin;
            this.panel = panel;
            this.client = client;
            this.connectionSettings = connectionSettings;
            this.history = history;
            this.gson = gson;
            this.dataDirectory = dataDirectory;
            this.historyPath = historyPath;
            this.legacyPath = legacyPath;
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
