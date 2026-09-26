package com.fatelocked;

import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SyncMachineTest
{
    private static final Instant START = Instant.parse("2026-09-25T12:00:00Z");

    private final SyncMachine machine = new SyncMachine();

    @Test
    public void aCheckIsDueAtOnceAndReservesTheNextTickWhileItRuns()
    {
        assertTrue(machine.checkDue(START));
        assertFalse(machine.checkDue(START.plusSeconds(SyncMachine.WAITING_POLL_SECONDS - 1)));
        assertTrue(machine.checkDue(START.plusSeconds(SyncMachine.WAITING_POLL_SECONDS)));
    }

    @Test
    public void failuresWaitTwiceAsLongEachTimeUpToFiveMinutes()
    {
        long[] waits = {30, 60, 120, 240, 300, 300};
        Instant now = START;
        for (long wait : waits)
        {
            machine.failed(now, SyncMachine.FAILURE_BACKOFF_SECONDS);
            assertFalse("before " + wait, machine.checkDue(now.plusSeconds(wait - 1)));
            now = now.plusSeconds(wait);
            assertTrue("after " + wait, machine.checkDue(now));
        }
    }

    @Test
    public void beforeAnyCheckTheStatusSaysWhetherSyncIsOffOrAboutToCheck()
    {
        assertEquals(SyncReason.CHECKING, SyncMachine.idle(true, true).getReason());
        assertEquals(SyncReason.SYNC_OFF, SyncMachine.idle(false, true).getReason());
        assertEquals(SyncReason.NOT_PAIRED, SyncMachine.idle(true, false).getReason());
        assertEquals(SyncReason.NOT_PAIRED, SyncMachine.idle(false, false).getReason());
    }

    @Test
    public void aFailureSaysWhyAndWhenTheNextCheckIs()
    {
        machine.accepted("41", START);

        TrackerConnectionSnapshot failed = machine.failure(TrackerConnectionState.OFFLINE,
            SyncReason.UNREACHABLE, START.plusSeconds(60), SyncMachine.FAILURE_BACKOFF_SECONDS);

        assertEquals(SyncReason.UNREACHABLE, failed.getReason());
        assertEquals(START.plusSeconds(90), failed.getNextCheck());
        assertEquals(START, failed.getLastSync());
        assertFalse(machine.checkDue(START.plusSeconds(89)));
        assertTrue(machine.checkDue(START.plusSeconds(90)));
    }

    @Test
    public void aRetryAfterIsHonouredAsGivenFromThirtySecondsToAnHour()
    {
        Instant now = START;
        // Asked twice for two minutes: two minutes each time, not doubled.
        for (int time = 0; time < 2; time++)
        {
            TrackerConnectionSnapshot busy = machine.busy(now, 120);
            assertEquals(SyncReason.BUSY, busy.getReason());
            assertEquals(now.plusSeconds(120), busy.getNextCheck());
            now = now.plusSeconds(120);
        }
        assertEquals(now.plusSeconds(30), machine.busy(now, 5).getNextCheck());
        assertEquals(now.plusSeconds(3600), machine.busy(now, 7200).getNextCheck());
    }

    @Test
    public void aBusyReplyWithoutRetryAfterBacksOffLikeAFailure()
    {
        assertEquals(START.plusSeconds(30), machine.busy(START, 0).getNextCheck());
        assertEquals(START.plusSeconds(60), machine.busy(START, 0).getNextCheck());
    }

    @Test
    public void checkNowForgetsTheBackOffAtMostEveryTenSeconds()
    {
        for (int failure = 0; failure < 4; failure++)
        {
            machine.failed(START, SyncMachine.FAILURE_BACKOFF_SECONDS);
        }
        assertFalse(machine.checkDue(START));

        assertTrue(machine.checkNow(START));
        assertTrue(machine.checkDue(START));
        assertFalse(machine.checkNow(START.plusSeconds(SyncMachine.CHECK_NOW_SECONDS - 1)));
        assertTrue(machine.checkNow(START.plusSeconds(SyncMachine.CHECK_NOW_SECONDS)));
        // And the next failure waits the shortest time again.
        machine.failed(START.plusSeconds(10), SyncMachine.FAILURE_BACKOFF_SECONDS);
        assertTrue(machine.checkDue(START.plusSeconds(10 + SyncMachine.FAILURE_BACKOFF_SECONDS)));
    }

    @Test
    public void loggedOutAHealthyConnectionIsCheckedEveryFiveMinutes()
    {
        machine.loggedIn(false, START);
        machine.accepted("41", START);

        assertFalse(machine.checkDue(START.plusSeconds(SyncMachine.CONNECTED_POLL_SECONDS)));
        assertFalse(machine.checkDue(START.plusSeconds(SyncMachine.LOGGED_OUT_POLL_SECONDS - 1)));
        assertTrue(machine.checkDue(START.plusSeconds(SyncMachine.LOGGED_OUT_POLL_SECONDS)));
    }

    @Test
    public void aLoginMakesACheckDueAtOnceButALoadingScreenDoesNot()
    {
        machine.loggedIn(false, START);
        machine.accepted("41", START);
        Instant login = START.plusSeconds(30);

        machine.loggedIn(true, login);
        assertTrue(machine.checkDue(login));

        machine.confirmed(login);
        machine.loggedIn(true, login.plusSeconds(10));
        assertFalse(machine.checkDue(login.plusSeconds(10)));
        assertTrue(machine.checkDue(login.plusSeconds(SyncMachine.CONNECTED_POLL_SECONDS)));
    }

    @Test
    public void aLoginStillWaitsForTheRelaysRetryAfter()
    {
        machine.loggedIn(false, START);
        machine.busy(START, 600);

        machine.loggedIn(true, START.plusSeconds(60));

        assertFalse(machine.checkDue(START.plusSeconds(599)));
        assertTrue(machine.checkDue(START.plusSeconds(600)));
    }

    @Test
    public void acceptedRulesAreConnectedAndCheckedEveryMinute()
    {
        machine.failed(START, SyncMachine.FAILURE_BACKOFF_SECONDS);

        TrackerConnectionSnapshot connected = machine.accepted("41", START);

        assertEquals(TrackerConnectionState.CONNECTED, connected.getState());
        assertEquals("41", machine.acceptedVersion());
        assertEquals(START, machine.lastSync());
        assertFalse(machine.checkDue(START.plusSeconds(SyncMachine.CONNECTED_POLL_SECONDS - 1)));
        assertTrue(machine.checkDue(START.plusSeconds(SyncMachine.CONNECTED_POLL_SECONDS)));
    }

    @Test
    public void aConfirmationRefreshesTheSyncTimeAndKeepsTheVersion()
    {
        machine.accepted("41", START);
        Instant later = START.plusSeconds(90);

        TrackerConnectionSnapshot connected = machine.confirmed(later);

        assertEquals(TrackerConnectionState.CONNECTED, connected.getState());
        assertEquals(later, connected.getLastSync());
        assertEquals("41", connected.getAcceptedVersion());
    }

    @Test
    public void aNewPairingWaitsForTheBrowserQuicklyThenSlowlyThenGivesUp()
    {
        machine.pairingStarted(START);

        TrackerConnectionSnapshot early = machine.notFound(false, START.plusSeconds(10));
        assertEquals(SyncReason.CONFIRM_IN_BROWSER.status, early.getMessage());
        assertTrue(machine.checkDue(START.plusSeconds(10 + SyncMachine.WAITING_POLL_SECONDS)));

        Instant slow = START.plusSeconds(SyncMachine.PAIRING_FAST_POLL_WINDOW_SECONDS);
        assertEquals(SyncReason.CONFIRM_IN_BROWSER.status, machine.notFound(false, slow).getMessage());
        assertFalse(machine.checkDue(slow.plusSeconds(SyncMachine.PAIRING_SLOW_POLL_SECONDS - 1)));

        TrackerConnectionSnapshot expired = machine.notFound(false,
            START.plusSeconds(SyncMachine.PAIRING_CONFIRM_SECONDS));
        assertEquals(TrackerConnectionState.EXPIRED, expired.getState());
        assertEquals(SyncReason.NO_PROFILE.status, expired.getMessage());
    }

    @Test
    public void aProfileThatLapsedSaysNoRecentUpdateAndForgetsItsVersion()
    {
        machine.accepted("41", START);

        TrackerConnectionSnapshot lapsed = machine.notFound(true, START.plusSeconds(60));

        assertEquals(TrackerConnectionState.WAITING, lapsed.getState());
        assertEquals(SyncReason.NO_RECENT_UPDATE.status, lapsed.getMessage());
        // The next check asks for whatever the relay has, with no validator.
        assertNull(machine.acceptedVersion());
        assertNull(machine.validator());
        assertEquals(START, lapsed.getLastSync());
    }

    @Test
    public void aLocalImportForgetsTheVersionSoTheNextCheckFetchesInFull()
    {
        assertFalse(machine.localRulesReplaced());
        machine.accepted("41", START);

        assertTrue(machine.localRulesReplaced());

        assertNull(machine.acceptedVersion());
        assertTrue(machine.checkDue(START));
    }

    @Test
    public void aSeedHoldsSavedRulesUntilThisSessionAcceptsAnything()
    {
        assertTrue(machine.seed("\"41\""));
        assertEquals("41", machine.acceptedVersion());

        machine.accepted("42", START);
        assertFalse(machine.seed("40"));
        assertEquals("42", machine.acceptedVersion());
    }

    @Test
    public void aRejectedVersionBecomesTheValidatorUntilNewerRulesArrive()
    {
        machine.accepted("41", START);
        assertEquals("41", machine.validator());

        TrackerConnectionSnapshot failed = machine.rejected("42", SyncReason.FUTURE_FORMAT, START);

        assertEquals(TrackerConnectionState.IMPORT_FAILED, failed.getState());
        assertEquals(SyncReason.FUTURE_FORMAT, failed.getReason());
        assertEquals("42", machine.validator());
        assertEquals("41", machine.acceptedVersion());
        // Still that version, still for the same reason.
        assertEquals(SyncReason.FUTURE_FORMAT,
            machine.stillRejected(START.plusSeconds(30)).getReason());
        assertEquals("42", machine.validator());

        machine.accepted("43", START.plusSeconds(90));
        assertNull(machine.rejectedVersion());
        assertEquals("43", machine.validator());
    }

    @Test
    public void aRejectedVersionGoesWithTheProfile()
    {
        machine.rejected("42", SyncReason.INVALID_RULES, START);

        machine.notFound(false, START.plusSeconds(30));

        assertNull(machine.rejectedVersion());
        assertNull(machine.validator());
    }

    @Test
    public void changingThePairingOrConsentStartsOver()
    {
        machine.accepted("41", START);
        assertEquals(TrackerConnectionState.WAITING, machine.pairingReplaced(true).getState());
        assertNull(machine.acceptedVersion());
        assertNull(machine.lastSync());

        machine.accepted("41", START);
        assertEquals(TrackerConnectionState.DISCONNECTED,
            machine.networkAccessChanged(false, true).getState());
        assertNull(machine.acceptedVersion());
        assertTrue(machine.checkDue(START));
    }
}
