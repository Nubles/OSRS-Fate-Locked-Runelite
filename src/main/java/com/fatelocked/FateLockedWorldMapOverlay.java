package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.Trust;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.RenderOverview;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.List;

/**
 * Draws tinted rectangles on the world map widget for every chunk the rules
 * decide (B8): green for owned, red for locked, and the frontier colour for
 * a Chunked run's next rolls. Land only, as the web map; nothing on another
 * character.
 *
 * The render math mirrors RuneLite's built-in WorldMapOverlay — we translate
 * world tile coords to world-map viewport pixels via RenderOverview's zoom
 * level and the centre of the currently-displayed map tile.
 */
public class FateLockedWorldMapOverlay extends Overlay
{
    private static final BasicStroke BORDER = new BasicStroke(1f);

    @Inject private Client client;
    @Inject private FateLockedPlugin plugin;
    @Inject private FateLockedConfig config;
    @Inject private TooltipManager tooltipManager;

    @Inject
    FateLockedWorldMapOverlay()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
        setLayer(OverlayLayer.MANUAL);
        drawAfterInterface(InterfaceID.WORLD_MAP);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.worldMapMode().shading()) return null;
        DecisionService decisions = plugin.decisions();
        // No rules, or another character's: the map draws nothing.
        if (decisions.trust() != Trust.TRUSTED) return null;

        Widget worldMap = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW);
        if (worldMap == null) return null;
        RenderOverview ro = client.getRenderOverview();
        if (ro == null) return null;

        Rectangle bounds = worldMap.getBounds();
        if (bounds == null) return null;

        Shape prevClip = graphics.getClip();
        graphics.setClip(bounds);
        graphics.setStroke(BORDER);

        // Clipping region on the world map
        Area clip = new Area(bounds);

        for (CanonicalChunk chunk : decisions.mappedChunks())
        {
            Rectangle2D rect = worldMapRectForChunk(chunk, bounds, ro);
            if (rect == null) continue;
            if (!clip.intersects(rect)) continue;
            WorldMapChunks.Fill fill = WorldMapChunks.fill(decisions, chunk);
            if (fill == null) continue;

            Color color = fill == WorldMapChunks.Fill.UNLOCKED ? config.unlockedColor()
                : fill == WorldMapChunks.Fill.FRONTIER ? config.frontierColor() : config.lockedColor();
            graphics.setColor(color);
            graphics.fill(rect);
            graphics.setColor(color.darker());
            graphics.draw(rect);
        }

        if (config.worldMapMode().tooltip())
        {
            addHoverTooltip(decisions, bounds, ro);
        }

        graphics.setClip(prevClip);
        return null;
    }

    /** Show the area name + lock status for the chunk under the cursor. */
    private void addHoverTooltip(DecisionService decisions, Rectangle bounds, RenderOverview ro)
    {
        Point mouse = client.getMouseCanvasPosition();
        if (mouse == null || !bounds.contains(mouse.getX(), mouse.getY())) return;

        float pixelsPerTile = ro.getWorldMapZoom();
        if (pixelsPerTile <= 0) return;
        Point centre = ro.getWorldMapPosition();
        if (centre == null) return;

        // Invert worldMapRectForChunk: pixel → world tile → chunk.
        double tileX = centre.getX() + (mouse.getX() - bounds.getCenterX()) / pixelsPerTile;
        double tileY = centre.getY() - (mouse.getY() - bounds.getCenterY()) / pixelsPerTile;
        CanonicalChunk hovered = new CanonicalChunk(
            ((int) Math.floor(tileX)) >> 6, ((int) Math.floor(tileY)) >> 6);

        if (WorldMapChunks.fill(decisions, hovered) == null) return; // nothing drawn there

        // Per-chunk "what's here" from the app's chunk-content dataset —
        // capped per category so dense chunks stay a tooltip, not a page.
        List<String> content = config.worldMapMode().contents()
            ? plugin.getBundle().contentAt(hovered, 4) : Collections.emptyList();
        tooltipManager.add(new Tooltip(WorldMapChunks.tooltip(decisions, hovered, content)));
    }

    /**
     * Translate a canonical chunk into world-map pixel coordinates inside the
     * world-map widget's bounds.
     */
    private Rectangle2D worldMapRectForChunk(CanonicalChunk chunk, Rectangle bounds, RenderOverview ro)
    {
        float pixelsPerTile = ro.getWorldMapZoom();
        // RuneLite's RenderOverview.getWorldMapPosition() returns a Point whose
        // (x, y) are world-tile coordinates of the map centre (not a WorldPoint).
        Point centre = ro.getWorldMapPosition();
        if (centre == null) return null;

        // The world-map widget shows a rectangular view of world tiles, centered
        // on `centre`. For a tile at world (tx, ty), its x-pixel on the widget is:
        //   px = widget.centerX + (tx - centre.x) * pixelsPerTile
        // y-axis is flipped (increasing y = northward in world, upward on screen):
        //   py = widget.centerY - (ty - centre.y) * pixelsPerTile
        double cx = bounds.getCenterX();
        double cy = bounds.getCenterY();

        int tileX = chunk.getCx() << 6;
        int tileY = chunk.getCy() << 6;

        double x0 = cx + (tileX - centre.getX()) * pixelsPerTile;
        double y1 = cy - (tileY - centre.getY()) * pixelsPerTile;
        double x1 = cx + (tileX + 64 - centre.getX()) * pixelsPerTile;
        double y0 = cy - (tileY + 64 - centre.getY()) * pixelsPerTile;

        return new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0);
    }
}
