package com.fatelocked;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fatelocked.FateLockedPlugin.RulesSource;
import java.time.Duration;
import java.time.Instant;
import org.junit.Test;

public class FreshnessPolicyTest
{
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant OLD_EXPORT = NOW.minus(Duration.ofDays(14));

    @Test
    public void trackerRulesStayFreshFor15MinutesAfterTheRelaysLastWord()
    {
        assertTrue(fresh(RulesSource.RELAY, true, NOW.minus(Duration.ofMinutes(14).plusSeconds(59)), OLD_EXPORT));
        assertFalse(fresh(RulesSource.RELAY, true, NOW.minus(Duration.ofMinutes(15)), OLD_EXPORT));
        assertFalse("never confirmed", fresh(RulesSource.RELAY, true, null, NOW));
    }

    @Test
    public void trackerRulesNoLongerCheckedCountFromTheirExport()
    {
        assertTrue(fresh(RulesSource.RELAY, false, null, NOW.minus(Duration.ofMinutes(2))));
        assertFalse(fresh(RulesSource.RELAY, false, NOW, OLD_EXPORT));
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
        }
    }

    @Test
    public void noRulesAreNeverFresh()
    {
        assertFalse(fresh(RulesSource.NONE, true, NOW, NOW));
    }

    private static boolean fresh(RulesSource source, boolean paired, Instant lastSync, Instant exported)
    {
        return FreshnessPolicy.isFresh(source, paired, lastSync, exported, NOW);
    }
}
