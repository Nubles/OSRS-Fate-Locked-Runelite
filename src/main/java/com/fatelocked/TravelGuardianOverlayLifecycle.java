package com.fatelocked;

/**
 * Tracks the two independently fallible registrations used by the travel
 * banner so partial startup and shutdown can be retried safely.
 */
final class TravelGuardianOverlayLifecycle
{
    private final Runnable addOverlay;
    private final Runnable registerMouse;
    private final Runnable unregisterMouse;
    private final Runnable removeOverlay;
    private boolean overlayAdded;
    private boolean mouseRegistered;

    TravelGuardianOverlayLifecycle(
        Runnable addOverlay,
        Runnable registerMouse,
        Runnable unregisterMouse,
        Runnable removeOverlay)
    {
        this.addOverlay = addOverlay;
        this.registerMouse = registerMouse;
        this.unregisterMouse = unregisterMouse;
        this.removeOverlay = removeOverlay;
    }

    synchronized void start()
    {
        if (!overlayAdded)
        {
            overlayAdded = true;
            try
            {
                addOverlay.run();
            }
            catch (RuntimeException addFailure)
            {
                rollbackOverlay(addFailure);
                throw addFailure;
            }
        }
        if (!mouseRegistered)
        {
            mouseRegistered = true;
            try
            {
                registerMouse.run();
            }
            catch (RuntimeException registrationFailure)
            {
                rollbackMouse(registrationFailure);
                rollbackOverlay(registrationFailure);
                throw registrationFailure;
            }
        }
    }

    private void rollbackMouse(RuntimeException startupFailure)
    {
        if (!mouseRegistered) return;
        try
        {
            unregisterMouse.run();
            mouseRegistered = false;
        }
        catch (RuntimeException cleanupFailure)
        {
            startupFailure.addSuppressed(cleanupFailure);
        }
    }

    private void rollbackOverlay(RuntimeException startupFailure)
    {
        if (!overlayAdded) return;
        try
        {
            removeOverlay.run();
            overlayAdded = false;
        }
        catch (RuntimeException cleanupFailure)
        {
            startupFailure.addSuppressed(cleanupFailure);
        }
    }

    synchronized void stop()
    {
        RuntimeException failure = null;
        if (mouseRegistered)
        {
            try
            {
                unregisterMouse.run();
                mouseRegistered = false;
            }
            catch (RuntimeException ex)
            {
                failure = ex;
            }
        }
        if (overlayAdded)
        {
            try
            {
                removeOverlay.run();
                overlayAdded = false;
            }
            catch (RuntimeException ex)
            {
                if (failure == null)
                {
                    failure = ex;
                }
                else
                {
                    failure.addSuppressed(ex);
                }
            }
        }
        if (failure != null) throw failure;
    }
}
