package com.fatelocked;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.fatelocked.detection.DetectionTables;
import com.fatelocked.rules.ChunkPermissionSnapshot;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RulesSnapshot;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FateLockedBundleTest
{
    private FateLockedBundle fixture(String name) throws Exception
    {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name))
        {
            assertNotNull("missing fixture " + name, in);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return FateLockedBundle.loadFromJson(new Gson(), json);
        }
    }

    @Test
    public void legacyBundleStillUsesContinentUnlocks() throws Exception
    {
        FateLockedBundle bundle = fixture("bundles/v1-legacy.json");

        assertEquals(FateLockedBundle.LockState.UNLOCKED,
            bundle.lockStateAt(new CanonicalChunk(46, 52)));
    }

    @Test
    public void standardBundleUsesSubAreaAndBankState() throws Exception
    {
        FateLockedBundle bundle = fixture("bundles/v3-standard.json");

        assertEquals(FateLockedBundle.LockState.UNLOCKED,
            bundle.lockStateAt(new CanonicalChunk(46, 52)));
        assertTrue(bundle.isBankUnlocked(new CanonicalChunk(46, 52)));
        assertEquals(FateLockedBundle.LockState.UNLOCKED,
            bundle.lockStateAt(new CanonicalChunk(50, 50)));
    }

    @Test
    public void emptyChunkedBundleStillUnlocksTheStartChunk() throws Exception
    {
        FateLockedBundle bundle = fixture("bundles/v3-chunked-empty.json");

        assertTrue(bundle.isChunkedBundle());
        assertEquals(FateLockedBundle.LockState.UNLOCKED,
            bundle.lockStateAt(FateLockedBundle.CHUNKED_START));
    }

    @Test
    public void parsesV4PermissionRows() throws Exception
    {
        FateLockedBundle bundle = fixture("bundles/v4-rules.json");
        ChunkPermissionSnapshot chunk = bundle
            .permissionsAt(new CanonicalChunk(50, 50)).get();

        assertEquals(PermissionStatus.ALLOWED, chunk.getEntry());
        assertEquals("Lumbridge General Store",
            chunk.getCategories().get("SHOPS").get(0).getName());
        assertTrue(!bundle.isLegacyRules());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFutureBundle() throws Exception
    {
        fixture("bundles/v5-future.json");
    }
    /** The web app's compressed form: "FLGZ:" + base64 of the gzip bytes. */
    private static String compressed(byte[] json) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes))
        {
            gzip.write(json);
        }
        return "FLGZ:" + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static byte[] fixtureBytes(String name) throws Exception
    {
        try (InputStream in = FateLockedBundleTest.class.getClassLoader().getResourceAsStream(name))
        {
            assertNotNull("missing fixture " + name, in);
            return in.readAllBytes();
        }
    }

    private static byte[] spaces(int count)
    {
        byte[] json = new byte[count];
        Arrays.fill(json, (byte) ' ');
        return json;
    }

    @Test
    public void readsTheCompressedFormLikePlainJson() throws Exception
    {
        FateLockedBundle bundle = FateLockedBundle.loadFromJson(new Gson(),
            compressed(fixtureBytes("bundles/v3-standard.json")));

        assertEquals(FateLockedBundle.LockState.UNLOCKED,
            bundle.lockStateAt(new CanonicalChunk(46, 52)));
        assertTrue(bundle.isBankUnlocked(new CanonicalChunk(46, 52)));
    }

    @Test
    public void acceptsACompressedBundleUpToTheInflatedLimit() throws Exception
    {
        // A real bundle padded with trailing whitespace to exactly the limit.
        byte[] json = fixtureBytes("bundles/v3-standard.json");
        byte[] padded = Arrays.copyOf(json, FateLockedBundle.MAX_INFLATED_BYTES);
        Arrays.fill(padded, json.length, padded.length, (byte) ' ');

        FateLockedBundle bundle = FateLockedBundle.loadFromJson(new Gson(), compressed(padded));

        assertEquals(FateLockedBundle.LockState.UNLOCKED,
            bundle.lockStateAt(new CanonicalChunk(46, 52)));
    }

    @Test
    public void refusesTextThatIsNotABundle()
    {
        // Each of these used to parse as an empty bundle and wipe the rules.
        for (String notABundle : new String[] {
            null, "", "   ", "null", "{}", "{\"version\":3}", "{\"version\":3,\"chunks\":{}}",
            "{\"profile\":\"x\"}" })
        {
            try
            {
                FateLockedBundle.loadFromJson(new Gson(), notABundle);
                fail("expected " + notABundle + " to be refused");
            }
            catch (JsonSyntaxException expected)
            {
                assertTrue(expected.getMessage(), expected.getMessage().contains("no chunk data"));
            }
        }
    }

    @Test
    public void readsBundleFilesAsUtf8() throws Exception
    {
        String json = new String(fixtureBytes("bundles/v3-standard.json"), StandardCharsets.UTF_8)
            .replaceFirst("\\{", "{\"profileName\":\"Mid·Run\",");
        java.nio.file.Path file = java.nio.file.Files.createTempFile("fate-locked-bundle", ".json");
        try
        {
            java.nio.file.Files.write(file, json.getBytes(StandardCharsets.UTF_8));
            assertEquals("Mid·Run",
                FateLockedBundle.loadFromFile(new Gson(), file).getProfileName());
        }
        finally
        {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    @Test
    public void exposesWhenTheTrackerExportedTheRules() throws Exception
    {
        assertEquals(java.time.Instant.parse("2026-07-24T10:00:00Z"),
            fixture("bundles/v4-rules.json").exportedAt());
        assertEquals(null, fixture("bundles/v3-standard.json").exportedAt());

        com.google.gson.JsonObject root = new Gson().fromJson(
            new String(fixtureBytes("bundles/v4-rules.json"), StandardCharsets.UTF_8),
            com.google.gson.JsonObject.class);
        root.getAsJsonObject("rules").addProperty("exportedAt", "yesterday");
        assertEquals(null, FateLockedBundle.loadFromJson(new Gson(), root.toString()).exportedAt());
    }

    @Test
    public void refusesACompressedBundleThatInflatesPastTheLimit() throws Exception
    {
        // A few KiB of gzip that would inflate one byte past the limit.
        String payload = compressed(spaces(FateLockedBundle.MAX_INFLATED_BYTES + 1));
        assertTrue("payload should be small", payload.length() < 64 * 1024);

        try
        {
            FateLockedBundle.loadFromJson(new Gson(), payload);
            fail("expected an oversized bundle to be refused");
        }
        catch (JsonSyntaxException expected)
        {
            assertTrue(expected.getMessage(), expected.getMessage().contains("larger than 8 MiB"));
        }
    }

    /**
     * E7 (R6): Stage 2 rules name the mode's free areas under their
     * capability, ahead of the root copy; the items that aren't names are
     * skipped, and an empty list frees nothing.
     */
    @Test
    public void theRulesNameTheFreeAreasUnderTheirCapability() throws Exception
    {
        JsonArray named = strings("Tutorial Island");
        named.add(7);
        FateLockedBundle tracker = withFreeAreas(strings("someFutureSection", "freeAreas"), named);
        assertTrue(tracker.isUnlocked("Tutorial Island"));
        assertFalse("the root copy is the older one", tracker.isUnlocked("Karamja"));

        FateLockedBundle none = withFreeAreas(strings("freeAreas"), new JsonArray());
        assertFalse(none.isUnlocked("Tutorial Island"));
        assertFalse(none.isUnlocked("Karamja"));
    }

    /** Without the capability, or as anything but a list, the root copy still decides. */
    @Test
    public void olderRulesKeepTheRootFreeAreas() throws Exception
    {
        for (FateLockedBundle older : Arrays.asList(
            withFreeAreas(null, strings("Tutorial Island")),
            withFreeAreas(strings("banks"), strings("Tutorial Island")),
            withFreeAreas(new JsonPrimitive("freeAreas"), strings("Tutorial Island")),
            withFreeAreas(strings("freeAreas"), new JsonPrimitive("Tutorial Island")),
            withFreeAreas(strings("freeAreas"), null)))
        {
            assertTrue(older.isUnlocked("Karamja"));
            assertFalse(older.isUnlocked("Tutorial Island"));
        }
    }

    /**
     * Stage 4: the tracker's names for what RuneLite notices come under their capability, and
     * only as an object; the detectors notice no boss or quest without them.
     */
    @Test
    public void theRulesNameWhatRuneLiteNoticesUnderTheirCapability() throws Exception
    {
        JsonObject detection = new Gson().fromJson("{\"bosses\":[{\"key\":\"Vorkath\",\"raid\":false,"
            + "\"killCounts\":[\"Vorkath\"]}],\"quests\":[{\"id\":\"Cook's Assistant\",\"name\":\"Cook's Assistant\"}],"
            + "\"diaryTiers\":[\"Varrock Easy\"]}", JsonObject.class);

        DetectionTables tables = withDetection(strings("banks", "detection"), detection).getRules().getDetection();
        assertEquals("Vorkath", tables.bossForKillCount("Vorkath").getKey());
        assertEquals("Cook's Assistant", tables.questId("Cook's Assistant"));
        assertTrue(tables.isDiaryTier("Varrock Easy"));
        assertFalse(tables.isDiaryTier("Varrock Hard"));

        assertNull(withDetection(null, detection).getRules().getDetection());
        assertNull(withDetection(strings("banks"), detection).getRules().getDetection());
        assertNull(withDetection(strings("detection"), new JsonPrimitive("detection")).getRules().getDetection());
        assertNull(withDetection(strings("detection"), null).getRules().getDetection());
    }

    /**
     * E7 (R15): rulesVersion is informational. A newer tracker's rules, with
     * a later version and a section this plugin doesn't know, give the same
     * answers everywhere the golden pins one.
     */
    @Test
    public void aLaterRulesVersionAndAnUnknownSectionKeepTheAnswers() throws Exception
    {
        Gson gson = new Gson();
        JsonObject wire = gson.fromJson(GoldenBundleContractTest.gunzip(
            GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")), JsonObject.class);
        FateLockedBundle today = FateLockedBundle.loadFromJson(gson, wire.toString());
        JsonObject rules = wire.getAsJsonObject("rules");
        rules.addProperty("rulesVersion", "2");
        rules.getAsJsonArray("capabilities").add("someFutureSection");
        rules.add("someFutureSection", gson.fromJson("{\"50,50\": \"LOCKED\"}", JsonObject.class));
        FateLockedBundle newer = FateLockedBundle.loadFromJson(gson, wire.toString());

        assertEquals("1", today.getRulesVersion());
        assertEquals("2", newer.getRulesVersion());
        DecisionService before = DecisionService.create(RulesSnapshot.of(today), null, null);
        DecisionService after = DecisionService.create(RulesSnapshot.of(newer), null, null);
        JsonObject expected = GoldenBundleContractTest.json("vanilla-mid.expect.json");
        assertTrue(expected.getAsJsonObject("chunks").size() > 100);
        for (String key : expected.getAsJsonObject("chunks").keySet())
        {
            CanonicalChunk chunk = GoldenBundleContractTest.chunk(key);
            assertEquals(key, today.lockStateAt(chunk), newer.lockStateAt(chunk));
            assertEquals(key, before.chunk(chunk), after.chunk(chunk));
        }
        for (String area : expected.getAsJsonObject("areas").keySet())
        {
            assertEquals(area, today.isUnlocked(area), newer.isUnlocked(area));
        }
        assertEquals(PermissionStatus.ALLOWED, after.chunk(new CanonicalChunk(50, 50)).getStatus());
    }

    /** The v4 fixture with root freeAreas [Karamja], and these capabilities and rules freeAreas (none when null). */
    private FateLockedBundle withFreeAreas(JsonElement capabilities, JsonElement named) throws Exception
    {
        JsonObject root = new Gson().fromJson(
            new String(fixtureBytes("bundles/v4-rules.json"), StandardCharsets.UTF_8), JsonObject.class);
        root.add("freeAreas", strings("Karamja"));
        JsonObject rules = root.getAsJsonObject("rules");
        if (capabilities != null) rules.add("capabilities", capabilities);
        if (named != null) rules.add("freeAreas", named);
        return FateLockedBundle.loadFromJson(new Gson(), root.toString());
    }

    /** The v4 fixture with these capabilities and this detection section (none when null). */
    private FateLockedBundle withDetection(JsonElement capabilities, JsonElement detection) throws Exception
    {
        JsonObject root = new Gson().fromJson(
            new String(fixtureBytes("bundles/v4-rules.json"), StandardCharsets.UTF_8), JsonObject.class);
        JsonObject rules = root.getAsJsonObject("rules");
        if (capabilities != null) rules.add("capabilities", capabilities);
        if (detection != null) rules.add("detection", detection);
        return FateLockedBundle.loadFromJson(new Gson(), root.toString());
    }

    private static JsonArray strings(String... values)
    {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        return array;
    }
}
