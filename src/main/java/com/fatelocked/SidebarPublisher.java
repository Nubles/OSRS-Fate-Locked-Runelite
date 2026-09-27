package com.fatelocked;

import com.fatelocked.sidebar.ConnectionModel;
import com.fatelocked.sidebar.HereModel;
import com.fatelocked.sidebar.RollInboxModel;
import com.fatelocked.sidebar.RunModel;
import com.fatelocked.sidebar.StatusCardModel;
import com.fatelocked.sidebar.StrictModeModel;
import java.util.Objects;

/**
 * Hands the sidebar only what changed (A9): the plugin works the models out every tick,
 * and an unchanged one is never posted to the Swing thread again. Client thread only.
 */
final class SidebarPublisher
{
    private final FateLockedPanel panel;
    private StatusCardModel status;
    private HereModel here;
    private StrictModeModel strictMode;
    private RunModel run;
    private ConnectionModel connection;
    private RollInboxModel rollInbox;

    SidebarPublisher(FateLockedPanel panel)
    {
        this.panel = panel;
    }

    void status(StatusCardModel next)
    {
        if (!Objects.equals(next, status))
        {
            status = next;
            panel.showStatus(next);
        }
    }

    void here(HereModel next)
    {
        if (!Objects.equals(next, here))
        {
            here = next;
            panel.showHere(next);
        }
    }

    void strictMode(StrictModeModel next)
    {
        if (!Objects.equals(next, strictMode))
        {
            strictMode = next;
            panel.showStrictMode(next);
        }
    }

    void run(RunModel next)
    {
        if (!Objects.equals(next, run))
        {
            run = next;
            panel.showRun(next);
        }
    }

    void connection(ConnectionModel next)
    {
        if (!Objects.equals(next, connection))
        {
            connection = next;
            panel.showConnection(next);
        }
    }

    void rollInbox(RollInboxModel next)
    {
        if (!Objects.equals(next, rollInbox))
        {
            rollInbox = next;
            panel.showRollInbox(next);
        }
    }
}
