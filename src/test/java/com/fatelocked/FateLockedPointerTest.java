package com.fatelocked;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fatelocked.sidebar.PointTarget;
import java.lang.reflect.Method;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

/**
 * The owner's review, 28 Sept: clicking a Here row puts the game's arrow on the nearest one,
 * says so, and takes it down on a second click, on arrival or on leaving the chunk; an arrow
 * the game or another plugin puts up is never taken down.
 */
public class FateLockedPointerTest
{
    private static final CanonicalChunk HERE = new CanonicalChunk(50, 50);
    private static final LocalPoint OAK = new LocalPoint(5 * 128, 2 * 128, -1);

    private final Client client = mock(Client.class);
    private final FateLockedPanel panel = mock(FateLockedPanel.class);
    private final ChunkLocator locator = mock(ChunkLocator.class);
    private final Player player = mock(Player.class);
    private final FateLockedPlugin plugin = new FateLockedPlugin();
    /** The arrow the game shows: an NPC, a point, or nothing. */
    private final Object[] arrow = new Object[1];
    private MockedStatic<SceneSearch> search;

    @Before
    public void setUp() throws Exception
    {
        PluginTestSupport.set(plugin, "client", client);
        PluginTestSupport.set(plugin, "panel", panel);
        PluginTestSupport.set(plugin, "chunkLocator", locator);
        when(locator.client()).thenReturn(client);
        when(locator.player()).thenReturn(HERE);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getLocalLocation()).thenReturn(new LocalPoint(20 * 128, 20 * 128, -1));
        doAnswer(ask -> arrow[0] = ask.getArgument(0)).when(client).setHintArrow(any(NPC.class));
        doAnswer(ask -> arrow[0] = ask.getArgument(0)).when(client).setHintArrow(any(LocalPoint.class));
        doAnswer(ask -> arrow[0] = null).when(client).clearHintArrow();
        when(client.hasHintArrow()).thenAnswer(ask -> arrow[0] != null);
        when(client.getHintArrowNpc()).thenAnswer(ask -> arrow[0] instanceof NPC ? arrow[0] : null);
        when(client.getHintArrowPoint()).thenAnswer(ask -> arrow[0] instanceof LocalPoint
            ? new WorldPoint(((LocalPoint) arrow[0]).getX(), ((LocalPoint) arrow[0]).getY(), 0) : null);
        search = mockStatic(SceneSearch.class);
    }

    @After
    public void tearDown()
    {
        search.close();
    }

    @Test
    public void aClickPointsAndASecondClickClears()
    {
        found("Oak tree", new SceneSearch.Found(null, OAK, true, 3 * 128));

        plugin.pointTo("SKILLING", "Oak tree");
        verify(client).setHintArrow(OAK);
        verify(panel).showHerePointer("The arrow points at the nearest Oak tree.", true);

        plugin.pointTo("SKILLING", "Oak tree");
        verify(client).clearHintArrow();
        verify(panel, times(2)).showHerePointer(null, false);
        verify(client, times(1).description("taken down, not put up again")).setHintArrow(OAK);
        org.junit.Assert.assertNull(arrow[0]);
    }

    @Test
    public void anotherRowMovesTheArrowAndAnNpcIsFollowed()
    {
        NPC spot = mock(NPC.class);
        found("Oak tree", new SceneSearch.Found(null, OAK, true, 3 * 128));
        found("Fishing spot (lure, bait)", new SceneSearch.Found(spot, new LocalPoint(0, 0, -1), true, 128));

        plugin.pointTo("SKILLING", "Oak tree");
        plugin.pointTo("SKILLING", "Fishing spot (lure, bait)");

        verify(client).clearHintArrow();
        verify(client).setHintArrow(spot);
        verify(panel).showHerePointer("The arrow points at the nearest Fishing spot (lure, bait).", true);
    }

    @Test
    public void onAnotherFloorItSaysSo()
    {
        found("Bank booth", new SceneSearch.Found(null, OAK, false, 128));
        plugin.pointTo("BANKS", "Bank booth");
        verify(panel).showHerePointer("The nearest Bank booth is on another floor. The arrow marks where.", true);
    }

    @Test
    public void noneNearSaysWhereItMayBe()
    {
        plugin.pointTo("SKILLING", "Fishing spot (frogspawn)");
        verify(client, never()).setHintArrow(any(LocalPoint.class));
        verify(panel).showHerePointer(
            "Can't find Fishing spot (frogspawn) near you here. It may be inside or underground.", false);

        plugin.pointTo("QUESTS", "Cook's Assistant");
        verify(panel).showHerePointer("Can't find Cook's Assistant near you here. It may be inside or underground.",
            false);
    }

    @Test
    public void reachingItTakesItDown() throws Exception
    {
        found("Oak tree", new SceneSearch.Found(null, OAK, true, 3 * 128));
        plugin.pointTo("SKILLING", "Oak tree");

        when(player.getLocalLocation()).thenReturn(new LocalPoint(5 * 128, 5 * 128, -1));
        keep(HERE);
        verify(client, never()).clearHintArrow();

        when(player.getLocalLocation()).thenReturn(new LocalPoint(5 * 128, 4 * 128, -1));
        keep(HERE);
        verify(client).clearHintArrow();
    }

    @Test
    public void leavingTheChunkTakesItDown() throws Exception
    {
        found("Oak tree", new SceneSearch.Found(null, OAK, true, 3 * 128));
        plugin.pointTo("SKILLING", "Oak tree");
        keep(new CanonicalChunk(51, 50));
        verify(client).clearHintArrow();
    }

    /** Someone else's arrow since, and Clear pressed before a tick: theirs stays up. */
    @Test
    public void clearingLeavesAnotherArrowUp()
    {
        found("Oak tree", new SceneSearch.Found(null, OAK, true, 3 * 128));
        plugin.pointTo("SKILLING", "Oak tree");
        arrow[0] = new LocalPoint(9 * 128, 9 * 128, -1);

        plugin.clearPointer();

        verify(client, never()).clearHintArrow();
    }

    /** Turning the plugin off takes its own arrow down, on the client thread. */
    @Test
    public void turningOffTakesItDown() throws Exception
    {
        net.runelite.client.callback.ClientThread thread = mock(net.runelite.client.callback.ClientThread.class);
        doAnswer(ask -> {
            ((Runnable) ask.getArgument(0)).run();
            return null;
        }).when(thread).invoke(any(Runnable.class));
        PluginTestSupport.set(plugin, "clientThread", thread);
        found("Oak tree", new SceneSearch.Found(null, OAK, true, 3 * 128));
        plugin.pointTo("SKILLING", "Oak tree");

        Method drop = FateLockedPlugin.class.getDeclaredMethod("dropPointer");
        drop.setAccessible(true);
        drop.invoke(plugin);

        verify(client).clearHintArrow();
    }

    /** Someone else's arrow since: the card forgets its own and leaves theirs up. */
    @Test
    public void anotherArrowIsLeftUp() throws Exception
    {
        found("Oak tree", new SceneSearch.Found(null, OAK, true, 3 * 128));
        plugin.pointTo("SKILLING", "Oak tree");
        arrow[0] = new LocalPoint(9 * 128, 9 * 128, -1);

        keep(HERE);
        plugin.clearPointer();

        verify(client, never()).clearHintArrow();
        // The click's own clearing first, then the tick that noticed, then the Clear.
        verify(panel, times(3)).showHerePointer(null, false);
    }

    private void found(String row, SceneSearch.Found found)
    {
        search.when(() -> SceneSearch.nearest(any(), any(), any(),
            org.mockito.ArgumentMatchers.argThat((PointTarget target) -> target != null
                && target.getLabel().equals(row)))).thenReturn(found);
    }

    private void keep(CanonicalChunk current) throws Exception
    {
        Method keep = FateLockedPlugin.class.getDeclaredMethod("keepPointer", CanonicalChunk.class);
        keep.setAccessible(true);
        keep.invoke(plugin, current);
    }
}
