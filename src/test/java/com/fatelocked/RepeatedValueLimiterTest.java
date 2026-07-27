package com.fatelocked;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RepeatedValueLimiterTest
{
    private static final long THIRTY_SECONDS = 30_000L;

    @Test
    public void reportsTheFirstValue()
    {
        RepeatedValueLimiter limiter =
            new RepeatedValueLimiter(THIRTY_SECONDS);

        assertTrue(limiter.shouldReport("first", 1_000L));
    }

    @Test
    public void suppressesTheSameValueInsideTheWindow()
    {
        RepeatedValueLimiter limiter =
            new RepeatedValueLimiter(THIRTY_SECONDS);

        assertTrue(limiter.shouldReport("first", 1_000L));
        assertFalse(limiter.shouldReport("first", 30_999L));
    }

    @Test
    public void reportsADifferentValueImmediately()
    {
        RepeatedValueLimiter limiter =
            new RepeatedValueLimiter(THIRTY_SECONDS);

        assertTrue(limiter.shouldReport("first", 1_000L));
        assertTrue(limiter.shouldReport("different", 2_000L));
    }

    @Test
    public void reportsTheSameValueAtTheWindowBoundary()
    {
        RepeatedValueLimiter limiter =
            new RepeatedValueLimiter(THIRTY_SECONDS);

        assertTrue(limiter.shouldReport("first", 1_000L));
        assertTrue(limiter.shouldReport("first", 31_000L));
    }

    @Test
    public void reportsAConcurrentIdenticalValueOnlyOnce() throws Exception
    {
        int callers = 16;
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        try
        {
            for (int round = 0; round < 100; round++)
            {
                RepeatedValueLimiter limiter =
                    new RepeatedValueLimiter(THIRTY_SECONDS);
                CountDownLatch ready = new CountDownLatch(callers);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Boolean>> results = new ArrayList<>();
                for (int caller = 0; caller < callers; caller++)
                {
                    results.add(executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return limiter.shouldReport("same", 1_000L);
                    }));
                }
                ready.await();
                start.countDown();

                int reports = 0;
                for (Future<Boolean> result : results)
                {
                    if (result.get()) reports++;
                }
                assertEquals(
                    "concurrent reports in round " + round,
                    1,
                    reports);
            }
        }
        finally
        {
            executor.shutdownNow();
        }
    }
}
