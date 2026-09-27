package com.fatelocked.sidebar;

import lombok.Value;

/** The Run section: whose run it is, its keys and Fate Points, and how far it has come. */
@Value
public class RunModel
{
    /** "Nubles (you)", or who the run belongs to and who is logged in. */
    String character;
    /** The run id by its last four characters, or null. */
    String runId;
    int keys;
    int omniKeys;
    int chaosKeys;
    int fatePoints;
    /** The ritual waiting on the next roll, by name, or null. */
    String ritual;
    /** The next pinned goal, or null. */
    String goal;
    /** "15 of 187 areas unlocked", or null without progress. */
    String progress;
    double fraction;
}
