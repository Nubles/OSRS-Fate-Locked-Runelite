package com.fatelocked.guardian;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** B16 (G10): Strict Mode's status, one view for the sidebar row and the HUD line. */
public class StrictModeStatusViewTest
{
    @Test
    public void eachStateReadsAsItShould()
    {
        StrictModeStatusView active = StrictModeStatusView.of(true, false, 0, null);
        assertEquals(StrictModeStatusView.Tone.ACTIVE, active.getTone());
        assertEquals("Active", active.getText());
        assertNull(active.getExplanation());

        StrictModeStatusView paused = StrictModeStatusView.of(true, true, 42, null);
        assertEquals(StrictModeStatusView.Tone.PAUSED, paused.getTone());
        assertEquals("Paused · 42s", paused.getText());
        assertEquals(42, paused.getSecondsLeft());

        StrictModeStatusView inactive = StrictModeStatusView.of(true, false, 0, "you are not logged in");
        assertEquals(StrictModeStatusView.Tone.INACTIVE, inactive.getTone());
        assertEquals("Inactive", inactive.getText());
        assertEquals("Not blocking anything: you are not logged in.", inactive.getExplanation());

        StrictModeStatusView off = StrictModeStatusView.of(false, true, 42, "no tracker rules are loaded");
        assertEquals(StrictModeStatusView.Tone.OFF, off.getTone());
        assertEquals("Off", off.getText());
    }

    @Test
    public void aPauseShowsFirstAndTheHudHidesOff()
    {
        assertEquals(StrictModeStatusView.Tone.PAUSED,
            StrictModeStatusView.of(true, true, 5, "the rules are more than 15 minutes old").getTone());
        assertEquals("Paused · 0s", StrictModeStatusView.of(true, true, -3, null).getText());
        assertEquals(StrictModeStatusView.Tone.ACTIVE, StrictModeStatusView.of(true, false, 0, "  ").getTone());

        assertFalse(StrictModeStatusView.of(false, false, 0, null).isShownOnHud());
        assertTrue(StrictModeStatusView.of(true, false, 0, null).isShownOnHud());
        assertTrue(StrictModeStatusView.of(true, true, 9, null).isShownOnHud());
        assertTrue(StrictModeStatusView.of(true, false, 0, "you are not logged in").isShownOnHud());
    }
}
