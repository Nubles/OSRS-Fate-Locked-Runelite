package com.fatelocked.ui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * A line inside a card that opens and closes what's under it: a chevron, OSRS art, a title
 * and a note on the right, such as a count. While it's closed, an optional summary sits
 * under the title, so a closed line still says what it holds.
 */
public class Fold extends JPanel
{
    /** How far a fold inside another is set in. */
    public static final int INDENT = 10;

    private final Chevron chevron = new Chevron();
    private final ArtSlot art = new ArtSlot(Space.ICON);
    private final JLabel note = new JLabel();
    private final JPanel header = new JPanel(new BorderLayout(Space.ICON_GAP, 0));
    private final JPanel summary = new JPanel(new BorderLayout());
    private final JPanel body = new JPanel(new Stack(Space.ROW));
    private final List<Consumer<Boolean>> listeners = new ArrayList<>();
    private boolean open;

    public Fold(String title, int indent)
    {
        super(new BorderLayout(0, 2));
        setOpaque(false);
        setBorder(new EmptyBorder(0, indent, 0, 0));

        JPanel lead = new JPanel(new BorderLayout(Space.ICON_GAP, 0));
        lead.setOpaque(false);
        lead.add(chevron, BorderLayout.WEST);
        lead.add(art, BorderLayout.CENTER);
        art.setVisible(false);
        TextBlock name = new TextBlock(Type.body(), Palette.TEXT, 1, ItemRow.TEXT_WIDTH);
        name.setText(title);
        note.setFont(Type.small());
        note.setForeground(Palette.TEXT_MUTED);
        header.setOpaque(false);
        header.setBackground(Palette.HOVER);
        header.setBorder(new EmptyBorder(2, 0, 2, 0));
        header.add(lead, BorderLayout.WEST);
        header.add(name, BorderLayout.CENTER);
        header.add(note, BorderLayout.EAST);
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // The whole line answers, whichever part of it is under the pointer.
        MouseAdapter toggle = new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                click();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                header.setOpaque(true);
                header.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                header.setOpaque(false);
                header.repaint();
            }
        };
        for (JComponent part : new JComponent[] {header, lead, chevron, art, name, note})
        {
            part.addMouseListener(toggle);
        }

        summary.setOpaque(false);
        alignSummary();
        body.setOpaque(false);
        JPanel under = new JPanel(new Stack(0));
        under.setOpaque(false);
        under.add(summary);
        under.add(body);
        add(header, BorderLayout.NORTH);
        add(under, BorderLayout.CENTER);
        body.setVisible(false);
    }

    /** Where what it opens goes. */
    public JPanel body()
    {
        return body;
    }

    /** The art beside the title, or null for none. */
    public void setArt(BufferedImage image)
    {
        art.setImage(image);
        art.setVisible(image != null);
        alignSummary();
    }

    /** The art's slot, for art that arrives later. */
    public ArtSlot art()
    {
        art.setVisible(true);
        alignSummary();
        return art;
    }

    /** What a click on the line does: open or close it, and tell the listeners. */
    public void click()
    {
        setOpen(!open);
        for (Consumer<Boolean> listener : listeners)
        {
            listener.accept(open);
        }
    }

    /** The summary starts under the title, past the chevron and the art. */
    private void alignSummary()
    {
        int left = Chevron.SIZE + Space.ICON_GAP + (art.isVisible() ? Space.ICON + Space.ICON_GAP : 0);
        summary.setBorder(new EmptyBorder(0, left, 0, 0));
    }

    /** A short note at the right of the line, such as a count, or null. */
    public void setNote(String text)
    {
        note.setText(text == null ? "" : text);
    }

    /** What it holds, shown under the title while it's closed, or null. */
    public void setSummary(JComponent line)
    {
        summary.removeAll();
        if (line != null)
        {
            summary.add(line, BorderLayout.CENTER);
        }
        summary.setVisible(line != null && !open);
    }

    public boolean isOpen()
    {
        return open;
    }

    /** Opens or closes it, without telling the listeners: they hear only the player's clicks. */
    public void setOpen(boolean open)
    {
        this.open = open;
        body.setVisible(open);
        summary.setVisible(!open && summary.getComponentCount() > 0);
        chevron.repaint();
        revalidate();
        repaint();
    }

    /** Called with the new state each time the player opens or closes it. */
    public void onToggle(Consumer<Boolean> listener)
    {
        listeners.add(listener);
    }

    /** The drawn chevron: pointing right while closed, down while open. */
    private final class Chevron extends JComponent
    {
        static final int SIZE = 8;

        @Override
        public Dimension getPreferredSize()
        {
            return new Dimension(SIZE, Space.ICON);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            try
            {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int mid = getHeight() / 2;
                Path2D shape = new Path2D.Float();
                if (open)
                {
                    shape.moveTo(0, mid - 2);
                    shape.lineTo(7, mid - 2);
                    shape.lineTo(3.5f, mid + 2.5f);
                }
                else
                {
                    shape.moveTo(1, mid - 4);
                    shape.lineTo(1, mid + 4);
                    shape.lineTo(5.5f, mid);
                }
                shape.closePath();
                g2.setColor(Palette.TEXT_MUTED);
                g2.fill(shape);
            }
            finally
            {
                g2.dispose();
            }
        }
    }
}
