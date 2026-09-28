package com.fatelocked;

import com.fatelocked.ui.Palette;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.Value;

/**
 * Draws the scene's chunk edges tile by tile (U3): each run's tile corners near the player,
 * on the exact chunk line. A corner that can't be projected, off the canvas or behind the
 * camera, breaks the line, so a gap is never bridged. The old outline joined four corners,
 * and vanished whole when any one of them couldn't be projected.
 *
 * <p>Locked edges are dashed over a dark underlay, which says "locked" in any colour (U15).
 * Nothing fills a chunk.
 *
 * <p>The dashes are laid on the ground, each a fixed share of a tile, so they stay put as the
 * camera moves; dashes laid along the screen slid as it turned (the owner's review, 28 Sept).
 * And since RuneLite draws this over the finished scene, a piece of line or fog that a
 * player, NPC or object nearer the camera stands in front of is left out ({@link Occlusion}).
 */
final class ChunkBorderRenderer
{
    /** Only corners within this many tiles of the player are drawn, as RuneLite's ground markers do. */
    static final int RANGE = 32;
    /** How deep the fog on the locked side of an edge goes, in tiles. */
    static final int FOG_TILES = 2;
    /** A dash and its gap on the game view, in tiles: three to a tile. */
    static final double GROUND_PERIOD = 1 / 3.0;
    /**
     * A dash and its gap on the minimap, where a tile is a few pixels: three to a chunk's side,
     * about as long as the screen's dashes were. Both periods divide a chunk's eight tiles, so
     * the pattern is the same on every chunk line and stays put when the scene loads again.
     */
    static final double MINIMAP_PERIOD = 8 / 3.0;
    /** The share of each period that is dash, in its middle, with half the gap at either end. */
    static final double DASH = 0.6;
    /** How far either side of the camera's line of sight to the ground something may stand and hide it, in tiles. */
    static final int ASIDE = 2;

    /** A tile corner of the scene on the canvas, or null when it can't be drawn there. */
    interface Projector
    {
        Point2D project(int sceneX, int sceneY);
    }

    /** One kind of edge, cut into pieces: every piece that shows, joined, and its dashes alone. */
    static final class Lines
    {
        final GeneralPath whole = new GeneralPath();
        final GeneralPath dashes = new GeneralPath();
    }

    private ChunkBorderRenderer()
    {
    }

    /**
     * Draw the edges near the player as the settings say: the fog first, under the lines; in
     * All edges, every chunk line thin and faint; and the locked edges on top. None of it is
     * drawn over what stands in front of it.
     */
    static void draw(Graphics2D graphics, List<SceneEdges.Run> runs, FateLockedConfig.ChunkBorders borders,
        boolean fog, Palette palette, int playerX, int playerY, int sizeX, int sizeY, Projector projector,
        double period, Occlusion occlusion)
    {
        if (fog)
        {
            GeneralPath shade = fog(runs, playerX, playerY, sizeX, sizeY, projector);
            Shape open = occlusion.uncovered(shade);
            graphics.setColor(palette.lockedShade());
            if (open == null)
            {
                graphics.fill(shade);
            }
            else
            {
                Shape before = graphics.getClip();
                graphics.clip(open);
                graphics.fill(shade);
                graphics.setClip(before);
            }
        }
        if (borders == FateLockedConfig.ChunkBorders.ALL_EDGES)
        {
            graphics.setStroke(Palette.PLAIN_EDGE_STROKE);
            graphics.setColor(Palette.PLAIN_EDGE);
            graphics.draw(lines(runs, SceneEdges.Kind.PLAIN, playerX, playerY, sizeX, sizeY, projector, period,
                occlusion).whole);
        }
        if (borders != FateLockedConfig.ChunkBorders.OFF)
        {
            Lines locked = lines(runs, SceneEdges.Kind.LOCKED, playerX, playerY, sizeX, sizeY, projector, period,
                occlusion);
            graphics.setStroke(Palette.UNDERLAY_STROKE);
            graphics.setColor(Palette.UNDERLAY);
            graphics.draw(locked.whole);
            graphics.setStroke(Palette.LOCKED_DASH_STROKE);
            graphics.setColor(palette.lockedEdge());
            graphics.draw(locked.dashes);
        }
    }

    /**
     * The edges of one kind near the player, each tile's line cut where its dashes start and
     * end, so the dashes keep their place on the ground. A piece that something stands in front
     * of is left out, and so is a tile with a corner that can't be projected.
     */
    static Lines lines(List<SceneEdges.Run> runs, SceneEdges.Kind kind, int playerX, int playerY, int sizeX,
        int sizeY, Projector projector, double period, Occlusion occlusion)
    {
        Lines lines = new Lines();
        for (SceneEdges.Run run : runs)
        {
            int[] span = run.getKind() == kind ? near(run, playerX, playerY) : null;
            if (span == null)
            {
                continue;
            }
            boolean joined = false;
            boolean dashing = false;
            Point2D from = corner(run, run.getLine(), span[0], sizeX, sizeY, projector);
            for (int along = span[0]; along < span[1]; along++)
            {
                Point2D to = corner(run, run.getLine(), along + 1, sizeX, sizeY, projector);
                if (from == null || to == null)
                {
                    joined = false;
                    dashing = false;
                    from = to;
                    continue;
                }
                double[] cuts = cuts(along, period);
                for (int i = 0; i + 1 < cuts.length; i++)
                {
                    double start = cuts[i] - along;
                    double end = cuts[i + 1] - along;
                    double middle = (start + end) / 2;
                    double sceneAlong = along + middle;
                    if (occlusion.hides(lerp(from.getX(), to.getX(), middle), lerp(from.getY(), to.getY(), middle),
                        run.isVertical() ? run.getLine() : sceneAlong, run.isVertical() ? sceneAlong : run.getLine()))
                    {
                        joined = false;
                        dashing = false;
                        continue;
                    }
                    double x0 = lerp(from.getX(), to.getX(), start);
                    double y0 = lerp(from.getY(), to.getY(), start);
                    double x1 = lerp(from.getX(), to.getX(), end);
                    double y1 = lerp(from.getY(), to.getY(), end);
                    joined = extend(lines.whole, joined, x0, y0, x1, y1);
                    dashing = dash(sceneAlong, period) && extend(lines.dashes, dashing, x0, y0, x1, y1);
                }
                from = to;
            }
        }
        return lines;
    }

    /**
     * Where the line along one tile is cut, in tiles along its run: at the tile's two corners
     * and at each end of a dash between them. The dashes are counted from the scene's corner,
     * so a piece of line always has the same dashes wherever the player stands.
     */
    static double[] cuts(int along, double period)
    {
        double[] cuts = new double[4 + 2 * (int) Math.ceil(1 / period)];
        int count = 0;
        cuts[count++] = along;
        double gap = (1 - DASH) / 2;
        for (long k = (long) Math.floor(along / period); k * period < along + 1; k++)
        {
            double start = (k + gap) * period;
            double end = (k + 1 - gap) * period;
            if (start > along && start < along + 1)
            {
                cuts[count++] = start;
            }
            if (end > along && end < along + 1)
            {
                cuts[count++] = end;
            }
        }
        cuts[count++] = along + 1;
        return Arrays.copyOf(cuts, count);
    }

    /** Whether a point along a run, in tiles, falls on a dash rather than in a gap. */
    static boolean dash(double along, double period)
    {
        double phase = along / period - Math.floor(along / period);
        return phase > (1 - DASH) / 2 && phase < (1 + DASH) / 2;
    }

    /** Add a piece to a path, joined to the one before it when that one was drawn; true after. */
    private static boolean extend(GeneralPath path, boolean joined, double x0, double y0, double x1, double y1)
    {
        if (!joined)
        {
            path.moveTo(x0, y0);
        }
        path.lineTo(x1, y1);
        return true;
    }

    private static double lerp(double from, double to, double share)
    {
        return from + (to - from) * share;
    }

    /**
     * The fog on the locked side of each locked edge near the player, FOG_TILES deep, as one
     * path of strips. A strip ends where a corner on either of its sides can't be projected.
     */
    static GeneralPath fog(List<SceneEdges.Run> runs, int playerX, int playerY, int sizeX, int sizeY,
        Projector projector)
    {
        GeneralPath path = new GeneralPath();
        List<Point2D> edge = new ArrayList<>();
        List<Point2D> deep = new ArrayList<>();
        for (SceneEdges.Run run : runs)
        {
            int[] span = run.getKind() == SceneEdges.Kind.LOCKED ? near(run, playerX, playerY) : null;
            if (span == null)
            {
                continue;
            }
            int inside = run.getLine() + run.getLockedSide() * FOG_TILES;
            for (int along = span[0]; along <= span[1]; along++)
            {
                Point2D onEdge = corner(run, run.getLine(), along, sizeX, sizeY, projector);
                Point2D inFog = onEdge == null ? null : corner(run, inside, along, sizeX, sizeY, projector);
                if (inFog == null)
                {
                    strip(path, edge, deep);
                    continue;
                }
                edge.add(onEdge);
                deep.add(inFog);
            }
            strip(path, edge, deep);
        }
        return path;
    }

    /**
     * Locked land, as one path of blocks. The minimap's projection is flat, so a block's four
     * corners place it exactly; one that can't be placed is left out.
     */
    static GeneralPath blocks(List<SceneEdges.Block> blocks, Projector projector)
    {
        GeneralPath path = new GeneralPath();
        for (SceneEdges.Block block : blocks)
        {
            Point2D a = projector.project(block.getX0(), block.getY0());
            Point2D b = projector.project(block.getX1(), block.getY0());
            Point2D c = projector.project(block.getX1(), block.getY1());
            Point2D d = projector.project(block.getX0(), block.getY1());
            if (a == null || b == null || c == null || d == null)
            {
                continue;
            }
            path.moveTo(a.getX(), a.getY());
            path.lineTo(b.getX(), b.getY());
            path.lineTo(c.getX(), c.getY());
            path.lineTo(d.getX(), d.getY());
            path.closePath();
        }
        return path;
    }

    /**
     * The tiles where something standing could hide part of what is drawn: those on the way
     * from each piece of drawn ground to the camera, and {@link #ASIDE} either side of them.
     * The ground is the locked edges, with the lines on, and the fog behind them, with it on.
     * Only what stands here is looked at, since working out the outline of everything in the
     * scene each frame would cost too much; the faint lines of All edges have no way of their
     * own for the same reason.
     */
    static boolean[][] corridor(List<SceneEdges.Run> runs, boolean lines, boolean fog, int playerX, int playerY,
        int sizeX, int sizeY, double cameraX, double cameraY, Projector projector)
    {
        boolean[][] sight = new boolean[sizeX][sizeY];
        for (SceneEdges.Run run : runs)
        {
            int[] span = run.getKind() == SceneEdges.Kind.LOCKED ? near(run, playerX, playerY) : null;
            if (span == null)
            {
                continue;
            }
            for (int along = span[0]; along < span[1]; along++)
            {
                // A tile behind the camera shows nothing to hide.
                if (corner(run, run.getLine(), along, sizeX, sizeY, projector) == null)
                {
                    continue;
                }
                if (lines)
                {
                    look(sight, run, run.getLine(), along + 0.5, cameraX, cameraY);
                }
                for (int depth = 0; fog && depth < FOG_TILES; depth++)
                {
                    look(sight, run, run.getLine() + run.getLockedSide() * (depth + 0.5), along + 0.5, cameraX,
                        cameraY);
                }
            }
        }
        boolean[][] corridor = new boolean[sizeX][sizeY];
        for (int x = 0; x < sizeX; x++)
        {
            for (int y = 0; y < sizeY; y++)
            {
                if (!sight[x][y])
                {
                    continue;
                }
                for (int aside = Math.max(0, x - ASIDE); aside <= Math.min(sizeX - 1, x + ASIDE); aside++)
                {
                    for (int ahead = Math.max(0, y - ASIDE); ahead <= Math.min(sizeY - 1, y + ASIDE); ahead++)
                    {
                        corridor[aside][ahead] = true;
                    }
                }
            }
        }
        return corridor;
    }

    /** Mark each tile on the way from a point on the ground to below the camera, a tile at a time. */
    private static void look(boolean[][] sight, SceneEdges.Run run, double across, double along, double cameraX,
        double cameraY)
    {
        double x = run.isVertical() ? across : along;
        double y = run.isVertical() ? along : across;
        double distance = Math.hypot(cameraX - x, cameraY - y);
        for (double step = 0; step <= distance; step++)
        {
            double share = step / Math.max(distance, 1);
            int tileX = (int) Math.floor(x + (cameraX - x) * share);
            int tileY = (int) Math.floor(y + (cameraY - y) * share);
            if (tileX >= 0 && tileY >= 0 && tileX < sight.length && tileY < sight[tileX].length)
            {
                sight[tileX][tileY] = true;
            }
        }
    }

    /** Close one strip: along the edge, then back along its far side. */
    private static void strip(GeneralPath path, List<Point2D> edge, List<Point2D> deep)
    {
        if (edge.size() > 1)
        {
            path.moveTo(edge.get(0).getX(), edge.get(0).getY());
            for (int i = 1; i < edge.size(); i++)
            {
                path.lineTo(edge.get(i).getX(), edge.get(i).getY());
            }
            for (int i = deep.size() - 1; i >= 0; i--)
            {
                path.lineTo(deep.get(i).getX(), deep.get(i).getY());
            }
            path.closePath();
        }
        edge.clear();
        deep.clear();
    }

    /** The part of a run within RANGE of the player, as {from, to}, or null when none is. */
    private static int[] near(SceneEdges.Run run, int playerX, int playerY)
    {
        int across = run.getLine() - (run.isVertical() ? playerX : playerY);
        int at = run.isVertical() ? playerY : playerX;
        int from = Math.max(run.getFrom(), at - RANGE);
        int to = Math.min(run.getTo(), at + RANGE);
        return Math.abs(across) > RANGE || from > to ? null : new int[] {from, to};
    }

    /** A corner on a run's line, or on a line beside it; null off the scene, where RuneLite has no ground height. */
    private static Point2D corner(SceneEdges.Run run, int line, int along, int sizeX, int sizeY, Projector projector)
    {
        int x = run.isVertical() ? line : along;
        int y = run.isVertical() ? along : line;
        if (x < 0 || y < 0 || x >= sizeX || y >= sizeY)
        {
            return null;
        }
        return projector.project(x, y);
    }

    /**
     * What stands between the camera and the ground: players, NPCs and objects, each by its
     * outline on the canvas and where it stands in the scene. A piece of ground is behind one
     * when that one stands nearer the camera, going by where each is on the ground, and its
     * outline covers the piece.
     */
    static final class Occlusion
    {
        /** Nothing in front of anything, as on the minimap. */
        static final Occlusion NONE = new Occlusion(0, 0, Collections.emptyList());

        private final double cameraX;
        private final double cameraY;
        /** Nearest the camera first, each with how far it is and the box around its outline. */
        private final Shape[] hulls;
        private final double[] distances;
        private final Rectangle2D[] boxes;

        /** The camera's place over the ground and what stands there, in the scene's tiles. */
        Occlusion(double cameraX, double cameraY, List<Occluder> occluders)
        {
            this.cameraX = cameraX;
            this.cameraY = cameraY;
            List<Occluder> nearest = new ArrayList<>(occluders);
            nearest.sort(Comparator.comparingDouble(o -> distance(o.getSceneX(), o.getSceneY())));
            hulls = new Shape[nearest.size()];
            distances = new double[nearest.size()];
            boxes = new Rectangle2D[nearest.size()];
            for (int i = 0; i < nearest.size(); i++)
            {
                Occluder occluder = nearest.get(i);
                hulls[i] = occluder.getHull();
                distances[i] = distance(occluder.getSceneX(), occluder.getSceneY());
                boxes[i] = occluder.getHull().getBounds2D();
            }
        }

        /** Whether the ground at a scene point, drawn at a canvas point, is behind something. */
        boolean hides(double canvasX, double canvasY, double sceneX, double sceneY)
        {
            if (hulls.length == 0)
            {
                return false;
            }
            double distance = distance(sceneX, sceneY);
            for (int i = 0; i < hulls.length && distances[i] < distance; i++)
            {
                if (boxes[i].contains(canvasX, canvasY) && hulls[i].contains(canvasX, canvasY))
                {
                    return true;
                }
            }
            return false;
        }

        /**
         * The part of the canvas around some ground that nothing stands on or in front of, to
         * draw that ground through; null when nothing reaches it. The fog lies on the ground,
         * so whatever covers it on the canvas stands in it or before it.
         */
        Shape uncovered(Shape ground)
        {
            Rectangle2D bounds = ground.getBounds2D();
            Area open = null;
            for (int i = 0; i < hulls.length; i++)
            {
                if (boxes[i].intersects(bounds) && ground.intersects(boxes[i]))
                {
                    if (open == null)
                    {
                        open = new Area(bounds);
                    }
                    open.subtract(new Area(hulls[i]));
                }
            }
            return open;
        }

        private double distance(double sceneX, double sceneY)
        {
            return Math.hypot(sceneX - cameraX, sceneY - cameraY);
        }
    }

    /** Something standing in the scene: its outline on the canvas, and where it stands, in tiles. */
    @Value
    static class Occluder
    {
        Shape hull;
        double sceneX;
        double sceneY;
    }
}
