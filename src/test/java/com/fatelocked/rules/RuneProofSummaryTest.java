package com.fatelocked.rules;

import com.google.gson.Gson;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RuneProofSummaryTest
{
    private final Gson gson = new Gson();

    @Test
    public void gsonParsesEveryStatusAndIgnoresFutureFields()
    {
        for (RuneProofSummary.Status status : RuneProofSummary.Status.values())
        {
            RuneProofSummary summary = gson.fromJson(summaryJson("goal:" + status.name(),
                status.name(), 7, "sha256-proof", "future display field"), RuneProofSummary.class)
                .normalized();

            assertEquals(status, summary.getStatus());
        }
    }

    @Test
    public void normalizationSortsSummaryAndLabelListsAndMakesThemImmutable()
    {
        RuneliteRulesManifest manifest = gson.fromJson("{\"runeProof\":["
            + summaryJson("goal:zeta", "BLOCKED", 7, null, null)
            + "," + summaryJson("goal:alpha", "OBTAINABLE", 7, "sha256-proof", null)
            + "]}", RuneliteRulesManifest.class).normalized();

        List<RuneProofSummary> summaries = manifest.getRuneProof();
        assertEquals(Arrays.asList("goal:alpha", "goal:zeta"), Arrays.asList(
            summaries.get(0).getGoalId(), summaries.get(1).getGoalId()));
        assertEquals(Arrays.asList("Alpha route", "Zulu route"),
            summaries.get(0).getRouteLabels());
        assertEquals(Arrays.asList("Alpha blocker", "Zulu blocker"),
            summaries.get(0).getBlockerLabels());
        assertImmutable(summaries);
        assertImmutable(summaries.get(0).getRouteLabels());
        assertImmutable(summaries.get(0).getBlockerLabels());
        assertImmutable(summaries.get(0).getUnavoidableBlockerLabels());
    }

    @Test
    public void absentRuneProofListNormalizesToAnEmptyImmutableList()
    {
        List<RuneProofSummary> summaries = gson.fromJson("{}", RuneliteRulesManifest.class)
            .normalized().getRuneProof();

        assertTrue(summaries.isEmpty());
        assertImmutable(summaries);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertImmutable(List values)
    {
        try
        {
            values.add("unexpected");
            fail("list must be immutable");
        }
        catch (UnsupportedOperationException expected)
        {
            // expected
        }
    }

    private static String summaryJson(String goalId, String status, long runRevision,
                                      String proofHash, String futureField)
    {
        String proof = proofHash == null ? "null" : "\"" + proofHash + "\"";
        String future = futureField == null ? "" : ",\"futureField\":\"" + futureField + "\"";
        return "{\"goalId\":\"" + goalId + "\",\"goalLabel\":\"Goal\","
            + "\"status\":\"" + status + "\",\"explanation\":\"Explanation\","
            + "\"routeLabels\":[\"Zulu route\",\"Alpha route\"],"
            + "\"blockerLabels\":[\"Zulu blocker\",\"Alpha blocker\"],"
            + "\"unavoidableBlockerLabels\":[\"Zulu blocker\",\"Alpha blocker\"],"
            + "\"proofHash\":" + proof + ",\"sourceVersion\":\"source-v1\","
            + "\"runRevision\":" + runRevision + future + "}";
    }
}