package com.fatelocked;

import com.google.gson.Gson;
import com.fatelocked.rules.ChunkPermissionSnapshot;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RuneProofSummary;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void displaysTheExactAppAuthoredPlankSummaryAndMarksAnOldRevisionStale()
        throws Exception
    {
        FateLockedBundle freshBundle = fixture("bundles/v4-runeproof-plank.json");
        RuneProofSummary summary = freshBundle.getRuneProofSummaries().get(0);

        assertEquals(RuneProofSummary.Status.OBTAINABLE_RNG, summary.getStatus());
        assertEquals(Collections.singletonList("Plank"), summary.getRouteLabels());
        assertEquals("FRESH", FateLockedPanel.runeProofBadge(freshBundle, summary));

        String json;
        try (InputStream in = getClass().getClassLoader()
            .getResourceAsStream("bundles/v4-runeproof-plank.json"))
        {
            assertNotNull(in);
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        FateLockedBundle staleBundle = FateLockedBundle.loadFromJson(new Gson(),
            json.replaceFirst("\"runRevision\": 7", "\"runRevision\": 8")
                .replaceFirst("\"runRevision\": 7", "\"runRevision\": 8"));
        RuneProofSummary stale = staleBundle.getRuneProofSummaries().get(0);

        assertEquals(RuneProofSummary.Status.OBTAINABLE_RNG, stale.getStatus());
        assertEquals(Collections.singletonList("Plank"), stale.getRouteLabels());
        assertEquals("STALE", FateLockedPanel.runeProofBadge(staleBundle, stale));
    }

    @Test
    public void runeProofFreshnessRequiresTheExactRunRevisionAndASourceVersion()
    {
        FateLockedBundle bundle = FateLockedBundle.loadFromJson(new Gson(),
            bundleWithRuneProof("OBTAINABLE", 41, "source-v1", "sha256-proof"));
        RuneProofSummary fresh = bundle.getRuneProofSummaries().get(0);
        RuneProofSummary stale = FateLockedBundle.loadFromJson(new Gson(),
            bundleWithRuneProof("OBTAINABLE", 40, "source-v1", "sha256-proof"))
            .getRuneProofSummaries().get(0);
        RuneProofSummary sourceMissing = FateLockedBundle.loadFromJson(new Gson(),
            bundleWithRuneProof("OBTAINABLE", 41, " ", "sha256-proof"))
            .getRuneProofSummaries().get(0);

        assertTrue(bundle.isRuneProofFresh(fresh));
        assertTrue(!bundle.isRuneProofFresh(stale));
        assertTrue(!bundle.isRuneProofFresh(sourceMissing));
    }

    @Test
    public void positiveRuneProofWithoutAHashIsUnverified()
    {
        FateLockedBundle bundle = FateLockedBundle.loadFromJson(new Gson(),
            bundleWithRuneProof("OBTAINABLE_RNG", 41, "source-v1", null));
        RuneProofSummary summary = bundle.getRuneProofSummaries().get(0);

        assertTrue(summary.isUnverified());
        assertEquals("UNVERIFIED", FateLockedPanel.runeProofBadge(bundle, summary));

        FateLockedBundle staleBundle = FateLockedBundle.loadFromJson(new Gson(),
            bundleWithRuneProof("OBTAINABLE_RNG", 40, "source-v1", null));
        assertEquals("UNVERIFIED · STALE", FateLockedPanel.runeProofBadge(staleBundle,
            staleBundle.getRuneProofSummaries().get(0)));
    }

    @Test
    public void runeProofPanelIncludesUnavoidableBlockerLabels()
    {
        RuneProofSummary summary = new Gson().fromJson("{\"goalId\":\"item:coins\","
            + "\"goalLabel\":\"Coins\",\"status\":\"BLOCKED\","
            + "\"explanation\":\"Blocked\",\"routeLabels\":[],\"blockerLabels\":[],"
            + "\"unavoidableBlockerLabels\":[\"Coins\"],\"proofHash\":null,"
            + "\"sourceVersion\":\"source-v1\",\"runRevision\":41}", RuneProofSummary.class)
            .normalized();

        assertEquals("Blockers: Coins", FateLockedPanel.runeProofBlockers(summary));
    }
    @Test(expected = IllegalArgumentException.class)
    public void rejectsFutureBundle() throws Exception
    {
        fixture("bundles/v5-future.json");
    }

    private static String bundleWithRuneProof(String status, long proofRevision,
                                              String sourceVersion, String proofHash)
    {
        String hash = proofHash == null ? "null" : "\"" + proofHash + "\"";
        return "{\"version\":4,\"chunks\":{},\"rules\":{"
            + "\"rulesVersion\":\"1\",\"runId\":\"run-1\",\"runRevision\":41,"
            + "\"gameModeId\":\"vanilla\",\"exportedAt\":\"2026-07-29T00:00:00Z\","
            + "\"unlocks\":{},\"chunks\":{},\"runeProof\":[{"
            + "\"goalId\":\"item:oak-plank\",\"goalLabel\":\"Oak plank\","
            + "\"status\":\"" + status + "\",\"explanation\":\"Certificate summary\","
            + "\"routeLabels\":[\"Sawmill\"],\"blockerLabels\":[],"
            + "\"unavoidableBlockerLabels\":[],\"proofHash\":" + hash + ","
            + "\"sourceVersion\":\"" + sourceVersion + "\",\"runRevision\":"
            + proofRevision + "}]}}";
    }
}
