package com.fatelocked;

import net.runelite.client.callback.ClientThread;

/**
 * The one way onto the client thread, where all plugin state changes. The
 * sidebar, the config panel, overlay clicks and startUp run on the Swing
 * thread, and file reads and relay replies on other threads; each hands its
 * work over here.
 *
 * Work always goes through ClientThread.invoke(Runnable), which runs it at
 * once on the client thread and on the next tick from anywhere else, never
 * through the BooleanSupplier overload, which re-runs a task that returns
 * false on every tick. One gate belongs to one plugin start: work it queued
 * does nothing once that start has ended.
 */
final class ClientThreadGate
{
    private final ClientThread clientThread;
    private final PluginSession session;

    ClientThreadGate(ClientThread clientThread, PluginSession session)
    {
        this.clientThread = clientThread;
        this.session = session;
    }

    /** The gate of a plugin that is not running: nothing passes it. */
    static ClientThreadGate closed()
    {
        return new ClientThreadGate(null, PluginSession.ended());
    }

    /** Run the task on the client thread: now if already there, else next tick. */
    void run(Runnable task)
    {
        if (!session.isActive())
        {
            return;
        }
        clientThread.invoke(session.guard(task));
    }

    /** Run the task on the next client tick, even when called on the client thread. */
    void runNextTick(Runnable task)
    {
        if (!session.isActive())
        {
            return;
        }
        clientThread.invokeLater(session.guard(task));
    }

    /** The task, for another queue, run only while this gate's start lasts. */
    Runnable guard(Runnable task)
    {
        return session.guard(task);
    }

    /** End this start: work already queued through the gate does nothing. */
    void close()
    {
        session.end();
    }
}
