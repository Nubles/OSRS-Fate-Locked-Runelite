package com.fatelocked.guardian.travel;

import lombok.Value;

import java.time.Instant;

/** Immutable, transient presentation state for a blocked travel action. */
@Value
public class TravelBlockNotice
{
    String fingerprint;
    String headline;
    String reason;
    String alternative;
    Instant expiresAt;
}
