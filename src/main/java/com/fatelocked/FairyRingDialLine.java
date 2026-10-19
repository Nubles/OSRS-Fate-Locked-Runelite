package com.fatelocked;

import com.fatelocked.guardian.travel.IntentClassifier;
import com.fatelocked.guardian.travel.TravelMatch;
import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.Trust;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Terms;
import lombok.Value;

/**
 * The fairy ring dial status: one line naming the code the dials show and the tracker's decision
 * for it, from the travel table's codes. "CKS: Unlocked", "CKS: Locked: Karamja" with the
 * tracker's reason, "BLQ: not in the rules". Advice only, with Strict Mode on or off: it never
 * stops anything. Another character, or nobody with rules, sees no line.
 */
final class FairyRingDialLine
{
    /** For a code the tracker's table doesn't list, or rules with no travel table. */
    static final String NOT_IN_THE_RULES = "not in the rules";
    /** The tracker's reason is cut to this, so the line stays short. */
    static final int MAX_REASON = 40;

    private static final IntentClassifier CLASSIFIER = new IntentClassifier();

    /** The line's words and its tone in the palette. */
    @Value
    static class Line
    {
        String text;
        Palette.Tone tone;
    }

    private FairyRingDialLine()
    {
    }

    /** The line for a dialled code; null for no code, or rules that don't apply to this character. */
    static Line present(String code, DecisionService rules)
    {
        if (code == null || rules == null || rules.trust() != Trust.TRUSTED) return null;
        TravelMatch match = CLASSIFIER.dialled(code, rules.travelTable());
        if (match == null) return new Line(code + ": " + NOT_IN_THE_RULES, Palette.Tone.NEUTRAL);
        Decision decision = rules.travel(match.getMethod(), match.getOption());
        switch (decision.getStatus())
        {
            case ALLOWED:
                return new Line(code + ": " + Terms.UNLOCKED, Palette.Tone.GOOD);
            case LOCKED:
                return new Line(code + ": " + Terms.LOCKED + why(decision), Palette.Tone.BAD);
            case NOT_READY:
                return new Line(code + ": " + Terms.NOT_READY + why(decision), Palette.Tone.PENDING);
            default:
                return new Line(code + ": " + Terms.NEEDS_CHECKING, Palette.Tone.NEUTRAL);
        }
    }

    /** ": " and the tracker's reason, cut short; nothing when it gives none. */
    private static String why(Decision decision)
    {
        String reason = decision.getReason();
        if (reason == null || reason.trim().isEmpty()) return "";
        return ": " + HudPresenter.shorten(reason.trim(), MAX_REASON);
    }
}
