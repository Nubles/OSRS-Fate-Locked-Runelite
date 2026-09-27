package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;
import com.fatelocked.MenuFacts;
import com.fatelocked.guardian.StrictModeClickHandler;
import com.fatelocked.guardian.StrictModeGuard;
import com.fatelocked.guardian.StrictModeReadiness;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import net.runelite.api.events.MenuOptionClicked;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.fatelocked.guardian.travel.TravelFixtures.AMULET_OF_GLORY_4;
import static com.fatelocked.guardian.travel.TravelFixtures.ANCIENT;
import static com.fatelocked.guardian.travel.TravelFixtures.DIGSITE_PENDANT_5;
import static com.fatelocked.guardian.travel.TravelFixtures.FAIRY_RING;
import static com.fatelocked.guardian.travel.TravelFixtures.FALADOR;
import static com.fatelocked.guardian.travel.TravelFixtures.FALADOR_TABLET;
import static com.fatelocked.guardian.travel.TravelFixtures.PORT_SARIM_CREW;
import static com.fatelocked.guardian.travel.TravelFixtures.cast;
import static com.fatelocked.guardian.travel.TravelFixtures.item;
import static com.fatelocked.guardian.travel.TravelFixtures.nubles;
import static com.fatelocked.guardian.travel.TravelFixtures.npc;
import static com.fatelocked.guardian.travel.TravelFixtures.object;
import static com.fatelocked.guardian.travel.TravelFixtures.other;
import static com.fatelocked.guardian.travel.TravelFixtures.spell;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The recognised-travel flow (F3): the tracker's table says what a click is
 * by id and what it decides for the option. A click is consumed only for a
 * trip with one destination, not advisory, that fresh rules bound to the
 * character playing lock; it is explained before it is consumed.
 */
public class TravelGuardianCoordinatorTest
{
    private static final CanonicalChunk ORIGIN = new CanonicalChunk(50, 51);

    private final TravelAvailability availability = mock(TravelAvailability.class);
    private final TravelBlockNoticeStore noticeStore = new TravelBlockNoticeStore(
        Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC));
    private final TravelAlternativeFinder finder = mock(TravelAlternativeFinder.class);
    private final StrictModeClickHandler clickHandler =
        spy(new StrictModeClickHandler(new StrictModeGuard()));
    private final TravelGuardianCoordinator coordinator = new TravelGuardianCoordinator(
        new IntentClassifier(),
        new TravelRuleEvaluator(),
        finder,
        noticeStore,
        clickHandler);
    private final DecisionService rules = nubles();

    /** A7: the click handler is called for a proven block and for nothing else. */
    @Test
    public void onlyAProvenBlockReachesTheClickHandler()
    {
        handle(cast("Falador Teleport"), context(true, true, true, true));
        handle(cast("Lumbridge Teleport"), context(true, false, true, true));
        handle(cast("Camelot Teleport"), context(true, false, true, true));
        handle(cast("Falador Teleport"), context(false, false, true, true));
        handle(cast("Falador Teleport"), context(true, false, true, false));
        handle(cast("Falador Teleport"), context(true, false, false, true));
        handle(other("Continue", ""), context(true, false, true, true));

        verify(clickHandler, never()).handleTravel(any(), any(), any(), any());

        MenuOptionClicked proven = handle(cast("Falador Teleport"), context(true, false, true, true));
        verify(clickHandler, times(1)).handleTravel(any(), any(), any(), any());
        verify(proven).consume();
    }

    @Test
    public void provenLockedTravelIsConsumedEachTimeButExplainedAndRecordedOnce()
    {
        MenuOptionClicked click = mock(MenuOptionClicked.class);

        TravelGuardianResult first = coordinator.handle(click, cast("Falador Teleport"), ORIGIN, active(), rules, availability);
        TravelGuardianResult repeated = coordinator.handle(click, cast("Falador Teleport"), ORIGIN, active(), rules, availability);

        verify(click, times(2)).consume();
        assertEquals("Strict Mode blocked Falador Teleport", noticeStore.current().get().getHeadline());
        assertEquals("the tracker's reason", "Unlock Falador", noticeStore.current().get().getReason());
        assertEquals("spell:standard:falador-teleport", first.getAction().getMethodId());
        assertEquals("Cast", first.getAction().getOption());
        assertEquals(FALADOR, first.getAction().getDestination());
        assertTrue(first.isWriteChat());
        assertFalse(repeated.isWriteChat());
        assertTrue(first.isWriteBlockedAudit());
        assertFalse(repeated.isWriteBlockedAudit());
        assertFalse(first.isWritePausedAudit());
    }

    /** Two locked options of one item are two trips: each is explained and recorded once. */
    @Test
    public void eachLockedOptionIsExplainedOnItsOwn()
    {
        TravelGuardianResult alKharid = coordinator.handle(mock(MenuOptionClicked.class),
            item(AMULET_OF_GLORY_4, "Al Kharid", "Amulet of glory(4)"), ORIGIN, active(), rules, availability);
        TravelGuardianResult karamja = coordinator.handle(mock(MenuOptionClicked.class),
            item(AMULET_OF_GLORY_4, "Karamja", "Amulet of glory(4)"), ORIGIN, active(), rules, availability);

        assertTrue(alKharid.isWriteChat());
        assertTrue(karamja.isWriteChat());
        assertTrue(karamja.isWriteBlockedAudit());
        assertEquals("Unlock Karamja", karamja.getNotice().getReason());
    }

    /** G6: each method's own unlock and decision, as the tracker has them: tablets, spellbooks, jewellery. */
    @Test
    public void eachMethodIsBlockedByTheTrackersOwnDecision()
    {
        assertBlocked(item(FALADOR_TABLET, "Break", "Falador teleport"), "Unlock Falador");
        assertBlocked(spell(ANCIENT, "Cast", "Senntisten Teleport"), "Needs Ancient Magicks");
        assertBlocked(item(AMULET_OF_GLORY_4, "Al Kharid", "Amulet of glory(4)"), "Unlock Al Kharid");
        assertBlocked(item(DIGSITE_PENDANT_5, "Digsite", "Digsite pendant (5)"), "Needs Digsite Pendant");
        assertNotBlocked(item(AMULET_OF_GLORY_4, "Edgeville", "Amulet of glory(4)"), PermissionStatus.ALLOWED);
    }

    /** G7: an option that goes to one of several places is never blocked, even when the tracker locks it. */
    @Test
    public void severalPlacesAreNeverBlocked()
    {
        TravelGuardianResult rub = assertNotBlocked(
            item(DIGSITE_PENDANT_5, "Rub", "Digsite pendant (5)"), PermissionStatus.UNKNOWN);
        assertEquals(TravelAction.Confidence.EXACT, rub.getAction().getConfidence());
        assertEquals(3, rub.getAction().getDestinations().size());
        assertNull(rub.getAction().getDestination());
        assertNotBlocked(cast("Varrock Teleport"), PermissionStatus.UNKNOWN);
        assertNotBlocked(item(AMULET_OF_GLORY_4, "Rub", "Amulet of glory(4)"), PermissionStatus.UNKNOWN);
    }

    /** Owner decision 2: networks and boats are tagged, never blocked, in Stage 2. */
    @Test
    public void advisoryTravelIsNeverBlocked()
    {
        TravelGuardianResult zanaris = assertNotBlocked(object(FAIRY_RING, "Zanaris", "Fairy ring"), PermissionStatus.LOCKED);
        assertTrue(zanaris.getAction().isAdvisory());
        assertNotBlocked(object(FAIRY_RING, "Last-destination (CKS)", "Fairy ring"), PermissionStatus.LOCKED);
        assertNotBlocked(npc(PORT_SARIM_CREW, "The Pandemonium", "Trader Crewmember"), PermissionStatus.LOCKED);
    }

    /** B15: the words are worked out first; if that fails, the click goes through. */
    @Test
    public void aPresenterFailureMeansNoConsume()
    {
        EnforcementPresenter broken = new EnforcementPresenter()
        {
            @Override
            public BlockNotice present(TravelAction action, TravelDecision decision, TravelAlternative alternative)
            {
                throw new IllegalStateException("no words");
            }
        };
        TravelGuardianCoordinator failing = new TravelGuardianCoordinator(
            new IntentClassifier(), new TravelRuleEvaluator(), finder, noticeStore, clickHandler, broken);
        MenuOptionClicked click = mock(MenuOptionClicked.class);

        try
        {
            failing.handle(click, cast("Falador Teleport"), ORIGIN, active(), rules, availability);
            org.junit.Assert.fail("the presenter's failure reaches the shell, which lets the click through");
        }
        catch (IllegalStateException expected)
        {
            // The shell's catch turns this into FAIL_OPEN.
        }
        verify(click, never()).consume();
        assertFalse(noticeStore.current().isPresent());
    }

    @Test
    public void theNoticeIsUpBeforeTheClickIsConsumed()
    {
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        boolean[] noticeWasUp = { false };
        org.mockito.Mockito.doAnswer(call -> {
            noticeWasUp[0] = noticeStore.current().isPresent();
            return null;
        }).when(click).consume();

        TravelGuardianResult result = coordinator.handle(click, cast("Falador Teleport"), ORIGIN, active(), rules, availability);

        verify(click).consume();
        assertTrue(noticeWasUp[0]);
        assertEquals("Strict Mode blocked Falador Teleport", result.getNotice().getHeadline());
        assertTrue(result.getNotice().getChatLine().contains("pause Strict Mode for 60 seconds"));
    }

    @Test
    public void pausedTravelIsAllowedAndRecordedOnceForLocalAudit()
    {
        MenuOptionClicked click = mock(MenuOptionClicked.class);

        TravelGuardianResult result = coordinator.handle(
            click, cast("Falador Teleport"), ORIGIN, context(true, true, true, true), rules, availability);
        TravelGuardianResult repeated = coordinator.handle(
            click, cast("Falador Teleport"), ORIGIN, context(true, true, true, true), rules, availability);

        verify(click, never()).consume();
        assertFalse(noticeStore.current().isPresent());
        assertFalse(result.isWriteChat());
        assertFalse(result.isWriteBlockedAudit());
        assertTrue(result.isWritePausedAudit());
        assertFalse(repeated.isWritePausedAudit());
    }

    @Test
    public void pausedTravelTheRulesAllowIsNotRecorded()
    {
        MenuOptionClicked click = mock(MenuOptionClicked.class);

        TravelGuardianResult result = coordinator.handle(
            click, cast("Lumbridge Teleport"), ORIGIN, context(true, true, true, true), rules, availability);

        verify(click, never()).consume();
        assertFalse(result.isWriteChat());
        assertFalse(result.isWriteBlockedAudit());
        assertFalse(result.isWritePausedAudit());
    }

    @Test
    public void strictModeOffLeavesTravelUnconsumedAndUnrecorded()
    {
        assertFailOpen(cast("Falador Teleport"), context(false, false, true, true), rules);
    }

    @Test
    public void staleRulesLeaveTravelUnconsumedAndUnrecorded()
    {
        assertFailOpen(cast("Falador Teleport"), context(true, false, true, false), rules);
    }

    @Test
    public void wrongAccountLeavesTravelUnconsumedAndUnrecorded()
    {
        assertFailOpen(cast("Falador Teleport"), context(true, false, false, true), rules);
        // Even with the gate forced open, another character's rules decide nothing.
        TravelGuardianResult other = assertFailOpen(cast("Falador Teleport"),
            new StrictModeReadiness(StrictModeReadiness.State.ACTIVE, null), TravelFixtures.playing("zezima"));
        assertEquals(PermissionStatus.UNKNOWN, other.getDecision().getStatus());
    }

    @Test
    public void legacyRulesLeaveTravelUnconsumedAndUnrecorded() throws Exception
    {
        // An older export, trusted for its own character: it has no travel table.
        DecisionService legacy = DecisionService.create(
            RulesSnapshot.of(fixture("bundles/v3-standard.json")), "nubles", "nubles");

        TravelGuardianResult result = assertFailOpen(cast("Falador Teleport"), active(), legacy);
        assertEquals(TravelAction.Confidence.UNKNOWN, result.getAction().getConfidence());
    }

    @Test
    public void unknownTravelLeavesTheClickAlone()
    {
        TravelGuardianResult result = assertFailOpen(other("Continue", ""), active(), rules);

        assertEquals(TravelAction.Confidence.UNKNOWN, result.getAction().getConfidence());
        assertNull(result.getAction().getMethodId());
    }

    /** G7: menu text alone is never travel; only a click the table matches by id is. */
    @Test
    public void travelWordsWithoutAnIdStayFailOpen()
    {
        for (String option : new String[] {"Travel", "Enter", "Teleport", "Cast", "Break"})
        {
            TravelGuardianResult result = assertFailOpen(other(option, "Falador Teleport"), active(), rules);
            assertEquals(option, TravelAction.Confidence.UNKNOWN, result.getAction().getConfidence());
            assertNull(option, result.getAction().getDestination());
        }
    }

    @Test
    public void optionsTheTableDoesNotListStayUnknownAndUnconsumed()
    {
        for (String option : new String[] {"Drop", "Examine", "Destroy", "Check", "Configure", "Cancel"})
        {
            TravelGuardianResult result = assertFailOpen(item(FALADOR_TABLET, option, "Falador teleport"), active(), rules);
            assertEquals(option, TravelAction.Confidence.UNKNOWN, result.getAction().getConfidence());
        }
    }

    @Test
    public void aNotReadyDestinationLeavesTravelUnconsumedAndUnrecorded()
    {
        TravelGuardianResult result = assertFailOpen(cast("Camelot Teleport"), active(), rules);

        assertEquals(PermissionStatus.NOT_READY, result.getDecision().getStatus());
    }

    @Test
    public void walkingDoorsAndLaddersAreNeverConsumedOrRecorded()
    {
        MenuFacts walk = other("Walk here", "");
        MenuFacts ladder = MenuFacts.builder().kind(MenuFacts.Kind.OBJECT).objectId(16683)
            .option("Climb-down").target("Ladder").build();
        for (MenuFacts click : new MenuFacts[] {walk, ladder})
        {
            TravelGuardianResult result = assertFailOpen(click, active(), rules);
            assertEquals(TravelAction.Confidence.UNKNOWN, result.getAction().getConfidence());
            assertNull(result.getAction().getDestination());
        }
    }

    @Test
    public void alternativeLookupFailureNeverCancelsAProvenBlock()
    {
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        when(finder.find(any(), any(), any())).thenThrow(new IllegalStateException("inventory unavailable"));

        TravelGuardianResult result = coordinator.handle(click, cast("Falador Teleport"), ORIGIN, active(), rules, availability);

        verify(click).consume();
        assertTrue(result.isWriteBlockedAudit());
        assertNull(result.getAlternative());
        assertNull(noticeStore.current().get().getAlternative());
    }

    private MenuOptionClicked handle(MenuFacts facts, StrictModeReadiness readiness)
    {
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        coordinator.handle(click, facts, ORIGIN, readiness, rules, availability);
        return click;
    }

    private void assertBlocked(MenuFacts facts, String reason)
    {
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        TravelGuardianResult result = coordinator.handle(click, facts, ORIGIN, active(), rules, availability);
        verify(click).consume();
        assertEquals(facts.toString(), PermissionStatus.LOCKED, result.getDecision().getStatus());
        assertEquals(facts.toString(), reason, result.getNotice().getReason());
    }

    private TravelGuardianResult assertNotBlocked(MenuFacts facts, PermissionStatus status)
    {
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        TravelGuardianResult result = coordinator.handle(click, facts, ORIGIN, active(), rules, availability);
        verify(click, never()).consume();
        assertEquals(facts.toString(), TravelAction.Confidence.EXACT, result.getAction().getConfidence());
        assertEquals(facts.toString(), status, result.getDecision().getStatus());
        assertNull(result.getNotice());
        return result;
    }

    private TravelGuardianResult assertFailOpen(MenuFacts facts, StrictModeReadiness readiness, DecisionService decisions)
    {
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        TravelGuardianResult result = coordinator.handle(click, facts, ORIGIN, readiness, decisions, availability);
        verify(click, never()).consume();
        assertFalse(noticeStore.current().isPresent());
        assertFalse(result.isWriteChat());
        assertFalse(result.isWriteBlockedAudit());
        assertFalse(result.isWritePausedAudit());
        return result;
    }

    private static StrictModeReadiness active()
    {
        return context(true, false, true, true);
    }

    /** The readiness the plugin would show for these facts, on rules bound to Nubles. */
    private static StrictModeReadiness context(boolean enabled, boolean paused, boolean accountMatches, boolean freshRules)
    {
        return StrictModeReadiness.evaluate(
            enabled, paused, true, "Nubles", accountMatches ? "Nubles" : "Zezima", accountMatches, freshRules);
    }

    private FateLockedBundle fixture(String name) throws Exception
    {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name))
        {
            return FateLockedBundle.loadFromJson(new Gson(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
