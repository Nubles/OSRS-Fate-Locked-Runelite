package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
