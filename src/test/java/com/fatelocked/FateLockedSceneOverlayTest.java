package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.ui.Palette;
import com.google.gson.Gson;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

/** U3: the scene overlay draws the rules' edges on the tile lines, and nothing for anyone else. */
public class FateLockedSceneOverlayTest
{
    private final Client client = mock(Client.class);
    private final FateLockedPlugin plugin = mock(FateLockedPlugin.class);
    private final FateLockedConfig config = mock(FateLockedConfig.class);
    private final WorldView view = mock(WorldView.class);
    private FateLockedSceneOverlay overlay;
    private DecisionService mine;

    @Before
    public void setUp() throws Exception
    {
        FateLockedBundle mid = FateLockedBundle.loadFromJson(new Gson(),
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")));
        mine = DecisionService.create(RulesSnapshot.of(mid), "iron example", "iron example");
        when(config.chunkBorders()).thenReturn(FateLockedConfig.ChunkBorders.LOCKED_EDGES);
        when(client.getTopLevelWorldView()).thenReturn(view);
        overlay = new FateLockedSceneOverlay(client, plugin, config);
    }

    /** The old outline stood half a tile inside the chunk, at the tiles' centres. */
    @Test
    public void aCornerSitsExactlyOnTheTileLine()
    {
        LocalPoint corner = FateLockedSceneOverlay.corner(64, 10, view);
        assertEquals(64 * 128, corner.getX());
        assertEquals(10 * 128, corner.getY());
    }

    /** Instances can load at the same base, so every scene load counts as a new scene. */
    @Test
    public void aSceneLoadIsANewScene()
    {
        FateLockedPlugin real = new FateLockedPlugin();
        net.runelite.api.events.GameStateChanged loading = new net.runelite.api.events.GameStateChanged();
        loading.setGameState(net.runelite.api.GameState.LOADING);

        real.onGameStateChanged(loading);
        real.onGameStateChanged(loading);

        assertEquals(2, real.sceneGeneration());
    }

    /** The lines and the fog are the player's to turn off: with both off, the game view draws nothing. */
    @Test
    public void bordersAndShadingOffDrawNothing()
    {
        when(config.chunkBorders()).thenReturn(FateLockedConfig.ChunkBorders.OFF);
        when(config.shadeNearbyLocked()).thenReturn(false);
        Graphics2D graphics = mock(Graphics2D.class);

        assertNull(overlay.render(graphics));

        verifyNoInteractions(graphics);
        verify(plugin, never()).decisions();
    }

    /** Turning the lines off leaves the fog to its own setting. */
    @Test
    public void shadingAloneStillDraws()
    {
        when(config.chunkBorders()).thenReturn(FateLockedConfig.ChunkBorders.OFF);
        when(config.shadeNearbyLocked()).thenReturn(true);
        when(plugin.decisions()).thenReturn(DecisionService.create(mine.rules(), "iron example", "someone else"));

        overlay.render(mock(Graphics2D.class));

        verify(plugin).decisions();
    }

    /** The lines and the fog reach the drawing as the player left them, each on its own. */
    @Test
    public void theSettingsReachTheDrawing()
    {
        ChunkLocator locator = mock(ChunkLocator.class);
        when(plugin.decisions()).thenReturn(mine);
        when(plugin.chunkLocator()).thenReturn(locator);
        when(locator.playerInScene()).thenReturn(new Located(null, null, 0, 60, 60));
        when(plugin.sceneEdges(mine, view, 0)).thenReturn(FateLockedMinimapOverlayTest.eastHalfLocked());
        when(plugin.palette()).thenReturn(Palette.defaults());
        Graphics2D graphics = mock(Graphics2D.class);

        // The camera five tiles along and two and a half up the scene.
        when(client.getCameraFpX()).thenReturn(640f);
        when(client.getCameraFpY()).thenReturn(320f);

        try (MockedStatic<ChunkBorderRenderer> renderer = mockStatic(ChunkBorderRenderer.class))
        {
            when(config.shadeNearbyLocked()).thenReturn(false);
            overlay.render(graphics);
            renderer.verify(() -> ChunkBorderRenderer.draw(eq(graphics), anyList(),
                eq(FateLockedConfig.ChunkBorders.LOCKED_EDGES), eq(false), any(), eq(60), eq(60), anyInt(), anyInt(),
                any(), eq(ChunkBorderRenderer.GROUND_PERIOD), any()));
            renderer.verify(() -> ChunkBorderRenderer.corridor(anyList(), eq(true), eq(false), eq(60), eq(60),
                anyInt(), anyInt(), eq(5.0), eq(2.5), any()));

            when(config.chunkBorders()).thenReturn(FateLockedConfig.ChunkBorders.OFF);
            when(config.shadeNearbyLocked()).thenReturn(true);
            overlay.render(graphics);
            renderer.verify(() -> ChunkBorderRenderer.draw(eq(graphics), anyList(),
                eq(FateLockedConfig.ChunkBorders.OFF), eq(true), any(), eq(60), eq(60), anyInt(), anyInt(), any(),
                eq(ChunkBorderRenderer.GROUND_PERIOD), any()));
            renderer.verify(() -> ChunkBorderRenderer.corridor(anyList(), eq(false), eq(true), eq(60), eq(60),
                anyInt(), anyInt(), eq(5.0), eq(2.5), any()));
        }
    }

    /**
     * Only what stands in the corridor to the camera has its outline worked out, each thing
     * once, and a player or NPC as itself, not as the object it also is on its tile.
     */
    @Test
    public void onlyWhatStandsBetweenTheCameraAndTheLinesIsLookedAt()
    {
        boolean[][] corridor = new boolean[104][104];
        corridor[62][10] = true;
        corridor[63][10] = true;
        // Made first: a local point asks its world view for its id, which can't happen mid-stubbing.
        LocalPoint nearTile = new LocalPoint(62 * 128 + 64, 10 * 128 + 64, view);
        LocalPoint awayTile = new LocalPoint(30 * 128 + 64, 30 * 128 + 64, view);
        LocalPoint rockTiles = new LocalPoint(63 * 128, 10 * 128 + 64, view);
        Player near = mock(Player.class);
        when(near.getLocalLocation()).thenReturn(nearTile);
        when(near.getConvexHull()).thenReturn(new Polygon(new int[] {630, 650, 630}, new int[] {150, 150, 300}, 3));
        NPC away = mock(NPC.class);
        when(away.getLocalLocation()).thenReturn(awayTile);
        doReturn(set(near)).when(view).players();
        doReturn(set(away)).when(view).npcs();

        GameObject rock = mock(GameObject.class);
        when(rock.getLocalLocation()).thenReturn(rockTiles);
        when(rock.getConvexHull()).thenReturn(new Rectangle(600, 100, 10, 10));
        GameObject standing = mock(GameObject.class);
        when(standing.getRenderable()).thenReturn(near);
        GameObject tree = mock(GameObject.class);
        Tile[][][] tiles = new Tile[4][104][104];
        tiles[0][62][10] = tile(rock, standing);
        tiles[0][63][10] = tile(rock);
        tiles[0][70][30] = tile(tree);
        Scene scene = mock(Scene.class);
        when(scene.getTiles()).thenReturn(tiles);
        when(view.getScene()).thenReturn(scene);

        ChunkBorderRenderer.Occlusion occlusion = FateLockedSceneOverlay.occlusion(view, 0, corridor, 60, 0);

        assertTrue("behind the player", occlusion.hides(635, 250, 64, 25));
        assertFalse("in front of the player", occlusion.hides(635, 160, 64, 5));
        assertTrue("behind the rock", occlusion.hides(605, 105, 64, 40));
        verify(rock, times(1)).getConvexHull();
        verify(standing, never()).getConvexHull();
        verify(away, never()).getConvexHull();
        verify(tree, never()).getConvexHull();
        assertFalse("nothing when nothing is looked for",
            FateLockedSceneOverlay.occlusion(view, 0, null, 60, 0).hides(635, 250, 64, 25));
    }

    private static Tile tile(GameObject... objects)
    {
        Tile tile = mock(Tile.class);
        when(tile.getGameObjects()).thenReturn(objects);
        return tile;
    }

    @SafeVarargs
    private static <T> IndexedObjectSet<T> set(T... actors)
    {
        @SuppressWarnings("unchecked")
        IndexedObjectSet<T> set = mock(IndexedObjectSet.class);
        when(set.iterator()).thenAnswer(invocation -> Arrays.asList(actors).iterator());
        return set;
    }

    @Test
    public void anotherCharacterGetsNoBorders()
    {
        when(plugin.decisions()).thenReturn(DecisionService.create(mine.rules(), "iron example", "someone else"));
        Graphics2D graphics = mock(Graphics2D.class);

        assertNull(overlay.render(graphics));

        verifyNoInteractions(graphics);
        verify(plugin, never()).chunkLocator();
    }
}
