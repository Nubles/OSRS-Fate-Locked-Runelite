package com.fatelocked;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Which rules chunk something is in (G11). On the surface that is the tile's
 * own chunk, as before. Inside an instance it is the chunk the instance was
 * copied from, so a raid or a quest cutscene is judged by its real place. On
 * a Sailing boat it is the sea chunk under the boat.
 *
 * <p>Anything it can't place is null, which every surface reads as Unknown:
 * never a guessed lock. That covers a boat whose ship can't be found, an
 * instance zone with no template, and menu options with no tile, such as
 * Walk here, the minimap and a ship's own options.
 */
final class ChunkLocator
{
    /** Instances are copied in 8-tile zones, 13 across. */
    private static final int ZONE_TILES = 8;
    private static final int INSTANCE_ZONES = 13;
    private static final int NO_TEMPLATE = -1;

    private final Client client;

    ChunkLocator(Client client)
    {
        this.client = client;
    }

    /** The logged-in player's chunk. */
    CanonicalChunk player()
    {
        Player local = client.getLocalPlayer();
        return local == null ? null : actor(local);
    }

    /** An actor's chunk: the player, or an NPC. */
    CanonicalChunk actor(Actor actor)
    {
        if (actor == null) return null;
        return locate(actor.getWorldView(), actor.getLocalLocation());
    }

    /** The chunk a menu option points at: its NPC, or its object's or ground item's tile. */
    CanonicalChunk menuTarget(MenuEntry entry)
    {
        if (entry == null) return null;
        NPC npc = entry.getNpc();
        if (npc != null) return actor(npc);
        return hasSceneTile(entry.getType())
            ? sceneTile(entry.getWorldViewId(), entry.getParam0(), entry.getParam1())
            : null;
    }

    /** A scene tile in a world view, as object and ground-item menu entries give it. */
    CanonicalChunk sceneTile(int worldViewId, int sceneX, int sceneY)
    {
        WorldView view = client.getWorldView(worldViewId);
        if (view == null
            || sceneX < 0 || sceneY < 0 || sceneX >= view.getSizeX() || sceneY >= view.getSizeY())
        {
            return null;
        }
        return locate(view, LocalPoint.fromScene(sceneX, sceneY, view));
    }

    private CanonicalChunk locate(WorldView view, LocalPoint point)
    {
        if (view == null || point == null) return null;
        if (!view.isTopLevel())
        {
            // A boat's deck is a world view of its own, floating on the main one.
            WorldEntity boat = shipOf(view);
            if (boat == null) return null;
            point = boat.transformToMainWorld(point);
            view = client.getTopLevelWorldView();
            if (point == null || view == null) return null;
        }
        Scene scene = view.getScene();
        if (scene == null) return null;
        int plane = view.getPlane();
        if (scene.isInstance() && !hasTemplate(scene, point, plane)) return null;
        WorldPoint world = WorldPoint.fromLocalInstance(scene, point, plane);
        return world == null ? null : WorldChunks.of(world);
    }

    /**
     * RuneLite decodes a missing template (-1), or a zone outside the grid,
     * into a far-off tile rather than failing, so check before asking.
     */
    private static boolean hasTemplate(Scene scene, LocalPoint point, int plane)
    {
        int zoneX = point.getSceneX() / ZONE_TILES;
        int zoneY = point.getSceneY() / ZONE_TILES;
        if (zoneX < 0 || zoneY < 0 || zoneX >= INSTANCE_ZONES || zoneY >= INSTANCE_ZONES) return false;
        int[][][] templates = scene.getInstanceTemplateChunks();
        return templates != null && plane >= 0 && plane < templates.length
            && templates[plane] != null && zoneX < templates[plane].length
            && templates[plane][zoneX] != null && zoneY < templates[plane][zoneX].length
            && templates[plane][zoneX][zoneY] != NO_TEMPLATE;
    }

    /** The ship whose deck is this world view, among the main view's world entities. */
    private WorldEntity shipOf(WorldView deck)
    {
        WorldView main = client.getTopLevelWorldView();
        if (main == null || main.worldEntities() == null) return null;
        WorldEntity indexed = main.worldEntities().byIndex(deck.getId());
        if (indexed != null && indexed.getWorldView() == deck) return indexed;
        for (WorldEntity entity : main.worldEntities())
        {
            if (entity != null && entity.getWorldView() == deck) return entity;
        }
        return null;
    }

    private static boolean hasSceneTile(MenuAction action)
    {
        if (action == null) return false;
        switch (action)
        {
            case GAME_OBJECT_FIRST_OPTION:
            case GAME_OBJECT_SECOND_OPTION:
            case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION:
            case GAME_OBJECT_FIFTH_OPTION:
            case GROUND_ITEM_FIRST_OPTION:
            case GROUND_ITEM_SECOND_OPTION:
            case GROUND_ITEM_THIRD_OPTION:
            case GROUND_ITEM_FOURTH_OPTION:
            case GROUND_ITEM_FIFTH_OPTION:
                return true;
            default:
                return false;
        }
    }
}
