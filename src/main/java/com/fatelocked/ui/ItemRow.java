package com.fatelocked.ui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;

/**
 * One thing in a list: its name and its status in a word, with the reason beneath
 * when there is one, wrapped to two lines at most.
 */
public class ItemRow extends JPanel
{
    /** The width a row's text wraps to before the first layout. */
    static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 2 * Space.EDGE - 2 * Space.PAD;

    private final ArtSlot art = new ArtSlot(Space.ICON);
    private final TextBlock name = new TextBlock(Type.body(), Palette.TEXT, 2, TEXT_WIDTH);
    private final StatusPill pill = new StatusPill();
    private final TextBlock reason = new TextBlock(Type.small(), Palette.TEXT_MUTED, 2, TEXT_WIDTH);

    public ItemRow()
    {
        super(new BorderLayout(0, 1));
        setOpaque(false);
        JPanel line = new JPanel(new BorderLayout(Space.ICON_GAP, 0));
        line.setOpaque(false);
        line.add(art, BorderLayout.WEST);
        line.add(name, BorderLayout.CENTER);
        // The pill keeps its own height when a long name wraps, beside the first line.
        JPanel pillSlot = new JPanel(new BorderLayout());
        pillSlot.setOpaque(false);
        pillSlot.add(pill, BorderLayout.NORTH);
        line.add(pillSlot, BorderLayout.EAST);
        add(line, BorderLayout.NORTH);
        add(reason, BorderLayout.CENTER);
        art.setVisible(false);
    }

    /**
     * @param word   the status in a word, or null for none
     * @param reason why, or null
     */
    public ItemRow show(String text, String word, Palette.Tone tone, String reason)
    {
        name.setText(text);
        pill.show(word, tone);
        this.reason.setText(reason);
        this.reason.setVisible(reason != null && !reason.isEmpty());
        return this;
    }

    /** Art beside the name; the reason then lines up under the name. */
    public ItemRow art(BufferedImage image)
    {
        art.setImage(image);
        art.setVisible(true);
        reason.setBorder(new EmptyBorder(0, Space.ICON + Space.ICON_GAP, 0, 0));
        return this;
    }

    public void setPalette(Palette palette)
    {
        pill.setPalette(palette);
    }

    /**
     * Makes the row answer a click, as a link does: the hand, a shade under the pointer, and
     * the action, wherever on the row the click lands.
     */
    public ItemRow onClick(Runnable action)
    {
        setBackground(Palette.HOVER);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter click = new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                action.run();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                setOpaque(true);
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                setOpaque(false);
                repaint();
            }
        };
        addMouseListener(click);
        for (JComponent part : new JComponent[] {art, name, pill, reason})
        {
            part.addMouseListener(click);
        }
        return this;
    }

    /** Whether a click on the row does something. */
    public boolean clickable()
    {
        return getMouseListeners().length > 0;
    }

    /** What a click on the row does, as the player's click would. */
    public void click()
    {
        for (java.awt.event.MouseListener listener : getMouseListeners())
        {
            listener.mouseClicked(new MouseEvent(this, MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, false));
        }
    }

    /** The row's name as shown. */
    public String title()
    {
        return name.getText();
    }

    public StatusPill pill()
    {
        return pill;
    }

    public TextBlock reason()
    {
        return reason;
    }
}
