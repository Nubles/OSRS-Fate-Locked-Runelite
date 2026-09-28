package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Palette.Tone;
import com.fatelocked.ui.Terms;

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

    /**
     * The chunk's fill, or null when the map draws nothing there: off the
     * tracker's land, such as the sea, whose entries the HUD and tints use.
     * NOT_READY is owned.
     */
    static Fill fill(DecisionService decisions, CanonicalChunk chunk)
    {
        if (!decisions.mappedChunks().contains(chunk)) return null;
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
     * The hover tooltip: the area, the status in the palette's colour and what's there, or
     * null where the map draws nothing. A chunk only the tracker names shows its coordinates.
     */
    static String tooltip(DecisionService decisions, CanonicalChunk chunk, List<String> content, Palette palette)
    {
        Fill fill = fill(decisions, chunk);
        if (fill == null) return null;
        String label = decisions.areaName(chunk);
        StringBuilder tip = new StringBuilder(label != null ? label
            : "Chunk (" + chunk.getCx() + ", " + chunk.getCy() + ")").append("</br>");
        if (fill == Fill.FRONTIER) coloured(tip, palette, Tone.FRONTIER, Terms.LOCKED + " — rollable next");
        else if (fill == Fill.LOCKED) coloured(tip, palette, Tone.BAD, Terms.LOCKED);
        else if (decisions.chunk(chunk).getStatus() == PermissionStatus.NOT_READY)
        {
            coloured(tip, palette, Tone.PENDING, Terms.NOT_READY);
        }
        else coloured(tip, palette, Tone.GOOD, Terms.UNLOCKED);
        for (String line : content)
        {
            coloured(tip.append("</br>"), palette, Tone.NEUTRAL, line);
        }
        return tip.toString();
    }

    private static StringBuilder coloured(StringBuilder tip, Palette palette, Tone tone, String text)
    {
        return tip.append("<col=").append(palette.hex(tone)).append('>').append(text).append("</col>");
    }
}
