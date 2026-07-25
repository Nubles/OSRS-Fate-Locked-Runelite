package com.fatelocked;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TravelGuardianOverlayLifecycleTest
{
    @Test
    public void registrationFailureRollsBackAnAddedOverlay()
    {
        RecordingOperations operations = new RecordingOperations();
        operations.failRegister = true;
        TravelGuardianOverlayLifecycle lifecycle = operations.lifecycle();

        assertThrows(IllegalStateException.class, lifecycle::start);

        assertEquals(Arrays.asList("add", "register", "remove"),
            operations.calls);
        lifecycle.stop();
        assertEquals(Arrays.asList("add", "register", "remove"),
            operations.calls);
    }

    @Test
    public void unregisterFailureStillRemovesTheOverlay()
    {
        RecordingOperations operations = new RecordingOperations();
        TravelGuardianOverlayLifecycle lifecycle = operations.lifecycle();
        lifecycle.start();
        operations.failUnregister = true;

        assertThrows(IllegalStateException.class, lifecycle::stop);

        assertEquals(Arrays.asList(
            "add", "register", "unregister", "remove"), operations.calls);
    }

    @Test
    public void partialShutdownCanBeRetriedWithoutRepeatingSuccessfulCleanup()
    {
        RecordingOperations operations = new RecordingOperations();
        TravelGuardianOverlayLifecycle lifecycle = operations.lifecycle();
        lifecycle.start();
        operations.failUnregister = true;
        operations.failRemove = true;
        assertThrows(IllegalStateException.class, lifecycle::stop);

        operations.failUnregister = false;
        operations.failRemove = false;
        lifecycle.stop();
        lifecycle.stop();

        assertEquals(Arrays.asList(
            "add", "register",
            "unregister", "remove",
            "unregister", "remove"), operations.calls);
    }

    @Test
    public void normalLifecycleIsSymmetricAndIdempotent()
    {
        RecordingOperations operations = new RecordingOperations();
        TravelGuardianOverlayLifecycle lifecycle = operations.lifecycle();

        lifecycle.start();
        lifecycle.start();
        lifecycle.stop();
        lifecycle.stop();

        assertEquals(Arrays.asList(
            "add", "register", "unregister", "remove"), operations.calls);
    }

    private static final class RecordingOperations
    {
        private final List<String> calls = new ArrayList<>();
        private boolean failRegister;
        private boolean failUnregister;
        private boolean failRemove;

        private TravelGuardianOverlayLifecycle lifecycle()
        {
            return new TravelGuardianOverlayLifecycle(
                () -> calls.add("add"),
                () -> {
                    calls.add("register");
                    if (failRegister)
                    {
                        throw new IllegalStateException("register");
                    }
                },
                () -> {
                    calls.add("unregister");
                    if (failUnregister)
                    {
                        throw new IllegalStateException("unregister");
                    }
                },
                () -> {
                    calls.add("remove");
                    if (failRemove)
                    {
                        throw new IllegalStateException("remove");
                    }
                });
        }
    }
}
