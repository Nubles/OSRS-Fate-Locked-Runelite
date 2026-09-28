package com.fatelocked;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fatelocked.ChunkBorderRenderer.Occluder;
import com.fatelocked.ChunkBorderRenderer.Occlusion;
import com.fatelocked.SceneEdges.Kind;
import com.fatelocked.SceneEdges.Run;
import com.fatelocked.ui.Palette;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
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

/**
 * U3: the scene's edges drawn corner by corner near the player, never bridging a gap, with
 * the dashes fixed on the ground and nothing drawn over what stands in front of it.
 */
public class ChunkBorderRendererTest
{
    private static final int SIZE = 104;
    private static final double GROUND = ChunkBorderRenderer.GROUND_PERIOD;
    /** Ten pixels a tile, straight down onto the scene. */
    private static final ChunkBorderRenderer.Projector FLAT = (x, y) -> new Point2D.Double(x * 10, y * 10);
    private static final Run LOCKED_EAST = new Run(true, 64, 0, 104, Kind.LOCKED, 1);

    /** Corners 20 to 25 tiles up are behind the camera: the line stops and starts again. */
    @Test
    public void aCornerThatCantBeProjectedBreaksThePathAndTheGapIsNotBridged()
    {
        ChunkBorderRenderer.Projector behind = (x, y) -> y >= 20 && y <= 25 ? null : FLAT.project(x, y);

        List<double[]> path = segments(whole(LOCKED_EAST, Kind.LOCKED, 60, 30, behind, Occlusion.NONE));

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
                double step = segment[2] - path.get(i - 1)[2];
                assertTrue("each piece is within a tile, going on: " + step, step > 0 && step < 10);
            }
        }
        assertEquals("stops at 19 and starts again at 26", Arrays.asList(0.0, 260.0), moves);
        assertEquals("32 tiles past the player", 620.0, path.get(path.size() - 1)[2], 1e-9);
    }

    @Test
    public void onlyEdgesNearThePlayerAreDrawn()
    {
        Run far = new Run(true, 100, 0, 104, Kind.LOCKED, 1);
        assertTrue(whole(far, Kind.LOCKED, 60, 30, FLAT, Occlusion.NONE).getPathIterator(null).isDone());

        Run along = new Run(false, 64, 0, 104, Kind.PLAIN, 0);
        Rectangle2D near = whole(along, Kind.PLAIN, 10, 60, FLAT, Occlusion.NONE).getBounds2D();
        assertEquals(new Rectangle2D.Double(0, 640, 420, 0), near);

        assertTrue("another kind isn't drawn",
            whole(along, Kind.LOCKED, 10, 60, FLAT, Occlusion.NONE).getPathIterator(null).isDone());
    }

    /** RuneLite has no ground height past the scene, so a corner on its far edge is skipped. */
    @Test
    public void aCornerOffTheSceneIsSkipped()
    {
        Rectangle2D drawn = whole(LOCKED_EAST, Kind.LOCKED, 60, 90, FLAT, Occlusion.NONE).getBounds2D();
        assertEquals(1030.0, drawn.getMaxY(), 1e-9);
    }

    /**
     * Three dashes to a tile on the game view, each 0.6 of its third, with half a gap at either
     * end, so every tile corner falls in a gap; and three to a chunk's side on the minimap.
     */
    @Test
    public void theDashesAreCutOnTheGround()
    {
        double[] cuts = ChunkBorderRenderer.cuts(5, GROUND);
        double[] expected = {5, 5 + 0.2 / 3, 5 + 0.8 / 3, 5 + 1.2 / 3, 5 + 1.8 / 3, 5 + 2.2 / 3, 5 + 2.8 / 3, 6};
        assertArrayEquals(expected, cuts, 1e-9);
        for (int i = 0; i + 1 < cuts.length; i++)
        {
            assertEquals("dash and gap in turn, from a gap", i % 2 == 1,
                ChunkBorderRenderer.dash((cuts[i] + cuts[i + 1]) / 2, GROUND));
        }

        double minimap = ChunkBorderRenderer.MINIMAP_PERIOD;
        assertArrayEquals(new double[] {0, 0.2 * 8 / 3, 1}, ChunkBorderRenderer.cuts(0, minimap), 1e-9);
        assertFalse("a chunk corner is in a gap", ChunkBorderRenderer.dash(8.01, minimap));
        for (double at = 0.05; at < 8; at += 0.1)
        {
            assertEquals("every chunk side is the same, at " + at, ChunkBorderRenderer.dash(at, minimap),
                ChunkBorderRenderer.dash(at + 24, minimap));
        }
    }

    /**
     * The dashes of a tile are the same wherever the player stands and however the camera
     * looks: laid along the screen from wherever the line began, they slid as the player
     * walked and the camera turned.
     */
    @Test
    public void theDashesKeepTheirPlaceWhereverThePlayerStands()
    {
        List<double[]> here = dashes(ChunkBorderRenderer.lines(Collections.singletonList(LOCKED_EAST), Kind.LOCKED,
            60, 40, SIZE, SIZE, FLAT, GROUND, Occlusion.NONE).dashes, 100, 110);
        List<double[]> aStepOn = dashes(ChunkBorderRenderer.lines(Collections.singletonList(LOCKED_EAST), Kind.LOCKED,
            60, 41, SIZE, SIZE, FLAT, GROUND, Occlusion.NONE).dashes, 100, 110);
        // The camera looking so a tile is twice as long up the screen: the same dashes, twice as long.
        ChunkBorderRenderer.Projector stretched = (x, y) -> new Point2D.Double(x * 10, y * 20);
        List<double[]> turned = dashes(ChunkBorderRenderer.lines(Collections.singletonList(LOCKED_EAST), Kind.LOCKED,
            60, 40, SIZE, SIZE, stretched, GROUND, Occlusion.NONE).dashes, 200, 220);
        assertEquals(3, turned.size());
        for (int i = 0; i < here.size(); i++)
        {
            assertArrayEquals(new double[] {here.get(i)[0] * 2, here.get(i)[1] * 2}, turned.get(i), 1e-3);
        }
        // On the minimap too, where a dash spans tiles.
        List<double[]> minimap = dashes(ChunkBorderRenderer.lines(Collections.singletonList(LOCKED_EAST),
            Kind.LOCKED, 60, 40, SIZE, SIZE, FLAT, ChunkBorderRenderer.MINIMAP_PERIOD, Occlusion.NONE).dashes, 160, 240);
        List<double[]> minimapStepOn = dashes(ChunkBorderRenderer.lines(Collections.singletonList(LOCKED_EAST),
            Kind.LOCKED, 60, 41, SIZE, SIZE, FLAT, ChunkBorderRenderer.MINIMAP_PERIOD, Occlusion.NONE).dashes, 160,
            240);
        assertEquals(3, minimap.size());
        for (int i = 0; i < minimap.size(); i++)
        {
            assertArrayEquals(minimap.get(i), minimapStepOn.get(i), 0);
        }

        // A path keeps its points as floats.
        assertEquals(3, here.size());
        assertArrayEquals(new double[] {100 + 2 / 3.0, 100 + 8 / 3.0}, here.get(0), 1e-4);
        assertArrayEquals(new double[] {104, 106}, here.get(1), 1e-4);
        assertArrayEquals(new double[] {100 + 22 / 3.0, 100 + 28 / 3.0}, here.get(2), 1e-4);
        for (int i = 0; i < here.size(); i++)
        {
            assertArrayEquals(here.get(i), aStepOn.get(i), 0);
        }
    }

    /**
     * A player standing between the camera and the line hides the pieces behind them, and
     * none in front, even where their outline covers those on the canvas.
     */
    @Test
    public void aPieceBehindSomethingNearerTheCameraIsLeftOut()
    {
        // The camera over (60, 0); someone at (63.5, 20.5), their outline over the line from 15 to 30 tiles up.
        Occlusion occlusion = new Occlusion(60, 0, Collections.singletonList(
            new Occluder(new Rectangle(630, 150, 20, 150), 63.5, 20.5)));

        List<double[]> drawn = pieces(whole(LOCKED_EAST, Kind.LOCKED, 60, 30, FLAT, occlusion));

        boolean inFront = false;
        for (double[] piece : drawn)
        {
            double middle = (piece[0] + piece[1]) / 20;
            assertFalse("nothing drawn behind them: " + middle, middle > 20.5 && middle < 30);
            inFront |= middle > 15 && middle < 20.3;
        }
        assertTrue("the line in front of them is drawn over them", inFront);
        assertTrue("and again past them", drawn.stream().anyMatch(piece -> piece[0] == 300));
    }

    @Test
    public void somethingFartherFromTheCameraHidesNothing()
    {
        Occlusion occlusion = new Occlusion(60, 0, Collections.singletonList(
            new Occluder(new Rectangle(630, 150, 20, 150), 64, 50)));

        List<double[]> path = segments(whole(LOCKED_EAST, Kind.LOCKED, 60, 30, FLAT, occlusion));

        assertEquals("one unbroken line", 1, path.stream().filter(s -> s[0] == PathIterator.SEG_MOVETO).count());
    }

    /** Only the outline itself hides anything, not the box around it. */
    @Test
    public void anOutlineHidesOnlyWhatItCovers()
    {
        Polygon triangle = new Polygon(new int[] {600, 700, 600}, new int[] {100, 100, 200}, 3);
        Occlusion occlusion = new Occlusion(60, 0, Collections.singletonList(new Occluder(triangle, 60, 5)));

        assertTrue(occlusion.hides(610, 110, 64, 20));
        assertFalse("in its box, outside it", occlusion.hides(690, 190, 64, 20));
        assertFalse("nearer the camera than it", occlusion.hides(610, 110, 60, 2));
        assertFalse(Occlusion.NONE.hides(610, 110, 64, 20));
    }

    /** The fog lies on the ground, under whatever stands in it or in front of it. */
    @Test
    public void theFogIsNotDrawnOverWhatStandsInIt()
    {
        Palette palette = Palette.defaults();
        Occlusion occlusion = new Occlusion(60, 0, Collections.singletonList(
            new Occluder(new Rectangle(645, 300, 10, 100), 65.5, 35.5)));

        BufferedImage image = draw(Collections.singletonList(LOCKED_EAST), FateLockedConfig.ChunkBorders.OFF, true,
            occlusion);

        assertFalse("not over them", near(image.getRGB(650, 350), palette.lockedShade()));
        assertTrue("beside them", near(image.getRGB(650, 450), palette.lockedShade()));
        assertTrue(near(image.getRGB(642, 350), palette.lockedShade()));
    }

    /**
     * Only what stands between the camera and the lines is looked at: the tiles on the way from
     * each piece to below the camera, two either side, and the fog's with the fog on.
     */
    @Test
    public void theCorridorRunsFromTheLinesToTheCamera()
    {
        List<Run> runs = Collections.singletonList(LOCKED_EAST);
        boolean[][] lines = ChunkBorderRenderer.corridor(runs, true, false, 60, 30, SIZE, SIZE, 60, 0, FLAT);

        assertTrue("between the line and the camera", lines[62][10]);
        assertTrue("two aside of the way", lines[59][30] && lines[66][30]);
        assertFalse(lines[58][30]);
        assertFalse("behind the line", lines[67][30] || lines[80][30]);
        assertFalse("far aside", lines[30][30]);

        boolean[][] fog = ChunkBorderRenderer.corridor(runs, false, true, 60, 30, SIZE, SIZE, 60, 0, FLAT);
        assertTrue("the fog's own tiles", fog[67][30] && fog[62][10]);

        ChunkBorderRenderer.Projector behind = (x, y) -> y > 40 ? null : FLAT.project(x, y);
        assertFalse("none from what the camera can't see",
            ChunkBorderRenderer.corridor(runs, true, true, 60, 30, SIZE, SIZE, 60, 0, behind)[64][50]);
        assertTrue(lines[64][50]);

        assertFalse("none with nothing drawn", any(
            ChunkBorderRenderer.corridor(runs, false, false, 60, 30, SIZE, SIZE, 60, 0, FLAT)));
        assertFalse("none for the faint lines", any(ChunkBorderRenderer.corridor(
            Collections.singletonList(new Run(true, 64, 0, 104, Kind.PLAIN, 0)), true, true, 60, 30, SIZE, SIZE, 60,
            0, FLAT)));
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

        BufferedImage lockedEdges = draw(runs, FateLockedConfig.ChunkBorders.LOCKED_EDGES, false, Occlusion.NONE);
        assertTrue(has(lockedEdges, palette.lockedEdge()));
        assertTrue("over the dark underlay", has(lockedEdges, Palette.UNDERLAY));
        assertFalse(has(lockedEdges, Palette.PLAIN_EDGE));
        assertFalse(has(lockedEdges, palette.lockedShade()));

        BufferedImage allEdges = draw(runs, FateLockedConfig.ChunkBorders.ALL_EDGES, false, Occlusion.NONE);
        assertTrue(has(allEdges, Palette.PLAIN_EDGE));
        assertTrue(has(allEdges, palette.lockedEdge()));

        BufferedImage fogOnly = draw(runs, FateLockedConfig.ChunkBorders.OFF, true, Occlusion.NONE);
        assertTrue(has(fogOnly, palette.lockedShade()));
        assertFalse(has(fogOnly, palette.lockedEdge()));
        assertFalse(has(fogOnly, Palette.PLAIN_EDGE));
    }

    /** The gaps between the dashes show the underlay, and the dashes alone the edge's colour. */
    @Test
    public void theUnderlayRunsOnThroughTheGaps()
    {
        Palette palette = Palette.defaults();
        // Thirty pixels up a tile, so each gap is several whole pixels.
        ChunkBorderRenderer.Projector tall = (x, y) -> new Point2D.Double(x * 10, y * 30);
        BufferedImage image = new BufferedImage(700, 700, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        ChunkBorderRenderer.draw(graphics, Collections.singletonList(LOCKED_EAST),
            FateLockedConfig.ChunkBorders.LOCKED_EDGES, false, palette, 60, 10, SIZE, SIZE, tall, GROUND,
            Occlusion.NONE);
        graphics.dispose();

        assertTrue("a dash", near(image.getRGB(640, 45), palette.lockedEdge()));
        assertTrue("a gap", near(image.getRGB(640, 40), Palette.UNDERLAY));
        assertTrue("a corner", near(image.getRGB(640, 30), Palette.UNDERLAY));
    }

    private static GeneralPath whole(Run run, Kind kind, int playerX, int playerY,
        ChunkBorderRenderer.Projector projector, Occlusion occlusion)
    {
        return ChunkBorderRenderer.lines(Collections.singletonList(run), kind, playerX, playerY, SIZE, SIZE, projector,
            GROUND, occlusion).whole;
    }

    private static BufferedImage draw(List<Run> runs, FateLockedConfig.ChunkBorders borders, boolean fog,
        Occlusion occlusion)
    {
        BufferedImage image = new BufferedImage(1100, 1100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        ChunkBorderRenderer.draw(graphics, runs, borders, fog, Palette.defaults(), 60, 60, SIZE, SIZE, FLAT, GROUND,
            occlusion);
        graphics.dispose();
        return image;
    }

    private static boolean any(boolean[][] tiles)
    {
        for (boolean[] column : tiles)
        {
            for (boolean tile : column)
            {
                if (tile)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether any pixel is this colour, give or take a unit of compositing. */
    private static boolean has(BufferedImage image, Color colour)
    {
        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                if (near(image.getRGB(x, y), colour))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean near(int argb, Color colour)
    {
        Color pixel = new Color(argb, true);
        return Math.abs(pixel.getRed() - colour.getRed()) <= 1 && Math.abs(pixel.getGreen() - colour.getGreen()) <= 1
            && Math.abs(pixel.getBlue() - colour.getBlue()) <= 1 && Math.abs(pixel.getAlpha() - colour.getAlpha()) <= 1;
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

    /** Each drawn piece of an upright line, as {from, to} up the canvas. */
    private static List<double[]> pieces(GeneralPath path)
    {
        List<double[]> pieces = new ArrayList<>();
        List<double[]> segments = segments(path);
        for (int i = 1; i < segments.size(); i++)
        {
            if (segments.get(i)[0] == PathIterator.SEG_LINETO)
            {
                pieces.add(new double[] {segments.get(i - 1)[2], segments.get(i)[2]});
            }
        }
        return pieces;
    }

    /** Each dash of an upright line wholly between two heights on the canvas, as {from, to}. */
    private static List<double[]> dashes(GeneralPath path, double from, double to)
    {
        List<double[]> dashes = new ArrayList<>();
        List<double[]> segments = segments(path);
        int i = 0;
        while (i < segments.size())
        {
            double start = segments.get(i++)[2];
            double end = start;
            while (i < segments.size() && segments.get(i)[0] == PathIterator.SEG_LINETO)
            {
                end = segments.get(i++)[2];
            }
            if (start >= from && end <= to)
            {
                dashes.add(new double[] {start, end});
            }
        }
        return dashes;
    }
}
