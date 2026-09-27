package com.fatelocked.rules;

import lombok.Value;

/**
 * How much of the run is unlocked, as the run card counts it: named areas,
 * or chunks in a Chunked run, with the land chunks owned for the percentage.
 * The tracker's own counts (R9), or for older rules the rules' area lists.
 */
@Value
public class Progress
{
    public static final String AREAS = "areas";
    public static final String CHUNKS = "chunks";

    /** What unlocked and total count: {@link #AREAS} or {@link #CHUNKS}. */
    String unit;
    int unlocked;
    int total;
    int chunksUnlocked;
    int chunksTotal;

    /** Percent of chunks unlocked, rounded; -1 when there are none. */
    public int percent()
    {
        return chunksTotal <= 0 ? -1 : (int) Math.round(100.0 * chunksUnlocked / chunksTotal);
    }
}
