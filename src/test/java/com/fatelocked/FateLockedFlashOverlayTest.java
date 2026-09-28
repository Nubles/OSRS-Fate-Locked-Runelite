package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fatelocked.ui.Palette;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import org.junit.Before;
import org.junit.Test;

/** U20: the fade frames the view once, in the palette's locked colour, and is gone after it ends. */
public class FateLockedFlashOverlayTest
{
    private final Client client = mock(Client.class);
    private final FateLockedPlugin plugin = mock(FateLockedPlugin.class);
    /** The fade began at 5 s on the test's own clock. */
    private static final long BEGAN = 5_000_000_000L;
    private long now = BEGAN;
    private final FateLockedFlashOverlay overlay = new FateLockedFlashOverlay(client, plugin, () -> now);

    @Before
    public void setUp()
    {
        when(client.getCanvasWidth()).thenReturn(200);
        when(client.getCanvasHeight()).thenReturn(150);
        when(plugin.palette()).thenReturn(Palette.defaults());
    }

    @Test
    public void noFadeOrAnEndedOneDrawsNothing()
    {
        Graphics2D graphics = mock(Graphics2D.class);
        when(plugin.getLockedFadeAt()).thenReturn(FateLockedPlugin.NO_FADE);
        assertNull(overlay.render(graphics));
        when(plugin.getLockedFadeAt()).thenReturn(BEGAN);
        now = BEGAN + FlashFade.DURATION_MS * 1_000_000;
        assertNull(overlay.render(graphics));
        verifyNoInteractions(graphics);
    }

    @Test
    public void aFreshFadeFramesTheViewInThePalettesLockedColour()
    {
        Palette custom = Palette.of(Palette.Preset.CUSTOM, new Color(0, 128, 255, 110),
            new Color(255, 255, 0, 100), new Color(128, 0, 128, 110));
        when(plugin.palette()).thenReturn(custom);
        when(plugin.getLockedFadeAt()).thenReturn(BEGAN);
        BufferedImage image = new BufferedImage(200, 150, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        Composite before = graphics.getComposite();

        overlay.render(graphics);

        Color edge = new Color(image.getRGB(3, 75), true);
        assertTrue("the palette's locked colour, give or take compositing: " + edge,
            Math.abs(edge.getRed() - 128) <= 1 && edge.getGreen() <= 1 && Math.abs(edge.getBlue() - 128) <= 1);
        assertTrue("its strongest at the start: " + edge.getAlpha(), Math.abs(edge.getAlpha() - FlashFade.MAX_ALPHA) <= 1);
        assertEquals("the view itself is untouched", 0, image.getRGB(100, 75) >>> 24);
        assertSame("the composite is put back", before, graphics.getComposite());
        graphics.dispose();
    }
}
