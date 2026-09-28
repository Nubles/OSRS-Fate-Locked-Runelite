package com.fatelocked;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The fade when a locked area is entered from unlocked land (U20): a frame around the view in
 * the palette's locked colour, fading out once ({@link FlashFade}). It never pulses; the old
 * frame pulsed at about 2.5 Hz. Whether it shows is the locked-area alert's to decide.
 */
public class FateLockedFlashOverlay extends Overlay
{
    private static final int FRAME_THICKNESS = 14;
    private static final BasicStroke FRAME = new BasicStroke(FRAME_THICKNESS);

    private final Client client;
    private final FateLockedPlugin plugin;
    /** The monotonic clock the fade is timed on, in nanoseconds. */
    private final LongSupplier clock;

    @Inject
    FateLockedFlashOverlay(Client client, FateLockedPlugin plugin)
    {
        this(client, plugin, System::nanoTime);
    }

    FateLockedFlashOverlay(Client client, FateLockedPlugin plugin, LongSupplier clock)
    {
        this.client = client;
        this.plugin = plugin;
        this.clock = clock;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        long at = plugin.getLockedFadeAt();
        if (at == FateLockedPlugin.NO_FADE) return null;
        int alpha = FlashFade.alpha((clock.getAsLong() - at) / 1_000_000);
        if (alpha == 0) return null;
        draw(graphics, plugin.palette().flash(), alpha, client.getCanvasWidth(), client.getCanvasHeight());
        return null;
    }

    /** The frame, in this colour at this alpha, around a view this size. */
    static void draw(Graphics2D graphics, Color colour, int alpha, int width, int height)
    {
        Composite before = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
        graphics.setColor(colour);
        graphics.setStroke(FRAME);
        graphics.drawRect(FRAME_THICKNESS / 2, FRAME_THICKNESS / 2, width - FRAME_THICKNESS, height - FRAME_THICKNESS);
        graphics.setComposite(before);
    }
}
