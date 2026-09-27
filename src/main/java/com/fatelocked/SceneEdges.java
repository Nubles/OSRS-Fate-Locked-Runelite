package com.fatelocked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Value;

/**
 * Where chunk lines run through the loaded scene, and which of them are locked (U3). It is
 * worked out once per scene from the scene's 8-tile zones, so an instance, which is copied
 * zone by zone from anywhere, breaks where its zones do. On the surface the zone lines that
 * matter are the chunk lines.
 *
 * <p>A line between a locked place and an unlocked one is a locked edge. A line between two
 * different chunks that are alike is a plain edge. A place the rules don't decide draws no
 * edge, and neither does the scene's own border.
 */
final class SceneEdges
{
    /** Instances are copied in 8-tile zones. */
    static final int ZONE = 8;

    enum Kind { LOCKED, PLAIN }

    /** A zone: how the rules tint it, and the rules chunk it is a copy of (null when unknown). */
    @Value
    static class Zone
    {
        TintPolicy.Tint tint;
        CanonicalChunk rules;
    }

    /**
     * A straight run of edge along one line of scene tiles. A vertical run lies on
     * {@code x = line} from {@code y = from} to {@code y = to}; a horizontal one on
     * {@code y = line}. The locked side is +1 when it has the larger coordinate, -1 when the
     * smaller, and 0 for a plain edge.
     */
    @Value
    static class Run
    {
        boolean vertical;
        int line;
        int from;
        int to;
        Kind kind;
        int lockedSide;
    }

    private SceneEdges()
    {
    }

    /** The runs between these zones, indexed [zone x][zone y], joined where they carry on. */
    static List<Run> of(Zone[][] zones)
    {
        int width = zones.length;
        int height = width == 0 ? 0 : zones[0].length;
        List<Run> runs = new ArrayList<>();
        for (int zx = 1; zx < width; zx++)
        {
            line(runs, true, zx * ZONE, zones[zx - 1], zones[zx]);
        }
        for (int zy = 1; zy < height; zy++)
        {
            Zone[] below = new Zone[width];
            Zone[] above = new Zone[width];
            for (int zx = 0; zx < width; zx++)
            {
                below[zx] = zones[zx][zy - 1];
                above[zx] = zones[zx][zy];
            }
            line(runs, false, zy * ZONE, below, above);
        }
        return Collections.unmodifiableList(runs);
    }

    /** The runs along one line, between the zones before it and the zones after it. */
    private static void line(List<Run> runs, boolean vertical, int line, Zone[] before, Zone[] after)
    {
        Kind open = null;
        int openSide = 0;
        int start = 0;
        for (int i = 0; i <= before.length; i++)
        {
            Kind kind = i < before.length ? kind(before[i], after[i]) : null;
            int side = kind == Kind.LOCKED && after[i].getTint() == TintPolicy.Tint.LOCKED ? 1
                : kind == Kind.LOCKED ? -1 : 0;
            if (open != null && (kind != open || side != openSide))
            {
                runs.add(new Run(vertical, line, start, i * ZONE, open, openSide));
                open = null;
            }
            if (kind != null && open == null)
            {
                open = kind;
                openSide = side;
                start = i * ZONE;
            }
        }
    }

    /** The edge between two neighbouring zones, or null for none. */
    private static Kind kind(Zone a, Zone b)
    {
        if (a == null || b == null
            || a.getTint() == TintPolicy.Tint.UNKNOWN || b.getTint() == TintPolicy.Tint.UNKNOWN)
        {
            return null;
        }
        if (a.getTint() != b.getTint())
        {
            return Kind.LOCKED;
        }
        return Objects.equals(a.getRules(), b.getRules()) ? null : Kind.PLAIN;
    }
}
