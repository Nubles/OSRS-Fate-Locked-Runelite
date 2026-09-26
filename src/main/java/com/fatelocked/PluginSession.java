package com.fatelocked;

/**
 * One per plugin start. Work queued for later, on the client thread, the
 * Swing thread or RuneLite's executor, is wrapped by the session that queued
 * it and does nothing once that session has ended, even if the plugin has
 * been started again since.
 */
final class PluginSession
{
    private volatile boolean active = true;

    /** A session that has already ended, for a plugin that is not running. */
    static PluginSession ended()
    {
        PluginSession session = new PluginSession();
        session.end();
        return session;
    }

    boolean isActive()
    {
        return active;
    }

    void end()
    {
        active = false;
    }

    /** The task, run only if this session is still active when it runs. */
    Runnable guard(Runnable task)
    {
        return () ->
        {
            if (active)
            {
                task.run();
            }
        };
    }
}
