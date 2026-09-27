package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;

/**
 * Tints the player's current chunk on the main game scene. Gives an at-a-glance
 * sense of the chunk's status (green/red/grey) matching the web app's colors.
 */
public class FateLockedSceneOverlay extends Overlay
{
    private static final Stroke STROKE = new BasicStroke(2f);
    private static final Stroke BORDER_STROKE = new BasicStroke(3.5f);

    @Inject private Client client;
    @Inject private FateLockedPlugin plugin;
    @Inject private FateLockedConfig config;

    @Inject
    FateLockedSceneOverlay()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        FateLockedConfig.ChunkBorders borders = config.chunkBorders();
        if (borders == FateLockedConfig.ChunkBorders.OFF && !config.shadeNearbyLocked()) return null;
        DecisionService decisions = plugin.decisions();
        if (decisions.rules().isEmpty()) return null; // no rules yet: nothing to tint
        ChunkLocator locator = plugin.chunkLocator();
        Located here = locator.playerInScene();
        WorldView view = client.getTopLevelWorldView();
        if (here == null || view == null) return null;
        // Drawn where the player stands in the scene; tinted as the rules judge it (B14).
        CanonicalChunk chunk = here.getScene();
        int plane = here.getPlane();

        // Light shading for surrounding locked chunks goes first, under the
        // current-chunk tint and borders.
        if (config.shadeNearbyLocked())
        {
            drawSurroundingLocked(graphics, chunk, plane, decisions, locator, view);
        }

        Color tint = TintPolicy.color(TintPolicy.at(decisions, here.getRules()), config);
        if (borders == FateLockedConfig.ChunkBorders.ALL_EDGES && tint != null)
        {
            drawChunkOutline(graphics, chunk, plane, view, tint);
        }

        if (borders != FateLockedConfig.ChunkBorders.OFF)
        {
            drawLockedBorders(graphics, chunk, plane, decisions, locator, view);
        }
        return null;
    }

    /**
     * Trace a bright line along any edge of the current chunk that borders a
     * locked chunk — the "danger here" cue right where you'd cross over.
     */
    private void drawLockedBorders(Graphics2D g, CanonicalChunk chunk, int plane, DecisionService decisions,
        ChunkLocator locator, WorldView view)
    {
        int cx = chunk.getCx();
        int cy = chunk.getCy();
        int bx = cx << 6;
        int by = cy << 6;

        Color c = config.lockedColor();
        g.setStroke(BORDER_STROKE);
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 255));

        if (isLocked(decisions, locator, cx + 1, cy)) drawEdge(g, bx + 63, by, bx + 63, by + 63, plane, view); // east
        if (isLocked(decisions, locator, cx - 1, cy)) drawEdge(g, bx, by, bx, by + 63, plane, view);           // west
        if (isLocked(decisions, locator, cx, cy + 1)) drawEdge(g, bx, by + 63, bx + 63, by + 63, plane, view); // north
        if (isLocked(decisions, locator, cx, cy - 1)) drawEdge(g, bx, by, bx + 63, by, plane, view);           // south
    }

    /** A neighbouring scene chunk, judged by the rules chunk it is a copy of. */
    private static boolean isLocked(DecisionService decisions, ChunkLocator locator, int cx, int cy)
    {
        return TintPolicy.isLocked(decisions, locator.sceneChunk(new CanonicalChunk(cx, cy)));
    }

    /**
     * Lightly tint every locked chunk overlapping the loaded scene (except the
     * one the player is standing in, which gets the full treatment elsewhere).
     */
    private void drawSurroundingLocked(Graphics2D g, CanonicalChunk current, int plane, DecisionService decisions,
        ChunkLocator locator, WorldView view)
    {
        int baseX = view.getBaseX();
        int baseY = view.getBaseY();
        int cxMin = baseX >> 6, cxMax = (baseX + view.getSizeX() - 1) >> 6;
        int cyMin = baseY >> 6, cyMax = (baseY + view.getSizeY() - 1) >> 6;

        Color light = faint(config.lockedColor());
        g.setColor(light);
        for (int cx = cxMin; cx <= cxMax; cx++)
        {
            for (int cy = cyMin; cy <= cyMax; cy++)
            {
                if (cx == current.getCx() && cy == current.getCy()) continue;
                CanonicalChunk c = new CanonicalChunk(cx, cy);
                if (!TintPolicy.isLocked(decisions, locator.sceneChunk(c))) continue;
                Polygon p = chunkScenePolyClamped(c, plane, view);
                if (p != null) g.fillPolygon(p);
            }
        }
    }

    /** Chunk outline polygon clipped to the loaded scene, so partly-visible chunks still draw. */
    private Polygon chunkScenePolyClamped(CanonicalChunk chunk, int plane, WorldView view)
    {
        int minX = view.getBaseX(), minY = view.getBaseY();
        int maxX = minX + view.getSizeX() - 1, maxY = minY + view.getSizeY() - 1;
        int x0 = Math.max(chunk.getCx() << 6, minX);
        int y0 = Math.max(chunk.getCy() << 6, minY);
        int x1 = Math.min((chunk.getCx() << 6) + 63, maxX);
        int y1 = Math.min((chunk.getCy() << 6) + 63, maxY);
        if (x0 > x1 || y0 > y1) return null; // no overlap with the scene

        int[][] cs = { { x0, y0 }, { x1, y0 }, { x1, y1 }, { x0, y1 } };
        Polygon p = new Polygon();
        for (int[] c : cs)
        {
            LocalPoint lp = LocalPoint.fromWorld(view, c[0], c[1]);
            if (lp == null) return null;
            Point cv = Perspective.localToCanvas(client, lp, plane);
            if (cv == null) return null;
            p.addPoint(cv.getX(), cv.getY());
        }
        return p;
    }

    /** A faint version of a color for subtle background shading. */
    private static Color faint(Color c)
    {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(20, Math.min(c.getAlpha(), 110) / 3));
    }

    private void drawEdge(Graphics2D g, int x0, int y0, int x1, int y1, int plane, WorldView view)
    {
        LocalPoint a = LocalPoint.fromWorld(view, x0, y0);
        LocalPoint b = LocalPoint.fromWorld(view, x1, y1);
        if (a == null || b == null) return; // edge off-scene
        Point ca = Perspective.localToCanvas(client, a, plane);
        Point cb = Perspective.localToCanvas(client, b, plane);
        if (ca == null || cb == null) return;
        g.drawLine(ca.getX(), ca.getY(), cb.getX(), cb.getY());
    }

    private void drawChunkOutline(Graphics2D g, CanonicalChunk chunk, int plane, WorldView view, Color color)
    {
        int baseX = chunk.getCx() << 6;
        int baseY = chunk.getCy() << 6;

        // Build a polygon around the chunk's perimeter — draw the four corner
        // tiles and let Perspective do the projection.
        LocalPoint[] corners = new LocalPoint[] {
            LocalPoint.fromWorld(view, baseX, baseY),
            LocalPoint.fromWorld(view, baseX + 63, baseY),
            LocalPoint.fromWorld(view, baseX + 63, baseY + 63),
            LocalPoint.fromWorld(view, baseX, baseY + 63)
        };

        Polygon p = new Polygon();
        for (LocalPoint lp : corners)
        {
            if (lp == null) return; // chunk is off-scene
            Point canvas = Perspective.localToCanvas(client, lp, plane);
            if (canvas == null) return;
            p.addPoint(canvas.getX(), canvas.getY());
        }

        g.setStroke(STROKE);
        g.setColor(color);
        g.fillPolygon(p);
        g.setColor(new Color(
            Math.min(color.getRed() + 30, 255),
            Math.min(color.getGreen() + 30, 255),
            Math.min(color.getBlue() + 30, 255),
            220));
        g.drawPolygon(p);
    }
}
