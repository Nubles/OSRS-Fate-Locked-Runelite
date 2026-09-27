package com.fatelocked.guardian;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Strict Mode's one-minute pause. It counts on a monotonic clock, so moving
 * the computer's clock neither ends it early nor stretches it, and the
 * plugin clears it when it shuts down.
 */
public final class StrictModePause
{
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final LongSupplier nanoTime;
    private boolean paused;
    private long pausedUntil;

    /** @param nanoTime a monotonic clock in nanoseconds, such as {@code System::nanoTime} */
    public StrictModePause(LongSupplier nanoTime)
    {
        this.nanoTime = nanoTime;
    }

    public synchronized void pauseFor(Duration duration)
    {
        pausedUntil = nanoTime.getAsLong() + duration.toNanos();
        paused = true;
    }

    public synchronized void resume()
    {
        paused = false;
    }

    public synchronized boolean isPaused()
    {
        return remainingSeconds() > 0;
    }

    public synchronized long remainingSeconds()
    {
        if (!paused) return 0;
        // Compared as a difference, as System.nanoTime requires.
        long remaining = pausedUntil - nanoTime.getAsLong();
        if (remaining <= 0)
        {
            paused = false;
            return 0;
        }
        return (remaining + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND;
    }
}
