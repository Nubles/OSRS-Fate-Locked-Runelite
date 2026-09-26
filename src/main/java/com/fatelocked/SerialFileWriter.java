package com.fatelocked;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

/**
 * Runs local file writes one at a time and in the order they were asked
 * for, off the game and Swing threads, on RuneLite's shared executor. A
 * write queued before the plugin stops still finishes, so nothing already
 * recorded is lost; anything a write reports back to plugin state goes
 * through the client-thread gate.
 */
@Slf4j
final class SerialFileWriter
{
    private final Executor executor;
    private final Queue<Runnable> queue = new ArrayDeque<>();
    private boolean draining;

    SerialFileWriter(Executor executor)
    {
        this.executor = executor;
    }

    void submit(Runnable write)
    {
        synchronized (queue)
        {
            queue.add(write);
            if (draining)
            {
                return;
            }
            draining = true;
        }
        try
        {
            executor.execute(this::drain);
        }
        catch (RuntimeException ex)
        {
            // The executor refused (the client is closing): let the next
            // write try again rather than wait for a drain that never runs.
            synchronized (queue)
            {
                draining = false;
            }
            log.warn("Could not queue a local file write: {}", ex.getMessage());
        }
    }

    private void drain()
    {
        while (true)
        {
            Runnable write;
            synchronized (queue)
            {
                write = queue.poll();
                if (write == null)
                {
                    draining = false;
                    return;
                }
            }
            try
            {
                write.run();
            }
            catch (RuntimeException ex)
            {
                log.warn("A local file write failed", ex);
            }
        }
    }
}
