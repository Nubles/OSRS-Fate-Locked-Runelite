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

    @Test
    public void accountNamesCompareAsTheTrackerDoes() throws IOException
    {
        List<String> mismatches = new ArrayList<>();
        for (JsonElement element : GoldenBundleContractTest.json("accounts.json").getAsJsonArray("pairs"))
        {
            JsonObject pair = element.getAsJsonObject();
            String bound = pair.get("bound").getAsString();
            String player = pair.get("player").getAsString();
            boolean want = pair.get("match").getAsBoolean();
            if (AccountBinding.sameAccount(bound, player) != want)
            {
                mismatches.add("'" + bound + "' vs '" + player + "' want " + want);
            }
        }
        assertEquals(List.of(), mismatches);
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
