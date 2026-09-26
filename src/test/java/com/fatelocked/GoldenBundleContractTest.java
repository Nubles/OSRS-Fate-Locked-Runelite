package com.fatelocked;

import com.fatelocked.rules.FateRuleEngine;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Runs the web app's golden bundles through the real codec and rule engine
 * and compares the answers with the app's own (see
 * src/test/resources/contracts/PINNED for the web commit they came from).
 */
@RunWith(Parameterized.class)
public class GoldenBundleContractTest
{
    static final String DIR = "contracts/golden-bundles/";
    private static final Gson GSON = new Gson();

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> scenarios() throws IOException
    {
        List<Object[]> ids = new ArrayList<>();
        for (JsonElement scenario : json("manifest.json").getAsJsonArray("scenarios"))
        {
            ids.add(new Object[] {scenario.getAsJsonObject().get("id").getAsString()});
        }
        return ids;
    }

    private final String id;
    private final byte[] gzipped;
    private final FateLockedBundle bundle;
    private final JsonObject expected;

    public GoldenBundleContractTest(String id) throws IOException
    {
        this.id = id;
        gzipped = bytes(id + ".bundle.json.gz");
        bundle = FateLockedBundle.loadFromJson(GSON, gunzip(gzipped));
        expected = json(id + ".expect.json");
    }

    @Test
    public void landChunksMatchTheTracker()
    {
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : expected.getAsJsonObject("chunks").entrySet())
        {
            CanonicalChunk chunk = chunk(entry.getKey());
            if (!isLand(chunk)) continue;
            FateLockedBundle.LockState want = entry.getValue().getAsBoolean()
                ? FateLockedBundle.LockState.UNLOCKED : FateLockedBundle.LockState.LOCKED;
            FateLockedBundle.LockState got = bundle.lockStateAt(chunk);
            if (got != want) mismatches.add(entry.getKey() + " want " + want + " got " + got);
        }
        assertEquals(id + " land chunks", List.of(), mismatches);
    }

    /**
     * Ocean and interior chunks have no legacy mapping in the plugin, so it
     * reads them as unauthored whatever the tracker decides (review findings
     * R1 and R4). Stage 2 gives them lock states and must change this test.
     */
    @Test
    public void unmappedChunksReadUnauthoredUntilStage2()
    {
        int unmapped = 0;
        for (String key : expected.getAsJsonObject("chunks").keySet())
        {
            CanonicalChunk chunk = chunk(key);
            if (isLand(chunk)) continue;
            unmapped++;
            assertEquals(id + " " + key, FateLockedBundle.LockState.UNAUTHORED, bundle.lockStateAt(chunk));
        }
        assertTrue(id + " has ocean or interior chunks", unmapped > 0);
    }

    @Test
    public void namedAreasMatchTheTracker()
    {
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : expected.getAsJsonObject("areas").entrySet())
        {
            boolean want = entry.getValue().getAsBoolean();
            if (bundle.isUnlocked(entry.getKey()) != want)
            {
                mismatches.add(entry.getKey() + " want " + want);
            }
        }
        assertEquals(id + " named areas", List.of(), mismatches);
    }

    @Test
    public void banksMatchTheTracker()
    {
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : expected.getAsJsonObject("banks").entrySet())
        {
            int bankId = Integer.parseInt(entry.getKey());
            CanonicalChunk chunk = new CanonicalChunk(bankId / 256, bankId % 256);
            boolean want = entry.getValue().getAsBoolean();
            if (bundle.isBankUnlocked(chunk) != want) mismatches.add(entry.getKey() + " want " + want);
        }
        assertEquals(id + " banks", List.of(), mismatches);
    }

    @Test
    public void frontierMatchesTheTracker()
    {
        if (!expected.has("frontier")) return;
        TreeSet<String> want = new TreeSet<>();
        for (JsonElement key : expected.getAsJsonArray("frontier")) want.add(key.getAsString());
        TreeSet<String> got = new TreeSet<>();
        for (String key : expected.getAsJsonObject("chunks").keySet())
        {
            CanonicalChunk chunk = chunk(key);
            if (isLand(chunk) && bundle.isFrontierChunk(chunk)) got.add(key);
        }
        assertEquals(id + " frontier", want, got);
    }

    @Test
    public void rulesEntriesSurviveTheCodec()
    {
        JsonObject rulesChunks = GSON.fromJson(gunzip(gzipped), JsonObject.class)
            .getAsJsonObject("rules").getAsJsonObject("chunks");
        FateRuleEngine engine = new FateRuleEngine(bundle, true, false);
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : rulesChunks.entrySet())
        {
            String want = entry.getValue().getAsJsonObject().get("entry").getAsString();
            String got = engine.entry(chunk(entry.getKey())).getStatus().name();
            if (!want.equals(got)) mismatches.add(entry.getKey() + " want " + want + " got " + got);
        }
        assertTrue(id + " has v4 rules", rulesChunks.size() > 0);
        assertEquals(id + " v4 entries", List.of(), mismatches);
    }

    @Test
    public void compressedBundleDecodesToTheSameRules()
    {
        FateLockedBundle compressed = FateLockedBundle.loadFromJson(GSON,
            "FLGZ:" + Base64.getEncoder().encodeToString(gzipped));
        for (String key : expected.getAsJsonObject("chunks").keySet())
        {
            CanonicalChunk chunk = chunk(key);
            assertEquals(id + " " + key, bundle.lockStateAt(chunk), compressed.lockStateAt(chunk));
        }
        for (String name : expected.getAsJsonObject("areas").keySet())
        {
            assertEquals(id + " " + name, bundle.isUnlocked(name), compressed.isUnlocked(name));
        }
    }

    /** A chunk the plugin's legacy engine maps to a named area or continent. */
    private boolean isLand(CanonicalChunk chunk)
    {
        return bundle.regionAt(chunk) != null || bundle.subAreaAt(chunk) != null;
    }

    static CanonicalChunk chunk(String key)
    {
        String[] parts = key.split(",");
        return new CanonicalChunk(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    static JsonObject json(String name) throws IOException
    {
        return GSON.fromJson(new String(bytes(name), StandardCharsets.UTF_8), JsonObject.class);
    }

    static byte[] bytes(String name) throws IOException
    {
        try (InputStream in = GoldenBundleContractTest.class.getClassLoader()
            .getResourceAsStream(DIR + name))
        {
            if (in == null) throw new IOException("missing golden file " + name);
            return in.readAllBytes();
        }
    }

    static String gunzip(byte[] gzipped)
    {
        try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(gzipped));
             ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("corrupt golden bundle", ex);
        }
    }
}
