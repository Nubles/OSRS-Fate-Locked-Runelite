package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.Trust;
import com.fatelocked.ui.Palette;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The scene's chunk lines on the minimap, from the same edges as the game view (U3): locked
 * edges dashed over the dark underlay and, under All edges, the other chunk lines thin and
 * faint. With "shade locked land nearby" the locked land is darkened like fog. Nothing fills
 * the chunk the player stands in, and nothing is drawn without rules for the character playing.
 */
public class FateLockedMinimapOverlay extends Overlay
{
    /**
     * How far from the player a point still projects, in local units: past the whole loaded
     * scene, so a block of locked land is placed whole. The minimap's clip drops the rest.
     */
    private static final int PROJECTION_DISTANCE = 30000;

    /** The minimap in each layout: fixed, resizable, and resizable with the bottom bar. */
    static final int[] MINIMAPS = {
        InterfaceID.Toplevel.MINIMAP,
        InterfaceID.ToplevelOsrsStretch.MINIMAP,
        InterfaceID.ToplevelPreEoc.MINIMAP,
    };

    private final Client client;
    private final FateLockedPlugin plugin;
    private final FateLockedConfig config;

    @Inject
    FateLockedMinimapOverlay(Client client, FateLockedPlugin plugin, FateLockedConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.drawMinimap()) return null;
        DecisionService decisions = plugin.decisions();
        // Another character's rules, or none, draw nothing (B6).
        if (decisions.trust() != Trust.TRUSTED) return null;
        Shape minimap = minimapClip();
        if (minimap == null) return null;
        WorldView view = client.getTopLevelWorldView();
        Located here = plugin.chunkLocator().playerInScene();
        if (view == null || here == null) return null;

        draw(graphics, minimap, plugin.sceneEdges(decisions, view, here.getPlane()), config.chunkBorders(),
            config.shadeNearbyLocked(), plugin.palette(), here.getSceneX(), here.getSceneY(),
            view.getSizeX(), view.getSizeY(), (x, y) ->
            {
                Point dot = Perspective.localToMinimap(client, FateLockedSceneOverlay.corner(x, y, view),
                    PROJECTION_DISTANCE);
                return dot == null ? null : new Point2D.Double(dot.getX(), dot.getY());
            });
        return null;
    }

    /**
     * Draw the scene inside the minimap: the locked land first, when it is shaded, then the
     * lines. The locked edges always show, the minimap's own setting being on; the other
     * chunk lines follow the game view's All edges.
     */
    static void draw(Graphics2D graphics, Shape minimap, SceneEdges scene, FateLockedConfig.ChunkBorders borders,
        boolean fog, Palette palette, int playerX, int playerY, int sizeX, int sizeY,
        ChunkBorderRenderer.Projector projector)
    {
        // A projected chunk is far bigger than the minimap, so everything is clipped to it.
        Shape before = graphics.getClip();
        graphics.clip(minimap);
        if (fog)
        {
            graphics.setColor(palette.lockedShade());
            graphics.fill(ChunkBorderRenderer.blocks(scene.locked(), projector));
        }
        FateLockedConfig.ChunkBorders lines = borders == FateLockedConfig.ChunkBorders.ALL_EDGES
            ? FateLockedConfig.ChunkBorders.ALL_EDGES : FateLockedConfig.ChunkBorders.LOCKED_EDGES;
        ChunkBorderRenderer.draw(graphics, scene.edges(), lines, false, palette, playerX, playerY, sizeX, sizeY,
            projector, ChunkBorderRenderer.MINIMAP_PERIOD, ChunkBorderRenderer.Occlusion.NONE);
        graphics.setClip(before);
    }

    /** The minimap shown in the current layout, as its round draw area; null when none is. */
    private Shape minimapClip()
    {
        for (int id : MINIMAPS)
        {
            Widget minimap = client.getWidget(id);
            if (minimap == null || minimap.isHidden()) continue;
            Rectangle bounds = minimap.getBounds();
            if (bounds != null && bounds.width > 0 && bounds.height > 0)
            {
                return new Ellipse2D.Double(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        }
        return null;
    }
}
