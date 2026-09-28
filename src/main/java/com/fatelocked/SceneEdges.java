package com.fatelocked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Value;

/**
 * The loaded scene as the rules draw it (U3): where its chunk lines run, which of them are
 * locked, and where its locked land lies. It is worked out once per scene from the scene's
 * 8-tile zones, so an instance, which is copied zone by zone from anywhere, breaks where its
 * zones do. On the surface the zone lines that matter are the chunk lines.
 *
 * <p>A line between a locked place and an unlocked one is a locked edge. A line between two
 * different chunks that are alike is a plain edge. A place the rules don't decide draws no
 * edge, and neither does the scene's own border.
 */
final class SceneEdges
{
    /** Instances are copied in 8-tile zones. */
    static final int ZONE = 8;
    /** A scene with nothing to draw. */
    static final SceneEdges NONE = new SceneEdges(Collections.emptyList(), Collections.emptyList());

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

    /** Locked land in one row of zones, in scene tiles: from x0, y0 up to x1, y1. */
    @Value
    static class Block
    {
        int x0;
        int y0;
        int x1;
        int y1;
    }

    private final List<Run> edges;
    private final List<Block> locked;

    private SceneEdges(List<Run> edges, List<Block> locked)
    {
        this.edges = edges;
        this.locked = locked;
    }

    /** The scene's edges, joined where they carry on. */
    List<Run> edges()
    {
        return edges;
    }

    /** The scene's locked land, one block per run of locked zones in a row. */
    List<Block> locked()
    {
        return locked;
    }

    /** The scene these zones make, indexed [zone x][zone y]. */
    static SceneEdges of(Zone[][] zones)
    {
        int width = zones.length;
        int height = width == 0 ? 0 : zones[0].length;
        List<Run> runs = new ArrayList<>();
        for (int zx = 1; zx < width; zx++)
        {
            line(runs, true, zx * ZONE, zones[zx - 1], zones[zx]);
        }
        List<Block> locked = new ArrayList<>();
        Zone[] below = null;
        for (int zy = 0; zy < height; zy++)
        {
            Zone[] row = new Zone[width];
            for (int zx = 0; zx < width; zx++)
            {
                row[zx] = zones[zx][zy];
            }
            if (below != null)
            {
                line(runs, false, zy * ZONE, below, row);
            }
            lockedRow(locked, zy, row);
            below = row;
        }
        return new SceneEdges(Collections.unmodifiableList(runs), Collections.unmodifiableList(locked));
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

    /** The locked zones of one row, joined where they touch. */
    private static void lockedRow(List<Block> locked, int zy, Zone[] row)
    {
        int start = -1;
        for (int zx = 0; zx <= row.length; zx++)
        {
            boolean isLocked = zx < row.length && row[zx] != null && row[zx].getTint() == TintPolicy.Tint.LOCKED;
            if (isLocked && start < 0)
            {
                start = zx;
            }
            else if (!isLocked && start >= 0)
            {
                locked.add(new Block(start * ZONE, zy * ZONE, zx * ZONE, (zy + 1) * ZONE));
                start = -1;
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
