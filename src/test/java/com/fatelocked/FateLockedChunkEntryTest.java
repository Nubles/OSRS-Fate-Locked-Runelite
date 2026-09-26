package com.fatelocked;

import com.fatelocked.rules.PermissionStatus;
import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What crossing into a chunk tells the player: nothing before rules are
 * loaded or in chunks the tracker hasn't mapped, and one locked warning on
 * the way into locked territory, whatever the chat setting.
 */
public class FateLockedChunkEntryTest
{
    private static final String RULES = "{\"version\":3,"
        + "\"chunks\":{\"Misthalin\":[{\"cx\":50,\"cy\":50}],"
        + "\"Asgarnia\":[{\"cx\":46,\"cy\":52},{\"cx\":47,\"cy\":52}]},"
        + "\"unlockedRegions\":[\"Misthalin\"]}";
    private static final CanonicalChunk LUMBRIDGE = new CanonicalChunk(50, 50);
    private static final CanonicalChunk FALADOR = new CanonicalChunk(46, 52);
    private static final CanonicalChunk FALADOR_EAST = new CanonicalChunk(47, 52);
    private static final CanonicalChunk UNMAPPED = new CanonicalChunk(1, 1);
    private static final int LOCKED_SOUND = 2277;
    /** vanilla-mid, bound to Iron Example: Rimmington is the one LOCKED chunk on this walk. */
    private static final CanonicalChunk RIMMINGTON = new CanonicalChunk(46, 50);
    private static final CanonicalChunk[] LUMBRIDGE_TO_FALADOR = {
        new CanonicalChunk(50, 50), new CanonicalChunk(49, 50), new CanonicalChunk(48, 50),
        new CanonicalChunk(47, 50), RIMMINGTON, new CanonicalChunk(46, 51), new CanonicalChunk(46, 52),
    };
    /** vanilla-mid: South Taverley is LOCKED and East Catherby NOT_READY. */
    private static final CanonicalChunk SOUTH_TAVERLEY = new CanonicalChunk(45, 53);
    private static final CanonicalChunk EAST_CATHERBY = new CanonicalChunk(44, 53);
    /** vanilla-mid: in Zeah, LOCKED by the tracker and outside the old area lists. */
    private static final CanonicalChunk ZEAH = new CanonicalChunk(16, 44);

    private final Client client = mock(Client.class);
    private final FateLockedConfig config = mock(FateLockedConfig.class);
    private final FateLockedPanel panel = mock(FateLockedPanel.class);
    private final ChatMessageManager chat = mock(ChatMessageManager.class);
    private final Notifier notifier = mock(Notifier.class);
    private final Player player = mock(Player.class);
    private FateLockedPlugin plugin;

    @Before
    public void setUp() throws Exception
    {
        plugin = new FateLockedPlugin();
        set("client", client);
        set("config", config);
        set("panel", panel);
        set("chatMessageManager", chat);
        set("notifier", notifier);
        when(client.getLocalPlayer()).thenReturn(player);
        when(config.chatOnEnter()).thenReturn(true);
        when(config.warnOnLocked()).thenReturn(true);
        when(config.useNotifier()).thenReturn(true);
    }

    @Test
    public void nothingIsAnnouncedBeforeRulesAreLoaded() throws Exception
    {
        FateLockedBundle none = FateLockedBundle.empty();
        assertTrue(none.isEmpty());
        set("active", new ActiveRules(none, FateLockedPlugin.RulesSource.NONE));

        walk(LUMBRIDGE, FALADOR, UNMAPPED);

        verify(chat, never()).queue(any(QueuedMessage.class));
        verify(client, never()).playSoundEffect(anyInt());
        verify(notifier, never()).notify(anyString());
    }

    @Test
    public void unmappedChunksAreNeverAnnounced() throws Exception
    {
        loadRules();

        walk(UNMAPPED);
        verify(chat, never()).queue(any(QueuedMessage.class));

        walk(LUMBRIDGE);
        verify(chat, times(1)).queue(any(QueuedMessage.class));
    }

    @Test
    public void theLockedWarningSoundsOnceOnTheWayIn() throws Exception
    {
        loadRules();

        walk(LUMBRIDGE, FALADOR, FALADOR_EAST, FALADOR);
        verify(client, times(1)).playSoundEffect(LOCKED_SOUND);
        verify(notifier, times(1)).notify("Entered LOCKED chunk: Asgarnia");

        walk(LUMBRIDGE, FALADOR);
        verify(client, times(2)).playSoundEffect(LOCKED_SOUND);
    }

    @Test
    public void theLockedWarningDoesNotNeedChunkChat() throws Exception
    {
        loadRules();
        when(config.chatOnEnter()).thenReturn(false);

        walk(LUMBRIDGE, FALADOR);

        verify(chat, never()).queue(any(QueuedMessage.class));
        verify(client).playSoundEffect(LOCKED_SOUND);
        verify(notifier).notify("Entered LOCKED chunk: Asgarnia");
    }

    @Test
    public void turningTheWarningOffSilencesIt() throws Exception
    {
        loadRules();
        when(config.warnOnLocked()).thenReturn(false);

        walk(LUMBRIDGE, FALADOR);

        verify(client, never()).playSoundEffect(anyInt());
        verify(notifier, never()).notify(anyString());
    }

    @Test
    public void theWarningsCountFollowsThePlayer() throws Exception
    {
        loadRules();

        walk(LUMBRIDGE);
        verify(panel, times(1)).updateRollInboxStatus(0, 0, 0, false);
        walk(FALADOR);
        verify(panel, times(1)).updateRollInboxStatus(0, 0, 1, false);
        walk(FALADOR_EAST);
        verify(panel, times(1)).updateRollInboxStatus(0, 0, 1, false);
        walk(LUMBRIDGE);
        verify(panel, times(2)).updateRollInboxStatus(0, 0, 0, false);
    }

    /** B6: a golden walk warns once, on the way into Rimmington, and announces every step. */
    @Test
    public void aGoldenWalkFromLumbridgeToFaladorWarnsOnceAtRimmington() throws Exception
    {
        FateLockedBundle mid = playing("Iron Example");

        walk(LUMBRIDGE_TO_FALADOR);

        List<String> lines = chatLines();
        assertEquals(LUMBRIDGE_TO_FALADOR.length, lines.size());
        assertEquals(1, lines.stream().filter(line -> line.contains("⚠ LOCKED")).count());
        assertTrue(lines.get(4), lines.get(4).contains("(46, 50)") && lines.get(4).contains("⚠ LOCKED"));
        verify(client, times(1)).playSoundEffect(LOCKED_SOUND);
        verify(notifier).notify("Entered LOCKED chunk: " + mid.labelAt(RIMMINGTON));
    }

    /** NOT_READY is owned: it reads as unlocked, never alerts and isn't a warning. */
    @Test
    public void notReadyNeverAlerts() throws Exception
    {
        FateLockedBundle mid = playing("Iron Example");
        assertEquals(PermissionStatus.NOT_READY, mid.permissionsAt(EAST_CATHERBY).get().getEntry());
        assertEquals(PermissionStatus.LOCKED, mid.permissionsAt(SOUTH_TAVERLEY).get().getEntry());

        walk(FALADOR, SOUTH_TAVERLEY, EAST_CATHERBY);

        List<String> lines = chatLines();
        assertTrue(lines.get(2), lines.get(2).contains("(44, 53)") && lines.get(2).contains("✓ unlocked"));
        verify(client, times(1)).playSoundEffect(LOCKED_SOUND);
        verify(panel, times(1)).updateRollInboxStatus(0, 0, 1, false);
        verify(panel, times(2)).updateRollInboxStatus(0, 0, 0, false);
    }

    /** Another character's rules say nothing about this one: no chat, no alert, no warning. */
    @Test
    public void anotherCharacterWalksInSilence() throws Exception
    {
        playing("Someone Else");

        walk(LUMBRIDGE_TO_FALADOR);

        verify(chat, never()).queue(any(QueuedMessage.class));
        verify(client, never()).playSoundEffect(anyInt());
        verify(notifier, never()).notify(anyString());
        verify(panel, never()).updateRollInboxStatus(0, 0, 1, false);
    }

    /** A chunk the tracker locks outside the old area lists is announced without an area. */
    @Test
    public void aChunkOnlyTheTrackerMapsIsAnnounced() throws Exception
    {
        FateLockedBundle mid = playing("Iron Example");
        assertEquals(FateLockedBundle.LockState.UNAUTHORED, mid.lockStateAt(ZEAH));

        walk(LUMBRIDGE, ZEAH);

        List<String> lines = chatLines();
        assertTrue(lines.get(1), lines.get(1).contains("Chunk (16, 44)") && lines.get(1).contains("⚠ LOCKED"));
        assertFalse(lines.get(1), lines.get(1).contains("null"));
        verify(notifier).notify("Entered LOCKED chunk (16, 44)");
    }

    /**
     * B14: inside an instance the rules judge the chunk it is a copy of. From
     * Lumbridge Castle the player enters an instance copied from Seers'
     * Village (LOCKED in vanilla-mid): one alert, none while inside, and an
     * instance zone with no template changes nothing.
     */
    @Test
    public void anInstanceCopiedFromALockedChunkAlertsOnce() throws Exception
    {
        FateLockedBundle mid = playing("Iron Example");
        CanonicalChunk seers = new CanonicalChunk(42, 54);
        assertEquals(PermissionStatus.LOCKED, mid.permissionsAt(seers).get().getEntry());

        walk(new CanonicalChunk(50, 50));
        verify(client, never()).playSoundEffect(LOCKED_SOUND);

        enterInstanceOf(seers, 5, 5);
        enterInstanceOf(seers, 6, 7);
        enterInstanceOf(null, 6, 7);

        verify(client, times(1)).playSoundEffect(LOCKED_SOUND);
        verify(notifier).notify("Entered LOCKED chunk: " + mid.labelAt(seers));
        List<String> lines = chatLines();
        assertEquals(2, lines.size());
        assertTrue(lines.get(1), lines.get(1).contains("(42, 54)") && lines.get(1).contains("⚠ LOCKED"));
    }

    /**
     * Stand in an instance whose every zone is a copy of the source chunk's
     * south-west zone (null: no template anywhere), at scene zone x, y.
     */
    private void enterInstanceOf(CanonicalChunk source, int zoneX, int zoneY)
    {
        int[][][] templates = new int[4][13][13];
        for (int[][] plane : templates) for (int[] row : plane) Arrays.fill(row, -1);
        if (source != null)
        {
            // RuneLite's encoding: the source zone in 8-tile units, plane 0, no rotation.
            int template = ((source.getCx() << 3) << 14) | ((source.getCy() << 3) << 3);
            for (int[] row : templates[0]) Arrays.fill(row, template);
        }
        Scene scene = mock(Scene.class);
        when(scene.isInstance()).thenReturn(true);
        when(scene.getInstanceTemplateChunks()).thenReturn(templates);
        WorldView instance = mock(WorldView.class);
        when(instance.isTopLevel()).thenReturn(true);
        when(instance.getScene()).thenReturn(scene);
        when(player.getWorldView()).thenReturn(instance);
        when(player.getLocalLocation()).thenReturn(
            new LocalPoint((zoneX * 8 + 3) * 128 + 64, (zoneY * 8 + 3) * 128 + 64, WorldView.TOPLEVEL));
        plugin.onGameTick(null);
    }

    /** vanilla-mid, with a character logged in. */
    private FateLockedBundle playing(String name) throws Exception
    {
        FateLockedBundle mid = FateLockedBundle.loadFromJson(new Gson(),
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")));
        set("active", new ActiveRules(mid, FateLockedPlugin.RulesSource.RELAY));
        when(player.getName()).thenReturn(name);
        return mid;
    }

    private List<String> chatLines()
    {
        ArgumentCaptor<QueuedMessage> queued = ArgumentCaptor.forClass(QueuedMessage.class);
        verify(chat, atLeast(0)).queue(queued.capture());
        List<String> lines = new ArrayList<>();
        for (QueuedMessage message : queued.getAllValues()) lines.add(message.getRuneLiteFormattedMessage());
        return lines;
    }

    private void loadRules() throws Exception
    {
        FateLockedBundle rules = FateLockedBundle.loadFromJson(new Gson(), RULES);
        assertFalse(rules.isEmpty());
        assertEquals(FateLockedBundle.LockState.UNLOCKED, rules.lockStateAt(LUMBRIDGE));
        assertEquals(FateLockedBundle.LockState.LOCKED, rules.lockStateAt(FALADOR));
        assertEquals(FateLockedBundle.LockState.LOCKED, rules.lockStateAt(FALADOR_EAST));
        assertEquals(FateLockedBundle.LockState.UNAUTHORED, rules.lockStateAt(UNMAPPED));
        set("active", new ActiveRules(rules, FateLockedPlugin.RulesSource.NONE));
    }

    /** Stand in each chunk for one game tick. */
    private void walk(CanonicalChunk... chunks)
    {
        for (CanonicalChunk chunk : chunks)
        {
            TestWorld.standAt(client, player, new WorldPoint((chunk.getCx() << 6) + 8, (chunk.getCy() << 6) + 8, 0));
            // The tick event carries nothing the plugin reads.
            plugin.onGameTick(null);
        }
    }

    private void set(String name, Object value) throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
