package com.fatelocked;

import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

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
        setField(testPlugin.plugin, "rulesSource", FateLockedPlugin.RulesSource.RELAY);

        assertFalse(applyClipboardBundle(testPlugin.plugin, PAIRING_CODE));
        assertFalse(applyClipboardBundle(testPlugin.plugin, PAIRING_CODE));

        assertSame(previous, testPlugin.plugin.getBundle());
        assertSame(FateLockedPlugin.RulesSource.RELAY, field(testPlugin.plugin, "rulesSource"));
        verify(testPlugin.panel, times(2)).flashStatus(
            "pairing code detected — use Connect tracker", false);
    }

    @Test
    public void repeatedMalformedImportKeepsPriorSnapshotAndEveryAttemptSaysSo()
        throws Exception
    {
        TestPlugin testPlugin = newPlugin();
        FateLockedBundle previous = testPlugin.plugin.getBundle();
        setField(testPlugin.plugin, "rulesSource", FateLockedPlugin.RulesSource.RELAY);

        assertFalse(applyClipboardBundle(testPlugin.plugin, "{bad"));
        assertFalse(applyClipboardBundle(testPlugin.plugin, "{bad"));

        assertSame(previous, testPlugin.plugin.getBundle());
        assertSame(FateLockedPlugin.RulesSource.RELAY, field(testPlugin.plugin, "rulesSource"));
        verify(testPlugin.panel, times(2)).flashStatus(
            "import failed — using previous rules", false);
    }

    @Test
    public void aFailedImportAfterASuccessIsNotHiddenUnderTheSuccessMessage()
        throws Exception
    {
        TestPlugin testPlugin = newPlugin();

        assertFalse(applyClipboardBundle(testPlugin.plugin, "{bad"));
        assertTrue(applyClipboardBundle(testPlugin.plugin, fixture("bundles/v4-rules.json")));
        assertFalse(applyClipboardBundle(testPlugin.plugin, "{bad"));

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
        setField(testPlugin.plugin, "rulesSource", FateLockedPlugin.RulesSource.FILE);

        assertFalse(acceptRelayPayload(
            testPlugin.plugin, fixture("bundles/v3-standard.json")));
        assertSame(previous, testPlugin.plugin.getBundle());
        assertSame(FateLockedPlugin.RulesSource.FILE, field(testPlugin.plugin, "rulesSource"));

        assertTrue(acceptRelayPayload(
            testPlugin.plugin, fixture("bundles/v4-rules.json")));
        assertSame(FateLockedPlugin.RulesSource.RELAY, field(testPlugin.plugin, "rulesSource"));
        verify(testPlugin.panel, times(1))
            .update(any(FateLockedBundle.class), any());
    }

    @Test
    public void relayImporterRollsBackWhenPanelRefreshFails() throws Exception
    {
        TestPlugin testPlugin = newPlugin();
        FateLockedBundle previous = testPlugin.plugin.getBundle();
        setField(testPlugin.plugin, "rulesSource", FateLockedPlugin.RulesSource.FILE);
        doThrow(new IllegalStateException("panel failed"))
            .when(testPlugin.panel)
            .update(any(FateLockedBundle.class), any());

        assertFalse(acceptRelayPayload(
            testPlugin.plugin, fixture("bundles/v4-rules.json")));

        assertSame(previous, testPlugin.plugin.getBundle());
        assertSame(FateLockedPlugin.RulesSource.FILE, field(testPlugin.plugin, "rulesSource"));
        verify(testPlugin.panel, never()).flashStatus(
            org.mockito.ArgumentMatchers.startsWith("synced "), eq(true));
    }

    private static TestPlugin newPlugin() throws Exception
    {
        FateLockedPlugin plugin = new FateLockedPlugin();
        FateLockedPanel panel = mock(FateLockedPanel.class);
        setField(plugin, "client", mock(Client.class));
        setField(plugin, "config", mock(FateLockedConfig.class));
        setField(plugin, "panel", panel);
        setField(plugin, "gson", new Gson());
        setField(plugin, "worldMapPointManager",
            mock(WorldMapPointManager.class));
        return new TestPlugin(plugin, panel);
    }

    private static boolean applyClipboardBundle(
        FateLockedPlugin plugin, String value) throws Exception
    {
        Method method = FateLockedPlugin.class.getDeclaredMethod(
            "applyClipboardBundle", String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(plugin, value);
    }

    private static boolean acceptRelayPayload(
        FateLockedPlugin plugin, String value) throws Exception
    {
        Method method = FateLockedPlugin.class.getDeclaredMethod(
            "acceptRelayPayload", String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(plugin, value);
    }

    private static Object field(Object target, String name) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(
        Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
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

        private TestPlugin(
            FateLockedPlugin plugin, FateLockedPanel panel)
        {
            this.plugin = plugin;
            this.panel = panel;
        }
    }
}
