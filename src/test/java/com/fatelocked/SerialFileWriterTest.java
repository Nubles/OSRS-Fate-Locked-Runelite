package com.fatelocked;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.Assert.assertEquals;

public class SerialFileWriterTest
{
    private final List<Runnable> executorTasks = new ArrayList<>();
    private final SerialFileWriter writer = new SerialFileWriter(executorTasks::add);

    @Test
    public void writesRunOffTheCallingThreadInTheOrderTheyWereAskedFor()
    {
        List<String> written = new ArrayList<>();

        writer.submit(() -> written.add("history"));
        writer.submit(() -> written.add("audit"));
        writer.submit(() -> written.add("slayer"));

        // Nothing is written by the caller, and one drain runs them all.
        assertEquals(0, written.size());
        assertEquals(1, executorTasks.size());

        runExecutor();
        assertEquals(List.of("history", "audit", "slayer"), written);
    }

    @Test
    public void aFailedWriteDoesNotStopTheOnesAfterIt()
    {
        List<String> written = new ArrayList<>();

        writer.submit(() -> { throw new IllegalStateException("disk full"); });
        writer.submit(() -> written.add("audit"));
        runExecutor();

        assertEquals(List.of("audit"), written);
    }

    @Test
    public void aWriteAskedForAfterTheQueueEmptiedStartsANewDrain()
    {
        List<String> written = new ArrayList<>();

        writer.submit(() -> written.add("first"));
        runExecutor();
        writer.submit(() -> written.add("second"));

        assertEquals(1, executorTasks.size());
        runExecutor();
        assertEquals(List.of("first", "second"), written);
    }

    @Test
    public void aRefusedDrainLetsTheNextWriteTryAgain()
    {
        List<String> written = new ArrayList<>();
        boolean[] refuse = {true};
        SerialFileWriter refusing = new SerialFileWriter(task -> {
            if (refuse[0])
            {
                throw new RejectedExecutionException("shutting down");
            }
            executorTasks.add(task);
        });

        refusing.submit(() -> written.add("first"));
        refuse[0] = false;
        refusing.submit(() -> written.add("second"));
        runExecutor();

        assertEquals(List.of("first", "second"), written);
    }

    private void runExecutor()
    {
        while (!executorTasks.isEmpty())
        {
            executorTasks.remove(0).run();
        }
    }
}
