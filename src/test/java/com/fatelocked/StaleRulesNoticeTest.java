package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.fatelocked.FreshnessPolicy.Verdict;
import com.fatelocked.FreshnessPolicy.Why;
import com.fatelocked.guardian.StrictModeReadiness;
import java.time.Duration;
import org.junit.Test;

/** ECO-3: one chat line when old rules stop Strict Mode, one when fresh rules bring it back. */
public class StaleRulesNoticeTest
{
    private static final StrictModeReadiness STALE = StrictModeReadiness.evaluate(
        true, false, true, true, "Iron Example", "Iron Example", true, false);
    private static final StrictModeReadiness ACTIVE = StrictModeReadiness.evaluate(
        true, false, true, true, "Iron Example", "Iron Example", true, true);
    private static final Verdict OLD = new Verdict(Why.TRACKER_RULES_OLD, Duration.ofHours(14).plusMinutes(10));
    private static final Verdict FRESH = new Verdict(Why.FRESH, Duration.ofMinutes(3));

    @Test
    public void saysOnceThatStrictModePausedForOldRulesAndOnceThatItIsBack()
    {
        StaleRulesNotice notice = new StaleRulesNotice();
        assertNull("nothing to say while fresh", notice.next(ACTIVE, FRESH));

        assertEquals("Strict Mode paused: the rules are 14 h old. Open the tracker, or reload its tab, so it sends"
            + " them again.", notice.next(STALE, OLD));
        for (int tick = 0; tick < 100; tick++)
        {
            assertNull("a long session hears it once", notice.next(STALE,
                new Verdict(Why.TRACKER_RULES_OLD, Duration.ofHours(14).plusMinutes(tick))));
        }
        assertNull("the relay going quiet doesn't repeat it", notice.next(STALE,
            new Verdict(Why.NOT_CONFIRMED, Duration.ofHours(16))));

        assertEquals(StaleRulesNotice.RESUMED, notice.next(ACTIVE, FRESH));
        assertNull(notice.next(ACTIVE, FRESH));
        assertEquals("old again later: said again", "Strict Mode paused: the rules are 14 h old. Open the tracker,"
            + " or reload its tab, so it sends them again.", notice.next(STALE, OLD));
    }

    @Test
    public void rulesThatDontSayWhenTheyWereSentAreNamed()
    {
        assertEquals("Strict Mode paused: the rules don't say when the tracker sent them. Open the tracker, or"
            + " reload its tab, so it sends them again.",
            new StaleRulesNotice().next(STALE, new Verdict(Why.NO_SEND_TIME, null)));
    }

    /** Only when the rules' age is the one thing stopping Strict Mode: never as noise. */
    @Test
    public void quietWhenSomethingElseStopsStrictModeOrTheRulesAreNotTheTrackersOld()
    {
        StaleRulesNotice notice = new StaleRulesNotice();
        assertNull("Strict Mode off", notice.next(StrictModeReadiness.evaluate(
            false, false, true, true, "Iron Example", "Iron Example", true, false), OLD));
        assertNull("another character", notice.next(StrictModeReadiness.evaluate(
            true, false, true, true, "Iron Example", "Someone Else", false, false), OLD));
        assertNull("the relay silent: the card says so, not chat", notice.next(STALE,
            new Verdict(Why.NOT_CONFIRMED, Duration.ofHours(1))));
        assertNull("an old backup", notice.next(STALE, new Verdict(Why.EXPORT_OLD, Duration.ofHours(1))));
        assertNull("fresh rules after nothing was said announce nothing", notice.next(ACTIVE, FRESH));
    }
}
