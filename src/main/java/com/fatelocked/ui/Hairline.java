package com.fatelocked.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JComponent;

/** A one-pixel line between groups inside a card. */
public class Hairline extends JComponent
{
    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(0, 1);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        g.setColor(Palette.HAIRLINE);
        g.fillRect(0, 0, getWidth(), 1);
    }
}
