package com.fatelocked;

import java.time.Duration;
import java.time.Instant;

/**
 * The tracker connection's state and timing: which relay version the plugin
 * holds, when the relay last confirmed it, when the next automatic check is
 * due, and what the sidebar says. Every transition takes the time it
 * happened and returns the snapshot to show, or changes only the timing.
 * No network, no clock, no threads: TrackerConnectionController owns those
 * and calls this under its lock.
 */
final class SyncMachine
{
    static final long CONNECTED_POLL_SECONDS = 60;
    /** How often a healthy connection is checked while the player is logged out. */
    static final long LOGGED_OUT_POLL_SECONDS = 5 * 60;
    static final long WAITING_POLL_SECONDS = 5;
    static final long FAILURE_BACKOFF_SECONDS = 30;
    /**
     * The longest a failure waits: well inside Strict Mode's 15-minute
     * freshness window, so a relay that recovers is noticed before the
     * rules count as old.
     */
    static final long MAX_FAILURE_BACKOFF_SECONDS = 5 * 60;
    /** The longest wait a relay's Retry-After is honoured for. */
    static final long MAX_RETRY_AFTER_SECONDS = 60 * 60;
    /** Check now works at most this often. */
    static final long CHECK_NOW_SECONDS = 10;
    /** How long a pairing started here waits for the browser to publish. */
    static final long PAIRING_CONFIRM_SECONDS = 10 * 60;
    /** Check every WAITING_POLL_SECONDS this long, then every PAIRING_SLOW_POLL_SECONDS. */
    static final long PAIRING_FAST_POLL_WINDOW_SECONDS = 2 * 60;
    static final long PAIRING_SLOW_POLL_SECONDS = 15;

    private String acceptedVersion;
    /** The relay version the plugin could not import, until something newer arrives. */
    private String rejectedVersion;
    /** Why it could not: a newer format or broken rules. */
    private SyncReason rejectedReason;
    private Instant lastSync;
    private Instant nextCheck = Instant.EPOCH;
    private int consecutiveFailures;
    /** When this session began the current pairing; null once it imports. */
    private Instant pairingStartedAt;
    /** Whether that pairing is a re-pairing, with another still in use until it delivers. */
    private boolean repairing;
    /** When the player last pressed Check now and it counted. */
    private Instant lastCheckNow;
    /** Whether the player is in game; taken as so until the plugin says otherwise. */
    private boolean loggedIn = true;
    /** Until when the relay asked the plugin to wait, or null. */
    private Instant retryAfterUntil;

    /** The relay version of the rules the plugin holds, or null. */
    String acceptedVersion()
    {
        return acceptedVersion;
    }

    /** The relay version the plugin could not import, or null. */
    String rejectedVersion()
    {
        return rejectedVersion;
    }

    /**
     * What the next check sends as If-None-Match: the rejected version while
     * there is one, so the relay answers 304 instead of sending it again,
     * else the version the plugin holds.
     */
    String validator()
    {
        return rejectedVersion != null ? rejectedVersion : acceptedVersion;
    }

    /** When the relay last delivered or confirmed the rules, or null. */
    Instant lastSync()
    {
        return lastSync;
    }

    /**
     * Whether an automatic check is due. If it is, the next tick is reserved
     * while the check is in flight; its reply sets the real cadence.
     */
    boolean checkDue(Instant now)
    {
        if (now.isBefore(nextCheck))
        {
            return false;
        }
        nextCheck = now.plusSeconds(WAITING_POLL_SECONDS);
        return true;
    }

    /**
     * The player logged in or out. Logged out, the rules are not in use, so
     * a healthy connection is checked every 5 minutes instead of every
     * minute. A login makes a check due at once, so the rules are current
     * when play starts, unless the relay has asked the plugin to wait. A
     * loading screen or a hop is not a login: the plugin reports them as
     * still logged in.
     */
    void loggedIn(boolean loggedIn, Instant now)
    {
        boolean login = loggedIn && !this.loggedIn;
        this.loggedIn = loggedIn;
        if (login && nextCheck.isAfter(now))
        {
            nextCheck = retryAfterUntil != null && retryAfterUntil.isAfter(now)
                ? retryAfterUntil : now;
        }
    }

    /** Whether a re-pairing is waiting for its new pairing to deliver. */
    boolean repairing()
    {
        return repairing;
    }

    /**
     * Whether this session started a first pairing that hasn't delivered:
     * its code has never worked, so a new pairing replaces it without
     * keeping it.
     */
    boolean firstPairingWaiting()
    {
        return pairingStartedAt != null && !repairing;
    }

    /**
     * This session started a new pairing while another works. The rules stay
     * in use, with their sync time, until the new pairing delivers; their
     * version is dropped, so the new code's first check asks for everything.
     */
    TrackerConnectionSnapshot repairStarted(Instant now)
    {
        acceptedVersion = null;
        rejectedVersion = null;
        rejectedReason = null;
        resetChecks();
        pairingStartedAt = now;
        repairing = true;
        return TrackerConnectionSnapshot.of(
            TrackerConnectionState.WAITING, lastSync, null, SyncReason.CONFIRM_REPAIR, null);
    }

    /** The player cancelled the re-pairing: back to the working pairing, checked at once. */
    TrackerConnectionSnapshot repairCancelled()
    {
        repairing = false;
        pairingStartedAt = null;
        resetChecks();
        return TrackerConnectionSnapshot.of(
            TrackerConnectionState.WAITING, lastSync, null, SyncReason.CHECKING, null);
    }

    /** This session started a pairing and opened the browser. */
    TrackerConnectionSnapshot pairingStarted(Instant now)
    {
        forget();
        pairingStartedAt = now;
        return TrackerConnectionSnapshot.of(
            TrackerConnectionState.WAITING, null, null, SyncReason.CONFIRM_IN_BROWSER, null);
    }

    /** The pairing code changed underneath this session (or was cleared). */
    TrackerConnectionSnapshot pairingReplaced(boolean paired)
    {
        forget();
        pairingStartedAt = null;
        return paired
            ? TrackerConnectionSnapshot.of(
                TrackerConnectionState.WAITING, null, null, SyncReason.CHECKING, null)
            : TrackerConnectionSnapshot.disconnected();
    }

    /** There is no pairing: nothing to check until one appears. */
    TrackerConnectionSnapshot unpaired(Instant now)
    {
        nextCheck = now.plusSeconds(CONNECTED_POLL_SECONDS);
        return TrackerConnectionSnapshot.disconnected();
    }

    /** Online sync was allowed or refused: start over. */
    TrackerConnectionSnapshot networkAccessChanged(boolean allowed, boolean paired)
    {
        forget();
        pairingStartedAt = null;
        return idle(allowed, paired);
    }

    /**
     * Before any check: sync off, with or without a pairing to keep; no
     * pairing; or paired and about to check.
     */
    static TrackerConnectionSnapshot idle(boolean allowed, boolean paired)
    {
        SyncReason reason = !paired ? SyncReason.NOT_PAIRED
            : allowed ? SyncReason.CHECKING : SyncReason.SYNC_OFF;
        return TrackerConnectionSnapshot.of(
            TrackerConnectionState.DISCONNECTED, null, null, reason, null);
    }

    /**
     * A local import replaced the tracker's rules: forget their version, so
     * the next check, due now, fetches the relay copy in full. False when
     * there was no version to forget.
     */
    boolean localRulesReplaced()
    {
        if (acceptedVersion == null)
        {
            return false;
        }
        acceptedVersion = null;
        resetChecks();
        return true;
    }

    /**
     * Saved tracker rules from this pairing came back at startup: hold their
     * version, so the first check asks only whether they are current. False
     * once this session has accepted anything.
     */
    boolean seed(String version)
    {
        if (acceptedVersion != null || lastSync != null)
        {
            return false;
        }
        acceptedVersion = RelayContract.canonicalVersion(version);
        return true;
    }

    /** The relay confirmed the rules the plugin holds. */
    TrackerConnectionSnapshot confirmed(Instant now)
    {
        lastSync = now;
        healthy(now);
        return TrackerConnectionSnapshot.connected(now, acceptedVersion);
    }

    /**
     * A 304 for a version the plugin does not hold. Nothing changes on
     * screen, so this returns null, but the next check waits a full interval
     * instead of the few seconds reserved while this one ran.
     */
    TrackerConnectionSnapshot unconfirmed(Instant now)
    {
        healthy(now);
        return null;
    }

    /** The client thread switched to the relay's rules at this version. */
    TrackerConnectionSnapshot accepted(String version, Instant now)
    {
        acceptedVersion = version;
        rejectedVersion = null;
        rejectedReason = null;
        lastSync = now;
        pairingStartedAt = null;
        repairing = false;
        healthy(now);
        return TrackerConnectionSnapshot.connected(now, version);
    }

    /**
     * The relay's rules at this version could not be imported, for example
     * a newer bundle format. Remember the version, so later checks ask only
     * whether something newer has arrived.
     */
    TrackerConnectionSnapshot rejected(String version, SyncReason reason, Instant now)
    {
        rejectedVersion = RelayContract.canonicalVersion(version);
        rejectedReason = reason;
        return stillRejected(now);
    }

    /**
     * The relay still has only the version the plugin could not import. The
     * status says why, not when the next check is: another check won't help
     * until the tracker sends something else or the plugin is updated.
     */
    TrackerConnectionSnapshot stillRejected(Instant now)
    {
        failed(now, FAILURE_BACKOFF_SECONDS);
        return show(TrackerConnectionState.IMPORT_FAILED, rejectedReason);
    }

    /**
     * The relay has no profile for this code. While a pairing this session
     * started is waiting for the browser, that is expected: say "Confirm in
     * browser" and keep checking, quickly at first. After 10 minutes, say no
     * profile arrived. Otherwise the relay's copy has lapsed (it keeps one
     * for 24 hours after the web app last published): the pairing still
     * works, and opening the web tracker sends the rules again. The rules
     * stay active, but their version is forgotten, so the next check sends
     * no validator and imports whatever version the relay has next, even
     * an older one after a restore.
     *
     * @param heldVersion whether the request asked about a version the plugin held
     */
    TrackerConnectionSnapshot notFound(boolean heldVersion, Instant now)
    {
        // Whatever the plugin held, or could not import, has gone with the profile.
        acceptedVersion = null;
        rejectedVersion = null;
        rejectedReason = null;
        if (!heldVersion && pairingStartedAt != null)
        {
            long waited = Duration.between(pairingStartedAt, now).getSeconds();
            if (waited < PAIRING_CONFIRM_SECONDS)
            {
                after(now, waited < PAIRING_FAST_POLL_WINDOW_SECONDS
                    ? WAITING_POLL_SECONDS : PAIRING_SLOW_POLL_SECONDS);
                return show(TrackerConnectionState.WAITING,
                    repairing ? SyncReason.CONFIRM_REPAIR : SyncReason.CONFIRM_IN_BROWSER);
            }
            healthy(now);
            if (repairing)
            {
                // The new pairing never arrived: the working one carries on.
                repairing = false;
                pairingStartedAt = null;
                return show(TrackerConnectionState.WAITING, SyncReason.REPAIR_ABANDONED);
            }
            return show(TrackerConnectionState.EXPIRED, SyncReason.NO_PROFILE);
        }
        healthy(now);
        return show(TrackerConnectionState.WAITING, SyncReason.NO_RECENT_UPDATE);
    }

    /**
     * A check failed: back off as failed does, and show the state and why,
     * with when the next check is.
     */
    TrackerConnectionSnapshot failure(
        TrackerConnectionState state, SyncReason reason, Instant now, long minimumSeconds)
    {
        failed(now, minimumSeconds);
        return TrackerConnectionSnapshot.of(state, lastSync, acceptedVersion, reason, nextCheck);
    }

    /**
     * The relay asked the plugin to slow down. A Retry-After is honoured as
     * given, from 30 seconds to an hour, instead of being stretched like a
     * failure's back-off; without one, it backs off like any failure.
     */
    TrackerConnectionSnapshot busy(Instant now, long retryAfterSeconds)
    {
        if (retryAfterSeconds <= 0)
        {
            return failure(TrackerConnectionState.OFFLINE, SyncReason.BUSY,
                now, FAILURE_BACKOFF_SECONDS);
        }
        consecutiveFailures++;
        nextCheck = now.plusSeconds(Math.max(FAILURE_BACKOFF_SECONDS,
            Math.min(retryAfterSeconds, MAX_RETRY_AFTER_SECONDS)));
        retryAfterUntil = nextCheck;
        return TrackerConnectionSnapshot.of(TrackerConnectionState.OFFLINE,
            lastSync, acceptedVersion, SyncReason.BUSY, nextCheck);
    }

    /**
     * The player pressed Check now: forget the back-off, and make a check due
     * at once. False when they pressed it under 10 seconds ago.
     */
    boolean checkNow(Instant now)
    {
        if (lastCheckNow != null && now.isBefore(lastCheckNow.plusSeconds(CHECK_NOW_SECONDS)))
        {
            return false;
        }
        lastCheckNow = now;
        resetChecks();
        return true;
    }

    /** A check failed: wait longer each time, from minimumSeconds up to 5 minutes. */
    void failed(Instant now, long minimumSeconds)
    {
        int shift = Math.min(consecutiveFailures, 5);
        long delay = minimumSeconds * (1L << shift);
        consecutiveFailures++;
        nextCheck = now.plusSeconds(Math.min(delay, MAX_FAILURE_BACKOFF_SECONDS));
    }

    /** The snapshot for a state and why, with the version and sync time held. */
    TrackerConnectionSnapshot show(TrackerConnectionState state, SyncReason reason)
    {
        return TrackerConnectionSnapshot.of(state, lastSync, acceptedVersion, reason, null);
    }

    private void forget()
    {
        acceptedVersion = null;
        rejectedVersion = null;
        rejectedReason = null;
        repairing = false;
        lastSync = null;
        resetChecks();
    }

    private void resetChecks()
    {
        consecutiveFailures = 0;
        nextCheck = Instant.EPOCH;
    }

    private void healthy(Instant now)
    {
        after(now, loggedIn ? CONNECTED_POLL_SECONDS : LOGGED_OUT_POLL_SECONDS);
    }

    private void after(Instant now, long seconds)
    {
        consecutiveFailures = 0;
        nextCheck = now.plusSeconds(seconds);
    }

}
