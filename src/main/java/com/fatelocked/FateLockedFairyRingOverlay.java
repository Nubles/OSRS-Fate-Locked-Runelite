package com.fatelocked;

import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Type;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * The fairy ring dial status: while a fairy ring's dials are open, one line, in the game's font
 * and shaded as the game shades its text, naming the code dialled and the tracker's decision
 * ({@link FairyRingDialLine}). It is placed by the dials' own bounds, read each frame: centred
 * above them where the game view leaves room (resizable mode), otherwise just inside their top
 * edge, so it covers neither the dials nor the travel log beside them. It only draws; it never
 * takes a click.
 */
class FateLockedFairyRingOverlay extends Overlay
{
    /** Pixels between the line and the dials' edge. */
    static final int GAP = 4;

    private final Client client;
    private final FateLockedPlugin plugin;
    private final FateLockedConfig config;

    @Inject
    FateLockedFairyRingOverlay(Client client, FateLockedPlugin plugin, FateLockedConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.fairyRingDialStatus()) return null;
        Rectangle dials = FairyRingDial.bounds(client);
        if (dials == null) return null;
        FairyRingDialLine.Line line = FairyRingDialLine.present(FairyRingDial.read(client), plugin.decisions());
        if (line == null) return null;
        Palette palette = plugin.palette();
        graphics.setFont(Type.body());
        FontMetrics metrics = graphics.getFontMetrics();
        int width = metrics.stringWidth(line.getText());
        int x = dials.x + (dials.width - width) / 2;
        OverlayUtil.renderTextLocation(graphics, new Point(x, baseline(dials, metrics.getAscent(),
            metrics.getDescent())), line.getText(), palette.text(line.getTone()));
        return null;
    }

    /**
     * Where the line's baseline goes for dials at these bounds: above them when there is room
     * above, otherwise inside their top edge.
     */
    static int baseline(Rectangle dials, int ascent, int descent)
    {
        int above = dials.y - GAP - descent;
        if (above - ascent >= 0) return above;
        return dials.y + GAP + ascent;
    }
}
