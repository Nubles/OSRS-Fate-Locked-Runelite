package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.MenuFacts;
import com.fatelocked.guardian.StrictModeClickHandler;
import com.fatelocked.guardian.StrictModeGuard;
import com.fatelocked.guardian.StrictModeReadiness;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.fatelocked.guardian.travel.NetworkFixtures.CHARTER_CREW;
import static com.fatelocked.guardian.travel.NetworkFixtures.FAIRY_RING;
import static com.fatelocked.guardian.travel.NetworkFixtures.GLIDER_PILOT;
import static com.fatelocked.guardian.travel.NetworkFixtures.PORT_SARIM_CREW;
import static com.fatelocked.guardian.travel.NetworkFixtures.confirm;
import static com.fatelocked.guardian.travel.TravelFixtures.cast;
import static com.fatelocked.guardian.travel.TravelFixtures.npc;
import static com.fatelocked.guardian.travel.TravelFixtures.object;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Block networks and boats" (off by default): with it on, a network or boat option the
 * tracker's table matches by id, to one place, with a LOCKED decision, is blocked exactly like a
 * teleport, with the same notice, chat line, audit entry and pause. An option that can go to
 * several places never is. With it off, networks and boats are only tagged, as before. A click
 * on a fairy ring's confirm button is the trip to the dialled code, as the table has it.
 */
public class BlockNetworksTest
{
    private final TravelAvailability availability = mock(TravelAvailability.class);
    private final TravelBlockNoticeStore noticeStore = new TravelBlockNoticeStore(
        Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC));
    private final TravelAlternativeFinder finder = mock(TravelAlternativeFinder.class);
    private final TravelGuardianCoordinator coordinator = new TravelGuardianCoordinator(
        new IntentClassifier(), new TravelRuleEvaluator(), finder, noticeStore,
        new StrictModeClickHandler(new StrictModeGuard()));
    private final DecisionService rules = NetworkFixtures.rules();

    public BlockNetworksTest()
    {
        when(finder.find(any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    public void onTheOnePlaceLockedNetworkAndBoatTripsAreBlocked()
    {
        assertBlocked(object(FAIRY_RING, "Zanaris", "Fairy ring"), "Fairy ring to Zanaris", "Needs Fairy Rings");
        assertBlocked(object(FAIRY_RING, "Last-destination (CKS)", "Fairy ring"),
            "Fairy ring to Last-destination (CKS)", "Karamja");
        assertBlocked(npc(CHARTER_CREW, "Charter-to Brimhaven", "Trader Crewmember"),
            "Trader Crewmember to Charter-to Brimhaven", "Unlock Brimhaven");
        assertBlocked(npc(GLIDER_PILOT, "Glider-to Gandius", "Captain Errdo"), "Captain Errdo to Glider-to Gandius",
            "Needs Gnome Gliders");
        assertBlocked(npc(PORT_SARIM_CREW, "The Pandemonium", "Captain Tobias"), "Captain Tobias to The Pandemonium",
            "Unlock The Pandemonium");
    }

    @Test
    public void onSeveralPlacesAndUnlockedTripsAreNeverBlocked()
    {
        assertLetThrough(npc(GLIDER_PILOT, "Glider", "Captain Errdo"), networksOn(), PermissionStatus.UNKNOWN);
        assertLetThrough(npc(CHARTER_CREW, "Charter", "Trader Crewmember"), networksOn(), PermissionStatus.UNKNOWN);
        assertLetThrough(npc(CHARTER_CREW, "Charter-to Port Sarim", "Trader Crewmember"), networksOn(),
            PermissionStatus.ALLOWED);
        assertLetThrough(object(FAIRY_RING, "Last-destination (AIQ)", "Fairy ring"), networksOn(),
            PermissionStatus.ALLOWED);
        assertLetThrough(object(FAIRY_RING, "Last-destination (CIP)", "Fairy ring"), networksOn(),
            PermissionStatus.NOT_READY);
        assertLetThrough(object(FAIRY_RING, "Last-destination (DKR)", "Fairy ring"), networksOn(),
            PermissionStatus.UNKNOWN);
        // Locked, but with no place to land: nothing proves where it goes.
        assertLetThrough(object(FAIRY_RING, "Last-destination (BLQ)", "Fairy ring"), networksOn(),
            PermissionStatus.UNKNOWN);
        assertLetThrough(object(FAIRY_RING, "Configure", "Fairy ring"), networksOn(), null);
    }

    @Test
    public void offNothingChangesFromBefore()
    {
        StrictModeReadiness off = active();
        assertFalse(off.isNetworks());
        assertLetThrough(object(FAIRY_RING, "Zanaris", "Fairy ring"), off, PermissionStatus.LOCKED);
        assertLetThrough(object(FAIRY_RING, "Last-destination (CKS)", "Fairy ring"), off, PermissionStatus.LOCKED);
        assertLetThrough(npc(CHARTER_CREW, "Charter-to Brimhaven", "Trader Crewmember"), off,
            PermissionStatus.LOCKED);
        assertLetThrough(confirm("CKS"), off, PermissionStatus.LOCKED);
        // Teleports are blocked either way.
        assertBlockedWith(cast("Falador Teleport"), off);
        assertBlockedWith(cast("Falador Teleport"), networksOn());
    }

    @Test
    public void aPauseLetsANetworkTripThroughAndRecordsIt()
    {
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        StrictModeReadiness paused = readiness(true, true).withNetworks(true);

        TravelGuardianResult result = coordinator.handle(click, object(FAIRY_RING, "Zanaris", "Fairy ring"), paused,
            rules, availability);

        verify(click, never()).consume();
        assertTrue(result.isWritePausedAudit());
        assertFalse(result.isWriteChat());
        assertFalse(noticeStore.current().isPresent());
    }

    @Test
    public void aDialledCodeIsTheTripToThatCode()
    {
        TravelGuardianResult result = assertBlocked(confirm("CKS"), "Fairy ring to CKS", "Karamja");
        assertEquals("network:fairy-ring", result.getAction().getMethodId());
        assertEquals("code:CKS", result.getAction().getOption());
        assertEquals(new CanonicalChunk(53, 54), result.getAction().getDestination());
        assertTrue(result.getAction().isAdvisory());

        assertLetThrough(confirm("AIQ"), networksOn(), PermissionStatus.ALLOWED);
        assertLetThrough(confirm("CIP"), networksOn(), PermissionStatus.NOT_READY);
        assertLetThrough(confirm("BLQ"), networksOn(), PermissionStatus.UNKNOWN);
    }

    /** A code the table doesn't list, an unread code (the dials may still be turning) and other ops fail open. */
    @Test
    public void aConfirmWithoutAKnownCodeIsNotTravel()
    {
        for (MenuFacts facts : new MenuFacts[] {
            confirm(null), confirm("AIP"), confirm("cks"),
            // Another component of the dials, even with a code on it.
            MenuFacts.builder().kind(MenuFacts.Kind.WIDGET).interfaceGroup(InterfaceID.FAIRYRINGS)
                .componentId(InterfaceID.Fairyrings._1_CLOCKWISE).option("Rotate clockwise").fairyRingCode("CKS")
                .build(),
            // A code on something that isn't the dials is ignored too.
            MenuFacts.builder().kind(MenuFacts.Kind.OBJECT).objectId(FAIRY_RING).option("Use").fairyRingCode("CKS")
                .build()})
        {
            TravelGuardianResult result = assertNotConsumed(facts, networksOn());
            assertEquals(facts.toString(), TravelAction.Confidence.UNKNOWN, result.getAction().getConfidence());
        }
    }

    /** The table is the source: whichever method lists the code, and none when two do. */
    @Test
    public void theCodeIsLookedUpInTheTable()
    {
        IntentClassifier classifier = new IntentClassifier();
        TravelMatch cks = classifier.dialled("CKS", rules.travelTable());
        assertEquals("network:fairy-ring", cks.getMethod().getId());
        assertEquals("Karamja", cks.getOption().getReason());
        assertNull(classifier.dialled("ZZZ", rules.travelTable()));
        assertNull(classifier.dialled(null, rules.travelTable()));
        assertNull(classifier.dialled("CKS", null));
        assertNull("the fixture without networks has no BLQ", classifier.dialled("BLQ",
            TravelFixtures.nubles().travelTable()));
    }

    private TravelGuardianResult assertBlocked(MenuFacts facts, String label, String reason)
    {
        TravelGuardianResult result = assertBlockedWith(facts, networksOn());
        assertEquals(facts.toString(), "Strict Mode blocked " + label, result.getNotice().getHeadline());
        assertEquals(facts.toString(), reason, result.getNotice().getReason());
        assertTrue(result.getNotice().getChatLine().contains("pause Strict Mode for 60 seconds"));
        assertTrue("explained once, like a teleport", result.isWriteChat());
        assertTrue(result.isWriteBlockedAudit());
        return result;
    }

    private TravelGuardianResult assertBlockedWith(MenuFacts facts, StrictModeReadiness readiness)
    {
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        TravelGuardianResult result = coordinator.handle(click, facts, readiness, rules, availability);
        verify(click).consume();
        assertEquals(facts.toString(), PermissionStatus.LOCKED, result.getDecision().getStatus());
        return result;
    }

    private void assertLetThrough(MenuFacts facts, StrictModeReadiness readiness, PermissionStatus status)
    {
        TravelGuardianResult result = assertNotConsumed(facts, readiness);
        if (status == null)
        {
            assertEquals(facts.toString(), TravelAction.Confidence.UNKNOWN, result.getAction().getConfidence());
        }
        else
        {
            assertEquals(facts.toString(), TravelAction.Confidence.EXACT, result.getAction().getConfidence());
            assertEquals(facts.toString(), status, result.getDecision().getStatus());
        }
    }

    private TravelGuardianResult assertNotConsumed(MenuFacts facts, StrictModeReadiness readiness)
    {
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        TravelGuardianResult result = coordinator.handle(click, facts, readiness, rules, availability);
        verify(click, never()).consume();
        assertNull(facts.toString(), result.getNotice());
        assertFalse(result.isWriteBlockedAudit());
        assertFalse(result.isWritePausedAudit());
        return result;
    }

    private static StrictModeReadiness networksOn()
    {
        return active().withNetworks(true);
    }

    private static StrictModeReadiness active()
    {
        return readiness(true, false);
    }

    private static StrictModeReadiness readiness(boolean enabled, boolean paused)
    {
        return StrictModeReadiness.evaluate(enabled, paused, true, true, "Iron Example", "Iron Example", true, true);
    }
}
