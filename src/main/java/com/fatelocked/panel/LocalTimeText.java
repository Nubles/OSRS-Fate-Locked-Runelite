package com.fatelocked.panel;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * A moment as the player's own clock shows it: "18:03" today, "Mon 18:03"
 * within a week either side, "27 Jul 18:03" further off. An absolute time,
 * unlike "5m ago", stays true however long the sidebar goes unrefreshed.
 */
public final class LocalTimeText
{
    private static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_AND_TIME =
        DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_AND_TIME =
        DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);

    private LocalTimeText()
    {
    }

    public static String of(Instant at, Instant now, ZoneId zone)
    {
        ZonedDateTime local = at.atZone(zone);
        long days = Math.abs(ChronoUnit.DAYS.between(
            now.atZone(zone).toLocalDate(), local.toLocalDate()));
        if (days == 0)
        {
            return TIME.format(local);
        }
        return (days < 7 ? DAY_AND_TIME : DATE_AND_TIME).format(local);
    }

    /** For the player's own clock. */
    public static String of(Instant at)
    {
        return of(at, Instant.now(), ZoneId.systemDefault());
    }
}
