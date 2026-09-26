package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;
import com.fatelocked.guardian.StrictModeClickHandler;
import com.fatelocked.guardian.StrictModeGuard;
import com.fatelocked.guardian.StrictModeReadiness;
import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TravelGuardianCoordinatorTest
{
    private static final CanonicalChunk ORIGIN = new CanonicalChunk(50, 51);
    /** Where "Cast" on "Falador Teleport" lands: the exact-travel example below. */
    private static final CanonicalChunk DESTINATION = new CanonicalChunk(46, 52);

    private final Client client = mock(Client.class);
    private final TravelAvailability availability = mock(TravelAvailability.class);
    private final TravelBlockNoticeStore noticeStore = new TravelBlockNoticeStore(
        Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC));
    private final TravelAlternativeFinder finder = mock(TravelAlternativeFinder.class);
    private final StrictModeClickHandler clickHandler =
        spy(new StrictModeClickHandler(new StrictModeGuard()));
    private final TravelGuardianCoordinator coordinator = new TravelGuardianCoordinator(
        new TravelActionResolver(),
        new TravelRuleEvaluator(),
        finder,
        noticeStore,
        clickHandler);

    /** A7: the click handler is called for a proven block and for nothing else. */
    @Test
    public void onlyAProvenBlockReachesTheClickHandler()
    {
        DecisionService locked = rules(PermissionStatus.LOCKED);
        DecisionService allowed = rules(PermissionStatus.ALLOWED);
        DecisionService notReady = rules(PermissionStatus.NOT_READY);
        handleTravel(context(true, true, true, true, locked), locked);
        handleTravel(context(true, false, true, true, allowed), allowed);
        handleTravel(context(true, false, true, true, notReady), notReady);
        handleTravel(context(false, false, true, true, locked), locked);
        handleTravel(context(true, false, true, false, locked), locked);
        handleTravel(context(true, false, false, true, locked), locked);
        MenuOptionClicked unknown = namedTeleportClick("Continue", "");
        coordinator.handle(unknown, unknown.getMenuEntry(), client, ORIGIN,
            context(true, false, true, true, locked), locked, availability);

        verify(clickHandler, never()).handleTravel(any(), any(), any(), any());

        MenuOptionClicked proven = handleTravel(context(true, false, true, true, locked), locked);
        verify(clickHandler, times(1)).handleTravel(any(), any(), any(), any());
        verify(proven).consume();
    }

    private MenuOptionClicked handleTravel(StrictModeReadiness readiness, DecisionService rules)
    {
        MenuOptionClicked click = travelClick();
        coordinator.handle(click, click.getMenuEntry(), client, ORIGIN, readiness, rules, availability);
        return click;
    }

    @Test
    public void provenLockedTravelIsConsumedEachTimeButExplainedAndRecordedOnce()
    {
        MenuOptionClicked click = travelClick();
        DecisionService rules = rules(PermissionStatus.LOCKED);

        TravelGuardianResult first = coordinator.handle(
            click, click.getMenuEntry(), client, ORIGIN,
            context(true, false, true, true, rules), rules, availability);
        TravelGuardianResult repeated = coordinator.handle(
            click, click.getMenuEntry(), client, ORIGIN,
            context(true, false, true, true, rules), rules, availability);

        verify(click, times(2)).consume();
        assertEquals("Strict Mode blocked Falador Teleport",
            noticeStore.current().get().getHeadline());
        assertTrue(first.isWriteChat());
        assertFalse(repeated.isWriteChat());
        assertTrue(first.isWriteBlockedAudit());
        assertFalse(repeated.isWriteBlockedAudit());
        assertFalse(first.isWritePausedAudit());
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
            new TravelActionResolver(), new TravelRuleEvaluator(), finder, noticeStore, clickHandler, broken);
        MenuOptionClicked click = travelClick();
        DecisionService rules = rules(PermissionStatus.LOCKED);

        try
        {
            failing.handle(click, click.getMenuEntry(), client, ORIGIN,
                context(true, false, true, true, rules), rules, availability);
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
        MenuOptionClicked click = travelClick();
        boolean[] noticeWasUp = { false };
        org.mockito.Mockito.doAnswer(call -> {
            noticeWasUp[0] = noticeStore.current().isPresent();
            return null;
        }).when(click).consume();
        DecisionService rules = rules(PermissionStatus.LOCKED);

        TravelGuardianResult result = coordinator.handle(click, click.getMenuEntry(), client, ORIGIN,
            context(true, false, true, true, rules), rules, availability);

        verify(click).consume();
        assertTrue(noticeWasUp[0]);
        assertEquals("Strict Mode blocked Falador Teleport", result.getNotice().getHeadline());
        assertTrue(result.getNotice().getChatLine().contains("pause Strict Mode for 60 seconds"));
    }

    @Test
    public void pausedTravelIsAllowedAndRecordedOnceForLocalAudit()
    {
        MenuOptionClicked click = travelClick();
        DecisionService rules = rules(PermissionStatus.LOCKED);

        TravelGuardianResult result = coordinator.handle(
            click, click.getMenuEntry(), client, ORIGIN,
            context(true, true, true, true, rules), rules, availability);
        TravelGuardianResult repeated = coordinator.handle(
            click, click.getMenuEntry(), client, ORIGIN,
            context(true, true, true, true, rules), rules, availability);

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
        MenuOptionClicked click = travelClick();
        DecisionService rules = rules(PermissionStatus.ALLOWED);

        TravelGuardianResult result = coordinator.handle(
            click, click.getMenuEntry(), client, ORIGIN,
            context(true, true, true, true, rules), rules, availability);

        verify(click, never()).consume();
        assertFalse(result.isWriteChat());
        assertFalse(result.isWriteBlockedAudit());
        assertFalse(result.isWritePausedAudit());
    }

    @Test
    public void strictModeOffLeavesTravelUnconsumedAndUnrecorded()
    {
        MenuOptionClicked click = travelClick();
        DecisionService rules = rules(PermissionStatus.LOCKED);

        TravelGuardianResult result = coordinator.handle(
            click, click.getMenuEntry(), client, ORIGIN,
            context(false, false, true, true, rules), rules, availability);

        assertFailOpen(click, result);
    }

    @Test
    public void staleRulesLeaveTravelUnconsumedAndUnrecorded()
    {
        MenuOptionClicked click = travelClick();
        DecisionService rules = rules(PermissionStatus.LOCKED);

        TravelGuardianResult result = coordinator.handle(
            click, click.getMenuEntry(), client, ORIGIN,
            context(true, false, true, false, rules), rules, availability);

        assertFailOpen(click, result);
    }

    @Test
    public void wrongAccountLeavesTravelUnconsumedAndUnrecorded()
    {
        MenuOptionClicked click = travelClick();
        DecisionService rules = rules(PermissionStatus.LOCKED);

        TravelGuardianResult result = coordinator.handle(
            click, click.getMenuEntry(), client, ORIGIN,
            context(true, false, false, true, rules), rules, availability);

        assertFailOpen(click, result);
    }

    @Test
    public void legacyRulesLeaveTravelUnconsumedAndUnrecorded() throws Exception
    {
        MenuOptionClicked click = namedTeleportClick("Teleport", "Falador");
        // An older export, trusted for its own character: its areas still never block.
        DecisionService legacy = DecisionService.create(
            RulesSnapshot.of(fixture("bundles/v3-standard.json")), "nubles", "nubles");

        TravelGuardianResult result = coordinator.handle(
            click, click.getMenuEntry(), client, ORIGIN,
            context(true, false, true, true, legacy), legacy, availability);

        assertFailOpen(click, result);
    }

    @Test
    public void unknownTravelLeavesTheClickForTheGenericPath()
    {
        MenuOptionClicked click = namedTeleportClick("Continue", "");
        DecisionService rules = rules(PermissionStatus.LOCKED);

        TravelGuardianResult result = coordinator.handle(
            click, click.getMenuEntry(), client, ORIGIN,
            context(true, false, true, true, rules), rules, availability);

        assertFailOpen(click, result);
        assertEquals(TravelAction.Confidence.UNKNOWN,
            result.getAction().getConfidence());
    }

    @Test
    public void genericTravelWordsWithoutADestinationStayFailOpen()
    {
        DecisionService rules = rules(PermissionStatus.LOCKED);

        assertGenericTravelWordFailsOpen("Travel", rules);
        assertGenericTravelWordFailsOpen("Enter", rules);
        assertGenericTravelWordFailsOpen("Teleport", rules);
    }

    @Test
    public void mappedNonActivationActionsStayUnknownAndUnconsumed()
    {
        DecisionService rules = rulesAt(
            new CanonicalChunk(50, 53), PermissionStatus.LOCKED);

        assertMappedNonActivationFailsOpen(
            "Drop", "Varrock teleport", rules);
        assertMappedNonActivationFailsOpen(
            "Examine", "Varrock teleport", rules);
        assertMappedNonActivationFailsOpen(
            "Destroy", "Varrock teleport", rules);
        assertMappedNonActivationFailsOpen(
            "Check", "Varrock teleport", rules);
        assertMappedNonActivationFailsOpen(
            "Configure", "Varrock teleport", rules);
        assertMappedNonActivationFailsOpen(
            "Cancel", "Varrock teleport", rules);
    }

    @Test
    public void notReadyDestinationLeavesTravelUnconsumedAndUnrecorded()
    {
        MenuOptionClicked click = travelClick();
        DecisionService rules = rules(PermissionStatus.NOT_READY);

        TravelGuardianResult result = coordinator.handle(
            click, click.getMenuEntry(), client, ORIGIN,
            context(true, false, true, true, rules), rules, availability);

        assertFailOpen(click, result);
        assertEquals(PermissionStatus.UNKNOWN, result.getDecision().getStatus());
    }

    @Test
    public void walkingIsNeverConsumedOrRecorded()
    {
        // Every chunk is locked, and walking is still left alone.
        DecisionService rules = lockedEverywhere();
        MenuOptionClicked click = walkClick();

        TravelGuardianResult result;
        try (MockedStatic<WorldPoint> points = walkDestination())
        {
            result = coordinator.handle(
                click, click.getMenuEntry(), client, ORIGIN,
                context(true, false, true, true, rules), rules, availability);
        }

        assertFailOpen(click, result);
        assertEquals(TravelAction.Confidence.UNKNOWN,
            result.getAction().getConfidence());
        assertNull(result.getAction().getDestination());
    }

    @Test
    public void doorsAndLaddersAreNeverConsumedOrRecorded()
    {
        DecisionService rules = lockedEverywhere();
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getOption()).thenReturn("Climb-down");
        when(entry.getTarget()).thenReturn("Ladder");
        when(entry.getType()).thenReturn(MenuAction.GAME_OBJECT_FIRST_OPTION);
        when(entry.getParam0()).thenReturn(10);
        when(entry.getParam1()).thenReturn(20);
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        when(click.getMenuEntry()).thenReturn(entry);

        TravelGuardianResult result;
        try (MockedStatic<WorldPoint> points = walkDestination())
        {
            result = coordinator.handle(
                click, click.getMenuEntry(), client, ORIGIN,
                context(true, false, true, true, rules), rules, availability);
        }

        assertFailOpen(click, result);
        assertEquals(TravelAction.Confidence.UNKNOWN,
            result.getAction().getConfidence());
    }

    @Test
    public void alternativeLookupFailureNeverCancelsAProvenBlock()
    {
        MenuOptionClicked click = travelClick();
        DecisionService rules = rules(PermissionStatus.LOCKED);
        when(finder.find(any(), any(), any()))
            .thenThrow(new IllegalStateException("inventory unavailable"));

        TravelGuardianResult result = coordinator.handle(
            click, click.getMenuEntry(), client, ORIGIN,
            context(true, false, true, true, rules), rules, availability);

        verify(click).consume();
        assertTrue(result.isWriteBlockedAudit());
        assertNull(result.getAlternative());
        assertNull(noticeStore.current().get().getAlternative());
    }

    private void assertFailOpen(
        MenuOptionClicked click, TravelGuardianResult result)
    {
        verify(click, never()).consume();
        assertFalse(noticeStore.current().isPresent());
        assertFalse(result.isWriteChat());
        assertFalse(result.isWriteBlockedAudit());
        assertFalse(result.isWritePausedAudit());
    }

    private MenuOptionClicked walkClick()
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getOption()).thenReturn("Walk here");
        when(entry.getTarget()).thenReturn("");
        when(entry.getType()).thenReturn(MenuAction.WALK);
        when(entry.getParam0()).thenReturn(10);
        when(entry.getParam1()).thenReturn(20);
        when(client.getPlane()).thenReturn(0);
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        when(click.getMenuEntry()).thenReturn(entry);
        return click;
    }

    /** An exact travel click: "Cast" on the tagged "Falador Teleport" lands in DESTINATION. */
    private static MenuOptionClicked travelClick()
    {
        return namedTeleportClick("Cast", "<col=00ff00>Falador Teleport</col> <col=ef4444>(LOCKED)</col>");
    }

    private static DecisionService lockedEverywhere()
    {
        DecisionService rules = mock(DecisionService.class);
        when(rules.chunk(any())).thenReturn(
            new Decision(PermissionStatus.LOCKED, "Locked destination", null, Decision.Source.CHUNK));
        return rules;
    }

    private static MenuOptionClicked namedTeleportClick(
        String option, String target)
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getOption()).thenReturn(option);
        when(entry.getTarget()).thenReturn(target);
        when(entry.getType()).thenReturn(MenuAction.UNKNOWN);
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        when(click.getMenuEntry()).thenReturn(entry);
        return click;
    }

    private void assertGenericTravelWordFailsOpen(String option, DecisionService rules)
    {
        MenuOptionClicked click = namedTeleportClick(option, "New destination");
        TravelGuardianResult result = coordinator.handle(
            click, click.getMenuEntry(), client, ORIGIN,
            context(true, false, true, true, rules), rules, availability);

        assertFailOpen(click, result);
        assertEquals(TravelAction.Confidence.UNKNOWN,
            result.getAction().getConfidence());
        assertNull(result.getAction().getDestination());
    }
    private void assertMappedNonActivationFailsOpen(
        String option, String target, DecisionService rules)
    {
        MenuOptionClicked click = namedTeleportClick(option, target);
        TravelGuardianResult result = coordinator.handle(
            click, click.getMenuEntry(), client, ORIGIN,
            context(true, false, true, true, rules), rules, availability);

        assertFailOpen(click, result);
        assertEquals(TravelAction.Family.UNKNOWN,
            result.getAction().getFamily());
        assertEquals(TravelAction.Confidence.UNKNOWN,
            result.getAction().getConfidence());
        assertNull(result.getAction().getDestination());
    }
    private MockedStatic<WorldPoint> walkDestination()
    {
        return walkDestination(DESTINATION);
    }

    private MockedStatic<WorldPoint> walkDestination(CanonicalChunk destination)
    {
        MockedStatic<WorldPoint> points = mockStatic(WorldPoint.class);
        points.when(() -> WorldPoint.fromScene(client, 10, 20, 0))
            .thenReturn(new WorldPoint(destination.getCx() << 6,
                destination.getCy() << 6, 0));
        return points;
    }

    /** The readiness the plugin would show for these facts, on rules bound to Nubles. */
    private static StrictModeReadiness context(
        boolean enabled,
        boolean paused,
        boolean accountMatches,
        boolean freshRules,
        DecisionService rules)
    {
        return StrictModeReadiness.evaluate(
            enabled, paused, rules != null, "Nubles",
            accountMatches ? "Nubles" : "Zezima", accountMatches, freshRules);
    }

    private static DecisionService rules(PermissionStatus status)
    {
        return rulesAt(DESTINATION, status);
    }

    private static DecisionService rulesAt(
        CanonicalChunk destination, PermissionStatus status)
    {
        DecisionService rules = mock(DecisionService.class);
        when(rules.chunk(destination)).thenReturn(
            new Decision(status, "Locked destination", null, Decision.Source.CHUNK));
        return rules;
    }

    private FateLockedBundle fixture(String name) throws Exception
    {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name))
        {
            return FateLockedBundle.loadFromJson(
                new Gson(),
                new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
