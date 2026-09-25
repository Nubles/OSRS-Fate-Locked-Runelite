package com.fatelocked;

import com.fatelocked.detectors.DetectedEvent;
import com.fatelocked.detectors.SlayerTaskDetector;
import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventHistory;
import com.fatelocked.events.FateEventType;
import com.fatelocked.guardian.StrictModeAuditEntry;
import com.fatelocked.guardian.StrictModeAuditLog;
import com.google.gson.Gson;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Local files are written by one background worker, never on the game
 * thread: the history, the Strict Mode audit log and the Slayer assignment
 * all wait for it.
 */
public class FateLockedPluginLocalWritesTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final List<Runnable> background = new ArrayList<>();
    private final FateLockedPanel panel = mock(FateLockedPanel.class);
    private FateLockedPlugin plugin;
    private Path dir;

    @Before
    public void setUp() throws Exception
    {
        dir = folder.newFolder("local-writes").toPath();
        plugin = new FateLockedPlugin();
        Gson gson = new Gson();
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Nubles");
        when(client.getLocalPlayer()).thenReturn(player);

        // The client thread runs at once; background work waits here.
        PluginTestSupport.runQueuedWorkInline(plugin);
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        doAnswer(invocation -> {
            background.add(invocation.getArgument(0));
            return null;
        }).when(executor).execute(any(Runnable.class));
        set("executor", executor);

        set("client", client);
        set("config", mock(FateLockedConfig.class));
        set("panel", panel);
        set("gson", gson);
        set("worldMapPointManager", mock(WorldMapPointManager.class));
        set("connectionSettings", new TrackerConnectionSettings(mock(ConfigManager.class)));
        set("active", new ActiveRules(
            FateLockedBundle.loadFromJson(gson, fixture("bundles/v4-rules.json")),
            FateLockedPlugin.RulesSource.NONE));
        set("eventHistory", new FateEventHistory(
            gson, history(), dir.resolve("event-outbox.json")));
        set("strictAuditLog", new StrictModeAuditLog(gson, audit()));
        set("slayerTaskDetector", new SlayerTaskDetector(gson, slayer()));
    }

    @Test
    public void aDetectedEventIsWrittenInTheBackground() throws Exception
    {
        invoke("record", DetectedEvent.class, DetectedEvent.builder()
            .type(FateEventType.QUEST)
            .canonicalLabel("Dragon Slayer")
            .confidence(EventConfidence.EXACT)
            .detectorId("quest-widget-v1")
            .detectorVersion(1)
            .evidence(Collections.<String, Object>emptyMap())
            .build());

        assertFalse(Files.exists(history()));
        verify(panel, never()).updateRollInboxStatus(
            anyInt(), anyInt(), anyInt(), anyBoolean());

        runBackground();

        assertTrue(Files.exists(history()));
        verify(panel).updateRollInboxStatus(1, 0, 0, false);
    }

    @Test
    public void aStrictModeAuditEntryIsWrittenInTheBackground() throws Exception
    {
        invoke("writeTravelAudit", StrictModeAuditEntry.class, new StrictModeAuditEntry(
            1_790_000_000_000L, "TRAVEL", "Teleport falador", "46,52",
            "Locked destination", "BLOCKED", false, false));

        assertFalse(Files.exists(audit()));
        verify(panel, never()).updateRecentPrevented(anyList());

        runBackground();

        assertTrue(Files.exists(audit()));
        verify(panel).updateRecentPrevented(anyList());
    }

    @Test
    public void aSlayerAssignmentIsWrittenInTheBackground()
    {
        ChatMessage message = new ChatMessage();
        message.setType(ChatMessageType.GAMEMESSAGE);
        message.setMessage("You're assigned to kill goblins; only 20 more to go.");

        plugin.onChatMessage(message);
        assertFalse(Files.exists(slayer()));

        runBackground();
        assertTrue(Files.exists(slayer()));
    }

    private void runBackground()
    {
        while (!background.isEmpty())
        {
            background.remove(0).run();
        }
    }

    private Path history()
    {
        return dir.resolve("event-history.json");
    }

    private Path audit()
    {
        return dir.resolve("strict-mode-events.json");
    }

    private Path slayer()
    {
        return dir.resolve("slayer-assignment.json");
    }

    private <T> void invoke(String name, Class<T> type, T argument) throws Exception
    {
        Method method = FateLockedPlugin.class.getDeclaredMethod(name, type);
        method.setAccessible(true);
        method.invoke(plugin, argument);
    }

    private void set(String name, Object value) throws Exception
    {
        PluginTestSupport.set(plugin, name, value);
    }

    private static String fixture(String name) throws Exception
    {
        try (InputStream in = FateLockedPluginLocalWritesTest.class
            .getClassLoader().getResourceAsStream(name))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
