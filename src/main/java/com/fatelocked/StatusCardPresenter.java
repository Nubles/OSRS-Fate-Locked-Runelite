package com.fatelocked;

import com.fatelocked.panel.LocalTimeText;
import com.fatelocked.rules.Trust;
import com.fatelocked.sidebar.CardAction;
import com.fatelocked.sidebar.StatusCardModel;
import com.fatelocked.ui.Copy;
import com.fatelocked.ui.Palette.Tone;
import com.fatelocked.ui.Terms;
import java.time.Duration;
import java.time.Instant;

/**
 * The status card: whether the rules are current and for this character, and the one
 * thing to do about it (U8, U21). Pure: made from {@link StatusFacts}.
 *
 * <p>The most important fact wins: another character, then a pairing waiting or
 * failing, then the tracker's copy, then the rules' own age.
 */
final class StatusCardPresenter
{
    static final String UP_TO_DATE = "Rules up to date";
    static final String OUT_OF_DATE = "Rules may be out of date";
    static final String STRICT_WAITS = "Strict Mode is inactive until the rules refresh.";

    private StatusCardPresenter()
    {
    }

    static StatusCardModel present(StatusFacts facts)
    {
        TrackerConnectionSnapshot connection = facts.getConnection();
        SyncReason reason = connection.getReason();

        if (facts.getTrust() == Trust.WRONG_CHARACTER)
        {
            return StatusCardModel.of(Tone.BAD, Terms.DIFFERENT_CHARACTER,
                "This run belongs to " + name(facts.getBoundAccount()) + ", and you're logged in as "
                    + name(facts.getLoggedInAs()) + ". Warnings and Strict Mode are off.");
        }

        switch (reason)
        {
            case CONFIRM_IN_BROWSER:
                return StatusCardModel.of(Tone.PENDING, "Waiting for confirmation",
                    "Confirm this profile in the browser tab RuneLite opened. RuneLite checks every few seconds.")
                    .withActions(CardAction.OPEN_PAGE_AGAIN, CardAction.CANCEL_PAIRING);
            case CONFIRM_REPAIR:
                return StatusCardModel.of(Tone.PENDING, "Waiting for confirmation",
                    "Confirm the new profile in the browser tab RuneLite opened. Your current pairing keeps"
                        + " working until it arrives.")
                    .withActions(CardAction.OPEN_PAGE_AGAIN, CardAction.CANCEL_REPAIR);
            case NO_PROFILE:
                return StatusCardModel.of(Tone.BAD, "No profile arrived",
                    "Nothing was confirmed in the browser within 10 minutes.")
                    .withActions(CardAction.CONNECT, null);
            case GONE:
                return StatusCardModel.of(Tone.NEUTRAL, "Disconnected in the tracker",
                    "The web tracker was told to stop sending rules to this pairing. " + keptRules(facts))
                    .withActions(CardAction.CONNECT, null);
            case FUTURE_FORMAT:
                return StatusCardModel.of(Tone.BAD, "Plugin update needed",
                    "Your tracker sent rules in a newer format than this Fate Locked reads. Restart RuneLite to"
                        + " update it from the Plugin Hub. " + keptRules(facts));
            case INVALID_RULES:
                return StatusCardModel.of(Tone.BAD, "Tracker rules not usable",
                    "The tracker sent rules RuneLite couldn't use. Open the web tracker to send them again. "
                        + keptRules(facts))
                    .withActions(CardAction.OPEN_TRACKER, null);
            case NO_RECENT_UPDATE:
                return StatusCardModel.of(Tone.PENDING, "Tracker copy expired",
                    "The tracker hasn't sent your rules in the last 24 hours. Open the web tracker to send them"
                        + " again. " + keptRules(facts))
                    .withActions(CardAction.OPEN_TRACKER, null);
            case OLDER_RULES:
                return StatusCardModel.of(Tone.PENDING, "Tracker has older rules",
                    "The relay sent older rules than yours, so yours stay in use. Open the web tracker to send"
                        + " yours again.")
                    .withActions(CardAction.OPEN_TRACKER, null);
            case REPAIR_ABANDONED:
                return StatusCardModel.of(Tone.NEUTRAL, "Kept your pairing",
                    "No new profile arrived within 10 minutes, so RuneLite kept your current pairing.");
            default:
                break;
        }
        if (connection.getState() == TrackerConnectionState.IMPORT_FAILED)
        {
            return StatusCardModel.of(Tone.BAD, "Couldn't apply the tracker's rules",
                "RuneLite will try again at the next check. " + keptRules(facts))
                .withActions(CardAction.CHECK_NOW, null);
        }

        switch (facts.getSource())
        {
            case FILE:
            case IMPORT:
                return backup(facts, reason);
            case RELAY:
                return tracker(facts, connection, reason);
            default:
                return nothingLoaded(facts, reason);
        }
    }

    /** Rules the relay sent, or saved by the last start from the relay. */
    private static StatusCardModel tracker(StatusFacts facts, TrackerConnectionSnapshot connection, SyncReason reason)
    {
        if (!facts.isPaired())
        {
            StatusCardModel card = StatusCardModel.of(Tone.NEUTRAL, "Using saved rules",
                "Rules from the tracker, exported " + time(facts.getExportedAt(), facts) + ". "
                    + (reason == SyncReason.SYNC_OFF ? "Online sync is off, so nothing checks them."
                        : "Nothing checks them until you connect the tracker."));
            return card.withActions(reason == SyncReason.SYNC_OFF ? CardAction.TURN_ON_SYNC : CardAction.CONNECT,
                null);
        }
        Instant lastSync = connection.getLastSync();
        boolean confirmedSinceStart = lastSync != null
            && (facts.getArrival() != RulesPrecedence.Arrival.SAVED || facts.getArrivedAt() == null
                || !lastSync.isBefore(facts.getArrivedAt()));
        if (!confirmedSinceStart)
        {
            return StatusCardModel.of(Tone.NEUTRAL, "Checking with the tracker",
                "Rules saved " + time(facts.getArrivedAt(), facts) + " are in use until the tracker answers.");
        }
        String failure = failure(connection, facts);
        if (facts.isFresh())
        {
            String detail = "Synced " + ago(lastSync, facts)
                + (facts.isBound() ? " for " + name(facts.getBoundAccount()) + "." : ". These rules name no"
                    + " character, so Strict Mode stays off.");
            return StatusCardModel.of(Tone.GOOD, UP_TO_DATE, failure == null ? detail : detail + " " + failure);
        }
        return StatusCardModel.of(Tone.PENDING, OUT_OF_DATE,
            "Last synced " + ago(lastSync, facts) + ". " + (failure == null ? "" : failure + " ") + STRICT_WAITS)
            .withActions(CardAction.CHECK_NOW, null);
    }

    /** Rules from the clipboard or a backup file. */
    private static StatusCardModel backup(StatusFacts facts, SyncReason reason)
    {
        String from = facts.getSource() == FateLockedPlugin.RulesSource.IMPORT ? "the clipboard" : "a backup file";
        StringBuilder detail = new StringBuilder("Rules from ").append(from);
        if (facts.getExportedAt() != null)
        {
            detail.append(", exported ").append(time(facts.getExportedAt(), facts));
        }
        detail.append(". ");
        if (facts.isLegacy())
        {
            detail.append("It's from an older tracker, so Strict Mode doesn't use it.");
        }
        else if (facts.isFresh())
        {
            detail.append("Strict Mode uses them for 15 minutes after the tracker exported them.");
        }
        else
        {
            detail.append("They're more than 15 minutes old, so Strict Mode doesn't use them.");
        }
        if (facts.isPaired())
        {
            detail.append(" The tracker's copy replaces them at its next check.");
            return StatusCardModel.of(Tone.NEUTRAL, "Using a backup", detail.toString());
        }
        return StatusCardModel.of(Tone.NEUTRAL, "Using a backup", detail.toString())
            .withActions(null, reason == SyncReason.SYNC_OFF ? CardAction.TURN_ON_SYNC : CardAction.CONNECT);
    }

    /** No rules at all yet. */
    private static StatusCardModel nothingLoaded(StatusFacts facts, SyncReason reason)
    {
        if (reason == SyncReason.SYNC_OFF)
        {
            return StatusCardModel.of(Tone.NEUTRAL, "Online sync is off",
                "Your pairing is kept. Turn online sync on to get your rules from the tracker.")
                .withActions(CardAction.TURN_ON_SYNC, CardAction.USE_BACKUP);
        }
        if (facts.isPaired())
        {
            return StatusCardModel.of(Tone.PENDING, "Checking with the tracker",
                "RuneLite is asking the tracker for your run's rules.");
        }
        return StatusCardModel.of(Tone.NEUTRAL, "Not connected", "Connect the tracker to load your run's rules.")
            .withActions(CardAction.CONNECT, CardAction.USE_BACKUP);
    }

    /** Why the last check failed and when the next one is, or null when it didn't fail. */
    private static String failure(TrackerConnectionSnapshot connection, StatusFacts facts)
    {
        String what;
        switch (connection.getReason())
        {
            case UNREACHABLE:
                what = "RuneLite couldn't reach the tracker";
                break;
            case UNREADABLE:
                what = "Something other than the tracker answered, such as a Wi-Fi sign-in page";
                break;
            case BUSY:
                what = "The tracker asked RuneLite to check less often";
                break;
            case UNAVAILABLE:
                what = "The tracker had a problem";
                break;
            default:
                return null;
        }
        Instant next = connection.getNextCheck();
        return next == null ? what + "." : what + "; next check " + time(next, facts) + ".";
    }

    private static String keptRules(StatusFacts facts)
    {
        return facts.getSource() == FateLockedPlugin.RulesSource.NONE
            ? "No rules are loaded." : "Your current rules stay in use.";
    }

    private static String ago(Instant at, StatusFacts facts)
    {
        return Copy.ago(Duration.between(at, facts.getNow()));
    }

    private static String time(Instant at, StatusFacts facts)
    {
        return at == null ? "at an unknown time" : "at " + LocalTimeText.of(at, facts.getNow(), facts.getZone());
    }

    private static String name(String account)
    {
        return account == null || account.isEmpty() ? "another character" : account;
    }
}
