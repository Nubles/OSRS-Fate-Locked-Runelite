package com.fatelocked.ui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicGraphicsUtils;

/** A status in a word, in its colour on a faint fill of the same colour. */
public class StatusPill extends JComponent
{
    private static final int PAD_X = 6;

    private String word = "";
    private Palette.Tone tone = Palette.Tone.NEUTRAL;
    private Palette palette = Palette.defaults();

    public StatusPill()
    {
        setFont(Type.small());
        setOpaque(false);
    }

    public StatusPill(String word, Palette.Tone tone)
    {
        this();
        show(word, tone);
    }

    public void show(String word, Palette.Tone tone)
    {
        this.word = word == null ? "" : word;
        this.tone = tone;
        setVisible(!this.word.isEmpty());
        revalidate();
        repaint();
    }

    public void setPalette(Palette palette)
    {
        this.palette = palette;
        repaint();
    }

    public String getWord()
    {
        return word;
    }

    public Palette.Tone getTone()
    {
        return tone;
    }

    @Override
    public Dimension getPreferredSize()
    {
        FontMetrics metrics = getFontMetrics(getFont());
        return new Dimension(Draw.width(this, metrics, word) + 2 * PAD_X,
            metrics.getHeight() + 2);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int height = getHeight();
            g2.setColor(palette.pill(tone));
            g2.fillRoundRect(0, 0, getWidth(), height, height, height);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setFont(getFont());
            g2.setColor(palette.text(tone));
            FontMetrics metrics = getFontMetrics(getFont());
            int y = (height - metrics.getHeight()) / 2 + metrics.getAscent();
            BasicGraphicsUtils.drawString(this, g2, word, PAD_X, y);
        }
        finally
        {
            g2.dispose();
        }
    }
}
