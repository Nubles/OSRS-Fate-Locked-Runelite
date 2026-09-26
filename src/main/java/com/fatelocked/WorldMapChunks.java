package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;

import java.util.List;

/**
 * What the world map draws for a chunk (B8), from the decision service: the
 * tracker's land in its colours, the frontier of a Chunked run, and a
 * tooltip. The sea the rules don't cover and another character's rules
 * draw nothing, as the web map shows land only.
 */
final class WorldMapChunks
{
    enum Fill { UNLOCKED, FRONTIER, LOCKED }

    private WorldMapChunks()
    {
    }

    /** The chunk's fill, or null when the map draws nothing there. NOT_READY is owned. */
    static Fill fill(DecisionService decisions, CanonicalChunk chunk)
    {
        switch (decisions.chunk(chunk).getStatus())
        {
            case ALLOWED:
            case NOT_READY:
                return Fill.UNLOCKED;
            case LOCKED:
                return decisions.isFrontier(chunk) ? Fill.FRONTIER : Fill.LOCKED;
            default:
                return null;
        }
    }

    /**
     * The hover tooltip: the area, the status and what's there, or null
     * where the map draws nothing. A chunk only the tracker names shows its
     * coordinates.
     */
    static String tooltip(DecisionService decisions, CanonicalChunk chunk, List<String> content)
    {
        Fill fill = fill(decisions, chunk);
        if (fill == null) return null;
        String label = decisions.areaName(chunk);
        StringBuilder tip = new StringBuilder(label != null ? label
            : "Chunk (" + chunk.getCx() + ", " + chunk.getCy() + ")").append("</br>");
        if (fill == Fill.FRONTIER) tip.append("<col=f59e0b>Locked — rollable next</col>");
        else if (fill == Fill.LOCKED) tip.append("<col=ef4444>Locked</col>");
        else if (decisions.chunk(chunk).getStatus() == PermissionStatus.NOT_READY)
        {
            tip.append("<col=f59e0b>Not ready</col>");
        }
        else tip.append("<col=2ee59d>Unlocked</col>");
        for (String line : content)
        {
            tip.append("</br><col=a8a8a8>").append(line).append("</col>");
        }
        return tip.toString();
    }
}
