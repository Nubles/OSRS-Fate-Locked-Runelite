package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
 * B13 (R5, part one): the HUD's nearest bank and shop come from the
 * tracker's BANKS and SHOPS rows. All 127 banks are candidates, including
 * the 19 the old points of interest missed, and a bank or shop the tracker
 * doesn't allow is never chosen.
 */
public class NearestBankTest
{
    private static final Gson GSON = new Gson();
    /** Bank chunks the old points-of-interest list never found (the review's R5 probe). */
    private static final Set<String> POI_MISSED = new TreeSet<>(List.of(
        "21,51", "22,46", "23,51", "25,48", "34,47", "38,50", "40,35", "41,57", "42,58", "43,43",
        "43,48", "43,54", "45,58", "47,53", "52,42", "53,50", "55,52", "58,59", "59,47"));

    /** vanilla-mid with every bank row and its chunk allowed: each bank is its own nearest. */
    @Test
    public void everyBankTheTrackerListsIsACandidate() throws Exception
    {
        JsonObject wire = wire("vanilla-mid");
        List<String> banks = new ArrayList<>();
        for (Map.Entry<String, JsonElement> chunk : wire.getAsJsonObject("rules").getAsJsonObject("chunks").entrySet())
        {
            JsonObject snapshot = chunk.getValue().getAsJsonObject();
            JsonArray rows = snapshot.getAsJsonObject("categories").getAsJsonArray("BANKS");
            if (rows == null) continue;
            banks.add(chunk.getKey());
            snapshot.addProperty("entry", "ALLOWED");
            for (JsonElement row : rows) row.getAsJsonObject().addProperty("status", "ALLOWED");
        }
        DecisionService allBanks = trusted(wire);

        List<String> notFound = new ArrayList<>();
        for (String key : banks)
        {
            FateLockedBundle.Nearest near = allBanks.nearestBank(GoldenBundleContractTest.chunk(key));
            if (near == null || near.getDistanceChunks() != 0) notFound.add(key);
        }
        assertEquals(127, banks.size());
        assertTrue(banks.containsAll(POI_MISSED));
        assertEquals(List.of(), notFound);
    }

    /** Every golden bundle: the chosen bank and shop are allowed and nothing allowed is closer. */
    @Test
    public void onlyAllowedBanksAndShopsAreChosenAndTheyAreTheNearest() throws Exception
    {
        int usableBanks = 0;
        for (Object[] scenario : GoldenBundleContractTest.scenarios())
        {
            String id = (String) scenario[0];
            JsonObject wire = wire(id);
            JsonObject chunks = wire.getAsJsonObject("rules").getAsJsonObject("chunks");
            DecisionService service = trusted(wire);
            List<CanonicalChunk> banks = usable(chunks, "BANKS");
            List<CanonicalChunk> shops = usable(chunks, "SHOPS");
            usableBanks += banks.size();
            for (String key : chunks.keySet())
            {
                CanonicalChunk from = GoldenBundleContractTest.chunk(key);
                assertEquals(id + " bank from " + key, nearest(from, banks), service.nearestBank(from));
                assertEquals(id + " shop from " + key, nearest(from, shops), service.nearestShop(from));
            }
        }
        assertTrue("some golden has a usable bank", usableBanks > 0);
    }

    /** The HUD's lines: custom-lumbridge-banks-off allows the banks at Lumbridge Castle and South Draynor. */
    @Test
    public void theHudShowsTheNearestAllowedBank() throws Exception
    {
        FateLockedBundle rules = FateLockedBundle.loadFromJson(GSON, wire("custom-lumbridge-banks-off").toString());
        FateLockedPlugin plugin = org.mockito.Mockito.mock(FateLockedPlugin.class);
        org.mockito.Mockito.when(plugin.getBundle()).thenReturn(rules);
        org.mockito.Mockito.when(plugin.decisions()).thenReturn(
            DecisionService.create(RulesSnapshot.of(rules), "iron example", "iron example"));
        FateLockedConfig config = org.mockito.Mockito.mock(FateLockedConfig.class);
        org.mockito.Mockito.when(config.showHud()).thenReturn(true);
        org.mockito.Mockito.when(config.showNearest()).thenReturn(true);
        net.runelite.api.Client client = org.mockito.Mockito.mock(net.runelite.api.Client.class);
        net.runelite.api.Player player = org.mockito.Mockito.mock(net.runelite.api.Player.class);
        org.mockito.Mockito.when(player.getName()).thenReturn("Iron Example");
        org.mockito.Mockito.when(client.getLocalPlayer()).thenReturn(player);
        FateLockedHudOverlay hud = new FateLockedHudOverlay(client, plugin, config);
        hud.setClearChildren(false);

        org.mockito.Mockito.when(player.getWorldLocation()).thenReturn(new net.runelite.api.coords.WorldPoint(48 * 64 + 5, 50 * 64 + 5, 0));
        Map<String, String> atDraynor = HudStatusTest.drawn(hud);
        assertEquals("here ✓", atDraynor.get("Bank"));
        assertEquals("none unlocked", atDraynor.get("Shop"));
        hud.getPanelComponent().getChildren().clear();

        org.mockito.Mockito.when(player.getWorldLocation()).thenReturn(new net.runelite.api.coords.WorldPoint(46 * 64 + 5, 50 * 64 + 5, 0));
        assertEquals("Draynor Vill… · 2 E", HudStatusTest.drawn(hud).get("Bank"));
        hud.getPanelComponent().getChildren().clear();

        // The tracker locks South Draynor's chunk, bank row allowed or not: Lumbridge is nearest.
        JsonObject locked = wire("custom-lumbridge-banks-off");
        locked.getAsJsonObject("rules").getAsJsonObject("chunks").getAsJsonObject("48,50").addProperty("entry", "LOCKED");
        FateLockedBundle lockedRules = FateLockedBundle.loadFromJson(GSON, locked.toString());
        org.mockito.Mockito.when(plugin.decisions()).thenReturn(
            DecisionService.create(RulesSnapshot.of(lockedRules), "iron example", "iron example"));
        org.mockito.Mockito.when(player.getWorldLocation()).thenReturn(new net.runelite.api.coords.WorldPoint(48 * 64 + 5, 50 * 64 + 5, 0));
        assertEquals("Lumbridge · 2 E", HudStatusTest.drawn(hud).get("Bank"));
    }

    @Test
    public void anotherCharacterGetsNoNearestLine() throws Exception
    {
        JsonObject wire = wire("custom-lumbridge-banks-off");
        DecisionService other = DecisionService.create(
            RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, wire.toString())), "iron example", "someone else");

        assertFalse(other.hasNearestData());
        assertNull(other.nearestBank(new CanonicalChunk(48, 50)));
        assertNull(other.nearestShop(new CanonicalChunk(48, 50)));
    }

    @Test
    public void anOlderExportFindsBanksAsBefore() throws Exception
    {
        JsonObject v3;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("bundles/v3-standard.json"))
        {
            v3 = GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
        }
        v3.add("chunkContent", GSON.fromJson(
            "{\"46,52\":{\"poi\":[\"Falador west bank\"],\"shop\":[\"Falador General Store\"]}}", JsonObject.class));
        FateLockedBundle legacy = FateLockedBundle.loadFromJson(GSON, v3.toString());
        DecisionService nubles = DecisionService.create(RulesSnapshot.of(legacy), "nubles", "nubles");
        CanonicalChunk lumbridge = new CanonicalChunk(50, 50);

        assertTrue(nubles.hasNearestData());
        assertEquals(legacy.nearestUsableBank(lumbridge), nubles.nearestBank(lumbridge));
        assertEquals(legacy.nearestUsableShop(lumbridge), nubles.nearestShop(lumbridge));
        assertEquals(new FateLockedBundle.Nearest(new CanonicalChunk(46, 52), 4), nubles.nearestBank(lumbridge));
    }

    /** The chunks with a row of this category the tracker allows, in a chunk it doesn't lock. */
    private static List<CanonicalChunk> usable(JsonObject chunks, String category)
    {
        List<CanonicalChunk> usable = new ArrayList<>();
        for (Map.Entry<String, JsonElement> chunk : chunks.entrySet())
        {
            JsonObject snapshot = chunk.getValue().getAsJsonObject();
            JsonArray rows = snapshot.getAsJsonObject("categories").getAsJsonArray(category);
            if (rows == null || "LOCKED".equals(snapshot.get("entry").getAsString())) continue;
            for (JsonElement row : rows)
            {
                if ("ALLOWED".equals(row.getAsJsonObject().get("status").getAsString()))
                {
                    usable.add(GoldenBundleContractTest.chunk(chunk.getKey()));
                    break;
                }
            }
        }
        return usable;
    }

    /** Brute force: the closest by the longer axis, ties to the smaller cx then cy. */
    private static FateLockedBundle.Nearest nearest(CanonicalChunk from, List<CanonicalChunk> candidates)
    {
        CanonicalChunk best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (CanonicalChunk chunk : candidates)
        {
            int distance = Math.max(Math.abs(chunk.getCx() - from.getCx()), Math.abs(chunk.getCy() - from.getCy()));
            if (best == null || distance < bestDistance || (distance == bestDistance
                && (chunk.getCx() < best.getCx() || (chunk.getCx() == best.getCx() && chunk.getCy() < best.getCy()))))
            {
                best = chunk;
                bestDistance = distance;
            }
        }
        return best == null ? null : new FateLockedBundle.Nearest(best, bestDistance);
    }

    private static DecisionService trusted(JsonObject wire)
    {
        return DecisionService.create(RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, wire.toString())), null, null);
    }

    private static JsonObject wire(String id) throws Exception
    {
        return GSON.fromJson(
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz")), JsonObject.class);
    }
}
