package com.fatelocked;

import com.google.gson.Gson;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B11: what the Slayer task messages tell the plugin. Konar's place is kept,
 * the master comes from SLAYER_MASTER only for values RuneLite relies on
 * (7 Krystilia, 10 Mortimer), and the warning follows the decision service:
 * in vanilla-mid Krystilia's abyssal demons warn, anyone else's don't.
 */
public class SlayerChatTest
{
    private static final String GEM_CHECK = "You're assigned to kill bears; only 42 more to go.";
    private static final String KONAR_CHECK =
        "You're assigned to kill aberrant spectres in the Catacombs of Kourend; only 105 more to go.";

    private final FateLockedPlugin plugin = new FateLockedPlugin();
    private final FateLockedConfig config = mock(FateLockedConfig.class);
    private final Client client = mock(Client.class);
    private final Player player = mock(Player.class);
    private final ChatMessageManager chat = mock(ChatMessageManager.class);

    @Test
    public void theMessageGivesTheTaskAndKonarsPlace()
    {
        SlayerAssignment goblins = SlayerAssignment.fromChat("You're assigned to kill goblins; only 20 more to go.", 0);
        assertEquals(new SlayerAssignment("goblins", null, null), goblins);
        assertEquals(new SlayerAssignment("aberrant spectres", "Catacombs of Kourend", SlayerAssignment.KONAR),
            SlayerAssignment.fromChat(KONAR_CHECK, 0));
        assertEquals(new SlayerAssignment("aberrant spectres", "Slayer Tower", SlayerAssignment.KONAR),
            SlayerAssignment.fromChat("Your new task is to kill 105 aberrant spectres in the Slayer Tower.", 0));
        assertEquals("Kalphite Queen",
            SlayerAssignment.fromChat("You're assigned to kill the Kalphite Queen; only 3 more to go.", 0).getTask());
        assertNull(SlayerAssignment.fromChat("Welcome to Old School RuneScape.", 0));
        assertNull(SlayerAssignment.fromChat(null, 0));
    }

    @Test
    public void onlyVerifiedMasterValuesNameAMaster()
    {
        assertEquals("krystilia", SlayerAssignment.fromChat(GEM_CHECK, 7).getMaster());
        assertEquals("mortimer", SlayerAssignment.fromChat(GEM_CHECK, 10).getMaster());
        for (int unverified : new int[] { 0, 1, 3, 6, 8, 9, 11 })
        {
            assertNull(String.valueOf(unverified), SlayerAssignment.fromChat(GEM_CHECK, unverified).getMaster());
        }
    }

    @Before
    public void setUp() throws Exception
    {
        when(config.warnLockedSlayer()).thenReturn(true);
        when(player.getName()).thenReturn("Iron Example");
        when(client.getLocalPlayer()).thenReturn(player);
        set("config", config);
        set("client", client);
        set("chatMessageManager", chat);
        set("notifier", mock(Notifier.class));
        set("active", new ActiveRules(FateLockedBundle.loadFromJson(new Gson(),
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-interiors.bundle.json.gz"))),
            FateLockedPlugin.RulesSource.RELAY));
        refreshDecisions();
    }

    @Test
    public void krystiliasTaskWarnsAndTheSameTaskFromAnyoneElseDoesNot() throws Exception
    {
        assertEquals(List.of(), say(GEM_CHECK, 0));
        assertNull(plugin.getSlayerTaskWarn());

        // In vanilla-interiors Krystilia's bears are all in the locked Wilderness; anyone else's aren't (R16).
        List<String> lines = say(GEM_CHECK, 7);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0), lines.get(0).contains("bears") && lines.get(0).contains("is locked: Area locked."));
        assertEquals("bears", plugin.getSlayerTaskWarn());

        // Not ready (Slayer 85) never alerts.
        assertEquals(List.of(), say("You're assigned to kill abyssal demons; only 42 more to go.", 0));
        assertNull(plugin.getSlayerTaskWarn());
    }

    @Test
    public void konarsPlaceDecides() throws Exception
    {
        List<String> lines = say(KONAR_CHECK, 0);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0), lines.get(0).contains("is locked: Master: Mount Karuulm."));
        assertEquals("aberrant spectres", plugin.getSlayerTaskWarn());
    }

    @Test
    public void anotherCharacterGetsNoWarningAndForgetsTheLastOnesTask() throws Exception
    {
        say(GEM_CHECK, 7);
        assertEquals("bears", plugin.getSlayerTaskWarn());

        // A different account logs in: the last character's task goes with it.
        when(player.getName()).thenReturn("Someone Else");
        when(client.getAccountHash()).thenReturn(99L);
        GameStateChanged loggedIn = new GameStateChanged();
        loggedIn.setGameState(GameState.LOGGED_IN);
        plugin.onGameStateChanged(loggedIn);
        assertNull(plugin.getSlayerTaskWarn());

        refreshDecisions();
        assertEquals(List.of(), say(GEM_CHECK, 7));
        assertNull(plugin.getSlayerTaskWarn());
    }

    /** A game message with this SLAYER_MASTER value; the chat lines it queued. */
    private List<String> say(String message, int master)
    {
        clearInvocations(chat);
        when(client.getVarbitValue(VarbitID.SLAYER_MASTER)).thenReturn(master);
        ChatMessage event = new ChatMessage();
        event.setType(ChatMessageType.GAMEMESSAGE);
        event.setMessage(message);
        plugin.onChatMessage(event);
        ArgumentCaptor<QueuedMessage> queued = ArgumentCaptor.forClass(QueuedMessage.class);
        verify(chat, atLeast(0)).queue(queued.capture());
        List<String> lines = new ArrayList<>();
        for (QueuedMessage line : queued.getAllValues()) lines.add(line.getRuneLiteFormattedMessage());
        return lines;
    }

    private void refreshDecisions() throws Exception
    {
        Method refresh = FateLockedPlugin.class.getDeclaredMethod("refreshDecisions");
        refresh.setAccessible(true);
        refresh.invoke(plugin);
    }

    private void set(String name, Object value) throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
