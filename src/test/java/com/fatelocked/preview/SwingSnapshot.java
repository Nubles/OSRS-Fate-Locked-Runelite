package com.fatelocked.preview;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/**
 * Paints a Swing component into an image with no display and no native peer.
 *
 * <p>Without a peer, {@code validate()} does nothing, so the tree is laid out by
 * hand, top-down. An HTML label learns its width only when it is painted, so the
 * layout runs a few times with a throwaway paint in between, as repeated paints
 * settle it in the client.
 */
public final class SwingSnapshot
{
    private static final int PASSES = 4;

    private SwingSnapshot()
    {
    }

    /**
     * Lays {@code component} out at {@code width} until HTML labels have wrapped,
     * and returns the height it then asks for.
     */
    public static int settle(JComponent component, int width)
    {
        int preferred = 0;
        for (int pass = 0; pass < PASSES; pass++)
        {
            invalidateTree(component);
            component.setSize(width, Math.max(1, component.getPreferredSize().height));
            layoutTree(component);
            BufferedImage scratch = new BufferedImage(width, Math.max(1, component.getHeight()),
                BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scratch.createGraphics();
            try
            {
                component.printAll(g);
            }
            finally
            {
                g.dispose();
            }
            preferred = component.getPreferredSize().height;
        }
        return preferred;
    }

    /**
     * Paints {@code component} at {@code width} by {@code height}; a height of 0
     * means the height it asks for once settled.
     */
    public static BufferedImage paint(JComponent component, int width, int height)
    {
        return paint(component, width, height, 1);
    }

    /**
     * Paints {@code component} laid out at {@code width} by {@code height}, as the client lays
     * it out, with {@code scale} image pixels to each of its own, as a high-density screen
     * shows it: text and shapes drawn that much finer, and the game's art that much bigger, pixel
     * for pixel. A height of 0 means the height it asks for once settled.
     */
    public static BufferedImage paint(JComponent component, int width, int height, int scale)
    {
        int preferred = settle(component, width);
        int h = height > 0 ? height : preferred;
        invalidateTree(component);
        component.setSize(width, h);
        layoutTree(component);
        BufferedImage image = new BufferedImage(width * scale, h * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.scale(scale, scale);
            component.printAll(g);
        }
        finally
        {
            g.dispose();
        }
        return image;
    }

    static void invalidateTree(Component c)
    {
        c.invalidate();
        if (c instanceof Container)
        {
            for (Component child : ((Container) c).getComponents())
            {
                invalidateTree(child);
            }
        }
    }

    static void layoutTree(Component c)
    {
        c.doLayout();
        if (c instanceof Container)
        {
            for (Component child : ((Container) c).getComponents())
            {
                layoutTree(child);
            }
        }
    }
}
