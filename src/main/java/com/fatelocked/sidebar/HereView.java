package com.fatelocked.sidebar;

import com.fatelocked.ui.Art;
import com.fatelocked.ui.FlatButton;
import com.fatelocked.ui.Fold;
import com.fatelocked.ui.Hairline;
import com.fatelocked.ui.IconSource;
import com.fatelocked.ui.ItemRow;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Section;
import com.fatelocked.ui.SkillArt;
import com.fatelocked.ui.Space;
import com.fatelocked.ui.StatTiles;
import com.fatelocked.ui.StatusPill;
import com.fatelocked.ui.Type;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * The Here section, drawn from a {@link HereModel}. Each category opens and closes, and
 * Skilling's skills do too; all start closed, each with a line saying what it holds, and
 * the card remembers which the player left open from place to place.
 */
public class HereView extends Section
{
    public static final String TITLE = "Here";

    /** Categories and skills showing every row, not the first few: forgotten from place to place. */
    private final Set<String> expandedGroups = new HashSet<>();
    /** Categories and skills left open: kept from place to place. */
    private final Set<String> open = new TreeSet<>();
    private final List<Consumer<Set<String>>> foldListeners = new ArrayList<>();
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

    /** The categories and skills to show open, such as those saved from last time. */
    public void setOpen(Set<String> keys)
    {
        open.clear();
        open.addAll(keys);
        if (model != null)
        {
            apply(model);
        }
    }

    /** The categories and skills open now. */
    public Set<String> open()
    {
        return Collections.unmodifiableSet(new TreeSet<>(open));
    }

    /** Called with what's open each time the player opens or closes a category or skill. */
    public void onFold(Consumer<Set<String>> listener)
    {
        foldListeners.add(listener);
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
            body.add(group(group));
        }
        refresh();
    }

    public HereModel model()
    {
        return model;
    }

    private Fold group(HereModel.Group group)
    {
        Fold fold = fold(group.getTitle(), 0, group.getCategory(), group.tally());
        fold.setNote(String.valueOf(group.getRows().size()));
        Art art = Art.forCategory(group.getCategory());
        if (art != null)
        {
            icons.load(art, fold.art()::setImage);
        }
        if (group.getSubgroups().isEmpty())
        {
            rows(fold.body(), group.getCategory(), group.shown(expandedGroups.contains(group.getCategory())),
                group.hidden());
            return fold;
        }
        for (HereModel.Subgroup subgroup : group.getSubgroups())
        {
            Fold inner = fold(subgroup.getTitle(), Fold.INDENT, subgroup.getKey(), subgroup.tally());
            inner.setArt(SkillArt.icon(subgroup.getSkill()));
            inner.setNote(subgroup.getNote() != null ? subgroup.getNote() : String.valueOf(subgroup.getRows().size()));
            rows(inner.body(), subgroup.getKey(), subgroup.shown(expandedGroups.contains(subgroup.getKey())),
                subgroup.hidden());
            fold.body().add(inner);
        }
        return fold;
    }

    /** A category or skill's line, open as the player left it, saying what it holds while closed. */
    private Fold fold(String title, int indent, String key, List<HereModel.Count> tally)
    {
        Fold fold = new Fold(title, indent);
        fold.setSummary(tally(tally));
        fold.setOpen(open.contains(key));
        fold.onToggle(isOpen -> {
            if (isOpen)
            {
                open.add(key);
            }
            else
            {
                open.remove(key);
            }
            Set<String> now = open();
            for (Consumer<Set<String>> listener : foldListeners)
            {
                listener.accept(now);
            }
        });
        return fold;
    }

    /** The rows, the first few until "+N more" is clicked, set in under the line they belong to. */
    private void rows(JPanel into, String key, List<HereModel.Row> shown, int hidden)
    {
        for (HereModel.Row row : shown)
        {
            ItemRow item = new ItemRow().show(row.getName(), row.getWord(), row.getTone(), row.getReason());
            item.setPalette(palette);
            into.add(indented(item));
        }
        if (hidden > 0)
        {
            boolean expanded = expandedGroups.contains(key);
            FlatButton more = new FlatButton(expanded ? "Show fewer" : "+" + hidden + " more", FlatButton.Kind.LINK);
            more.addActionListener(e -> {
                if (!expandedGroups.remove(key))
                {
                    expandedGroups.add(key);
                }
                apply(this.model);
            });
            JPanel left = new JPanel(new BorderLayout());
            left.setOpaque(false);
            left.add(more, BorderLayout.WEST);
            into.add(indented(left));
        }
    }

    /** "3 can do · 2 locked", each count in its status's colour. */
    private JComponent tally(List<HereModel.Count> tally)
    {
        if (tally.isEmpty())
        {
            return null;
        }
        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        line.setOpaque(false);
        for (int i = 0; i < tally.size(); i++)
        {
            HereModel.Count count = tally.get(i);
            if (i > 0)
            {
                line.add(Sidebar.label(" · ", Type.small(), Palette.TEXT_MUTED));
            }
            line.add(Sidebar.label(count.getValue() + " " + count.getLabel(), Type.small(),
                palette.text(count.getTone())));
        }
        return line;
    }

    private static JComponent indented(JComponent row)
    {
        JPanel set = new JPanel(new BorderLayout());
        set.setOpaque(false);
        set.setBorder(new javax.swing.border.EmptyBorder(0, Fold.INDENT, 0, 0));
        set.add(row, BorderLayout.CENTER);
        return set;
    }

    private void refresh()
    {
        body().revalidate();
        body().repaint();
    }
}
