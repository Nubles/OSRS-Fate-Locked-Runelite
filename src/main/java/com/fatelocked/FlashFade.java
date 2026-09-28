package com.fatelocked;

/**
 * The fade when a locked area is entered (U20): one fade out, never a pulse. It starts at its
 * strongest and only weakens, easing out, so it can't flicker. The old frame pulsed at about
 * 2.5 Hz for 1.6 seconds; RuneLite's own flash toggles at 1.25 Hz.
 */
final class FlashFade
{
    /** How long the fade lasts. */
    static final long DURATION_MS = 1_100;
    /** Its alpha at the start, the strongest it gets. */
    static final int MAX_ALPHA = 200;

    private FlashFade()
    {
    }

    /** The fade's alpha this long after it started: the strongest at the start, and none from the end on. */
    static int alpha(long elapsedMs)
    {
        if (elapsedMs < 0 || elapsedMs >= DURATION_MS)
        {
            return 0;
        }
        double left = 1 - (double) elapsedMs / DURATION_MS;
        return (int) Math.round(MAX_ALPHA * left * left);
    }
}
