package com.fatelocked.sidebar;

import com.fatelocked.ui.FlatButton;
import com.fatelocked.ui.Hairline;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Section;
import com.fatelocked.ui.Space;
import com.fatelocked.ui.ToggleSwitch;
import com.fatelocked.ui.Type;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.function.Consumer;
import javax.swing.JPanel;

/**
 * The Connection and backup section, drawn from a {@link ConnectionModel}. Its online
 * sync switch carries the player's consent, so the sidebar keeps it (Decision 4).
 */
public class ConnectionView extends Section
{
    public static final String TITLE = "Connection & backup";
    static final String PRIVACY = "RuneLite fetches your rules from the Fate Locked relay, which sees your"
        + " IP address. Nothing from your game is uploaded. The rules name your character, so the relay"
        + " can link the two.";

    private final ToggleSwitch sync = new ToggleSwitch();
    private Consumer<Boolean> onSyncToggle = on -> { };
    private Consumer<CardAction> onAction = action -> { };
    private ConnectionModel model;

    public ConnectionView()
    {
        super(TITLE, false);
        sync.addActionListener(e -> onSyncToggle.accept(sync.isSelected()));
    }

    /** Called with the switch's new position; consent is asked before it turns on. */
    public void onSyncToggle(Consumer<Boolean> handler)
    {
        onSyncToggle = handler;
    }

    public void onAction(Consumer<CardAction> handler)
    {
        onAction = handler;
    }

    public ToggleSwitch syncSwitch()
    {
        return sync;
    }

    public void apply(ConnectionModel model)
    {
        this.model = model;
        setCount(model.getSummary());
        sync.setSelected(model.isOnlineSync());
        JPanel body = body();
        body.removeAll();

        JPanel syncRow = new JPanel(new BorderLayout(Space.ICON_GAP, 0));
        syncRow.setOpaque(false);
        syncRow.add(Sidebar.text("Online sync", Type.body(), Palette.TEXT, 1), BorderLayout.CENTER);
        syncRow.add(sync, BorderLayout.EAST);
        body.add(syncRow);
        if (model.getPairing() != null)
        {
            body.add(Sidebar.pair("Pairing", model.getPairing()));
        }
        if (model.getSource() != null)
        {
            body.add(Sidebar.text(model.getSource(), Type.small(), Palette.TEXT_MUTED, 0));
        }
        JPanel pairingButtons = new JPanel(new GridLayout(1, 0, Space.GAP, 0));
        pairingButtons.setOpaque(false);
        pairingButtons.add(button(model.getPairingAction()));
        if (model.isCanDisconnect())
        {
            pairingButtons.add(button(CardAction.DISCONNECT));
        }
        body.add(pairingButtons);
        if (model.isCanCheckNow())
        {
            body.add(button(CardAction.CHECK_NOW));
        }
        body.add(new Hairline());
        body.add(Sidebar.text("Backup", Type.small(), Palette.TEXT_MUTED, 1));
        body.add(button(CardAction.IMPORT_CLIPBOARD));
        body.add(button(CardAction.LOAD_BACKUP_FILE));
        body.add(new Hairline());
        body.add(Sidebar.text(PRIVACY, Type.small(), Palette.TEXT_MUTED, 0));
        body.revalidate();
        body.repaint();
    }

    public ConnectionModel model()
    {
        return model;
    }

    private FlatButton button(CardAction action)
    {
        FlatButton button = new FlatButton(action.label(), FlatButton.Kind.SECONDARY);
        button.addActionListener(e -> onAction.accept(action));
        return button;
    }
}
