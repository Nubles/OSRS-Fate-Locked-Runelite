package com.fatelocked;

import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.TravelTable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * F1: the tracker's travel table, read from rules.travel under its
 * capability. Every golden table reads whole; a malformed method or option
 * is dropped, never the bundle; options that are never travel are dropped
 * whatever the table says; and the table, methods and options have caps.
 */
public class TravelTableTest
{
    private static final Gson GSON = new Gson();
    private static final int AMULET_OF_GLORY_4 = 1712;
    private static final int FAIRY_RING = 29495;
    /** One of the Port Sarim ship's crew. */
    private static final int PORT_SARIM_CREW = 14978;

    /** Every method in every golden, with every option's destinations and decision, and each pinned answer. */
    @Test
    public void everyGoldenTableReadsWhole() throws Exception
    {
        for (Object[] scenario : GoldenBundleContractTest.scenarios())
        {
            String id = (String) scenario[0];
            String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
            TravelTable table = FateLockedBundle.loadFromJson(GSON, json).getRules().getTravel();
            JsonObject wire = GSON.fromJson(json, JsonObject.class).getAsJsonObject("rules").getAsJsonObject("travel");
            assertNotNull(id, table);
            assertEquals(id, wire.size(), table.methods().size());
            List<String> mismatches = new ArrayList<>();
            for (Map.Entry<String, JsonElement> row : wire.entrySet())
            {
                TravelTable.Method method = table.method(row.getKey());
                JsonObject want = row.getValue().getAsJsonObject();
                if (method == null || !method.getLabel().equals(want.get("label").getAsString())
                    || method.isAdvisory() != (want.has("advisory") && want.get("advisory").getAsBoolean()))
                {
                    mismatches.add(row.getKey());
                    continue;
                }
                sameOptions(row.getKey(), want.getAsJsonObject("options"), method.getOptions(), mismatches);
                if (want.has("codes")) sameOptions(row.getKey(), want.getAsJsonObject("codes"), method.getCodes(), mismatches);
            }
            assertEquals(id, List.of(), mismatches);

            JsonObject answers = GoldenBundleContractTest.json(id + ".expect.json").getAsJsonObject("travel");
            assertTrue(id, answers.size() >= 30);
            for (Map.Entry<String, JsonElement> answer : answers.entrySet())
            {
                String[] key = answer.getKey().split("\\|", 2);
                String option = key[1].startsWith("code:") ? "Last-destination (" + key[1].substring(5) + ")" : key[1];
                assertEquals(answer.getKey(), answer.getValue().getAsString(),
                    table.method(key[0]).option(option).getStatus().name());
            }
        }
    }

    private static void sameOptions(String method, JsonObject want, Map<String, TravelTable.Option> got, List<String> mismatches)
    {
        if (want.size() != got.size()) mismatches.add(method + " has " + got.size() + " of " + want.size());
        for (Map.Entry<String, JsonElement> entry : want.entrySet())
        {
            TravelTable.Option option = got.get(entry.getKey());
            JsonObject row = entry.getValue().getAsJsonObject();
            List<CanonicalChunk> to = new ArrayList<>();
            for (JsonElement key : row.getAsJsonArray("to")) to.add(GoldenBundleContractTest.chunk(key.getAsString()));
            String reason = row.has("reason") ? row.get("reason").getAsString() : null;
            if (option == null || !option.getTo().equals(to)
                || !option.getStatus().name().equals(row.get("status").getAsString())
                || !String.valueOf(reason).equals(String.valueOf(option.getReason())))
            {
                mismatches.add(method + "|" + entry.getKey());
            }
        }
    }

    /** Each match kind finds only its own methods: the spellbook and name, or an id of its kind. */
    @Test
    public void aMethodIsFoundByItsSpellOrAnIdOfItsKind() throws Exception
    {
        TravelTable table = golden("vanilla-interiors");

        assertEquals(List.of("item:amulet-of-glory"), ids(table.byId(TravelTable.Match.ITEMS, AMULET_OF_GLORY_4)));
        assertEquals(List.of("network:fairy-ring"), ids(table.byId(TravelTable.Match.OBJECTS, FAIRY_RING)));
        assertEquals(List.of("boat:port-sarim-ship"), ids(table.byId(TravelTable.Match.NPCS, PORT_SARIM_CREW)));
        assertEquals("an object's id isn't an item's", List.of(), ids(table.byId(TravelTable.Match.ITEMS, FAIRY_RING)));
        assertEquals(List.of(), ids(table.byId(TravelTable.Match.NPCS, AMULET_OF_GLORY_4)));

        // Two spells share a name; the spellbook tells them apart (G7).
        assertEquals(List.of("spell:arceuus:ape-atoll-teleport"), ids(table.spell("arceuus", "ape atoll teleport")));
        assertEquals(List.of("spell:standard:ape-atoll-teleport"), ids(table.spell("standard", "Ape Atoll Teleport")));
        assertEquals(List.of(), ids(table.spell("lunar", "Ape Atoll Teleport")));
        assertEquals(List.of(), ids(table.spell(null, "Ape Atoll Teleport")));
        TravelTable.Method arceuus = table.method("spell:arceuus:ape-atoll-teleport");
        assertEquals(TravelTable.Match.SPELL, arceuus.getMatch());
        assertEquals("Ape Atoll Teleport", arceuus.getSpell());
        assertEquals(new CanonicalChunk(43, 142), arceuus.option("Cast").destination());
    }

    /** An option is its exact text, ignoring case; a fairy ring's Last-destination is its code's. */
    @Test
    public void anOptionIsItsTextAndALastDestinationItsCode() throws Exception
    {
        TravelTable table = golden("vanilla-interiors");
        TravelTable.Method glory = table.method("item:amulet-of-glory");

        assertEquals("Edgeville", glory.option("edgeville").getText());
        assertEquals(new CanonicalChunk(48, 54), glory.option(" Edgeville ").destination());
        assertNull("Rub picks the place after the click", glory.option("Rub").destination());
        assertEquals(4, glory.option("Rub").getTo().size());
        assertNull(glory.option("Edgeville Teleport"));
        assertNull(glory.option(null));

        TravelTable.Method fairyRing = table.method("network:fairy-ring");
        assertTrue(fairyRing.isAdvisory());
        assertEquals("code:CKS", fairyRing.option("Last-destination (cks)").getText());
        assertEquals(new CanonicalChunk(53, 54), fairyRing.option("Last-destination (CKS)").destination());
        assertNull("not a code", fairyRing.option("Last-destination (XYZ)"));
        assertEquals("Zanaris", fairyRing.option("Zanaris").getText());

        TravelTable.Method spiritTree = table.method("network:spirit-tree");
        assertEquals("a Last-destination with no codes is its own option",
            "Last-destination", spiritTree.option("Last-destination").getText());
        assertNull(spiritTree.option("Last-destination (CKS)"));
    }

    /** A malformed method is dropped and the rest are kept. */
    @Test
    public void aBadMethodIsDroppedNeverTheTable()
    {
        JsonObject rows = new JsonObject();
        rows.add("good", row("Good", "{\"items\": [1, 2.5, -3, \"4\", 7]}", options("Teleport", "\"1,1\"", "ALLOWED")));
        rows.add("no label", row(null, "{\"items\": [1]}", options("Teleport", "\"1,1\"", "ALLOWED")));
        rows.add("blank label", row(" ", "{\"items\": [1]}", options("Teleport", "\"1,1\"", "ALLOWED")));
        rows.add("two kinds", row("Two", "{\"items\": [1], \"objects\": [2]}", options("Teleport", "\"1,1\"", "ALLOWED")));
        rows.add("no kind", row("None", "{\"places\": [1]}", options("Teleport", "\"1,1\"", "ALLOWED")));
        rows.add("unknown book", row("Book", "{\"spell\": {\"book\": \"necromancy\", \"name\": \"Raise\"}}",
            options("Cast", "\"1,1\"", "ALLOWED")));
        rows.add("unnamed spell", row("Spell", "{\"spell\": {\"book\": \"lunar\"}}", options("Cast", "\"1,1\"", "ALLOWED")));
        rows.add("spell not an object", row("Spell", "{\"spell\": \"Moonclan Teleport\"}", options("Cast", "\"1,1\"", "ALLOWED")));
        rows.add("no ids", row("Ids", "{\"npcs\": []}", options("Travel", "\"1,1\"", "ALLOWED")));
        rows.add("no good ids", row("Ids", "{\"npcs\": [\"a\", -1]}", options("Travel", "\"1,1\"", "ALLOWED")));
        rows.add("too many ids", row("Ids", "{\"objects\": " + numbers(TravelTable.MAX_IDS + 1) + "}",
            options("Travel", "\"1,1\"", "ALLOWED")));
        rows.add("most ids", row("Ids", "{\"objects\": " + numbers(TravelTable.MAX_IDS) + "}",
            options("Travel", "\"1,1\"", "ALLOWED")));
        rows.add("no options", row("Options", "{\"items\": [1]}", new JsonObject()));
        rows.add("only never-travel options", row("Options", "{\"items\": [1]}", options("Wear", "\"1,1\"", "LOCKED")));
        JsonObject tooMany = new JsonObject();
        for (int i = 0; i <= TravelTable.MAX_OPTIONS; i++) tooMany.add("Option " + i, option("\"1,1\"", "ALLOWED"));
        rows.add("too many options", row("Options", "{\"items\": [1]}", tooMany));
        rows.add("not an object", new JsonPrimitive("a method"));
        rows.add(" ", row("Blank id", "{\"items\": [1]}", options("Teleport", "\"1,1\"", "ALLOWED")));

        TravelTable table = TravelTable.parse(rows);

        assertEquals(List.of("good", "most ids"), ids(new ArrayList<>(table.methods())));
        assertEquals(new HashSet<>(Arrays.asList(1, 7)), table.method("good").getIds());
        assertEquals(TravelTable.MAX_IDS, table.method("most ids").getIds().size());
        assertEquals(List.of("good"), ids(table.byId(TravelTable.Match.ITEMS, 7)));
        assertNull(TravelTable.parse(new JsonPrimitive("a table")));
        assertNull(TravelTable.parse(new JsonArray()));
        assertNull(TravelTable.parse(null));
    }

    /** A malformed option is dropped, and so is one that is never travel; the rest of its method stays. */
    @Test
    public void aBadOptionIsDroppedAndTheMethodKept()
    {
        JsonObject options = new JsonObject();
        options.add("Edgeville", option("\"48,54\"", "ALLOWED"));
        options.add("Rub", option("\"48,54\", \"not a key\"", "UNKNOWN"));
        options.add("Karamja", option("\"45,-49\"", "LOCKED"));
        options.add("Draynor Village", option("\"48,50\"", null));
        options.add("Al Kharid", GSON.fromJson("{\"to\": \"51,49\", \"status\": \"LOCKED\"}", JsonObject.class));
        options.add("Walk here", option("\"50,50\"", "LOCKED"));
        options.add("attack", option("\"50,50\"", "LOCKED"));
        options.add("Talk-to", option("\"50,50\"", "LOCKED"));
        options.add("Teleport", option("\"50,50\"", "A_NEWER_STATUS"));
        options.add("teleport", option("\"50,51\"", "LOCKED"));
        options.add("Nowhere", option("", "UNKNOWN"));
        JsonObject tooFar = option(String.join(", ", keys(TravelTable.MAX_DESTINATIONS + 1)), "UNKNOWN");
        options.add("Everywhere", tooFar);
        options.add("Farthest", option(String.join(", ", keys(TravelTable.MAX_DESTINATIONS)), "UNKNOWN"));
        options.add("Twice", option("\"50,50\", \"50,50\"", "LOCKED"));
        JsonObject wordy = option("\"50,50\"", "LOCKED");
        wordy.addProperty("reason", "x".repeat(200));
        options.add("Wordy", wordy);
        JsonObject rows = new JsonObject();
        rows.add("item:glory", row("Amulet of glory", "{\"items\": [1712]}", options));

        TravelTable.Method glory = TravelTable.parse(rows).method("item:glory");

        assertEquals(List.of("Edgeville", "Teleport", "Nowhere", "Farthest", "Twice", "Wordy"),
            new ArrayList<>(glory.getOptions().keySet()));
        assertEquals("a status this build doesn't know reads as Unknown",
            PermissionStatus.UNKNOWN, glory.option("Teleport").getStatus());
        assertEquals("the first of two texts that differ in case", new CanonicalChunk(50, 50),
            glory.option("TELEPORT").destination());
        assertNull(glory.option("Nowhere").destination());
        assertEquals(TravelTable.MAX_DESTINATIONS, glory.option("Farthest").getTo().size());
        assertEquals("one place, however often it's named", new CanonicalChunk(50, 50), glory.option("Twice").destination());
        assertEquals(TravelTable.MAX_TEXT, glory.option("Wordy").getReason().length());
        assertTrue(glory.option("Wordy").getReason().endsWith("…"));
        assertNull(glory.option("Walk here"));
    }

    /** Codes are a fairy ring's three dials; the table says plainly when a method isn't advisory. */
    @Test
    public void codesAreDialsAndAdvisoryIsTheSafeDefault()
    {
        JsonObject codes = new JsonObject();
        codes.add("CKS", option("\"54,54\"", "LOCKED"));
        codes.add("cks", option("\"54,54\"", "LOCKED"));
        codes.add("XYZ", option("\"1,1\"", "LOCKED"));
        codes.add("AIQR", option("\"1,1\"", "LOCKED"));
        JsonObject ring = row("Fairy ring", "{\"objects\": [29495]}", options("Zanaris", "\"37,69\"", "LOCKED"));
        ring.add("codes", codes);
        JsonObject rows = new JsonObject();
        rows.add("ring", ring);
        JsonObject onlyCodes = row("Codes only", "{\"objects\": [29560]}", new JsonObject());
        onlyCodes.add("codes", codes);
        rows.add("only codes", onlyCodes);
        for (String advisory : Arrays.asList("true", "false", "\"no\"", "0", "null"))
        {
            JsonObject method = row("Advisory " + advisory, "{\"items\": [1]}", options("Teleport", "\"1,1\"", "LOCKED"));
            method.add("advisory", GSON.fromJson(advisory, JsonElement.class));
            rows.add("advisory " + advisory, method);
        }
        rows.add("advisory absent", row("Absent", "{\"items\": [1]}", options("Teleport", "\"1,1\"", "LOCKED")));
        rows.add("no codes", row("No codes", "{\"items\": [2]}", options("Last-destination (ABC)", "\"1,1\"", "LOCKED")));

        TravelTable table = TravelTable.parse(rows);

        assertEquals(List.of("CKS"), new ArrayList<>(table.method("ring").getCodes().keySet()));
        assertNotNull("a method may have only codes", table.method("only codes"));
        assertEquals("without codes, the text is the option's own", "Last-destination (ABC)",
            table.method("no codes").option("last-destination (abc)").getText());
        assertTrue(table.method("advisory true").isAdvisory());
        assertFalse(table.method("advisory false").isAdvisory());
        assertTrue("anything but a plain false is advisory", table.method("advisory \"no\"").isAdvisory());
        assertTrue(table.method("advisory 0").isAdvisory());
        assertFalse(table.method("advisory null").isAdvisory());
        assertFalse(table.method("advisory absent").isAdvisory());
    }

    /** At most a thousand methods, the first in the table's order. */
    @Test
    public void theTableKeepsItsFirstThousandMethods()
    {
        JsonObject rows = new JsonObject();
        for (int i = 0; i <= TravelTable.MAX_METHODS; i++)
        {
            rows.add("item:" + i, row("Item " + i, "{\"items\": [" + i + "]}", options("Teleport", "\"1,1\"", "LOCKED")));
        }
        TravelTable table = TravelTable.parse(rows);

        assertEquals(TravelTable.MAX_METHODS, table.methods().size());
        assertNotNull(table.method("item:" + (TravelTable.MAX_METHODS - 1)));
        assertNull(table.method("item:" + TravelTable.MAX_METHODS));
    }

    /** Read only under the travel capability, and only as an object. */
    @Test
    public void theTableNeedsItsCapability() throws Exception
    {
        JsonObject wire = GSON.fromJson(
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")), JsonObject.class);
        JsonArray capabilities = new JsonArray();
        for (JsonElement capability : wire.getAsJsonObject("rules").getAsJsonArray("capabilities"))
        {
            if (!"travel".equals(capability.getAsString())) capabilities.add(capability);
        }
        JsonObject without = wire.deepCopy();
        without.getAsJsonObject("rules").add("capabilities", capabilities);
        assertNull(FateLockedBundle.loadFromJson(GSON, without.toString()).getRules().getTravel());

        JsonObject notATable = wire.deepCopy();
        notATable.getAsJsonObject("rules").addProperty("travel", "a table");
        assertNull(FateLockedBundle.loadFromJson(GSON, notATable.toString()).getRules().getTravel());
    }

    private static TravelTable golden(String id) throws Exception
    {
        return FateLockedBundle.loadFromJson(GSON,
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"))).getRules().getTravel();
    }

    private static List<String> ids(List<TravelTable.Method> methods)
    {
        return methods.stream().map(TravelTable.Method::getId).collect(Collectors.toList());
    }

    private static JsonObject row(String label, String match, JsonObject options)
    {
        JsonObject row = new JsonObject();
        if (label != null) row.addProperty("label", label);
        row.add("unlocks", new JsonArray());
        row.add("match", GSON.fromJson(match, JsonObject.class));
        row.add("options", options);
        return row;
    }

    private static JsonObject options(String text, String to, String status)
    {
        JsonObject options = new JsonObject();
        options.add(text, option(to, status));
        return options;
    }

    private static JsonObject option(String to, String status)
    {
        JsonObject option = GSON.fromJson("{\"to\": [" + to + "]}", JsonObject.class);
        if (status != null) option.addProperty("status", status);
        return option;
    }

    private static String numbers(int count)
    {
        Set<String> numbers = new java.util.LinkedHashSet<>();
        for (int i = 0; i < count; i++) numbers.add(String.valueOf(i));
        return "[" + String.join(", ", numbers) + "]";
    }

    private static List<String> keys(int count)
    {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) keys.add("\"" + i + ",1\"");
        return keys;
    }
}
