package com.fatelocked.ui;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;

/**
 * A quiet cross that dismisses one row: muted until the pointer is on it. It is drawn rather than
 * written, so it looks the same whatever the font has.
 */
public class DismissButton extends JButton
{
    public static final String LABEL = "Dismiss";
    private static final int SIZE = 14;
    /** The cross's half-width, from its centre. */
    private static final int ARM = 3;

    public DismissButton()
    {
        setToolTipText(LABEL);
        getAccessibleContext().setAccessibleName(LABEL);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(SIZE, SIZE);
    }

    @Override
    public Dimension getMinimumSize()
    {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getModel().isRollover() || hasFocus() ? Palette.TEXT : Palette.TEXT_MUTED);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int x = getWidth() / 2;
            int y = getHeight() / 2;
            g2.drawLine(x - ARM, y - ARM, x + ARM, y + ARM);
            g2.drawLine(x - ARM, y + ARM, x + ARM, y - ARM);
        }
        finally
        {
            g2.dispose();
        }
    }
}
