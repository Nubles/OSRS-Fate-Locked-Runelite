package com.fatelocked;

import com.fatelocked.panel.ChunkPanelViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.fatelocked.PluginTestSupport.importFromClipboard;
import static com.fatelocked.PluginTestSupport.importFromRelay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FateLockedRelayImportTest
{
    private static final String PAIRING_CODE =
        "0123456789abcdef0123456789abcdef";

    @Test
    public void manualPairingCodeIsDetectedBeforeParsingAndEveryAttemptSaysSo()
        throws Exception
    {
        TestPlugin testPlugin = newPlugin();
        FateLockedBundle previous = testPlugin.plugin.getBundle();
        setSource(testPlugin.plugin, FateLockedPlugin.RulesSource.RELAY);

        importFromClipboard(testPlugin.plugin, PAIRING_CODE);
        importFromClipboard(testPlugin.plugin, PAIRING_CODE);

        assertSame(previous, testPlugin.plugin.getBundle());
        assertSame(FateLockedPlugin.RulesSource.RELAY, source(testPlugin.plugin));
        verify(testPlugin.panel, times(2)).flashStatus(
            "pairing code detected — use Connect tracker", false);
    }

    @Test
    public void repeatedMalformedImportKeepsPriorSnapshotAndEveryAttemptSaysSo()
        throws Exception
    {
        TestPlugin testPlugin = newPlugin();
        FateLockedBundle previous = testPlugin.plugin.getBundle();
        setSource(testPlugin.plugin, FateLockedPlugin.RulesSource.RELAY);

        importFromClipboard(testPlugin.plugin, "{bad");
        importFromClipboard(testPlugin.plugin, "{bad");

        assertSame(previous, testPlugin.plugin.getBundle());
        assertSame(FateLockedPlugin.RulesSource.RELAY, source(testPlugin.plugin));
        verify(testPlugin.panel, times(2)).flashStatus(
            "import failed — using previous rules", false);
    }

    @Test
    public void aFailedImportAfterASuccessIsNotHiddenUnderTheSuccessMessage()
        throws Exception
    {
        TestPlugin testPlugin = newPlugin();

        importFromClipboard(testPlugin.plugin, "{bad");
        importFromClipboard(testPlugin.plugin, fixture("bundles/v4-rules.json"));
        importFromClipboard(testPlugin.plugin, "{bad");

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(testPlugin.panel);
        order.verify(testPlugin.panel).flashStatus(
            "import failed — using previous rules", false);
        order.verify(testPlugin.panel).flashStatus(
            org.mockito.ArgumentMatchers.startsWith("imported "), eq(true));
        order.verify(testPlugin.panel).flashStatus(
            "import failed — using previous rules", false);
    }

    @Test
    public void relayImporterCommitsOnlyStrictV4Bundles() throws Exception
    {
        TestPlugin testPlugin = newPlugin();
        FateLockedBundle previous = testPlugin.plugin.getBundle();
        setSource(testPlugin.plugin, FateLockedPlugin.RulesSource.FILE);

        assertFalse(importFromRelay(
            testPlugin.plugin, fixture("bundles/v3-standard.json")));
        assertSame(previous, testPlugin.plugin.getBundle());
        assertSame(FateLockedPlugin.RulesSource.FILE, source(testPlugin.plugin));

        assertTrue(importFromRelay(
            testPlugin.plugin, fixture("bundles/v4-rules.json")));
        assertSame(FateLockedPlugin.RulesSource.RELAY, source(testPlugin.plugin));
        verify(testPlugin.panel, times(1))
            .update(any(FateLockedBundle.class), any());
    }

    @Test
    public void theRelayImporterSaysWhenANewerFormatNeedsAPluginUpdate() throws Exception
    {
        TrackerConnectionController.RelayBundleImporter<Object> importer =
            PluginTestSupport.relayImporter(newPlugin().plugin);
        JsonObject v5 = new Gson().fromJson(fixture("bundles/v4-rules.json"), JsonObject.class);
        v5.addProperty("version", 5);

        assertEquals(TrackerConnectionController.ImportVerdict.FUTURE_FORMAT,
            importer.prepare(v5.toString()).verdict);
        assertEquals(TrackerConnectionController.ImportVerdict.INVALID,
            importer.prepare("{bad").verdict);
        assertEquals(TrackerConnectionController.ImportVerdict.INVALID,
            importer.prepare(fixture("bundles/v3-standard.json")).verdict);
        assertEquals(TrackerConnectionController.ImportVerdict.OK,
            importer.prepare(fixture("bundles/v4-rules.json")).verdict);
    }

    @Test
    public void rulesThatCannotBeWorkedOutChangeNothing() throws Exception
    {
        // Working out what the new rules mean fails before anything changes.
        TestPlugin testPlugin = newPlugin(new FateLockedPlugin()
        {
            @Override
            ChunkPanelViewModel viewModelFor(FateLockedBundle source, CanonicalChunk chunk)
            {
                throw new IllegalStateException("view failed");
            }
        });
        when(testPlugin.config.worldMapMarkers()).thenReturn(true);
        FateLockedBundle previous = testPlugin.plugin.getBundle();
        setSource(testPlugin.plugin, FateLockedPlugin.RulesSource.FILE);

        assertFalse(importFromRelay(testPlugin.plugin, withALockedArea()));

        assertSame(previous, testPlugin.plugin.getBundle());
        assertSame(FateLockedPlugin.RulesSource.FILE, source(testPlugin.plugin));
        verify(testPlugin.panel, never()).update(any(FateLockedBundle.class), any());
        verify(testPlugin.pins, never()).add(any());
        verify(testPlugin.panel, never()).flashStatus(
            org.mockito.ArgumentMatchers.startsWith("synced "), eq(true));
    }

    @Test
    public void aSidebarThatFailsToShowNewRulesDoesNotUndoThem() throws Exception
    {
        TestPlugin testPlugin = newPlugin();
        when(testPlugin.config.worldMapMarkers()).thenReturn(true);
        setSource(testPlugin.plugin, FateLockedPlugin.RulesSource.FILE);
        doThrow(new IllegalStateException("panel failed"))
            .when(testPlugin.panel)
            .update(any(FateLockedBundle.class), any());

        assertTrue(importFromRelay(testPlugin.plugin, withALockedArea()));

        // The rules are in force, and the other changes still show.
        assertFalse(testPlugin.plugin.getBundle().getRegionChunks().isEmpty());
        assertSame(FateLockedPlugin.RulesSource.RELAY, source(testPlugin.plugin));
        verify(testPlugin.pins).add(any(FateLockedPlugin.LockedAreaPoint.class));
        verify(testPlugin.panel).flashStatus(
            org.mockito.ArgumentMatchers.startsWith("synced "), eq(true));
    }

    /** The v4 rules plus one authored area they leave locked, which gets a map pin. */
    private static String withALockedArea() throws Exception
    {
        JsonObject root = new Gson().fromJson(
            fixture("bundles/v4-rules.json"), JsonObject.class);
        JsonArray chunks = new JsonArray();
        JsonObject chunk = new JsonObject();
        chunk.addProperty("cx", 48);
        chunk.addProperty("cy", 50);
        chunks.add(chunk);
        root.getAsJsonObject("subAreaChunks").add("Draynor Village", chunks);
        return root.toString();
    }

    private static TestPlugin newPlugin() throws Exception
    {
        return newPlugin(new FateLockedPlugin());
    }

    private static TestPlugin newPlugin(FateLockedPlugin plugin) throws Exception
    {
        FateLockedPanel panel = mock(FateLockedPanel.class);
        FateLockedConfig config = mock(FateLockedConfig.class);
        WorldMapPointManager pins = mock(WorldMapPointManager.class);
        setField(plugin, "client", mock(Client.class));
        setField(plugin, "config", config);
        setField(plugin, "panel", panel);
        setField(plugin, "gson", new Gson());
        setField(plugin, "worldMapPointManager", pins);
        PluginTestSupport.runQueuedWorkInline(plugin);
        return new TestPlugin(plugin, panel, config, pins);
    }

    private static void setSource(
        FateLockedPlugin plugin, FateLockedPlugin.RulesSource source) throws Exception
    {
        setField(plugin, "active", new ActiveRules(plugin.getBundle(), source));
    }

    private static FateLockedPlugin.RulesSource source(FateLockedPlugin plugin)
        throws Exception
    {
        return ((ActiveRules) field(plugin, "active")).getSource();
    }

    private static Object field(FateLockedPlugin target, String name) throws Exception
    {
        return PluginTestSupport.get(target, name);
    }

    private static void setField(
        FateLockedPlugin target, String name, Object value) throws Exception
    {
        PluginTestSupport.set(target, name, value);
    }

    private static String fixture(String name) throws Exception
    {
        try (InputStream input =
            FateLockedRelayImportTest.class.getClassLoader()
                .getResourceAsStream(name))
        {
            assertNotNull("missing fixture " + name, input);
            return new String(
                input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class TestPlugin
    {
        private final FateLockedPlugin plugin;
        private final FateLockedPanel panel;
        private final FateLockedConfig config;
        private final WorldMapPointManager pins;

        private TestPlugin(
            FateLockedPlugin plugin, FateLockedPanel panel,
            FateLockedConfig config, WorldMapPointManager pins)
        {
            this.plugin = plugin;
            this.panel = panel;
            this.config = config;
            this.pins = pins;
        }
    }
}
