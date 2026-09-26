package com.fatelocked;

import lombok.Value;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Slayer task as a game message states it (B11): "You're assigned to kill
 * goblins; only 20 more to go." and Konar's "...to kill aberrant spectres in
 * the Catacombs of Kourend; only 105 more to go.", with the place kept.
 */
@Value
class SlayerAssignment
{
    /** The monster from "to kill [count] ..." up to "in", ";", ":" or ".", then Konar's place. */
    private static final Pattern TASK = Pattern.compile(
        "to kill\\s+(?:\\d+\\s+)?(?:the\\s+)?(.+?)(?:\\s+in\\s+(?:the\\s+)?([^;:.]+?))?\\s*(?:[;:.]|$)",
        Pattern.CASE_INSENSITIVE);
    /** Only Konar gives a place, and the tracker lists her tasks under this name. */
    static final String KONAR = "konar quo maten";
    /** SLAYER_MASTER values RuneLite's own Slayer plugin relies on; the rest aren't verified. */
    private static final int KRYSTILIA = 7;
    private static final int MORTIMER = 10;

    String task;
    /** Konar's place; null for every other master. */
    String location;
    /** The master, as the tracker names them, when known; null otherwise. */
    String master;

    /** The task a message states, or null when it states none. */
    static SlayerAssignment fromChat(String message, int masterVarbit)
    {
        if (message == null) return null;
        Matcher match = TASK.matcher(message);
        if (!match.find()) return null;
        String task = match.group(1).trim();
        String location = match.group(2) == null ? null : match.group(2).trim();
        if (task.isEmpty()) return null;
        return new SlayerAssignment(task, location, master(masterVarbit, location));
    }

    private static String master(int masterVarbit, String location)
    {
        if (location != null) return KONAR;
        switch (masterVarbit)
        {
            case KRYSTILIA: return "krystilia";
            case MORTIMER: return "mortimer";
            default: return null;
        }
    }
}
