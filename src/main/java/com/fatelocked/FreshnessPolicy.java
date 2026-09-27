package com.fatelocked;

import java.time.Duration;
import java.time.Instant;

/**
 * Whether the rules in force are recent enough to act on: one answer for Strict Mode,
 * the status card and the HUD. Pure.
 *
 * <p>Tracker rules stay fresh while the relay keeps confirming them. Backups (files and
 * the clipboard), and tracker rules no longer being checked, count from when the
 * tracker exported them, so an old file can never block anything.
 */
final class FreshnessPolicy
{
    /** How long rules stay fresh after the relay's last confirmation, or their export. */
    static final Duration WINDOW = Duration.ofMinutes(15);
    /** How far in the future an export time may be before it can't be trusted. */
    static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private FreshnessPolicy()
    {
    }

    /**
     * @param paired     whether online sync is on and paired, so the relay confirms the rules
     * @param lastSync   the relay's last delivery or confirmation, or null
     * @param exportedAt when the tracker exported the rules, or null
     */
    static boolean isFresh(FateLockedPlugin.RulesSource source, boolean paired, Instant lastSync,
        Instant exportedAt, Instant now)
    {
        if (source == FateLockedPlugin.RulesSource.NONE)
        {
            return false;
        }
        if (source == FateLockedPlugin.RulesSource.RELAY && paired)
        {
            return lastSync != null && Duration.between(lastSync, now).compareTo(WINDOW) < 0;
        }
        if (exportedAt == null || exportedAt.isAfter(now.plus(CLOCK_SKEW)))
        {
            return false;
        }
        return Duration.between(exportedAt, now).compareTo(WINDOW) < 0;
    }
}
