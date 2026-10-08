package com.fatelocked;

import com.fatelocked.rules.UnlockNews;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Type;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The banner when a sync brings something new (the owner's call, 8 Oct): "Fate unlocked" over
 * what opened, in the top third of the view, for a few seconds, then fading out once. It never
 * takes a click. Whether it shows is the Announce new unlocks setting's to decide.
 */
public class FateLockedUnlockOverlay extends Overlay
{
    /** How long the banner stays, then how long it takes to fade, in milliseconds. */
    static final long HOLD_MILLIS = 4000;
    static final long FADE_MILLIS = 1000;
    private static final BasicStroke BORDER = new BasicStroke(2f);
    private static final int PAD_X = 22;
    private static final int PAD_Y = 12;
    private static final int GAP = 4;
    static final String KICKER = "FATE UNLOCKED";
    static final String MAP_LINE = "Now open on your map";

    private final Client client;
    private final FateLockedPlugin plugin;
    private final LongSupplier clock;

    @Inject
    FateLockedUnlockOverlay(Client client, FateLockedPlugin plugin)
    {
        this(client, plugin, System::nanoTime);
    }

    FateLockedUnlockOverlay(Client client, FateLockedPlugin plugin, LongSupplier clock)
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
        long at = plugin.getUnlockShownAt();
        UnlockNews news = plugin.getUnlockNews();
        if (at == FateLockedPlugin.NO_FADE || news == null || news.isEmpty()) return null;
        int alpha = alpha((clock.getAsLong() - at) / 1_000_000);
        if (alpha == 0) return null;
        draw(graphics, news, alpha, client.getCanvasWidth(), client.getCanvasHeight());
        return null;
    }

    /** The banner's opacity this long after it began: full while it holds, then fading to none. */
    static int alpha(long elapsedMillis)
    {
        if (elapsedMillis < 0 || elapsedMillis >= HOLD_MILLIS + FADE_MILLIS) return 0;
        if (elapsedMillis < HOLD_MILLIS) return 255;
        return (int) (255 * (HOLD_MILLIS + FADE_MILLIS - elapsedMillis) / FADE_MILLIS);
    }

    static void draw(Graphics2D graphics, UnlockNews news, int alpha, int width, int height)
    {
        Font small = Type.small();
        Font big = Type.title();
        String headline = news.headline();
        String sub = news.getChunks().isEmpty() ? null : MAP_LINE;
        FontMetrics smallMetrics = graphics.getFontMetrics(small);
        FontMetrics bigMetrics = graphics.getFontMetrics(big);
        int inner = Math.max(smallMetrics.stringWidth(KICKER), bigMetrics.stringWidth(headline));
        if (sub != null) inner = Math.max(inner, smallMetrics.stringWidth(sub));
        int boxWidth = inner + 2 * PAD_X;
        int boxHeight = 2 * PAD_Y + smallMetrics.getHeight() + GAP + bigMetrics.getHeight()
            + (sub == null ? 0 : GAP + smallMetrics.getHeight());
        int x = (width - boxWidth) / 2;
        int y = height / 4 - boxHeight / 2;

        Composite before = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
        graphics.setColor(Palette.BANNER);
        graphics.fillRect(x, y, boxWidth, boxHeight);
        graphics.setColor(Palette.ACCENT);
        graphics.setStroke(BORDER);
        graphics.drawRect(x, y, boxWidth, boxHeight);

        int line = y + PAD_Y + smallMetrics.getAscent();
        graphics.setFont(small);
        graphics.drawString(KICKER, (width - smallMetrics.stringWidth(KICKER)) / 2, line);
        line += smallMetrics.getDescent() + GAP + bigMetrics.getAscent();
        graphics.setFont(big);
        graphics.setColor(Palette.TITLE);
        graphics.drawString(headline, (width - bigMetrics.stringWidth(headline)) / 2, line);
        if (sub != null)
        {
            line += bigMetrics.getDescent() + GAP + smallMetrics.getAscent();
            graphics.setFont(small);
            graphics.setColor(Palette.TEXT);
            graphics.drawString(sub, (width - smallMetrics.stringWidth(sub)) / 2, line);
        }
        graphics.setComposite(before);
    }
}
