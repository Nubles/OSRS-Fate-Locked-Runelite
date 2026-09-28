package com.fatelocked;

import com.fatelocked.rules.Trust;
import java.time.Instant;
import java.time.ZoneId;
import lombok.Builder;
import lombok.Value;

/**
 * Everything the status card depends on, gathered in one place so the card is a pure
 * function of it (C2). Built on the client thread from the plugin's state.
 */
@Value
@Builder
class StatusFacts
{
    TrackerConnectionSnapshot connection;
    /** Where the rules in force came from. */
    FateLockedPlugin.RulesSource source;
    /** How they arrived, or null when not known. */
    RulesPrecedence.Arrival arrival;
    /** When they arrived, or were saved by the last start; null when not known. */
    Instant arrivedAt;
    /** When the tracker exported them, or null. */
    Instant exportedAt;
    /** Whether they are a version 1–3 export. */
    boolean legacy;
    Trust trust;
    /** Whether the rules name a character. */
    boolean bound;
    /** The run's character as players see it, or null. */
    String boundAccount;
    /** The character logged in, or null. */
    String loggedInAs;
    /** Whether online sync is on and paired, so the relay confirms the rules. */
    boolean paired;
    /** FreshnessPolicy's answer, which Strict Mode also uses. */
    boolean fresh;
    Instant now;
    ZoneId zone;
}
