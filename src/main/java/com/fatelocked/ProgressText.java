package com.fatelocked;

import com.fatelocked.rules.Progress;

/**
 * Unlock progress as the HUD and the infobox show it (B10). Null progress
 * (no rules, or another character's) shows nothing or a dash.
 */
final class ProgressText
{
    private ProgressText()
    {
    }

    /** The HUD's "Unlocked" line, e.g. "13/177 · 7%"; null when there is none. */
    static String hudLine(Progress progress)
    {
        if (progress == null || progress.getChunksTotal() <= 0) return null;
        return progress.getAreasUnlocked() + "/" + progress.getAreasTotal() + " · " + progress.percent() + "%";
    }

    static String infoBoxText(Progress progress)
    {
        return progress == null || progress.getChunksTotal() <= 0 ? "—" : progress.percent() + "%";
    }

    static String infoBoxTooltip(Progress progress)
    {
        if (progress == null) return "Unlock progress";
        return "Unlock progress: " + progress.getAreasUnlocked() + "/" + progress.getAreasTotal()
            + " areas · " + progress.getChunksUnlocked() + "/" + progress.getChunksTotal() + " chunks";
    }
}
