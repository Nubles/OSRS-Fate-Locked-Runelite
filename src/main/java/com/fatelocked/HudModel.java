package com.fatelocked;

import com.fatelocked.ui.Palette;
import java.util.Collections;
import java.util.List;
import lombok.Value;

/**
 * What the HUD shows (E6): worked out on the client thread when anything changes, and drawn
 * as it is, so nothing is rebuilt each frame. No lines means no HUD.
 */
@Value
class HudModel
{
    static final HudModel NONE = new HudModel(Collections.emptyList(), false);

    List<Line> lines;
    /** Detailed lists what the place holds, so it is drawn wider. */
    boolean detailed;

    /**
     * One line. With a value, the label is plain and the value takes the tone (none: plain).
     * Without one it stands alone: a heading when it has no tone, else text in its tone.
     */
    @Value
    static class Line
    {
        String label;
        String value;
        Palette.Tone tone;

        static Line heading(String text)
        {
            return new Line(text, null, null);
        }
    }
}
