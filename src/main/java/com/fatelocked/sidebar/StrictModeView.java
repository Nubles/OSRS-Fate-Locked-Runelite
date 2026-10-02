package com.fatelocked.sidebar;

import com.fatelocked.ui.Art;
import com.fatelocked.ui.ArtSlot;
import com.fatelocked.ui.FlatButton;
import com.fatelocked.ui.Hairline;
import com.fatelocked.ui.IconSource;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Section;
import com.fatelocked.ui.Space;
import com.fatelocked.ui.Stack;
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
    static final String INTRO = "Strict Mode stops a teleport it recognises exactly, with one place it can go,"
        + " when fresh rules for this character lock that place. It also stops one to an unlocked place when you"
        + " haven't unlocked that kind of teleport, such as Teleport Tablets, Jewelry Teleports or a spellbook. A"
        + " worn item's teleport, such as a glory's Edgeville, counts. Walking, NPCs, objects, banks and putting"
        + " on gear are never stopped; tags and warnings cover those. Pause it for 60 seconds here, or with a"
        + " hotkey you can set in RuneLite's configuration.";

    private final ToggleSwitch toggle = new ToggleSwitch();
    private final IconSource icons;
    private Consumer<Boolean> onToggle = on -> { };
    private Runnable onIntroDismiss = () -> { };
    private boolean introShown;
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

    /** Called when the player dismisses the first-time explanation. */
    public void onIntroDismiss(Runnable handler)
    {
        onIntroDismiss = handler;
    }

    /** Explain Strict Mode once, the first time it is turned on, until dismissed. */
    public void showIntro()
    {
        introShown = true;
        setExpanded(true);
        if (model != null)
        {
            apply(model);
        }
    }

    public boolean isIntroShown()
    {
        return introShown;
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
        if (introShown)
        {
            body.add(intro());
        }

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

    private JPanel intro()
    {
        JPanel intro = new JPanel(new Stack(Space.ROW));
        intro.setOpaque(false);
        intro.add(Sidebar.text(INTRO, Type.small(), Palette.TEXT, 0));
        FlatButton gotIt = new FlatButton("Got it", FlatButton.Kind.LINK);
        gotIt.addActionListener(e -> {
            introShown = false;
            onIntroDismiss.run();
            if (model != null)
            {
                apply(model);
            }
        });
        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(gotIt, BorderLayout.WEST);
        intro.add(left);
        intro.add(new Hairline());
        return intro;
    }
}
