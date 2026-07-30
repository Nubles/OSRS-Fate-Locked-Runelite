package com.fatelocked.guardian;

import lombok.Value;

@Value
public class StrictModeAuditEntry
{
    long timestamp;
    String actionKind;
    String target;
    String chunk;
    String reason;
    String outcome;
    boolean paused;
    boolean alternativeAvailable;

    public StrictModeAuditEntry(
        long timestamp,
        String actionKind,
        String target,
        String chunk,
        String reason)
    {
        this(timestamp, actionKind, target, chunk, reason,
            "BLOCKED", false, false);
    }

    public StrictModeAuditEntry(
        long timestamp,
        String actionKind,
        String target,
        String chunk,
        String reason,
        String outcome,
        boolean paused,
        boolean alternativeAvailable)
    {
        this.timestamp = timestamp;
        this.actionKind = actionKind;
        this.target = target;
        this.chunk = chunk;
        this.reason = reason;
        this.outcome = outcome;
        this.paused = paused;
        this.alternativeAvailable = alternativeAvailable;
    }
}
