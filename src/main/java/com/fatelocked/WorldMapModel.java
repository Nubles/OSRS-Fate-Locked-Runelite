package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import lombok.Value;

/**
 * What the world map draws for a run (U18, decision 4), worked out once per decision service:
 * its locked land and frontier as runs along each chunk row, and the outline of its unlocked
 * land. Unlocked land is left clear, so the map reads as fog over what is still locked. The
 * outline carries "locked" without relying on colour (U15), and goes all the way round: it
 * also follows unlocked land where the tracker's map stops, such as at Tutorial Island, which
 * is free but left off the map because no one can go back.
 */
final class WorldMapModel
{
    /** Nothing to draw: no rules, or another character's. */
    static final WorldMapModel NONE = new WorldMapModel(Collections.emptyList(), Collections.emptyList());

    /** Chunks in one row with the same fill, from column cx0 to cx1, both included. */
    @Value
    static class Run
    {
        int cy;
        int cx0;
        int cx1;
        WorldMapChunks.Fill fill;
    }

    /**
     * A stretch of the unlocked land's outline along a chunk line, in chunks: a vertical one on
     * the west side of column {@code line}, from row {@code from} up to row {@code to}; a
     * horizontal one on the south side of row {@code line}, from column {@code from} to {@code to}.
     */
    @Value
    static class Edge
    {
        boolean vertical;
        int line;
        int from;
        int to;
    }

    private final List<Run> runs;
    private final List<Edge> outline;

    private WorldMapModel(List<Run> runs, List<Edge> outline)
    {
        this.runs = runs;
        this.outline = outline;
    }

    /** The locked land and the frontier, row by row from the south. */
    List<Run> runs()
    {
        return runs;
    }

    /** The outline of the unlocked land: every side where it meets land that isn't unlocked. */
    List<Edge> outline()
    {
        return outline;
    }

    static WorldMapModel of(DecisionService decisions)
    {
        Map<CanonicalChunk, WorldMapChunks.Fill> fills = new HashMap<>();
        for (CanonicalChunk chunk : decisions.mappedChunks())
        {
            WorldMapChunks.Fill fill = WorldMapChunks.fill(decisions, chunk);
            if (fill != null)
            {
                fills.put(chunk, fill);
            }
        }
        return new WorldMapModel(runs(fills), outline(fills));
    }

    private static List<Run> runs(Map<CanonicalChunk, WorldMapChunks.Fill> fills)
    {
        // Row by row, west to east.
        TreeMap<Integer, TreeMap<Integer, WorldMapChunks.Fill>> rows = new TreeMap<>();
        for (Map.Entry<CanonicalChunk, WorldMapChunks.Fill> chunk : fills.entrySet())
        {
            if (chunk.getValue() != WorldMapChunks.Fill.UNLOCKED)
            {
                rows.computeIfAbsent(chunk.getKey().getCy(), row -> new TreeMap<>())
                    .put(chunk.getKey().getCx(), chunk.getValue());
            }
        }
        List<Run> runs = new ArrayList<>();
        for (Map.Entry<Integer, TreeMap<Integer, WorldMapChunks.Fill>> row : rows.entrySet())
        {
            Run open = null;
            for (Map.Entry<Integer, WorldMapChunks.Fill> chunk : row.getValue().entrySet())
            {
                int cx = chunk.getKey();
                if (open != null && open.getCx1() == cx - 1 && open.getFill() == chunk.getValue())
                {
                    open = new Run(open.getCy(), open.getCx0(), cx, open.getFill());
                    continue;
                }
                if (open != null)
                {
                    runs.add(open);
                }
                open = new Run(row.getKey(), cx, cx, chunk.getValue());
            }
            runs.add(open);
        }
        return Collections.unmodifiableList(runs);
    }

    private static List<Edge> outline(Map<CanonicalChunk, WorldMapChunks.Fill> fills)
    {
        // Each unit of outline, keyed by its line: the rows (vertical) or columns (horizontal) it spans.
        TreeMap<Integer, TreeSet<Integer>> vertical = new TreeMap<>();
        TreeMap<Integer, TreeSet<Integer>> horizontal = new TreeMap<>();
        for (Map.Entry<CanonicalChunk, WorldMapChunks.Fill> chunk : fills.entrySet())
        {
            if (chunk.getValue() != WorldMapChunks.Fill.UNLOCKED)
            {
                continue;
            }
            int cx = chunk.getKey().getCx();
            int cy = chunk.getKey().getCy();
            if (!unlocked(fills, cx - 1, cy)) unit(vertical, cx, cy);
            if (!unlocked(fills, cx + 1, cy)) unit(vertical, cx + 1, cy);
            if (!unlocked(fills, cx, cy - 1)) unit(horizontal, cy, cx);
            if (!unlocked(fills, cx, cy + 1)) unit(horizontal, cy + 1, cx);
        }
        List<Edge> outline = new ArrayList<>();
        join(outline, true, vertical);
        join(outline, false, horizontal);
        return Collections.unmodifiableList(outline);
    }

    /** Unlocked land; the rest is locked, the frontier, or off the tracker's map. */
    private static boolean unlocked(Map<CanonicalChunk, WorldMapChunks.Fill> fills, int cx, int cy)
    {
        return fills.get(new CanonicalChunk(cx, cy)) == WorldMapChunks.Fill.UNLOCKED;
    }

    private static void unit(TreeMap<Integer, TreeSet<Integer>> lines, int line, int at)
    {
        lines.computeIfAbsent(line, l -> new TreeSet<>()).add(at);
    }

    /** Join each line's units into stretches. */
    private static void join(List<Edge> outline, boolean vertical, TreeMap<Integer, TreeSet<Integer>> lines)
    {
        for (Map.Entry<Integer, TreeSet<Integer>> line : lines.entrySet())
        {
            int from = Integer.MIN_VALUE;
            int to = Integer.MIN_VALUE;
            for (int at : line.getValue())
            {
                if (at != to)
                {
                    if (from != Integer.MIN_VALUE)
                    {
                        outline.add(new Edge(vertical, line.getKey(), from, to));
                    }
                    from = at;
                }
                to = at + 1;
            }
            outline.add(new Edge(vertical, line.getKey(), from, to));
        }
    }
}
