package com.fatelocked;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fatelocked.sidebar.RollInboxModel;
import com.fatelocked.sidebar.StatusCardModel;
import com.fatelocked.ui.Palette.Tone;
import org.junit.Test;

/** A9: the plugin works the sidebar out every tick, but only a change reaches the Swing thread. */
public class SidebarPublisherTest
{
    @Test
    public void anUnchangedModelIsPostedOnce()
    {
        FateLockedPanel panel = mock(FateLockedPanel.class);
        SidebarPublisher publisher = new SidebarPublisher(panel);
        StatusCardModel upToDate = StatusCardModel.of(Tone.GOOD, "Rules up to date", "Synced just now.");

        publisher.status(upToDate);
        publisher.status(StatusCardModel.of(Tone.GOOD, "Rules up to date", "Synced just now."));
        verify(panel, times(1)).showStatus(upToDate);

        StatusCardModel older = StatusCardModel.of(Tone.GOOD, "Rules up to date", "Synced 1 min ago.");
        publisher.status(older);
        verify(panel, times(1)).showStatus(older);

        publisher.rollInbox(new RollInboxModel(1, 0, 0, false));
        publisher.rollInbox(new RollInboxModel(1, 0, 0, false));
        publisher.rollInbox(new RollInboxModel(1, 0, 1, false));
        verify(panel, times(1)).showRollInbox(new RollInboxModel(1, 0, 0, false));
        verify(panel, times(1)).showRollInbox(new RollInboxModel(1, 0, 1, false));
    }
}
