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
        /** Nothing: it sorts itself out, or already has. */
        NONE,
        /** Press Connect tracker. */
        CONNECT,
        /** Tick "Enable online sync"; the pairing is kept. */
        ENABLE_SYNC,
        /** Open the web tracker, which sends the rules again. */
        OPEN_TRACKER
    }

    final String status;
    final Tone tone;
    /** Why, and what to do; null when the status says it all. */
    final String detail;
    final Action action;
    /** When the relay last delivered or confirmed the rules, or a dash. */
    final String lastSync;

    private SyncView(String status, Tone tone, String detail, Action action, String lastSync)
    {
        this.status = status;
        this.tone = tone;
        this.detail = detail;
        this.action = action;
        this.lastSync = lastSync;
    }

    static SyncView of(TrackerConnectionSnapshot snapshot, Instant now, ZoneId zone)
    {
        String lastSync = snapshot.getLastSync() == null
            ? "\u2014" : LocalTimeText.of(snapshot.getLastSync(), now, zone);
        String nextCheck = snapshot.getNextCheck() == null
            ? "" : " Next check at " + LocalTimeText.of(snapshot.getNextCheck(), now, zone) + ".";
        Tone tone = tone(snapshot.getState());
        Action action = Action.NONE;
        String detail;
        switch (snapshot.getReason())
        {
            case NOT_PAIRED:
                detail = "Press Connect tracker to get your rules from the web tracker.";
                action = Action.CONNECT;
                break;
            case SYNC_OFF:
                detail = "Your pairing is kept. Tick Enable online sync, under Bundle,"
                    + " to pick it up again.";
                action = Action.ENABLE_SYNC;
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
            default:
                detail = snapshot.getState() == TrackerConnectionState.IMPORT_FAILED
                    ? "RuneLite couldn't use the tracker's rules." + nextCheck : null;
                break;
        }
        return new SyncView(snapshot.getMessage(), tone, detail, action, lastSync);
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
