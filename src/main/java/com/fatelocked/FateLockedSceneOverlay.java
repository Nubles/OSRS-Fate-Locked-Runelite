package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.Trust;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
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
 * <p>Where the edges run is worked out once per scene ({@link SceneEdges}); only the
 * projection is done each frame, since the camera moves ({@link ChunkBorderRenderer}).
 */
public class FateLockedSceneOverlay extends Overlay
{
    private final Client client;
    private final FateLockedPlugin plugin;
    private final FateLockedConfig config;

    // The scene the edges were worked out for, and the edges.
    private DecisionService edgesDecisions;
    private int edgesGeneration = -1;
    private int edgesPlane = -1;
    private int edgesBaseX;
    private int edgesBaseY;
    private List<SceneEdges.Run> edges = Collections.emptyList();

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
        List<SceneEdges.Run> runs = edges(decisions, view, plane);
        if (runs.isEmpty()) return null;
        ChunkBorderRenderer.draw(graphics, runs, borders, fog, plugin.palette(),
            here.getSceneX(), here.getSceneY(), view.getSizeX(), view.getSizeY(), (x, y) ->
            {
                Point canvas = Perspective.localToCanvas(client, corner(x, y, view), plane);
                return canvas == null ? null : new Point2D.Double(canvas.getX(), canvas.getY());
            });
        return null;
    }

    /** A tile corner of the scene, exactly on the tile line, where RuneLite has the ground's height. */
    static LocalPoint corner(int sceneX, int sceneY, WorldView view)
    {
        return new LocalPoint(sceneX << Perspective.LOCAL_COORD_BITS, sceneY << Perspective.LOCAL_COORD_BITS, view);
    }

    /** The scene's edges, worked out again only when the scene or the rules change. */
    List<SceneEdges.Run> edges(DecisionService decisions, WorldView view, int plane)
    {
        int generation = plugin.sceneGeneration();
        if (decisions != edgesDecisions || generation != edgesGeneration || plane != edgesPlane
            || view.getBaseX() != edgesBaseX || view.getBaseY() != edgesBaseY)
        {
            edgesDecisions = decisions;
            edgesGeneration = generation;
            edgesPlane = plane;
            edgesBaseX = view.getBaseX();
            edgesBaseY = view.getBaseY();
            edges = SceneEdges.of(zones(decisions, view.getSizeX() / SceneEdges.ZONE,
                view.getSizeY() / SceneEdges.ZONE));
        }
        return edges;
    }

    /** Each zone of the scene, as the rules tint the chunk it is a copy of. */
    private SceneEdges.Zone[][] zones(DecisionService decisions, int width, int height)
    {
        ChunkLocator locator = plugin.chunkLocator();
        SceneEdges.Zone[][] zones = new SceneEdges.Zone[width][height];
        for (int zx = 0; zx < width; zx++)
        {
            for (int zy = 0; zy < height; zy++)
            {
                CanonicalChunk rules = locator.sceneZone(zx, zy);
                zones[zx][zy] = new SceneEdges.Zone(
                    rules == null ? TintPolicy.Tint.UNKNOWN : TintPolicy.at(decisions, rules), rules);
            }
        }
        return zones;
    }
}
