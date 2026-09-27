package com.fatelocked.sidebar;

import com.fatelocked.ui.Art;
import com.fatelocked.ui.ArtSlot;
import com.fatelocked.ui.FlatButton;
import com.fatelocked.ui.Hairline;
import com.fatelocked.ui.IconSource;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Section;
import com.fatelocked.ui.Space;
import com.fatelocked.ui.StatusPill;
import com.fatelocked.ui.Terms;
import com.fatelocked.ui.ToggleSwitch;
import com.fatelocked.ui.Type;
import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.util.function.Consumer;
import javax.swing.JPanel;

/**
 * The Strict Mode section, drawn from a {@link StrictModeModel}. The switch in its
 * header is the one setting the sidebar keeps besides online sync (Decision 4). The
 * section is open while Strict Mode is on and closed while it is off.
 */
public class StrictModeView extends Section
{
    private final ToggleSwitch toggle = new ToggleSwitch();
    private final IconSource icons;
    private Consumer<Boolean> onToggle = on -> { };
    private Consumer<CardAction> onAction = action -> { };
    private Palette palette = Palette.defaults();
    private StrictModeModel model;

    public StrictModeView(IconSource icons)
    {
        super(Terms.STRICT_MODE, false);
        this.icons = icons;
        toggle.setToolTipText("Turn Strict Mode on or off");
        toggle.addActionListener(e -> onToggle.accept(toggle.isSelected()));
        setTrailing(toggle);
    }

    /** Called with the switch's new position when the player flips it. */
    public void onToggle(Consumer<Boolean> handler)
    {
        onToggle = handler;
    }

    public void onAction(Consumer<CardAction> handler)
    {
        onAction = handler;
    }

    public void setPalette(Palette palette)
    {
        this.palette = palette;
        if (model != null)
        {
            apply(model);
        }
    }

    public ToggleSwitch toggle()
    {
        return toggle;
    }

    public void apply(StrictModeModel model)
    {
        boolean wasOn = this.model != null && this.model.isOn();
        this.model = model;
        toggle.setSelected(model.isOn());
        setCount(model.isOn() ? null : model.getWord());
        JPanel body = body();
        body.removeAll();

        JPanel state = new JPanel(new BorderLayout(Space.ICON_GAP, 0));
        state.setOpaque(false);
        ArtSlot teleBlock = new ArtSlot(20);
        icons.load(model.getTone() == Palette.Tone.GOOD ? Art.STRICT_MODE : Art.STRICT_MODE_OFF,
            teleBlock::setImage);
        StatusPill pill = new StatusPill(model.getWord(), model.getTone());
        pill.setPalette(palette);
        JPanel pillCentre = new JPanel(new GridBagLayout());
        pillCentre.setOpaque(false);
        pillCentre.add(pill);
        JPanel pillSlot = new JPanel(new BorderLayout(Space.ICON_GAP, 0));
        pillSlot.setOpaque(false);
        pillSlot.add(teleBlock, BorderLayout.WEST);
        pillSlot.add(pillCentre, BorderLayout.CENTER);
        state.add(pillSlot, BorderLayout.WEST);
        if (model.getAction() != null)
        {
            FlatButton button = new FlatButton(model.getAction().label(), FlatButton.Kind.SECONDARY);
            CardAction action = model.getAction();
            button.addActionListener(e -> onAction.accept(action));
            state.add(button, BorderLayout.EAST);
        }
        body.add(state);
        if (model.getDetail() != null)
        {
            body.add(Sidebar.text(model.getDetail(), Type.small(), Palette.TEXT_MUTED, 0));
        }
        if (!model.getRecent().isEmpty())
        {
            body.add(new Hairline());
            body.add(Sidebar.text("Recently stopped", Type.small(), Palette.TEXT_MUTED, 1));
            for (String line : model.getRecent())
            {
                body.add(Sidebar.text(line, Type.body(), Palette.TEXT, 2));
            }
        }
        if (model.isOn() != wasOn)
        {
            setExpanded(model.isOn());
        }
        body.revalidate();
        body.repaint();
    }

    public StrictModeModel model()
    {
        return model;
    }
}
