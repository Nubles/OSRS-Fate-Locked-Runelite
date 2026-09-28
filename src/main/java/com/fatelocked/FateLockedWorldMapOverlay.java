package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.Trust;
import com.fatelocked.ui.Palette;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * The run on the world map (U18, decision 4): locked land shaded dark like fog of war, the
 * frontier of a Chunked run lightly filled, unlocked land left clear, and the unlocked land
 * outlined with the locked edge's dash. Land only, as the web map shows it; nothing on another
 * character. It draws inside the map only, never over the overview or the surface selector.
 *
 * <p>What to draw is worked out once per decision service ({@link WorldMapModel}); each frame
 * places it with RuneLite's own maths ({@link WorldMapProjection}), so it lines up with the pins.
 */
public class FateLockedWorldMapOverlay extends Overlay
{
    private final Client client;
    private final FateLockedPlugin plugin;
    private final FateLockedConfig config;
    private final TooltipManager tooltipManager;

    // What the map draws for the rules in force.
    private DecisionService modelDecisions;
    private WorldMapModel model = WorldMapModel.NONE;
    // The last tooltip, kept while the mouse stays on one chunk.
    private DecisionService tipDecisions;
    private CanonicalChunk tipChunk;
    private boolean tipContents;
    private Palette tipPalette;
    private String tip;

    @Inject
    FateLockedWorldMapOverlay(Client client, FateLockedPlugin plugin, FateLockedConfig config,
        TooltipManager tooltipManager)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.tooltipManager = tooltipManager;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(Overlay.PRIORITY_LOW);
        setLayer(OverlayLayer.MANUAL);
        drawAfterInterface(InterfaceID.WORLDMAP);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        FateLockedConfig.WorldMapMode mode = config.worldMapMode();
        if (!mode.shading()) return null;
        DecisionService decisions = plugin.decisions();
        // No rules, or another character's: the map draws nothing.
        if (decisions.trust() != Trust.TRUSTED) return null;
        Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        WorldMap worldMap = client.getWorldMap();
        if (map == null || worldMap == null) return null;
        Rectangle bounds = map.getBounds();
        Point centre = worldMap.getWorldMapPosition();
        float zoom = worldMap.getWorldMapZoom();
        if (bounds == null || centre == null || zoom <= 0) return null;

        WorldMapProjection projection = new WorldMapProjection(bounds, zoom, centre.getX(), centre.getY());
        Shape clip = WorldMapClip.of(bounds, shown(InterfaceID.Worldmap.OVERVIEW_CONTAINER),
            shown(InterfaceID.Worldmap.MAPLIST_BOX_GRAPHIC0));
        draw(graphics, model(decisions), projection, plugin.palette(), clip);
        if (mode.tooltip())
        {
            tooltip(decisions, projection, clip, mode.contents());
        }
        return null;
    }

    /**
     * Draw the model where the projection places it, inside the clip: one fill per run of
     * chunks in view, and the outline as one path.
     */
    static void draw(Graphics2D graphics, WorldMapModel model, WorldMapProjection projection, Palette palette,
        Shape clip)
    {
        int west = projection.westChunk();
        int east = projection.eastChunk();
        int south = projection.southChunk();
        int north = projection.northChunk();
        Shape before = graphics.getClip();
        graphics.clip(clip);
        for (WorldMapModel.Run run : model.runs())
        {
            if (run.getCy() < south || run.getCy() > north || run.getCx1() < west || run.getCx0() > east)
            {
                continue;
            }
            graphics.setColor(run.getFill() == WorldMapChunks.Fill.FRONTIER
                ? palette.frontierFill() : palette.lockedShade());
            int x0 = projection.lineX(Math.max(run.getCx0(), west) << 6);
            int x1 = projection.lineX((Math.min(run.getCx1(), east) + 1) << 6);
            int y0 = projection.lineY((run.getCy() + 1) << 6);
            int y1 = projection.lineY(run.getCy() << 6);
            graphics.fillRect(x0, y0, x1 - x0, y1 - y0);
        }

        GeneralPath outline = new GeneralPath();
        for (WorldMapModel.Edge edge : model.outline())
        {
            // Kept to the chunks in view, so a long dashed line isn't worked out off the map.
            int low = edge.isVertical() ? south : west;
            int high = (edge.isVertical() ? north : east) + 1;
            int across = edge.isVertical() ? east + 1 : north + 1;
            int acrossLow = edge.isVertical() ? west : south;
            int from = Math.max(edge.getFrom(), low);
            int to = Math.min(edge.getTo(), high);
            if (edge.getLine() < acrossLow || edge.getLine() > across || from >= to)
            {
                continue;
            }
            if (edge.isVertical())
            {
                int x = projection.lineX(edge.getLine() << 6);
                outline.moveTo(x, projection.lineY(from << 6));
                outline.lineTo(x, projection.lineY(to << 6));
            }
            else
            {
                int y = projection.lineY(edge.getLine() << 6);
                outline.moveTo(projection.lineX(from << 6), y);
                outline.lineTo(projection.lineX(to << 6), y);
            }
        }
        graphics.setStroke(Palette.UNDERLAY_STROKE);
        graphics.setColor(Palette.UNDERLAY);
        graphics.draw(outline);
        graphics.setStroke(Palette.LOCKED_EDGE_STROKE);
        graphics.setColor(palette.lockedEdge());
        graphics.draw(outline);
        graphics.setClip(before);
    }

    /** What the map draws for these rules, worked out again only when they change. */
    private WorldMapModel model(DecisionService decisions)
    {
        if (decisions != modelDecisions)
        {
            modelDecisions = decisions;
            model = WorldMapModel.of(decisions);
        }
        return model;
    }

    /** A widget's bounds while it is shown, else null. */
    private Rectangle shown(int id)
    {
        Widget widget = client.getWidget(id);
        return widget == null || widget.isHidden() ? null : widget.getBounds();
    }

    /** The area, status and contents of the chunk under the mouse, built again only when it changes. */
    private void tooltip(DecisionService decisions, WorldMapProjection projection, Shape clip, boolean contents)
    {
        Point mouse = client.getMouseCanvasPosition();
        if (mouse == null || !clip.contains(mouse.getX(), mouse.getY())) return;
        CanonicalChunk hovered = new CanonicalChunk(projection.tileX(mouse.getX()) >> 6,
            projection.tileY(mouse.getY()) >> 6);
        Palette palette = plugin.palette();
        if (decisions != tipDecisions || !hovered.equals(tipChunk) || contents != tipContents
            || palette != tipPalette)
        {
            tipDecisions = decisions;
            tipChunk = hovered;
            tipContents = contents;
            tipPalette = palette;
            // Capped per category, so a dense chunk stays a tooltip, not a page.
            List<String> content = contents ? plugin.getBundle().contentAt(hovered, 4) : Collections.emptyList();
            tip = WorldMapChunks.tooltip(decisions, hovered, content, palette);
        }
        if (tip != null)
        {
            tooltipManager.add(new Tooltip(tip));
        }
    }
}
