package com.fatelocked.guardian.travel;

/**
 * Words for a blocked trip (B15, G10). Pure: the coordinator stages its
 * notice before the click is consumed, so if this fails nothing is.
 */
public class EnforcementPresenter
{
    static final String PAUSE_HINT =
        "To go anyway, pause Strict Mode for 60 seconds from the banner or the sidebar.";

    public BlockNotice present(TravelAction action, TravelDecision decision, TravelAlternative alternative)
    {
        String label = decision.getLabel();
        String reason = decision.getReason() == null || decision.getReason().trim().isEmpty()
            ? "Travel is locked" : decision.getReason().trim();
        String instead = alternative == null || alternative.getLabel() == null
            || alternative.getLabel().trim().isEmpty() ? null : alternative.getLabel().trim();
        String headline = "Strict Mode blocked " + label;
        StringBuilder chat = new StringBuilder(headline).append(": ").append(reason).append('.');
        if (instead != null) chat.append(" Try ").append(instead).append(" instead.");
        chat.append(' ').append(PAUSE_HINT);
        return new BlockNotice(headline, reason, instead, chat.toString());
    }
}
