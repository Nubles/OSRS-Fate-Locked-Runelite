package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;
import com.fatelocked.rules.FateRuleEngine;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RuleDecision;
import com.google.gson.Gson;
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
            exact(null), engine("LOCKED", "[\"Fairy Rings\"]", true, false));
        assertEquals(PermissionStatus.LOCKED, destinationLocked.getStatus());
        assertEquals("Lumbridge is locked", destinationLocked.getReason());

        TravelDecision mobilityLocked = evaluator.evaluate(
            exact("Fairy Rings"), engine("ALLOWED", "[]", true, false));
        assertEquals(PermissionStatus.LOCKED, mobilityLocked.getStatus());
        assertEquals("Fairy Rings is not unlocked", mobilityLocked.getReason());

        assertEquals(PermissionStatus.ALLOWED,
            evaluator.evaluate(exact("Fairy Rings"),
                engine("ALLOWED", "[\"Fairy Rings\"]", true, false)).getStatus());
        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(unknown(),
                engine("ALLOWED", "[\"Fairy Rings\"]", true, false)).getStatus());
        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(exact("Unmapped Network"),
                engine("ALLOWED", "[\"Fairy Rings\"]", true, false)).getStatus());
    }

    @Test
    public void destinationUncertaintyTakesPrecedenceOverMobility() throws Exception
    {
        FateRuleEngine lockedMobility = engine("ALLOWED", "[]", true, false);

        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(exactAt(new CanonicalChunk(1, 1), "Fairy Rings"),
                lockedMobility).getStatus());
        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(exact("Fairy Rings"),
                engine("NOT_READY", "[]", true, false)).getStatus());
        assertEquals(PermissionStatus.LOCKED,
            evaluator.evaluate(exact("Unmapped Network"),
                engine("LOCKED", "[]", true, false)).getStatus());
    }

    @Test
    public void notReadyMobilityRemainsUnknown()
    {
        FateRuleEngine notReady = mock(FateRuleEngine.class);
        when(notReady.entry(destination)).thenReturn(
            new RuleDecision(
                PermissionStatus.ALLOWED, "Lumbridge", null));
        when(notReady.mobility("Fairy Rings")).thenReturn(
            new RuleDecision(
                PermissionStatus.NOT_READY, "Fairy Rings", null));

        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(exact("Fairy Rings"), notReady).getStatus());
    }

    @Test
    public void untrustedAndLegacyRulesNeverBecomeLocked() throws Exception    {
        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(exact(null),
                engine("LOCKED", "[]", false, false)).getStatus());
        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(exact(null),
                engine("LOCKED", "[]", true, true)).getStatus());

        FateRuleEngine legacy = new FateRuleEngine(
            fixture("bundles/v3-standard.json"), true, false);
        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(exactAt(new CanonicalChunk(46, 52), null), legacy)
                .getStatus());
    }

    @Test
    public void nullAndUnresolvedActionsRemainUnknown() throws Exception
    {
        FateRuleEngine allowed = engine(
            "ALLOWED", "[\"Fairy Rings\"]", true, false);

        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(null, allowed).getStatus());
        assertEquals(PermissionStatus.UNKNOWN,
            evaluator.evaluate(new TravelAction(
                TravelAction.Family.WALK, "walk", "Walk here", destination,
                null, null, TravelAction.Confidence.EXACT), allowed).getStatus());
        TravelDecision unknown = evaluator.evaluate(unknown(), allowed);
        assertEquals("Unknown travel", unknown.getLabel());
        assertNull(unknown.getReason());
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

    private FateRuleEngine engine(
        String entry, String mobility, boolean accountMatches, boolean stale)
        throws Exception
    {
        String json = fixtureText("bundles/v4-rules.json")
            .replace("\"entry\": \"ALLOWED\"", "\"entry\": \"" + entry + "\"")
            .replace("\"mobility\": [\"Fairy Rings\"]", "\"mobility\": " + mobility);
        return new FateRuleEngine(
            FateLockedBundle.loadFromJson(new Gson(), json), accountMatches, stale);
    }

    private FateLockedBundle fixture(String name) throws Exception
    {
        return FateLockedBundle.loadFromJson(new Gson(), fixtureText(name));
    }

    private String fixtureText(String name) throws Exception
    {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}