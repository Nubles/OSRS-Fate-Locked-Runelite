package com.fatelocked.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/**
 * A square for a piece of OSRS art. It stays empty until the art arrives, since
 * sprites load from the game cache after the sidebar is built. Art larger than the
 * square is scaled down to fit; smaller art is drawn at its own size, centred.
 */
public class ArtSlot extends JComponent
{
    private final int size;
    private BufferedImage image;

    public ArtSlot(int size)
    {
        this.size = size;
        setOpaque(false);
    }

    public void setImage(BufferedImage image)
    {
        this.image = image;
        repaint();
    }

    public BufferedImage getImage()
    {
        return image;
    }

    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(size, size);
    }

    @Override
    public Dimension getMinimumSize()
    {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        if (image == null)
        {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try
        {
            int w = image.getWidth();
            int h = image.getHeight();
            if (w > size || h > size)
            {
                double scale = Math.min(size / (double) w, size / (double) h);
                w = (int) Math.round(w * scale);
                h = (int) Math.round(h * scale);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            }
            g2.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
        }
        finally
        {
            g2.dispose();
        }
    }
}
