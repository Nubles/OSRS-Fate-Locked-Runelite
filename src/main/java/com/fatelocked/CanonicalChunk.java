package com.fatelocked;

import lombok.Value;

/**
 * A 64-tile chunk in canonical OSRS worldspace — i.e. the same {@code cx=x>>6,
 * cy=y>>6} that RuneLite produces. The web app stores chunks in a translated
 * space; {@link FateLockedBundle} normalizes everything to this class so
 * downstream code never has to think about the offset.
 *
 * <p>Part of the RuneLite-free rules core ({@code RulesCoreBoundaryTest}):
 * the adapter turns world points into tile coordinates.
 */
@Value
public class CanonicalChunk
{
    int cx;
    int cy;

    /** The chunk holding world tile {@code (x, y)}. */
    public static CanonicalChunk ofTile(int x, int y)
    {
        return new CanonicalChunk(x >> 6, y >> 6);
    }
}
