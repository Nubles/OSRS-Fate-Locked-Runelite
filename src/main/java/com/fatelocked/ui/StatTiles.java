package com.fatelocked.ui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.plaf.basic.BasicGraphicsUtils;

/** A row of counts, each a number in its status colour above a word. */
public class StatTiles extends JPanel
{
    private static final int TILE_HEIGHT = 38;
    private static final int ART_HEIGHT = 30;

    private final IconSource icons;
    private Palette palette = Palette.defaults();

    public StatTiles()
    {
        this(IconSource.NONE);
    }

    public StatTiles(IconSource icons)
    {
        super(new GridLayout(1, 0, Space.ROW, 0));
        this.icons = icons;
        setOpaque(false);
    }

    public void setPalette(Palette palette)
    {
        this.palette = palette;
        repaint();
    }

    /** Replaces the tiles. */
    public void show(Tile... tiles)
    {
        removeAll();
        for (Tile tile : tiles)
        {
            add(new TileView(tile));
        }
        revalidate();
        repaint();
    }

    /** One count: its value, its word and its tone, and optionally its art. */
    public static final class Tile
    {
        final int value;
        final String label;
        final Palette.Tone tone;
        final Art art;

        public Tile(int value, String label, Palette.Tone tone)
        {
            this(value, label, tone, null);
        }

        public Tile(int value, String label, Palette.Tone tone, Art art)
        {
            this.value = value;
            this.label = label;
            this.tone = tone;
            this.art = art;
        }
    }

    private final class TileView extends JComponent
    {
        private final Tile tile;
        private BufferedImage image;

        TileView(Tile tile)
        {
            this.tile = tile;
            setToolTipText(tile.value + " " + tile.label);
            if (tile.art != null)
            {
                icons.load(tile.art, loaded -> {
                    image = ArtSlot.trim(loaded);
                    repaint();
                });
            }
        }

        @Override
        public Dimension getPreferredSize()
        {
            return new Dimension(0, TILE_HEIGHT + (tile.art == null ? 0 : ART_HEIGHT));
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            try
            {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Palette.RAISED);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), Card.ARC, Card.ARC);

                String value = String.valueOf(tile.value);
                FontMetrics big = getFontMetrics(Type.title());
                FontMetrics small = getFontMetrics(Type.small());
                int artSpace = tile.art == null ? 0 : ART_HEIGHT;
                if (image != null)
                {
                    int h = Math.min(image.getHeight(), ART_HEIGHT - 4);
                    int w = image.getWidth() * h / image.getHeight();
                    g2.drawImage(image, (getWidth() - w) / 2, 4 + (ART_HEIGHT - 4 - h) / 2, w, h, null);
                }
                int top = artSpace + (getHeight() - artSpace - big.getHeight() - small.getHeight()) / 2;
                g2.setFont(Type.title());
                g2.setColor(tile.value == 0 ? Palette.TEXT_MUTED : palette.text(tile.tone));
                BasicGraphicsUtils.drawString(this, g2, value,
                    (getWidth() - Draw.width(this, big, value)) / 2f, top + big.getAscent());
                g2.setFont(Type.small());
                g2.setColor(Palette.TEXT_MUTED);
                BasicGraphicsUtils.drawString(this, g2, tile.label,
                    (getWidth() - Draw.width(this, small, tile.label)) / 2f,
                    top + big.getHeight() + small.getAscent());
            }
            finally
            {
                g2.dispose();
            }
        }
    }
}
