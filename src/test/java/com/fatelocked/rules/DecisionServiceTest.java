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
import static org.junit.Assert.assertTrue;
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

    /** The fixture as a Chunked run that owns only the start chunk, with these Stage 2 fields in its rules. */
    private static DecisionService chunked(JsonElement capabilities, JsonElement frontier) throws Exception
    {
        JsonObject root = new Gson().fromJson(fixtureText("bundles/v4-rules.json"), JsonObject.class);
        JsonObject rules = root.getAsJsonObject("rules");
        rules.addProperty("gameModeId", "chunked");
        if (capabilities != null) rules.add("capabilities", capabilities);
        if (frontier != null) rules.add("frontier", frontier);
        return playing(root.toString(), "nubles");
    }

    private static JsonArray strings(String... values)
    {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        return array;
    }

    @Test
    public void theFrontierIsTheTrackersWhenTheRulesNameIt() throws Exception
    {
        JsonArray frontier = strings("60,60", "not a key", "50,51");
        frontier.add(7);
        DecisionService rules = chunked(strings("someFutureSection", "frontier"), frontier);

        // Across the sea, as only the tracker knows; the bad items are skipped.
        assertTrue(rules.isFrontier(new CanonicalChunk(60, 60)));
        assertTrue(rules.isFrontier(new CanonicalChunk(50, 51)));
        // Next to the start chunk, but not on the tracker's frontier.
        assertFalse(rules.isFrontier(new CanonicalChunk(49, 50)));
    }

    @Test
    public void withoutItsCapabilityOrAListTheFrontierIsTheOwnedChunksNeighbours() throws Exception
    {
        JsonObject notAList = new JsonObject();
        for (DecisionService rules : Arrays.asList(
            chunked(null, strings("60,60")),
            chunked(strings("banks"), strings("60,60")),
            chunked(new JsonPrimitive("frontier"), strings("60,60")),
            chunked(strings("frontier"), notAList)))
        {
            assertTrue(rules.isFrontier(new CanonicalChunk(49, 50)));
            assertFalse(rules.isFrontier(new CanonicalChunk(60, 60)));
        }
    }

    /** The fixture with chunkEntries and places in its rules, and these capabilities (none when null). */
    private static DecisionService withPlaces(JsonElement entries, JsonElement places, JsonArray capabilities)
        throws Exception
    {
        JsonObject root = new Gson().fromJson(fixtureText("bundles/v4-rules.json"), JsonObject.class);
        JsonObject rules = root.getAsJsonObject("rules");
        rules.add("chunkEntries", entries);
        rules.add("places", places);
        if (capabilities != null) rules.add("capabilities", capabilities);
        return playing(root.toString(), "nubles");
    }

    private static DecisionService withPlaces(JsonElement entries, JsonElement places, boolean named) throws Exception
    {
        return withPlaces(entries, places, named ? strings("chunkEntries", "places") : null);
    }

    private static JsonObject place(String kind, String name, String area)
    {
        JsonObject place = new JsonObject();
        place.addProperty("kind", kind);
        if (name != null) place.addProperty("name", name);
        if (area != null) place.addProperty("area", area);
        return place;
    }

    @Test
    public void theOceanAndInteriorsReadTheTrackersEntries() throws Exception
    {
        JsonObject entries = new JsonObject();
        entries.addProperty("45,45", "LOCKED");
        entries.addProperty("48,150", "ALLOWED");
        entries.addProperty("48,151", "NOT_READY");
        entries.addProperty("48,152", "A_NEWER_STATUS");
        entries.addProperty("48,153", 3);
        JsonObject places = new JsonObject();
        places.add("45,45", place("ocean", null, null));
        places.add("48,150", place("interior", "Mor Ul Rek · Outer Area", "Mor Ul Rek (TzHaar City)"));
        places.add("48,151", place("interior", null, "Keldagrim"));
        places.add("48,152", new JsonPrimitive("not a place"));
        DecisionService rules = withPlaces(entries, places, true);

        assertEquals(new Decision(PermissionStatus.LOCKED, "Ocean", null, Decision.Source.CHUNK),
            rules.chunk(new CanonicalChunk(45, 45)));
        assertEquals(new Decision(PermissionStatus.ALLOWED, "Mor Ul Rek · Outer Area", null, Decision.Source.CHUNK),
            rules.chunk(new CanonicalChunk(48, 150)));
        assertEquals(new Decision(PermissionStatus.NOT_READY, "Keldagrim", null, Decision.Source.CHUNK),
            rules.chunk(new CanonicalChunk(48, 151)));
        // A status from a newer tracker reads UNKNOWN; a value that isn't one leaves the chunk unmapped.
        assertEquals(new Decision(PermissionStatus.UNKNOWN, null, null, Decision.Source.CHUNK),
            rules.chunk(new CanonicalChunk(48, 152)));
        assertFalse(rules.chunk(new CanonicalChunk(48, 153)).isAuthored());
        // Land keeps its snapshot's answer.
        assertEquals(PermissionStatus.ALLOWED, rules.chunk(LUMBRIDGE).getStatus());
    }

    @Test
    public void withoutTheirCapabilitiesOrAsAnythingButObjectsThePlacesAreUnmapped() throws Exception
    {
        JsonObject entries = new JsonObject();
        entries.addProperty("45,45", "LOCKED");
        JsonObject places = new JsonObject();
        places.add("45,45", place("ocean", null, null));
        for (DecisionService rules : Arrays.asList(
            withPlaces(entries, places, false),
            withPlaces(strings("45,45"), places, true),
            withPlaces(new JsonPrimitive("LOCKED"), places, true)))
        {
            assertFalse(rules.chunk(new CanonicalChunk(45, 45)).isAuthored());
        }
        // Entries without places, or without the places capability, still decide, unnamed.
        assertEquals(new Decision(PermissionStatus.LOCKED, null, null, Decision.Source.CHUNK),
            withPlaces(entries, new JsonArray(), true).chunk(new CanonicalChunk(45, 45)));
        assertEquals(new Decision(PermissionStatus.LOCKED, null, null, Decision.Source.CHUNK),
            withPlaces(entries, places, strings("chunkEntries")).chunk(new CanonicalChunk(45, 45)));
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
