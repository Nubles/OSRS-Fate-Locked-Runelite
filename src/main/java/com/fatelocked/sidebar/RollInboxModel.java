package com.fatelocked.sidebar;

import lombok.Value;

/** The Roll inbox section: what RuneLite noticed locally, and the warnings in force. */
@Value
public class RollInboxModel
{
    int localEvents;
    /** Events the detectors weren't sure of. */
    int needsChecking;
    /** Warnings in force now: a locked area, a locked Slayer task, over-tier gear. */
    int warnings;
    /** Whether saving the local history failed. */
    boolean saveFailed;
}
