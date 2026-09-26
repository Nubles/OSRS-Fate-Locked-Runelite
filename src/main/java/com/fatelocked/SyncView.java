package com.fatelocked;

import com.fatelocked.panel.LocalTimeText;

import java.time.Instant;
import java.time.ZoneId;

/**
 * What the sidebar shows for the tracker connection: a short status in a
 * tone, one line saying why and what to do about it, and times on the
 * player's own clock. Pure: made from a snapshot, the time now and the
 * player's time zone.
 */
final class SyncView
{
    enum Tone
    {
        GREEN,
        AMBER,
        RED,
        GRAY
    }

    /** The one thing the player can do about the state. */
    enum Action
    {
        /** Nothing: it sorts itself out. */
        NONE,
        /** Press Check now. */
        CHECK_NOW,
        /** Press Connect tracker. */
        CONNECT,
        /** Tick "Enable online sync"; the pairing is kept. */
        ENABLE_SYNC,
        /** Open the web tracker, which sends the rules again. */
        OPEN_TRACKER,
        /** Update Fate Locked in the Plugin Hub. */
        UPDATE_PLUGIN
    }

    final String status;
    final Tone tone;
    /** Why, and what to do; null when the status says it all. */
    final String detail;
    final Action action;
    /** When the relay last delivered or confirmed the rules, or a dash. */
    final String lastSync;
    /** Whether Check now has anything to check: a pairing, with online sync on. */
    final boolean canCheckNow;
    /** What the connect button says and does. */
    final Connect connect;
    /** The code in use, as its last four characters, or a dash. */
    final String pairing;

    /** The connect button's job in each state. */
    enum Connect
    {
        /** No working pairing: pair, asking for consent first if need be. */
        CONNECT("Connect tracker"),
        /** Paired with online sync off: turn it on and use that pairing. */
        TURN_ON_SYNC("Turn on online sync"),
        /** A working pairing: ask, then pair again, keeping it until the new one delivers. */
        REPAIR("Re-pair tracker\u2026"),
        /** A re-pairing is waiting: go back to the working pairing. */
        CANCEL_REPAIR("Cancel re-pairing");

        final String label;

        Connect(String label)
        {
            this.label = label;
        }
    }

    private SyncView(String status, Tone tone, String detail, Action action,
        String lastSync, boolean canCheckNow, Connect connect, String pairing)
    {
        this.pairing = pairing;
        this.status = status;
        this.tone = tone;
        this.detail = detail;
        this.action = action;
        this.lastSync = lastSync;
        this.canCheckNow = canCheckNow;
        this.connect = connect;
    }

    static SyncView of(TrackerConnectionSnapshot snapshot, Instant now, ZoneId zone)
    {
        String lastSync = snapshot.getLastSync() == null
            ? "\u2014" : LocalTimeText.of(snapshot.getLastSync(), now, zone);
        String nextCheck = snapshot.getNextCheck() == null
            ? "" : " Next check at " + LocalTimeText.of(snapshot.getNextCheck(), now, zone) + ".";
        Tone tone = tone(snapshot.getState());
        TrackerConnectionState state = snapshot.getState();
        Action action = state == TrackerConnectionState.CONNECTED
            || state == TrackerConnectionState.OFFLINE
            ? Action.CHECK_NOW : Action.NONE;
        String detail;
        switch (snapshot.getReason())
        {
            case NOT_PAIRED:
                detail = "Press Connect tracker to get your rules from the web tracker.";
                action = Action.CONNECT;
                break;
            case SYNC_OFF:
                detail = "Your pairing is kept. Press Turn on online sync to pick it"
                    + " up again.";
                action = Action.ENABLE_SYNC;
                break;
            case CONFIRM_REPAIR:
                detail = "Confirm the profile in the browser tab RuneLite opened."
                    + " Until it arrives, RuneLite keeps your current pairing.";
                break;
            case GONE:
                detail = "The tracker's owner pressed Disconnect in the web tracker,"
                    + " so it sends no more rules to this pairing. Press Connect"
                    + " tracker to pair again.";
                action = Action.CONNECT;
                break;
            case REPAIR_ABANDONED:
                detail = "No new profile arrived within 10 minutes, so RuneLite kept"
                    + " your current pairing.";
                break;
            case CONFIRM_IN_BROWSER:
                detail = "Confirm the profile in the browser tab RuneLite opened."
                    + " RuneLite checks every few seconds.";
                break;
            case NO_PROFILE:
                detail = "No profile arrived within 10 minutes."
                    + " Press Connect tracker to try again.";
                action = Action.CONNECT;
                break;
            case NO_RECENT_UPDATE:
                detail = "The tracker hasn't sent your rules in the last 24 hours."
                    + " Open the web tracker to send them again.";
                action = Action.OPEN_TRACKER;
                break;
            case OLDER_RULES:
                detail = "The relay sent older rules than yours, so yours stay in use."
                    + " Open the web tracker to send yours again." + nextCheck;
                action = Action.OPEN_TRACKER;
                break;
            case UNREACHABLE:
                detail = "RuneLite couldn't reach the tracker." + nextCheck;
                break;
            case UNREADABLE:
                detail = "Something other than the tracker answered, such as a Wi-Fi"
                    + " sign-in page." + nextCheck;
                break;
            case BUSY:
                detail = "The tracker asked RuneLite to check less often." + nextCheck;
                break;
            case UNAVAILABLE:
                detail = "The tracker had a problem." + nextCheck;
                break;
            case FUTURE_FORMAT:
                detail = "Your tracker sent rules in a newer format than this version"
                    + " of Fate Locked reads. Update it in the Plugin Hub.";
                action = Action.UPDATE_PLUGIN;
                break;
            case INVALID_RULES:
                detail = "The tracker sent rules RuneLite couldn't use."
                    + " Open the web tracker to send them again.";
                action = Action.OPEN_TRACKER;
                break;
            default:
                if (state == TrackerConnectionState.IMPORT_FAILED)
                {
                    detail = "RuneLite couldn't apply the tracker's rules." + nextCheck;
                    action = Action.CHECK_NOW;
                }
                else
                {
                    detail = null;
                }
                break;
        }
        boolean canCheckNow = snapshot.getReason() != SyncReason.NOT_PAIRED
            && snapshot.getReason() != SyncReason.SYNC_OFF
            && snapshot.getReason() != SyncReason.GONE;
        String pairing = snapshot.getPairingEnding() == null
            ? "\u2014" : "\u2026" + snapshot.getPairingEnding();
        return new SyncView(snapshot.getMessage(), tone, detail, action, lastSync, canCheckNow,
            connect(snapshot.getReason()), pairing);
    }

    private static Connect connect(SyncReason reason)
    {
        switch (reason)
        {
            case NOT_PAIRED:
            case CONFIRM_IN_BROWSER:
            case NO_PROFILE:
            case GONE:
                // Nothing that works to keep: pair from scratch.
                return Connect.CONNECT;
            case SYNC_OFF:
                return Connect.TURN_ON_SYNC;
            case CONFIRM_REPAIR:
                return Connect.CANCEL_REPAIR;
            default:
                return Connect.REPAIR;
        }
    }

    private static Tone tone(TrackerConnectionState state)
    {
        switch (state)
        {
            case CONNECTED:
                return Tone.GREEN;
            case WAITING:
            case IMPORTING:
                return Tone.AMBER;
            case EXPIRED:
            case IMPORT_FAILED:
                return Tone.RED;
            default:
                return Tone.GRAY;
        }
    }
}
