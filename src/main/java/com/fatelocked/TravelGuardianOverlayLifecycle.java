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
        if (mouseRegistered) return;
        if (!overlayAdded)
        {
            addOverlay.run();
            overlayAdded = true;
        }
        try
        {
            registerMouse.run();
            mouseRegistered = true;
        }
        catch (RuntimeException registrationFailure)
        {
            try
            {
                removeOverlay.run();
                overlayAdded = false;
            }
            catch (RuntimeException rollbackFailure)
            {
                registrationFailure.addSuppressed(rollbackFailure);
            }
            throw registrationFailure;
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
