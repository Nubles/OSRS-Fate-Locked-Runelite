package com.fatelocked.sidebar;

import com.fatelocked.guardian.StrictModeStatusView;
import com.fatelocked.ui.Palette.Tone;
import java.util.Collections;
import java.util.List;

/**
 * The Strict Mode section from the status the HUD also shows (B16), so the two never
 * disagree. Pause is offered only while Strict Mode can act; "Recently stopped" shows
 * only while it is on and has something in it.
 */
public final class StrictModeSectionPresenter
{
    public static final String WHAT_IT_DOES = "Stops a teleport to a place your rules lock, or a teleport of a kind"
        + " you haven't unlocked, such as Teleport Tablets.";

    private StrictModeSectionPresenter()
    {
    }

    /** @param recent what Strict Mode stopped lately, newest first, or null */
    public static StrictModeModel present(StrictModeStatusView status, List<String> recent)
    {
        List<String> stopped = recent == null ? Collections.emptyList() : recent;
        switch (status.getTone())
        {
            case ACTIVE:
                return new StrictModeModel(true, status.getText(), Tone.GOOD, WHAT_IT_DOES,
                    CardAction.PAUSE_STRICT_MODE, stopped);
            case PAUSED:
                return new StrictModeModel(true, status.getText(), Tone.PENDING, null,
                    CardAction.RESUME_STRICT_MODE, stopped);
            case INACTIVE:
                return new StrictModeModel(true, status.getText(), Tone.PENDING, status.getExplanation(), null,
                    stopped);
            default:
                return StrictModeModel.off();
        }
    }
}
