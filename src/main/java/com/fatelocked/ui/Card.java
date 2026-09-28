package com.fatelocked.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * A block of related content on the sidebar: the card colour, softly rounded, with an
 * optional bar down its left side in the colour of its status.
 */
public class Card extends JPanel
{
    static final int ARC = 6;
    static final int BAR = 3;

    private Color accent;

    /** A card that stacks its children, each at full width. */
    public Card()
    {
        this(new Stack(Space.ROW));
    }

    public Card(LayoutManager layout)
    {
        super(layout);
        setOpaque(false);
        updateBorder();
    }

    /** The bar down the left side, or null for none. */
    public void setAccent(Color accent)
    {
        this.accent = accent;
        updateBorder();
        repaint();
    }

    public Color getAccent()
    {
        return accent;
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), ARC, ARC);
            g2.setColor(Palette.CARD);
            g2.fill(shape);
            if (accent != null)
            {
                g2.clip(shape);
                g2.setColor(accent);
                g2.fillRect(0, 0, BAR, getHeight());
            }
        }
        finally
        {
            g2.dispose();
        }
    }

    private void updateBorder()
    {
        int left = Space.PAD + (accent == null ? 0 : BAR);
        setBorder(new EmptyBorder(Space.PAD, left, Space.PAD, Space.PAD));
    }
}
