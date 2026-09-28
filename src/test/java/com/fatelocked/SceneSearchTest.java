package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fatelocked.sidebar.PointTarget;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Before;
import org.junit.Test;

/**
 * The owner's review, 28 Sept: a clicked Here row's nearest thing, in the loaded scene, in
 * the player's chunk, on their floor first. A 16-tile scene: zone (0, 1), north of the
 * player, is another chunk.
 */
public class SceneSearchTest
{
    private static final CanonicalChunk HERE = new CanonicalChunk(50, 50);
    private static final CanonicalChunk NEXT_DOOR = new CanonicalChunk(51, 50);
    private static final int OAK = 10820;
    private static final int PATCH = 8550;

    private final Client client = mock(Client.class);
    private final ChunkLocator locator = mock(ChunkLocator.class);
    private final WorldView view = mock(WorldView.class);
    private final Player player = mock(Player.class);
    private final Tile[][][] tiles = new Tile[4][16][16];
    private final List<NPC> npcs = new ArrayList<>();

    @Before
    public void setUp()
    {
        Scene scene = mock(Scene.class);
        when(client.getTopLevelWorldView()).thenReturn(view);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldView()).thenReturn(view);
        when(player.getLocalLocation()).thenReturn(at(3, 6));
        when(view.getScene()).thenReturn(scene);
        when(scene.getTiles()).thenReturn(tiles);
        when(view.getSizeX()).thenReturn(16);
        when(view.getSizeY()).thenReturn(16);
        IndexedObjectSet<NPC> set = mock(IndexedObjectSet.class);
        when(set.iterator()).thenAnswer(ask -> npcs.iterator());
        doReturn(set).when(view).npcs();
        when(locator.sceneZone(anyInt(), anyInt())).thenReturn(HERE);
        when(locator.sceneZone(0, 1)).thenReturn(NEXT_DOOR);
        ObjectComposition oak = composition("Oak tree", "Chop down");
        when(client.getObjectDefinition(OAK)).thenReturn(oak);
    }

    @Test
    public void theNearestOnTheFloorInTheChunk()
    {
        GameObject far = object(OAK, 0, 3, 1);
        GameObject near = object(OAK, 0, 6, 6);
        object(OAK, 0, 3, 8);   // Next door, closer still.
        object(OAK, 1, 3, 7);   // Upstairs, closer still.

        SceneSearch.Found found = SceneSearch.nearest(client, locator, HERE, PointTarget.of("SKILLING", "Oak tree"));

        assertSame(near.getLocalLocation(), found.getPoint());
        assertNull(found.getNpc());
        assertTrue(found.isSameFloor());
        assertEquals(3 * 128, found.getDistance());
        assertFalse(far.getLocalLocation() == found.getPoint());
    }

    @Test
    public void onAnotherFloorWhenThatIsAllThereIs()
    {
        GameObject upstairs = object(OAK, 2, 3, 3);
        SceneSearch.Found found = SceneSearch.nearest(client, locator, HERE, PointTarget.of("SKILLING", "Oak tree"));
        assertSame(upstairs.getLocalLocation(), found.getPoint());
        assertFalse(found.isSameFloor());
    }

    /** A fishing spot that offers what the row says, not the nearer one that doesn't; none next door. */
    @Test
    public void anNpcByItsNameAndOptions()
    {
        npc("Fishing spot", HERE, 3, 2, "Net", "Bait");
        NPC lure = npc("Fishing spot", HERE, 7, 7, "Lure", "Bait");
        npc("Fishing spot", NEXT_DOOR, 2, 3, "Lure", "Bait");

        SceneSearch.Found found = SceneSearch.nearest(client, locator, HERE,
            PointTarget.of("SKILLING", "Fishing spot (lure, bait)"));

        assertSame(lure, found.getNpc());
        assertTrue(found.isSameFloor());
    }

    /** A patch shows another object as it grows: it's found by the one it shows now. */
    @Test
    public void anObjectByWhatItShowsNow()
    {
        ObjectComposition base = composition("null");
        when(base.getImpostorIds()).thenReturn(new int[] {8551, 8552});
        ObjectComposition grown = composition("Herb patch", "Rake");
        when(base.getImpostor()).thenReturn(grown);
        when(client.getObjectDefinition(PATCH)).thenReturn(base);
        GameObject patch = object(PATCH, 0, 4, 4);

        SceneSearch.Found found = SceneSearch.nearest(client, locator, HERE, PointTarget.of("FARMING", "Herb patch"));
        assertSame(patch.getLocalLocation(), found.getPoint());
    }

    /** A 2x2 tree sits on four tiles; it's looked at once. */
    @Test
    public void anObjectOnSeveralTilesIsLookedAtOnce()
    {
        GameObject tree = object(OAK, 0, 4, 4);
        for (int[] tile : new int[][] {{5, 4}, {4, 5}, {5, 5}})
        {
            tileAt(0, tile[0], tile[1]);
            when(tiles[0][tile[0]][tile[1]].getGameObjects()).thenReturn(new GameObject[] {tree});
        }
        SceneSearch.nearest(client, locator, HERE, PointTarget.of("SKILLING", "Oak tree"));
        verify(tree, times(1)).getId();
    }

    @Test
    public void nothingWhereThereIsNoneOrNoOneOnTheLand()
    {
        object(OAK, 0, 4, 4);
        assertNull(SceneSearch.nearest(client, locator, HERE, PointTarget.of("SKILLING", "Yew tree")));
        assertNull(SceneSearch.nearest(client, locator, HERE, null));
        assertNull(SceneSearch.nearest(client, locator, null, PointTarget.of("SKILLING", "Oak tree")));

        when(player.getWorldView()).thenReturn(mock(WorldView.class));
        assertNull("on a boat's deck", SceneSearch.nearest(client, locator, HERE,
            PointTarget.of("SKILLING", "Oak tree")));
    }

    private GameObject object(int id, int plane, int x, int y)
    {
        GameObject object = mock(GameObject.class);
        when(object.getId()).thenReturn(id);
        when(object.getPlane()).thenReturn(plane);
        LocalPoint point = at(x, y);
        when(object.getLocalLocation()).thenReturn(point);
        tileAt(plane, x, y);
        when(tiles[plane][x][y].getGameObjects()).thenReturn(new GameObject[] {null, object});
        return object;
    }

    private void tileAt(int plane, int x, int y)
    {
        if (tiles[plane][x][y] == null)
        {
            tiles[plane][x][y] = mock(Tile.class);
        }
    }

    private NPC npc(String name, CanonicalChunk chunk, int x, int y, String... actions)
    {
        NPC npc = mock(NPC.class);
        NPCComposition shown = mock(NPCComposition.class);
        when(shown.getName()).thenReturn(name);
        when(shown.getActions()).thenReturn(actions);
        when(npc.getTransformedComposition()).thenReturn(shown);
        when(npc.getLocalLocation()).thenReturn(at(x, y));
        when(locator.actor(npc)).thenReturn(chunk);
        npcs.add(npc);
        return npc;
    }

    private static ObjectComposition composition(String name, String... actions)
    {
        ObjectComposition composition = mock(ObjectComposition.class);
        when(composition.getName()).thenReturn(name);
        when(composition.getActions()).thenReturn(actions);
        return composition;
    }

    private static LocalPoint at(int x, int y)
    {
        return new LocalPoint(x * 128 + 64, y * 128 + 64, -1);
    }
}
