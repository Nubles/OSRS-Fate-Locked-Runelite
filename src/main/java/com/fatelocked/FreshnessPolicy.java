package com.fatelocked;

import java.time.Duration;
import java.time.Instant;

/**
 * Whether the rules in force are recent enough to act on: one answer for Strict Mode,
 * the status card and the HUD. Pure.
 *
 * <p>Tracker rules need two things to be fresh. The relay must have answered lately, or
 * RuneLite can't know whether the tracker sent something newer. And the rules themselves,
 * counted from when the tracker sent them (their {@code exportedAt}), must be younger than
 * 12 hours (ECO-3). A 304 from the relay proves
 * only that RuneLite holds the last thing the tracker published, not that the tracker is
 * still sending: a publish that failed, or a roll on a second device, leaves the relay's
 * copy unchanged and every check still answers 304. So a confirmation alone never makes
 * old rules fresh, and Strict Mode stops blocking rather than enforce locks that may have
 * been lifted.
 *
 * <p>Rules that don't say when the tracker sent them (an export time missing or unreadable,
 * or further ahead than this computer's clock allows) are never fresh: their age can't be
 * known, so Strict Mode lets the player through. Every current tracker sends the time; a
 * bundle without it is from an old or broken tracker.
 *
 * <p>Backups (files and the clipboard), and tracker rules no longer being checked, count
 * from when the tracker exported them too, with a short window, so an old file can never
 * block anything.
 */
final class FreshnessPolicy
{
    /** How long rules stay fresh after the relay's last answer, or a backup after its export. */
    static final Duration WINDOW = Duration.ofMinutes(15);
    /** How far in the future an export time may be before it can't be trusted. */
    static final Duration CLOCK_SKEW = Duration.ofMinutes(5);
    /**
     * How old the tracker's rules may be, from when it sent them, before Strict Mode stops
     * blocking. Rules change only when the player
     * rolls or unlocks in the tracker, and the tracker sends them again, with a new time, each
     * time it opens and each time the run changes. Twelve hours covers a long day's play started
     * from the tracker; the next day's play starts by opening the tracker, as rolling needs.
     */
    static final Duration DEFAULT_TRACKER_MAX_AGE = Duration.ofHours(12);

    /** Why the rules are, or aren't, fresh. */
    enum Why
    {
        FRESH,
        /** No rules are loaded. */
        NO_RULES,
        /** Paired, but the relay hasn't answered within {@link #WINDOW}. */
        NOT_CONFIRMED,
        /** Paired and confirmed, but the tracker sent them longer ago than the limit. */
        TRACKER_RULES_OLD,
        /** Paired and confirmed, but they don't say when the tracker sent them. */
        NO_SEND_TIME,
        /** A backup, or tracker rules nothing checks, exported too long ago or at no known time. */
        EXPORT_OLD
    }

    /** The answer, with the rules' age when it is known. */
    static final class Verdict
    {
        private final Why why;
        private final Duration age;

        Verdict(Why why, Duration age)
        {
            this.why = why;
            this.age = age;
        }

        Why getWhy()
        {
            return why;
        }

        /** How long ago the tracker sent the rules; null when that isn't known. */
        Duration getAge()
        {
            return age;
        }

        boolean isFresh()
        {
            return why == Why.FRESH;
        }

        /**
         * Whether the tracker's rules are confirmed but too old, or of unknown age: the case
         * where Strict Mode stops blocking and asks the player to open the tracker.
         */
        boolean isTrackerRulesOld()
        {
            return why == Why.TRACKER_RULES_OLD || why == Why.NO_SEND_TIME;
        }

        @Override
        public boolean equals(Object other)
        {
            if (!(other instanceof Verdict)) return false;
            Verdict that = (Verdict) other;
            return why == that.why && java.util.Objects.equals(age, that.age);
        }

        @Override
        public int hashCode()
        {
            return java.util.Objects.hash(why, age);
        }

        @Override
        public String toString()
        {
            return why + (age == null ? "" : " " + age);
        }
    }

    private FreshnessPolicy()
    {
    }

    /** {@link #check} with the default limit for the tracker's rules. */
    static boolean isFresh(FateLockedPlugin.RulesSource source, boolean paired, Instant lastSync,
        Instant exportedAt, Instant now)
    {
        return check(source, paired, lastSync, exportedAt, now, DEFAULT_TRACKER_MAX_AGE).isFresh();
    }

    /**
     * @param paired         whether online sync is on and paired, so the relay confirms the rules
     * @param lastSync       the relay's last delivery or confirmation (a 304 counts), or null
     * @param exportedAt     when the tracker sent the rules, from the rules themselves, or null
     * @param trackerMaxAge  how old the tracker's rules may be; null or not positive means the default
     *                       (a parameter so a later setting can change it)
     */
    static Verdict check(FateLockedPlugin.RulesSource source, boolean paired, Instant lastSync,
        Instant exportedAt, Instant now, Duration trackerMaxAge)
    {
        if (source == FateLockedPlugin.RulesSource.NONE)
        {
            return new Verdict(Why.NO_RULES, null);
        }
        Duration age = exportedAt == null || exportedAt.isAfter(now.plus(CLOCK_SKEW))
            ? null : nonNegative(Duration.between(exportedAt, now));
        if (source == FateLockedPlugin.RulesSource.RELAY && paired)
        {
            if (lastSync == null || Duration.between(lastSync, now).compareTo(WINDOW) >= 0)
            {
                return new Verdict(Why.NOT_CONFIRMED, age);
            }
            if (age == null)
            {
                return new Verdict(Why.NO_SEND_TIME, null);
            }
            Duration limit = trackerMaxAge == null || trackerMaxAge.isZero() || trackerMaxAge.isNegative()
                ? DEFAULT_TRACKER_MAX_AGE : trackerMaxAge;
            return new Verdict(age.compareTo(limit) < 0 ? Why.FRESH : Why.TRACKER_RULES_OLD, age);
        }
        if (age == null || age.compareTo(WINDOW) >= 0)
        {
            return new Verdict(Why.EXPORT_OLD, age);
        }
        return new Verdict(Why.FRESH, age);
    }

    /** An export time within the clock skew counts as just now. */
    private static Duration nonNegative(Duration age)
    {
        return age.isNegative() ? Duration.ZERO : age;
    }
}
