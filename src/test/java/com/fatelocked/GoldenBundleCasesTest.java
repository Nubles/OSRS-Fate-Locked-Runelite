package com.fatelocked;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * The web app's bundle cases (contracts/golden-bundles/cases.json): input
 * every import must refuse rather than load empty or partial rules, and
 * changes it must shrug off, keeping the same answers as the run they start
 * from.
 */
@RunWith(Parameterized.class)
public class GoldenBundleCasesTest
{
    private static final Gson GSON = new Gson();

    @Parameterized.Parameters(name = "{0} {1}")
    public static List<Object[]> cases() throws IOException
    {
        JsonObject file = GoldenBundleContractTest.json("cases.json");
        List<Object[]> cases = new ArrayList<>();
        for (JsonElement element : file.getAsJsonArray("reject"))
        {
            cases.add(new Object[] {"refuses", name(element), element.getAsJsonObject()});
        }
        for (JsonElement element : file.getAsJsonArray("sameAnswers"))
        {
            cases.add(new Object[] {"keeps its answers for", name(element), element.getAsJsonObject()});
        }
        return cases;
    }

    private final boolean refuse;
    private final String name;
    private final JsonObject bundleCase;

    public GoldenBundleCasesTest(String kind, String name, JsonObject bundleCase)
    {
        this.refuse = kind.equals("refuses");
        this.name = name;
        this.bundleCase = bundleCase;
    }

    @Test
    public void readsTheCaseAsTheTrackerIntends() throws IOException
    {
        String input = input();
        if (refuse)
        {
            try
            {
                FateLockedBundle.loadFromJson(GSON, input);
                fail(name + " was accepted");
            }
            catch (JsonParseException | IllegalArgumentException expected)
            {
                // Refused on purpose, so the active rules stay as they were.
            }
            return;
        }

        FateLockedBundle bundle = FateLockedBundle.loadFromJson(GSON, input);
        String scenario = bundleCase.get("scenario").getAsString();
        JsonObject expected = GoldenBundleContractTest.json(scenario + ".expect.json");
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : expected.getAsJsonObject("chunks").entrySet())
        {
            CanonicalChunk chunk = GoldenBundleContractTest.chunk(entry.getKey());
            if (bundle.regionAt(chunk) == null && bundle.subAreaAt(chunk) == null) continue;
            FateLockedBundle.LockState want = entry.getValue().getAsBoolean()
                ? FateLockedBundle.LockState.UNLOCKED : FateLockedBundle.LockState.LOCKED;
            if (bundle.lockStateAt(chunk) != want) mismatches.add("chunk " + entry.getKey());
        }
        for (Map.Entry<String, JsonElement> entry : expected.getAsJsonObject("areas").entrySet())
        {
            if (bundle.isUnlocked(entry.getKey()) != entry.getValue().getAsBoolean())
            {
                mismatches.add("area " + entry.getKey());
            }
        }
        for (Map.Entry<String, JsonElement> entry : expected.getAsJsonObject("banks").entrySet())
        {
            int bankId = Integer.parseInt(entry.getKey());
            CanonicalChunk chunk = new CanonicalChunk(bankId / 256, bankId % 256);
            if (bundle.isBankUnlocked(chunk) != entry.getValue().getAsBoolean())
            {
                mismatches.add("bank " + entry.getKey());
            }
        }
        assertEquals(name, List.of(), mismatches);
    }

    /** The case's input text, or its scenario's bundle changed as the recipe says. */
    private String input() throws IOException
    {
        if (bundleCase.has("input"))
        {
            return bundleCase.get("input").getAsString();
        }
        String scenario = bundleCase.get("scenario").getAsString();
        JsonObject root = GSON.fromJson(GoldenBundleContractTest.gunzip(
            GoldenBundleContractTest.bytes(scenario + ".bundle.json.gz")), JsonObject.class);
        if (bundleCase.has("set"))
        {
            for (Map.Entry<String, JsonElement> entry : bundleCase.getAsJsonObject("set").entrySet())
            {
                parentOf(root, entry.getKey()).add(last(entry.getKey()), entry.getValue());
            }
        }
        if (bundleCase.has("remove"))
        {
            for (JsonElement path : bundleCase.getAsJsonArray("remove"))
            {
                String dotted = path.getAsString();
                if (parentOf(root, dotted).remove(last(dotted)) == null)
                {
                    fail(name + ": nothing at " + dotted);
                }
            }
        }
        String text = GSON.toJson(root);
        if (bundleCase.has("truncate"))
        {
            text = text.substring(0, (int) (text.length() * bundleCase.get("truncate").getAsDouble()));
        }
        if (bundleCase.has("compress") && bundleCase.get("compress").getAsBoolean())
        {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bytes))
            {
                gzip.write(text.getBytes(StandardCharsets.UTF_8));
            }
            text = "FLGZ:" + Base64.getEncoder().encodeToString(bytes.toByteArray());
        }
        return text;
    }

    private static JsonObject parentOf(JsonObject root, String dotted)
    {
        String[] parts = dotted.split("\\.");
        JsonObject node = root;
        for (int i = 0; i < parts.length - 1; i++)
        {
            node = node.getAsJsonObject(parts[i]);
        }
        return node;
    }

    private static String last(String dotted)
    {
        String[] parts = dotted.split("\\.");
        return parts[parts.length - 1];
    }

    private static String name(JsonElement bundleCase)
    {
        return bundleCase.getAsJsonObject().get("name").getAsString();
    }
}
