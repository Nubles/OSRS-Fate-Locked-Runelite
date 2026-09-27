package com.fatelocked.sidebar;

import com.fatelocked.ui.Palette;
import lombok.Value;

/**
 * The card at the top of the sidebar: whether the rules are current and for this
 * character, in a title, a line of detail and at most two actions.
 */
@Value
public class StatusCardModel
{
    Palette.Tone tone;
    String title;
    /** Why, and what to do; null when the title says it all. */
    String detail;
    /** The one primary action, or null. */
    CardAction primary;
    /** A quieter second action, or null. */
    CardAction secondary;

    public static StatusCardModel of(Palette.Tone tone, String title, String detail)
    {
        return new StatusCardModel(tone, title, detail, null, null);
    }

    public StatusCardModel withActions(CardAction primary, CardAction secondary)
    {
        return new StatusCardModel(tone, title, detail, primary, secondary);
    }
}
