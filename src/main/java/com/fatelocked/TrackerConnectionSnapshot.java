package com.fatelocked;

import java.time.Instant;

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
}
