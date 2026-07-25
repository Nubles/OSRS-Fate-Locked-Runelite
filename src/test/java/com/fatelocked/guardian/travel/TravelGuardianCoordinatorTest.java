package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;
import com.fatelocked.guardian.GuardContext;
import com.fatelocked.guardian.StrictModeClickHandler;
import com.fatelocked.guardian.StrictModeGuard;
import com.fatelocked.rules.FateRuleEngine;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RuleDecision;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TravelGuardianCoordinatorTest
{
    private static final CanonicalChunk ORIGIN = new CanonicalChunk(50, 51);
    private static final CanonicalChunk DESTINATION = new CanonicalChunk(51, 51);

    private final Client client = mock(Client.class);
    private final TravelAvailability availability = mock(TravelAvailability.class);
    private final TravelBlockNoticeStore noticeStore = new TravelBlockNoticeStore(
        Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC));
    private final TravelAlternativeFinder finder = mock(TravelAlternativeFinder.class);
    private final TravelGuardianCoordinator coordinator = new TravelGuardianCoordinator(
        new TravelActionResolver(),
        new TravelRuleEvaluator(),
        finder,
        noticeStore,
        new StrictModeClickHandler(new StrictModeGuard()));

    @Test
    public void provenLockedTravelIsConsumedAndExplainedOnce()
    {
        MenuOptionClicked click = walkClick();
        FateRuleEngine rules = rules(PermissionStatus.LOCKED);

        try (MockedStatic<WorldPoint> points = walkDestination())
        {
            TravelGuardianResult first = coordinator.handle(
                click, click.getMenuEntry(), client, ORIGIN,
                context(true, false, true, true, rules), rules, availability);
            TravelGuardianResult repeated = coordinator.handle(
                click, click.getMenuEntry(), client, ORIGIN,
                context(true, false, true, true, rules), rules, availability);

            verify(click, times(2)).consume();
            assertEquals("Travel blocked \u2014 Walk here",
                noticeStore.current().get().getHeadline());
            assertTrue(first.isWriteChat());
            assertFalse(repeated.isWriteChat());
            assertTrue(first.isWriteBlockedAudit());
            assertFalse(first.isWritePausedAudit());
        }
    }

    @Test
    public void pausedTravelIsAllowedAndMarkedOnlyForLocalAudit()
    {
        MenuOptionClicked click = walkClick();
        FateRuleEngine rules = rules(PermissionStatus.LOCKED);

        TravelGuardianResult result;
        try (MockedStatic<WorldPoint> points = walkDestination())
        {
            result = coordinator.handle(
                click, click.getMenuEntry(), client, ORIGIN,
                context(true, true, true, true, rules), rules, availability);
        }

        verify(click, never()).consume();
        assertFalse(noticeStore.current().isPresent());
        assertFalse(result.isWriteChat());
        assertFalse(result.isWriteBlockedAudit());
        assertTrue(result.isWritePausedAudit());
    }

    @Test
    public void strictModeOffLeavesTravelUnconsumedAndUnrecorded()
    {
        MenuOptionClicked click = walkClick();
        FateRuleEngine rules = rules(PermissionStatus.LOCKED);

        TravelGuardianResult result;
        try (MockedStatic<WorldPoint> points = walkDestination())
        {
            result = coordinator.handle(
                click, click.getMenuEntry(), client, ORIGIN,
                context(false, false, true, true, rules), rules, availability);
        }

        assertFailOpen(click, result);
    }

    @Test
    public void staleRulesLeaveTravelUnconsumedAndUnrecorded()
    {
        MenuOptionClicked click = walkClick();
        FateRuleEngine rules = rules(PermissionStatus.LOCKED);

        TravelGuardianResult result;
        try (MockedStatic<WorldPoint> points = walkDestination())
        {
            result = coordinator.handle(
                click, click.getMenuEntry(), client, ORIGIN,
                context(true, false, true, false, rules), rules, availability);
        }

        assertFailOpen(click, result);
    }

    @Test
    public void wrongAccountLeavesTravelUnconsumedAndUnrecorded()
    {
        MenuOptionClicked click = walkClick();
        FateRuleEngine rules = rules(PermissionStatus.LOCKED);

        TravelGuardianResult result;
        try (MockedStatic<WorldPoint> points = walkDestination())
        {
            result = coordinator.handle(
                click, click.getMenuEntry(), client, ORIGIN,
                context(true, false, false, true, rules), rules, availability);
        }

        assertFailOpen(click, result);
    }

    @Test
    public void legacyRulesLeaveTravelUnconsumedAndUnrecorded() throws Exception
    {
        MenuOptionClicked click = namedTeleportClick("Teleport", "Falador");
        FateRuleEngine legacy = new FateRuleEngine(
            fixture("bundles/v3-standard.json"), true, false);

        TravelGuardianResult result = coordinator.handle(
            click, click.getMenuEntry(), client, ORIGIN,
            context(true, false, true, true, legacy), legacy, availability);

        assertFailOpen(click, result);
    }

    @Test
    public void unknownTravelLeavesTheClickForTheGenericPath()
    {
        MenuOptionClicked click = namedTeleportClick("Continue", "");
        FateRuleEngine rules = rules(PermissionStatus.LOCKED);

        TravelGuardianResult result = coordinator.handle(
            click, click.getMenuEntry(), client, ORIGIN,
            context(true, false, true, true, rules), rules, availability);

        assertFailOpen(click, result);
        assertEquals(TravelAction.Confidence.UNKNOWN,
            result.getAction().getConfidence());
    }

    @Test
    public void notReadyDestinationLeavesTravelUnconsumedAndUnrecorded()
    {
        MenuOptionClicked click = walkClick();
        FateRuleEngine rules = rules(PermissionStatus.NOT_READY);

        TravelGuardianResult result;
        try (MockedStatic<WorldPoint> points = walkDestination())
        {
            result = coordinator.handle(
                click, click.getMenuEntry(), client, ORIGIN,
                context(true, false, true, true, rules), rules, availability);
        }

        assertFailOpen(click, result);
        assertEquals(PermissionStatus.UNKNOWN, result.getDecision().getStatus());
    }

    @Test
    public void sameChunkWalkInsideALockedChunkRemainsUnconsumedAndUnrecorded()
    {
        MenuOptionClicked click = walkClick();
        FateRuleEngine rules = rulesAt(ORIGIN, PermissionStatus.LOCKED);

        TravelGuardianResult result;
        try (MockedStatic<WorldPoint> points = walkDestination(ORIGIN))
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
    public void walkWithoutAKnownOriginRemainsUnconsumedAndUnrecorded()
    {
        MenuOptionClicked click = walkClick();
        FateRuleEngine rules = rules(PermissionStatus.LOCKED);

        TravelGuardianResult result;
        try (MockedStatic<WorldPoint> points = walkDestination())
        {
            result = coordinator.handle(
                click, click.getMenuEntry(), client, null,
                context(true, false, true, true, rules), rules, availability);
        }

        assertFailOpen(click, result);
        assertEquals(TravelAction.Confidence.UNKNOWN,
            result.getAction().getConfidence());
        assertNull(result.getAction().getDestination());
    }

    @Test
    public void alternativeLookupFailureNeverCancelsAProvenBlock()
    {
        MenuOptionClicked click = walkClick();
        FateRuleEngine rules = rules(PermissionStatus.LOCKED);
        when(finder.find(any(), any(), any()))
            .thenThrow(new IllegalStateException("inventory unavailable"));

        TravelGuardianResult result;
        try (MockedStatic<WorldPoint> points = walkDestination())
        {
            result = coordinator.handle(
                click, click.getMenuEntry(), client, ORIGIN,
                context(true, false, true, true, rules), rules, availability);
        }

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

    private static GuardContext context(
        boolean enabled,
        boolean paused,
        boolean accountMatches,
        boolean freshRules,
        FateRuleEngine rules)
    {
        return new GuardContext(
            enabled, paused, accountMatches, freshRules, rules);
    }

    private static FateRuleEngine rules(PermissionStatus status)
    {
        return rulesAt(DESTINATION, status);
    }

    private static FateRuleEngine rulesAt(
        CanonicalChunk destination, PermissionStatus status)
    {
        FateRuleEngine rules = mock(FateRuleEngine.class);
        when(rules.entry(destination)).thenReturn(
            new RuleDecision(status, "Locked destination", null));
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
