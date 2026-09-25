package com.fatelocked;

import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
            when(player.getWorldLocation()).thenReturn(
                new WorldPoint((chunk.getCx() << 6) + 8, (chunk.getCy() << 6) + 8, 0));
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
