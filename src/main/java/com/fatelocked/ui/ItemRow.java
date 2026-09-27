package com.fatelocked.ui;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
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
    private final TextBlock name = new TextBlock(Type.body(), Palette.TEXT, 1, TEXT_WIDTH);
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
        line.add(pill, BorderLayout.EAST);
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
