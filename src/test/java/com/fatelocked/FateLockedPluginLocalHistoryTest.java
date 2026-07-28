package com.fatelocked;

import com.fatelocked.detectors.DetectedEvent;
import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventHistory;
import com.fatelocked.events.FateEventType;
import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
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
        assertTrue(invokeRelayImport(relay.plugin, rules));
        invokeRecord(relay.plugin, detected("Dragon Slayer"));
        assertEquals(1, relay.history.events().size());

        Harness clipboard = harness("clipboard-source");
        assertTrue(invokePastedImport(
            clipboard.plugin, rules, "CLIPBOARD"));
        invokeRecord(clipboard.plugin, detected("Dragon Slayer"));
        assertEquals(1, clipboard.history.events().size());

        Harness file = harness("file-source");
        Files.write(
            file.dataDirectory.resolve("fate-locked-bundle-test.json"),
            rules.getBytes(StandardCharsets.UTF_8));
        invokeNoArg(file.plugin, "reloadBundle");
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
        Path temporary = harness.historyPath.resolveSibling(
            harness.historyPath.getFileName() + ".tmp");
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

        setField(plugin, "client", client);
        setField(plugin, "config", mock(FateLockedConfig.class));
        setField(plugin, "panel", panel);
        setField(plugin, "gson", gson);
        setField(plugin, "worldMapPointManager",
            mock(WorldMapPointManager.class));
        setField(plugin, "connectionSettings", connectionSettings);
        setField(plugin, "eventHistory", history);
        setField(plugin, "bundle", FateLockedBundle.loadFromJson(
            gson, fixture("bundles/v4-rules.json")));

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

    private static boolean invokeRelayImport(
        FateLockedPlugin plugin, String value) throws Exception
    {
        Method method = FateLockedPlugin.class.getDeclaredMethod(
            "acceptRelayPayload", String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(plugin, value);
    }

    private static boolean invokePastedImport(
        FateLockedPlugin plugin, String value, String sourceName)
        throws Exception
    {
        Class<?> sourceClass = Class.forName(
            FateLockedPlugin.class.getName() + "$ImportSource");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object source = Enum.valueOf(
            (Class<? extends Enum>) sourceClass, sourceName);
        Method method = FateLockedPlugin.class.getDeclaredMethod(
            "applyPastedBundle", String.class, sourceClass);
        method.setAccessible(true);
        return (Boolean) method.invoke(plugin, value, source);
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
