package com.fatelocked.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicGraphicsUtils;

/**
 * Text that wraps to its width, in at most a set number of lines. A line that is cut
 * ends in an ellipsis, and the tooltip then holds the whole text.
 *
 * <p>Its preferred height depends on its width: before the first layout it wraps to
 * the width it was given, and a new width re-lays the sidebar out.
 */
public class TextBlock extends JComponent
{
    private static final String ELLIPSIS = "…";

    private final int maxLines;
    private final int widthHint;
    private String text = "";
    private List<String> lines = Collections.emptyList();
    private int linesWidth = -1;
    private boolean cut;

    /**
     * @param maxLines  the most lines to show, or 0 for no limit
     * @param widthHint the width to wrap to before the first layout
     */
    public TextBlock(Font font, Color colour, int maxLines, int widthHint)
    {
        this.maxLines = maxLines;
        this.widthHint = widthHint;
        setFont(font);
        setForeground(colour);
        setOpaque(false);
    }

    public void setText(String text)
    {
        String next = text == null ? "" : text;
        if (next.equals(this.text))
        {
            return;
        }
        this.text = next;
        linesWidth = -1;
        revalidate();
        repaint();
    }

    public String getText()
    {
        return text;
    }

    /** The lines as drawn at the current width, for tests. */
    public List<String> lines()
    {
        return wrap(currentWidth());
    }

    @Override
    public String getToolTipText()
    {
        wrap(currentWidth());
        return cut ? text : super.getToolTipText();
    }

    @Override
    public Dimension getPreferredSize()
    {
        if (isPreferredSizeSet())
        {
            return super.getPreferredSize();
        }
        return new Dimension(widthHint, wrap(currentWidth()).size() * lineHeight());
    }

    @Override
    public void setBounds(int x, int y, int width, int height)
    {
        boolean resized = width != getWidth();
        super.setBounds(x, y, width, height);
        if (resized && !text.isEmpty())
        {
            revalidate();
        }
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        try
        {
            g2.setFont(getFont());
            g2.setColor(getForeground());
            FontMetrics metrics = getFontMetrics(getFont());
            int y = metrics.getAscent();
            for (String line : wrap(getWidth()))
            {
                BasicGraphicsUtils.drawString(this, g2, line, 0, y);
                y += lineHeight();
            }
        }
        finally
        {
            g2.dispose();
        }
    }

    private int currentWidth()
    {
        return getWidth() > 0 ? getWidth() : widthHint;
    }

    private int lineHeight()
    {
        return getFontMetrics(getFont()).getHeight();
    }

    private List<String> wrap(int width)
    {
        if (width == linesWidth)
        {
            return lines;
        }
        FontMetrics metrics = getFontMetrics(getFont());
        List<String> all = new ArrayList<>();
        for (String paragraph : text.split("\n", -1))
        {
            wrapParagraph(paragraph, width, metrics, all);
        }
        if (text.isEmpty())
        {
            all.clear();
        }
        cut = maxLines > 0 && all.size() > maxLines;
        if (cut)
        {
            List<String> kept = new ArrayList<>(all.subList(0, maxLines));
            kept.set(maxLines - 1, withEllipsis(kept.get(maxLines - 1), width, metrics));
            all = kept;
        }
        lines = Collections.unmodifiableList(all);
        linesWidth = width;
        return lines;
    }

    private void wrapParagraph(String paragraph, int width, FontMetrics metrics, List<String> out)
    {
        StringBuilder line = new StringBuilder();
        for (String word : paragraph.split(" "))
        {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fits(candidate, width, metrics))
            {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (line.length() > 0)
            {
                out.add(line.toString());
                line.setLength(0);
            }
            // A word wider than the line breaks where it must.
            String rest = word;
            while (!fits(rest, width, metrics) && rest.length() > 1)
            {
                int end = rest.length() - 1;
                while (end > 1 && !fits(rest.substring(0, end), width, metrics))
                {
                    end--;
                }
                out.add(rest.substring(0, end));
                rest = rest.substring(end);
            }
            line.append(rest);
        }
        out.add(line.toString());
    }

    private String withEllipsis(String line, int width, FontMetrics metrics)
    {
        String base = line;
        while (!base.isEmpty() && !fits(base + ELLIPSIS, width, metrics))
        {
            base = base.substring(0, base.length() - 1);
        }
        return base.replaceAll("[\\s,;:.]+$", "") + ELLIPSIS;
    }

    private boolean fits(String candidate, int width, FontMetrics metrics)
    {
        return Draw.width(this, metrics, candidate) <= width;
    }
}
