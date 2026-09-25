package com.fatelocked;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The copied web contract files are intact, and account names compare as the tracker compares them. */
public class GoldenContractFilesTest
{
    @Test
    public void everyCopiedFileMatchesTheManifest() throws Exception
    {
        JsonObject files = GoldenBundleContractTest.json("manifest.json").getAsJsonObject("files");
        assertTrue(files.size() > 0);
        for (Map.Entry<String, JsonElement> file : files.entrySet())
        {
            assertEquals(file.getKey(), file.getValue().getAsString(),
                sha256(GoldenBundleContractTest.bytes(file.getKey())));
        }
    }

    @Test
    public void thePinnedWebCommitIsRecorded() throws IOException
    {
        String pinned;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("contracts/PINNED"))
        {
            pinned = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(pinned, pinned.contains("repository=Nubles/OSRS-Fate-Locked\n"));
        assertTrue(pinned, pinned.matches("(?s).*\\bcommit=[0-9a-f]{40}\n.*"));
    }

    /**
     * Logged-in names the plugin still compares differently from the tracker:
     * it neither trims nor collapses spaces (review finding R11). Stage 1 task
     * B11 fixes this and must empty the set.
     */
    private static final Set<String> KNOWN_R11 = Set.of(" Iron Example ", "Iron  Example");

    @Test
    public void accountNamesCompareAsTheTrackerDoes() throws IOException
    {
        List<String> mismatches = new ArrayList<>();
        Set<String> divergent = new TreeSet<>();
        for (JsonElement element : GoldenBundleContractTest.json("accounts.json").getAsJsonArray("pairs"))
        {
            JsonObject pair = element.getAsJsonObject();
            String bound = pair.get("bound").getAsString();
            String player = pair.get("player").getAsString();
            boolean want = pair.get("match").getAsBoolean();
            boolean got = FateLockedPlugin.normName(bound).equals(FateLockedPlugin.normName(player));
            if (got != want)
            {
                divergent.add(player);
                if (!KNOWN_R11.contains(player))
                {
                    mismatches.add("'" + bound + "' vs '" + player + "' want " + want);
                }
            }
        }
        assertEquals(List.of(), mismatches);
        assertEquals("known R11 divergences; remove any the plugin now agrees on",
            new TreeSet<>(KNOWN_R11), divergent);
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException
    {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
        {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
