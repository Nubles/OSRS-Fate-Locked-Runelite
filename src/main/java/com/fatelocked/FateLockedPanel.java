package com.fatelocked;

import com.fatelocked.sidebar.CardAction;
import com.fatelocked.sidebar.ConnectionModel;
import com.fatelocked.sidebar.HereModel;
import com.fatelocked.sidebar.RollInboxModel;
import com.fatelocked.sidebar.RunModel;
import com.fatelocked.sidebar.Sidebar;
import com.fatelocked.sidebar.StatusCardModel;
import com.fatelocked.sidebar.StrictModeModel;
import com.fatelocked.ui.GameArt;
import com.fatelocked.ui.IconSource;
import com.fatelocked.ui.Palette;
import java.awt.BorderLayout;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * The sidebar (Stage 3): a thin Swing view of the models the plugin works out on the
 * client thread (C7). Every update is queued on the Swing thread, so updates apply in
 * the order they were made, whichever thread made them. Settings live in RuneLite's
 * configuration (Decision 4); the sidebar keeps Strict Mode's switch, and online sync's,
 * which carries the player's consent.
 */
class FateLockedPanel extends PluginPanel
{
    static final String TRACKER_URL = "https://nubles.github.io/OSRS-Fate-Locked/";

    private final Sidebar sidebar;
    private Consumer<CardAction> onAction = action -> { };
    private Consumer<Boolean> onStrictModeToggle = on -> { };
    private Consumer<Boolean> onSyncToggle = on -> { };
    private String rollInboxUrl = rollInboxUrl(TRACKER_URL);

    @Inject
    FateLockedPanel(SpriteManager sprites, ItemManager items)
    {
        this(new GameArt(sprites, items));
    }

    FateLockedPanel(IconSource icons)
    {
        setLayout(new BorderLayout());
        setBorder(null);
        setBackground(Palette.PANEL);
        sidebar = new Sidebar(icons);
        add(sidebar, BorderLayout.NORTH);
        sidebar.openTracker().addActionListener(e -> LinkBrowser.browse(TRACKER_URL));
        sidebar.status().onAction(this::act);
        sidebar.strictMode().onAction(this::act);
        sidebar.connection().onAction(this::act);
        sidebar.strictMode().onToggle(on -> onStrictModeToggle.accept(on));
        sidebar.connection().onSyncToggle(on -> onSyncToggle.accept(on));
        sidebar.rollInbox().onOpen(() -> LinkBrowser.browse(rollInboxUrl));
    }

    /** What the player asked for, on the Swing thread; the sidebar handles its own. */
    void act(CardAction action)
    {
        if (action == CardAction.USE_BACKUP)
        {
            sidebar.connection().setExpanded(true);
            return;
        }
        if (action == CardAction.OPEN_TRACKER)
        {
            LinkBrowser.browse(TRACKER_URL);
            return;
        }
        onAction.accept(action);
    }

    void onAction(Consumer<CardAction> handler)
    {
        onAction = handler;
    }

    /** Called with Strict Mode's switch, as the player flipped it. */
    void onStrictModeToggle(Consumer<Boolean> handler)
    {
        onStrictModeToggle = handler;
    }

    /** Called with the online-sync switch, as the player flipped it; consent comes next. */
    void onSyncToggle(Consumer<Boolean> handler)
    {
        onSyncToggle = handler;
    }

    void onIntroDismiss(Runnable handler)
    {
        sidebar.strictMode().onIntroDismiss(handler);
    }

    void showStatus(StatusCardModel model)
    {
        queueOnEdt(() -> sidebar.status().apply(model));
    }

    void showHere(HereModel model)
    {
        queueOnEdt(() -> sidebar.here().apply(model));
    }

    void showStrictMode(StrictModeModel model)
    {
        queueOnEdt(() -> sidebar.strictMode().apply(model));
    }

    void showRun(RunModel model)
    {
        queueOnEdt(() -> sidebar.run().apply(model));
    }

    void showConnection(ConnectionModel model)
    {
        queueOnEdt(() -> sidebar.connection().apply(model));
    }

    void showRollInbox(RollInboxModel model)
    {
        queueOnEdt(() -> sidebar.rollInbox().apply(model));
    }

    void setPalette(Palette palette)
    {
        queueOnEdt(() -> sidebar.setPalette(palette));
    }

    /** A short message under the status card, which clears itself (U16). */
    void flashStatus(String message, boolean ok)
    {
        queueOnEdt(() -> sidebar.notice(message, ok ? Palette.Tone.GOOD : Palette.Tone.BAD));
    }

    void showStrictModeIntro()
    {
        queueOnEdt(() -> sidebar.strictMode().showIntro());
    }

    /** Put Strict Mode's switch back where the rules have it, after a change that didn't happen. */
    void restoreStrictMode()
    {
        queueOnEdt(() -> {
            StrictModeModel shown = sidebar.strictMode().model();
            if (shown != null)
            {
                sidebar.strictMode().apply(shown);
            }
        });
    }

    /** Put the online-sync switch back, after the player declined consent or it couldn't be saved. */
    void restoreConnection()
    {
        queueOnEdt(() -> {
            ConnectionModel shown = sidebar.connection().model();
            if (shown != null)
            {
                sidebar.connection().apply(shown);
            }
        });
    }

    void setRollInboxLink(String trackerUrl)
    {
        rollInboxUrl = rollInboxUrl(trackerUrl);
    }

    static String rollInboxUrl(String trackerUrl)
    {
        String base = trackerUrl == null || trackerUrl.trim().isEmpty() ? TRACKER_URL : trackerUrl.trim();
        return base + "?open=roll-inbox";
    }

    /** Ask before replacing a pairing that works. */
    boolean confirmRepair()
    {
        Object[] options = {"Re-pair", "Cancel"};
        return JOptionPane.showOptionDialog(
            this,
            "<html><body style='width: 320px'>Pair RuneLite with a tracker profile again?"
                + "<br><br>RuneLite keeps using your current pairing until the new one"
                + " sends your rules. If none arrives within 10 minutes, nothing"
                + " changes.</body></html>",
            "Fate Locked re-pairing", JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, options, options[1]) == 0;
    }

    /** Ask before forgetting the pairing; the rules in force stay (decision 9). */
    boolean confirmDisconnect()
    {
        Object[] options = {"Disconnect", "Cancel"};
        return JOptionPane.showOptionDialog(
            this,
            "<html><body style='width: 320px'>Disconnect RuneLite from the tracker?"
                + "<br><br>RuneLite stops checking for rules. The rules you have now stay in"
                + " use until you connect again or import others.</body></html>",
            "Fate Locked disconnect", JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, options, options[1]) == 0;
    }

    boolean confirmNetworkConnection()
    {
        Object[] options = {"Enable online sync", "Cancel"};
        return JOptionPane.showOptionDialog(
            this,
            "<html><body style='width: 320px'>" + FateLockedConfig.NETWORK_WARNING
                + "<br><br>RuneLite retrieves rules from the Fate Locked relay. "
                + "It does not upload gameplay data.<br><br>Allow online sync?</body></html>",
            "Fate Locked online sync", JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE, null, options, options[1]) == 0;
    }

    Sidebar sidebar()
    {
        return sidebar;
    }

    /**
     * Queue a sidebar update on the Swing thread, even from the Swing thread
     * itself, so updates from every thread apply in the order they were
     * made. Running it at once there let it overtake older updates still
     * queued from other threads: a "Not connected" could be followed by a
     * stale "Connected".
     */
    private static void queueOnEdt(Runnable update)
    {
        SwingUtilities.invokeLater(update);
    }
}
