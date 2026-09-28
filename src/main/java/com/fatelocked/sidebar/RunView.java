package com.fatelocked.sidebar;

import com.fatelocked.ui.Art;
import com.fatelocked.ui.Hairline;
import com.fatelocked.ui.IconSource;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.ProgressBar;
import com.fatelocked.ui.Section;
import com.fatelocked.ui.StatTiles;
import com.fatelocked.ui.Terms;
import com.fatelocked.ui.Type;
import javax.swing.JPanel;

/** The Run section, drawn from a {@link RunModel}. */
public class RunView extends Section
{
    public static final String TITLE = "Run";

    private final IconSource icons;
    private RunModel model;

    public RunView(IconSource icons)
    {
        super(TITLE, false);
        this.icons = icons;
    }

    public void apply(RunModel model)
    {
        this.model = model;
        setCount(model.getProgress() == null ? null
            : Math.round(model.getFraction() * 100) + "%");
        JPanel body = body();
        body.removeAll();
        body.add(Sidebar.pair("Character", model.getCharacter()));
        if (model.getRunId() != null)
        {
            body.add(Sidebar.pair("Run", model.getRunId()));
        }
        if (model.getProgress() != null)
        {
            body.add(Sidebar.text(model.getProgress(), Type.body(), Palette.TEXT, 1));
            ProgressBar bar = new ProgressBar();
            bar.setFraction(model.getFraction());
            body.add(bar);
        }
        body.add(new Hairline());
        StatTiles keys = new StatTiles(icons);
        keys.show(new StatTiles.Tile(model.getKeys(), Terms.KEYS, Palette.Tone.FRONTIER, Art.KEYS),
            new StatTiles.Tile(model.getOmniKeys(), Terms.OMNI_KEYS, Palette.Tone.FRONTIER, Art.OMNI_KEYS),
            new StatTiles.Tile(model.getChaosKeys(), Terms.CHAOS_KEYS, Palette.Tone.FRONTIER, Art.CHAOS_KEYS));
        body.add(keys);
        body.add(Sidebar.pair(Terms.FATE_POINTS, String.valueOf(model.getFatePoints()), Art.FATE_POINTS, icons));
        if (model.getRitual() != null)
        {
            body.add(Sidebar.pair("Ritual", model.getRitual()));
        }
        if (model.getGoal() != null)
        {
            body.add(Sidebar.pair("Next goal", model.getGoal()));
        }
        body.revalidate();
        body.repaint();
    }

    public RunModel model()
    {
        return model;
    }
}
