package com.fatelocked;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fatelocked.sidebar.PointTarget;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

/**
 * The owner's review, 28 Sept: clicking a Here row puts the game's arrow on the nearest one,
 * says so, and takes it down on a second click, on arrival or on leaving the chunk; an arrow
 * the game or another plugin puts up is never taken down. With none loaded near the player,
 * it shows the way to the nearest one seen there: the arrow on that spot, a pin on the world
 * map, and Shortest Path's route when it runs.
 */
public class FateLockedPointerTest
{
    private static final CanonicalChunk HERE = new CanonicalChunk(50, 50);
    private static final LocalPoint OAK = new LocalPoint(5 * 128, 2 * 128, -1);
    /** Where the player stands in the world, and an oak seen before, five tiles off. */
    private static final WorldPoint STANDING = new WorldPoint(3205, 3205, 0);
    private static final WorldPoint SEEN_OAK = new WorldPoint(3210, 3200, 0);
    private static final String NOT_SEEN = " near you here. Once you've been near one, clicking it shows the way back.";
    private static final String REMEMBERED =
        "The minimap's arrow points the way to the nearest Oak tree you've seen here, and the world map has a pin.";

    private final Client client = mock(Client.class);
    private final FateLockedPanel panel = mock(FateLockedPanel.class);
    private final ChunkLocator locator = mock(ChunkLocator.class);
    private final Player player = mock(Player.class);
    private final ShortestPathHandOff shortestPath = mock(ShortestPathHandOff.class);
    private final WorldMapPointManager pins = mock(WorldMapPointManager.class);
    private final SpriteManager sprites = mock(SpriteManager.class);
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
        PluginTestSupport.set(plugin, "shortestPath", shortestPath);
        PluginTestSupport.set(plugin, "worldMapPointManager", pins);
        PluginTestSupport.set(plugin, "spriteManager", sprites);
        when(sprites.getSprite(SpriteID.MAPMARKER, 0)).thenReturn(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB));
        when(locator.playerWorld()).thenReturn(STANDING);
        when(locator.client()).thenReturn(client);
        when(locator.player()).thenReturn(HERE);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getLocalLocation()).thenReturn(new LocalPoint(20 * 128, 20 * 128, -1));
        doAnswer(ask -> arrow[0] = ask.getArgument(0)).when(client).setHintArrow(any(NPC.class));
        doAnswer(ask -> arrow[0] = ask.getArgument(0)).when(client).setHintArrow(any(LocalPoint.class));
        doAnswer(ask -> arrow[0] = ask.getArgument(0)).when(client).setHintArrow(any(WorldPoint.class));
        doAnswer(ask -> arrow[0] = null).when(client).clearHintArrow();
        when(client.hasHintArrow()).thenAnswer(ask -> arrow[0] != null);
        when(client.getHintArrowNpc()).thenAnswer(ask -> arrow[0] instanceof NPC ? arrow[0] : null);
        when(client.getHintArrowPoint()).thenAnswer(ask -> arrow[0] instanceof LocalPoint
            ? new WorldPoint(((LocalPoint) arrow[0]).getX(), ((LocalPoint) arrow[0]).getY(), 0)
            : arrow[0] instanceof WorldPoint ? arrow[0] : null);
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
    public void noneNearOrSeenSaysSo()
    {
        plugin.pointTo("SKILLING", "Fishing spot (frogspawn)");
        verify(client, never()).setHintArrow(any(LocalPoint.class));
        verify(client, never()).setHintArrow(any(WorldPoint.class));
        verify(panel).showHerePointer("Can't find Fishing spot (frogspawn)" + NOT_SEEN, false);

        plugin.pointTo("QUESTS", "Cook's Assistant");
        verify(panel).showHerePointer("Can't find Cook's Assistant" + NOT_SEEN, false);
        verify(pins, never()).add(any());
        verify(shortestPath, never()).route(any(), any());
    }

    /** None loaded near: the arrow on the nearest spot seen on the floor, a pin on the world map, and Shortest Path asked. */
    @Test
    public void noneLoadedShowsTheWayToTheNearestSeen() throws Exception
    {
        spots().see(HERE, "SKILLING", "Oak tree", new SpotMemory.Spot(3230, 3230, 0));
        spots().see(HERE, "SKILLING", "Oak tree", new SpotMemory.Spot(3210, 3200, 0));
        spots().see(HERE, "SKILLING", "Oak tree", new SpotMemory.Spot(3206, 3206, 1));

        plugin.pointTo("SKILLING", "Oak tree");

        verify(client).setHintArrow(SEEN_OAK);
        verify(shortestPath).route(STANDING, SEEN_OAK);
        verify(pins).add(argThat(pin -> pin instanceof FateLockedPlugin.WayPoint
            && pin.getWorldPoint().equals(SEEN_OAK) && SEEN_OAK.equals(pin.getTarget()) && pin.isSnapToEdge()
            && pin.isJumpOnClick() && "Oak tree (seen here)".equals(pin.getTooltip())));
        verify(panel).showHerePointer(REMEMBERED, true);
    }

    @Test
    public void withShortestPathItDrawsTheWayAndClearingTakesItDown() throws Exception
    {
        spots().see(HERE, "SKILLING", "Oak tree", new SpotMemory.Spot(3210, 3200, 0));
        when(shortestPath.route(STANDING, SEEN_OAK)).thenReturn(true);

        plugin.pointTo("SKILLING", "Oak tree");
        verify(panel).showHerePointer("Shortest Path shows the way to the nearest Oak tree you've seen here.", true);

        plugin.clearPointer();
        verify(client).clearHintArrow();
        verify(shortestPath).clear();
        verify(pins).removeIf(argThat(onlyWays()));
    }

    /** Only a route it asked for is taken down, and only a way has a pin to take off. */
    @Test
    public void onlyWhatItPutUpComesDown() throws Exception
    {
        spots().see(HERE, "SKILLING", "Oak tree", new SpotMemory.Spot(3210, 3200, 0));
        plugin.pointTo("SKILLING", "Oak tree");
        plugin.clearPointer();
        verify(shortestPath, never()).clear();

        found("Oak tree", new SceneSearch.Found(null, OAK, true, 3 * 128));
        plugin.pointTo("SKILLING", "Oak tree");
        plugin.clearPointer();
        verify(pins, times(1)).removeIf(any());
    }

    /** The way may cross other places; the line and the arrow stay up. */
    @Test
    public void theWayGoesOnFromPlaceToPlace() throws Exception
    {
        spots().see(HERE, "SKILLING", "Oak tree", new SpotMemory.Spot(3210, 3200, 0));
        plugin.pointTo("SKILLING", "Oak tree");

        keep(new CanonicalChunk(51, 50));

        verify(client, never()).clearHintArrow();
        verify(pins, never()).removeIf(any());
    }

    /** Once one loads, the arrow moves onto it, and reaching it takes everything down. */
    @Test
    public void onceOneLoadsTheArrowMovesOntoIt() throws Exception
    {
        spots().see(HERE, "SKILLING", "Oak tree", new SpotMemory.Spot(3210, 3200, 0));
        when(shortestPath.route(STANDING, SEEN_OAK)).thenReturn(true);
        plugin.pointTo("SKILLING", "Oak tree");

        found("Oak tree", new SceneSearch.Found(null, OAK, true, 3 * 128));
        keep(HERE);
        verify(client, never().description("not looked for until one loads")).setHintArrow(OAK);

        PluginTestSupport.set(plugin, "pointerLooks", true);
        keep(new CanonicalChunk(51, 50));
        verify(client).setHintArrow(OAK);
        verify(panel).showHerePointer("The arrow points at the nearest Oak tree.", true);
        verify(pins, never()).removeIf(any());

        keep(new CanonicalChunk(51, 50));
        verify(client, never().description("still on the way, from another place")).clearHintArrow();

        when(player.getLocalLocation()).thenReturn(new LocalPoint(5 * 128, 4 * 128, -1));
        keep(new CanonicalChunk(51, 50));
        verify(client).clearHintArrow();
        verify(shortestPath).clear();
        verify(pins).removeIf(any());
    }

    /** At the spot, and nothing like it there: the spot is forgotten, and the card says so. */
    @Test
    public void atTheSpotWithNothingThereItIsForgotten() throws Exception
    {
        spots().see(HERE, "SKILLING", "Oak tree", new SpotMemory.Spot(3210, 3200, 0));
        plugin.pointTo("SKILLING", "Oak tree");

        when(locator.playerWorld()).thenReturn(new WorldPoint(3212, 3203, 0));
        keep(HERE);
        verify(client, never()).clearHintArrow();

        when(locator.playerWorld()).thenReturn(new WorldPoint(3212, 3202, 0));
        keep(HERE);
        verify(client).clearHintArrow();
        verify(panel).showHerePointer("The Oak tree you saw here has moved or gone, so that spot is forgotten.",
            false);
        assertNull(spots().nearest(HERE, "SKILLING", "Oak tree", new SpotMemory.Spot(3205, 3205, 0)));
    }

    /** Inside an instance or on a boat there is no way to show: the spots are the real world's. */
    @Test
    public void inAnInstanceNoWayIsShown() throws Exception
    {
        spots().see(HERE, "SKILLING", "Oak tree", new SpotMemory.Spot(3210, 3200, 0));
        when(locator.playerWorld()).thenReturn(null);

        plugin.pointTo("SKILLING", "Oak tree");

        verify(client, never()).setHintArrow(any(WorldPoint.class));
        verify(panel).showHerePointer("Can't find Oak tree" + NOT_SEEN, false);
    }

    /** The same row in another place is another oak: it points there, rather than taking the arrow down. */
    @Test
    public void theSameRowInAnotherPlacePointsAgain() throws Exception
    {
        spots().see(HERE, "SKILLING", "Oak tree", new SpotMemory.Spot(3210, 3200, 0));
        plugin.pointTo("SKILLING", "Oak tree");
        CanonicalChunk next = new CanonicalChunk(51, 50);
        when(locator.player()).thenReturn(next);
        spots().see(next, "SKILLING", "Oak tree", new SpotMemory.Spot(3270, 3210, 0));

        plugin.pointTo("SKILLING", "Oak tree");

        verify(client).setHintArrow(new WorldPoint(3270, 3210, 0));
    }

    /** The line stays up from place to place, so it names the place the spot was seen in. */
    @Test
    public void theLineNamesThePlace() throws Exception
    {
        com.fatelocked.rules.DecisionService lumbridge = com.fatelocked.rules.DecisionService.create(
            com.fatelocked.rules.RulesSnapshot.of(FateLockedBundle.loadFromJson(new com.google.gson.Gson(),
                GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")))),
            "iron example", "iron example");
        PluginTestSupport.set(plugin, "decisions", lumbridge);
        spots().see(HERE, "SKILLING", "Oak tree", new SpotMemory.Spot(3210, 3200, 0));
        String place = com.fatelocked.sidebar.HerePresenter.placeName(lumbridge, HERE);

        plugin.pointTo("SKILLING", "Oak tree");
        verify(panel).showHerePointer("The minimap's arrow points the way to the nearest Oak tree you've seen in "
            + place + ", and the world map has a pin.", true);
        org.junit.Assert.assertEquals("the card's own title", new com.fatelocked.sidebar.HerePresenter()
            .present(lumbridge, HERE).getPlace(), place);

        when(locator.playerWorld()).thenReturn(new WorldPoint(3211, 3201, 0));
        keep(new CanonicalChunk(51, 50));
        verify(panel).showHerePointer("The Oak tree you saw in " + place
            + " has moved or gone, so that spot is forgotten.", false);
    }

    /** Logging out ends the way too: the pin comes off and the route down. */
    @Test
    public void loggingOutEndsTheWay() throws Exception
    {
        spots().see(HERE, "SKILLING", "Oak tree", new SpotMemory.Spot(3210, 3200, 0));
        when(shortestPath.route(STANDING, SEEN_OAK)).thenReturn(true);
        plugin.pointTo("SKILLING", "Oak tree");
        net.runelite.api.events.GameStateChanged out = new net.runelite.api.events.GameStateChanged();
        out.setGameState(net.runelite.api.GameState.LOGIN_SCREEN);

        try
        {
            plugin.onGameStateChanged(out);
        }
        catch (RuntimeException ignored)
        {
            // Logging out goes on to parts this test doesn't set up; the way ends first.
        }

        verify(shortestPath).clear();
        verify(pins).removeIf(any());
        assertNull(PluginTestSupport.get(plugin, "pointer"));
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

    private SpotMemory spots() throws Exception
    {
        SpotMemory spots = (SpotMemory) PluginTestSupport.get(plugin, "spots");
        assertNotNull(spots);
        return spots;
    }

    /** A filter that takes off a way's pin and leaves the locked areas' pins. */
    private static org.mockito.ArgumentMatcher<Predicate<WorldMapPoint>> onlyWays()
    {
        return filter -> filter.test(mock(FateLockedPlugin.WayPoint.class))
            && !filter.test(mock(FateLockedPlugin.LockedAreaPoint.class));
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
