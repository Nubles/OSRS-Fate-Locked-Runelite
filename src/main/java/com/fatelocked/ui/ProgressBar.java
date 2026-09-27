package com.fatelocked.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;

/** A slim bar: how much of the run is unlocked. */
public class ProgressBar extends JComponent
{
    private static final int BAR_HEIGHT = 4;

    private double fraction;

    public void setFraction(double fraction)
    {
        this.fraction = Math.max(0, Math.min(1, fraction));
        repaint();
    }

    public double getFraction()
    {
        return fraction;
    }

    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(0, BAR_HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Palette.TRACK);
            g2.fillRoundRect(0, 0, getWidth(), BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT);
            int filled = (int) Math.round(getWidth() * fraction);
            if (filled > 0)
            {
                g2.setColor(Palette.ACCENT);
                g2.fillRoundRect(0, 0, Math.max(filled, BAR_HEIGHT), BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT);
            }
        }
        finally
        {
            g2.dispose();
        }
    }
}
