package com.fatelocked.sidebar;

import com.fatelocked.ui.Art;
import com.fatelocked.ui.ArtSlot;
import com.fatelocked.ui.FlatButton;
import com.fatelocked.ui.Hairline;
import com.fatelocked.ui.IconSource;
import com.fatelocked.ui.ItemRow;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Section;
import com.fatelocked.ui.Space;
import com.fatelocked.ui.StatTiles;
import com.fatelocked.ui.StatusPill;
import com.fatelocked.ui.TextBlock;
import com.fatelocked.ui.Type;
import java.awt.BorderLayout;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** The Here section, drawn from a {@link HereModel}. */
public class HereView extends Section
{
    public static final String TITLE = "Here";

    private final Set<String> expandedGroups = new HashSet<>();
    private final IconSource icons;
    private Palette palette = Palette.defaults();
    private HereModel model;

    public HereView(IconSource icons)
    {
        super(TITLE, true);
        this.icons = icons;
    }

    public void setPalette(Palette palette)
    {
        this.palette = palette;
        if (model != null)
        {
            apply(model);
        }
    }

    public void apply(HereModel model)
    {
        if (this.model == null || !java.util.Objects.equals(this.model.getPlace(), model.getPlace()))
        {
            expandedGroups.clear();
        }
        this.model = model;
        JPanel body = body();
        body.removeAll();
        if (model.getMessage() != null)
        {
            body.add(Sidebar.text(model.getMessage(), Type.body(), Palette.TEXT_MUTED, 0));
            refresh();
            return;
        }

        JPanel title = new JPanel(new BorderLayout(Space.ICON_GAP, 0));
        title.setOpaque(false);
        title.add(Sidebar.text(model.getPlace(), Type.title(), Palette.TITLE, 1), BorderLayout.CENTER);
        if (model.getWord() != null)
        {
            StatusPill pill = new StatusPill(model.getWord(), model.getTone());
            pill.setPalette(palette);
            title.add(pill, BorderLayout.EAST);
        }
        body.add(title);
        if (model.getWhere() != null)
        {
            body.add(Sidebar.text(model.getWhere(), Type.small(), Palette.TEXT_MUTED, 1));
        }
        if (model.getReason() != null)
        {
            body.add(Sidebar.text(model.getReason(), Type.body(), palette.text(model.getTone()), 3));
        }
        if (!model.getCounts().isEmpty())
        {
            StatTiles tiles = new StatTiles();
            tiles.setPalette(palette);
            tiles.show(model.getCounts().stream()
                .map(count -> new StatTiles.Tile(count.getValue(), count.getLabel(), count.getTone()))
                .toArray(StatTiles.Tile[]::new));
            body.add(tiles);
        }
        for (HereModel.Group group : model.getGroups())
        {
            body.add(new Hairline());
            body.add(groupTitle(group));
            boolean expanded = expandedGroups.contains(group.getTitle());
            for (HereModel.Row row : group.shown(expanded))
            {
                ItemRow item = new ItemRow().show(row.getName(), row.getWord(), row.getTone(), row.getReason());
                item.setPalette(palette);
                body.add(item);
            }
            if (group.hidden() > 0)
            {
                FlatButton more = new FlatButton(expanded ? "Show fewer" : "+" + group.hidden() + " more",
                    FlatButton.Kind.LINK);
                more.addActionListener(e -> {
                    if (!expandedGroups.remove(group.getTitle()))
                    {
                        expandedGroups.add(group.getTitle());
                    }
                    apply(this.model);
                });
                JPanel left = new JPanel(new BorderLayout());
                left.setOpaque(false);
                left.add(more, BorderLayout.WEST);
                body.add(left);
            }
        }
        refresh();
    }

    public HereModel model()
    {
        return model;
    }

    private JPanel groupTitle(HereModel.Group group)
    {
        JPanel line = new JPanel(new BorderLayout(Space.ICON_GAP, 0));
        line.setOpaque(false);
        line.setBorder(new EmptyBorder(2, 0, 0, 0));
        Art art = Art.forCategory(group.getCategory());
        if (art != null)
        {
            ArtSlot slot = new ArtSlot(Space.ICON);
            icons.load(art, slot::setImage);
            line.add(slot, BorderLayout.WEST);
        }
        TextBlock title = Sidebar.text(group.getTitle(), Type.small(), Palette.TEXT_MUTED, 1);
        line.add(title, BorderLayout.CENTER);
        line.add(Sidebar.label(String.valueOf(group.getRows().size()), Type.small(), Palette.TEXT_MUTED),
            BorderLayout.EAST);
        return line;
    }

    private void refresh()
    {
        body().revalidate();
        body().repaint();
    }
}
