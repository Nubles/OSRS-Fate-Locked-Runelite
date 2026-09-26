package com.fatelocked.rules;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * The decision service on the small v4 fixture (bound to Nubles). These are
 * the cases FateRuleEngineTest pinned before B4 deleted that engine; the
 * golden bundles cover the rest in DecisionServiceGoldenTest.
 */
public class DecisionServiceTest
{
    private static final CanonicalChunk LUMBRIDGE = new CanonicalChunk(50, 50);

    private static String fixtureText(String name) throws Exception
    {
        try (InputStream in = DecisionServiceTest.class.getClassLoader().getResourceAsStream(name))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static DecisionService playing(String json, String player)
    {
        return DecisionService.create(
            RulesSnapshot.of(FateLockedBundle.loadFromJson(new Gson(), json)), "nubles", player);
    }

    private static DecisionService nubles() throws Exception
    {
        return playing(fixtureText("bundles/v4-rules.json"), "nubles");
    }

    @Test
    public void preservesAuthoredStatusesAndMatchesNamesCaseInsensitively() throws Exception
    {
        DecisionService rules = nubles();

        assertEquals(PermissionStatus.ALLOWED, rules.chunk(LUMBRIDGE).getStatus());
        assertEquals(PermissionStatus.ALLOWED,
            rules.target(LUMBRIDGE, "shop", "LUMBRIDGE GENERAL STORE").getStatus());
        assertEquals(PermissionStatus.NOT_READY,
            rules.target(LUMBRIDGE, null, "Cook's Assistant").getStatus());
        assertEquals(PermissionStatus.LOCKED, rules.target(LUMBRIDGE, "NPC", "Goblin").getStatus());
        assertEquals(PermissionStatus.UNKNOWN, rules.target(LUMBRIDGE, "OBJECT", "Unmapped").getStatus());
        assertNull(rules.target(LUMBRIDGE, "OBJECT", "Unmapped").getReason());
        assertEquals(PermissionStatus.LOCKED, rules.item(4151).getStatus());
        assertEquals(PermissionStatus.UNKNOWN, rules.item(999999).getStatus());
    }

    @Test
    public void evaluatesOnlyAppAuthoredMobilityNames() throws Exception
    {
        DecisionService rules = nubles();

        assertEquals(PermissionStatus.ALLOWED, rules.mobility("Fairy Rings").getStatus());
        assertEquals(PermissionStatus.ALLOWED, rules.mobility("  fairy   rings ").getStatus());
        assertEquals(PermissionStatus.LOCKED, rules.mobility("Spirit Trees").getStatus());
        assertEquals(PermissionStatus.UNKNOWN,
            playing(fixtureText("bundles/v4-rules.json"), "zezima").mobility("Fairy Rings").getStatus());
        assertEquals(PermissionStatus.UNKNOWN, rules.mobility("Unmapped Network").getStatus());

        JsonObject withoutKnownNames = new Gson().fromJson(fixtureText("bundles/v4-rules.json"), JsonObject.class);
        withoutKnownNames.getAsJsonObject("rules").remove("knownMobility");
        assertEquals(PermissionStatus.UNKNOWN,
            playing(withoutKnownNames.toString(), "nubles").mobility("Spirit Trees").getStatus());
    }

    @Test
    public void malformedKnownMobilityShapesBecomeEmptyAndUnknown() throws Exception
    {
        JsonArray mixed = new JsonArray();
        mixed.add("Fairy Rings");
        mixed.add(7);

        for (JsonElement malformed : Arrays.asList(
            new JsonObject(), new JsonPrimitive("Fairy Rings"), new JsonPrimitive(7), mixed))
        {
            JsonObject root = new Gson().fromJson(fixtureText("bundles/v4-rules.json"), JsonObject.class);
            root.getAsJsonObject("rules").add("knownMobility", malformed);

            FateLockedBundle bundle = FateLockedBundle.loadFromJson(new Gson(), root.toString());
            assertEquals(Collections.emptyList(), bundle.getRules().getKnownMobility());
            try
            {
                bundle.getRules().getKnownMobility().add("Spirit Trees");
                fail("normalized known mobility must be immutable");
            }
            catch (UnsupportedOperationException expected)
            {
                // Expected: parsed authority cannot be mutated after validation.
            }
            assertEquals(PermissionStatus.UNKNOWN,
                playing(root.toString(), "nubles").mobility("Fairy Rings").getStatus());
        }
    }

    @Test
    public void anotherCharacterGetsNoRowsNorLocks() throws Exception
    {
        DecisionService zezima = playing(fixtureText("bundles/v4-rules.json"), "zezima");

        assertEquals(PermissionStatus.UNKNOWN,
            zezima.target(LUMBRIDGE, "SHOP", "Lumbridge General Store").getStatus());
        assertEquals(PermissionStatus.UNKNOWN, zezima.chunk(LUMBRIDGE).getStatus());
        assertFalse(zezima.details(LUMBRIDGE).isPresent());
        assertEquals("Wrong account", zezima.trustReason());
        // Names aren't decisions.
        assertEquals("Lumbridge", zezima.chunkName(LUMBRIDGE));
    }

    @Test
    public void unauthoredChunksAndOlderExportRowsRemainUnknown() throws Exception
    {
        DecisionService legacy = playing(fixtureText("bundles/v3-standard.json"), "nubles");
        CanonicalChunk falador = new CanonicalChunk(46, 52);
        assertEquals(PermissionStatus.UNKNOWN, legacy.target(falador, "BANK", "").getStatus());
        assertEquals(Decision.Source.LEGACY, legacy.chunk(falador).getSource());

        assertEquals(PermissionStatus.UNKNOWN, nubles().chunk(new CanonicalChunk(1, 1)).getStatus());
        assertEquals(Decision.Source.UNMAPPED, nubles().chunk(new CanonicalChunk(1, 1)).getSource());
    }
}
