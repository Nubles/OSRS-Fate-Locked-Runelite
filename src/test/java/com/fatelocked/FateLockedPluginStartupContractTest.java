package com.fatelocked;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
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
import net.runelite.client.util.HotkeyListener;
import okhttp3.OkHttpClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
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

            // Both clicks hand their work to the client thread.
            assertEquals("", harness.settings.pairingCode());
            assertEquals(1, harness.consentPrompts.get());
            assertFalse(harness.settings.networkAccessAllowed());
            assertEquals(0, harness.plugin.pauseCalls.get());
            assertEquals(2, harness.clientTasks.size());

            harness.runClientTasks();
            harness.flushEdt();

            assertEquals(1, harness.plugin.pauseCalls.get());
            String code = harness.settings.pairingCode();
            assertTrue(code.matches("[0-9a-f]{32}"));
            assertTrue(harness.settings.networkAccessAllowed());
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
    public void theTrackerTickKeepsRunningAfterAFailedCheck() throws Exception
    {
        Harness harness = new Harness(folder.newFolder("tick"));
        try
        {
            ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
            verify(harness.executor).scheduleWithFixedDelay(
                tick.capture(), eq(2L), eq(4L), eq(TimeUnit.SECONDS));
            TrackerConnectionController controller = mock(TrackerConnectionController.class);
            doThrow(new IllegalStateException("settings unreadable"))
                .doNothing()
                .when(controller).pollIfDue();
            harness.set("connectionController", controller);

            // A scheduled task that throws is never run again, so the tick
            // must not let the failure out.
            tick.getValue().run();
            tick.getValue().run();

            verify(controller, times(2)).pollIfDue();
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    @Test
    public void startupReadsTheGameOnlyOnTheClientThread() throws Exception
    {
        File dir = folder.newFolder("startup-on-client-thread");
        // A backup file whose gear tiers put the worn weapon above its
        // tier, so switching to it reads worn equipment and item names.
        write(new File(dir, "fate-locked-bundle-export.json"), overTierWeaponBundle());
        Harness harness = new Harness(dir, false);
        try
        {
            // startUp runs on the Swing thread: it queues the work and reads
            // nothing from the game.
            assertTrue(harness.plugin.getBundle().getRegionChunks().isEmpty());
            assertEquals(0, harness.gameReads.get());

            harness.runBackgroundTasks();
            harness.runClientTasks();
            harness.flushEdt();

            assertFalse(harness.plugin.getBundle().getRegionChunks().isEmpty());
            assertEquals("Weapon", harness.plugin.getOverTierSummary());
            assertTrue(harness.gameReads.get() > 0);
            assertTrue(harness.offThreadGameReads.toString(),
                harness.offThreadGameReads.isEmpty());
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    @Test
    public void damagedLocalStateFilesDoNotStopStartup() throws Exception
    {
        File dir = folder.newFolder("damaged-state");
        write(new File(dir, "slayer-assignment.json"), "{\"name\":\"Abyssal demons\",");
        write(new File(dir, "event-history.json"), "[");
        write(new File(dir, "strict-mode-events.json"), "{\"entries\":");

        Harness harness = new Harness(dir);
        try
        {
            assertEquals(1, harness.navigationAdds.get());
            assertNotNull(harness.panel.sectionForTest("Guardian"));
            assertNotNull(harness.panel.connectButtonForTest());
            File[] kept = dir.listFiles((parent, name) ->
                name.startsWith("slayer-assignment.json.corrupt-"));
            assertEquals(1, kept == null ? 0 : kept.length);
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    @Test
    public void clipboardRulesAreParsedOffTheGameThreadAndAppliedOnIt() throws Exception
    {
        Harness harness = new Harness(folder.newFolder("clipboard-on-client-thread"));
        try
        {
            harness.plugin.clipboard = fixture("bundles/v4-rules.json");
            SwingUtilities.invokeAndWait(() ->
                harness.panel.buttonForTest("Import from clipboard").doClick());

            // The Swing thread only hands the text over. A full bundle takes
            // a while to parse, so that happens in the background; the
            // switch reads game state such as worn equipment, which RuneLite
            // allows only on the client thread.
            assertEquals(1, harness.backgroundTasks.size());
            assertTrue(harness.clientTasks.isEmpty());

            harness.runBackgroundTasks();
            assertTrue(harness.plugin.getBundle().getRegionChunks().isEmpty());
            assertEquals(1, harness.clientTasks.size());

            harness.runClientTasks();
            harness.flushEdt();

            assertFalse(harness.plugin.getBundle().getRegionChunks().isEmpty());
            assertTrue(harness.panel.hasTextForTest("imported "));
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    @Test
    public void theNewestBackupFileIsReadOffTheGameThreadAndAppliedOnIt()
        throws Exception
    {
        File dir = folder.newFolder("backup-file");
        Harness harness = new Harness(dir);
        try
        {
            // Written after startup: nothing watches the folder.
            write(new File(dir, "fate-locked-bundle-export.json"),
                fixture("bundles/v4-rules.json"));
            harness.flushEdt();
            assertTrue(harness.plugin.getBundle().getRegionChunks().isEmpty());
            assertTrue(harness.backgroundTasks.isEmpty());

            SwingUtilities.invokeAndWait(() ->
                harness.panel.buttonForTest("Load newest backup file").doClick());

            // Neither the Swing thread nor the client thread reads the file.
            assertEquals(1, harness.backgroundTasks.size());
            assertTrue(harness.clientTasks.isEmpty());

            harness.runBackgroundTasks();
            assertEquals(1, harness.clientTasks.size());
            assertTrue(harness.plugin.getBundle().getRegionChunks().isEmpty());

            harness.runClientTasks();
            harness.flushEdt();

            assertFalse(harness.plugin.getBundle().getRegionChunks().isEmpty());
            assertTrue(harness.panel.hasTextForTest("loaded backup file: "));
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    @Test
    public void rulesSurviveARestartAndAnOfflineStart() throws Exception
    {
        File dir = folder.newFolder("restart");
        Harness first = new Harness(dir);
        first.plugin.clipboard = fixture("bundles/v4-rules.json");
        SwingUtilities.invokeAndWait(() ->
            first.panel.buttonForTest("Import from clipboard").doClick());
        first.runBackgroundTasks();
        first.runClientTasks();
        // The switch queued the save; it runs in the background too.
        first.runBackgroundTasks();
        first.plugin.shutDown();
        assertTrue(new File(dir, SavedRulesStore.FILE_NAME).exists());

        // Online sync is off, so nothing but the saved rules can bring them back.
        Harness second = new Harness(dir);
        try
        {
            assertFalse(second.settings.networkAccessAllowed());
            assertEquals("run-1", second.plugin.getBundle().getRunId());
            assertTrue(second.panel.hasTextForTest("saved rules from "));
        }
        finally
        {
            second.plugin.shutDown();
        }
    }

    @Test
    public void workQueuedBeforeShutdownDoesNothingAfterIt() throws Exception
    {
        File dir = folder.newFolder("queued-before-shutdown");
        Harness harness = new Harness(dir);
        write(new File(dir, "fate-locked-bundle-export.json"),
            fixture("bundles/v4-rules.json"));
        harness.plugin.clipboard = fixture("bundles/v4-rules.json");

        // The player presses the re-import hotkey, loads the backup file and
        // clicks Connect, then turns the plugin off before any of it runs.
        harness.pressReimportHotkey();
        SwingUtilities.invokeAndWait(() -> {
            harness.panel.buttonForTest("Load newest backup file").doClick();
            harness.panel.connectButtonForTest().doClick();
        });
        assertEquals(1, harness.clientTasks.size());
        assertEquals(2, harness.backgroundTasks.size());

        harness.plugin.shutDown();
        harness.runBackgroundTasks();
        harness.runClientTasks();
        harness.flushEdt();

        // No rules come back while the plugin is off, and Connect neither
        // records consent nor opens the browser.
        assertTrue(harness.plugin.getBundle().getRegionChunks().isEmpty());
        assertFalse(harness.settings.networkAccessAllowed());
        assertEquals("", harness.settings.pairingCode());
        assertTrue(harness.plugin.browserUrls.isEmpty());
    }

    @Test
    public void aFailedImportRunsOnceAndSaysSo() throws Exception
    {
        Harness harness = new Harness(folder.newFolder("failed-import-once"));
        try
        {
            harness.plugin.clipboard = "{}";
            harness.pressReimportHotkey();
            harness.plugin.clipboard = "not a bundle";
            SwingUtilities.invokeAndWait(() ->
                harness.panel.buttonForTest("Import from clipboard").doClick());
            assertEquals(2, harness.backgroundTasks.size());

            harness.runBackgroundTasks();
            harness.flushEdt();

            // Text that doesn't parse never reaches the client thread, and
            // nothing asks to run again.
            assertTrue(harness.clientTasks.isEmpty());
            assertTrue(harness.backgroundTasks.isEmpty());
            assertTrue(harness.panel.hasTextForTest("import failed"));
            assertTrue(harness.plugin.getBundle().getRegionChunks().isEmpty());
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    private static void write(File file, String text) throws Exception
    {
        java.nio.file.Files.write(file.toPath(),
            text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String fixture(String name) throws Exception
    {
        try (InputStream in = FateLockedPluginStartupContractTest.class
            .getClassLoader().getResourceAsStream(name))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The v4 rules, with the harness's worn weapon one tier above the unlocked Weapon tier. */
    private static String overTierWeaponBundle() throws Exception
    {
        JsonObject root = new Gson().fromJson(
            fixture("bundles/v4-rules.json"), JsonObject.class);
        JsonObject tiers = new JsonObject();
        tiers.addProperty(String.valueOf(Harness.WORN_WEAPON), 6);
        root.add("itemTiers", tiers);
        JsonObject equipment = new JsonObject();
        equipment.addProperty("Weapon", 5);
        root.getAsJsonObject("state").add("equipment", equipment);
        return root.toString();
    }

    @Test
    public void decliningConnectWarningKeepsPairingAndBrowserUntouched() throws Exception
    {
        Harness harness = new Harness(folder.newFolder("decline-consent"));
        try
        {
            harness.acceptConsent = false;
            String previousCode = "0123456789abcdef0123456789abcdef";
            harness.configuration.put(TrackerConnectionSettings.PAIRING_CODE_KEY, previousCode);
            SwingUtilities.invokeAndWait(() -> harness.panel.connectButtonForTest().doClick());
            harness.runClientTasks();
            harness.flushEdt();

            assertEquals(1, harness.consentPrompts.get());
            assertEquals(previousCode, harness.settings.pairingCode());
            assertFalse(harness.settings.networkAccessAllowed());
            assertTrue(harness.plugin.browserUrls.isEmpty());
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    @Test
    public void revocationBeforeQueuedReconnectDoesNotReenableSync() throws Exception
    {
        Harness harness = new Harness(folder.newFolder("revoke-before-reconnect"));
        try
        {
            harness.settings.allowNetworkAccess();
            SwingUtilities.invokeAndWait(() -> harness.panel.connectButtonForTest().doClick());
            harness.configuration.put(FateLockedConfig.NETWORK_ACCESS_KEY, "false");
            harness.runClientTasks();
            harness.flushEdt();

            assertEquals(0, harness.consentPrompts.get());
            assertFalse(harness.settings.networkAccessAllowed());
            assertEquals("", harness.settings.pairingCode());
            assertTrue(harness.plugin.browserUrls.isEmpty());
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    private static final class Harness
    {
        private final ConcurrentLinkedQueue<BooleanSupplier> clientTasks =
            new ConcurrentLinkedQueue<>();
        /** Work handed to RuneLite's shared executor, off the game thread. */
        private final ConcurrentLinkedQueue<Runnable> backgroundTasks =
            new ConcurrentLinkedQueue<>();
        private final AtomicInteger navigationAdds = new AtomicInteger();
        private final AtomicInteger consentPrompts = new AtomicInteger();
        private boolean acceptConsent = true;
        private final Map<String, String> configuration =
            new ConcurrentHashMap<>();
        private final TrackerConnectionSettings settings;
        private final FateLockedPanel panel;
        private final ScheduledExecutorService executor =
            mock(ScheduledExecutorService.class);
        private final TestPlugin plugin;
        private NavigationButton navigation;
        /** The item the harness's player wears as a weapon. */
        static final int WORN_WEAPON = 4151;
        /** Whether a client tick is running, the only time RuneLite allows game reads. */
        private final AtomicBoolean inClientTick = new AtomicBoolean();
        private final AtomicInteger gameReads = new AtomicInteger();
        private final ConcurrentLinkedQueue<String> offThreadGameReads =
            new ConcurrentLinkedQueue<>();

        /** A started plugin after its first client tick. */
        private Harness(File dataDirectory) throws Exception
        {
            this(dataDirectory, true);
        }

        private Harness(File dataDirectory, boolean firstTick) throws Exception
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
            FateLockedConfig config = new FateLockedConfig() { };
            panel = new FateLockedPanel(config, configManager)
            {
                @Override
                boolean confirmNetworkConnection()
                {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    consentPrompts.incrementAndGet();
                    return acceptConsent;
                }
            };
            plugin = new TestPlugin(dataDirectory);

            // Like RuneLite's ClientThread: a Runnable runs once, and a
            // BooleanSupplier that returns false runs again next tick.
            ClientThread clientThread = mock(ClientThread.class);
            doAnswer(invocation -> {
                Runnable task = invocation.getArgument(0);
                clientTasks.add(() -> {
                    task.run();
                    return true;
                });
                return null;
            }).when(clientThread).invoke(any(Runnable.class));
            doAnswer(invocation -> {
                clientTasks.add(invocation.getArgument(0));
                return null;
            }).when(clientThread).invoke(any(BooleanSupplier.class));
            doAnswer(invocation -> {
                Runnable task = invocation.getArgument(0);
                clientTasks.add(() -> {
                    task.run();
                    return true;
                });
                return null;
            }).when(clientThread).invokeLater(any(Runnable.class));

            // Game reads the injected client allows only on the client thread.
            Client client = mock(Client.class);
            ItemContainer worn = mock(ItemContainer.class);
            when(worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx()))
                .thenReturn(new Item(WORN_WEAPON, 1));
            when(client.getItemContainer(anyInt())).thenAnswer(invocation -> {
                noteGameRead("getItemContainer");
                return worn;
            });
            when(client.getLocalPlayer()).thenAnswer(invocation -> {
                noteGameRead("getLocalPlayer");
                return null;
            });
            ItemManager itemManager = mock(ItemManager.class);
            ItemComposition weapon = mock(ItemComposition.class);
            when(weapon.getName()).thenReturn("Abyssal whip");
            when(itemManager.getItemComposition(anyInt())).thenAnswer(invocation -> {
                noteGameRead("getItemComposition");
                return weapon;
            });

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
            doAnswer(invocation -> {
                backgroundTasks.add(invocation.getArgument(0));
                return null;
            }).when(executor).execute(any(Runnable.class));

            set("client", client);
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
            set("itemManager", itemManager);
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
            if (firstTick)
            {
                runBackgroundTasks();
                runClientTasks();
                flushEdt();
            }
        }

        private void noteGameRead(String read)
        {
            gameReads.incrementAndGet();
            if (!inClientTick.get())
            {
                offThreadGameReads.add(read + " on " + Thread.currentThread().getName());
            }
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

        /** One client tick: run each queued task once, keeping any that ask to run again. */
        private void runClientTick()
        {
            List<BooleanSupplier> due = new ArrayList<>();
            BooleanSupplier task;
            while ((task = clientTasks.poll()) != null)
            {
                due.add(task);
            }
            inClientTick.set(true);
            try
            {
                for (BooleanSupplier queued : due)
                {
                    if (!queued.getAsBoolean())
                    {
                        clientTasks.add(queued);
                    }
                }
            }
            finally
            {
                inClientTick.set(false);
            }
        }

        private void runClientTasks()
        {
            for (int tick = 0; !clientTasks.isEmpty(); tick++)
            {
                assertTrue("a client task keeps asking to run again", tick < 50);
                runClientTick();
            }
        }

        private void runBackgroundTasks()
        {
            Runnable task;
            while ((task = backgroundTasks.poll()) != null)
            {
                task.run();
            }
        }

        private void pressReimportHotkey() throws Exception
        {
            Field declared = FateLockedPlugin.class.getDeclaredField("reimportHotkey");
            declared.setAccessible(true);
            ((HotkeyListener) declared.get(plugin)).hotkeyPressed();
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
        private String clipboard = "";

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
        }

        @Override
        String clipboardText()
        {
            return clipboard;
        }

        @Override
        void pauseStrictModeForSixtySeconds()
        {
            pauseCalls.incrementAndGet();
            super.pauseStrictModeForSixtySeconds();
        }
    }
}
