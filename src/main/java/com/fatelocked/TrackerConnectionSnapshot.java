package com.fatelocked;

import java.time.Instant;
import java.util.Objects;

final class TrackerConnectionSnapshot
{
    private final TrackerConnectionState state;
    private final Instant lastSync;
    private final String acceptedVersion;
    private final SyncReason reason;
    /** When the next check is due, for states that say so; null otherwise. */
    private final Instant nextCheck;
    /** The last four characters of the code in use, or null: all the sidebar shows of it. */
    private final String pairingEnding;

    private TrackerConnectionSnapshot(
        TrackerConnectionState state,
        Instant lastSync,
        String acceptedVersion,
        SyncReason reason,
        Instant nextCheck,
        String pairingEnding)
    {
        this.state = state;
        this.lastSync = lastSync;
        this.acceptedVersion = acceptedVersion;
        this.reason = reason == null ? SyncReason.NONE : reason;
        this.nextCheck = nextCheck;
        this.pairingEnding = pairingEnding;
    }

    TrackerConnectionState getState()
    {
        return state;
    }

    Instant getLastSync()
    {
        return lastSync;
    }

    String getAcceptedVersion()
    {
        return acceptedVersion;
    }

    SyncReason getReason()
    {
        return reason;
    }

    Instant getNextCheck()
    {
        return nextCheck;
    }

    String getPairingEnding()
    {
        return pairingEnding;
    }

    /** This snapshot for the pairing whose code this is. */
    TrackerConnectionSnapshot forPairing(String code)
    {
        String ending = code == null || code.length() < 4 ? null : code.substring(code.length() - 4);
        return new TrackerConnectionSnapshot(
            state, lastSync, acceptedVersion, reason, nextCheck, ending);
    }

    /** The short status: the reason's, or the state's own. */
    String getMessage()
    {
        return reason.status != null ? reason.status : stateStatus(state);
    }

    static TrackerConnectionSnapshot disconnected()
    {
        return of(TrackerConnectionState.DISCONNECTED, null, null, SyncReason.NOT_PAIRED, null);
    }

    static TrackerConnectionSnapshot waiting()
    {
        return of(TrackerConnectionState.WAITING, null, null, SyncReason.NONE, null);
    }

    static TrackerConnectionSnapshot connected(
        Instant at, String version)
    {
        return of(TrackerConnectionState.CONNECTED, at, version, SyncReason.NONE, null);
    }

    static TrackerConnectionSnapshot of(
        TrackerConnectionState state,
        Instant lastSync,
        String acceptedVersion,
        SyncReason reason,
        Instant nextCheck)
    {
        return new TrackerConnectionSnapshot(
            state, lastSync, acceptedVersion, reason, nextCheck, null);
    }

    private static String stateStatus(TrackerConnectionState state)
    {
        switch (state)
        {
            case DISCONNECTED:
                return "Not connected";
            case WAITING:
                return "Waiting for tracker";
            case IMPORTING:
                return "Importing tracker data";
            case CONNECTED:
                return "Connected";
            case EXPIRED:
                return "Pairing request expired";
            case OFFLINE:
                return "Tracker is offline";
            case IMPORT_FAILED:
                return "Could not import tracker data";
            default:
                return "";
        }
    }

    /** Two snapshots that would show the same thing are equal, so nothing republishes it. */
    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof TrackerConnectionSnapshot)) return false;
        TrackerConnectionSnapshot that = (TrackerConnectionSnapshot) other;
        return state == that.state
            && Objects.equals(lastSync, that.lastSync)
            && Objects.equals(acceptedVersion, that.acceptedVersion)
            && reason == that.reason
            && Objects.equals(nextCheck, that.nextCheck)
            && Objects.equals(pairingEnding, that.pairingEnding);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(state, lastSync, acceptedVersion, reason, nextCheck, pairingEnding);
    }
}
