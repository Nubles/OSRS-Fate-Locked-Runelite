package com.fatelocked.guardian;

import com.fatelocked.guardian.travel.TravelDecision;
import lombok.Value;

@Value
public class GuardResult
{
    public enum Outcome
    {
        ALLOW,
        BLOCK,
        /** Proven locked, and let through only because Strict Mode is paused. */
        ALLOW_PAUSED
    }

    Outcome outcome;
    TravelDecision decision;
}
