package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.awt.Color;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B7: the scene and minimap tints, from the decision service. Over every
 * golden bundle each chunk tints as the tracker decides it (NOT_READY as
 * unlocked), the sea the rules don't cover and another character's rules
 * tint as unknown, and an older export tints as before.
 */
public class TintPolicyTest
{
    private static final Gson GSON = new Gson();

    @Test
    public void everyGoldenChunkTintsAsTheTrackerDecides() throws Exception
    {
        for (Object[] scenario : GoldenBundleContractTest.scenarios())
        {
            String id = (String) scenario[0];
            String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
            JsonObject wire = GSON.fromJson(json, JsonObject.class);
            JsonObject chunks = wire.getAsJsonObject("rules").getAsJsonObject("chunks");
            JsonElement bound = wire.getAsJsonObject("rules").get("account");
            String account = bound == null || bound.isJsonNull() ? null : AccountBinding.normalize(bound.getAsString());
            RulesSnapshot rules = RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json));
            DecisionService playing = DecisionService.create(rules, account, account);
            DecisionService other = DecisionService.create(rules, account, "someone else");

            List<String> mismatches = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : chunks.entrySet())
            {
                CanonicalChunk chunk = GoldenBundleContractTest.chunk(entry.getKey());
                TintPolicy.Tint want = tint(entry.getValue().getAsJsonObject().get("entry").getAsString());
                TintPolicy.Tint got = TintPolicy.at(playing, chunk);
                boolean otherQuiet = account == null || TintPolicy.at(other, chunk) == TintPolicy.Tint.UNKNOWN;
                if (want != got || !otherQuiet) mismatches.add(entry.getKey() + " want " + want + " got " + got);
            }
            // The sea without a snapshot tints as the rules' chunk entries say (R1).
            JsonObject entries = GoldenBundleContractTest.json(id + ".expect.json").getAsJsonObject("entries");
            for (String key : GoldenBundleContractTest.json(id + ".expect.json").getAsJsonObject("chunks").keySet())
            {
                if (chunks.has(key)) continue;
                TintPolicy.Tint want = tint(entries.get(key).getAsString());
                TintPolicy.Tint sea = TintPolicy.at(playing, GoldenBundleContractTest.chunk(key));
                if (sea != want) mismatches.add(key + " is not in the rules' chunks and tints " + sea + ", not " + want);
            }
            assertTrue(id, chunks.size() > 600);
            assertEquals(id, List.of(), mismatches);
        }
    }

    @Test
    public void anOlderExportTintsAsBefore() throws Exception
    {
        FateLockedBundle v3;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("bundles/v3-standard.json"))
        {
            v3 = FateLockedBundle.loadFromJson(GSON, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        DecisionService nubles = DecisionService.create(RulesSnapshot.of(v3), "nubles", "nubles");

        assertEquals(TintPolicy.Tint.UNLOCKED, TintPolicy.at(nubles, new CanonicalChunk(46, 52)));
        assertEquals(TintPolicy.Tint.UNLOCKED, TintPolicy.at(nubles, new CanonicalChunk(50, 50)));
        assertEquals(TintPolicy.Tint.UNKNOWN, TintPolicy.at(nubles, new CanonicalChunk(1, 1)));
        assertEquals(TintPolicy.Tint.UNKNOWN,
            TintPolicy.at(DecisionService.create(RulesSnapshot.of(v3), "nubles", "zezima"), new CanonicalChunk(46, 52)));
    }

    @Test
    public void colorsFollowTheSettings()
    {
        FateLockedConfig config = mock(FateLockedConfig.class);
        when(config.unlockedColor()).thenReturn(Color.GREEN);
        when(config.lockedColor()).thenReturn(Color.RED);

        assertEquals(Color.GREEN, TintPolicy.color(TintPolicy.Tint.UNLOCKED, config));
        assertEquals(Color.RED, TintPolicy.color(TintPolicy.Tint.LOCKED, config));
        assertEquals("a place the rules don't decide isn't tinted", null,
            TintPolicy.color(TintPolicy.Tint.UNKNOWN, config));
    }

    /** Until B17's boundary test covers every surface: both overlays tint through TintPolicy. */
    @Test
    public void theSceneAndMinimapTintThroughThePolicy() throws Exception
    {
        for (String overlay : new String[] { "FateLockedSceneOverlay.java", "FateLockedMinimapOverlay.java" })
        {
            String source = new String(Files.readAllBytes(
                Paths.get("src", "main", "java", "com", "fatelocked", overlay)), StandardCharsets.UTF_8);
            assertFalse(overlay, source.contains("lockStateAt("));
            assertTrue(overlay, source.contains("TintPolicy."));
        }
    }

    private static TintPolicy.Tint tint(String entry)
    {
        switch (entry)
        {
            case "ALLOWED":
            case "NOT_READY":
                return TintPolicy.Tint.UNLOCKED;
            case "LOCKED":
                return TintPolicy.Tint.LOCKED;
            default:
                return TintPolicy.Tint.UNKNOWN;
        }
    }
}
