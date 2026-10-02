package com.fatelocked.ui;

import com.fatelocked.rules.PermissionStatus;

/**
 * The words players read, as the web app says them. Every surface takes its status
 * words, names and tags from here, so the sidebar, the HUD, chat and the web guide
 * agree (U12).
 */
public final class Terms
{
    /** A place the run can use. */
    public static final String UNLOCKED = "Unlocked";
    /** A row the player can do now. */
    public static final String CAN_DO = "Can do";
    /** Owned, but not usable yet. */
    public static final String NOT_READY = "Not ready";
    public static final String LOCKED = "Locked";
    /** A row the tracker can't decide. */
    public static final String NEEDS_CHECKING = "Needs checking";
    /** A place the tracker doesn't map. */
    public static final String UNCHARTED = "Uncharted";
    /** Next to the unlocked area, in Chunked mode. */
    public static final String FRONTIER = "Frontier";

    public static final String STRICT_MODE = "Strict Mode";
    public static final String KEYS = "Keys";
    public static final String OMNI_KEYS = "Omni-Keys";
    public static final String CHAOS_KEYS = "Chaos Keys";
    public static final String FATE_POINTS = "Fate Points";
    public static final String DIFFERENT_CHARACTER = "Different character";
    /** The Roll inbox card's button that copies what RuneLite noticed, for the tracker (Stage 4). */
    public static final String COPY_FOR_TRACKER = "Copy for tracker";
    /** The tracker's Roll Inbox button that brings the copy in. */
    public static final String PASTE_FROM_RUNELITE = "Paste from RuneLite";
    /** An event in the Roll inbox card that hasn't been copied yet. */
    public static final String NEW = "New";
    /** An event in the Roll inbox card that has been copied for the tracker. */
    public static final String COPIED = "Copied";

    /** Appended to a right-click option the rules lock. */
    public static final String LOCKED_TAG = " (Locked)";

    private Terms()
    {
    }

    /** What a place is, in a word. */
    public static String place(PermissionStatus status)
    {
        switch (status)
        {
            case ALLOWED:
                return UNLOCKED;
            case NOT_READY:
                return NOT_READY;
            case LOCKED:
                return LOCKED;
            default:
                return UNCHARTED;
        }
    }

    /** What a row of a place's content is, in a word. */
    public static String row(PermissionStatus status)
    {
        switch (status)
        {
            case ALLOWED:
                return CAN_DO;
            case NOT_READY:
                return NOT_READY;
            case LOCKED:
                return LOCKED;
            default:
                return NEEDS_CHECKING;
        }
    }

    /**
     * The ritual waiting on the next roll, by the name the web app gives it, or null
     * for none or one this build doesn't know, so a raw id is never shown.
     */
    public static String ritual(String id)
    {
        if ("LUCK".equals(id))
        {
            return "Ritual of Clarity";
        }
        if ("GREED".equals(id))
        {
            return "Ritual of Greed";
        }
        return null;
    }
}
