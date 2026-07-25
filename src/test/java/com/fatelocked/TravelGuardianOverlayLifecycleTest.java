package com.fatelocked;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TravelGuardianOverlayLifecycleTest
{
    @Test
    public void addMutationFailureIsRolledBackAndSuccessfulRollbackClearsResponsibility()
    {
        RecordingOperations operations = new RecordingOperations();
        operations.failAdd = true;
        TravelGuardianOverlayLifecycle lifecycle = operations.lifecycle();

        IllegalStateException failure = assertThrows(
            IllegalStateException.class, lifecycle::start);

        assertEquals("add", failure.getMessage());
        assertEquals(0, failure.getSuppressed().length);
        assertFalse(operations.overlayPresent);
        assertEquals(Arrays.asList("add", "remove"), operations.calls);

        lifecycle.stop();
        assertEquals(Arrays.asList("add", "remove"), operations.calls);
    }

    @Test
    public void addMutationFailureKeepsFailedRollbackRetryable()
    {
        RecordingOperations operations = new RecordingOperations();
        operations.failAdd = true;
        operations.failRemove = true;
        TravelGuardianOverlayLifecycle lifecycle = operations.lifecycle();

        IllegalStateException failure = assertThrows(
            IllegalStateException.class, lifecycle::start);

        assertEquals("add", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("remove", failure.getSuppressed()[0].getMessage());
        assertTrue(operations.overlayPresent);
        assertEquals(Arrays.asList("add", "remove"), operations.calls);

        operations.failRemove = false;
        lifecycle.stop();
        lifecycle.stop();

        assertFalse(operations.overlayPresent);
        assertEquals(Arrays.asList("add", "remove", "remove"),
            operations.calls);
    }

    @Test
    public void registrationMutationFailureRollsBackMouseAndOverlay()
    {
        RecordingOperations operations = new RecordingOperations();
        operations.failRegister = true;
        TravelGuardianOverlayLifecycle lifecycle = operations.lifecycle();

        IllegalStateException failure = assertThrows(
            IllegalStateException.class, lifecycle::start);

        assertEquals("register", failure.getMessage());
        assertEquals(0, failure.getSuppressed().length);
        assertFalse(operations.mousePresent);
        assertFalse(operations.overlayPresent);
        assertEquals(Arrays.asList("add", "register", "unregister", "remove"),
            operations.calls);

        lifecycle.stop();
        assertEquals(Arrays.asList("add", "register", "unregister", "remove"),
            operations.calls);
    }

    @Test
    public void registrationMutationFailureKeepsEachFailedCleanupRetryable()
    {
        RecordingOperations operations = new RecordingOperations();
        operations.failRegister = true;
        operations.failUnregister = true;
        operations.failRemove = true;
        TravelGuardianOverlayLifecycle lifecycle = operations.lifecycle();

        IllegalStateException failure = assertThrows(
            IllegalStateException.class, lifecycle::start);

        assertEquals("register", failure.getMessage());
        assertEquals(2, failure.getSuppressed().length);
        assertEquals("unregister", failure.getSuppressed()[0].getMessage());
        assertEquals("remove", failure.getSuppressed()[1].getMessage());
        assertTrue(operations.mousePresent);
        assertTrue(operations.overlayPresent);
        assertEquals(Arrays.asList("add", "register", "unregister", "remove"),
            operations.calls);

        operations.failUnregister = false;
        operations.failRemove = false;
        lifecycle.stop();
        lifecycle.stop();

        assertFalse(operations.mousePresent);
        assertFalse(operations.overlayPresent);
        assertEquals(Arrays.asList(
            "add", "register", "unregister", "remove",
            "unregister", "remove"), operations.calls);
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

        assertFalse(operations.mousePresent);
        assertFalse(operations.overlayPresent);
        assertEquals(Arrays.asList(
            "add", "register", "unregister", "remove"), operations.calls);
    }

    private static final class RecordingOperations
    {
        private final List<String> calls = new ArrayList<>();
        private boolean failAdd;
        private boolean failRegister;
        private boolean failUnregister;
        private boolean failRemove;
        private boolean overlayPresent;
        private boolean mousePresent;

        private TravelGuardianOverlayLifecycle lifecycle()
        {
            return new TravelGuardianOverlayLifecycle(
                () -> {
                    calls.add("add");
                    overlayPresent = true;
                    if (failAdd)
                    {
                        throw new IllegalStateException("add");
                    }
                },
                () -> {
                    calls.add("register");
                    mousePresent = true;
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
                    mousePresent = false;
                },
                () -> {
                    calls.add("remove");
                    if (failRemove)
                    {
                        throw new IllegalStateException("remove");
                    }
                    overlayPresent = false;
                });
        }
    }
}
