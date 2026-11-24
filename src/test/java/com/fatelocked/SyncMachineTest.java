package com.fatelocked;

import com.fatelocked.FateLockedPlugin.RulesSource;
import com.fatelocked.rules.Trust;
import com.fatelocked.sidebar.CardAction;
import com.fatelocked.sidebar.StatusCardModel;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneOffset;

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
    public void aRePairingKeepsTheSyncTimeAndGivesUpAfterTenMinutes()
    {
        machine.accepted("41", START);
        Instant repair = START.plusSeconds(60);

        TrackerConnectionSnapshot waiting = machine.repairStarted(repair);
        assertEquals(SyncReason.CONFIRM_REPAIR, waiting.getReason());
        assertEquals(START, waiting.getLastSync());
        assertNull(machine.acceptedVersion());
        assertFalse(machine.firstPairingWaiting());

        assertEquals(SyncReason.CONFIRM_REPAIR, machine.notFound(false, repair.plusSeconds(10)).getReason());
        assertTrue(machine.repairing());

        TrackerConnectionSnapshot kept = machine.notFound(false,
            repair.plusSeconds(SyncMachine.PAIRING_CONFIRM_SECONDS));
        assertEquals(SyncReason.REPAIR_ABANDONED, kept.getReason());
        assertEquals(START, kept.getLastSync());
        assertFalse(machine.repairing());
    }

    @Test
    public void aFirstPairingIsNotARePairing()
    {
        machine.pairingStarted(START);

        assertTrue(machine.firstPairingWaiting());
        assertEquals(SyncReason.NO_PROFILE, machine.notFound(false,
            START.plusSeconds(SyncMachine.PAIRING_CONFIRM_SECONDS)).getReason());
        assertTrue(machine.firstPairingWaiting());
    }

    @Test
    public void cancellingARePairingChecksTheWorkingPairingAtOnce()
    {
        machine.accepted("41", START);
        machine.repairStarted(START.plusSeconds(60));

        TrackerConnectionSnapshot back = machine.repairCancelled();

        assertEquals(SyncReason.CHECKING, back.getReason());
        assertFalse(machine.repairing());
        assertTrue(machine.checkDue(START.plusSeconds(61)));
    }

    @Test
    public void aRePairingThatDeliversIsOver()
    {
        machine.repairStarted(START);

        machine.accepted("42", START.plusSeconds(30));

        assertFalse(machine.repairing());
        assertFalse(machine.firstPairingWaiting());
    }

    @Test
    public void aCodeTheOwnerDisconnectedIsGoneUntilItDeliversAgain()
    {
        machine.accepted("41", START);
        Instant gone = START.plusSeconds(60);

        TrackerConnectionSnapshot shown = machine.gone(gone);

        assertEquals(TrackerConnectionState.EXPIRED, shown.getState());
        assertEquals(SyncReason.GONE, shown.getReason());
        assertEquals(START, shown.getLastSync());
        assertNull(machine.acceptedVersion());
        assertTrue(machine.pairingGone());
        assertFalse(machine.checkDue(gone.plusSeconds(SyncMachine.LOGGED_OUT_POLL_SECONDS - 1)));
        assertTrue(machine.checkDue(gone.plusSeconds(SyncMachine.LOGGED_OUT_POLL_SECONDS)));

        machine.accepted("42", gone.plusSeconds(600));
        assertFalse(machine.pairingGone());
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
    /** Every way a check can end without an answer the plugin can use, as the controller reports it. */
    private static final String[] FAILURES = {"UNREACHABLE", "UNREADABLE", "UNAVAILABLE", "BUSY", "BUSY_RETRY_AFTER"};

    /** The reason a failure carries, and the card's words for it, as a working pairing's card says them. */
    private static SyncReason reasonOf(String failure)
    {
        return failure.startsWith("BUSY") ? SyncReason.BUSY : SyncReason.valueOf(failure);
    }

    private static String wordsFor(String failure)
    {
        switch (reasonOf(failure))
        {
            case UNREACHABLE:
                return "RuneLite couldn't reach the tracker";
            case UNREADABLE:
                return "Something other than the tracker answered, such as a Wi-Fi sign-in page";
            case UNAVAILABLE:
                return "The tracker had a problem";
            default:
                return "The tracker asked RuneLite to check less often";
        }
    }

    private static TrackerConnectionSnapshot fail(SyncMachine machine, String failure, Instant now)
    {
        switch (failure)
        {
            case "BUSY":
                return machine.busy(now, 0);
            case "BUSY_RETRY_AFTER":
                return machine.busy(now, 30);
            default:
                return machine.failure(TrackerConnectionState.OFFLINE, SyncReason.valueOf(failure), now,
                    SyncMachine.FAILURE_BACKOFF_SECONDS);
        }
    }

    private static StatusCardModel card(TrackerConnectionSnapshot connection, Instant now)
    {
        return StatusCardPresenter.present(StatusFacts.builder()
            .connection(connection)
            .source(RulesSource.NONE)
            .trust(Trust.NO_RULES)
            .paired(true)
            .now(now)
            .zone(ZoneOffset.UTC)
            .build());
    }

    @Test
    public void aFailedCheckWhileAFirstPairingWaitsKeepsThePairingsControls()
    {
        for (String failure : FAILURES)
        {
            SyncMachine pairing = new SyncMachine();
            pairing.pairingStarted(START);
            Instant failedAt = START.plusSeconds(20);

            TrackerConnectionSnapshot failed = fail(pairing, failure, failedAt);

            assertEquals(failure, TrackerConnectionState.WAITING, failed.getState());
            assertEquals(failure, SyncReason.CONFIRM_IN_BROWSER, failed.getReason());
            assertEquals(failure, reasonOf(failure), failed.getFailure());
            // The back-off is a failure's, as before.
            assertEquals(failure, failedAt.plusSeconds(SyncMachine.FAILURE_BACKOFF_SECONDS), failed.getNextCheck());
            assertFalse(failure, pairing.checkDue(failedAt.plusSeconds(SyncMachine.FAILURE_BACKOFF_SECONDS - 1)));
            assertTrue(failure, pairing.firstPairingWaiting());
            SyncView view = SyncView.of(failed, failedAt, ZoneOffset.UTC);
            assertEquals(failure, "Confirm in browser", view.status);
            assertEquals(failure, SyncView.Connect.CONNECT, view.connect);
            assertEquals(failure, "Confirm the profile in the browser tab RuneLite opened. "
                + wordsFor(failure) + ". Next check at 12:00.", view.detail);
            StatusCardModel card = card(failed, failedAt);
            assertEquals(failure, "Waiting for confirmation", card.getTitle());
            assertEquals(failure, CardAction.OPEN_PAGE_AGAIN, card.getPrimary());
            assertEquals(failure, CardAction.CANCEL_PAIRING, card.getSecondary());
            // The card says why, as README.md promises for a failed check.
            assertEquals(failure, "Confirm this profile in the browser tab RuneLite opened. "
                + wordsFor(failure) + "; next check at 12:00.", card.getDetail());

            // A 404 brings the plain waiting state back, at the pairing's pace.
            Instant later = failed.getNextCheck();
            TrackerConnectionSnapshot waiting = pairing.notFound(false, later);
            assertEquals(failure, SyncReason.CONFIRM_IN_BROWSER, waiting.getReason());
            assertNull(failure, waiting.getNextCheck());
            assertNull(failure, waiting.getFailure());
            assertEquals(failure, "Confirm the profile in the browser tab RuneLite opened."
                + " RuneLite checks every few seconds.", SyncView.of(waiting, later, ZoneOffset.UTC).detail);
            assertEquals(failure, CardAction.CANCEL_PAIRING, card(waiting, later).getSecondary());
            assertFalse(failure, pairing.checkDue(later.plusSeconds(SyncMachine.WAITING_POLL_SECONDS - 1)));
            assertTrue(failure, pairing.checkDue(later.plusSeconds(SyncMachine.WAITING_POLL_SECONDS)));

            // Then a 200 connects.
            TrackerConnectionSnapshot connected = pairing.accepted("1", later.plusSeconds(5));
            assertEquals(failure, TrackerConnectionState.CONNECTED, connected.getState());
            assertFalse(failure, pairing.firstPairingWaiting());
            assertEquals(failure, SyncView.Connect.REPAIR, SyncView.of(connected, later, ZoneOffset.UTC).connect);
        }
    }

    @Test
    public void aFailedCheckWhileARePairingWaitsKeepsThePairingsControls()
    {
        for (String failure : FAILURES)
        {
            SyncMachine pairing = new SyncMachine();
            Instant synced = START.minusSeconds(3600);
            pairing.accepted("41", synced);
            pairing.repairStarted(START);
            Instant failedAt = START.plusSeconds(20);

            TrackerConnectionSnapshot failed = fail(pairing, failure, failedAt);

            assertEquals(failure, TrackerConnectionState.WAITING, failed.getState());
            assertEquals(failure, SyncReason.CONFIRM_REPAIR, failed.getReason());
            assertEquals(failure, reasonOf(failure), failed.getFailure());
            assertEquals(failure, synced, failed.getLastSync());
            assertEquals(failure, failedAt.plusSeconds(SyncMachine.FAILURE_BACKOFF_SECONDS), failed.getNextCheck());
            assertTrue(failure, pairing.repairing());
            SyncView view = SyncView.of(failed, failedAt, ZoneOffset.UTC);
            assertEquals(failure, SyncView.Connect.CANCEL_REPAIR, view.connect);
            assertEquals(failure, "Confirm the profile in the browser tab RuneLite opened. Until it arrives,"
                + " RuneLite keeps your current pairing. " + wordsFor(failure) + ". Next check at 12:00.",
                view.detail);
            StatusCardModel card = card(failed, failedAt);
            assertEquals(failure, "Waiting for confirmation", card.getTitle());
            assertEquals(failure, CardAction.OPEN_PAGE_AGAIN, card.getPrimary());
            assertEquals(failure, CardAction.CANCEL_REPAIR, card.getSecondary());
            assertEquals(failure, "Confirm the new profile in the browser tab RuneLite opened. Your current"
                + " pairing keeps working until it arrives. " + wordsFor(failure) + "; next check at 12:00.",
                card.getDetail());

            Instant later = failed.getNextCheck();
            TrackerConnectionSnapshot waiting = pairing.notFound(false, later);
            assertEquals(failure, SyncReason.CONFIRM_REPAIR, waiting.getReason());
            assertNull(failure, waiting.getNextCheck());
            assertNull(failure, waiting.getFailure());
            assertEquals(failure, "Confirm the profile in the browser tab RuneLite opened. Until it arrives,"
                + " RuneLite keeps your current pairing.", SyncView.of(waiting, later, ZoneOffset.UTC).detail);
            assertEquals(failure, SyncView.Connect.CANCEL_REPAIR, SyncView.of(waiting, later, ZoneOffset.UTC).connect);

            TrackerConnectionSnapshot connected = pairing.accepted("1", later.plusSeconds(5));
            assertEquals(failure, TrackerConnectionState.CONNECTED, connected.getState());
            assertFalse(failure, pairing.repairing());
        }
    }

    @Test
    public void aFailedCheckAfterAFirstPairingRanOutKeepsNoProfile()
    {
        machine.pairingStarted(START);
        machine.notFound(false, START.plusSeconds(SyncMachine.PAIRING_CONFIRM_SECONDS));

        TrackerConnectionSnapshot failed = machine.failure(TrackerConnectionState.OFFLINE, SyncReason.UNREACHABLE,
            START.plusSeconds(SyncMachine.PAIRING_CONFIRM_SECONDS + 60), SyncMachine.FAILURE_BACKOFF_SECONDS);

        assertEquals(SyncReason.NO_PROFILE, failed.getReason());
        assertEquals(SyncView.Connect.CONNECT, SyncView.of(failed, START, ZoneOffset.UTC).connect);
    }

    @Test
    public void aWorkingPairingStillShowsWhyACheckFailed()
    {
        machine.accepted("41", START);
        machine.pairingStarted(START);
        machine.accepted("42", START.plusSeconds(10));

        assertEquals(SyncReason.UNREACHABLE, machine.failure(TrackerConnectionState.OFFLINE,
            SyncReason.UNREACHABLE, START.plusSeconds(20), SyncMachine.FAILURE_BACKOFF_SECONDS).getReason());
        assertEquals(SyncReason.BUSY, machine.busy(START.plusSeconds(30), 60).getReason());
    }

    @Test
    public void anUnconfirmedFirstPairingFromAnEarlierStartCountsAsAFirstPairing()
    {
        TrackerConnectionSnapshot shown = machine.unconfirmedPairing();
        assertEquals(SyncReason.NOT_CONFIRMED, shown.getReason());
        assertTrue(machine.firstPairingWaiting());
        assertFalse(machine.repairing());

        TrackerConnectionSnapshot missing = machine.notFound(false, START);
        assertEquals(SyncReason.NOT_CONFIRMED, missing.getReason());
        SyncView view = SyncView.of(missing, START, ZoneOffset.UTC);
        assertEquals("Pairing not confirmed", view.status);
        assertEquals("This pairing hasn't sent any rules yet. Press Connect tracker to pair again.", view.detail);
        assertEquals(SyncView.Action.CONNECT, view.action);
        assertEquals(SyncView.Connect.CONNECT, view.connect);
        // Checked at a working pairing's pace: its start time is unknown.
        assertFalse(machine.checkDue(START.plusSeconds(SyncMachine.CONNECTED_POLL_SECONDS - 1)));
        assertTrue(machine.checkDue(START.plusSeconds(SyncMachine.CONNECTED_POLL_SECONDS)));

        TrackerConnectionSnapshot failed = machine.failure(TrackerConnectionState.OFFLINE,
            SyncReason.UNREACHABLE, START.plusSeconds(60), SyncMachine.FAILURE_BACKOFF_SECONDS);
        assertEquals(SyncReason.NOT_CONFIRMED, failed.getReason());
        assertEquals(START.plusSeconds(90), failed.getNextCheck());
        assertEquals(SyncView.Connect.CONNECT, SyncView.of(failed, START, ZoneOffset.UTC).connect);

        machine.accepted("1", START.plusSeconds(90));
        assertFalse(machine.firstPairingWaiting());
        assertEquals(SyncReason.NO_RECENT_UPDATE, machine.notFound(true, START.plusSeconds(200)).getReason());
    }
}
