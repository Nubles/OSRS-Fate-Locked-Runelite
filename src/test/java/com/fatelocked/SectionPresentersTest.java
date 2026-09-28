package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fatelocked.guardian.StrictModeStatusView;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.Progress;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.rules.Trust;
import com.fatelocked.sidebar.CardAction;
import com.fatelocked.sidebar.ConnectionModel;
import com.fatelocked.sidebar.RunModel;
import com.fatelocked.sidebar.StrictModeModel;
import com.fatelocked.sidebar.StrictModeSectionPresenter;
import com.fatelocked.ui.Palette.Tone;
import com.google.gson.Gson;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** The Strict Mode, Run and Connection sections (C4, C5, C6). */
public class SectionPresentersTest
{
    private static final Gson GSON = new Gson();
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    public void strictModeOffersPauseOnlyWhileItCanAct()
    {
        StrictModeModel active = StrictModeSectionPresenter.present(
            StrictModeStatusView.of(true, false, 0, null), Collections.singletonList("Varrock Teleport, 11:40"));
        assertTrue(active.isOn());
        assertEquals("Active", active.getWord());
        assertEquals(Tone.GOOD, active.getTone());
        assertEquals(CardAction.PAUSE_STRICT_MODE, active.getAction());
        assertEquals(Collections.singletonList("Varrock Teleport, 11:40"), active.getRecent());

        StrictModeModel paused = StrictModeSectionPresenter.present(StrictModeStatusView.of(true, true, 42, null), null);
        assertEquals("Paused · 42s", paused.getWord());
        assertEquals(CardAction.RESUME_STRICT_MODE, paused.getAction());
        assertEquals(Collections.emptyList(), paused.getRecent());

        StrictModeModel inactive = StrictModeSectionPresenter.present(
            StrictModeStatusView.of(true, false, 0, "the rules are more than 15 minutes old"), null);
        assertEquals("Inactive", inactive.getWord());
        assertEquals(Tone.PENDING, inactive.getTone());
        assertNull("nothing to pause", inactive.getAction());
        assertEquals("Not blocking anything: the rules are more than 15 minutes old.", inactive.getDetail());

        StrictModeModel off = StrictModeSectionPresenter.present(StrictModeStatusView.of(false, false, 0, null),
            Collections.singletonList("Varrock Teleport, 11:40"));
        assertFalse(off.isOn());
        assertTrue("an off switch hides what it stopped", off.getRecent().isEmpty());
    }

    @Test
    public void theRunShowsWhoseItIsItsIdByFourCharactersAndProgressWithItsUnit() throws Exception
    {
        FateLockedBundle mid = golden("vanilla-mid");
        RunModel run = RunPresenter.present(mid, decisions(mid, "iron example"), "Iron Example");
        assertTrue(run.getCharacter(), run.getCharacter().endsWith("(you)"));
        assertTrue(run.getRunId(), run.getRunId().startsWith("…"));
        assertEquals(5, run.getRunId().length());
        assertEquals("15 of 187 areas unlocked", run.getProgress());
        assertTrue(run.getFraction() > 0 && run.getFraction() < 1);

        RunModel other = RunPresenter.present(mid, decisions(mid, "zezima"), "Zezima");
        assertTrue(other.getCharacter(), other.getCharacter().endsWith(", and you're on Zezima"));
        assertNull("another character's progress isn't shown", other.getProgress());
    }

    @Test
    public void aRunWithNothingToCountShowsNoProgress()
    {
        DecisionService decisions = org.mockito.Mockito.mock(DecisionService.class);
        org.mockito.Mockito.when(decisions.trust()).thenReturn(Trust.TRUSTED);
        org.mockito.Mockito.when(decisions.progress()).thenReturn(new Progress(Progress.AREAS, 0, 0, 0, 0));
        RunModel run = RunPresenter.present(FateLockedBundle.empty(), decisions, "Iron Example");
        assertNull(run.getProgress());
        assertEquals(0.0, run.getFraction(), 0);
        assertEquals(0, run.getKeys());
        assertNull(run.getRitual());
    }

    @Test
    public void theCharacterLineNamesBothOnlyWhenTheyDiffer()
    {
        assertEquals("Nubles (you)", RunPresenter.character("Nubles", "Nubles", Trust.TRUSTED));
        assertEquals("Nubles, and you're on Zezima", RunPresenter.character("Nubles", "Zezima", Trust.WRONG_CHARACTER));
        assertEquals("Nubles", RunPresenter.character("Nubles", null, Trust.LOGGED_OUT));
        assertEquals("No character named", RunPresenter.character(null, "Zezima", Trust.TRUSTED));
    }

    @Test
    public void connectionOffersTheRightPairingButtonAndSaysWhereTheRulesCameFrom()
    {
        TrackerConnectionSnapshot connected = TrackerConnectionSnapshot.connected(NOW.minusSeconds(120), "41")
            .forPairing("0123456789abcdef0123456789abcdef");
        ConnectionModel online = ConnectionPresenter.present(connected, true, true, FateLockedPlugin.RulesSource.RELAY,
            null, NOW, ZoneOffset.UTC);
        assertTrue(online.isOnlineSync());
        assertEquals("…cdef", online.getPairing());
        assertEquals(CardAction.REPAIR, online.getPairingAction());
        assertTrue(online.isCanCheckNow());
        assertTrue(online.isCanDisconnect());
        assertEquals("Online", online.getSummary());
        assertEquals("Rules from the tracker, last synced at 11:58.", online.getSource());

        ConnectionModel off = ConnectionPresenter.present(TrackerConnectionSnapshot.disconnected(), false, false,
            FateLockedPlugin.RulesSource.IMPORT, NOW.minusSeconds(3600), NOW, ZoneOffset.UTC);
        assertEquals(CardAction.CONNECT, off.getPairingAction());
        assertFalse(off.isCanCheckNow());
        assertFalse(off.isCanDisconnect());
        assertEquals("Off", off.getSummary());
        assertEquals("Rules from the clipboard, exported at 11:00.", off.getSource());
        assertNull(off.getPairing());

        ConnectionModel syncOff = ConnectionPresenter.present(TrackerConnectionSnapshot.of(
                TrackerConnectionState.DISCONNECTED, null, null, SyncReason.SYNC_OFF, null), false, true,
            FateLockedPlugin.RulesSource.NONE, null, NOW, ZoneOffset.UTC);
        assertEquals(CardAction.TURN_ON_SYNC, syncOff.getPairingAction());
        assertNull(syncOff.getSource());
        for (String summary : Arrays.asList(online.getSummary(), off.getSummary(), syncOff.getSummary()))
        {
            assertTrue(summary, summary.length() <= 12);
        }
    }

    private static FateLockedBundle golden(String scenario) throws Exception
    {
        return FateLockedBundle.loadFromJson(GSON,
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(scenario + ".bundle.json.gz")));
    }

    private static DecisionService decisions(FateLockedBundle bundle, String player)
    {
        return DecisionService.create(RulesSnapshot.of(bundle), "iron example", player);
    }
}
