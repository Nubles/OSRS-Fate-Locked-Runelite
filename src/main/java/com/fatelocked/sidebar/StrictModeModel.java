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
    /** "Networks and boats: blocked" or "… tagged only", while it can stop something; null otherwise. */
    String scope;

    public StrictModeModel(boolean on, String word, Palette.Tone tone, String detail, CardAction action,
        List<String> recent)
    {
        this(on, word, tone, detail, action, recent, null);
    }

    public StrictModeModel(boolean on, String word, Palette.Tone tone, String detail, CardAction action,
        List<String> recent, String scope)
    {
        this.on = on;
        this.word = word;
        this.tone = tone;
        this.detail = detail;
        this.action = action;
        this.recent = recent;
        this.scope = scope;
    }

    public static StrictModeModel off()
    {
        return new StrictModeModel(false, "Off", Palette.Tone.NEUTRAL,
            StrictModeSectionPresenter.WHAT_IT_DOES, null, Collections.emptyList());
    }
}
