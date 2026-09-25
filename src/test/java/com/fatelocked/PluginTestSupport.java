package com.fatelocked;

import net.runelite.client.callback.ClientThread;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/** Helpers for tests that drive the plugin's imports without starting it. */
final class PluginTestSupport
{
    private PluginTestSupport()
    {
    }

    /**
     * Background and client-thread work runs at once, on the calling
     * thread. FateLockedPluginStartupContractTest checks which thread runs
     * what; these tests check what the work does.
     */
    static void runQueuedWorkInline(FateLockedPlugin plugin) throws Exception
    {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        ClientThread clientThread = mock(ClientThread.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(clientThread).invoke(any(Runnable.class));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(clientThread).invokeLater(any(Runnable.class));
        set(plugin, "executor", executor);
        set(plugin, "clientThread", clientThread);
        set(plugin, "gate", new ClientThreadGate(clientThread, new PluginSession()));
    }

    /** The clipboard import, as the hotkey and the sidebar button run it. */
    static void importFromClipboard(FateLockedPlugin plugin, String text) throws Exception
    {
        Method method = FateLockedPlugin.class.getDeclaredMethod(
            "importClipboardText", String.class);
        method.setAccessible(true);
        method.invoke(plugin, text);
    }

    /** Both steps of a relay import: parse, then switch; false if either rejects it. */
    static boolean importFromRelay(FateLockedPlugin plugin, String payload) throws Exception
    {
        return importFromRelay(plugin, payload, "1");
    }

    static boolean importFromRelay(FateLockedPlugin plugin, String payload, String version)
        throws Exception
    {
        Method parse = FateLockedPlugin.class.getDeclaredMethod(
            "parseRelayPayload", String.class);
        parse.setAccessible(true);
        Object parsed = parse.invoke(plugin, payload);
        if (parsed == null)
        {
            return false;
        }
        Method accept = FateLockedPlugin.class.getDeclaredMethod(
            "acceptRelayRules", FateLockedBundle.class, String.class, String.class);
        accept.setAccessible(true);
        return (Boolean) accept.invoke(plugin, parsed, payload, version);
    }

    static void set(FateLockedPlugin plugin, String name, Object value) throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    static Object get(FateLockedPlugin plugin, String name) throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(plugin);
    }
}
