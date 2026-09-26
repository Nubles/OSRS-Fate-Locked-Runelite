package com.fatelocked;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginSessionTest
{
    @Test
    public void guardedWorkRunsWhileTheSessionLasts()
    {
        PluginSession session = new PluginSession();
        AtomicInteger runs = new AtomicInteger();

        session.guard(runs::incrementAndGet).run();

        assertTrue(session.isActive());
        assertEquals(1, runs.get());
    }

    @Test
    public void workQueuedBeforeTheSessionEndedDoesNothing()
    {
        PluginSession session = new PluginSession();
        AtomicInteger runs = new AtomicInteger();
        Runnable queued = session.guard(runs::incrementAndGet);

        session.end();
        queued.run();

        assertFalse(session.isActive());
        assertEquals(0, runs.get());
    }

    @Test
    public void aLaterStartDoesNotReviveWorkQueuedByAnEarlierOne()
    {
        PluginSession first = new PluginSession();
        AtomicInteger runs = new AtomicInteger();
        Runnable queued = first.guard(runs::incrementAndGet);
        first.end();

        PluginSession second = new PluginSession();
        queued.run();

        assertTrue(second.isActive());
        assertEquals(0, runs.get());
    }

    @Test
    public void aPluginThatIsNotRunningHasNoActiveSession()
    {
        AtomicInteger runs = new AtomicInteger();

        PluginSession.ended().guard(runs::incrementAndGet).run();

        assertEquals(0, runs.get());
    }
}
