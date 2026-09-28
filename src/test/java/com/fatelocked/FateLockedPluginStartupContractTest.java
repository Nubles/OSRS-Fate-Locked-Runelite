package com.fatelocked;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
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
            assertEquals("the status card asks to connect", "Not connected",
                harness.panel.sidebar().status().model().getTitle());
            assertEquals(com.fatelocked.sidebar.CardAction.CONNECT,
                harness.panel.sidebar().status().model().getPrimary());
            assertNotNull("Strict Mode has its section", harness.panel.sidebar().strictMode().model());
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
                harness.panel.act(com.fatelocked.sidebar.CardAction.CONNECT);
                harness.panel.act(com.fatelocked.sidebar.CardAction.PAUSE_STRICT_MODE);
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
    public void checkNowRunsTheTrackerTickAtOnce() throws Exception
    {
        Harness harness = new Harness(folder.newFolder("check-now"));
        try
        {
            TrackerConnectionController controller = mock(TrackerConnectionController.class);
            when(controller.checkNow()).thenReturn(true);
            harness.set("connectionController", controller);
            int queued = harness.backgroundTasks.size();

            SwingUtilities.invokeAndWait(() -> harness.panel.act(com.fatelocked.sidebar.CardAction.CHECK_NOW));

            verify(controller).checkNow();
            assertEquals(queued + 1, harness.backgroundTasks.size());
            harness.runBackgroundTasks();
            verify(controller).pollIfDue();
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    @Test
    public void onlyTheLoginScreenCountsAsLoggedOutForTheTracker() throws Exception
    {
        Harness harness = new Harness(folder.newFolder("login"));
        try
        {
            TrackerConnectionController controller = mock(TrackerConnectionController.class);
            harness.set("connectionController", controller);

            harness.plugin.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
            verify(controller).loggedIn(false);

            // A hop and its loading screen are still in game.
            harness.plugin.onGameStateChanged(gameState(GameState.HOPPING));
            harness.plugin.onGameStateChanged(gameState(GameState.LOADING));
            harness.plugin.onGameStateChanged(gameState(GameState.LOGGED_IN));
            verify(controller, times(1)).loggedIn(false);
            verify(controller).loggedIn(true);
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    /** A Strict Mode pause belongs to one start: turning the plugin off ends it. */
    @Test
    public void aStrictModePauseEndsWhenThePluginStops() throws Exception
    {
        Harness harness = new Harness(folder.newFolder("pause"));
        com.fatelocked.guardian.StrictModePause pause =
            (com.fatelocked.guardian.StrictModePause) PluginTestSupport.get(harness.plugin, "strictPause");
        pause.pauseFor(java.time.Duration.ofSeconds(60));
        org.junit.Assert.assertTrue(pause.isPaused());

        harness.plugin.shutDown();

        org.junit.Assert.assertFalse(pause.isPaused());
    }

    /** U10: stopping the plugin forgets the areas alerted and any fade, so a restart alerts afresh. */
    @Test
    public void theAreaAlertsAndTheFadeEndWhenThePluginStops() throws Exception
    {
        Harness harness = new Harness(folder.newFolder("alerts"));
        LockedAreaAlerts alerts = (LockedAreaAlerts) PluginTestSupport.get(harness.plugin, "areaAlerts");
        FateLockedConfig.LockedAreaAlert all = FateLockedConfig.LockedAreaAlert.CHAT_SOUND_FADE;
        alerts.enter(com.fatelocked.rules.PermissionStatus.ALLOWED, "Lumbridge", 0, all, false);
        alerts.enter(com.fatelocked.rules.PermissionStatus.LOCKED, "Falador", 1, all, false);
        Field fade = FateLockedPlugin.class.getDeclaredField("lockedFadeAt");
        fade.setAccessible(true);
        fade.setLong(harness.plugin, System.nanoTime());

        harness.plugin.shutDown();

        assertEquals(FateLockedPlugin.NO_FADE, harness.plugin.getLockedFadeAt());
        assertEquals(new LockedAreaAlerts.Alert(true, true, true),
            alerts.enter(com.fatelocked.rules.PermissionStatus.LOCKED, "Falador", 2, all, false));
    }

    /** E6: the HUD is gone at the login screen and when the plugin stops. */
    @Test
    public void theHudEndsAtTheLoginScreenAndWhenThePluginStops() throws Exception
    {
        Harness harness = new Harness(folder.newFolder("hud"));
        Field hud = FateLockedPlugin.class.getDeclaredField("hudModel");
        hud.setAccessible(true);
        HudModel shown = new HudModel(java.util.Collections.singletonList(
            new HudModel.Line("Here", "Lumbridge", null)), false);
        try
        {
            hud.set(harness.plugin, shown);
            harness.plugin.onGameStateChanged(gameState(GameState.LOADING));
            assertSame("a loading screen is still in game", shown, harness.plugin.hudModel());
            harness.plugin.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
            assertSame(HudModel.NONE, harness.plugin.hudModel());
            hud.set(harness.plugin, shown);
        }
        finally
        {
            harness.plugin.shutDown();
        }
        assertSame(HudModel.NONE, harness.plugin.hudModel());
    }

    private static GameStateChanged gameState(GameState state)
    {
        GameStateChanged event = new GameStateChanged();
        event.setGameState(state);
        return event;
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
            assertNotNull(harness.panel.sidebar().status().model());
            // The shared files are only read, when an account's own files
            // start from them: left exactly as they were.
            assertEquals("{\"name\":\"Abyssal demons\",",
                new String(java.nio.file.Files.readAllBytes(new File(dir, "slayer-assignment.json").toPath()),
                    java.nio.charset.StandardCharsets.UTF_8));
            File[] moved = dir.listFiles((parent, name) -> name.contains(".corrupt-"));
            assertEquals(0, moved == null ? 0 : moved.length);
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
                harness.panel.act(com.fatelocked.sidebar.CardAction.IMPORT_CLIPBOARD));

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
            assertTrue(harness.notice(), harness.notice().startsWith("Imported rules from the clipboard, exported at "));
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
                harness.panel.act(com.fatelocked.sidebar.CardAction.LOAD_BACKUP_FILE));

            // Neither the Swing thread nor the client thread reads the file.
            assertEquals(1, harness.backgroundTasks.size());
            assertTrue(harness.clientTasks.isEmpty());

            harness.runBackgroundTasks();
            assertEquals(1, harness.clientTasks.size());
            assertTrue(harness.plugin.getBundle().getRegionChunks().isEmpty());

            harness.runClientTasks();
            harness.flushEdt();

            assertFalse(harness.plugin.getBundle().getRegionChunks().isEmpty());
            assertTrue(harness.notice(), harness.notice().startsWith("Loaded the newest backup file, exported at "));
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
            first.panel.act(com.fatelocked.sidebar.CardAction.IMPORT_CLIPBOARD));
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
            assertTrue(second.notice(), second.notice().startsWith("Restored the rules saved at "));
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
            harness.panel.act(com.fatelocked.sidebar.CardAction.LOAD_BACKUP_FILE);
            harness.panel.act(com.fatelocked.sidebar.CardAction.CONNECT);
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
                harness.panel.act(com.fatelocked.sidebar.CardAction.IMPORT_CLIPBOARD));
            assertEquals(2, harness.backgroundTasks.size());

            harness.runBackgroundTasks();
            harness.flushEdt();

            // Text that doesn't parse never reaches the client thread, and
            // nothing asks to run again.
            assertTrue(harness.clientTasks.isEmpty());
            assertTrue(harness.backgroundTasks.isEmpty());
            assertTrue(harness.notice(), harness.notice().equals(Notices.IMPORT_FAILED));
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
    /**
     * Rules that rate the worn weapon T6 with weapons unlocked to T5 (B12
     * reads itemRules and unlocks), on a profile bound to no one, so they
     * apply at startup with nobody logged in.
     */
    private static String overTierWeaponBundle() throws Exception
    {
        JsonObject root = new Gson().fromJson(
            fixture("bundles/v4-rules.json"), JsonObject.class);
        JsonObject rules = root.getAsJsonObject("rules");
        rules.remove("account");
        JsonObject weapon = new JsonObject();
        weapon.addProperty("slot", "Weapon");
        weapon.addProperty("tier", 6);
        JsonObject itemRules = new JsonObject();
        itemRules.add(String.valueOf(Harness.WORN_WEAPON), weapon);
        rules.add("itemRules", itemRules);
        rules.getAsJsonObject("unlocks").getAsJsonObject("equipment").addProperty("Weapon", 5);
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
            SwingUtilities.invokeAndWait(() -> harness.panel.act(com.fatelocked.sidebar.CardAction.CONNECT));
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
    public void turningOnlineSyncBackOnKeepsTheSavedPairing() throws Exception
    {
        Harness harness = new Harness(folder.newFolder("sync-back-on"));
        try
        {
            String saved = "0123456789abcdef0123456789abcdef";
            harness.configuration.put(TrackerConnectionSettings.PAIRING_CODE_KEY, saved);
            SwingUtilities.invokeAndWait(() -> harness.panel.act(com.fatelocked.sidebar.CardAction.TURN_ON_SYNC));
            harness.runClientTasks();
            harness.flushEdt();

            assertEquals(1, harness.consentPrompts.get());
            assertTrue(harness.settings.networkAccessAllowed());
            assertEquals(saved, harness.settings.pairingCode());
            assertTrue(harness.plugin.browserUrls.isEmpty());
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    @Test
    public void rePairingAsksFirstAndKeepsTheSavedPairingUntilTheNewOneDelivers()
        throws Exception
    {
        Harness harness = new Harness(folder.newFolder("re-pair"));
        try
        {
            String saved = "0123456789abcdef0123456789abcdef";
            harness.configuration.put(TrackerConnectionSettings.PAIRING_CODE_KEY, saved);
            harness.settings.allowNetworkAccess();
            // Declined: nothing changes.
            harness.acceptRepair = false;
            SwingUtilities.invokeAndWait(() -> harness.panel.act(com.fatelocked.sidebar.CardAction.REPAIR));
            harness.runClientTasks();
            harness.flushEdt();
            assertEquals(1, harness.repairPrompts.get());
            assertTrue(harness.plugin.browserUrls.isEmpty());

            harness.acceptRepair = true;
            SwingUtilities.invokeAndWait(() -> harness.panel.act(com.fatelocked.sidebar.CardAction.REPAIR));
            harness.runClientTasks();
            harness.flushEdt();

            assertEquals(2, harness.repairPrompts.get());
            assertEquals(saved, harness.settings.pairingCode());
            assertEquals(1, harness.plugin.browserUrls.size());
            assertFalse(harness.plugin.browserUrls.peek().contains(saved));
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    @Test
    public void disconnectAsksFirstThenForgetsThePairingAndKeepsTheRules() throws Exception
    {
        Harness harness = new Harness(folder.newFolder("disconnect"));
        try
        {
            String saved = "0123456789abcdef0123456789abcdef";
            harness.configuration.put(TrackerConnectionSettings.PAIRING_CODE_KEY, saved);
            harness.settings.allowNetworkAccess();
            FateLockedBundle rules = harness.plugin.getBundle();

            harness.acceptDisconnect = false;
            SwingUtilities.invokeAndWait(() -> harness.panel.act(com.fatelocked.sidebar.CardAction.DISCONNECT));
            assertEquals(1, harness.disconnectPrompts.get());
            assertEquals(saved, harness.settings.pairingCode());

            harness.acceptDisconnect = true;
            SwingUtilities.invokeAndWait(() -> harness.panel.act(com.fatelocked.sidebar.CardAction.DISCONNECT));
            assertEquals(2, harness.disconnectPrompts.get());
            assertEquals("", harness.settings.pairingCode());
            assertTrue("online sync stays on for the next pairing", harness.settings.networkAccessAllowed());
            assertSame(rules, harness.plugin.getBundle());
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    @Test
    public void aFirstPairingCanBeCancelledOrItsPageOpenedAgain() throws Exception
    {
        Harness harness = new Harness(folder.newFolder("cancel-pairing"));
        try
        {
            SwingUtilities.invokeAndWait(() -> harness.panel.act(com.fatelocked.sidebar.CardAction.CONNECT));
            harness.runClientTasks();
            harness.flushEdt();
            String code = harness.settings.pairingCode();
            assertEquals(1, harness.plugin.browserUrls.size());

            SwingUtilities.invokeAndWait(() -> harness.panel.act(com.fatelocked.sidebar.CardAction.OPEN_PAGE_AGAIN));
            assertEquals(2, harness.plugin.browserUrls.size());
            harness.plugin.browserUrls.poll();
            assertEquals(PairingSupport.trackerPairingUrl(code), harness.plugin.browserUrls.poll());

            SwingUtilities.invokeAndWait(() -> harness.panel.act(com.fatelocked.sidebar.CardAction.CANCEL_PAIRING));
            assertEquals("", harness.settings.pairingCode());
            SwingUtilities.invokeAndWait(() -> harness.panel.act(com.fatelocked.sidebar.CardAction.OPEN_PAGE_AGAIN));
            assertTrue("no pairing, no page", harness.plugin.browserUrls.isEmpty());
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    @Test
    public void resumeEndsAPauseAndTheStrictModeSwitchSavesTheSetting() throws Exception
    {
        Harness harness = new Harness(folder.newFolder("strict-switch"));
        try
        {
            com.fatelocked.guardian.StrictModePause pause =
                (com.fatelocked.guardian.StrictModePause) PluginTestSupport.get(harness.plugin, "strictPause");
            SwingUtilities.invokeAndWait(() -> harness.panel.act(com.fatelocked.sidebar.CardAction.PAUSE_STRICT_MODE));
            harness.runClientTasks();
            assertTrue(pause.isPaused());
            SwingUtilities.invokeAndWait(() -> harness.panel.act(com.fatelocked.sidebar.CardAction.RESUME_STRICT_MODE));
            harness.runClientTasks();
            assertFalse(pause.isPaused());

            // The harness's config reads Strict Mode as off, so the switch shows it off.
            harness.configuration.remove("strictMode");
            harness.flushEdt();
            assertFalse(harness.panel.sidebar().strictMode().toggle().isSelected());
            SwingUtilities.invokeAndWait(() -> harness.panel.sidebar().strictMode().toggle().doClick());
            assertEquals("true", harness.configuration.get("strictMode"));
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    /**
     * D2: a player's settings from before Stage 3 are carried over before anything reads a
     * setting, and again when RuneLite switches to a profile that hasn't been carried over.
     */
    @Test
    public void oldSettingsAreCarriedOverFirstAndAgainOnAProfileSwitch() throws Exception
    {
        Map<String, String> old = new java.util.HashMap<>(SettingsMigration.OLD_DEFAULTS);
        old.put("warnOnLocked", "false");
        old.put("showHud", "false");
        Harness harness = new Harness(folder.newFolder("old-settings"), true, old);
        try
        {
            assertEquals("get " + SettingsMigration.VERSION_KEY, harness.settingsLog.peek());
            assertEquals("CHAT_FADE", harness.configuration.get("lockedAreaAlert"));
            assertEquals("OFF", harness.configuration.get("hudMode"));
            assertEquals("2", harness.configuration.get(SettingsMigration.VERSION_KEY));
            assertEquals("the old setting stays for one release", "false", harness.configuration.get("warnOnLocked"));

            // Another profile, not carried over yet, where the world map was switched off.
            harness.configuration.remove(SettingsMigration.VERSION_KEY);
            harness.configuration.put("drawWorldMap", "false");
            harness.plugin.onProfileChanged(new net.runelite.client.events.ProfileChanged());

            assertEquals("OFF", harness.configuration.get("worldMapMode"));
            assertEquals("2", harness.configuration.get(SettingsMigration.VERSION_KEY));
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    /** E1: the stored colours are drawn from the start, and a profile switch draws its own. */
    @Test
    public void theStoredColoursAreDrawnFromTheStartAndOnAProfileSwitch() throws Exception
    {
        Map<String, String> stored = new java.util.HashMap<>();
        stored.put("colourPreset", "COLOUR_BLIND_SAFE");
        Harness harness = new Harness(folder.newFolder("colours"), true, stored);
        try
        {
            com.fatelocked.ui.Palette safe = com.fatelocked.ui.Palette.of(
                com.fatelocked.ui.Palette.Preset.COLOUR_BLIND_SAFE, null, null, null);
            assertSame(safe, harness.plugin.palette());
            assertSame("the Strict Mode banner draws in it too", safe, bannerPalette(harness));

            harness.configuration.put("colourPreset", "DEFAULT");
            harness.plugin.onProfileChanged(new net.runelite.client.events.ProfileChanged());
            harness.runClientTasks();
            assertSame(com.fatelocked.ui.Palette.defaults(), harness.plugin.palette());
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    /** The palette the Strict Mode banner would draw in now. */
    @SuppressWarnings("unchecked")
    private static com.fatelocked.ui.Palette bannerPalette(Harness harness) throws Exception
    {
        Object banner = PluginTestSupport.get(harness.plugin, "travelBlockOverlay");
        Field field = FateLockedTravelBlockOverlay.class.getDeclaredField("palette");
        field.setAccessible(true);
        return ((java.util.function.Supplier<com.fatelocked.ui.Palette>) field.get(banner)).get();
    }

    /** Settings that can't be carried over keep their defaults, and the plugin carries on. */
    @Test
    public void settingsThatCantBeReadDoNotStopThePlugin() throws Exception
    {
        Harness harness = new Harness(folder.newFolder("unreadable-settings"));
        try
        {
            harness.configuration.remove(SettingsMigration.VERSION_KEY);
            harness.settingsUnreadable = true;
            harness.plugin.onProfileChanged(new net.runelite.client.events.ProfileChanged());
            harness.settingsUnreadable = false;

            assertEquals(null, harness.configuration.get(SettingsMigration.VERSION_KEY));
        }
        finally
        {
            harness.plugin.shutDown();
        }
    }

    @Test
    public void theSyncSwitchAsksForConsentAndGoesBackWhenDeclined() throws Exception
    {
        Harness harness = new Harness(folder.newFolder("sync-switch"));
        try
        {
            harness.runClientTasks();
            harness.flushEdt();
            javax.swing.JToggleButton sync = harness.panel.sidebar().connection().syncSwitch();

            harness.acceptConsent = false;
            SwingUtilities.invokeAndWait(sync::doClick);
            harness.runClientTasks();
            harness.flushEdt();
            assertEquals(1, harness.consentPrompts.get());
            assertFalse(harness.settings.networkAccessAllowed());
            assertFalse("the switch goes back", sync.isSelected());

            harness.acceptConsent = true;
            SwingUtilities.invokeAndWait(sync::doClick);
            harness.runClientTasks();
            assertEquals(2, harness.consentPrompts.get());
            assertTrue(harness.settings.networkAccessAllowed());

            harness.flushEdt();
            SwingUtilities.invokeAndWait(() -> {
                if (!sync.isSelected()) sync.setSelected(true);
                sync.doClick();
            });
            harness.runClientTasks();
            assertEquals("turning it off asks nothing", 2, harness.consentPrompts.get());
            assertFalse(harness.settings.networkAccessAllowed());
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
            SwingUtilities.invokeAndWait(() -> harness.panel.act(com.fatelocked.sidebar.CardAction.CONNECT));
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
        private final AtomicInteger repairPrompts = new AtomicInteger();
        private boolean acceptRepair = true;
        private final AtomicInteger disconnectPrompts = new AtomicInteger();
        private boolean acceptDisconnect = true;
        private final Map<String, String> configuration =
            new ConcurrentHashMap<>();
        private final TrackerConnectionSettings settings;
        private final FateLockedPanel panel;
        private final ScheduledExecutorService executor =
            mock(ScheduledExecutorService.class);
        private final TestPlugin plugin;
        private NavigationButton navigation;

        /** The sidebar's notice line, once the Swing thread has caught up; "" for none. */
        String notice() throws Exception
        {
            flushEdt();
            String shown = panel.sidebar().noticeText();
            return shown == null ? "" : shown;
        }
        /** The item the harness's player wears as a weapon. */
        static final int WORN_WEAPON = 4151;
        /** Whether a client tick is running, the only time RuneLite allows game reads. */
        private final AtomicBoolean inClientTick = new AtomicBoolean();
        private final AtomicInteger gameReads = new AtomicInteger();
        private final ConcurrentLinkedQueue<String> offThreadGameReads =
            new ConcurrentLinkedQueue<>();
        /** Every stored-setting read and write since startUp began, in order: "get key" or "set key". */
        private final ConcurrentLinkedQueue<String> settingsLog =
            new ConcurrentLinkedQueue<>();
        /** While set, reading a stored setting fails, as a damaged profile might. */
        private volatile boolean settingsUnreadable;

        /** A started plugin after its first client tick. */
        private Harness(File dataDirectory) throws Exception
        {
            this(dataDirectory, true);
        }

        private Harness(File dataDirectory, boolean firstTick) throws Exception
        {
            this(dataDirectory, firstTick, java.util.Collections.emptyMap());
        }

        /** A plugin started over these stored settings, as an earlier release left them. */
        private Harness(File dataDirectory, boolean firstTick, Map<String, String> stored) throws Exception
        {
            configuration.putAll(stored);
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
            // Defaults, but the colour preset is read from the stored settings.
            FateLockedConfig config = new FateLockedConfig()
            {
                @Override
                public ColourPreset colourPreset()
                {
                    String stored = configuration.get("colourPreset");
                    return stored == null ? ColourPreset.DEFAULT : ColourPreset.valueOf(stored);
                }
            };
            panel = new FateLockedPanel(com.fatelocked.ui.IconSource.NONE)
            {
                @Override
                boolean confirmNetworkConnection()
                {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    consentPrompts.incrementAndGet();
                    return acceptConsent;
                }

                @Override
                boolean confirmRepair()
                {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    repairPrompts.incrementAndGet();
                    return acceptRepair;
                }

                @Override
                boolean confirmDisconnect()
                {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    disconnectPrompts.incrementAndGet();
                    return acceptDisconnect;
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
            set("spriteManager", mock(SpriteManager.class));
            set("keyManager", mock(KeyManager.class));
            set("mouseManager", mock(MouseManager.class));
            set("okHttpClient", new OkHttpClient());
            set("configManager", configManager);
            set("connectionSettings", settings);

            settingsLog.clear();
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
                .thenAnswer(invocation -> {
                    settingsLog.add("get " + invocation.getArgument(1));
                    if (settingsUnreadable)
                    {
                        throw new IllegalStateException("unreadable settings");
                    }
                    return configuration.get(stored(invocation.getArgument(0), invocation.getArgument(1)));
                });
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
                settingsLog.add("set " + invocation.getArgument(1));
                configuration.put(stored(invocation.getArgument(0), invocation.getArgument(1)),
                    invocation.getArgument(2));
                return null;
            }).when(manager).setConfiguration(
                anyString(), anyString(), anyString());
            doAnswer(invocation -> {
                settingsLog.add("set " + invocation.getArgument(1));
                configuration.put(stored(invocation.getArgument(0), invocation.getArgument(1)),
                    String.valueOf((Object) invocation.getArgument(2)));
                return null;
            }).when(manager).setConfiguration(
                anyString(), anyString(), any(Object.class));
            doAnswer(invocation -> {
                configuration.remove(stored(invocation.getArgument(0), invocation.getArgument(1)));
                return null;
            }).when(manager).unsetConfiguration(anyString(), anyString());
            return manager;
        }

        /** The plugin's own settings are stored by key; another group's are kept apart. */
        private static String stored(String group, String key)
        {
            return FateLockedConfig.GROUP.equals(group) ? key : group + "." + key;
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
