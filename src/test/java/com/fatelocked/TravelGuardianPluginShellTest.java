package com.fatelocked;

import com.fatelocked.guardian.GuardResult;
import com.fatelocked.guardian.StrictModeAuditEntry;
import com.fatelocked.guardian.StrictModeClickHandler;
import com.fatelocked.guardian.StrictModeGuard;
import com.fatelocked.guardian.StrictModeReadiness;
import com.fatelocked.guardian.travel.TravelAction;
import com.fatelocked.guardian.travel.TravelActionResolver;
import com.fatelocked.guardian.travel.TravelAlternative;
import com.fatelocked.guardian.travel.TravelAlternativeFinder;
import com.fatelocked.guardian.travel.TravelAvailability;
import com.fatelocked.guardian.travel.TravelBlockNoticeStore;
import com.fatelocked.guardian.travel.TravelDecision;
import com.fatelocked.guardian.travel.EnforcementPresenter;
import com.fatelocked.guardian.travel.TravelGuardianCoordinator;
import com.fatelocked.guardian.travel.TravelGuardianResult;
import com.fatelocked.guardian.travel.TravelRuleEvaluator;
import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TravelGuardianPluginShellTest
{
    private static final CanonicalChunk ORIGIN = new CanonicalChunk(50, 51);
    private static final CanonicalChunk DESTINATION = new CanonicalChunk(51, 51);
    /** Where "Teleport" on "Falador" lands. */
    private static final CanonicalChunk FALADOR = new CanonicalChunk(46, 52);
    /** Strict Mode on, with fresh rules bound to the character playing. */
    private static final StrictModeReadiness ACTIVE =
        StrictModeReadiness.evaluate(true, false, true, "Nubles", "Nubles", true, true);
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC);

    @Test
    public void exactTravelShortCircuitsAndEverythingElseIsLeftAlone()
    {
        Harness harness = new Harness();
        DecisionService rules = rulesAt(FALADOR, PermissionStatus.ALLOWED);
        TravelGuardianPluginShell shell = harness.actualShell();
        MenuOptionClicked unresolvedClick =
            click("Continue", "", MenuAction.UNKNOWN);

        TravelGuardianPluginShell.Route exact = shell.handle(
            click("Teleport", "Falador", MenuAction.UNKNOWN),
            harness.client, ORIGIN, ACTIVE, rules);
        TravelGuardianPluginShell.Route unresolved = shell.handle(
            unresolvedClick, harness.client, ORIGIN, ACTIVE, rules);

        assertEquals(TravelGuardianPluginShell.Route.EXACT_TRAVEL, exact);
        assertEquals(TravelGuardianPluginShell.Route.NOT_TRAVEL, unresolved);
        verify(unresolvedClick, never()).consume();
    }

    @Test
    public void coordinatorExceptionFailsOpen()
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
        DecisionService rules = rulesAt(FALADOR, PermissionStatus.LOCKED);

        TravelGuardianPluginShell.Route route = shell.handle(
            click, harness.client, ORIGIN, ACTIVE, rules);

        assertEquals(TravelGuardianPluginShell.Route.FAIL_OPEN, route);
        verify(click, never()).consume();
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
        DecisionService locked = rulesAt(FALADOR, PermissionStatus.LOCKED);
        MenuOptionClicked first = click("Teleport", "Falador", MenuAction.UNKNOWN);
        chatFailure.actualShell().handle(
            first, chatFailure.client, ORIGIN, ACTIVE, locked);

        verify(first).consume();
        assertTrue(chatFailure.noticeStore.current().isPresent());
        assertEquals(1, chatFailure.audit.size());
        assertEquals(Collections.singletonList("chat"),
            chatFailure.diagnostics);

        Harness auditFailure = new Harness();
        auditFailure.auditFailure = new IllegalStateException("audit");
        DecisionService secondLocked = rulesAt(FALADOR, PermissionStatus.LOCKED);
        MenuOptionClicked second = click("Teleport", "Falador", MenuAction.UNKNOWN);
        auditFailure.actualShell().handle(
            second, auditFailure.client, ORIGIN,
            ACTIVE, secondLocked);

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
        DecisionService rules = rulesAt(DESTINATION, PermissionStatus.LOCKED);

        shell.handle(click("Cast", "Ectophial", MenuAction.UNKNOWN),
            harness.client, ORIGIN, ACTIVE, rules);
        shell.handle(click("Cast", "Ectophial", MenuAction.UNKNOWN),
            harness.client, ORIGIN, ACTIVE, rules);

        assertEquals(
            "Strict Mode blocked Teleport to Morytania: Morytania is locked. "
                + "Try Varrock teleport tablet instead. "
                + "To go anyway, pause Strict Mode for 60 seconds from the banner or the sidebar.",
            harness.chat.get(0));
        assertEquals(
            "Strict Mode blocked Teleport to Morytania: Morytania is locked. "
                + "To go anyway, pause Strict Mode for 60 seconds from the banner or the sidebar.",
            harness.chat.get(1));
        assertFalse(harness.chat.get(1).contains("Try"));
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
        DecisionService rules = rulesAt(DESTINATION, PermissionStatus.LOCKED);

        shell.handle(click("Cast", "Ectophial", MenuAction.UNKNOWN),
            harness.client, ORIGIN, ACTIVE, rules);
        shell.handle(click("Cast", "Ectophial", MenuAction.UNKNOWN),
            harness.client, ORIGIN, ACTIVE, rules);

        StrictModeAuditEntry blocked = harness.audit.get(0);
        assertEquals(CLOCK.millis(), blocked.getTimestamp());
        assertEquals("TRAVEL", blocked.getActionKind());
        assertEquals("Teleport to Morytania", blocked.getTarget());
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
    public void mappedNonActivationOptionsAreNeverConsumed()
    {
        Harness harness = new Harness();
        TravelGuardianPluginShell shell = harness.actualShell();
        DecisionService locked = rulesAt(
            new CanonicalChunk(50, 53), PermissionStatus.LOCKED);
        String[] nonActivationOptions = {
            "Drop", "Examine", "Destroy", "Check", "Configure", "Cancel"
        };

        for (String option : nonActivationOptions)
        {
            MenuOptionClicked click = click(
                option, "Varrock teleport", MenuAction.UNKNOWN);

            TravelGuardianPluginShell.Route route = shell.handle(
                click, harness.client, ORIGIN, ACTIVE, locked);

            assertEquals(TravelGuardianPluginShell.Route.NOT_TRAVEL, route);
            verify(click, never()).consume();
        }

        assertTrue(harness.chat.isEmpty());
        assertTrue(harness.audit.isEmpty());
        assertFalse(harness.noticeStore.current().isPresent());
    }

    @Test
    public void npcObjectBankAndEquipmentClicksAreNeverConsumed()
    {
        // Strict Mode blocks travel only (review finding G3). Even with fresh,
        // bound rules that lock every target, these clicks are left alone.
        Harness harness = new Harness();
        TravelGuardianPluginShell shell = harness.actualShell();
        DecisionService rules = mock(DecisionService.class);
        Decision locked = new Decision(
            PermissionStatus.LOCKED, "Locked target", "locked", Decision.Source.CHUNK);
        when(rules.item(anyInt())).thenReturn(locked);
        when(rules.target(any(), anyString(), anyString())).thenReturn(locked);
        when(rules.chunk(any())).thenReturn(locked);
        StrictModeReadiness trusted = ACTIVE;

        MenuOptionClicked equipment = click(
            "Wield", "Abyssal whip", MenuAction.UNKNOWN);
        when(equipment.getMenuEntry().getItemId()).thenReturn(4151);

        NPC bankerNpc = mock(NPC.class);
        when(bankerNpc.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        MenuOptionClicked bank = click("Bank", "Banker", MenuAction.NPC_FIRST_OPTION);
        when(bank.getMenuEntry().getNpc()).thenReturn(bankerNpc);

        NPC goblinNpc = mock(NPC.class);
        when(goblinNpc.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        MenuOptionClicked npc = click(
            "Attack", "Goblin (level-2)", MenuAction.NPC_FIRST_OPTION);
        when(npc.getMenuEntry().getNpc()).thenReturn(goblinNpc);

        MenuOptionClicked object = click(
            "Chop down", "Oak tree", MenuAction.GAME_OBJECT_FIRST_OPTION);
        when(object.getMenuEntry().getParam0()).thenReturn(10);
        when(object.getMenuEntry().getParam1()).thenReturn(20);
        when(harness.client.getPlane()).thenReturn(0);

        try (MockedStatic<WorldPoint> points = mockStatic(WorldPoint.class))
        {
            points.when(() -> WorldPoint.fromScene(harness.client, 10, 20, 0))
                .thenReturn(new WorldPoint(3200, 3200, 0));
            for (MenuOptionClicked click : new MenuOptionClicked[] {
                equipment, bank, npc, object })
            {
                assertEquals(TravelGuardianPluginShell.Route.NOT_TRAVEL,
                    shell.handle(click, harness.client, ORIGIN, trusted, rules));
                verify(click, never()).consume();
            }
        }
        assertTrue(harness.chat.isEmpty());
        assertTrue(harness.audit.isEmpty());
    }

    @Test
    public void walkingIsNeverConsumed()
    {
        Harness harness = new Harness();
        TravelGuardianPluginShell shell = harness.actualShell();
        DecisionService locked = mock(DecisionService.class);
        when(locked.chunk(any())).thenReturn(new Decision(
            PermissionStatus.LOCKED, "Morytania", null, Decision.Source.CHUNK));

        MenuOptionClicked withOrigin = walkClick(harness.client);
        MenuOptionClicked withoutOrigin = walkClick(harness.client);
        try (MockedStatic<WorldPoint> points =
            walkDestination(harness.client, DESTINATION))
        {
            assertEquals(TravelGuardianPluginShell.Route.NOT_TRAVEL,
                shell.handle(withOrigin, harness.client, ORIGIN, ACTIVE, locked));
            assertEquals(TravelGuardianPluginShell.Route.NOT_TRAVEL,
                shell.handle(withoutOrigin, harness.client, null, ACTIVE, locked));
        }

        verify(withOrigin, never()).consume();
        verify(withoutOrigin, never()).consume();
        assertTrue(harness.chat.isEmpty());
        assertTrue(harness.audit.isEmpty());
        assertFalse(harness.noticeStore.current().isPresent());
    }

    @Test
    public void destinationNamedCarrierMenusRouteThroughMobilityEnforcement()
    {
        Harness harness = new Harness();
        TravelGuardianPluginShell shell = harness.actualShell();

        DecisionService jewelryRules = mobilityLocked(
            new CanonicalChunk(48, 54), "Edgeville", "Jewelry Teleports");
        MenuOptionClicked jewelry = click(
            "Edgeville", "Amulet of glory(6)", MenuAction.UNKNOWN);
        assertEquals(TravelGuardianPluginShell.Route.EXACT_TRAVEL,
            shell.handle(jewelry, harness.client, ORIGIN,
                ACTIVE, jewelryRules));
        verify(jewelry).consume();
        assertTrue(harness.noticeStore.current().isPresent());
        assertEquals("BLOCKED", harness.audit.get(0).getOutcome());

        DecisionService spiritTreeRules = mobilityLocked(
            new CanonicalChunk(38, 53), "Tree Gnome Stronghold", "Spirit Trees");
        MenuOptionClicked spiritTree = click(
            "Tree Gnome Stronghold", "Spirit tree", MenuAction.UNKNOWN);
        assertEquals(TravelGuardianPluginShell.Route.EXACT_TRAVEL,
            shell.handle(spiritTree, harness.client, ORIGIN,
                ACTIVE, spiritTreeRules));
        verify(spiritTree).consume();
        assertTrue(harness.noticeStore.current().isPresent());
        assertEquals("BLOCKED", harness.audit.get(1).getOutcome());

        assertEquals(2, harness.chat.size());
        assertEquals(2, harness.audit.size());
    }

    private static TravelGuardianResult blockedResult(
        TravelAlternative alternative)
    {
        TravelAction action = exactAction();
        TravelDecision decision = new TravelDecision(
            PermissionStatus.LOCKED, "Teleport to Morytania", "Morytania is locked");
        return new TravelGuardianResult(
            action, decision, alternative,
            new GuardResult(GuardResult.Outcome.BLOCK, decision),
            new EnforcementPresenter().present(action, decision, alternative),
            true, true, false);
    }

    private static TravelGuardianResult pausedResult()
    {
        TravelAction action = exactAction();
        TravelDecision decision = new TravelDecision(
            PermissionStatus.LOCKED, "Teleport to Morytania", "Morytania is locked");
        return new TravelGuardianResult(
            action, decision, null,
            new GuardResult(GuardResult.Outcome.ALLOW_PAUSED, null), null,
            false, false, true);
    }

    private static TravelAction exactAction()
    {
        return new TravelAction(
            TravelAction.Family.SPELL_OR_ITEM, "named-teleport", "Teleport to Morytania",
            ORIGIN, DESTINATION, null, TravelAction.Confidence.EXACT);
    }

    private static DecisionService mobilityLocked(
        CanonicalChunk destination, String label, String mobility)
    {
        DecisionService rules = mock(DecisionService.class);
        when(rules.chunk(destination)).thenReturn(
            new Decision(PermissionStatus.ALLOWED, label, null, Decision.Source.CHUNK));
        when(rules.mobility(mobility)).thenReturn(
            new Decision(PermissionStatus.LOCKED, mobility,
                mobility + " is locked", Decision.Source.MOBILITY));
        return rules;
    }

    private static DecisionService rulesAt(
        CanonicalChunk chunk, PermissionStatus status)
    {
        DecisionService rules = mock(DecisionService.class);
        when(rules.chunk(chunk)).thenReturn(
            new Decision(status, "Morytania", null, Decision.Source.CHUNK));
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
        private RuntimeException chatFailure;
        private Exception auditFailure;

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
                (stage, error) -> diagnostics.add(stage),
                CLOCK);
        }
    }
}
