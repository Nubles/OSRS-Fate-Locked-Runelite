package com.fatelocked.sidebar;

import com.fatelocked.ui.Palette;
import java.util.Collections;
import java.util.List;
import lombok.Value;

/** The Strict Mode section: the switch, its state in a word, why, and what it stopped lately. */
@Value
public class StrictModeModel
{
    boolean on;
    /** "Active", "Paused · 42 s", "Inactive" or "Off". */
    String word;
    Palette.Tone tone;
    /** Why it is inactive, or what it does; null for neither. */
    String detail;
    /** Pause or resume, or null. */
    CardAction action;
    /** What it stopped lately, newest first; hidden while empty. */
    List<String> recent;

    public static StrictModeModel off()
    {
        return new StrictModeModel(false, "Off", Palette.Tone.NEUTRAL,
            StrictModeSectionPresenter.WHAT_IT_DOES, null, Collections.emptyList());
    }
}
