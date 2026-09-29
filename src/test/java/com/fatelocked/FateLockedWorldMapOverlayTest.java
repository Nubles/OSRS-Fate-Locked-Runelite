package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.ui.Palette;
import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/** U18: the world map shows fog over locked land, inside the map only, with a tooltip for the chunk hovered. */
public class FateLockedWorldMapOverlayTest
{
    private static final Rectangle MAP = new Rectangle(0, 0, 700, 470);
    private static final Rectangle OVERVIEW = new Rectangle(500, 320, 200, 150);

    private final Client client = mock(Client.class);
    private final FateLockedPlugin plugin = mock(FateLockedPlugin.class);
    private final FateLockedConfig config = mock(FateLockedConfig.class);
    private final TooltipManager tooltips = mock(TooltipManager.class);
    private FateLockedWorldMapOverlay overlay;
    private FateLockedBundle mid;
    private DecisionService mine;
    private WorldMapProjection projection;

    @Before
    public void setUp() throws Exception
    {
        mid = FateLockedBundle.loadFromJson(new Gson(),
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")));
        mine = DecisionService.create(RulesSnapshot.of(mid), "iron example", "iron example");
        when(plugin.decisions()).thenReturn(mine);
        when(plugin.palette()).thenReturn(Palette.defaults());
        when(plugin.getBundle()).thenReturn(mid);
        when(config.worldMapMode()).thenReturn(FateLockedConfig.WorldMapMode.SHADING_TOOLTIP_CONTENTS);
        when(config.worldMapBorders()).thenReturn(FateLockedConfig.ChunkBorders.LOCKED_EDGES);
        Widget map = widget(MAP);
        Widget overview = widget(OVERVIEW);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(map);
        when(client.getWidget(InterfaceID.Worldmap.OVERVIEW_CONTAINER)).thenReturn(overview);
        WorldMap worldMap = mock(WorldMap.class);
        when(worldMap.getWorldMapZoom()).thenReturn(1.5f);
        when(worldMap.getWorldMapPosition()).thenReturn(new Point(3200, 3200));
        when(client.getWorldMap()).thenReturn(worldMap);
        projection = new WorldMapProjection(MAP, 1.5f, 3200, 3200);
        overlay = new FateLockedWorldMapOverlay(client, plugin, config, tooltips);
    }

    @Test
    public void lockedLandIsFogAndUnlockedLandIsClearInsideTheMapOnly()
    {
        BufferedImage image = render();
        CanonicalChunk locked = inView(WorldMapChunks.Fill.LOCKED);
        CanonicalChunk unlocked = inView(WorldMapChunks.Fill.UNLOCKED);

        assertTrue("locked land is fog", near(image.getRGB(middleX(locked), middleY(locked)),
            Palette.defaults().lockedShade()));
        assertEquals("unlocked land is clear", 0, image.getRGB(middleX(unlocked), middleY(unlocked)) >>> 24);
        for (int x = OVERVIEW.x; x < OVERVIEW.x + OVERVIEW.width; x++)
        {
            for (int y = OVERVIEW.y; y < OVERVIEW.y + OVERVIEW.height; y++)
            {
                assertEquals("nothing over the overview", 0, image.getRGB(x, y) >>> 24);
            }
        }
    }

    /** Worked out once per decision service, not in every frame. */
    @Test
    public void theMapIsWorkedOutOncePerRules()
    {
        DecisionService watched = org.mockito.Mockito.spy(mine);
        when(plugin.decisions()).thenReturn(watched);
        render();
        clearInvocations(watched);
        render();
        verify(watched, never()).mappedChunks();
    }

    /** A Chunked run's frontier has its own light fill. */
    @Test
    public void theFrontierHasItsOwnLightFill() throws Exception
    {
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("chunked-walk.bundle.json.gz"));
        com.google.gson.JsonElement bound = new Gson().fromJson(json, com.google.gson.JsonObject.class)
            .getAsJsonObject("rules").get("account");
        String account = bound == null || bound.isJsonNull() ? null : AccountBinding.normalize(bound.getAsString());
        DecisionService walking = DecisionService.create(
            RulesSnapshot.of(FateLockedBundle.loadFromJson(new Gson(), json)), account, account);
        CanonicalChunk frontier = walking.mappedChunks().stream()
            .filter(chunk -> WorldMapChunks.fill(walking, chunk) == WorldMapChunks.Fill.FRONTIER)
            .findFirst().orElseThrow(AssertionError::new);
        when(plugin.decisions()).thenReturn(walking);
        int centreX = (frontier.getCx() << 6) + 32;
        int centreY = (frontier.getCy() << 6) + 32;
        when(client.getWorldMap().getWorldMapPosition()).thenReturn(new Point(centreX, centreY));
        projection = new WorldMapProjection(MAP, 1.5f, centreX, centreY);

        BufferedImage image = render();

        assertTrue(near(image.getRGB(middleX(frontier), middleY(frontier)), Palette.defaults().frontierFill()));
    }

    /** World map borders off: the fog and the tooltip stay, and the dashed outline goes. */
    @Test
    public void bordersOffKeepTheShadingAndTheTooltipWithoutTheOutline()
    {
        CanonicalChunk locked = inView(WorldMapChunks.Fill.LOCKED);
        when(client.getMouseCanvasPosition()).thenReturn(new Point(middleX(locked), middleY(locked)));
        when(config.worldMapBorders()).thenReturn(FateLockedConfig.ChunkBorders.OFF);
        Graphics2D graphics = mock(Graphics2D.class);

        overlay.render(graphics);

        verify(graphics, org.mockito.Mockito.atLeastOnce()).fillRect(org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt());
        verify(graphics, never()).draw(org.mockito.ArgumentMatchers.any());
        verify(tooltips).add(org.mockito.ArgumentMatchers.any());
        assertTrue("locked land is still fog", near(render().getRGB(middleX(locked), middleY(locked)),
            Palette.defaults().lockedShade()));

        when(config.worldMapBorders()).thenReturn(FateLockedConfig.ChunkBorders.LOCKED_EDGES);
        drawn();
    }

    /** All edges: a faint line on every chunk edge, under the outline, as the map drew before Stage 3. */
    @Test
    public void allEdgesDrawsTheChunkGridUnderTheOutline()
    {
        when(config.worldMapBorders()).thenReturn(FateLockedConfig.ChunkBorders.ALL_EDGES);
        Graphics2D graphics = mock(Graphics2D.class);
        overlay.render(graphics);

        ArgumentCaptor<Shape> drawn = ArgumentCaptor.forClass(Shape.class);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(graphics);
        order.verify(graphics).setStroke(Palette.PLAIN_EDGE_STROKE);
        order.verify(graphics).setColor(Palette.PLAIN_EDGE);
        order.verify(graphics).draw(drawn.capture());
        order.verify(graphics).setStroke(Palette.UNDERLAY_STROKE);
        Shape grid = drawn.getValue();
        assertTrue("every chunk edge is more than the outline", segments(grid)
            > segments(FateLockedWorldMapOverlay.outlinePath(WorldMapModel.of(mine), projection)));

        Graphics2D again = mock(Graphics2D.class);
        overlay.render(again);
        ArgumentCaptor<Shape> next = ArgumentCaptor.forClass(Shape.class);
        verify(again, times(3)).draw(next.capture());
        assertSame("a map at rest reuses its grid", grid, next.getAllValues().get(0));
    }

    /** The separate lines a path is made of. */
    private static int segments(Shape shape)
    {
        int count = 0;
        for (java.awt.geom.PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next())
        {
            if (it.currentSegment(new double[6]) == java.awt.geom.PathIterator.SEG_MOVETO) count++;
        }
        return count;
    }

    /** The clip goes back as it was, for whatever RuneLite draws next. */
    @Test
    public void theClipIsPutBack()
    {
        BufferedImage image = new BufferedImage(MAP.width, MAP.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        Rectangle before = new Rectangle(5, 5, 600, 400);
        graphics.setClip(before);

        overlay.render(graphics);

        assertEquals(before, graphics.getClip());
        graphics.dispose();
    }

    @Test
    public void offOrAnotherCharacterDrawsNothing()
    {
        Graphics2D graphics = mock(Graphics2D.class);
        when(config.worldMapMode()).thenReturn(FateLockedConfig.WorldMapMode.OFF);
        overlay.render(graphics);

        when(config.worldMapMode()).thenReturn(FateLockedConfig.WorldMapMode.SHADING_TOOLTIP_CONTENTS);
        when(plugin.decisions()).thenReturn(DecisionService.create(mine.rules(), "iron example", "someone else"));
        overlay.render(graphics);

        verifyNoInteractions(graphics, tooltips);
    }

    /** The tooltip is for the chunk under the mouse, built once while it stays there, and never over the overview. */
    @Test
    public void theTooltipIsTheHoveredChunksAndIsBuiltOnce()
    {
        CanonicalChunk locked = inView(WorldMapChunks.Fill.LOCKED);
        when(client.getMouseCanvasPosition()).thenReturn(new Point(middleX(locked), middleY(locked)));
        render();
        render();

        ArgumentCaptor<Tooltip> shown = ArgumentCaptor.forClass(Tooltip.class);
        verify(tooltips, times(2)).add(shown.capture());
        assertEquals(WorldMapChunks.tooltip(mine, locked, mid.contentAt(locked, 4), Palette.defaults()),
            shown.getValue().getText());
        verify(plugin, times(1)).getBundle();

        CanonicalChunk unlocked = inView(WorldMapChunks.Fill.UNLOCKED);
        when(client.getMouseCanvasPosition()).thenReturn(new Point(middleX(unlocked), middleY(unlocked)));
        render();
        verify(tooltips, times(3)).add(shown.capture());
        assertEquals("the mouse moved on", WorldMapChunks.tooltip(mine, unlocked, mid.contentAt(unlocked, 4),
            Palette.defaults()), shown.getValue().getText());

        clearInvocations(tooltips);
        when(config.worldMapMode()).thenReturn(FateLockedConfig.WorldMapMode.SHADING);
        render();
        verify(tooltips, never()).add(org.mockito.ArgumentMatchers.any());
        when(config.worldMapMode()).thenReturn(FateLockedConfig.WorldMapMode.SHADING_TOOLTIP_CONTENTS);

        clearInvocations(tooltips);
        when(client.getMouseCanvasPosition()).thenReturn(new Point(600, 400));
        render();
        verify(tooltips, never()).add(org.mockito.ArgumentMatchers.any());
    }

    /** A9: a map at rest reuses its clip and outline; each thing they depend on works them out again. */
    @Test
    public void aMapAtRestReusesItsClipAndOutline()
    {
        Shape[] first = drawn();
        Shape[] again = drawn();
        assertSame("the clip", first[0], again[0]);
        assertSame("the outline", first[1], again[1]);

        WorldMap worldMap = client.getWorldMap();
        when(worldMap.getWorldMapPosition()).thenReturn(new Point(3264, 3200));
        Shape[] east = drawn();
        assertSame("panning keeps the clip", again[0], east[0]);
        assertNotSame("but not the outline", again[1], east[1]);
        when(worldMap.getWorldMapPosition()).thenReturn(new Point(3264, 3264));
        Shape[] north = drawn();
        assertNotSame(east[1], north[1]);
        when(worldMap.getWorldMapZoom()).thenReturn(2f);
        Shape[] zoomed = drawn();
        assertNotSame(north[1], zoomed[1]);

        Widget overview = client.getWidget(InterfaceID.Worldmap.OVERVIEW_CONTAINER);
        when(overview.isHidden()).thenReturn(true);
        Shape[] closed = drawn();
        assertNotSame("the overview closed", zoomed[0], closed[0]);
        assertSame(zoomed[1], closed[1]);
        Widget selector = widget(new Rectangle(0, 0, 150, 40));
        when(client.getWidget(InterfaceID.Worldmap.MAPLIST_BOX_GRAPHIC0)).thenReturn(selector);
        Shape[] selecting = drawn();
        assertNotSame("the surface selector opened", closed[0], selecting[0]);

        Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        when(map.getBounds()).thenReturn(new Rectangle(10, 0, 700, 470));
        Shape[] moved = drawn();
        assertNotSame("the map moved on the screen", selecting[0], moved[0]);
        assertNotSame(selecting[1], moved[1]);

        when(plugin.decisions()).thenReturn(DecisionService.create(mine.rules(), "iron example", "iron example"));
        Shape[] rules = drawn();
        assertSame(moved[0], rules[0]);
        assertNotSame("new rules, a new outline", moved[1], rules[1]);
    }

    /** The clip and the outline one frame was drawn with. */
    private Shape[] drawn()
    {
        Graphics2D graphics = mock(Graphics2D.class);
        overlay.render(graphics);
        ArgumentCaptor<Shape> clip = ArgumentCaptor.forClass(Shape.class);
        ArgumentCaptor<Shape> outline = ArgumentCaptor.forClass(Shape.class);
        verify(graphics).clip(clip.capture());
        verify(graphics, times(2)).draw(outline.capture());
        assertSame("the underlay and the edge are one outline", outline.getAllValues().get(0), outline.getValue());
        return new Shape[] {clip.getValue(), outline.getValue()};
    }

    private BufferedImage render()
    {
        BufferedImage image = new BufferedImage(MAP.width, MAP.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        overlay.render(graphics);
        graphics.dispose();
        return image;
    }

    /** A chunk in view, clear of the overview, with this fill. */
    private CanonicalChunk inView(WorldMapChunks.Fill fill)
    {
        for (int cx = projection.westChunk(); cx <= projection.eastChunk(); cx++)
        {
            for (int cy = projection.southChunk(); cy <= projection.northChunk(); cy++)
            {
                CanonicalChunk chunk = new CanonicalChunk(cx, cy);
                int x = middleX(chunk);
                int y = middleY(chunk);
                if (MAP.contains(x, y) && !OVERVIEW.contains(x, y) && WorldMapChunks.fill(mine, chunk) == fill)
                {
                    return chunk;
                }
            }
        }
        throw new AssertionError("no " + fill + " chunk in view");
    }

    private int middleX(CanonicalChunk chunk)
    {
        return (projection.lineX(chunk.getCx() << 6) + projection.lineX((chunk.getCx() + 1) << 6)) / 2;
    }

    private int middleY(CanonicalChunk chunk)
    {
        return (projection.lineY(chunk.getCy() << 6) + projection.lineY((chunk.getCy() + 1) << 6)) / 2;
    }

    private static Widget widget(Rectangle bounds)
    {
        Widget widget = mock(Widget.class);
        when(widget.getBounds()).thenReturn(bounds);
        assertNotNull(bounds);
        return widget;
    }

    /** Whether a pixel is this colour, give or take a unit of compositing. */
    private static boolean near(int argb, Color colour)
    {
        Color pixel = new Color(argb, true);
        return Math.abs(pixel.getRed() - colour.getRed()) <= 1 && Math.abs(pixel.getGreen() - colour.getGreen()) <= 1
            && Math.abs(pixel.getBlue() - colour.getBlue()) <= 1 && Math.abs(pixel.getAlpha() - colour.getAlpha()) <= 1;
    }
}
