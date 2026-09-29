package com.fatelocked.ui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicGraphicsUtils;

/**
 * The sidebar's one kind of section: a card that opens and closes under a one-line
 * header with a title, an optional count and an optional control on the right.
 */
public class Section extends JPanel
{
    private final Header header;
    private final JPanel trailingSlot = new JPanel(new BorderLayout());
    private final JPanel body = new JPanel(new Stack(Space.ROW));
    private final List<Consumer<Boolean>> listeners = new ArrayList<>();
    private boolean expanded;

    public Section(String title, boolean expanded)
    {
        super(new BorderLayout());
        setOpaque(false);
        header = new Header(title);
        trailingSlot.setOpaque(false);
        trailingSlot.setBorder(new EmptyBorder(0, 0, 0, Space.PAD));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(header, BorderLayout.CENTER);
        top.add(trailingSlot, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(0, Space.PAD, Space.PAD, Space.PAD));
        add(body, BorderLayout.CENTER);
        this.expanded = !expanded;
        setExpanded(expanded);
    }

    /** Where the section's content goes. */
    public JPanel body()
    {
        return body;
    }

    public String getTitle()
    {
        return header.title;
    }

    /** A short count or state after the title, such as "3" or "Paused", or null. */
    public void setCount(String count)
    {
        header.count = count;
        header.repaint();
    }

    /** The count or state shown after the title, or null. */
    public String getCount()
    {
        return header.count;
    }

    /** A control at the right of the header, such as a toggle, or null. */
    public void setTrailing(JComponent control)
    {
        trailingSlot.removeAll();
        if (control != null)
        {
            trailingSlot.add(control, BorderLayout.CENTER);
        }
        trailingSlot.revalidate();
    }

    public void setExpanded(boolean expanded)
    {
        if (this.expanded == expanded)
        {
            return;
        }
        this.expanded = expanded;
        body.setVisible(expanded);
        revalidate();
        repaint();
        for (Consumer<Boolean> listener : listeners)
        {
            listener.accept(expanded);
        }
    }

    public boolean isExpanded()
    {
        return expanded;
    }

    public void onToggle(Consumer<Boolean> listener)
    {
        listeners.add(listener);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), Card.ARC, Card.ARC);
            g2.setColor(Palette.CARD);
            g2.fill(shape);
            if (header.hover)
            {
                g2.clip(shape);
                g2.setColor(Palette.HOVER);
                g2.fillRect(0, 0, getWidth(), header.getHeight());
            }
        }
        finally
        {
            g2.dispose();
        }
    }

    /** The clickable title row. */
    private final class Header extends JComponent
    {
        private static final int HEADER_HEIGHT = 26;
        private static final int CHEVRON = 7;

        private final String title;
        private String count;
        private boolean hover;

        Header(String title)
        {
            this.title = title;
            setFont(Type.title());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    setExpanded(!expanded);
                }

                @Override
                public void mouseEntered(MouseEvent e)
                {
                    hover = true;
                    Section.this.repaint();
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    hover = false;
                    Section.this.repaint();
                }
            });
        }

        @Override
        public Dimension getPreferredSize()
        {
            return new Dimension(0, HEADER_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            try
            {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int mid = getHeight() / 2;
                int x = Space.PAD;
                Path2D chevron = new Path2D.Float();
                if (expanded)
                {
                    chevron.moveTo(x, mid - 2);
                    chevron.lineTo(x + CHEVRON, mid - 2);
                    chevron.lineTo(x + CHEVRON / 2f, mid + 2.5f);
                }
                else
                {
                    chevron.moveTo(x + 1, mid - 4);
                    chevron.lineTo(x + 1, mid + 4);
                    chevron.lineTo(x + 5.5f, mid);
                }
                chevron.closePath();
                g2.setColor(Palette.TEXT_MUTED);
                g2.fill(chevron);

                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                FontMetrics metrics = getFontMetrics(getFont());
                int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                int textX = x + CHEVRON + Space.ICON_GAP;
                g2.setFont(getFont());
                g2.setColor(Palette.TITLE);
                BasicGraphicsUtils.drawString(this, g2, title, textX, baseline);
                if (count != null && !count.isEmpty())
                {
                    FontMetrics small = getFontMetrics(Type.small());
                    g2.setFont(Type.small());
                    g2.setColor(Palette.TEXT_MUTED);
                    int countX = getWidth() - Space.PAD - Draw.width(this, small, count);
                    int countBaseline = (getHeight() - small.getHeight()) / 2 + small.getAscent();
                    BasicGraphicsUtils.drawString(this, g2, count, countX, countBaseline);
                }
            }
            finally
            {
                g2.dispose();
            }
        }
    }
}
