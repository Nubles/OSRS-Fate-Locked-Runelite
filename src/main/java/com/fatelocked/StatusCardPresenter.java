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
    /**
     * Tracker rules the relay still confirms. Never "up to date": an unchanged relay copy
     * doesn't mean the tracker managed to send its latest rules (a failed send, a second
     * device), so the card says when the tracker sent them instead.
     */
    static final String TRACKER_RULES = "Using the tracker's rules";
    static final String OUT_OF_DATE = "Rules may be out of date";
    /** Paired, no rules loaded, and every check since this start failed. */
    static final String NO_RULES_YET = "Couldn't get your rules";
    static final String STRICT_WAITS = "Strict Mode is inactive until the rules refresh.";
    /** What opening the tracker does about old rules: it sends them again, with a new time. */
    static final String REFRESH = "Open the tracker, or reload its tab, so it sends them again.";

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
                    "Confirm this profile in the browser tab RuneLite opened. "
                        + (failedCheck(connection, facts) == null ? "RuneLite checks every few seconds."
                            : failedCheck(connection, facts)))
                    .withActions(CardAction.OPEN_PAGE_AGAIN, CardAction.CANCEL_PAIRING);
            case CONFIRM_REPAIR:
                return StatusCardModel.of(Tone.PENDING, "Waiting for confirmation",
                    withFailedCheck("Confirm the new profile in the browser tab RuneLite opened. Your current"
                        + " pairing keeps working until it arrives.", connection, facts))
                    .withActions(CardAction.OPEN_PAGE_AGAIN, CardAction.CANCEL_REPAIR);
            case NO_PROFILE:
                return StatusCardModel.of(Tone.BAD, "No profile arrived",
                    withFailedCheck("Nothing was confirmed in the browser within 10 minutes.", connection, facts))
                    .withActions(CardAction.CONNECT, null);
            case NOT_CONFIRMED:
                // A first pairing from before this start or a reset: no rules ever came from it.
                return StatusCardModel.of(Tone.PENDING, "Pairing not confirmed",
                    withFailedCheck("This pairing hasn't sent any rules yet. Press Connect tracker to pair"
                        + " again.", connection, facts))
                    .withActions(CardAction.CONNECT, null);
            case GONE:
                return StatusCardModel.of(Tone.NEUTRAL, "Disconnected in the tracker",
                    withFailedCheck("The web tracker was told to stop sending rules to this pairing. "
                        + keptRules(facts), connection, facts))
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
                    withFailedCheck("The tracker hasn't sent your rules recently. Open the web tracker so it"
                        + " can send them again. " + keptRules(facts), connection, facts))
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
                return backup(facts, connection, reason);
            case RELAY:
                return tracker(facts, connection, reason);
            default:
                return nothingLoaded(facts, connection, reason);
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
        String failure = failure(connection, facts);
        if (!confirmedSinceStart)
        {
            // Rules the relay sent in this session (their sync time is forgotten when online sync
            // is turned off and on) are the tracker's; rules restored at start are saved ones.
            String saved = facts.getArrival() == RulesPrecedence.Arrival.RELAY
                ? (facts.getExportedAt() == null ? "Rules from the tracker"
                    : "Rules the tracker sent " + time(facts.getExportedAt(), facts))
                    + " are in use until it answers."
                : "Rules saved " + time(facts.getArrivedAt(), facts) + " are in use until the tracker answers.";
            if (failure == null)
            {
                return StatusCardModel.of(Tone.NEUTRAL, "Checking with the tracker", saved);
            }
            // Every check since this start failed: say why and when the next one is, as the
            // card does once a check has worked, and offer Check now.
            // Saved rules wait for a confirmation, so Strict Mode lets everything through meanwhile.
            return StatusCardModel.of(Tone.PENDING, OUT_OF_DATE, saved + " " + failure + " " + STRICT_WAITS)
                .withActions(CardAction.CHECK_NOW, null);
        }
        if (facts.isFresh())
        {
            String forWhom = facts.isBound() ? " for " + name(facts.getBoundAccount()) : "";
            // When the tracker sent them, from the rules themselves, and when RuneLite last
            // heard from the relay: the relay answering says nothing about the rules' age.
            String detail = facts.getExportedAt() == null
                ? "Synced " + ago(lastSync, facts) + forWhom + "."
                : "The tracker sent these rules" + forWhom + " " + time(facts.getExportedAt(), facts)
                    + ". RuneLite last checked " + ago(lastSync, facts) + ".";
            if (!facts.isBound())
            {
                detail += " These rules name no character, so Strict Mode stays off.";
            }
            return StatusCardModel.of(Tone.GOOD, TRACKER_RULES, failure == null ? detail : detail + " " + failure)
                .withActions(CardAction.CHECK_NOW, null);
        }
        FreshnessPolicy.Verdict freshness = facts.getFreshness();
        if (freshness != null && freshness.isTrackerRulesOld())
        {
            // The relay answers, but the rules are old (ECO-3): a 304 says only that nothing new
            // reached it, not that the tracker managed to send. Strict Mode lets everything through.
            String forWhom = facts.isBound() ? " for " + name(facts.getBoundAccount()) : "";
            String sent = facts.getExportedAt() == null || freshness.getAge() == null ? ""
                : "The tracker sent these rules" + forWhom + " " + time(facts.getExportedAt(), facts) + ". ";
            String detail = sent + oldRules(freshness, facts.isStrictMode()) + " " + REFRESH;
            return StatusCardModel.of(Tone.PENDING, OUT_OF_DATE, failure == null ? detail : detail + " " + failure)
                .withActions(CardAction.OPEN_TRACKER, CardAction.CHECK_NOW);
        }
        return StatusCardModel.of(Tone.PENDING, OUT_OF_DATE,
            "Last synced " + ago(lastSync, facts) + ". " + (failure == null ? "" : failure + " ") + STRICT_WAITS)
            .withActions(CardAction.CHECK_NOW, null);
    }

    /** Rules from the clipboard or a backup file. */
    private static StatusCardModel backup(StatusFacts facts, TrackerConnectionSnapshot connection,
        SyncReason reason)
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
            // As the tracker's card says: Strict Mode acts only on rules that name a character.
            detail.append(facts.isBound()
                ? "Strict Mode uses them for 15 minutes after the tracker exported them."
                : "These rules name no character, so Strict Mode stays off.");
        }
        else
        {
            detail.append("They're more than 15 minutes old, so Strict Mode doesn't use them.");
        }
        if (facts.isPaired())
        {
            detail.append(" The tracker's copy replaces them at its next check.");
            String failure = failure(connection, facts);
            if (failure == null)
            {
                return StatusCardModel.of(Tone.NEUTRAL, "Using a backup", detail.toString());
            }
            // The README's promise holds here too: a failed check says why, when the next is, and Check now.
            return StatusCardModel.of(Tone.NEUTRAL, "Using a backup", detail.append(' ').append(failure).toString())
                .withActions(CardAction.CHECK_NOW, null);
        }
        return StatusCardModel.of(Tone.NEUTRAL, "Using a backup", detail.toString())
            .withActions(null, reason == SyncReason.SYNC_OFF ? CardAction.TURN_ON_SYNC : CardAction.CONNECT);
    }

    /** No rules at all yet. */
    private static StatusCardModel nothingLoaded(StatusFacts facts, TrackerConnectionSnapshot connection,
        SyncReason reason)
    {
        if (reason == SyncReason.SYNC_OFF)
        {
            return StatusCardModel.of(Tone.NEUTRAL, "Online sync is off",
                "Your pairing is kept. Turn online sync on to get your rules from the tracker.")
                .withActions(CardAction.TURN_ON_SYNC, CardAction.USE_BACKUP);
        }
        if (facts.isPaired())
        {
            String failure = failure(connection, facts);
            if (failure != null)
            {
                return StatusCardModel.of(Tone.PENDING, NO_RULES_YET, "No rules are loaded yet. " + failure)
                    .withActions(CardAction.CHECK_NOW, CardAction.USE_BACKUP);
            }
            return StatusCardModel.of(Tone.PENDING, "Checking with the tracker",
                "RuneLite is asking the tracker for your run's rules.");
        }
        return StatusCardModel.of(Tone.NEUTRAL, "Not connected", "Connect the tracker to load your run's rules.")
            .withActions(CardAction.CONNECT, CardAction.USE_BACKUP);
    }

    /**
     * The tracker's rules are too old, or of unknown age (ECO-3). With Strict Mode on: "Strict
     * Mode paused: the rules are 14 h old." The status card and the one chat line say the same.
     */
    static String oldRules(FreshnessPolicy.Verdict freshness, boolean strictMode)
    {
        String why = freshness.getAge() == null
            ? "the rules don't say when the tracker sent them"
            : "the rules are " + Copy.age(freshness.getAge()) + " old";
        return strictMode
            ? "Strict Mode paused: " + why + "."
            : Character.toUpperCase(why.charAt(0)) + why.substring(1) + ", so they may not match your run.";
    }

    /** Why the last check failed and when the next one is, or null when it didn't fail. */
    private static String failure(TrackerConnectionSnapshot connection, StatusFacts facts)
    {
        return failure(connection.getReason(), connection.getNextCheck(), facts);
    }

    private static String failure(SyncReason why, Instant next, StatusFacts facts)
    {
        String what;
        switch (why)
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
        return next == null ? what + "." : what + "; next check " + time(next, facts) + ".";
    }

    /**
     * For a pairing that waits, why the last check failed and when the next one is, as a
     * failed check says it ("RuneLite couldn't reach the tracker; next check at 12:00."),
     * or null when the last check didn't fail.
     */
    private static String failedCheck(TrackerConnectionSnapshot connection, StatusFacts facts)
    {
        Instant next = connection.getNextCheck();
        String why = connection.getFailure() == null ? null : failure(connection.getFailure(), next, facts);
        if (why != null)
        {
            return why;
        }
        // A waiting state carries a next check time only after a failed check.
        return next == null ? null : "The last check failed; next check " + time(next, facts) + ".";
    }

    private static String withFailedCheck(String detail, TrackerConnectionSnapshot connection, StatusFacts facts)
    {
        String failed = failedCheck(connection, facts);
        return failed == null ? detail : detail + " " + failed;
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
