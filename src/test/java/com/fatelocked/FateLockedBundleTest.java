package com.fatelocked;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.fatelocked.rules.ChunkPermissionSnapshot;
import com.fatelocked.rules.PermissionStatus;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
}
