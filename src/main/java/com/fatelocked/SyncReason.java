package com.fatelocked;

/**
 * Why the tracker connection is in its state. SyncMachine picks one with
 * each state; SyncView turns it into the sidebar's words and one action.
 */
enum SyncReason
{
    /** Nothing to add to the state itself. */
    NONE(null),
    /** There is no pairing, so nothing to check. */
    NOT_PAIRED("Not connected"),
    /** Online sync is off; a pairing, if any, is kept. */
    SYNC_OFF("Online sync is off"),
    /** Paired with online sync on, and the tracker has not answered yet. */
    CHECKING("Checking the tracker"),
    /** A pairing started here waits for the player to confirm it in the browser. */
    CONFIRM_IN_BROWSER("Confirm in browser"),
    /** A pairing started here got no profile within 10 minutes. */
    NO_PROFILE("No profile received"),
    /** The relay's copy lapsed: it keeps a profile 24 hours after the last publish. */
    NO_RECENT_UPDATE("No recent update"),
    /** No reply at all: the network, or the relay's host, failed. */
    UNREACHABLE("Could not reach tracker"),
    /** A reply the plugin cannot use, such as a captive portal's page. */
    UNREADABLE("Tracker sent an unreadable reply"),
    /** The relay asked the plugin to slow down. */
    BUSY("Tracker relay is busy; retrying later"),
    /** The relay, or something in front of it, failed. */
    UNAVAILABLE("Tracker is unavailable");

    /** The short status the sidebar's Connection row shows, or null for the state's own. */
    final String status;

    SyncReason(String status)
    {
        this.status = status;
    }
}
