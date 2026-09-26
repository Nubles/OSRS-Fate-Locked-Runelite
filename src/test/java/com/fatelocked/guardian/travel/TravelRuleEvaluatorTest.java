package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;
import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TravelRuleEvaluatorTest
{
    private final TravelRuleEvaluator evaluator = new TravelRuleEvaluator();
    private final CanonicalChunk destination = new CanonicalChunk(50, 50);

    @Test
    public void onlyExactAuthoredLocksRemainLocked() throws Exception
    {
        TravelDecision destinationLocked = evaluator.evaluate(
            exact(null), rules("LOCKED", "[\"Fairy Rings\"]", true));
        assertEquals(PermissionStatus.LOCKED, destinationLocked.getStatus());
        assertEquals("Lumbridge is locked", destinationLocked.getReason());

        TravelDecision mobilityLocked = evaluator.evaluate(
            exact("Fairy Rings"), rules("ALLOWED", "[]", true));
        assertEquals(PermissionStatus.LOCKED, mobilityLocked.getStatus());
        assertEquals("Fairy Rings is not unlocked", mobilityLocked.getReason());

        assertEquals(PermissionStatus.ALLOWED,
            evaluator.evaluate(exact("Fairy Rings"),
                rules("ALLOWED", "[\"Fairy Rings\"]", true)).getStatus());
        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(unknown(),
                rules("ALLOWED", "[\"Fairy Rings\"]", true)).getStatus());
        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(exact("Unmapped Network"),
                rules("ALLOWED", "[\"Fairy Rings\"]", true)).getStatus());
    }

    @Test
    public void destinationUncertaintyTakesPrecedenceOverMobility() throws Exception
    {
        DecisionService lockedMobility = rules("ALLOWED", "[]", true);

        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(exactAt(new CanonicalChunk(1, 1), "Fairy Rings"),
                lockedMobility).getStatus());
        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(exact("Fairy Rings"),
                rules("NOT_READY", "[]", true)).getStatus());
        assertEquals(PermissionStatus.LOCKED,
            evaluator.evaluate(exact("Unmapped Network"),
                rules("LOCKED", "[]", true)).getStatus());
    }

    @Test
    public void notReadyMobilityRemainsUnknown()
    {
        DecisionService notReady = mock(DecisionService.class);
        when(notReady.chunk(destination)).thenReturn(
            new Decision(PermissionStatus.ALLOWED, "Lumbridge", null, Decision.Source.CHUNK));
        when(notReady.mobility("Fairy Rings")).thenReturn(
            new Decision(PermissionStatus.NOT_READY, "Fairy Rings", null, Decision.Source.MOBILITY));

        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(exact("Fairy Rings"), notReady).getStatus());
    }

    /** Only the tracker's own chunk decisions count; an older export's never do. */
    @Test
    public void untrustedAndLegacyRulesNeverBecomeLocked() throws Exception
    {
        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(exact(null),
                rules("LOCKED", "[]", false)).getStatus());

        // The v3 fixture with Seers' Village added and not unlocked.
        JsonObject v3 = new Gson().fromJson(fixtureText("bundles/v3-standard.json"), JsonObject.class);
        JsonArray seers = new Gson().fromJson("[{\"cx\":42,\"cy\":54}]", JsonArray.class);
        v3.getAsJsonObject("chunks").add("Kandarin", seers);
        v3.getAsJsonObject("subAreaChunks").add("Seers' Village", seers.deepCopy());
        v3.getAsJsonObject("regionGroups").add("Kandarin",
            new Gson().fromJson("[\"Seers' Village\"]", JsonArray.class));
        DecisionService legacy = DecisionService.create(
            RulesSnapshot.of(FateLockedBundle.loadFromJson(new Gson(), v3.toString())), "nubles", "nubles");
        CanonicalChunk seersVillage = new CanonicalChunk(42, 54);
        assertEquals("the older export locks it", PermissionStatus.LOCKED, legacy.chunk(seersVillage).getStatus());
        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(exactAt(seersVillage, null), legacy).getStatus());
    }

    @Test
    public void nullAndUnresolvedActionsRemainUnknown() throws Exception
    {
        DecisionService allowed = rules("ALLOWED", "[\"Fairy Rings\"]", true);

        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(null, allowed).getStatus());
        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(new TravelAction(
                TravelAction.Family.WALK, "walk", "Walk here", destination,
                null, null, TravelAction.Confidence.EXACT), allowed).getStatus());
        TravelDecision unknown = evaluator.evaluate(unknown(), allowed);
        assertEquals("Unknown travel", unknown.getLabel());
        assertNull(unknown.getReason());
        assertEquals(PermissionStatus.UNKNOWN, evaluator.evaluate(exact(null), null).getStatus());
    }

    @Test
    public void unresolvedNamedMethodsRemainUnknownEvenWhenTheDestinationWouldLock()
        throws Exception
    {
        DecisionService locked = rules("LOCKED", "[\"Fairy Rings\"]", true);
        TravelAction unresolved = new TravelAction(
            TravelAction.Family.SPELL_OR_ITEM, "named-teleport",
            "Teleport New destination", new CanonicalChunk(49, 50), null,
            null, TravelAction.Confidence.UNKNOWN);

        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(unresolved, locked).getStatus());
    }

    private TravelAction exact(String requiredUnlock)
    {
        return exactAt(destination, requiredUnlock);
    }

    private TravelAction exactAt(CanonicalChunk chunk, String requiredUnlock)
    {
        return new TravelAction(
            TravelAction.Family.WALK, "walk", "Walk here",
            new CanonicalChunk(49, 50), chunk, requiredUnlock,
            TravelAction.Confidence.EXACT);
    }

    private TravelAction unknown()
    {
        return new TravelAction(
            TravelAction.Family.UNKNOWN, "unknown", "Unknown travel",
            new CanonicalChunk(49, 50), null, null,
            TravelAction.Confidence.UNKNOWN);
    }

    /** The v4 fixture (bound to Nubles) with this entry and rolled mobility, for Nubles or someone else. */
    private DecisionService rules(String entry, String mobility, boolean boundCharacter)
        throws Exception
    {
        String json = fixtureText("bundles/v4-rules.json")
            .replace("\"entry\": \"ALLOWED\"", "\"entry\": \"" + entry + "\"")
            .replace("\"mobility\": [\"Fairy Rings\"]", "\"mobility\": " + mobility);
        return DecisionService.create(
            RulesSnapshot.of(FateLockedBundle.loadFromJson(new Gson(), json)),
            "nubles", boundCharacter ? "nubles" : "zezima");
    }

    private String fixtureText(String name) throws Exception
    {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
