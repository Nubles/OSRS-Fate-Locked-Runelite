package com.fatelocked;

import java.time.Instant;
import java.util.Objects;

final class TrackerConnectionSnapshot
{
    private final TrackerConnectionState state;
    private final Instant lastSync;
    private final String acceptedVersion;
    private final String message;

    private TrackerConnectionSnapshot(
        TrackerConnectionState state,
        Instant lastSync,
        String acceptedVersion,
        String message)
    {
        this.state = state;
        this.lastSync = lastSync;
        this.acceptedVersion = acceptedVersion;
        this.message = message;
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

    String getMessage()
    {
        return message;
    }

    static TrackerConnectionSnapshot disconnected()
    {
        return new TrackerConnectionSnapshot(
            TrackerConnectionState.DISCONNECTED,
            null, null, "Not connected");
    }

    static TrackerConnectionSnapshot waiting()
    {
        return new TrackerConnectionSnapshot(
            TrackerConnectionState.WAITING,
            null, null, "Waiting for tracker");
    }

    static TrackerConnectionSnapshot connected(
        Instant at, String version)
    {
        return new TrackerConnectionSnapshot(
            TrackerConnectionState.CONNECTED,
            at, version, "Connected");
    }

    static TrackerConnectionSnapshot of(
        TrackerConnectionState state,
        Instant lastSync,
        String acceptedVersion,
        String message)
    {
        return new TrackerConnectionSnapshot(
            state, lastSync, acceptedVersion, message);
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
            && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(state, lastSync, acceptedVersion, message);
    }
}
