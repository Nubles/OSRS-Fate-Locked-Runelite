package com.fatelocked;

import org.junit.Test;

import static com.fatelocked.FateLockedPlugin.RulesSource.FILE;
import static com.fatelocked.FateLockedPlugin.RulesSource.IMPORT;
import static com.fatelocked.FateLockedPlugin.RulesSource.NONE;
import static com.fatelocked.FateLockedPlugin.RulesSource.RELAY;
import static com.fatelocked.RulesPrecedence.mayReplace;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RulesPrecedenceTest
{
    @Test
    public void rulesFoundAtStartupOnlyFillAnEmptySlot()
    {
        for (RulesPrecedence.Arrival startup : new RulesPrecedence.Arrival[] {
            RulesPrecedence.Arrival.SAVED, RulesPrecedence.Arrival.STARTUP_FILE })
        {
            assertTrue(startup.name(), mayReplace(NONE, startup));
            assertFalse(startup.name(), mayReplace(RELAY, startup));
            assertFalse(startup.name(), mayReplace(IMPORT, startup));
            assertFalse(startup.name(), mayReplace(FILE, startup));
        }
    }

    @Test
    public void theTrackerWinsWhilePaired()
    {
        for (FateLockedPlugin.RulesSource active : FateLockedPlugin.RulesSource.values())
        {
            assertTrue(active.name(), mayReplace(active, RulesPrecedence.Arrival.RELAY));
        }
    }

    @Test
    public void aPlayersOwnImportAlwaysApplies()
    {
        for (FateLockedPlugin.RulesSource active : FateLockedPlugin.RulesSource.values())
        {
            assertTrue(active.name(), mayReplace(active, RulesPrecedence.Arrival.IMPORT));
        }
    }
}
