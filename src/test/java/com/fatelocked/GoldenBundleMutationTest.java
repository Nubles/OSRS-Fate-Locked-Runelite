package com.fatelocked;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * The golden bundles with one root field changed, to prove that a v4
 * bundle's answers come from its rules and not from leftover root fields.
 */
@RunWith(Parameterized.class)
public class GoldenBundleMutationTest
{
    private static final Gson GSON = new Gson();

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> scenarios() throws IOException
    {
        return GoldenBundleContractTest.scenarios();
    }

    private final String id;
    private final JsonObject wire;
    private final JsonObject expected;

    public GoldenBundleMutationTest(String id) throws IOException
    {
        this.id = id;
        wire = GSON.fromJson(GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz")),
            JsonObject.class);
        expected = GoldenBundleContractTest.json(id + ".expect.json");
    }

    /**
     * R10: the mere presence of a root unlockedChunks list used to turn any
     * v4 run into a Chunked one. Only the rules decide that now.
     */
    @Test
    public void strayRootUnlockedChunksChangeNothing()
    {
        FateLockedBundle original = FateLockedBundle.loadFromJson(GSON, wire.toString());
        JsonObject mutated = wire.deepCopy();
        mutated.add("unlockedChunks", new JsonArray());
        FateLockedBundle stray = FateLockedBundle.loadFromJson(GSON, mutated.toString());

        assertEquals(id, original.isChunkedBundle(), stray.isChunkedBundle());
        List<String> changed = new ArrayList<>();
        for (String key : expected.getAsJsonObject("chunks").keySet())
        {
            CanonicalChunk chunk = GoldenBundleContractTest.chunk(key);
            if (original.lockStateAt(chunk) != stray.lockStateAt(chunk)) changed.add(key);
        }
        assertEquals(id + " chunks", List.of(), changed);
        assertEquals(id + " areas", original.getUnlockedAreas(), stray.getUnlockedAreas());
        assertEquals(id + " area total", original.getTotalAreas(), stray.getTotalAreas());
    }
}
