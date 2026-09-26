package com.fatelocked.guardian;

import lombok.Value;

/**
 * Strict Mode's status as the sidebar row and the HUD line show it (B16,
 * G10): "Active", "Paused · 42s", "Inactive" with why, or "Off", which the
 * HUD hides. A pause shows first, since the player chose it and can end it.
 */
@Value
public class StrictModeStatusView
{
    public enum Tone { OFF, ACTIVE, PAUSED, INACTIVE }

    Tone tone;
    String text;
    /** "Not blocking anything: …" for INACTIVE; null otherwise. */
    String explanation;
    /** Seconds left in a pause; 0 otherwise. */
    long secondsLeft;

    /**
     * @param inactiveReason why Strict Mode can't act (its readiness reason),
     *                       or null when it can
     */
    public static StrictModeStatusView of(boolean enabled, boolean paused, long secondsLeft, String inactiveReason)
    {
        if (!enabled) return new StrictModeStatusView(Tone.OFF, "Off", null, 0);
        if (paused)
        {
            long left = Math.max(0, secondsLeft);
            return new StrictModeStatusView(Tone.PAUSED, "Paused · " + left + "s", null, left);
        }
        if (inactiveReason != null && !inactiveReason.trim().isEmpty())
        {
            return new StrictModeStatusView(Tone.INACTIVE, "Inactive",
                "Not blocking anything: " + inactiveReason.trim() + ".", 0);
        }
        return new StrictModeStatusView(Tone.ACTIVE, "Active", null, 0);
    }

    /** The HUD shows a line unless Strict Mode is off. */
    public boolean isShownOnHud()
    {
        return tone != Tone.OFF;
    }
}
