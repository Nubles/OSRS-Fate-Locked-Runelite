package com.fatelocked.sidebar;

import com.fatelocked.ui.Art;
import com.fatelocked.ui.ArtSlot;
import com.fatelocked.ui.FlatButton;
import com.fatelocked.ui.IconSource;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Space;
import com.fatelocked.ui.Stack;
import com.fatelocked.ui.TextBlock;
import com.fatelocked.ui.Type;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;

/**
 * The whole sidebar, status first: the header, the status card, Here, Strict Mode,
 * Run, and Connection and backup, then a pointer to RuneLite's configuration, where
 * the settings live (Decision 4).
 */
public class Sidebar extends JPanel
{
    /** The width text wraps to inside a card, before the first layout. */
    static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 2 * Space.EDGE - 2 * Space.PAD;
    static final String FOOTER = "More settings are in RuneLite's configuration, under Fate Locked Ironman.";

    private static final int MARK = 28;

    private final ArtSlot mark = new ArtSlot(MARK);
    private final FlatButton openTracker = new FlatButton("Open tracker", FlatButton.Kind.LINK);
    private final StatusCardView status = new StatusCardView();
    private final HereView here;
    private final StrictModeView strictMode;
    private final RunView run;
    private final ConnectionView connection = new ConnectionView();

    public Sidebar(IconSource icons)
    {
        super(new Stack(Space.GAP));
        here = new HereView(icons);
        strictMode = new StrictModeView(icons);
        run = new RunView(icons);
        icons.load(Art.MARK, mark::setImage);
        setBackground(Palette.PANEL);
        setBorder(new EmptyBorder(Space.EDGE, Space.EDGE, Space.EDGE, Space.EDGE));
        add(header());
        add(status);
        add(here);
        add(strictMode);
        add(run);
        add(connection);
        add(text(FOOTER, Type.small(), Palette.TEXT_MUTED, 0));
    }

    public StatusCardView status()
    {
        return status;
    }

    public HereView here()
    {
        return here;
    }

    public StrictModeView strictMode()
    {
        return strictMode;
    }

    public RunView run()
    {
        return run;
    }

    public ConnectionView connection()
    {
        return connection;
    }

    public FlatButton openTracker()
    {
        return openTracker;
    }

    public void setPalette(Palette palette)
    {
        status.setPalette(palette);
        here.setPalette(palette);
        strictMode.setPalette(palette);
    }

    private JComponent header()
    {
        JPanel header = new JPanel(new BorderLayout(Space.ICON_GAP, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(2, 2, 2, 0));
        header.add(mark, BorderLayout.WEST);
        header.add(label("Fate Locked", Type.title(), Palette.TITLE), BorderLayout.CENTER);
        header.add(openTracker, BorderLayout.EAST);
        return header;
    }

    /** Text that wraps to a card's width, in at most {@code lines} lines (0: no limit). */
    static TextBlock text(String value, Font font, Color colour, int lines)
    {
        TextBlock block = new TextBlock(font, colour, lines, TEXT_WIDTH);
        block.setText(value);
        return block;
    }

    /** A single line that takes only the width it needs. */
    static JLabel label(String value, Font font, Color colour)
    {
        JLabel label = new JLabel(value);
        label.setFont(font);
        label.setForeground(colour);
        return label;
    }

    /** A label on the left and its value on the right, on one line. */
    static JPanel pair(String name, String value)
    {
        return pair(name, value, null, IconSource.NONE);
    }

    /** As {@link #pair(String, String)}, with OSRS art before the label. */
    static JPanel pair(String name, String value, Art art, IconSource icons)
    {
        JPanel row = new JPanel(new BorderLayout(Space.ICON_GAP, 0));
        row.setOpaque(false);
        JPanel left = new JPanel(new BorderLayout(Space.ICON_GAP, 0));
        left.setOpaque(false);
        if (art != null)
        {
            ArtSlot slot = new ArtSlot(20);
            icons.load(art, slot::setImage);
            left.add(slot, BorderLayout.WEST);
        }
        left.add(label(name, Type.body(), Palette.TEXT_MUTED), BorderLayout.CENTER);
        row.add(left, BorderLayout.WEST);
        JLabel right = label(value == null ? "—" : value, Type.body(), Palette.TEXT);
        right.setHorizontalAlignment(JLabel.RIGHT);
        row.add(right, BorderLayout.CENTER);
        return row;
    }
}
