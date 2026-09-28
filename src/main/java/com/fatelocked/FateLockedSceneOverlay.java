package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.Trust;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The chunk lines around the player on the game scene (U3): locked edges dashed over a dark
 * underlay and, under All edges, every other chunk line thin and faint. With "shade nearby
 * locked", a short fog lies on the locked side of each locked edge. Nothing fills a chunk, and
 * nothing is drawn without rules for the character playing.
 *
 * <p>Where the edges run is worked out once per scene ({@link SceneEdgesCache}); only the
 * projection is done each frame, since the camera moves ({@link ChunkBorderRenderer}). So is
 * what stands in front of the lines, from the players, NPCs and objects between them and the
 * camera: RuneLite draws over the finished scene, and the lines would cover them.
 */
public class FateLockedSceneOverlay extends Overlay
{
    private final Client client;
    private final FateLockedPlugin plugin;
    private final FateLockedConfig config;

    @Inject
    FateLockedSceneOverlay(Client client, FateLockedPlugin plugin, FateLockedConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        FateLockedConfig.ChunkBorders borders = config.chunkBorders();
        boolean fog = config.shadeNearbyLocked();
        if (borders == FateLockedConfig.ChunkBorders.OFF && !fog) return null;
        DecisionService decisions = plugin.decisions();
        // Another character's rules, or none, draw nothing (B6).
        if (decisions.trust() != Trust.TRUSTED) return null;
        WorldView view = client.getTopLevelWorldView();
        Located here = plugin.chunkLocator().playerInScene();
        if (view == null || here == null) return null;

        int plane = here.getPlane();
        SceneEdges scene = plugin.sceneEdges(decisions, view, plane);
        if (scene.edges().isEmpty()) return null;
        ChunkBorderRenderer.Projector projector = (x, y) ->
        {
            Point canvas = Perspective.localToCanvas(client, corner(x, y, view), plane);
            return canvas == null ? null : new Point2D.Double(canvas.getX(), canvas.getY());
        };
        double cameraX = client.getCameraFpX() / Perspective.LOCAL_TILE_SIZE;
        double cameraY = client.getCameraFpY() / Perspective.LOCAL_TILE_SIZE;
        boolean[][] corridor = ChunkBorderRenderer.corridor(scene.edges(),
            borders != FateLockedConfig.ChunkBorders.OFF, fog, here.getSceneX(), here.getSceneY(), view.getSizeX(),
            view.getSizeY(), cameraX, cameraY, projector);
        ChunkBorderRenderer.draw(graphics, scene.edges(), borders, fog, plugin.palette(),
            here.getSceneX(), here.getSceneY(), view.getSizeX(), view.getSizeY(), projector,
            ChunkBorderRenderer.GROUND_PERIOD, occlusion(view, plane, corridor, cameraX, cameraY));
        return null;
    }

    /** A tile corner of the scene, exactly on the tile line, where RuneLite has the ground's height. */
    static LocalPoint corner(int sceneX, int sceneY, WorldView view)
    {
        return new LocalPoint(sceneX << Perspective.LOCAL_COORD_BITS, sceneY << Perspective.LOCAL_COORD_BITS, view);
    }

    /**
     * What stands in the corridor between the camera and the lines, each by its outline: the
     * players and NPCs, and the objects, walls and wall decorations on the player's floor.
     * Nothing elsewhere has its outline worked out.
     */
    static ChunkBorderRenderer.Occlusion occlusion(WorldView view, int plane, boolean[][] corridor, double cameraX,
        double cameraY)
    {
        if (corridor == null)
        {
            return ChunkBorderRenderer.Occlusion.NONE;
        }
        List<ChunkBorderRenderer.Occluder> occluders = new ArrayList<>();
        actors(occluders, corridor, view.players());
        actors(occluders, corridor, view.npcs());
        Scene scene = view.getScene();
        Tile[][][] tiles = scene == null ? null : scene.getTiles();
        if (tiles != null && plane >= 0 && plane < tiles.length)
        {
            Set<GameObject> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int x = 0; x < corridor.length && x < tiles[plane].length; x++)
            {
                for (int y = 0; y < corridor[x].length && y < tiles[plane][x].length; y++)
                {
                    Tile tile = corridor[x][y] ? tiles[plane][x][y] : null;
                    if (tile == null)
                    {
                        continue;
                    }
                    GameObject[] objects = tile.getGameObjects();
                    for (int i = 0; objects != null && i < objects.length; i++)
                    {
                        GameObject object = objects[i];
                        // A player or NPC stands on its tile as an object too; it is counted once, as itself.
                        if (object != null && !(object.getRenderable() instanceof Actor) && seen.add(object))
                        {
                            add(occluders, object.getLocalLocation(), object.getConvexHull());
                        }
                    }
                    WallObject wall = tile.getWallObject();
                    if (wall != null)
                    {
                        add(occluders, wall.getLocalLocation(), wall.getConvexHull());
                        add(occluders, wall.getLocalLocation(), wall.getConvexHull2());
                    }
                    DecorativeObject decoration = tile.getDecorativeObject();
                    if (decoration != null)
                    {
                        add(occluders, decoration.getLocalLocation(), decoration.getConvexHull());
                        add(occluders, decoration.getLocalLocation(), decoration.getConvexHull2());
                    }
                }
            }
        }
        return new ChunkBorderRenderer.Occlusion(cameraX, cameraY, occluders);
    }

    private static void actors(List<ChunkBorderRenderer.Occluder> occluders, boolean[][] corridor,
        Iterable<? extends Actor> actors)
    {
        if (actors == null)
        {
            return;
        }
        for (Actor actor : actors)
        {
            LocalPoint at = actor == null ? null : actor.getLocalLocation();
            if (at != null && at.getSceneX() >= 0 && at.getSceneY() >= 0 && at.getSceneX() < corridor.length
                && at.getSceneY() < corridor[at.getSceneX()].length && corridor[at.getSceneX()][at.getSceneY()])
            {
                add(occluders, at, actor.getConvexHull());
            }
        }
    }

    private static void add(List<ChunkBorderRenderer.Occluder> occluders, LocalPoint at, Shape hull)
    {
        if (at != null && hull != null)
        {
            occluders.add(new ChunkBorderRenderer.Occluder(hull, at.getX() / (double) Perspective.LOCAL_TILE_SIZE,
                at.getY() / (double) Perspective.LOCAL_TILE_SIZE));
        }
    }
}
