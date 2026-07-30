package com.fatelocked;

import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

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
    public void manualPairingCodeIsDetectedBeforeParsingAndRepeatedWarningIsSuppressed()
        throws Exception
    {
        TestPlugin testPlugin = newPlugin();
        FateLockedBundle previous = testPlugin.plugin.getBundle();
        Instant importedAt = Instant.parse("2026-07-27T12:34:56Z");
        setField(testPlugin.plugin, "rulesImportedAt", importedAt);

        assertFalse(applyPastedBundle(
            testPlugin.plugin, PAIRING_CODE, "PASTE"));
        assertFalse(applyPastedBundle(
            testPlugin.plugin, PAIRING_CODE, "CLIPBOARD"));

        assertSame(previous, testPlugin.plugin.getBundle());
        assertSame(importedAt, field(testPlugin.plugin, "rulesImportedAt"));
        verify(testPlugin.panel, times(1)).flashStatus(
            "pairing code detected — use Connect tracker", false);
    }

    @Test
    public void repeatedMalformedImportKeepsPriorSnapshotAndWarnsOnlyOnce()
        throws Exception
    {
        TestPlugin testPlugin = newPlugin();
        FateLockedBundle previous = testPlugin.plugin.getBundle();
        Instant importedAt = Instant.parse("2026-07-27T12:34:56Z");
        setField(testPlugin.plugin, "rulesImportedAt", importedAt);

        assertFalse(applyPastedBundle(testPlugin.plugin, "{bad", "PASTE"));
        assertFalse(applyPastedBundle(
            testPlugin.plugin, "{bad", "CLIPBOARD"));

        assertSame(previous, testPlugin.plugin.getBundle());
        assertSame(importedAt, field(testPlugin.plugin, "rulesImportedAt"));
        verify(testPlugin.panel, times(1)).flashStatus(
            "import failed — using previous rules", false);
    }

    @Test
    public void relayImporterCommitsOnlyStrictV4Bundles() throws Exception
    {
        TestPlugin testPlugin = newPlugin();
        FateLockedBundle previous = testPlugin.plugin.getBundle();
        Instant importedAt = Instant.parse("2026-07-27T12:34:56Z");
        setField(testPlugin.plugin, "rulesImportedAt", importedAt);

        assertFalse(acceptRelayPayload(
            testPlugin.plugin, fixture("bundles/v3-standard.json")));
        assertSame(previous, testPlugin.plugin.getBundle());
        assertSame(importedAt, field(testPlugin.plugin, "rulesImportedAt"));

        assertTrue(acceptRelayPayload(
            testPlugin.plugin, fixture("bundles/v4-rules.json")));
        assertNotNull(field(testPlugin.plugin, "rulesImportedAt"));
        verify(testPlugin.panel, times(1))
            .update(any(FateLockedBundle.class), any());
    }

    @Test
    public void relayImporterRollsBackWhenPanelRefreshFails() throws Exception
    {
        TestPlugin testPlugin = newPlugin();
        FateLockedBundle previous = testPlugin.plugin.getBundle();
        Instant importedAt = Instant.parse("2026-07-27T12:34:56Z");
        setField(testPlugin.plugin, "rulesImportedAt", importedAt);
        doThrow(new IllegalStateException("panel failed"))
            .when(testPlugin.panel)
            .update(any(FateLockedBundle.class), any());

        assertFalse(acceptRelayPayload(
            testPlugin.plugin, fixture("bundles/v4-rules.json")));

        assertSame(previous, testPlugin.plugin.getBundle());
        assertSame(importedAt, field(testPlugin.plugin, "rulesImportedAt"));
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

    private static boolean applyPastedBundle(
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
