package com.fatelocked;

import com.fatelocked.sidebar.PointTarget;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/**
 * Finds what a row of the Here card points at (the owner's review, 28 Sept): the nearest
 * object or NPC by that name in the loaded scene, inside the chunk the player stands in,
 * on their own floor first. The scene is placed zone by zone through {@link ChunkLocator},
 * so an instance is searched as the chunk it copies. Client thread, on a click only.
 */
final class SceneSearch
{
    /** Instances are copied in 8-tile zones, and a zone never straddles a chunk line. */
    private static final int ZONE_TILES = 8;

    private SceneSearch()
    {
    }

    /** What was found: an NPC, or an object at a point; on the player's floor or another. */
    @Value
    static class Found
    {
        /** The NPC, or null for an object. */
        NPC npc;
        LocalPoint point;
        boolean sameFloor;
        /** How far from the player, in local units. */
        int distance;
    }

    /** The nearest thing the target names in the chunk, or null: none loaded, or no player on the land. */
    static Found nearest(Client client, ChunkLocator locator, CanonicalChunk chunk, PointTarget target)
    {
        WorldView view = client.getTopLevelWorldView();
        Player player = client.getLocalPlayer();
        if (view == null || player == null || chunk == null || target == null || player.getWorldView() != view)
        {
            return null;
        }
        LocalPoint from = player.getLocalLocation();
        if (from == null)
        {
            return null;
        }
        Found best = null;
        if (view.npcs() != null)
        {
            for (NPC npc : view.npcs())
            {
                if (npc == null)
                {
                    continue;
                }
                NPCComposition shown = npc.getTransformedComposition();
                String name = shown != null ? shown.getName() : npc.getName();
                if (!target.matches(name, shown != null ? shown.getActions() : null)
                    || !chunk.equals(locator.actor(npc)))
                {
                    continue;
                }
                LocalPoint at = npc.getLocalLocation();
                if (at != null)
                {
                    // NPCs near the player walk the player's floor.
                    best = nearer(best, new Found(npc, at, true, from.distanceTo(at)));
                }
            }
        }
        Scene scene = view.getScene();
        Tile[][][] tiles = scene == null ? null : scene.getTiles();
        if (tiles == null)
        {
            return best;
        }
        int floor = view.getPlane();
        Set<TileObject> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int zoneX = 0; zoneX * ZONE_TILES < view.getSizeX(); zoneX++)
        {
            for (int zoneY = 0; zoneY * ZONE_TILES < view.getSizeY(); zoneY++)
            {
                if (!chunk.equals(locator.sceneZone(zoneX, zoneY)))
                {
                    continue;
                }
                for (int plane = 0; plane < tiles.length; plane++)
                {
                    for (int x = zoneX * ZONE_TILES; x < (zoneX + 1) * ZONE_TILES && x < tiles[plane].length; x++)
                    {
                        for (int y = zoneY * ZONE_TILES; y < (zoneY + 1) * ZONE_TILES && y < tiles[plane][x].length;
                            y++)
                        {
                            Tile tile = tiles[plane][x][y];
                            if (tile == null)
                            {
                                continue;
                            }
                            for (TileObject object : objectsOn(tile))
                            {
                                if (object == null || !seen.add(object) || !named(client, object, target))
                                {
                                    continue;
                                }
                                LocalPoint at = object.getLocalLocation();
                                if (at != null)
                                {
                                    best = nearer(best, new Found(null, at, object.getPlane() == floor,
                                        from.distanceTo(at)));
                                }
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Whether an object is the target, by the name and options it shows now. */
    private static boolean named(Client client, TileObject object, PointTarget target)
    {
        ObjectComposition shown = shown(client, object);
        return shown != null && target.matches(shown.getName(), shown.getActions());
    }

    /** What an object shows now: the one it stands for, as a patch does as it grows, or itself. Client thread. */
    static ObjectComposition shown(Client client, TileObject object)
    {
        return shown(client, object.getId());
    }

    /** What an object by its id shows now, as above. Client thread. */
    static ObjectComposition shown(Client client, int id)
    {
        ObjectComposition shown = client.getObjectDefinition(id);
        if (shown != null && shown.getImpostorIds() != null)
        {
            ObjectComposition now = shown.getImpostor();
            shown = now != null ? now : shown;
        }
        return shown;
    }

    static TileObject[] objectsOn(Tile tile)
    {
        GameObject[] games = tile.getGameObjects();
        int count = games == null ? 0 : games.length;
        TileObject[] all = new TileObject[count + 3];
        for (int i = 0; i < count; i++)
        {
            all[i] = games[i];
        }
        all[count] = tile.getWallObject();
        all[count + 1] = tile.getDecorativeObject();
        all[count + 2] = tile.getGroundObject();
        return all;
    }

    /** On the player's floor first, then the closer. */
    private static Found nearer(Found best, Found next)
    {
        if (best == null)
        {
            return next;
        }
        if (best.isSameFloor() != next.isSameFloor())
        {
            return next.isSameFloor() ? next : best;
        }
        return next.getDistance() < best.getDistance() ? next : best;
    }
}
