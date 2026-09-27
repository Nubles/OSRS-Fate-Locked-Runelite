package com.fatelocked.sidebar;

import com.fatelocked.ui.FlatButton;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Section;
import com.fatelocked.ui.Terms;
import com.fatelocked.ui.Type;
import javax.swing.JPanel;

/**
 * The Roll inbox section, drawn from a {@link RollInboxModel}. What RuneLite notices
 * stays on this computer; the web Roll Inbox is separate until Stage 4 joins them.
 */
public class RollInboxView extends Section
{
    public static final String TITLE = "Roll inbox";
    static final String LOCAL_ONLY = "Local only: RuneLite doesn't upload gameplay data.";

    private final FlatButton open = new FlatButton("Open web Roll Inbox", FlatButton.Kind.SECONDARY);
    private Palette palette = Palette.defaults();
    private RollInboxModel model;

    public RollInboxView()
    {
        super(TITLE, false);
        open.setToolTipText("Open the web Roll Inbox; the local history isn't sent to it");
    }

    /** Called when the player opens the web Roll Inbox. */
    public void onOpen(Runnable handler)
    {
        for (java.awt.event.ActionListener listener : open.getActionListeners())
        {
            open.removeActionListener(listener);
        }
        open.addActionListener(e -> handler.run());
    }

    public void setPalette(Palette palette)
    {
        this.palette = palette;
        if (model != null)
        {
            apply(model);
        }
    }

    public void apply(RollInboxModel model)
    {
        this.model = model;
        setCount(model.getWarnings() > 0 ? model.getWarnings() + (model.getWarnings() == 1 ? " warning" : " warnings")
            : null);
        JPanel body = body();
        body.removeAll();
        body.add(Sidebar.pair("Local events", String.valueOf(Math.max(0, model.getLocalEvents()))));
        body.add(Sidebar.pair(Terms.NEEDS_CHECKING, String.valueOf(Math.max(0, model.getNeedsChecking()))));
        body.add(Sidebar.pair("Warnings", model.getWarnings() <= 0 ? "None" : model.getWarnings() + " active",
            palette.text(model.getWarnings() <= 0 ? Palette.Tone.GOOD : Palette.Tone.BAD)));
        if (model.isSaveFailed())
        {
            body.add(Sidebar.text("Saving the local history failed.", Type.small(), palette.text(Palette.Tone.BAD),
                0));
        }
        body.add(Sidebar.text(LOCAL_ONLY, Type.small(), Palette.TEXT_MUTED, 0));
        body.add(open);
        body.revalidate();
        body.repaint();
    }

    public RollInboxModel model()
    {
        return model;
    }
}
