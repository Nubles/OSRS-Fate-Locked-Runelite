package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * B8: the world map, from the decision service. It draws the tracker's land
 * (every chunk the rules decide, the sea left out) in its colours, the
 * frontier of a Chunked run exactly as the tracker's golden frontier, and a
 * tooltip; nothing at all on another character.
 */
public class WorldMapTooltipTest
{
    private static final Gson GSON = new Gson();

    @Test
    public void theMapDrawsTheTrackersLandAndFrontierOnEveryGolden() throws Exception
    {
        for (Object[] scenario : GoldenBundleContractTest.scenarios())
        {
            String id = (String) scenario[0];
            String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
            JsonObject wire = GSON.fromJson(json, JsonObject.class);
            JsonObject chunks = wire.getAsJsonObject("rules").getAsJsonObject("chunks");
            JsonObject expected = GoldenBundleContractTest.json(id + ".expect.json");
            RulesSnapshot rules = RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json));
            String account = account(wire);
            DecisionService playing = DecisionService.create(rules, account, account);

            Set<String> drawn = new TreeSet<>();
            Set<String> frontier = new TreeSet<>();
            List<String> mismatches = new ArrayList<>();
            for (CanonicalChunk chunk : playing.mappedChunks())
            {
                String key = chunk.getCx() + "," + chunk.getCy();
                drawn.add(key);
                WorldMapChunks.Fill fill = WorldMapChunks.fill(playing, chunk);
                if (fill == WorldMapChunks.Fill.FRONTIER) frontier.add(key);
                String entry = chunks.getAsJsonObject(key).get("entry").getAsString();
                boolean owned = "ALLOWED".equals(entry) || "NOT_READY".equals(entry);
                if (owned != (fill == WorldMapChunks.Fill.UNLOCKED)) mismatches.add(key + " " + entry + " drawn " + fill);
            }
            Set<String> wantFrontier = new TreeSet<>();
            if (expected.has("frontier"))
            {
                for (JsonElement key : expected.getAsJsonArray("frontier")) wantFrontier.add(key.getAsString());
            }

            assertEquals(id + " draws the tracker's land", new TreeSet<>(chunks.keySet()), drawn);
            assertEquals(id + " colours", List.of(), mismatches);
            assertEquals(id + " frontier", wantFrontier, frontier);
            for (String key : expected.getAsJsonObject("chunks").keySet())
            {
                if (!chunks.has(key)) assertFalse(id + " sea " + key, drawn.contains(key));
            }
            if (account != null)
            {
                DecisionService other = DecisionService.create(rules, account, "someone else");
                for (CanonicalChunk chunk : other.mappedChunks())
                {
                    assertNull(id + " another character", WorldMapChunks.fill(other, chunk));
                }
            }
        }
        assertFalse("a Chunked golden has a frontier", frontierOf("chunked-walk").isEmpty());
    }

    @Test
    public void tooltipsSayWhereAndWhat() throws Exception
    {
        DecisionService mid = playing("vanilla-mid");

        assertEquals("Lumbridge · Misthalin</br><col=2ee59d>Unlocked</col>",
            WorldMapChunks.tooltip(mid, new CanonicalChunk(50, 50), List.of()));
        assertTrue(WorldMapChunks.tooltip(mid, new CanonicalChunk(42, 54), List.of())
            .endsWith("</br><col=ef4444>Locked</col>"));
        assertTrue(WorldMapChunks.tooltip(mid, new CanonicalChunk(39, 53), List.of())
            .endsWith("</br><col=f59e0b>Not ready</col>"));
        assertEquals("a chunk only the tracker names",
            "Chunk (16, 44)</br><col=ef4444>Locked</col>",
            WorldMapChunks.tooltip(mid, new CanonicalChunk(16, 44), List.of()));
        assertNull("the sea", WorldMapChunks.tooltip(mid, new CanonicalChunk(40, 41), List.of()));
        assertEquals("Lumbridge · Misthalin</br><col=2ee59d>Unlocked</col>"
                + "</br><col=a8a8a8>Monsters: Cow</col>",
            WorldMapChunks.tooltip(mid, new CanonicalChunk(50, 50), List.of("Monsters: Cow")));

        CanonicalChunk next = frontierOf("chunked-walk").iterator().next();
        assertTrue(WorldMapChunks.tooltip(playing("chunked-walk"), next, List.of())
            .endsWith("</br><col=f59e0b>Locked — rollable next</col>"));
    }

    @Test
    public void anotherCharacterGetsNoTooltip() throws Exception
    {
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz"));
        DecisionService other = DecisionService.create(
            RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json)), "iron example", "someone else");

        assertNull(WorldMapChunks.tooltip(other, new CanonicalChunk(50, 50), List.of()));

        String walk = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("chunked-walk.bundle.json.gz"));
        RulesSnapshot walkRules = RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, walk));
        CanonicalChunk next = frontierOf("chunked-walk").iterator().next();
        assertTrue(DecisionService.create(walkRules, "iron example", "iron example").isFrontier(next));
        assertFalse(DecisionService.create(walkRules, "iron example", "someone else").isFrontier(next));
    }

    @Test
    public void anOlderExportDrawsItsAreasAsBefore() throws Exception
    {
        FateLockedBundle v3;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("bundles/v3-standard.json"))
        {
            v3 = FateLockedBundle.loadFromJson(GSON, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        DecisionService nubles = DecisionService.create(RulesSnapshot.of(v3), "nubles", "nubles");

        assertEquals(Set.of(new CanonicalChunk(50, 50), new CanonicalChunk(46, 52)), nubles.mappedChunks());
        assertEquals(WorldMapChunks.Fill.UNLOCKED, WorldMapChunks.fill(nubles, new CanonicalChunk(46, 52)));
        assertEquals("Falador · Asgarnia</br><col=2ee59d>Unlocked</col>",
            WorldMapChunks.tooltip(nubles, new CanonicalChunk(46, 52), List.of()));
    }

    /** Until B17's boundary test covers every surface: the overlay draws through WorldMapChunks. */
    @Test
    public void theOverlayDrawsThroughWorldMapChunks() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src", "main", "java", "com", "fatelocked", "FateLockedWorldMapOverlay.java")), StandardCharsets.UTF_8);
        for (String old : new String[] { "lockStateAt(", "isFrontierChunk(", "labelAt(", "getRegionChunks(" })
        {
            assertFalse(old, source.contains(old));
        }
        assertTrue(source.contains("WorldMapChunks.fill("));
        assertTrue(source.contains("WorldMapChunks.tooltip("));
    }

    private static Set<CanonicalChunk> frontierOf(String id) throws Exception
    {
        Set<CanonicalChunk> frontier = new java.util.LinkedHashSet<>();
        for (JsonElement key : GoldenBundleContractTest.json(id + ".expect.json").getAsJsonArray("frontier"))
        {
            frontier.add(GoldenBundleContractTest.chunk(key.getAsString()));
        }
        return frontier;
    }

    private static DecisionService playing(String id) throws Exception
    {
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
        String account = account(GSON.fromJson(json, JsonObject.class));
        return DecisionService.create(RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json)), account, account);
    }

    private static String account(JsonObject wire)
    {
        JsonElement bound = wire.getAsJsonObject("rules").get("account");
        return bound == null || bound.isJsonNull() ? null : AccountBinding.normalize(bound.getAsString());
    }
}
