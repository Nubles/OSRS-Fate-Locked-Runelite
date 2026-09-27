package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fatelocked.SceneEdges.Kind;
import com.fatelocked.SceneEdges.Run;
import com.fatelocked.ui.Palette;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** U3: the scene's edges drawn corner by corner near the player, never bridging a gap. */
public class ChunkBorderRendererTest
{
    private static final int SIZE = 104;
    /** Ten pixels a tile, straight down onto the scene. */
    private static final ChunkBorderRenderer.Projector FLAT = (x, y) -> new Point2D.Double(x * 10, y * 10);
    private static final Run LOCKED_EAST = new Run(true, 64, 0, 104, Kind.LOCKED, 1);

    /** Corners 20 to 25 tiles up are behind the camera: the line stops and starts again. */
    @Test
    public void aCornerThatCantBeProjectedBreaksThePathAndTheGapIsNotBridged()
    {
        ChunkBorderRenderer.Projector behind = (x, y) -> y >= 20 && y <= 25 ? null : FLAT.project(x, y);

        List<double[]> path = segments(ChunkBorderRenderer.edges(Collections.singletonList(LOCKED_EAST),
            Kind.LOCKED, 60, 30, SIZE, SIZE, behind));

        assertEquals(Arrays.asList(PathIterator.SEG_MOVETO, 0.0), Arrays.asList((int) path.get(0)[0], path.get(0)[2]));
        List<Double> moves = new ArrayList<>();
        for (int i = 0; i < path.size(); i++)
        {
            double[] segment = path.get(i);
            assertEquals("the line sits on the chunk line", 640.0, segment[1], 0);
            if (segment[0] == PathIterator.SEG_MOVETO)
            {
                moves.add(segment[2]);
            }
            else
            {
                double from = path.get(i - 1)[2];
                assertEquals("each step is one tile", 10.0, segment[2] - from, 0);
            }
        }
        assertEquals("stops at 19 and starts again at 26", Arrays.asList(0.0, 260.0), moves);
        assertEquals("32 tiles past the player", 620.0, path.get(path.size() - 1)[2], 0);
    }

    @Test
    public void onlyEdgesNearThePlayerAreDrawn()
    {
        Run far = new Run(true, 100, 0, 104, Kind.LOCKED, 1);
        assertTrue(ChunkBorderRenderer.edges(Collections.singletonList(far), Kind.LOCKED, 60, 30, SIZE, SIZE, FLAT)
            .getPathIterator(null).isDone());

        Run along = new Run(false, 64, 0, 104, Kind.PLAIN, 0);
        Rectangle2D near = ChunkBorderRenderer.edges(Collections.singletonList(along), Kind.PLAIN, 10, 60, SIZE, SIZE,
            FLAT).getBounds2D();
        assertEquals(new Rectangle2D.Double(0, 640, 420, 0), near);

        assertTrue("another kind isn't drawn", ChunkBorderRenderer.edges(Collections.singletonList(along),
            Kind.LOCKED, 10, 60, SIZE, SIZE, FLAT).getPathIterator(null).isDone());
    }

    /** RuneLite has no ground height past the scene, so a corner on its far edge is skipped. */
    @Test
    public void aCornerOffTheSceneIsSkipped()
    {
        Rectangle2D drawn = ChunkBorderRenderer.edges(Collections.singletonList(LOCKED_EAST), Kind.LOCKED, 60, 90,
            SIZE, SIZE, FLAT).getBounds2D();
        assertEquals(1030.0, drawn.getMaxY(), 0);
    }

    @Test
    public void theFogLiesOnTheLockedSide()
    {
        Rectangle2D east = ChunkBorderRenderer.fog(Collections.singletonList(LOCKED_EAST), 60, 30, SIZE, SIZE, FLAT)
            .getBounds2D();
        assertEquals(new Rectangle2D.Double(640, 0, 20, 620), east);

        Run lockedWest = new Run(true, 64, 0, 104, Kind.LOCKED, -1);
        Rectangle2D west = ChunkBorderRenderer.fog(Collections.singletonList(lockedWest), 60, 30, SIZE, SIZE, FLAT)
            .getBounds2D();
        assertEquals(new Rectangle2D.Double(620, 0, 20, 620), west);

        assertTrue("a plain edge has no fog", ChunkBorderRenderer.fog(
            Collections.singletonList(new Run(true, 64, 0, 104, Kind.PLAIN, 0)), 60, 30, SIZE, SIZE, FLAT)
            .getPathIterator(null).isDone());
    }

    /** The minimap's locked land: each block placed by its corners, and one that can't be, left out. */
    @Test
    public void lockedLandIsDrawnBlockByBlock()
    {
        List<SceneEdges.Block> blocks = Arrays.asList(new SceneEdges.Block(24, 16, 48, 24),
            new SceneEdges.Block(96, 56, 104, 64));
        GeneralPath all = ChunkBorderRenderer.blocks(blocks, FLAT);
        assertEquals(new Rectangle2D.Double(240, 160, 800, 480), all.getBounds2D());
        assertTrue("each block is whole, to its far corner", all.contains(475, 235) && all.contains(1035, 635));

        ChunkBorderRenderer.Projector near = (x, y) -> x > 60 ? null : FLAT.project(x, y);
        assertEquals(new Rectangle2D.Double(240, 160, 240, 80), ChunkBorderRenderer.blocks(blocks, near).getBounds2D());
    }

    @Test
    public void eachSettingDrawsItsOwnLines()
    {
        List<Run> runs = Arrays.asList(LOCKED_EAST, new Run(false, 64, 0, 104, Kind.PLAIN, 0));
        Palette palette = Palette.defaults();

        BufferedImage lockedEdges = draw(runs, FateLockedConfig.ChunkBorders.LOCKED_EDGES, false);
        assertTrue(has(lockedEdges, palette.lockedEdge()));
        assertTrue("over the dark underlay", has(lockedEdges, Palette.UNDERLAY));
        assertFalse(has(lockedEdges, Palette.PLAIN_EDGE));
        assertFalse(has(lockedEdges, palette.lockedShade()));

        BufferedImage allEdges = draw(runs, FateLockedConfig.ChunkBorders.ALL_EDGES, false);
        assertTrue(has(allEdges, Palette.PLAIN_EDGE));
        assertTrue(has(allEdges, palette.lockedEdge()));

        BufferedImage fogOnly = draw(runs, FateLockedConfig.ChunkBorders.OFF, true);
        assertTrue(has(fogOnly, palette.lockedShade()));
        assertFalse(has(fogOnly, palette.lockedEdge()));
        assertFalse(has(fogOnly, Palette.PLAIN_EDGE));
    }

    private static BufferedImage draw(List<Run> runs, FateLockedConfig.ChunkBorders borders, boolean fog)
    {
        BufferedImage image = new BufferedImage(1100, 1100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        ChunkBorderRenderer.draw(graphics, runs, borders, fog, Palette.defaults(), 60, 60, SIZE, SIZE, FLAT);
        graphics.dispose();
        return image;
    }

    /** Whether any pixel is this colour, give or take a unit of compositing. */
    private static boolean has(BufferedImage image, Color colour)
    {
        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                Color pixel = new Color(image.getRGB(x, y), true);
                if (Math.abs(pixel.getRed() - colour.getRed()) <= 1 && Math.abs(pixel.getGreen() - colour.getGreen()) <= 1
                    && Math.abs(pixel.getBlue() - colour.getBlue()) <= 1
                    && Math.abs(pixel.getAlpha() - colour.getAlpha()) <= 1)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /** Each segment of a path as {type, x, y}. */
    private static List<double[]> segments(GeneralPath path)
    {
        List<double[]> segments = new ArrayList<>();
        double[] coords = new double[6];
        for (PathIterator it = path.getPathIterator(null); !it.isDone(); it.next())
        {
            int type = it.currentSegment(coords);
            segments.add(new double[] {type, coords[0], coords[1]});
        }
        return segments;
    }
}
