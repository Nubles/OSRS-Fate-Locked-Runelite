package com.fatelocked;

import java.awt.Rectangle;
import java.awt.geom.Area;

/**
 * Where on the world map this plugin may draw (U18): the map, less the overview and the surface
 * selector wherever they're shown, as RuneLite clips its own map. The old clip was the whole
 * map, so the tint covered both, and hovering the overview showed a chunk's tooltip.
 */
final class WorldMapClip
{
    private WorldMapClip()
    {
    }

    /** The map less whichever of the overview and the selector are shown (null when not). */
    static Area of(Rectangle map, Rectangle overview, Rectangle selector)
    {
        Area clip = new Area(map);
        if (overview != null)
        {
            clip.subtract(new Area(overview));
        }
        if (selector != null)
        {
            clip.subtract(new Area(selector));
        }
        return clip;
    }
}
