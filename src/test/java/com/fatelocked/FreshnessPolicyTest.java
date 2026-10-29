package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fatelocked.FateLockedPlugin.RulesSource;
import com.fatelocked.FreshnessPolicy.Why;
import java.time.Duration;
import java.time.Instant;
import org.junit.Test;

public class FreshnessPolicyTest
{
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant JUST_CONFIRMED = NOW.minusSeconds(30);
    private static final Instant OLD_EXPORT = NOW.minus(Duration.ofDays(14));

    @Test
    public void trackerRulesSentLatelyAndConfirmedLatelyAreFresh()
    {
        FreshnessPolicy.Verdict verdict = check(JUST_CONFIRMED, NOW.minus(Duration.ofHours(2)));
        assertTrue(verdict.isFresh());
        assertEquals(Duration.ofHours(2), verdict.getAge());
        assertTrue("just under the limit",
            check(JUST_CONFIRMED, NOW.minus(Duration.ofHours(12)).plusSeconds(1)).isFresh());
    }

    @Test
    public void trackerRulesOlderThanTheLimitAreStaleWithTheirAge()
    {
        FreshnessPolicy.Verdict verdict = check(JUST_CONFIRMED, NOW.minus(Duration.ofHours(14)));
        assertEquals(Why.TRACKER_RULES_OLD, verdict.getWhy());
        assertEquals(Duration.ofHours(14), verdict.getAge());
        assertTrue(verdict.isTrackerRulesOld());
        assertEquals(Why.TRACKER_RULES_OLD, check(JUST_CONFIRMED, NOW.minus(Duration.ofHours(12))).getWhy());
    }

    /**
     * ECO-3: a 304 just now says only that the relay still holds the last thing the tracker
     * published. A publish that failed, or a roll on a second device, leaves it unchanged, so
     * the confirmation never makes two-week-old rules fresh.
     */
    @Test
    public void aRelayConfirmationJustNowDoesNotMakeOldRulesFresh()
    {
        assertFalse(FreshnessPolicy.isFresh(RulesSource.RELAY, true, NOW, OLD_EXPORT, NOW));
        FreshnessPolicy.Verdict verdict = check(NOW, OLD_EXPORT);
        assertEquals(Why.TRACKER_RULES_OLD, verdict.getWhy());
        assertEquals(Duration.ofDays(14), verdict.getAge());
    }

    @Test
    public void theRelayMustStillAnswerHoweverYoungTheRules()
    {
        Instant sent = NOW.minus(Duration.ofMinutes(5));
        assertTrue(check(NOW.minus(Duration.ofMinutes(14).plusSeconds(59)), sent).isFresh());
        assertEquals(Why.NOT_CONFIRMED, check(NOW.minus(Duration.ofMinutes(15)), sent).getWhy());
        assertEquals("never confirmed", Why.NOT_CONFIRMED, check(null, sent).getWhy());
        assertFalse("not the case that asks to open the tracker",
            check(null, sent).isTrackerRulesOld());
    }

    /**
     * Rules from a tracker that doesn't send its export time (an old or broken one) are of
     * unknown age. The safe answer after ECO-3 is to let the player through: Strict Mode never
     * blocks on rules it can't date, and the status card and chat ask for the tracker.
     */
    @Test
    public void trackerRulesWithNoSendTimeAreNeverFresh()
    {
        FreshnessPolicy.Verdict verdict = check(JUST_CONFIRMED, null);
        assertEquals(Why.NO_SEND_TIME, verdict.getWhy());
        assertNull(verdict.getAge());
        assertTrue(verdict.isTrackerRulesOld());

        assertEquals("a clock far ahead can't be dated either", Why.NO_SEND_TIME,
            check(JUST_CONFIRMED, NOW.plus(Duration.ofMinutes(6))).getWhy());
        FreshnessPolicy.Verdict skewed = check(JUST_CONFIRMED, NOW.plus(Duration.ofMinutes(5)));
        assertTrue("within the skew counts as just sent", skewed.isFresh());
        assertEquals(Duration.ZERO, skewed.getAge());
    }

    @Test
    public void theLimitIsThePlayersAndDefaultsToTwelveHours()
    {
        Instant sent = NOW.minus(Duration.ofHours(20));
        assertTrue(FreshnessPolicy.check(RulesSource.RELAY, true, JUST_CONFIRMED, sent, NOW,
            Duration.ofHours(24)).isFresh());
        assertFalse(FreshnessPolicy.check(RulesSource.RELAY, true, JUST_CONFIRMED, sent, NOW,
            Duration.ofHours(6)).isFresh());
        assertEquals(Duration.ofHours(12), FreshnessPolicy.DEFAULT_TRACKER_MAX_AGE);
        for (Duration unset : new Duration[]{null, Duration.ZERO, Duration.ofHours(-1)})
        {
            assertTrue(unset + " means the default", FreshnessPolicy.check(RulesSource.RELAY, true, JUST_CONFIRMED,
                NOW.minus(Duration.ofHours(11)), NOW, unset).isFresh());
            assertFalse(unset + " means the default", FreshnessPolicy.check(RulesSource.RELAY, true,
                JUST_CONFIRMED, NOW.minus(Duration.ofHours(13)), NOW, unset).isFresh());
        }
    }

    @Test
    public void trackerRulesNoLongerCheckedCountFromTheirExport()
    {
        assertTrue(fresh(RulesSource.RELAY, false, null, NOW.minus(Duration.ofMinutes(2))));
        assertFalse(fresh(RulesSource.RELAY, false, NOW, OLD_EXPORT));
        assertEquals(Why.EXPORT_OLD, FreshnessPolicy.check(RulesSource.RELAY, false, NOW,
            NOW.minus(Duration.ofHours(1)), NOW, null).getWhy());
        assertFalse("unpaired rules don't ask for the tracker in chat", FreshnessPolicy.check(RulesSource.RELAY,
            false, NOW, OLD_EXPORT, NOW, null).isTrackerRulesOld());
    }

    @Test
    public void backupsCountFromTheirExportNeverFromWhenTheyWereLoaded()
    {
        for (RulesSource local : new RulesSource[]{RulesSource.FILE, RulesSource.IMPORT})
        {
            assertTrue(local.name(), fresh(local, true, NOW, NOW.minus(Duration.ofMinutes(14))));
            assertFalse(local.name(), fresh(local, true, NOW, NOW.minus(Duration.ofMinutes(15))));
            assertFalse(local + " from a clock far ahead", fresh(local, true, NOW, NOW.plus(Duration.ofMinutes(6))));
            assertTrue(local + " within the skew", fresh(local, true, NOW, NOW.plus(Duration.ofMinutes(5))));
            assertFalse(local + " without an export time", fresh(local, true, NOW, null));
            assertEquals(local.name(), Why.EXPORT_OLD,
                FreshnessPolicy.check(local, true, NOW, null, NOW, null).getWhy());
        }
    }

    @Test
    public void noRulesAreNeverFresh()
    {
        assertFalse(fresh(RulesSource.NONE, true, NOW, NOW));
        assertEquals(Why.NO_RULES, FreshnessPolicy.check(RulesSource.NONE, true, NOW, NOW, NOW, null).getWhy());
    }

    /** Paired tracker rules with the default limit. */
    private static FreshnessPolicy.Verdict check(Instant lastSync, Instant exported)
    {
        return FreshnessPolicy.check(RulesSource.RELAY, true, lastSync, exported, NOW, null);
    }

    private static boolean fresh(RulesSource source, boolean paired, Instant lastSync, Instant exported)
    {
        return FreshnessPolicy.isFresh(source, paired, lastSync, exported, NOW);
    }
}
