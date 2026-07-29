package com.fatelocked.rules;

import com.fatelocked.FateLockedBundle;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RuneProofWireValidationTest
{
    private static final String HASH =
        "sha256-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private final Gson gson = new Gson();

    @Test
    public void acceptsOneCanonicalV1Summary()
    {
        assertEquals(1, load(bundle(summary("item:plank"))).getRuneProofSummaries().size());
    }

    @Test
    public void suppressesUnknownMissingAndNonCanonicalFields()
    {
        String canonical = summary("item:plank");
        assertSuppressed(canonical.replace("}", ",\"futureField\":true}"));
        assertSuppressed(canonical.replace("\"goalLabel\":\"Plank\",", ""));
        assertSuppressed(canonical.replace("item:plank", "Item:Plank"));
        assertTrue(load(bundle(canonical).replace(
            "\"runeProofSchemaVersion\":1",
            "\"runeProofSchemaVersion\":\"1\"")).getRuneProofSummaries().isEmpty());
    }

    @Test
    public void suppressesDuplicateGoalsAndExcessiveSummaryCounts()
    {
        String canonical = summary("item:plank");
        assertSuppressed(canonical + "," + canonical);

        List<String> summaries = new ArrayList<>();
        for (int index = 0; index < 21; index++)
        {
            summaries.add(summary("item:goal-" + index));
        }
        assertSuppressed(String.join(",", summaries));
    }

    @Test
    public void suppressesOversizedTextLabelsAndContradictoryClaims()
    {
        String canonical = summary("item:plank");
        String longText = String.join("", Collections.nCopies(513, "x"));
        assertSuppressed(canonical.replace("Current route", longText));
        assertSuppressed(canonical.replace("Graveyard spawn", longText));
        assertSuppressed(canonical
            .replace("\"status\":\"OBTAINABLE\"", "\"status\":\"UNKNOWN\""));
    }

    @Test
    public void suppressesPayloadsOverTheV1ByteBudget()
    {
        List<String> labels = new ArrayList<>();
        for (int index = 0; index < 32; index++)
        {
            labels.add("\"" + String.join("", Collections.nCopies(150, "x"))
                + String.format("%02d", index) + "\"");
        }
        String routeLabels = "[" + String.join(",", labels) + "]";
        List<String> summaries = new ArrayList<>();
        for (int index = 0; index < 20; index++)
        {
            summaries.add(summary("item:large-" + index).replace(
                "[\"Graveyard spawn\"]", routeLabels));
        }

        assertSuppressed(String.join(",", summaries));
    }
    private void assertSuppressed(String summaries)
    {
        assertTrue(load(bundle(summaries)).getRuneProofSummaries().isEmpty());
    }

    private FateLockedBundle load(String json)
    {
        return FateLockedBundle.loadFromJson(gson, json);
    }

    private static String bundle(String summaries)
    {
        return "{\"version\":4,\"chunks\":{},\"rules\":{"
            + "\"rulesVersion\":\"1\",\"runId\":\"run-1\",\"runRevision\":7,"
            + "\"runeProofSchemaVersion\":1,\"gameModeId\":\"chunked\","
            + "\"exportedAt\":\"2026-07-29T00:00:00Z\",\"unlocks\":{},"
            + "\"chunks\":{},\"runeProof\":[" + summaries + "]}}";
    }

    private static String summary(String goalId)
    {
        return "{\"goalId\":\"" + goalId + "\",\"goalLabel\":\"Plank\","
            + "\"status\":\"OBTAINABLE\",\"explanation\":\"Current route\","
            + "\"routeLabels\":[\"Graveyard spawn\"],\"blockerLabels\":[],"
            + "\"unavoidableBlockerLabels\":[],\"proofHash\":\"" + HASH + "\","
            + "\"sourceVersion\":\"source-v1\",\"runRevision\":7}";
    }
}