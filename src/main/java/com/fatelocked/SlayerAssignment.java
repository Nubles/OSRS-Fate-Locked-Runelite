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
    /**
     * A master handing out a task in dialogue (Wiki transcripts of Turael, Duradel, Krystilia):
     * "Your new task is to kill 15 cows." Any place named is dropped: only Konar's are kept.
     */
    private static final Pattern NEW_TASK = Pattern.compile(
        "new\\s+task\\s+is\\s+to\\s+kill\\s+(?:\\d+\\s+)?(?:the\\s+)?(.+?)(?:\\s+in\\s+(?:the\\s+)?[^;:.]+?)?\\s*(?:[;:.]|$)",
        Pattern.CASE_INSENSITIVE);
    /** Konar's own words in dialogue: "You are to bring balance to 105 aberrant spectres in the Slayer Tower." */
    private static final Pattern BALANCE = Pattern.compile(
        "bring\\s+balance\\s+to\\s+(?:\\d+\\s+)?(?:the\\s+)?(.+?)(?:\\s+in\\s+(?:the\\s+)?([^;:.]+?))?\\s*(?:[;:.]|$)",
        Pattern.CASE_INSENSITIVE);
    /**
     * Only Konar gives a place (Krystilia's "in the Wilderness" isn't kept), and the tracker
     * lists her tasks under this name.
     */
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
        // Krystilia's lines say "in the Wilderness", which is her area, not one of Konar's places.
        if (masterVarbit == KRYSTILIA) return new SlayerAssignment(task, null, "krystilia");
        return new SlayerAssignment(task, location, master(masterVarbit, location));
    }

    /**
     * The task a master's dialogue line hands out, or null for any other line. Only the
     * assignment lines are read: other NPCs say "to kill" too, and Turael's first task
     * ("...you'll need to kill 15 of them.") names the monster elsewhere.
     */
    static SlayerAssignment fromDialogue(String message, int masterVarbit)
    {
        if (message == null) return null;
        Matcher balance = BALANCE.matcher(message);
        if (balance.find())
        {
            String task = balance.group(1).trim();
            String location = balance.group(2) == null ? null : balance.group(2).trim();
            return task.isEmpty() ? null : new SlayerAssignment(task, location, KONAR);
        }
        Matcher assigned = NEW_TASK.matcher(message);
        if (!assigned.find()) return null;
        String task = assigned.group(1).trim();
        return task.isEmpty() ? null : new SlayerAssignment(task, null, master(masterVarbit, null));
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
