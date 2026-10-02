package com.fatelocked.rules;

import lombok.Value;

/**
 * How much of the run is unlocked, as the run card counts it: named areas,
 * or chunks in a Chunked run, and the land chunks owned besides. The
 * tracker's own counts (R9), or for older rules the rules' area lists.
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

    /**
     * Percent unlocked of what the count counts, rounded, so "15 of 187 areas" is 8% (the
     * owner's call T7: it was 7%, of the map's chunks); -1 when there is nothing to count.
     */
    public int percent()
    {
        return total <= 0 ? -1 : (int) Math.round(100.0 * unlocked / total);
    }
}
