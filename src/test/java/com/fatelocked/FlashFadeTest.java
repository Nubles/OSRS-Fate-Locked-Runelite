package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** U20: the locked-area fade is one fade out, never a pulse. */
public class FlashFadeTest
{
    @Test
    public void itStartsStrongestAndOnlyWeakensToNothing()
    {
        assertEquals(FlashFade.MAX_ALPHA, FlashFade.alpha(0));
        int previous = FlashFade.MAX_ALPHA;
        for (long ms = 0; ms <= 3_000; ms++)
        {
            int alpha = FlashFade.alpha(ms);
            assertTrue(ms + " ms: never stronger again", alpha <= previous);
            previous = alpha;
        }
        assertTrue("still there near the end", FlashFade.alpha(FlashFade.DURATION_MS - 100) > 0);
        assertEquals(0, FlashFade.alpha(FlashFade.DURATION_MS));
        assertEquals(0, FlashFade.alpha(FlashFade.DURATION_MS + 1));
        assertEquals("not begun", 0, FlashFade.alpha(-1));
    }

    /** It eases out: most of its strength goes early, so it's quiet well before it ends. */
    @Test
    public void itEasesOut()
    {
        assertTrue(FlashFade.alpha(FlashFade.DURATION_MS / 2) < FlashFade.MAX_ALPHA / 2);
    }
}
