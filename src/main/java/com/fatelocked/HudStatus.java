package com.fatelocked;

import com.fatelocked.rules.Decision;
import lombok.Value;

import java.awt.Color;

/**
 * The HUD's "Status" line for the chunk the player is in (B5): the decision
 * service's answer, so it says what the sidebar and the tags say. Not ready
 * and another character now show as themselves instead of "Unlocked" and a
 * lock the character doesn't have. A locked or not-ready chunk says why, in
 * the tracker's words (E8).
 */
@Value
class HudStatus
{
    static final Color GREEN = new Color(52, 211, 153);
    static final Color RED = new Color(248, 113, 113);
    static final Color AMBER = new Color(245, 158, 11);
    static final Color GRAY = new Color(156, 163, 175);

    String text;
    Color color;
    /** Why the chunk is locked or not ready ("Unlock Falador"); null when the rules don't say. */
    String why;

    static HudStatus of(Decision decision)
    {
        switch (decision.getStatus())
        {
            case ALLOWED:
                return new HudStatus("Unlocked", GREEN, null);
            case LOCKED:
                return new HudStatus("LOCKED", RED, decision.getReason());
            case NOT_READY:
                return new HudStatus("Not ready", AMBER, decision.getReason());
            default:
                // The rules don't apply here (another character); else they say nothing.
                return decision.getSource() == Decision.Source.TRUST && decision.getReason() != null
                    ? new HudStatus(decision.getReason(), AMBER, null)
                    : new HudStatus("Unknown", GRAY, null);
        }
    }
}
