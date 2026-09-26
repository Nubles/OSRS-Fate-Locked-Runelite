package com.fatelocked.rules;

import lombok.Value;

/**
 * How much of the run is unlocked: named areas, and chunks. Today's counts
 * from the rules' area lists; the tracker's own progress (E5) replaces them.
 */
@Value
public class Progress
{
    int unlockedAreas;
    int totalAreas;
    int unlockedChunks;
    int totalChunks;

    /** Percent of chunks unlocked, rounded; -1 when there are none. */
    public int percent()
    {
        return totalChunks <= 0 ? -1 : (int) Math.round(100.0 * unlockedChunks / totalChunks);
    }
}
