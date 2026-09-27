package com.fatelocked;

import com.fatelocked.ui.Palette;
import java.awt.Graphics2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the scene's chunk edges tile by tile (U3): each run's tile corners near the player,
 * on the exact chunk line, joined into one path. A corner that can't be projected, off the
 * canvas or behind the camera, breaks the path, so a gap is never bridged. The old outline
 * joined four corners, and vanished whole when any one of them couldn't be projected.
 *
 * <p>Locked edges are dashed over a dark underlay, which says "locked" in any colour (U15).
 * Nothing fills a chunk.
 */
final class ChunkBorderRenderer
{
    /** Only corners within this many tiles of the player are drawn, as RuneLite's ground markers do. */
    static final int RANGE = 32;
    /** How deep the fog on the locked side of an edge goes, in tiles. */
    static final int FOG_TILES = 2;

    /** A tile corner of the scene on the canvas, or null when it can't be drawn there. */
    interface Projector
    {
        Point2D project(int sceneX, int sceneY);
    }

    private ChunkBorderRenderer()
    {
    }

    /**
     * Draw the edges near the player as the settings say: the fog first, under the lines; in
     * All edges, every chunk line thin and faint; and the locked edges on top.
     */
    static void draw(Graphics2D graphics, List<SceneEdges.Run> runs, FateLockedConfig.ChunkBorders borders,
        boolean fog, Palette palette, int playerX, int playerY, int sizeX, int sizeY, Projector projector)
    {
        if (fog)
        {
            graphics.setColor(palette.lockedShade());
            graphics.fill(fog(runs, playerX, playerY, sizeX, sizeY, projector));
        }
        if (borders == FateLockedConfig.ChunkBorders.ALL_EDGES)
        {
            graphics.setStroke(Palette.PLAIN_EDGE_STROKE);
            graphics.setColor(Palette.PLAIN_EDGE);
            graphics.draw(edges(runs, SceneEdges.Kind.PLAIN, playerX, playerY, sizeX, sizeY, projector));
        }
        if (borders != FateLockedConfig.ChunkBorders.OFF)
        {
            GeneralPath locked = edges(runs, SceneEdges.Kind.LOCKED, playerX, playerY, sizeX, sizeY, projector);
            graphics.setStroke(Palette.UNDERLAY_STROKE);
            graphics.setColor(Palette.UNDERLAY);
            graphics.draw(locked);
            graphics.setStroke(Palette.LOCKED_EDGE_STROKE);
            graphics.setColor(palette.lockedEdge());
            graphics.draw(locked);
        }
    }

    /** The edges of one kind near the player, as one path. */
    static GeneralPath edges(List<SceneEdges.Run> runs, SceneEdges.Kind kind, int playerX, int playerY,
        int sizeX, int sizeY, Projector projector)
    {
        GeneralPath path = new GeneralPath();
        for (SceneEdges.Run run : runs)
        {
            int[] span = run.getKind() == kind ? near(run, playerX, playerY) : null;
            if (span == null)
            {
                continue;
            }
            boolean drawing = false;
            for (int along = span[0]; along <= span[1]; along++)
            {
                Point2D corner = corner(run, run.getLine(), along, sizeX, sizeY, projector);
                if (corner == null)
                {
                    drawing = false;
                }
                else if (drawing)
                {
                    path.lineTo(corner.getX(), corner.getY());
                }
                else
                {
                    path.moveTo(corner.getX(), corner.getY());
                    drawing = true;
                }
            }
        }
        return path;
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
}
