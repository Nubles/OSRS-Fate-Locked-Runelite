package com.fatelocked.sidebar;

import com.fatelocked.events.FateEventType;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * The Roll inbox section (Stage 4): what RuneLite noticed for this run, newest first, for Copy for
 * tracker, and the warnings in force.
 */
@Value
@Builder
public class RollInboxModel
{
    /** The most events the card lists; the rest are counted. */
    public static final int MAX_ROWS = 5;

    /** The newest events, at most {@link #MAX_ROWS}. */
    @Builder.Default
    List<Row> rows = Collections.emptyList();
    /** Events beyond the rows listed. */
    int more;
    /** Events not copied yet. */
    int newEvents;
    /** Events copied already, which a copy takes again. */
    int copied;
    /** Warnings in force now: a locked area, a locked Slayer task, over-tier gear. */
    int warnings;
    /** Whether saving the local history failed. */
    boolean saveFailed;
    /** Whose events these are, while the run is linked to no one; null otherwise. */
    String character;
    /** What the last copy did, or why it couldn't; null for none. */
    String notice;

    /** One event the card lists. */
    @Value
    public static class Row
    {
        String eventId;
        FateEventType type;
        String label;
        /** The skill whose icon the row shows, for a level or a Slayer task; null otherwise. */
        String skill;
        /** Whether the tracker will ask the player to check it. */
        boolean needsChecking;
        boolean copied;
    }
}
