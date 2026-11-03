package com.fatelocked;

import com.fatelocked.guardian.StrictModeAuditEntry;
import com.fatelocked.guardian.StrictModeClickHandler;
import com.fatelocked.guardian.StrictModeGuard;
import com.fatelocked.guardian.StrictModeReadiness;
import com.fatelocked.guardian.travel.EnforcementPresenter;
import com.fatelocked.guardian.travel.IntentClassifier;
import com.fatelocked.guardian.travel.TravelAction;
import com.fatelocked.guardian.travel.TravelAlternative;
import com.fatelocked.guardian.travel.TravelAlternativeFinder;
import com.fatelocked.guardian.travel.TravelAvailability;
import com.fatelocked.guardian.travel.TravelBlockNoticeStore;
import com.fatelocked.guardian.travel.TravelDecision;
import com.fatelocked.guardian.travel.TravelFixtures;
import com.fatelocked.guardian.travel.TravelGuardianCoordinator;
import com.fatelocked.guardian.travel.TravelGuardianResult;
import com.fatelocked.guardian.travel.TravelRuleEvaluator;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.fatelocked.guardian.travel.TravelFixtures.AMULET_OF_GLORY_4;
import static com.fatelocked.guardian.travel.TravelFixtures.FAIRY_RING;
import static com.fatelocked.guardian.travel.TravelFixtures.FALADOR_TABLET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The plugin's travel boundary: it reads each click as RuneLite gives it,
 * routes it through the coordinator, and writes chat and audit after the
 * verdict. Only a trip the tracker's table matches by id is travel; any
 * failure lets the click through.
 */
public class TravelGuardianPluginShellTest
{
    private static final CanonicalChunk DESTINATION = new CanonicalChunk(51, 51);
    /** Strict Mode on, with fresh rules bound to the character playing. */
    private static final StrictModeReadiness ACTIVE =
        StrictModeReadiness.evaluate(true, false, true, true, "Nubles", "Nubles", true, true);
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC);

    private final DecisionService rules = TravelFixtures.nubles();

    @Test
    public void exactTravelShortCircuitsAndEverythingElseIsLeftAlone()
    {
        Harness harness = new Harness();
        TravelGuardianPluginShell shell = harness.actualShell();
        MenuOptionClicked allowed = TravelClicks.cast("Lumbridge Teleport");
        MenuOptionClicked unresolved = TravelClicks.click(MenuAction.WIDGET_CONTINUE, "Continue", "");

        assertEquals(TravelGuardianPluginShell.Route.EXACT_TRAVEL,
            shell.handle(allowed, harness.client, ACTIVE, rules));
        assertEquals(TravelGuardianPluginShell.Route.NOT_TRAVEL,
            shell.handle(unresolved, harness.client, ACTIVE, rules));
        verify(allowed, never()).consume();
        verify(unresolved, never()).consume();
    }

    @Test
    public void coordinatorExceptionFailsOpen()
    {
        Harness harness = new Harness();
        TravelGuardianCoordinator coordinator = mock(TravelGuardianCoordinator.class);
        when(coordinator.handle(any(), any(), any(), any(), any()))
            .thenThrow(new IllegalStateException("classifier failed"));
        TravelGuardianPluginShell shell = harness.shell(coordinator);
        MenuOptionClicked click = TravelClicks.cast("Falador Teleport");

        TravelGuardianPluginShell.Route route = shell.handle(click, harness.client, ACTIVE, rules);

        assertEquals(TravelGuardianPluginShell.Route.FAIL_OPEN, route);
        verify(click, never()).consume();
        assertTrue(harness.chat.isEmpty());
        assertTrue(harness.audit.isEmpty());
        assertEquals(Collections.singletonList("coordinator"), harness.diagnostics);
    }

    /** Reading the click is inside the same catch: a menu entry that can't be read lets the click through. */
    @Test
    public void aClickThatCannotBeReadFailsOpen()
    {
        Harness harness = new Harness();
        MenuOptionClicked click = TravelClicks.cast("Falador Teleport");
        when(harness.client.getVarbitValue(anyInt())).thenThrow(new IllegalStateException("no varbits"));

        assertEquals(TravelGuardianPluginShell.Route.FAIL_OPEN,
            harness.actualShell().handle(click, harness.client, ACTIVE, rules));
        verify(click, never()).consume();
        assertEquals(Collections.singletonList("coordinator"), harness.diagnostics);
    }

    @Test
    public void chatAndAuditFailuresStayIndependentFromEnforcementAndBanner()
    {
        Harness chatFailure = new Harness();
        chatFailure.chatFailure = new IllegalStateException("chat");
        MenuOptionClicked first = TravelClicks.cast("Falador Teleport");
        chatFailure.actualShell().handle(first, chatFailure.client, ACTIVE, rules);

        verify(first).consume();
        assertTrue(chatFailure.noticeStore.current().isPresent());
        assertEquals(1, chatFailure.audit.size());
        assertEquals(Collections.singletonList("chat"), chatFailure.diagnostics);

        Harness auditFailure = new Harness();
        auditFailure.auditFailure = new IllegalStateException("audit");
        MenuOptionClicked second = TravelClicks.cast("Falador Teleport");
        auditFailure.actualShell().handle(second, auditFailure.client, ACTIVE, rules);

        verify(second).consume();
        assertTrue(auditFailure.noticeStore.current().isPresent());
        assertEquals(1, auditFailure.chat.size());
        assertEquals(Collections.singletonList("audit"), auditFailure.diagnostics);
    }

    @Test
    public void chatWordingIncludesOnlyVerifiedSuggestions()
    {
        Harness harness = new Harness();
        TravelGuardianCoordinator coordinator = mock(TravelGuardianCoordinator.class);
        TravelAlternative alternative = new TravelAlternative(
            "tablet:varrock-teleport|Break", "Varrock teleport tablet", new CanonicalChunk(50, 53));
        when(coordinator.handle(any(), any(), any(), any(), any()))
            .thenReturn(blockedResult(alternative), blockedResult(null));
        TravelGuardianPluginShell shell = harness.shell(coordinator);

        shell.handle(TravelClicks.item(4251, "Empty", "Ectophial"), harness.client, ACTIVE, rules);
        shell.handle(TravelClicks.item(4251, "Empty", "Ectophial"), harness.client, ACTIVE, rules);

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
        TravelGuardianCoordinator coordinator = mock(TravelGuardianCoordinator.class);
        TravelAlternative alternative = new TravelAlternative(
            "tablet:varrock-teleport|Break", "Varrock teleport tablet", new CanonicalChunk(50, 53));
        when(coordinator.handle(any(), any(), any(), any(), any()))
            .thenReturn(blockedResult(alternative), pausedResult());
        TravelGuardianPluginShell shell = harness.shell(coordinator);

        shell.handle(TravelClicks.item(4251, "Empty", "Ectophial"), harness.client, ACTIVE, rules);
        shell.handle(TravelClicks.item(4251, "Empty", "Ectophial"), harness.client, ACTIVE, rules);

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
    public void optionsTheTableDoesNotListAreNeverConsumed()
    {
        Harness harness = new Harness();
        TravelGuardianPluginShell shell = harness.actualShell();

        for (String option : new String[] {"Drop", "Examine", "Destroy", "Check", "Configure", "Cancel"})
        {
            MenuOptionClicked click = TravelClicks.item(FALADOR_TABLET, option, "Falador teleport");

            assertEquals(option, TravelGuardianPluginShell.Route.NOT_TRAVEL,
                shell.handle(click, harness.client, ACTIVE, rules));
            verify(click, never()).consume();
        }

        assertTrue(harness.chat.isEmpty());
        assertTrue(harness.audit.isEmpty());
        assertFalse(harness.noticeStore.current().isPresent());
    }

    /** Strict Mode blocks travel only (review finding G3): NPCs, objects, banks and gear are left alone. */
    @Test
    public void npcObjectBankAndEquipmentClicksAreNeverConsumed()
    {
        Harness harness = new Harness();
        TravelGuardianPluginShell shell = harness.actualShell();

        for (MenuOptionClicked click : new MenuOptionClicked[] {
            TravelClicks.item(4151, "Wield", "Abyssal whip"),
            TravelClicks.npc(1613, "Bank", "Banker"),
            TravelClicks.npc(3029, "Attack", "Goblin (level-2)"),
            TravelClicks.object(10820, "Chop down", "Oak tree") })
        {
            assertEquals(TravelGuardianPluginShell.Route.NOT_TRAVEL,
                shell.handle(click, harness.client, ACTIVE, rules));
            verify(click, never()).consume();
        }
        assertTrue(harness.chat.isEmpty());
        assertTrue(harness.audit.isEmpty());
    }

    @Test
    public void walkingIsNeverConsumed()
    {
        Harness harness = new Harness();
        TravelGuardianPluginShell shell = harness.actualShell();
        MenuOptionClicked walk = TravelClicks.click(MenuAction.WALK, "Walk here", "");

        assertEquals(TravelGuardianPluginShell.Route.NOT_TRAVEL,
            shell.handle(walk, harness.client, ACTIVE, rules));

        verify(walk, never()).consume();
        assertTrue(harness.chat.isEmpty());
        assertTrue(harness.audit.isEmpty());
        assertFalse(harness.noticeStore.current().isPresent());
    }

    /** A jewellery destination is its own option in the table (G6): Al Kharid is locked, Edgeville allowed. */
    @Test
    public void jewelleryDestinationsFollowTheTrackersDecision()
    {
        Harness harness = new Harness();
        TravelGuardianPluginShell shell = harness.actualShell();

        MenuOptionClicked alKharid = TravelClicks.item(AMULET_OF_GLORY_4, "Al Kharid", "Amulet of glory(4)");
        assertEquals(TravelGuardianPluginShell.Route.EXACT_TRAVEL,
            shell.handle(alKharid, harness.client, ACTIVE, rules));
        verify(alKharid).consume();
        assertEquals("Unlock Al Kharid", harness.noticeStore.current().get().getReason());
        assertEquals("BLOCKED", harness.audit.get(0).getOutcome());
        assertEquals("51,49", harness.audit.get(0).getChunk());

        MenuOptionClicked edgeville = TravelClicks.item(AMULET_OF_GLORY_4, "Edgeville", "Amulet of glory(4)");
        assertEquals(TravelGuardianPluginShell.Route.EXACT_TRAVEL,
            shell.handle(edgeville, harness.client, ACTIVE, rules));
        verify(edgeville, never()).consume();

        assertEquals(1, harness.chat.size());
        assertEquals(1, harness.audit.size());
    }

    /** F7: the notice suggests a trip the player carries that the tracker allows. */
    @Test
    public void aBlockedTripSuggestsACarriedTripTheTrackerAllows()
    {
        Harness harness = new Harness();
        when(harness.availability.hasAnyItem(Collections.singleton(TravelFixtures.LUMBRIDGE_TABLET))).thenReturn(true);

        harness.actualShell().handle(TravelClicks.cast("Falador Teleport"), harness.client, ACTIVE, rules);

        assertEquals("Lumbridge teleport", harness.noticeStore.current().get().getAlternative());
        assertTrue(harness.chat.get(0), harness.chat.get(0).contains("Try Lumbridge teleport instead."));
        assertTrue(harness.audit.get(0).isAlternativeAvailable());
    }

    /** Owner decision 2: a fairy ring is matched and tagged, but blocked only with "Block networks and boats" on. */
    @Test
    public void advisoryTravelIsMatchedButNeverConsumed()
    {
        Harness harness = new Harness();
        MenuOptionClicked zanaris = TravelClicks.object(FAIRY_RING, "Zanaris", "Fairy ring");

        assertEquals(TravelGuardianPluginShell.Route.EXACT_TRAVEL,
            harness.actualShell().handle(zanaris, harness.client, ACTIVE, rules));
        verify(zanaris, never()).consume();
        assertTrue(harness.chat.isEmpty());
        assertTrue(harness.audit.isEmpty());
    }

    /**
     * "Block networks and boats": a left click on a fairy ring's confirm button is the trip to the
     * code its dials show (CKS, which the fixture locks), read as RuneLite gives it.
     */
    @Test
    public void aDialledLockedCodeIsBlockedOnlyWithNetworksBlocked()
    {
        Harness harness = new Harness();
        harness.dials(2, 2, 1);
        TravelGuardianPluginShell shell = harness.actualShell();

        MenuOptionClicked tagged = confirm();
        assertEquals(TravelGuardianPluginShell.Route.EXACT_TRAVEL, shell.handle(tagged, harness.client, ACTIVE, rules));
        verify(tagged, never()).consume();
        assertTrue(harness.audit.isEmpty());

        MenuOptionClicked blocked = confirm();
        assertEquals(TravelGuardianPluginShell.Route.EXACT_TRAVEL,
            shell.handle(blocked, harness.client, ACTIVE.withNetworks(true), rules));
        verify(blocked).consume();
        assertEquals("Strict Mode blocked Fairy ring to CKS", harness.noticeStore.current().get().getHeadline());
        assertTrue(harness.chat.get(0), harness.chat.get(0).startsWith("Strict Mode blocked Fairy ring to CKS:"));
        assertEquals("BLOCKED", harness.audit.get(0).getOutcome());
        assertEquals("53,54", harness.audit.get(0).getChunk());
    }

    /** A confirm in the same tick as a turn may still read the old code: it is let through. */
    @Test
    public void aConfirmRightAfterATurnIsLetThrough()
    {
        Harness harness = new Harness();
        harness.dials(2, 2, 1);
        when(harness.client.getTickCount()).thenReturn(50);
        TravelGuardianPluginShell shell = harness.actualShell();

        MenuOptionClicked turn = TravelClicks.click(MenuAction.CC_OP, "Rotate clockwise", "");
        when(turn.getMenuEntry().getParam1()).thenReturn(InterfaceID.Fairyrings._1_CLOCKWISE);
        when(turn.getMenuEntry().getIdentifier()).thenReturn(1);
        assertEquals(TravelGuardianPluginShell.Route.NOT_TRAVEL,
            shell.handle(turn, harness.client, ACTIVE.withNetworks(true), rules));

        MenuOptionClicked early = confirm();
        assertEquals(TravelGuardianPluginShell.Route.NOT_TRAVEL,
            shell.handle(early, harness.client, ACTIVE.withNetworks(true), rules));
        verify(early, never()).consume();
        verify(turn, never()).consume();
        assertTrue(harness.audit.isEmpty());

        when(harness.client.getTickCount()).thenReturn(52);
        MenuOptionClicked settled = confirm();
        shell.handle(settled, harness.client, ACTIVE.withNetworks(true), rules);
        verify(settled).consume();
    }

    /** The dials' confirm button, its first op, as a CC_OP entry gives it. */
    private static MenuOptionClicked confirm()
    {
        MenuOptionClicked click = TravelClicks.click(MenuAction.CC_OP, "Confirm", "");
        when(click.getMenuEntry().getParam1()).thenReturn(InterfaceID.Fairyrings.CONFIRM);
        when(click.getMenuEntry().getIdentifier()).thenReturn(1);
        return click;
    }

    private static TravelGuardianResult blockedResult(TravelAlternative alternative)
    {
        TravelAction action = exactAction();
        TravelDecision decision = new TravelDecision(
            PermissionStatus.LOCKED, "Teleport to Morytania", "Morytania is locked");
        return new TravelGuardianResult(
            action, decision, alternative,
            new EnforcementPresenter().present(action, decision, alternative),
            true, true, false);
    }

    private static TravelGuardianResult pausedResult()
    {
        TravelAction action = exactAction();
        TravelDecision decision = new TravelDecision(
            PermissionStatus.LOCKED, "Teleport to Morytania", "Morytania is locked");
        return new TravelGuardianResult(
            action, decision, null, null,
            false, false, true);
    }

    private static TravelAction exactAction()
    {
        return new TravelAction("item:ectophial", "Empty", "Teleport to Morytania",
            Collections.singletonList(DESTINATION), false, TravelAction.Confidence.EXACT);
    }

    private static final class Harness
    {
        private final Client client = mock(Client.class);
        private final TravelAvailability availability = mock(TravelAvailability.class);
        private final TravelBlockNoticeStore noticeStore = new TravelBlockNoticeStore(CLOCK);
        private final List<String> chat = new ArrayList<>();
        private final List<StrictModeAuditEntry> audit = new ArrayList<>();
        private final List<String> diagnostics = new ArrayList<>();
        private RuntimeException chatFailure;
        private Exception auditFailure;

        /** The dials' three varbits, as RuneLite's Fairy Rings plugin reads them. */
        private void dials(int left, int middle, int right)
        {
            when(client.getVarbitValue(VarbitID.FAIRYRING_1)).thenReturn(left);
            when(client.getVarbitValue(VarbitID.FAIRYRING_2)).thenReturn(middle);
            when(client.getVarbitValue(VarbitID.FAIRYRING_3)).thenReturn(right);
        }

        private TravelGuardianPluginShell actualShell()
        {
            return shell(new TravelGuardianCoordinator(
                new IntentClassifier(),
                new TravelRuleEvaluator(),
                new TravelAlternativeFinder(),
                noticeStore,
                new StrictModeClickHandler(new StrictModeGuard())));
        }

        private TravelGuardianPluginShell shell(TravelGuardianCoordinator coordinator)
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
