package com.fatelocked.guardian;

import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StrictModePauseTest
{
    private final AtomicLong nanos = new AtomicLong(Long.MAX_VALUE - Duration.ofSeconds(30).toNanos());
    private final StrictModePause pause = new StrictModePause(nanos::get);

    @Test
    public void pausesForExactlySixtySeconds()
    {
        pause.pauseFor(Duration.ofSeconds(60));
        assertEquals(60, pause.remainingSeconds());
        advance(Duration.ofSeconds(17));
        assertEquals(43, pause.remainingSeconds());
        advance(Duration.ofMillis(42_500));
        assertEquals("a part-second still counts", 1, pause.remainingSeconds());
        advance(Duration.ofMillis(500));
        assertFalse(pause.isPaused());
    }

    /** The clock starts near its limit, so the countdown also survives nanoTime wrapping. */
    @Test
    public void countsElapsedTimeAcrossTheClocksWrap()
    {
        pause.pauseFor(Duration.ofSeconds(60));
        advance(Duration.ofSeconds(45));
        assertTrue(pause.isPaused());
        assertEquals(15, pause.remainingSeconds());
    }

    @Test
    public void resumeEndsThePauseAtOnce()
    {
        pause.pauseFor(Duration.ofSeconds(60));
        pause.resume();
        assertFalse(pause.isPaused());
        assertEquals(0, pause.remainingSeconds());
    }

    private void advance(Duration duration)
    {
        nanos.addAndGet(duration.toNanos());
    }
}
