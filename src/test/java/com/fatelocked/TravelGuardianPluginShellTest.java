package com.fatelocked;

import com.fatelocked.guardian.GuardContext;
import com.fatelocked.guardian.GuardResult;
import com.fatelocked.guardian.GuardedAction;
import com.fatelocked.guardian.GuardedActionFactory;
import com.fatelocked.guardian.StrictModeAuditEntry;
import com.fatelocked.guardian.StrictModeClickHandler;
import com.fatelocked.guardian.StrictModeGuard;
import com.fatelocked.guardian.travel.TravelAction;
import com.fatelocked.guardian.travel.TravelActionResolver;
import com.fatelocked.guardian.travel.TravelAlternative;
import com.fatelocked.guardian.travel.TravelAlternativeFinder;
import com.fatelocked.guardian.travel.TravelAvailability;
import com.fatelocked.guardian.travel.TravelBlockNoticeStore;
import com.fatelocked.guardian.travel.TravelDecision;
import com.fatelocked.guardian.travel.TravelGuardianCoordinator;
import com.fatelocked.guardian.travel.TravelGuardianResult;
import com.fatelocked.guardian.travel.TravelRuleEvaluator;
import com.fatelocked.rules.FateRuleEngine;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RuleDecision;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TravelGuardianPluginShellTest
{
    private static final CanonicalChunk ORIGIN = new CanonicalChunk(50, 51);
    private static final CanonicalChunk DESTINATION = new CanonicalChunk(51, 51);
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC);

    @Test
    public void exactTravelShortCircuitsWhileUnresolvedActionsUseGenericGuard()
    {
        Harness harness = new Harness();
        FateRuleEngine rules = rulesAt(
            new CanonicalChunk(46, 52), PermissionStatus.ALLOWED);
        TravelGuardianPluginShell shell = harness.actualShell();

        TravelGuardianPluginShell.Route exact = shell.handle(
            click("Teleport", "Falador", MenuAction.UNKNOWN),
            harness.client, ORIGIN, enabled(rules), rules, enabled(rules));
        TravelGuardianPluginShell.Route unresolved = shell.handle(
            click("Continue", "", MenuAction.UNKNOWN),
            harness.client, ORIGIN, enabled(rules), rules, enabled(rules));

        assertEquals(TravelGuardianPluginShell.Route.EXACT_TRAVEL, exact);
        assertEquals(TravelGuardianPluginShell.Route.GENERIC, unresolved);
        assertEquals(1, harness.genericCalls);
    }

    @Test
    public void coordinatorExceptionFailsOpenWithoutFallingIntoGenericGuard()
    {
        Harness harness = new Harness();
        TravelGuardianCoordinator coordinator =
            mock(TravelGuardianCoordinator.class);
        when(coordinator.handle(
            any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new IllegalStateException("resolver failed"));
        TravelGuardianPluginShell shell = harness.shell(coordinator);
        MenuOptionClicked click =
            click("Teleport", "Falador", MenuAction.UNKNOWN);
        FateRuleEngine rules = rulesAt(
            new CanonicalChunk(46, 52), PermissionStatus.LOCKED);

        TravelGuardianPluginShell.Route route = shell.handle(
            click, harness.client, ORIGIN, enabled(rules), rules, enabled(rules));

        assertEquals(TravelGuardianPluginShell.Route.FAIL_OPEN, route);
        verify(click, never()).consume();
        assertEquals(0, harness.genericCalls);
        assertTrue(harness.chat.isEmpty());
        assertTrue(harness.audit.isEmpty());
        assertEquals(Collections.singletonList("coordinator"),
            harness.diagnostics);
    }

    @Test
    public void chatAndAuditFailuresStayIndependentFromEnforcementAndBanner()
    {
        Harness chatFailure = new Harness();
        chatFailure.chatFailure = new IllegalStateException("chat");
        FateRuleEngine locked = rulesAt(DESTINATION, PermissionStatus.LOCKED);
        MenuOptionClicked first = walkClick(chatFailure.client);
        try (MockedStatic<WorldPoint> points =
            walkDestination(chatFailure.client, DESTINATION))
        {
            chatFailure.actualShell().handle(
                first, chatFailure.client, ORIGIN, enabled(locked), locked, enabled(locked));
        }

        verify(first).consume();
        assertTrue(chatFailure.noticeStore.current().isPresent());
        assertEquals(1, chatFailure.audit.size());
        assertEquals(Collections.singletonList("chat"),
            chatFailure.diagnostics);

        Harness auditFailure = new Harness();
        auditFailure.auditFailure = new IllegalStateException("audit");
        FateRuleEngine secondLocked =
            rulesAt(DESTINATION, PermissionStatus.LOCKED);
        MenuOptionClicked second = walkClick(auditFailure.client);
        try (MockedStatic<WorldPoint> points =
            walkDestination(auditFailure.client, DESTINATION))
        {
            auditFailure.actualShell().handle(
                second, auditFailure.client, ORIGIN,
                enabled(secondLocked), secondLocked, enabled(secondLocked));
        }

        verify(second).consume();
        assertTrue(auditFailure.noticeStore.current().isPresent());
        assertEquals(1, auditFailure.chat.size());
        assertEquals(Collections.singletonList("audit"),
            auditFailure.diagnostics);
    }

    @Test
    public void chatWordingIncludesOnlyVerifiedSuggestions()
    {
        Harness harness = new Harness();
        TravelGuardianCoordinator coordinator =
            mock(TravelGuardianCoordinator.class);
        TravelAlternative alternative = new TravelAlternative(
            "varrock-tablet", "Varrock teleport tablet",
            new CanonicalChunk(50, 53), "Teleport Tablets",
            Collections.singleton(8007),
            null, 0, null);
        when(coordinator.handle(
            any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(blockedResult(alternative), blockedResult(null));
        TravelGuardianPluginShell shell = harness.shell(coordinator);
        FateRuleEngine rules = rulesAt(DESTINATION, PermissionStatus.LOCKED);

        shell.handle(click("Walk here", "", MenuAction.WALK),
            harness.client, ORIGIN, enabled(rules), rules, enabled(rules));
        shell.handle(click("Walk here", "", MenuAction.WALK),
            harness.client, ORIGIN, enabled(rules), rules, enabled(rules));

        assertEquals(
            "[Fate Guardian] Blocked Walk here: Morytania is locked. "
                + "Suggested: Varrock teleport tablet.",
            harness.chat.get(0));
        assertEquals(
            "[Fate Guardian] Blocked Walk here: Morytania is locked.",
            harness.chat.get(1));
        assertFalse(harness.chat.get(1).contains("Suggested"));
    }

    @Test
    public void auditMappingDistinguishesBlockedAndPausedTravel()
    {
        Harness harness = new Harness();
        TravelGuardianCoordinator coordinator =
            mock(TravelGuardianCoordinator.class);
        TravelAlternative alternative = new TravelAlternative(
            "varrock-tablet", "Varrock teleport tablet",
            new CanonicalChunk(50, 53), "Teleport Tablets",
            Collections.singleton(8007),
            null, 0, null);
        when(coordinator.handle(
            any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(blockedResult(alternative), pausedResult());
        TravelGuardianPluginShell shell = harness.shell(coordinator);
        FateRuleEngine rules = rulesAt(DESTINATION, PermissionStatus.LOCKED);

        shell.handle(click("Walk here", "", MenuAction.WALK),
            harness.client, ORIGIN, enabled(rules), rules, enabled(rules));
        shell.handle(click("Walk here", "", MenuAction.WALK),
            harness.client, ORIGIN, enabled(rules), rules, enabled(rules));

        StrictModeAuditEntry blocked = harness.audit.get(0);
        assertEquals(CLOCK.millis(), blocked.getTimestamp());
        assertEquals("TRAVEL", blocked.getActionKind());
        assertEquals("Walk here", blocked.getTarget());
        assertEquals("51,51", blocked.getChunk());
        assertEquals("Morytania is locked", blocked.getReason());
        assertEquals("BLOCKED", blocked.getOutcome());
        assertFalse(blocked.isPaused());
        assertTrue(blocked.isAlternativeAvailable());

        StrictModeAuditEntry paused = harness.audit.get(1);
        assertEquals("ALLOWED_PAUSED", paused.getOutcome());
        assertTrue(paused.isPaused());
        assertFalse(paused.isAlternativeAvailable());
    }

    @Test
    public void mappedNonActivationOptionsStayUnknownThroughGenericFallback()
    {
        Harness harness = new Harness();
        harness.useRealGenericGuard();
        TravelGuardianPluginShell shell = harness.actualShell();
        FateRuleEngine locked = rulesAt(
            new CanonicalChunk(50, 53), PermissionStatus.LOCKED);
        String[] nonActivationOptions = {
            "Drop", "Examine", "Destroy", "Check", "Configure", "Cancel"
        };

        for (String option : nonActivationOptions)
        {
            MenuOptionClicked click = click(
                option, "Varrock teleport", MenuAction.UNKNOWN);

            TravelGuardianPluginShell.Route route = shell.handle(
                click, harness.client, ORIGIN, enabled(locked), locked, enabled(locked));

            assertEquals(TravelGuardianPluginShell.Route.GENERIC, route);
            verify(click, never()).consume();
        }

        assertEquals(nonActivationOptions.length, harness.genericCalls);
        assertTrue(harness.chat.isEmpty());
        assertTrue(harness.audit.isEmpty());
        assertFalse(harness.noticeStore.current().isPresent());
    }
    @Test
    public void genericFallbackUsesItsOwnTrustContextForLegacyCategories()
    {
        Harness harness = new Harness();
        harness.useRealGenericGuard();
        TravelGuardianPluginShell shell = harness.actualShell();
        FateRuleEngine travelRules = mock(FateRuleEngine.class);
        GuardContext unboundTravel = new GuardContext(
            true, false, false, true, travelRules);
        FateRuleEngine genericRules = mock(FateRuleEngine.class);
        GuardContext trustedGeneric = new GuardContext(
            true, false, true, true, genericRules);
        CanonicalChunk targetChunk = new CanonicalChunk(50, 50);
        RuleDecision locked = new RuleDecision(
            PermissionStatus.LOCKED, "Legacy target", "locked");
        when(genericRules.equipment(4151)).thenReturn(locked);
        when(genericRules.target(targetChunk, "BANK", "")).thenReturn(locked);
        when(genericRules.target(targetChunk, "NPC", "goblin")).thenReturn(locked);
        when(genericRules.target(targetChunk, "OBJECT", "oak tree")).thenReturn(locked);

        MenuOptionClicked equipment = click(
            "Wield", "Abyssal whip", MenuAction.UNKNOWN);
        when(equipment.getMenuEntry().getItemId()).thenReturn(4151);
        shell.handle(equipment, harness.client, ORIGIN,
            unboundTravel, travelRules, trustedGeneric);

        NPC bankerNpc = mock(NPC.class);
        when(bankerNpc.getWorldLocation()).thenReturn(
            new WorldPoint(3200, 3200, 0));
        MenuOptionClicked bank = click(
            "Bank", "Banker", MenuAction.NPC_FIRST_OPTION);
        when(bank.getMenuEntry().getNpc()).thenReturn(bankerNpc);
        shell.handle(bank, harness.client, ORIGIN,
            unboundTravel, travelRules, trustedGeneric);

        NPC goblinNpc = mock(NPC.class);
        when(goblinNpc.getWorldLocation()).thenReturn(
            new WorldPoint(3200, 3200, 0));
        MenuOptionClicked npc = click(
            "Attack", "Goblin", MenuAction.NPC_FIRST_OPTION);
        when(npc.getMenuEntry().getNpc()).thenReturn(goblinNpc);
        shell.handle(npc, harness.client, ORIGIN,
            unboundTravel, travelRules, trustedGeneric);

        MenuOptionClicked object = click(
            "Chop down", "Oak tree", MenuAction.GAME_OBJECT_FIRST_OPTION);
        when(object.getMenuEntry().getParam0()).thenReturn(10);
        when(object.getMenuEntry().getParam1()).thenReturn(20);
        when(harness.client.getPlane()).thenReturn(0);
        try (MockedStatic<WorldPoint> points = mockStatic(WorldPoint.class))
        {
            points.when(() -> WorldPoint.fromScene(harness.client, 10, 20, 0))
                .thenReturn(new WorldPoint(3200, 3200, 0));
            shell.handle(object, harness.client, ORIGIN,
                unboundTravel, travelRules, trustedGeneric);
        }

        verify(equipment).consume();
        verify(bank).consume();
        verify(npc).consume();
        verify(object).consume();
        assertEquals(4, harness.genericCalls);
        assertTrue(harness.chat.isEmpty());
        assertTrue(harness.audit.isEmpty());
    }
    @Test
    public void nullOriginAndSameChunkWalksRemainGenericAndUnconsumed()
    {
        Harness harness = new Harness();
        harness.useRealGenericGuard();
        TravelGuardianPluginShell shell = harness.actualShell();
        FateRuleEngine locked =
            rulesAt(DESTINATION, PermissionStatus.LOCKED);
        MenuOptionClicked nullOrigin = walkClick(harness.client);
        try (MockedStatic<WorldPoint> points =
            walkDestination(harness.client, DESTINATION))
        {
            shell.handle(nullOrigin, harness.client, null,
                enabled(locked), locked, enabled(locked));
        }

        MenuOptionClicked sameChunk = walkClick(harness.client);
        try (MockedStatic<WorldPoint> points =
            walkDestination(harness.client, DESTINATION))
        {
            shell.handle(sameChunk, harness.client, DESTINATION,
                enabled(locked), locked, enabled(locked));
        }

        verify(nullOrigin, never()).consume();
        verify(sameChunk, never()).consume();
        assertEquals(2, harness.genericCalls);
        assertTrue(harness.chat.isEmpty());
        assertTrue(harness.audit.isEmpty());
        assertFalse(harness.noticeStore.current().isPresent());
    }

    @Test
    public void destinationNamedCarrierMenusRouteThroughMobilityEnforcement()
    {
        Harness harness = new Harness();
        TravelGuardianPluginShell shell = harness.actualShell();

        FateRuleEngine jewelryRules = mobilityLocked(
            new CanonicalChunk(48, 54), "Edgeville", "Jewelry Teleports");
        MenuOptionClicked jewelry = click(
            "Edgeville", "Amulet of glory(6)", MenuAction.UNKNOWN);
        assertEquals(TravelGuardianPluginShell.Route.EXACT_TRAVEL,
            shell.handle(jewelry, harness.client, ORIGIN,
                enabled(jewelryRules), jewelryRules, enabled(jewelryRules)));
        verify(jewelry).consume();
        assertTrue(harness.noticeStore.current().isPresent());
        assertEquals("BLOCKED", harness.audit.get(0).getOutcome());

        FateRuleEngine spiritTreeRules = mobilityLocked(
            new CanonicalChunk(38, 53), "Tree Gnome Stronghold", "Spirit Trees");
        MenuOptionClicked spiritTree = click(
            "Tree Gnome Stronghold", "Spirit tree", MenuAction.UNKNOWN);
        assertEquals(TravelGuardianPluginShell.Route.EXACT_TRAVEL,
            shell.handle(spiritTree, harness.client, ORIGIN,
                enabled(spiritTreeRules), spiritTreeRules, enabled(spiritTreeRules)));
        verify(spiritTree).consume();
        assertTrue(harness.noticeStore.current().isPresent());
        assertEquals("BLOCKED", harness.audit.get(1).getOutcome());

        assertEquals(2, harness.chat.size());
        assertEquals(2, harness.audit.size());
        assertEquals(0, harness.genericCalls);
    }
    private static TravelGuardianResult blockedResult(
        TravelAlternative alternative)
    {
        TravelAction action = exactAction();
        TravelDecision decision = new TravelDecision(
            PermissionStatus.LOCKED, "Walk here", "Morytania is locked");
        return new TravelGuardianResult(
            action, decision, alternative,
            new GuardResult(
                GuardResult.Outcome.BLOCK,
                new RuleDecision(
                    PermissionStatus.LOCKED,
                    "Walk here",
                    "Morytania is locked")),
            true, true, false);
    }

    private static TravelGuardianResult pausedResult()
    {
        TravelAction action = exactAction();
        TravelDecision decision = new TravelDecision(
            PermissionStatus.LOCKED, "Walk here", "Morytania is locked");
        return new TravelGuardianResult(
            action, decision, null,
            new GuardResult(GuardResult.Outcome.ALLOW, null),
            false, false, true);
    }

    private static TravelAction exactAction()
    {
        return new TravelAction(
            TravelAction.Family.WALK, "walk", "Walk here",
            ORIGIN, DESTINATION, null, TravelAction.Confidence.EXACT);
    }

    private static GuardContext enabled(FateRuleEngine rules)
    {
        return new GuardContext(true, false, true, true, rules);
    }

    private static FateRuleEngine mobilityLocked(
        CanonicalChunk destination, String label, String mobility)
    {
        FateRuleEngine rules = mock(FateRuleEngine.class);
        when(rules.entry(destination)).thenReturn(
            new RuleDecision(PermissionStatus.ALLOWED, label, null));
        when(rules.mobility(mobility)).thenReturn(
            new RuleDecision(PermissionStatus.LOCKED, mobility,
                mobility + " is locked"));
        return rules;
    }
    private static FateRuleEngine rulesAt(
        CanonicalChunk chunk, PermissionStatus status)
    {
        FateRuleEngine rules = mock(FateRuleEngine.class);
        when(rules.entry(chunk)).thenReturn(
            new RuleDecision(status, "Morytania", null));
        return rules;
    }

    private static MenuOptionClicked click(
        String option, String target, MenuAction action)
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getOption()).thenReturn(option);
        when(entry.getTarget()).thenReturn(target);
        when(entry.getType()).thenReturn(action);
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        when(click.getMenuEntry()).thenReturn(entry);
        return click;
    }

    private static MenuOptionClicked walkClick(Client client)
    {
        MenuOptionClicked click = click("Walk here", "", MenuAction.WALK);
        MenuEntry entry = click.getMenuEntry();
        when(entry.getParam0()).thenReturn(10);
        when(entry.getParam1()).thenReturn(20);
        when(client.getPlane()).thenReturn(0);
        return click;
    }

    private static MockedStatic<WorldPoint> walkDestination(
        Client client, CanonicalChunk destination)
    {
        MockedStatic<WorldPoint> points = mockStatic(WorldPoint.class);
        points.when(() -> WorldPoint.fromScene(client, 10, 20, 0))
            .thenReturn(new WorldPoint(
                destination.getCx() << 6,
                destination.getCy() << 6,
                0));
        return points;
    }

    private static final class Harness
    {
        private final Client client = mock(Client.class);
        private final TravelAvailability availability =
            mock(TravelAvailability.class);
        private final TravelBlockNoticeStore noticeStore =
            new TravelBlockNoticeStore(CLOCK);
        private final List<String> chat = new ArrayList<>();
        private final List<StrictModeAuditEntry> audit = new ArrayList<>();
        private final List<String> diagnostics = new ArrayList<>();
        private int genericCalls;
        private TravelGuardianPluginShell.GenericHandler genericHandler =
            (event, context) -> genericCalls++;
        private RuntimeException chatFailure;
        private Exception auditFailure;

        private void useRealGenericGuard()
        {
            GuardedActionFactory factory = new GuardedActionFactory();
            StrictModeClickHandler clickHandler =
                new StrictModeClickHandler(new StrictModeGuard());
            genericHandler = (event, context) -> {
                genericCalls++;
                GuardedAction action = factory.from(
                    event.getMenuEntry(), client);
                clickHandler.handle(event, action, context);
            };
        }
        private TravelGuardianPluginShell actualShell()
        {
            return shell(new TravelGuardianCoordinator(
                new TravelActionResolver(),
                new TravelRuleEvaluator(),
                new TravelAlternativeFinder(),
                noticeStore,
                new StrictModeClickHandler(new StrictModeGuard())));
        }

        private TravelGuardianPluginShell shell(
            TravelGuardianCoordinator coordinator)
        {
            return new TravelGuardianPluginShell(
                coordinator,
                availability,
                message -> {
                    if (chatFailure != null) throw chatFailure;
                    chat.add(message);
                },
                entry -> {
                    if (auditFailure != null) throw auditFailure;
                    audit.add(entry);
                },
                genericHandler,
                (stage, error) -> diagnostics.add(stage),
                CLOCK);
        }
    }
}
