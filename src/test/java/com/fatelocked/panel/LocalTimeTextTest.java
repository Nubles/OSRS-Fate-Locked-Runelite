package com.fatelocked.panel;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;

public class LocalTimeTextTest
{
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    /** Saturday 26 September 2026, 18:00 in London (BST). */
    private static final Instant NOW = Instant.parse("2026-09-26T17:00:00Z");

    @Test
    public void todayIsJustTheTimeOnThePlayersClock()
    {
        assertEquals("17:03", LocalTimeText.of(Instant.parse("2026-09-26T16:03:00Z"), NOW, LONDON));
        assertEquals("16:03", LocalTimeText.of(
            Instant.parse("2026-09-26T16:03:00Z"), NOW, ZoneId.of("UTC")));
    }

    @Test
    public void withinAWeekTheDayIsNamed()
    {
        assertEquals("Wed 09:15", LocalTimeText.of(Instant.parse("2026-09-23T08:15:00Z"), NOW, LONDON));
        // A check due just after midnight.
        assertEquals("Sun 00:05", LocalTimeText.of(Instant.parse("2026-09-26T23:05:00Z"), NOW, LONDON));
    }

    @Test
    public void furtherOffTheDateIsShown()
    {
        assertEquals("27 Jul 15:05", LocalTimeText.of(Instant.parse("2026-07-27T14:05:06Z"), NOW, LONDON));
        assertEquals("19 Sep 18:00", LocalTimeText.of(Instant.parse("2026-09-19T17:00:00Z"), NOW, LONDON));
    }
}
