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
     * 100 only once everything is unlocked, and at most 99 before then, as the tracker and the
     * Discord bot show it: 622 of 623 chunks is 99%, not a rounded 100%.
     */
    public int percent()
    {
        if (total <= 0) return -1;
        if (unlocked >= total) return 100;
        return Math.min(99, (int) Math.round(100.0 * unlocked / total));
    }
}
