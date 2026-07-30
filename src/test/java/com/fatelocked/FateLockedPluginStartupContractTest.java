package com.fatelocked;

import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import okhttp3.OkHttpClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FateLockedPluginStartupContractTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void realStartupOwnsOnePanelWithConnectAndGuardianCallbacks()
        throws Exception
    {
        Harness harness = new Harness(folder.newFolder("runtime"));
        try
        {
            assertEquals(1, harness.navigationAdds.get());
            assertSame(harness.panel, harness.navigation.getPanel());
            assertNotNull(harness.panel.connectButtonForTest());
            assertNotNull(harness.panel.sectionForTest("Guardian"));
            assertNotNull(harness.panel.guardianPauseButtonForTest());
            assertFalse(harness.configuration.containsKey("onlineSync"));
            assertFalse(harness.configuration.containsKey("syncCode"));
            assertFalse(harness.configuration.containsKey("relayUrl"));
            assertFalse(harness.configuration.keySet().stream()
                .anyMatch(key -> key.startsWith("eventToken.")
                    || key.startsWith("stateToken.")
                    || key.startsWith("suggestToken.")
                    || key.startsWith("ackToken.")));
            assertEquals("true", harness.configuration.get("strictMode"));

            verify(harness.executor, times(1)).scheduleWithFixedDelay(
                any(Runnable.class), eq(2L), eq(4L),
                eq(TimeUnit.SECONDS));
            verify(harness.executor, times(1)).scheduleWithFixedDelay(
                any(Runnable.class), anyLong(), anyLong(),
                eq(TimeUnit.SECONDS));

            SwingUtilities.invokeAndWait(() -> {
                harness.panel.connectButtonForTest().doClick();
                harness.panel.guardianPauseButtonForTest().doClick();
            });

            assertEquals("", harness.settings.pairingCode());
            assertEquals(1, harness.plugin.pauseCalls.get());
            assertEquals(1, harness.clientTasks.size());

            harness.runClientTasks();
            harness.flushEdt();

            String code = harness.settings.pairingCode();
            assertTrue(code.matches("[0-9a-f]{32}"));
            assertEquals(1, harness.plugin.browserUrls.size());
            assertEquals(PairingSupport.trackerPairingUrl(code),
                harness.plugin.browserUrls.peek());
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    @Test
    public void browserFailureKeepsRuntimePairingRetryableAndVisible()
        throws Exception
    {
        Harness harness = new Harness(folder.newFolder("browser-failure"));
        try
        {
            harness.plugin.failBrowser = true;
            SwingUtilities.invokeAndWait(
                () -> harness.panel.connectButtonForTest().doClick());
            harness.runClientTasks();
            harness.flushEdt();
            harness.runClientTasks();
            harness.flushEdt();

            String firstCode = harness.settings.pairingCode();
            assertTrue(harness.panel.hasTextForTest(
                "couldn't open the web tracker"));
            assertEquals("Could not open the web tracker",
                harness.panel.connectionTextForTest());

            SwingUtilities.invokeAndWait(
                () -> harness.panel.connectButtonForTest().doClick());
            harness.runClientTasks();
            harness.flushEdt();

            assertNotEquals(firstCode, harness.settings.pairingCode());
            assertEquals(2, harness.plugin.browserUrls.size());
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    private static final class Harness
    {
        private final ConcurrentLinkedQueue<Runnable> clientTasks =
            new ConcurrentLinkedQueue<>();
        private final AtomicInteger navigationAdds = new AtomicInteger();
        private final Map<String, String> configuration =
            new ConcurrentHashMap<>();
        private final TrackerConnectionSettings settings;
        private final FateLockedPanel panel;
        private final ScheduledExecutorService executor =
            mock(ScheduledExecutorService.class);
        private final TestPlugin plugin;
        private NavigationButton navigation;

        private Harness(File dataDirectory) throws Exception
        {
            String legacyCode = "0123456789abcdef0123456789abcdef";
            configuration.put("onlineSync", "true");
            configuration.put("syncCode", "OLD-CODE");
            configuration.put("relayUrl", "https://legacy.invalid");
            configuration.put("eventToken." + legacyCode, "event");
            configuration.put("stateToken." + legacyCode, "state");
            configuration.put("suggestToken." + legacyCode, "suggest");
            configuration.put("ackToken." + legacyCode, "ack");
            configuration.put("strictMode", "true");

            ConfigManager configManager = statefulConfigManager();
            settings = new TrackerConnectionSettings(configManager);
            FateLockedConfig config = new FateLockedConfig()
            {
                @Override
                public boolean autoReload()
                {
                    return false;
                }
            };
            panel = new FateLockedPanel(config, configManager);
            plugin = new TestPlugin(dataDirectory);

            ClientThread clientThread = mock(ClientThread.class);
            doAnswer(invocation -> {
                clientTasks.add(invocation.getArgument(0));
                return null;
            }).when(clientThread).invoke(any(Runnable.class));

            ClientToolbar toolbar = mock(ClientToolbar.class);
            doAnswer(invocation -> {
                navigation = invocation.getArgument(0);
                navigationAdds.incrementAndGet();
                return null;
            }).when(toolbar).addNavigation(any(NavigationButton.class));

            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            doReturn(future).when(executor).scheduleWithFixedDelay(
                any(Runnable.class), anyLong(), anyLong(),
                any(TimeUnit.class));

            set("client", mock(Client.class));
            set("clientThread", clientThread);
            set("config", config);
            set("overlayManager", mock(OverlayManager.class));
            set("worldMapOverlay", mock(FateLockedWorldMapOverlay.class));
            set("sceneOverlay", mock(FateLockedSceneOverlay.class));
            set("minimapOverlay", mock(FateLockedMinimapOverlay.class));
            set("hudOverlay", mock(FateLockedHudOverlay.class));
            set("contentOverlay", mock(FateLockedContentOverlay.class));
            set("flashOverlay", mock(FateLockedFlashOverlay.class));
            set("chatMessageManager", mock(ChatMessageManager.class));
            set("clientToolbar", toolbar);
            set("panel", panel);
            set("gson", new Gson());
            set("executor", executor);
            set("itemManager", mock(ItemManager.class));
            set("notifier", mock(Notifier.class));
            set("worldMapPointManager", mock(WorldMapPointManager.class));
            set("infoBoxManager", mock(InfoBoxManager.class));
            set("keyManager", mock(KeyManager.class));
            set("mouseManager", mock(MouseManager.class));
            set("okHttpClient", new OkHttpClient());
            set("configManager", configManager);
            set("connectionSettings", settings);

            plugin.startUp();
            flushEdt();
        }

        private ConfigManager statefulConfigManager()
        {
            ConfigManager manager = mock(ConfigManager.class);
            when(manager.getConfiguration(anyString(), anyString()))
                .thenAnswer(invocation ->
                    configuration.get(invocation.getArgument(1)));
            when(manager.getConfigurationKeys(anyString()))
                .thenAnswer(invocation -> {
                    String prefix = invocation.getArgument(0);
                    List<String> keys = new ArrayList<>();
                    for (String key : configuration.keySet())
                    {
                        keys.add(prefix + key);
                    }
                    return keys;
                });
            doAnswer(invocation -> {
                configuration.put(
                    invocation.getArgument(1), invocation.getArgument(2));
                return null;
            }).when(manager).setConfiguration(
                anyString(), anyString(), anyString());
            doAnswer(invocation -> {
                configuration.remove(invocation.getArgument(1));
                return null;
            }).when(manager).unsetConfiguration(anyString(), anyString());
            return manager;
        }

        private void set(String field, Object value) throws Exception
        {
            Field declared = FateLockedPlugin.class.getDeclaredField(field);
            declared.setAccessible(true);
            declared.set(plugin, value);
        }

        private void runClientTasks()
        {
            Runnable task;
            while ((task = clientTasks.poll()) != null)
            {
                task.run();
            }
        }

        private void flushEdt() throws Exception
        {
            SwingUtilities.invokeAndWait(() -> { });
        }
    }

    private static final class TestPlugin extends FateLockedPlugin
    {
        private final File dataDirectory;
        private final ConcurrentLinkedQueue<String> browserUrls =
            new ConcurrentLinkedQueue<>();
        private final AtomicInteger pauseCalls = new AtomicInteger();
        private boolean failBrowser;

        private TestPlugin(File dataDirectory)
        {
            this.dataDirectory = dataDirectory;
        }

        @Override
        File dataDirectory()
        {
            return dataDirectory;
        }

        @Override
        void launchTrackerBrowser(String url)
        {
            browserUrls.add(url);
            if (failBrowser)
            {
                throw new RuntimeException("browser unavailable");
            }
        }

        @Override
        void pauseStrictModeForSixtySeconds()
        {
            pauseCalls.incrementAndGet();
            super.pauseStrictModeForSixtySeconds();
        }
    }
}
