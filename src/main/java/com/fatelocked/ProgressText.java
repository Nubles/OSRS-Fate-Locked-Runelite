package com.fatelocked;

import com.fatelocked.rules.Progress;
import com.fatelocked.ui.Copy;

/**
 * Unlock progress as the HUD and the infobox show it (B10), in the sidebar's words. Null
 * progress (no rules, or another character's) shows nothing.
 */
final class ProgressText
{
    private ProgressText()
    {
    }

    /** The HUD's "Unlocked" line, e.g. "15/187 · 8%"; null when there is none. */
    static String hudLine(Progress progress)
    {
        if (progress == null || progress.getTotal() <= 0) return null;
        return progress.getUnlocked() + "/" + progress.getTotal() + " · " + progress.percent() + "%";
    }

    /** The infobox's count, e.g. "8%"; null when there is none, and the box isn't drawn. */
    static String infoBoxText(Progress progress)
    {
        return progress == null || progress.getTotal() <= 0 ? null : progress.percent() + "%";
    }

    /** "15 of 187 areas unlocked", then the chunks for a run that counts areas; null without progress. */
    static String infoBoxTooltip(Progress progress)
    {
        if (progress == null) return null;
        String unlocked = Copy.unlocked(progress.getUnlocked(), progress.getTotal(), progress.getUnit());
        if (Progress.CHUNKS.equals(progress.getUnit())) return unlocked;
        return unlocked + "</br>" + progress.getChunksUnlocked() + " of " + progress.getChunksTotal() + " chunks";
    }
}
