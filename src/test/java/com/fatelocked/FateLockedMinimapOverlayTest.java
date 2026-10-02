package com.fatelocked;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.widgets.Widget;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

/** U3: the minimap draws the same scene as the game view, inside the minimap, for the rules' character. */
public class FateLockedMinimapOverlayTest
{
    private final Client client = mock(Client.class);
    private final FateLockedPlugin plugin = mock(FateLockedPlugin.class);
    private final FateLockedConfig config = mock(FateLockedConfig.class);
    private final Graphics2D graphics = mock(Graphics2D.class);
    private FateLockedMinimapOverlay overlay;
    private DecisionService mine;

    @Before
    public void setUp() throws Exception
    {
        FateLockedBundle mid = FateLockedBundle.loadFromJson(new Gson(),
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")));
        mine = DecisionService.create(RulesSnapshot.of(mid), "iron example", "iron example");
        when(config.drawMinimap()).thenReturn(true);
        when(plugin.decisions()).thenReturn(mine);
        when(client.getTopLevelWorldView()).thenReturn(mock(WorldView.class));
        overlay = new FateLockedMinimapOverlay(client, plugin, config);
    }

    /** The same widgets as the deprecated ComponentID draw areas, by their gameval names. */
    @Test
    public void theMinimapIsFoundInEveryLayout()
    {
        assertArrayEquals(new int[] {35913750, 10551326, 10747934}, FateLockedMinimapOverlay.MINIMAPS);
    }

    @Test
    public void theSettingOffOrAnotherCharacterDrawsNothing()
    {
        when(config.drawMinimap()).thenReturn(false);
        assertNull(overlay.render(graphics));

        when(config.drawMinimap()).thenReturn(true);
        when(plugin.decisions()).thenReturn(DecisionService.create(mine.rules(), "iron example", "someone else"));
        assertNull(overlay.render(graphics));

        verifyNoInteractions(graphics);
        verify(client, never()).getWidget(anyInt());
    }

    /** Fog and lines are drawn inside the minimap only, and the clip is left as it was found. */
    @Test
    public void theSceneIsDrawnInsideTheMinimap()
    {
        Palette palette = Palette.defaults();

        BufferedImage fogAndLockedEdges = draw(FateLockedConfig.ChunkBorders.OFF, true);
        assertTrue("locked land is shaded", near(fogAndLockedEdges.getRGB(800, 500), palette.lockedShade()));
        assertEquals("nothing past the minimap", 0, fogAndLockedEdges.getRGB(950, 500) >>> 24);
        assertTrue("locked edges show with the game view's lines off", has(fogAndLockedEdges, palette.lockedEdge()));
        assertFalse(has(fogAndLockedEdges, Palette.PLAIN_EDGE));

        BufferedImage allEdges = draw(FateLockedConfig.ChunkBorders.ALL_EDGES, false);
        assertTrue(has(allEdges, Palette.PLAIN_EDGE));
        assertFalse("no fog when it's off", has(allEdges, palette.lockedShade()));

        BufferedImage chunkGrid = draw(FateLockedConfig.ChunkBorders.CHUNK_GRID, false);
        assertTrue("the game view's chunk grid", has(chunkGrid, Palette.PLAIN_EDGE));
        assertFalse("without its dashes", has(chunkGrid, palette.lockedEdge()));
    }

    /** The fog follows its own setting here too, and the lines the game view's, as the player left them. */
    @Test
    public void theSettingsReachTheDrawing()
    {
        Widget minimap = mock(Widget.class);
        when(minimap.getBounds()).thenReturn(new Rectangle(500, 0, 400, 400));
        when(client.getWidget(anyInt())).thenReturn(minimap);
        ChunkLocator locator = mock(ChunkLocator.class);
        when(plugin.chunkLocator()).thenReturn(locator);
        when(locator.playerInScene()).thenReturn(new Located(null, null, 0, 60, 60));
        SceneEdges scene = eastHalfLocked();
        when(plugin.sceneEdges(eq(mine), any(), eq(0))).thenReturn(scene);
        when(plugin.palette()).thenReturn(Palette.defaults());
        when(config.chunkBorders()).thenReturn(FateLockedConfig.ChunkBorders.ALL_EDGES);

        try (MockedStatic<FateLockedMinimapOverlay> drawn = mockStatic(FateLockedMinimapOverlay.class))
        {
            when(config.shadeNearbyLocked()).thenReturn(false);
            overlay.render(graphics);
            drawn.verify(() -> FateLockedMinimapOverlay.draw(eq(graphics), any(), eq(scene),
                eq(FateLockedConfig.ChunkBorders.ALL_EDGES), eq(false), any(), eq(60), eq(60), anyInt(), anyInt(),
                any()));

            when(config.chunkBorders()).thenReturn(FateLockedConfig.ChunkBorders.OFF);
            when(config.shadeNearbyLocked()).thenReturn(true);
            overlay.render(graphics);
            drawn.verify(() -> FateLockedMinimapOverlay.draw(eq(graphics), any(), eq(scene),
                eq(FateLockedConfig.ChunkBorders.OFF), eq(true), any(), eq(60), eq(60), anyInt(), anyInt(), any()));
        }
    }

    /**
     * A tile is a few pixels on the minimap, so its dashes run a third of a chunk's side, not
     * a third of a tile; and nothing stands in front of anything there.
     */
    @Test
    public void theMinimapsDashesAreAChunksThird()
    {
        try (MockedStatic<ChunkBorderRenderer> renderer = mockStatic(ChunkBorderRenderer.class))
        {
            FateLockedMinimapOverlay.draw(graphics, new Rectangle(0, 0, 10, 10), eastHalfLocked(),
                FateLockedConfig.ChunkBorders.LOCKED_EDGES, false, Palette.defaults(), 60, 60, 104, 104,
                (x, y) -> null);
            renderer.verify(() -> ChunkBorderRenderer.draw(eq(graphics), any(), any(), eq(false), any(), eq(60),
                eq(60), eq(104), eq(104), any(), eq(ChunkBorderRenderer.MINIMAP_PERIOD),
                eq(ChunkBorderRenderer.Occlusion.NONE)));
        }
    }

    /** A scene whose east half is locked, with a chunk line at 64 tiles both ways. */
    static SceneEdges eastHalfLocked()
    {
        SceneEdges.Zone[][] zones = new SceneEdges.Zone[13][13];
        for (int zx = 0; zx < 13; zx++)
        {
            for (int zy = 0; zy < 13; zy++)
            {
                zones[zx][zy] = new SceneEdges.Zone(zx < 8 ? TintPolicy.Tint.UNLOCKED : TintPolicy.Tint.LOCKED,
                    new CanonicalChunk(50 + zx / 8, 50 + zy / 8));
            }
        }
        return SceneEdges.of(zones);
    }

    /** The minimap's scene over a flat projection. */
    private static BufferedImage draw(FateLockedConfig.ChunkBorders borders, boolean fog)
    {
        BufferedImage image = new BufferedImage(1100, 1100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        FateLockedMinimapOverlay.draw(graphics, new Rectangle(500, 0, 400, 1100), eastHalfLocked(), borders, fog,
            Palette.defaults(), 60, 60, 104, 104, (x, y) -> new Point2D.Double(x * 10, y * 10));
        assertNull("the clip is put back", graphics.getClip());
        graphics.dispose();
        return image;
    }

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

    /** Whether a pixel is this colour, give or take a unit of compositing. */
    private static boolean near(int argb, Color colour)
    {
        Color pixel = new Color(argb, true);
        return Math.abs(pixel.getRed() - colour.getRed()) <= 1 && Math.abs(pixel.getGreen() - colour.getGreen()) <= 1
            && Math.abs(pixel.getBlue() - colour.getBlue()) <= 1 && Math.abs(pixel.getAlpha() - colour.getAlpha()) <= 1;
    }

    /** With no minimap on screen, nothing is drawn and the scene isn't worked out. */
    @Test
    public void noMinimapShownDrawsNothing()
    {
        Widget hidden = mock(Widget.class);
        when(hidden.isHidden()).thenReturn(true);
        when(client.getWidget(anyInt())).thenReturn(hidden);

        assertNull(overlay.render(graphics));

        verifyNoInteractions(graphics);
        verify(plugin, never()).sceneEdges(any(), any(), anyInt());
    }
}
