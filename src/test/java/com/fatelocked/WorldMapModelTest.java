package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/** U18, decision 4: the world map shades what's locked, fills the frontier, and outlines the rest. */
public class WorldMapModelTest
{
    private static final Gson GSON = new Gson();

    /** WorldMapChunks.fill stays the oracle: the runs are its locked land and frontier, nothing else. */
    @Test
    public void everyGoldensLockedLandAndFrontierIsDrawnAndItsUnlockedLandIsClear() throws Exception
    {
        for (Object[] scenario : GoldenBundleContractTest.scenarios())
        {
            String id = (String) scenario[0];
            DecisionService playing = playing(id);
            WorldMapModel model = WorldMapModel.of(playing);

            Map<CanonicalChunk, WorldMapChunks.Fill> fills = new HashMap<>();
            Map<CanonicalChunk, WorldMapChunks.Fill> want = new HashMap<>();
            for (CanonicalChunk chunk : playing.mappedChunks())
            {
                WorldMapChunks.Fill fill = WorldMapChunks.fill(playing, chunk);
                if (fill == null) continue;
                fills.put(chunk, fill);
                if (fill != WorldMapChunks.Fill.UNLOCKED) want.put(chunk, fill);
            }
            Map<CanonicalChunk, WorldMapChunks.Fill> drawn = new HashMap<>();
            WorldMapModel.Run previous = null;
            for (WorldMapModel.Run run : model.runs())
            {
                for (int cx = run.getCx0(); cx <= run.getCx1(); cx++)
                {
                    assertNull(id + " drawn once", drawn.put(new CanonicalChunk(cx, run.getCy()), run.getFill()));
                }
                assertFalse(id + " touching runs of one fill are joined", previous != null
                    && previous.getCy() == run.getCy() && previous.getCx1() + 1 == run.getCx0()
                    && previous.getFill() == run.getFill());
                previous = run;
            }
            assertEquals(id, want, drawn);
            assertEquals(id + " outlines its unlocked land", outline(fills), units(model));
        }
    }

    @Test
    public void anotherCharacterGetsAClearMap() throws Exception
    {
        DecisionService other = DecisionService.create(playing("vanilla-mid").rules(), "iron example", "someone else");
        WorldMapModel model = WorldMapModel.of(other);
        assertTrue(model.runs().isEmpty());
        assertTrue(model.outline().isEmpty());
    }

    /** The map is clipped as RuneLite clips it: never over the overview or the surface selector. */
    @Test
    public void theOverviewAndTheSelectorAreLeftOutWhereShown()
    {
        Rectangle map = new Rectangle(0, 0, 700, 470);
        Area clip = WorldMapClip.of(map, new Rectangle(500, 320, 200, 150), new Rectangle(0, 0, 150, 40));
        assertFalse("the overview", clip.contains(600, 400));
        assertFalse("the surface selector", clip.contains(20, 20));
        assertTrue(clip.contains(300, 200));

        Area hidden = WorldMapClip.of(map, null, null);
        assertTrue(hidden.contains(600, 400));
        assertTrue(hidden.contains(20, 20));
    }

    /** Each unit of outline wanted: every side where unlocked land meets locked land or the frontier. */
    private static Set<String> outline(Map<CanonicalChunk, WorldMapChunks.Fill> fills)
    {
        Set<String> units = new HashSet<>();
        for (Map.Entry<CanonicalChunk, WorldMapChunks.Fill> chunk : fills.entrySet())
        {
            if (chunk.getValue() != WorldMapChunks.Fill.UNLOCKED) continue;
            int cx = chunk.getKey().getCx();
            int cy = chunk.getKey().getCy();
            if (locked(fills, cx - 1, cy)) units.add("V" + cx + "," + cy);
            if (locked(fills, cx + 1, cy)) units.add("V" + (cx + 1) + "," + cy);
            if (locked(fills, cx, cy - 1)) units.add("H" + cy + "," + cx);
            if (locked(fills, cx, cy + 1)) units.add("H" + (cy + 1) + "," + cx);
        }
        return units;
    }

    private static boolean locked(Map<CanonicalChunk, WorldMapChunks.Fill> fills, int cx, int cy)
    {
        WorldMapChunks.Fill fill = fills.get(new CanonicalChunk(cx, cy));
        return fill == WorldMapChunks.Fill.LOCKED || fill == WorldMapChunks.Fill.FRONTIER;
    }

    /** The model's outline, cut back into units; stretches that meet are joined. */
    private static Set<String> units(WorldMapModel model)
    {
        Set<String> units = new HashSet<>();
        WorldMapModel.Edge previous = null;
        for (WorldMapModel.Edge edge : model.outline())
        {
            assertFalse("stretches that meet are joined: " + edge, previous != null
                && previous.isVertical() == edge.isVertical() && previous.getLine() == edge.getLine()
                && previous.getTo() == edge.getFrom());
            previous = edge;
            for (int at = edge.getFrom(); at < edge.getTo(); at++)
            {
                assertTrue("each unit once", units.add((edge.isVertical() ? "V" : "H") + edge.getLine() + "," + at));
            }
        }
        return units;
    }

    private static DecisionService playing(String id) throws Exception
    {
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
        JsonElement bound = GSON.fromJson(json, JsonObject.class).getAsJsonObject("rules").get("account");
        String account = bound == null || bound.isJsonNull() ? null : AccountBinding.normalize(bound.getAsString());
        return DecisionService.create(RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json)), account, account);
    }
}
