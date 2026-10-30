package com.fatelocked;

import com.fatelocked.rules.Trust;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.TierMarks;
import com.fatelocked.ui.Type;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The Fate orb (plugin vision idea 4): Fate Points drawn like the minimap's HP, Prayer and Run
 * orbs, a number box hanging off a round orb. The ring fills toward the Pity Key and glows
 * when it's close, a sync that brings more Fate sends "+1" rising into it, and the Keys held
 * sit inside. It only shows the pity timer fate already runs; it changes no odds. It sits with
 * the overlays by the minimap and moves like any other overlay (Alt and drag).
 */
public class FateLockedFateOrbOverlay extends Overlay
{
    /** Close to the Pity Key: the share of the way there where the ring turns red and glows. */
    static final double NEAR_PITY = 0.8;
    /** How long a "+1" takes to rise and fade, in milliseconds. */
    static final long GAIN_MILLIS = 1400;

    private static final int BOX_W = 34;
    private static final int BOX_H = 20;
    private static final int ORB = 34;
    private static final int RING = 4;
    private static final int RISE = 18;

    private final FateLockedPlugin plugin;
    private final FateLockedConfig config;
    private final LongSupplier clock;

    private int lastFate = -1;
    private int gain;
    private long gainAt;

    @Inject
    FateLockedFateOrbOverlay(FateLockedPlugin plugin, FateLockedConfig config)
    {
        this(plugin, config, System::nanoTime);
    }

    FateLockedFateOrbOverlay(FateLockedPlugin plugin, FateLockedConfig config, LongSupplier clock)
    {
        this.plugin = plugin;
        this.config = config;
        this.clock = clock;
        setPosition(OverlayPosition.TOP_RIGHT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.fateOrb() || plugin.decisions().trust() != Trust.TRUSTED) return null;
        FateLockedBundle.RunState run = plugin.getBundle().getState();
        if (run == null) return null;
        int fate = run.getFatePoints();
        long now = clock.getAsLong();
        noteGain(fate, now);

        Integer pity = run.getPityThreshold();
        double filled = pity == null || pity <= 0 ? 0 : Math.min(1, fate / (double) pity);
        boolean near = pity != null && pity > 0 && filled >= NEAR_PITY;

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int top = RISE;
        int boxY = top + (ORB - BOX_H) / 2;
        int orbX = BOX_W - 4;

        // The number box, as the game's orbs hang theirs on the left.
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, boxY, BOX_W, BOX_H);
        graphics.setColor(Palette.ORB_WELL);
        graphics.fillRect(1, boxY + 1, BOX_W - 2, BOX_H - 2);
        String count = String.valueOf(fate);
        TierMarks.shadowed(graphics, count, (BOX_W - 4 - TierMarks.width(graphics, count, Type.small())) / 2,
            boxY + BOX_H - 5, Type.small(), near ? Palette.ORB_RED : Palette.ORB_GREEN);

        // The orb: a ring that fills toward the Pity Key, glowing when it's close.
        if (near && config.fateOrbGlow())
        {
            double pulse = 0.5 + 0.5 * Math.sin(now / 1_000_000_000.0 * Math.PI * 1.4);
            Composite before = graphics.getComposite();
            graphics.setColor(Palette.ORB_GLOW);
            for (int spread = 4; spread >= 1; spread--)
            {
                graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    (float) ((30 + 40 * pulse) / spread / 255)));
                graphics.fill(new Ellipse2D.Double(orbX - spread, top - spread, ORB + 2 * spread, ORB + 2 * spread));
            }
            graphics.setComposite(before);
        }
        graphics.setColor(Color.BLACK);
        graphics.fill(new Ellipse2D.Double(orbX, top, ORB, ORB));
        graphics.setColor(Palette.ORB_RING);
        graphics.fill(new Ellipse2D.Double(orbX + 1, top + 1, ORB - 2, ORB - 2));
        if (filled > 0)
        {
            graphics.setColor(near ? Palette.ORB_FILL_NEAR : Palette.ORB_FILL);
            graphics.fill(new Arc2D.Double(orbX + 1, top + 1, ORB - 2, ORB - 2, 90, -360 * filled, Arc2D.PIE));
        }
        graphics.setColor(Color.BLACK);
        graphics.fill(new Ellipse2D.Double(orbX + RING, top + RING, ORB - 2 * RING, ORB - 2 * RING));
        graphics.setColor(Palette.ORB_CORE);
        graphics.fill(new Ellipse2D.Double(orbX + RING + 1, top + RING + 1, ORB - 2 * RING - 2, ORB - 2 * RING - 2));

        // The Keys held, inside the orb, in the game's stack yellow.
        String keys = String.valueOf(run.getKeys());
        TierMarks.shadowed(graphics, keys, orbX + (ORB - TierMarks.width(graphics, keys, Type.small())) / 2,
            top + ORB / 2 + 5, Type.small(), Palette.ORB_YELLOW);

        drawGain(graphics, orbX, top, now);
        graphics.setStroke(new BasicStroke(1f));
        return new Dimension(orbX + ORB + 4, top + ORB + 2);
    }

    /** Fate going up since the last frame starts a "+N"; going down (a Pity Key, a ritual) doesn't. */
    private void noteGain(int fate, long now)
    {
        if (lastFate >= 0 && fate > lastFate)
        {
            gain = fate - lastFate;
            gainAt = now;
        }
        lastFate = fate;
    }

    /** The "+N" still rising, or 0. */
    int risingGain()
    {
        return gain;
    }

    private void drawGain(Graphics2D graphics, int orbX, int top, long now)
    {
        if (gain <= 0) return;
        long elapsed = (now - gainAt) / 1_000_000;
        if (elapsed < 0 || elapsed >= GAIN_MILLIS)
        {
            gain = 0;
            return;
        }
        double done = elapsed / (double) GAIN_MILLIS;
        String text = "+" + gain;
        Composite before = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (1 - done * done)));
        int y = top + ORB / 2 - (int) (RISE * done);
        TierMarks.shadowed(graphics, text, orbX + (ORB - TierMarks.width(graphics, text, Type.small())) / 2, y,
            Type.small(), Palette.ORB_YELLOW);
        graphics.setComposite(before);
    }
}
