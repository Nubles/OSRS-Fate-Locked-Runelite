package com.fatelocked;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.client.events.ConfigChanged;
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
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Where the active rules came from decides whether a reload may replace them,
 * whether the tracker's copy wins on its next check, and how fresh they are.
 */
public class FateLockedRulesSourceTest
{
    private static final String CODE = "0123456789abcdef0123456789abcdef";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void startingWithoutABackupFileKeepsTheActiveRules() throws Exception
    {
        Harness h = new Harness(folder.newFolder("no-file"));
        FateLockedBundle tracker = v4Bundle(Instant.now());
        h.setRules(tracker, FateLockedPlugin.RulesSource.RELAY);

        h.loadBackupFileAtStartup();

        assertSame(tracker, h.plugin.getBundle());
        assertEquals(FateLockedPlugin.RulesSource.RELAY, h.source());
        verify(h.controller, never()).localRulesReplacedTrackerRules();
        verify(h.panel, never()).flashStatus(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    public void loadingABackupFileWhenThereIsNoneSaysSoAndKeepsTheRules() throws Exception
    {
        Harness h = new Harness(folder.newFolder("button"));
        FateLockedBundle tracker = v4Bundle(Instant.now());
        h.setRules(tracker, FateLockedPlugin.RulesSource.RELAY);

        h.invoke("loadNewestBackupFile");

        assertSame(tracker, h.plugin.getBundle());
        assertEquals(FateLockedPlugin.RulesSource.RELAY, h.source());
        verify(h.controller, never()).localRulesReplacedTrackerRules();
        verify(h.panel).flashStatus(
            "no backup file in .runelite/fate-locked — rules unchanged", false);
    }

    @Test
    public void anUnreadableBackupFileKeepsTheRulesAndSaysSo() throws Exception
    {
        File dir = folder.newFolder("unreadable");
        Files.write(new File(dir, "fate-locked-bundle-bad.json").toPath(),
            "{\"rules\":".getBytes(StandardCharsets.UTF_8));
        Harness h = new Harness(dir);
        FateLockedBundle tracker = v4Bundle(Instant.now());
        h.setRules(tracker, FateLockedPlugin.RulesSource.RELAY);

        h.invoke("loadNewestBackupFile");

        assertSame(tracker, h.plugin.getBundle());
        assertEquals(FateLockedPlugin.RulesSource.RELAY, h.source());
        verify(h.controller, never()).localRulesReplacedTrackerRules();
        verify(h.panel).flashStatus(
            "couldn't read the backup file — rules unchanged", false);
    }

    @Test
    public void theRemovedAutoReloadSettingIsIgnored() throws Exception
    {
        File dir = folder.newFolder("toggle");
        Files.write(new File(dir, "fate-locked-bundle-old.json").toPath(),
            fixture("bundles/v3-standard.json").getBytes(StandardCharsets.UTF_8));
        Harness h = new Harness(dir);
        FateLockedBundle tracker = v4Bundle(Instant.now());
        h.setRules(tracker, FateLockedPlugin.RulesSource.RELAY);

        // A profile can still hold the old Auto-reload value.
        ConfigChanged toggled = new ConfigChanged();
        toggled.setGroup(FateLockedConfig.GROUP);
        toggled.setKey("autoReload");
        h.plugin.onConfigChanged(toggled);

        assertSame(tracker, h.plugin.getBundle());
        assertEquals(FateLockedPlugin.RulesSource.RELAY, h.source());
        verifyNoInteractions(h.executor);
    }

    @Test
    public void aBackupFileLoadsTheNewestFileAndHandsBackToTheTracker() throws Exception
    {
        File dir = folder.newFolder("file-import");
        File older = new File(dir, "fate-locked-bundle-older.json");
        Files.write(older.toPath(),
            fixture("bundles/v3-standard.json").getBytes(StandardCharsets.UTF_8));
        File newer = new File(dir, "fate-locked-bundle-newer.json");
        Files.write(newer.toPath(),
            fixture("bundles/v4-rules.json").getBytes(StandardCharsets.UTF_8));
        assertTrue(older.setLastModified(1_000_000_000_000L));
        assertTrue(newer.setLastModified(1_000_000_060_000L));
        Harness h = new Harness(dir);
        h.setRules(v4Bundle(Instant.now()), FateLockedPlugin.RulesSource.RELAY);

        h.invoke("loadNewestBackupFile");

        assertFalse(h.plugin.getBundle().isLegacyRules());
        assertEquals(FateLockedPlugin.RulesSource.FILE, h.source());
        verify(h.controller).localRulesReplacedTrackerRules();
        verify(h.panel).flashStatus(
            "loaded backup file: "
                + h.plugin.getBundle().getRegionChunks().size() + " regions",
            true);
    }

    @Test
    public void aClipboardImportHandsBackToTheTrackerOnItsNextCheck() throws Exception
    {
        Harness h = new Harness(folder.newFolder("clipboard"));

        PluginTestSupport.importFromClipboard(h.plugin, v4Json(Instant.now()));

        assertEquals(FateLockedPlugin.RulesSource.IMPORT, h.source());
        verify(h.controller).localRulesReplacedTrackerRules();
    }

    @Test
    public void trackerRulesAreSavedWithTheirVersionAndThePairingThatSentThem() throws Exception
    {
        File dir = folder.newFolder("save-relay");
        Harness h = new Harness(dir);
        // A re-pairing's new code sent them; the old one is still the saved pairing.
        String newCode = "fedcba9876543210fedcba9876543210";
        when(h.settings.pairingCode()).thenReturn(CODE);
        when(h.controller.activeCode()).thenReturn(newCode);

        assertTrue(PluginTestSupport.importFromRelay(h.plugin, v4Json(Instant.now()), "41"));

        SavedRules saved = h.savedRules();
        assertEquals(FateLockedPlugin.RulesSource.RELAY, saved.getSource());
        assertEquals("41", saved.getRelayVersion());
        assertEquals(PairingSupport.tag(newCode), saved.getPairingTag());
        assertEquals("run-1",
            FateLockedBundle.loadFromJson(new Gson(), saved.getPayload()).getRunId());
    }

    @Test
    public void importedRulesAreSavedWithoutAPairing() throws Exception
    {
        File dir = folder.newFolder("save-imports");
        Harness h = new Harness(dir);
        when(h.settings.pairingCode()).thenReturn(CODE);

        PluginTestSupport.importFromClipboard(h.plugin, v4Json(Instant.now()));
        SavedRules clipboard = h.savedRules();
        assertEquals(FateLockedPlugin.RulesSource.IMPORT, clipboard.getSource());
        assertNull(clipboard.getRelayVersion());
        assertNull(clipboard.getPairingTag());

        Files.write(new File(dir, "fate-locked-bundle-2026.json").toPath(),
            fixture("bundles/v4-rules.json").getBytes(StandardCharsets.UTF_8));
        h.invoke("loadNewestBackupFile");
        assertEquals(FateLockedPlugin.RulesSource.FILE, h.savedRules().getSource());
    }

    @Test
    public void rulesThatAreRejectedAreNotSaved() throws Exception
    {
        File dir = folder.newFolder("save-rejected");
        Harness h = new Harness(dir);

        PluginTestSupport.importFromClipboard(h.plugin, "{bad");
        assertFalse(PluginTestSupport.importFromRelay(
            h.plugin, fixture("bundles/v3-standard.json")));

        assertFalse(new File(dir, SavedRulesStore.FILE_NAME).exists());
    }

    @Test
    public void savedRulesComeBackAtStartupInsteadOfTheBackupFile() throws Exception
    {
        File dir = folder.newFolder("restore");
        // An older backup file sits beside the saved rules; the saved rules win.
        Files.write(new File(dir, "fate-locked-bundle-old.json").toPath(),
            fixture("bundles/v3-standard.json").getBytes(StandardCharsets.UTF_8));
        Harness h = new Harness(dir);
        h.save(FateLockedPlugin.RulesSource.IMPORT, null, null);

        h.invoke("loadSavedRules");

        assertFalse(h.plugin.getBundle().isLegacyRules());
        assertEquals("run-1", h.plugin.getBundle().getRunId());
        assertEquals(FateLockedPlugin.RulesSource.IMPORT, h.source());
        verify(h.panel).flashStatus(
            org.mockito.ArgumentMatchers.startsWith("saved rules from "), eq(true));
        verify(h.controller, never()).seedAcceptedVersion(anyString());
    }

    @Test
    public void withNothingSavedTheNewestBackupFileLoadsAtStartup() throws Exception
    {
        File dir = folder.newFolder("first-start");
        Files.write(new File(dir, "fate-locked-bundle-2026.json").toPath(),
            fixture("bundles/v4-rules.json").getBytes(StandardCharsets.UTF_8));
        Harness h = new Harness(dir);

        h.invoke("loadSavedRules");

        assertEquals(FateLockedPlugin.RulesSource.FILE, h.source());
        // From now on the next start finds these as saved rules.
        assertEquals(FateLockedPlugin.RulesSource.FILE, h.savedRules().getSource());
    }

    @Test
    public void savedRulesNeverReplaceRulesThatArrivedSince() throws Exception
    {
        Harness h = new Harness(folder.newFolder("arrived-since"));
        h.save(FateLockedPlugin.RulesSource.RELAY, "41", PairingSupport.tag(CODE));
        when(h.settings.pairingCode()).thenReturn(CODE);
        FateLockedBundle tracker = v4Bundle(Instant.now());
        h.setRules(tracker, FateLockedPlugin.RulesSource.RELAY);

        h.invoke("loadSavedRules");

        assertSame(tracker, h.plugin.getBundle());
        verify(h.controller, never()).seedAcceptedVersion(anyString());
    }

    @Test
    public void theStartupFileNeverReplacesRulesThatArrivedSince() throws Exception
    {
        File dir = folder.newFolder("startup-file-late");
        Files.write(new File(dir, "fate-locked-bundle-2026.json").toPath(),
            fixture("bundles/v4-rules.json").getBytes(StandardCharsets.UTF_8));
        Harness h = new Harness(dir);
        FateLockedBundle tracker = v4Bundle(Instant.now());
        h.setRules(tracker, FateLockedPlugin.RulesSource.RELAY);

        h.invoke("loadSavedRules");

        assertSame(tracker, h.plugin.getBundle());
        assertEquals(FateLockedPlugin.RulesSource.RELAY, h.source());
    }

    @Test
    public void savedTrackerRulesAskTheRelayOnlyWhetherTheyAreCurrent() throws Exception
    {
        Harness h = new Harness(folder.newFolder("same-pairing"));
        h.save(FateLockedPlugin.RulesSource.RELAY, "41", PairingSupport.tag(CODE));
        when(h.settings.pairingCode()).thenReturn(CODE);

        h.invoke("loadSavedRules");

        assertEquals(FateLockedPlugin.RulesSource.RELAY, h.source());
        verify(h.controller).seedAcceptedVersion("41");
        // Not fresh for Strict Mode until the relay confirms them.
        h.paired(true);
        assertFalse(h.plugin.rulesAreFresh());
    }

    @Test
    public void savedRulesFromAnotherPairingAreFetchedAfresh() throws Exception
    {
        Harness h = new Harness(folder.newFolder("other-pairing"));
        h.save(FateLockedPlugin.RulesSource.RELAY, "41",
            PairingSupport.tag("fedcba9876543210fedcba9876543210"));
        when(h.settings.pairingCode()).thenReturn(CODE);

        h.invoke("loadSavedRules");

        assertFalse(h.plugin.getBundle().getRegionChunks().isEmpty());
        verify(h.controller, never()).seedAcceptedVersion(anyString());
    }

    @Test
    public void nothingImportedIsNeverFresh() throws Exception
    {
        Harness h = new Harness(folder.newFolder("nothing"));
        assertFalse(h.plugin.rulesAreFresh());
    }

    @Test
    public void fileAndClipboardRulesCountFromTheirExportTime() throws Exception
    {
        Harness h = new Harness(folder.newFolder("export-time"));
        Instant now = Instant.now();
        for (FateLockedPlugin.RulesSource local : new FateLockedPlugin.RulesSource[] {
            FateLockedPlugin.RulesSource.FILE, FateLockedPlugin.RulesSource.IMPORT })
        {
            h.setRules(v4Bundle(now.minus(Duration.ofMinutes(2))), local);
            assertTrue(local + " exported 2 minutes ago", h.plugin.rulesAreFresh());

            // Loaded just now, but exported two weeks ago: never fresh.
            h.setRules(v4Bundle(now.minus(Duration.ofDays(14))), local);
            assertFalse(local + " exported two weeks ago", h.plugin.rulesAreFresh());

            h.setRules(v4Bundle(now.plus(Duration.ofHours(1))), local);
            assertFalse(local + " exported in the future", h.plugin.rulesAreFresh());

            h.setRules(fixtureBundle("bundles/v3-standard.json"), local);
            assertFalse(local + " without an export time", h.plugin.rulesAreFresh());
        }
    }

    @Test
    public void trackerRulesStayFreshWhileTheRelayConfirmsThem() throws Exception
    {
        Harness h = new Harness(folder.newFolder("relay"));
        Instant now = Instant.now();
        // Exported long ago, but confirmed by the relay a minute ago.
        h.setRules(v4Bundle(now.minus(Duration.ofDays(2))),
            FateLockedPlugin.RulesSource.RELAY);
        h.paired(true);

        h.lastSync(now.minus(Duration.ofMinutes(1)));
        assertTrue(h.plugin.rulesAreFresh());

        h.lastSync(now.minus(Duration.ofMinutes(20)));
        assertFalse(h.plugin.rulesAreFresh());

        // Online sync turned off: nothing confirms them, so fall back to the
        // export time, which is two days old.
        h.lastSync(now.minus(Duration.ofMinutes(1)));
        h.paired(false);
        assertFalse(h.plugin.rulesAreFresh());
    }

    private static FateLockedBundle v4Bundle(Instant exportedAt) throws Exception
    {
        return FateLockedBundle.loadFromJson(new Gson(), v4Json(exportedAt));
    }

    private static String v4Json(Instant exportedAt) throws Exception
    {
        JsonObject root = new Gson().fromJson(
            fixture("bundles/v4-rules.json"), JsonObject.class);
        root.getAsJsonObject("rules").addProperty("exportedAt", exportedAt.toString());
        return root.toString();
    }

    private static FateLockedBundle fixtureBundle(String name) throws Exception
    {
        return FateLockedBundle.loadFromJson(new Gson(), fixture(name));
    }

    private static String fixture(String name) throws Exception
    {
        try (InputStream in = FateLockedRulesSourceTest.class.getClassLoader()
            .getResourceAsStream(name))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class Harness
    {
        private final FateLockedPlugin plugin;
        private final FateLockedPanel panel = mock(FateLockedPanel.class);
        private final TrackerConnectionController controller =
            mock(TrackerConnectionController.class);
        private final TrackerConnectionSettings settings =
            mock(TrackerConnectionSettings.class);
        private final ScheduledExecutorService executor;
        private final Path savedRulesPath;

        private Harness(File dataDirectory) throws Exception
        {
            plugin = new FateLockedPlugin()
            {
                @Override
                File dataDirectory()
                {
                    return dataDirectory;
                }
            };
            PluginTestSupport.runQueuedWorkInline(plugin);
            executor = (ScheduledExecutorService) PluginTestSupport.get(plugin, "executor");
            savedRulesPath = dataDirectory.toPath().resolve(SavedRulesStore.FILE_NAME);
            set("savedRules", new SavedRulesStore(new Gson(), savedRulesPath));
            set("client", mock(Client.class));
            set("config", mock(FateLockedConfig.class));
            set("panel", panel);
            set("gson", new Gson());
            set("worldMapPointManager", mock(WorldMapPointManager.class));
            set("connectionController", controller);
            set("connectionSettings", settings);
            when(controller.snapshot()).thenReturn(TrackerConnectionSnapshot.disconnected());
        }

        /** Rules a previous start saved: the v4 fixture, exported just now. */
        void save(FateLockedPlugin.RulesSource source, String version, String pairingTag)
            throws Exception
        {
            new SavedRulesStore(new Gson(), savedRulesPath).save(new SavedRules(
                v4Json(Instant.now()), source, Instant.now(), version, pairingTag));
        }

        /** What the next start would find. */
        SavedRules savedRules()
        {
            SavedRules saved = new SavedRulesStore(new Gson(), savedRulesPath).load();
            assertNotNull("no saved rules", saved);
            return saved;
        }

        void setRules(FateLockedBundle bundle, FateLockedPlugin.RulesSource source)
            throws Exception
        {
            set("active", new ActiveRules(bundle, source));
        }

        FateLockedPlugin.RulesSource source() throws Exception
        {
            Field field = FateLockedPlugin.class.getDeclaredField("active");
            field.setAccessible(true);
            return ((ActiveRules) field.get(plugin)).getSource();
        }

        void paired(boolean paired)
        {
            when(settings.networkAccessAllowed()).thenReturn(paired);
            when(settings.isPaired()).thenReturn(paired);
        }

        void lastSync(Instant at)
        {
            when(controller.snapshot()).thenReturn(
                TrackerConnectionSnapshot.connected(at, "5"));
        }

        void invoke(String method) throws Exception
        {
            Method declared = FateLockedPlugin.class.getDeclaredMethod(method);
            declared.setAccessible(true);
            declared.invoke(plugin);
        }

        void loadBackupFileAtStartup() throws Exception
        {
            Method declared = FateLockedPlugin.class.getDeclaredMethod(
                "loadBackupFile", boolean.class);
            declared.setAccessible(true);
            declared.invoke(plugin, false);
        }

        private void set(String name, Object value) throws Exception
        {
            Field field = FateLockedPlugin.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(plugin, value);
        }
    }
}
