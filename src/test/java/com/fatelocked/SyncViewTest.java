package com.fatelocked;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SyncViewTest
{
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    /** 18:00 in London. */
    private static final Instant NOW = Instant.parse("2026-09-26T17:00:00Z");

    @Test
    public void connectedShowsWhenTheRulesWereLastConfirmedOnThePlayersClock()
    {
        SyncView view = view(TrackerConnectionSnapshot.connected(
            Instant.parse("2026-09-26T16:59:00Z"), "41"));

        assertEquals("Connected", view.status);
        assertEquals(SyncView.Tone.GREEN, view.tone);
        assertEquals("17:59", view.lastSync);
        assertNull(view.detail);
        assertEquals(SyncView.Action.CHECK_NOW, view.action);
    }

    @Test
    public void aFailedCheckSaysWhyAndWhenTheNextCheckIs()
    {
        SyncView view = view(TrackerConnectionSnapshot.of(TrackerConnectionState.OFFLINE,
            Instant.parse("2026-09-26T16:30:00Z"), "41",
            SyncReason.UNREACHABLE, NOW.plusSeconds(120)));

        assertEquals("Could not reach tracker", view.status);
        assertEquals("RuneLite couldn't reach the tracker. Next check at 18:02.", view.detail);
        assertEquals("17:30", view.lastSync);
        assertEquals(SyncView.Action.CHECK_NOW, view.action);
    }

    @Test
    public void syncOffKeepsThePairingAndPointsToTheSetting()
    {
        SyncView view = view(SyncMachine.idle(false, true));

        assertEquals("Online sync is off", view.status);
        assertTrue(view.detail, view.detail.contains("Enable online sync"));
        assertEquals(SyncView.Action.ENABLE_SYNC, view.action);
        assertEquals("\u2014", view.lastSync);
    }

    @Test
    public void withNoPairingTheActionIsConnect()
    {
        for (boolean allowed : new boolean[] {false, true})
        {
            SyncView view = view(SyncMachine.idle(allowed, false));
            assertEquals("Not connected", view.status);
            assertEquals(SyncView.Action.CONNECT, view.action);
        }
    }

    @Test
    public void aPairingThatTimedOutPointsToConnect()
    {
        SyncView view = view(TrackerConnectionSnapshot.of(TrackerConnectionState.EXPIRED,
            null, null, SyncReason.NO_PROFILE, null));

        assertEquals(SyncView.Tone.RED, view.tone);
        assertEquals(SyncView.Action.CONNECT, view.action);
    }

    @Test
    public void aLapsedProfilePointsToTheWebTracker()
    {
        SyncView view = view(TrackerConnectionSnapshot.of(TrackerConnectionState.WAITING,
            NOW.minusSeconds(3600), null, SyncReason.NO_RECENT_UPDATE, null));

        assertEquals(SyncView.Tone.AMBER, view.tone);
        assertEquals(SyncView.Action.OPEN_TRACKER, view.action);
    }

    @Test
    public void aNewerBundleFormatAsksForAPluginUpdate()
    {
        SyncView view = view(TrackerConnectionSnapshot.of(TrackerConnectionState.IMPORT_FAILED,
            null, "41", SyncReason.FUTURE_FORMAT, null));

        assertEquals("Plugin update needed", view.status);
        assertEquals(SyncView.Tone.RED, view.tone);
        assertEquals(SyncView.Action.UPDATE_PLUGIN, view.action);
        assertTrue(view.detail, view.detail.contains("Plugin Hub"));
    }

    @Test
    public void rulesThatCannotBeUsedPointToTheWebTracker()
    {
        SyncView view = view(TrackerConnectionSnapshot.of(TrackerConnectionState.IMPORT_FAILED,
            null, "41", SyncReason.INVALID_RULES, null));

        assertEquals(SyncView.Action.OPEN_TRACKER, view.action);
    }

    @Test
    public void checkNowIsOfferedOnlyWithAPairingAndSyncOn()
    {
        assertFalse(view(SyncMachine.idle(true, false)).canCheckNow);
        assertFalse(view(SyncMachine.idle(false, true)).canCheckNow);
        assertTrue(view(SyncMachine.idle(true, true)).canCheckNow);
        assertTrue(view(TrackerConnectionSnapshot.connected(NOW, "41")).canCheckNow);
        assertTrue(view(TrackerConnectionSnapshot.of(TrackerConnectionState.OFFLINE,
            null, null, SyncReason.BUSY, NOW.plusSeconds(120))).canCheckNow);
    }

    @Test
    public void everyReasonHasItsOwnStatusAndExplanation()
    {
        Set<String> statuses = new HashSet<>();
        Set<String> details = new HashSet<>();
        for (SyncReason reason : SyncReason.values())
        {
            if (reason == SyncReason.NONE) continue;
            SyncView view = view(TrackerConnectionSnapshot.of(
                TrackerConnectionState.OFFLINE, null, null, reason, NOW.plusSeconds(30)));
            assertTrue(reason + " shares its status", statuses.add(view.status));
            if (reason == SyncReason.CHECKING)
            {
                // "Checking the tracker" says it all.
                assertNull(view.detail);
                continue;
            }
            assertNotNull(reason + " has no explanation", view.detail);
            assertTrue(reason + " shares its explanation", details.add(view.detail));
        }
    }

    private static SyncView view(TrackerConnectionSnapshot snapshot)
    {
        return SyncView.of(snapshot, NOW, LONDON);
    }
}
