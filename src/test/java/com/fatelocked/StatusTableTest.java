package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fatelocked.FateLockedPlugin.RulesSource;
import com.fatelocked.rules.Trust;
import com.fatelocked.sidebar.CardAction;
import com.fatelocked.sidebar.StatusCardModel;
import com.fatelocked.ui.Palette.Tone;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;
import org.junit.Test;

/**
 * Stage 3's done-when: a presenter test covers every row of the status table in the UX
 * report (U-ux.md "Proposed target UX"). Rows 9 and 11 are the Here card's
 * (HerePresenterGoldenTest); the status card must stay "up to date" through them.
 */
public class StatusTableTest
{
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final String RUN = "Iron Example";

    private static StatusFacts.StatusFactsBuilder facts()
    {
        return StatusFacts.builder()
            .connection(TrackerConnectionSnapshot.disconnected())
            .source(RulesSource.NONE)
            .trust(Trust.NO_RULES)
            .now(NOW)
            .zone(ZoneOffset.UTC);
    }

    private static StatusFacts.StatusFactsBuilder synced(Duration ago, boolean fresh)
    {
        return facts()
            .connection(TrackerConnectionSnapshot.connected(NOW.minus(ago), "41"))
            .source(RulesSource.RELAY)
            .arrival(RulesPrecedence.Arrival.RELAY)
            .arrivedAt(NOW.minus(Duration.ofHours(1)))
            .trust(Trust.TRUSTED)
            .bound(true)
            .boundAccount(RUN)
            .loggedInAs(RUN)
            .paired(true)
            .fresh(fresh);
    }

    @Test
    public void row1And2FirstRunAsksToConnectLoggedInOrOut()
    {
        for (String player : new String[]{null, RUN})
        {
            StatusCardModel card = StatusCardPresenter.present(facts().loggedInAs(player).build());
            assertCard(card, Tone.NEUTRAL, "Not connected", CardAction.CONNECT, CardAction.USE_BACKUP);
            assertEquals("Connect the tracker to load your run's rules.", card.getDetail());
        }
    }

    @Test
    public void row3WaitingForTheBrowserOffersToOpenThePageAgainOrCancel()
    {
        StatusCardModel card = StatusCardPresenter.present(facts().paired(true)
            .connection(TrackerConnectionSnapshot.of(TrackerConnectionState.WAITING, null, null,
                SyncReason.CONFIRM_IN_BROWSER, null)).build());
        assertCard(card, Tone.PENDING, "Waiting for confirmation", CardAction.OPEN_PAGE_AGAIN,
            CardAction.CANCEL_PAIRING);
        assertTrue(card.getDetail(), card.getDetail().contains("browser tab RuneLite opened"));
    }

    @Test
    public void row4ConnectedSaysHowLongAgoAndForWhom()
    {
        StatusCardModel card = StatusCardPresenter.present(synced(Duration.ofMinutes(2), true).build());
        assertCard(card, Tone.GOOD, "Rules up to date", null, null);
        assertEquals("Synced 2 min ago for Iron Example.", card.getDetail());
    }

    @Test
    public void row5StaleRulesSayStrictModeWaitsAndOfferACheck()
    {
        StatusCardModel card = StatusCardPresenter.present(synced(Duration.ofMinutes(32), false)
            .connection(TrackerConnectionSnapshot.of(TrackerConnectionState.OFFLINE, NOW.minus(Duration.ofMinutes(32)),
                "41", SyncReason.UNREACHABLE, NOW.plus(Duration.ofMinutes(3))))
            .build());
        assertCard(card, Tone.PENDING, "Rules may be out of date", CardAction.CHECK_NOW, null);
        assertEquals("Last synced 32 min ago. RuneLite couldn't reach the tracker; next check at 12:03."
            + " Strict Mode is inactive until the rules refresh.", card.getDetail());
    }

    @Test
    public void row5AFailedCheckWhileStillFreshStaysUpToDate()
    {
        StatusCardModel card = StatusCardPresenter.present(synced(Duration.ofMinutes(4), true)
            .connection(TrackerConnectionSnapshot.of(TrackerConnectionState.OFFLINE, NOW.minus(Duration.ofMinutes(4)),
                "41", SyncReason.BUSY, NOW.plus(Duration.ofMinutes(1))))
            .build());
        assertCard(card, Tone.GOOD, "Rules up to date", null, null);
        assertTrue(card.getDetail(), card.getDetail().endsWith("check less often; next check at 12:01."));
    }

    @Test
    public void row6ALapsedTrackerCopyAsksToOpenTheTracker()
    {
        StatusCardModel card = StatusCardPresenter.present(synced(Duration.ofHours(30), false)
            .connection(TrackerConnectionSnapshot.of(TrackerConnectionState.WAITING, NOW.minus(Duration.ofHours(30)),
                null, SyncReason.NO_RECENT_UPDATE, null))
            .build());
        assertCard(card, Tone.PENDING, "Tracker copy expired", CardAction.OPEN_TRACKER, null);
        assertTrue(card.getDetail(), card.getDetail().endsWith("Your current rules stay in use."));
    }

    @Test
    public void row7ABackupSaysWhereItCameFromAndWhenStrictModeUsesIt()
    {
        StatusCardModel card = StatusCardPresenter.present(facts()
            .source(RulesSource.IMPORT)
            .arrival(RulesPrecedence.Arrival.IMPORT)
            .exportedAt(NOW.minus(Duration.ofMinutes(2)))
            .trust(Trust.TRUSTED).bound(true).boundAccount(RUN).loggedInAs(RUN)
            .fresh(true)
            .build());
        assertCard(card, Tone.NEUTRAL, "Using a backup", null, CardAction.CONNECT);
        assertEquals("Rules from the clipboard, exported at 11:58. Strict Mode uses them for 15 minutes after"
            + " the tracker exported them.", card.getDetail());

        StatusCardModel old = StatusCardPresenter.present(facts()
            .source(RulesSource.FILE)
            .exportedAt(NOW.minus(Duration.ofDays(3)))
            .trust(Trust.TRUSTED)
            .paired(true)
            .connection(TrackerConnectionSnapshot.of(TrackerConnectionState.WAITING, null, null, SyncReason.CHECKING,
                null))
            .build());
        assertEquals("Rules from a backup file, exported at Mon 12:00. They're more than 15 minutes old, so"
            + " Strict Mode doesn't use them. The tracker's copy replaces them at its next check.",
            old.getDetail());
        assertNull("paired, so nothing to do", old.getSecondary());
    }

    @Test
    public void row8AnotherCharacterNamesBothAndSaysWhatIsOff()
    {
        StatusCardModel card = StatusCardPresenter.present(synced(Duration.ofMinutes(2), true)
            .trust(Trust.WRONG_CHARACTER).loggedInAs("Zezima").build());
        assertCard(card, Tone.BAD, "Different character", null, null);
        assertEquals("This run belongs to Iron Example, and you're logged in as Zezima. Warnings and Strict Mode"
            + " are off.", card.getDetail());
    }

    @Test
    public void rows9To11LeaveTheStatusCardUpToDate()
    {
        // A locked area, a paused Strict Mode and an instance are the Here card's and Strict Mode's to show.
        assertEquals("Rules up to date",
            StatusCardPresenter.present(synced(Duration.ofSeconds(20), true).build()).getTitle());
    }

    @Test
    public void savedRulesAtStartupWaitForTheTrackersFirstAnswer()
    {
        Instant saved = NOW.minus(Duration.ofHours(20));
        StatusCardModel card = StatusCardPresenter.present(synced(Duration.ofHours(21), false)
            .arrival(RulesPrecedence.Arrival.SAVED).arrivedAt(saved)
            .connection(TrackerConnectionSnapshot.of(TrackerConnectionState.WAITING, NOW.minus(Duration.ofHours(21)),
                null, SyncReason.CHECKING, null))
            .build());
        assertCard(card, Tone.NEUTRAL, "Checking with the tracker", null, null);
        assertEquals("Rules saved at Wed 16:00 are in use until the tracker answers.", card.getDetail());
    }

    @Test
    public void aNewPairingWithNoRulesYetIsCheckingNotDisconnected()
    {
        StatusCardModel card = StatusCardPresenter.present(facts().paired(true)
            .connection(TrackerConnectionSnapshot.of(TrackerConnectionState.WAITING, null, null, SyncReason.CHECKING,
                null)).build());
        assertCard(card, Tone.PENDING, "Checking with the tracker", null, null);
        assertEquals("RuneLite is asking the tracker for your run's rules.", card.getDetail());
    }

    @Test
    public void rulesNamingNoCharacterSayStrictModeStaysOff()
    {
        StatusCardModel card = StatusCardPresenter.present(synced(Duration.ofMinutes(1), true).bound(false).build());
        assertEquals("Synced 1 min ago. These rules name no character, so Strict Mode stays off.", card.getDetail());
    }

    @Test
    public void anOldBackupFromAnOlderTrackerSaysStrictModeIgnoresIt()
    {
        StatusCardModel card = StatusCardPresenter.present(facts().source(RulesSource.FILE).legacy(true)
            .trust(Trust.TRUSTED).fresh(true).exportedAt(NOW).build());
        assertTrue(card.getDetail(), card.getDetail().contains("older tracker, so Strict Mode doesn't use it"));
    }

    @Test
    public void syncOffOffersToTurnItBackOn()
    {
        TrackerConnectionSnapshot off = TrackerConnectionSnapshot.of(TrackerConnectionState.DISCONNECTED, null, null,
            SyncReason.SYNC_OFF, null);
        assertCard(StatusCardPresenter.present(facts().connection(off).build()), Tone.NEUTRAL, "Online sync is off",
            CardAction.TURN_ON_SYNC, CardAction.USE_BACKUP);
        StatusCardModel saved = StatusCardPresenter.present(synced(Duration.ofHours(2), false).paired(false)
            .connection(off).exportedAt(NOW.minus(Duration.ofHours(3))).build());
        assertCard(saved, Tone.NEUTRAL, "Using saved rules", CardAction.TURN_ON_SYNC, null);
    }

    @Test
    public void everySyncReasonHasACardWithADetailAndNoRawStatus()
    {
        Set<SyncReason> seen = EnumSet.noneOf(SyncReason.class);
        for (SyncReason reason : SyncReason.values())
        {
            for (TrackerConnectionState state : TrackerConnectionState.values())
            {
                for (RulesSource source : RulesSource.values())
                {
                    StatusCardModel card = StatusCardPresenter.present(synced(Duration.ofMinutes(20), false)
                        .source(source)
                        .connection(TrackerConnectionSnapshot.of(state, NOW.minus(Duration.ofMinutes(20)), "41",
                            reason, null))
                        .build());
                    assertNotNull(reason + " " + state + " " + source, card.getTitle());
                    assertNotNull(reason + " " + state + " " + source, card.getDetail());
                    assertTrue(card.getDetail(), !card.getDetail().contains("null"));
                    seen.add(reason);
                }
            }
        }
        assertEquals(EnumSet.allOf(SyncReason.class), seen);
    }

    @Test
    public void aFailedImportOffersACheck()
    {
        StatusCardModel card = StatusCardPresenter.present(synced(Duration.ofMinutes(5), true)
            .connection(TrackerConnectionSnapshot.of(TrackerConnectionState.IMPORT_FAILED, NOW, "41",
                SyncReason.NONE, null)).build());
        assertCard(card, Tone.BAD, "Couldn't apply the tracker's rules", CardAction.CHECK_NOW, null);
        StatusCardModel future = StatusCardPresenter.present(synced(Duration.ofMinutes(5), true)
            .connection(TrackerConnectionSnapshot.of(TrackerConnectionState.IMPORT_FAILED, NOW, "41",
                SyncReason.FUTURE_FORMAT, null)).build());
        assertCard(future, Tone.BAD, "Plugin update needed", null, null);
        assertTrue(future.getDetail().contains("Restart RuneLite"));
    }

    private static void assertCard(StatusCardModel card, Tone tone, String title, CardAction primary,
        CardAction secondary)
    {
        assertEquals(title, card.getTitle());
        assertEquals(tone, card.getTone());
        assertEquals(primary, card.getPrimary());
        assertEquals(secondary, card.getSecondary());
    }
}
