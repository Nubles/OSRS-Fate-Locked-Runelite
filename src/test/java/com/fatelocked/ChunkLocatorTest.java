package com.fatelocked;

import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Where the rules think something is (G11): the tile's own chunk on the
 * surface, the template chunk inside an instance, the sea chunk under a
 * boat, and nothing at all when that can't be known.
 */
public class ChunkLocatorTest
{
    /** The main scene's south-west corner: tile 3136,3136 is chunk 49,49. */
    private static final int BASE = 3136;
    private static final int DECK_ID = 3;

    private final Client client = mock(Client.class);
    private final WorldView main = mock(WorldView.class);
    private final Scene scene = mock(Scene.class);
    private final ChunkLocator locator = new ChunkLocator(client);

    @Before
    public void surface()
    {
        when(client.getTopLevelWorldView()).thenReturn(main);
        when(client.getWorldView(WorldView.TOPLEVEL)).thenReturn(main);
        when(main.isTopLevel()).thenReturn(true);
        when(main.getId()).thenReturn(WorldView.TOPLEVEL);
        when(main.getScene()).thenReturn(scene);
        when(main.getPlane()).thenReturn(0);
        when(main.getSizeX()).thenReturn(104);
        when(main.getSizeY()).thenReturn(104);
        when(scene.getBaseX()).thenReturn(BASE);
        when(scene.getBaseY()).thenReturn(BASE);
    }

    @Test
    public void onTheSurfaceItIsTheTilesOwnChunk()
    {
        standAt(main, 40, 104 - 1);

        assertEquals(new CanonicalChunk((BASE + 40) >> 6, (BASE + 103) >> 6), locator.player());
    }

    @Test
    public void insideAnInstanceItIsTheChunkTheInstanceCameFrom()
    {
        // Zone 5,5 of this instance is a copy of the zone at tile 3200,3200 (Lumbridge, chunk 50,50).
        instance(5, 5, template(400, 400));
        standAt(main, 5 * 8 + 3, 5 * 8 + 6);

        assertEquals(new CanonicalChunk(50, 50), locator.player());
    }

    @Test
    public void anInstanceZoneWithNoTemplateIsUnknown()
    {
        instance(5, 5, template(400, 400));
        standAt(main, 6 * 8, 6 * 8);

        assertNull(locator.player());
    }

    @Test
    public void aZoneOutsideTheInstanceGridIsUnknown()
    {
        instance(5, 5, template(400, 400));
        standAt(main, 13 * 8 + 1, 40);

        assertNull(locator.player());
    }

    @Test
    public void onABoatItIsTheSeaChunkUnderTheShip()
    {
        WorldView deck = deck();
        WorldEntity ship = mock(WorldEntity.class);
        when(ship.getWorldView()).thenReturn(deck);
        when(ship.transformToMainWorld(any(LocalPoint.class)))
            .thenReturn(new LocalPoint(100 * 128 + 64, 10 * 128 + 64, WorldView.TOPLEVEL));
        ships(ship);
        standAt(deck, 2, 2);

        assertEquals(new CanonicalChunk((BASE + 100) >> 6, (BASE + 10) >> 6), locator.player());
    }

    @Test
    public void aDeckWithNoShipIsUnknown()
    {
        WorldView deck = deck();
        ships();
        standAt(deck, 2, 2);

        assertNull(locator.player());
    }

    @Test
    public void menuTargetsUseTheirNpcOrTheirTile()
    {
        NPC guard = mock(NPC.class);
        when(guard.getWorldView()).thenReturn(main);
        when(guard.getLocalLocation()).thenReturn(new LocalPoint(10 * 128 + 64, 20 * 128 + 64, WorldView.TOPLEVEL));
        MenuEntry talk = entry(MenuAction.NPC_FIRST_OPTION, 0, 0);
        when(talk.getNpc()).thenReturn(guard);
        assertEquals(new CanonicalChunk((BASE + 10) >> 6, (BASE + 20) >> 6), locator.menuTarget(talk));

        MenuEntry door = entry(MenuAction.GAME_OBJECT_FIRST_OPTION, 70, 30);
        assertEquals(new CanonicalChunk((BASE + 70) >> 6, (BASE + 30) >> 6), locator.menuTarget(door));
        MenuEntry item = entry(MenuAction.GROUND_ITEM_THIRD_OPTION, 1, 2);
        assertEquals(new CanonicalChunk(BASE >> 6, BASE >> 6), locator.menuTarget(item));
    }

    @Test
    public void optionsWithNoTileAreUnknown()
    {
        for (MenuAction action : new MenuAction[] {
            MenuAction.WALK, MenuAction.WORLD_ENTITY_FIRST_OPTION, MenuAction.CC_OP })
        {
            assertNull(action.name(), locator.menuTarget(entry(action, 10, 10)));
        }
        assertNull("off the scene", locator.menuTarget(entry(MenuAction.GAME_OBJECT_FIRST_OPTION, 104, 5)));
        assertNull("an unknown world view", locator.sceneTile(99, 5, 5));
        assertNull("nobody logged in", locator.player());
        assertNull(locator.menuTarget(null));
    }

    /** B14: overlays draw where the player stands and tint it as the rules judge it. */
    @Test
    public void inTheSceneThePlayerStandsInTheCopyButIsJudgedByTheOriginal()
    {
        instance(5, 5, template(400, 400));
        when(main.getBaseX()).thenReturn(6400);
        when(main.getBaseY()).thenReturn(6400);
        standAt(main, 5 * 8 + 3, 5 * 8 + 6);

        Located here = locator.playerInScene();

        assertEquals(new CanonicalChunk(50, 50), here.getRules());
        assertEquals(new CanonicalChunk((6400 + 43) >> 6, (6400 + 46) >> 6), here.getScene());
        assertEquals(0, here.getPlane());
        assertEquals("the player's tile in the scene", 43, here.getSceneX());
        assertEquals(46, here.getSceneY());
    }

    @Test
    public void onABoatTheSceneIsTheSeaUnderTheShip()
    {
        when(main.getBaseX()).thenReturn(BASE);
        when(main.getBaseY()).thenReturn(BASE);
        WorldView deck = deck();
        WorldEntity ship = mock(WorldEntity.class);
        when(ship.getWorldView()).thenReturn(deck);
        when(ship.transformToMainWorld(any(LocalPoint.class)))
            .thenReturn(new LocalPoint(100 * 128 + 64, 10 * 128 + 64, WorldView.TOPLEVEL));
        ships(ship);
        standAt(deck, 2, 2);

        Located here = locator.playerInScene();

        CanonicalChunk sea = new CanonicalChunk((BASE + 100) >> 6, (BASE + 10) >> 6);
        assertEquals(sea, here.getRules());
        assertEquals(sea, here.getScene());
        assertEquals("the sea tile under the ship", 100, here.getSceneX());
        assertEquals(10, here.getSceneY());
        ships();
        assertNull("a deck with no ship", locator.playerInScene());
        when(client.getLocalPlayer()).thenReturn(null);
        assertNull("nobody logged in", locator.playerInScene());
    }

    @Test
    public void aSceneChunkIsJudgedByTheChunkItIsACopyOf()
    {
        when(main.getBaseX()).thenReturn(BASE);
        when(main.getBaseY()).thenReturn(BASE);
        assertEquals("on the surface, itself", new CanonicalChunk(49, 49), locator.sceneChunk(new CanonicalChunk(49, 49)));
        assertEquals("partly loaded, judged by what is", new CanonicalChunk(50, 50),
            locator.sceneChunk(new CanonicalChunk(50, 50)));
        assertNull("not loaded", locator.sceneChunk(new CanonicalChunk(60, 60)));

        // The scene chunk at 6400,6400 has its centre (scene tile 31) in zone 3,3, a copy of Lumbridge.
        instance(3, 3, template(400, 400));
        when(main.getBaseX()).thenReturn(6400);
        when(main.getBaseY()).thenReturn(6400);
        assertEquals(new CanonicalChunk(50, 50), locator.sceneChunk(new CanonicalChunk(100, 100)));
        assertNull("its centre zone has no template", locator.sceneChunk(new CanonicalChunk(101, 100)));
    }

    /** U3: the scene's borders are worked out zone by zone, each judged by what it is a copy of. */
    @Test
    public void eachSceneZoneIsJudgedByTheChunkItIsACopyOf()
    {
        when(main.getBaseX()).thenReturn(BASE);
        when(main.getBaseY()).thenReturn(BASE);
        assertEquals("on the surface, its own chunk",
            new CanonicalChunk((BASE + 8 * 8) >> 6, BASE >> 6), locator.sceneZone(8, 0));
        assertNull("past the scene", locator.sceneZone(13, 0));
        assertNull(locator.sceneZone(0, 13));
        assertNull(locator.sceneZone(-1, 0));

        instance(5, 5, template(400, 400));
        when(main.getBaseX()).thenReturn(6400);
        when(main.getBaseY()).thenReturn(6400);
        assertEquals("a copy of Lumbridge", new CanonicalChunk(50, 50), locator.sceneZone(5, 5));
        assertNull("a zone with no template", locator.sceneZone(5, 6));
    }

    private void standAt(WorldView view, int sceneX, int sceneY)
    {
        // Read the view's id first: Mockito can't stub while another stub is half-made.
        int viewId = view.getId();
        Player player = mock(Player.class);
        when(player.getWorldView()).thenReturn(view);
        when(player.getLocalLocation()).thenReturn(new LocalPoint(sceneX * 128 + 64, sceneY * 128 + 64, viewId));
        when(client.getLocalPlayer()).thenReturn(player);
    }

    /** An instance whose only copied zone is (zoneX, zoneY), on plane 0. */
    private void instance(int zoneX, int zoneY, int template)
    {
        int[][][] templates = new int[4][13][13];
        for (int[][] plane : templates) for (int[] row : plane) Arrays.fill(row, -1);
        templates[0][zoneX][zoneY] = template;
        when(scene.isInstance()).thenReturn(true);
        when(scene.getInstanceTemplateChunks()).thenReturn(templates);
    }

    /** RuneLite's template encoding: the source zone in 8-tile units, plane 0, no rotation. */
    private static int template(int zoneX, int zoneY)
    {
        return (zoneX << 14) | (zoneY << 3);
    }

    private WorldView deck()
    {
        WorldView deck = mock(WorldView.class);
        when(deck.isTopLevel()).thenReturn(false);
        when(deck.getId()).thenReturn(DECK_ID);
        return deck;
    }

    @SuppressWarnings("unchecked")
    private void ships(WorldEntity... ships)
    {
        IndexedObjectSet<WorldEntity> entities = mock(IndexedObjectSet.class);
        when(entities.byIndex(DECK_ID)).thenReturn(ships.length > 0 ? ships[0] : null);
        when(entities.iterator()).thenAnswer(call -> ships.length > 0
            ? Arrays.asList(ships).iterator() : Collections.<WorldEntity>emptyIterator());
        org.mockito.Mockito.doReturn(entities).when(main).worldEntities();
    }

    private MenuEntry entry(MenuAction action, int sceneX, int sceneY)
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getType()).thenReturn(action);
        when(entry.getParam0()).thenReturn(sceneX);
        when(entry.getParam1()).thenReturn(sceneY);
        when(entry.getWorldViewId()).thenReturn(WorldView.TOPLEVEL);
        return entry;
    }
}
