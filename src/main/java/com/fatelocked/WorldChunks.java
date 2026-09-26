package com.fatelocked;

import net.runelite.api.coords.WorldPoint;

/**
 * Adapter from RuneLite world points to the rules core's chunks, so
 * {@link CanonicalChunk} itself needs no RuneLite types.
 */
public final class WorldChunks
{
    private WorldChunks()
    {
    }

    /** The chunk of a world point as given: instances and boats are not mapped here. */
    public static CanonicalChunk of(WorldPoint point)
    {
        return CanonicalChunk.ofTile(point.getX(), point.getY());
    }
}
