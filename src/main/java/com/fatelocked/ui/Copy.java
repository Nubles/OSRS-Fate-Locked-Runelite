package com.fatelocked.ui;

import java.time.Duration;

/** Phrases built from values: ages, short ids and counts, in sentence case. */
public final class Copy
{
    private static final String ELLIPSIS = "…";

    private Copy()
    {
    }

    /** How long ago something happened: "just now", "4 min ago", "3 h ago", "2 days ago". */
    public static String ago(Duration elapsed)
    {
        long seconds = elapsed.getSeconds();
        if (seconds < 60)
        {
            return "just now";
        }
        long minutes = seconds / 60;
        if (minutes < 60)
        {
            return minutes + " min ago";
        }
        long hours = minutes / 60;
        if (hours < 24)
        {
            return hours + " h ago";
        }
        long days = hours / 24;
        return days == 1 ? "1 day ago" : days + " days ago";
    }

    /** An id shown by its last four characters, so a screenshot never carries it whole. */
    public static String shortId(String id)
    {
        if (id == null || id.isEmpty())
        {
            return null;
        }
        return id.length() <= 4 ? id : ELLIPSIS + id.substring(id.length() - 4);
    }

    /** Progress in the unit the run counts. */
    public static String unlocked(int unlocked, int total, String unit)
    {
        return unlocked + " of " + total + " " + unit + " unlocked";
    }
}
