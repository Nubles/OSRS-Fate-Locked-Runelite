package com.fatelocked.badges;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Each badge holder's key, above their head and over where RuneLite's player names go. The
 * holders are found once a tick ({@link BadgeHolders}); a frame only places and draws them.
 */
public final class BadgeOverlay extends Overlay
{
    /** Where RuneLite's Player Indicators write a name over the head, in model height units. */
    static final int NAME_HEIGHT = 40;
    /** The key sits this many pixels above that point, clear of the name. */
    static final int LIFT = 15;

    private final BadgeHolders holders;
    private final BadgeIcons icons;

    BadgeOverlay(BadgeHolders holders, BadgeIcons icons)
    {
        this.holders = holders;
        this.icons = icons;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(Overlay.PRIORITY_LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        int count = holders.count();
        for (int i = 0; i < count; i++)
        {
            Player player = holders.player(i);
            BufferedImage key = icons.image(holders.tier(i));
            if (player == null || key == null)
            {
                continue;
            }
            Point at = player.getCanvasImageLocation(key, player.getLogicalHeight() + NAME_HEIGHT);
            if (at != null)
            {
                graphics.drawImage(key, at.getX(), at.getY() - LIFT, null);
            }
        }
        return null;
    }
}
